# 定时任务设计

## 1. 目标

- 保证每日 `time_slot` 能按配置自动生成。
- 保证开抢前完成预热（Redis 索引、信号量、去重集合、闸门时间）。
- 保证到达开抢时间后自动开闸。
- 支持多实例部署下的幂等执行与补偿触发。

## 2. 任务总览

### 2.1 生成当日 slot

- 调度器：`DailySlotGenerateScheduler.maybeGenerateTodaySlots`
- 周期：每分钟执行一次
- 触发条件：`nowMinute >= GENERATE_TIME_SLOT_TIME`
- 执行动作：遍历所有 session，调用 `adminService.generateSlot(sessionId)`

### 2.2 预热近期开抢 session

- 调度器：`WarmupScheduler.warmupNearFutureSlots`
- 周期：每分钟执行一次
- 输入参数：`WARMUP_MINUTE`
- 查询窗口：`flashTime in [nowMinute, nowMinute + WARMUP_MINUTE]`
- 执行动作：调用 `adminService.warmupSession(session)`
- 目的：只预热“已进入预热窗口且尚未开抢”的 session，避免全量扫描历史场次

### 2.3 开闸

- 调度器：`WarmupScheduler.openGate`
- 周期：每分钟执行一次
- 查询条件：`flashTime <= now`
- 执行动作：
  - 若闸门已是 `1`，跳过
  - 若未预热，先触发 `warmupSession`
  - 预热完成后设置 `gate=1`

## 3. 幂等与并发控制

## 3.1 生成 slot 幂等

- done 标记：`bf:slotgen:done:{yyyymmdd}:{sessionId}`
- 锁：`bf:slotgen:lock:{yyyymmdd}:{sessionId}`
- 语义：同一天同一 session 只生成一次；多实例并发下仅一个实例真正执行

## 3.2 预热幂等

- session 级 done 标记：`bf:warmup:done:{yyyymmdd}:{sessionId}`
- session 级锁：`bf:warmup:lock:{yyyymmdd}:{sessionId}`
- slot 级 done 标记：`bf:warmup:done:{slotId}`
- 语义：
  - 同一天同一 session 只做一次完整预热
  - 每个 slot 的 Redis 初始化最多执行一次

## 3.3 预热写入内容

对当天该 session 下所有 slot：

- `bf:slot:session:{slotId}` -> sessionId
- `bf:sem:{slotId}` 初始化 permits（当前实现为 1）
- `bf:dedup:{slotId}` 清空后占位并设置 TTL
- `bf:gate:{sessionId}` 不存在时设为 `0`
- `bf:gate:time:{sessionId}` 记录今日开抢 epoch second

以上 key 均设置到当天结束的 TTL。

## 4. 配置更新后的补偿

- 入口：`AdminServiceImpl.updateConfig`
- 行为：
  - 先校验“候选配置 + 全部 session”一致性
  - DB 更新成功后，通过 `afterCommit` 触发补偿，避免读到未提交状态
- 补偿规则：
  - `GENERATE_TIME_SLOT_TIME` 变化：触发所有 session 的 `generateSlot`
  - `WARMUP_MINUTE` 变化：对已进入预热窗口的 session 触发 `warmupSession`

## 5. 配置与 session 约束

系统要求同一天内的时间关系成立：

- `generateTime <= warmupTime <= flashTime <= beginTime < endTime`
- `slotInterval > 0`
- `(endTime - beginTime)` 必须可被 `slotInterval` 整除
- `warmupMinutes <= flashTime(分钟值)`（防止 `flash - warmup` 跨午夜）

校验时机：

- 更新配置时：候选配置必须对所有 session 成立，否则拒绝更新
- 新增/修改 session 时：必须对当前配置成立，否则拒绝提交

## 6. 手动接口与调度协同

管理接口 `POST /admin/warmup/{sessionId}`、`POST /admin/open/{sessionId}`、`POST /admin/slot-gen/{sessionId}`
仍然保留。它们与定时任务共用同一组幂等标记与锁，允许人工补偿，不会破坏一致性。

## 7. 已知边界

- 当前任务依赖数据库配置值可解析（如 `LocalTime.parse`、`Integer.parseInt`）。
- 若未来放开“可跨日 session”需求，需要重新设计 `LocalTime` 维度下的比较与查询窗口逻辑。
