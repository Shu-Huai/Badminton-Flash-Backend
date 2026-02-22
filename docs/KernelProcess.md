# 核心流程

## 概述

校园羽毛球约场系统，后端技术栈：
Spring Boot 3、Redis/Redisson、RabbitMQ、MySQL、MyBatis-Plus、Bucket4j。

关键业务目标：
- 每日固定开闸（示例 13:00）；
- 高并发下不超卖；
- 出现发布失败、消费失败、支付超时、取消/退款时可补偿且幂等。

## 设计原则

1. 唯一资源：`time_slot.id(slotId)` 代表可抢资源，数据库 `reservation(slot_id)` 唯一约束兜底。
2. 快慢分层：
- 快路径（同步）：开闸校验、限流、去重、扣库存、投递 MQ。
- 慢路径（异步）：消费落库、重试、死信补偿。
3. 一致性优先级：先保证“不超卖 + 最终一致”，再追求实时“已确认”。

## 状态机

### ReservationStatus

- `PENDING_PAYMENT`：预约已占位，待支付（MQ 消费成功后进入）。
- `CONFIRMED`：支付成功。
- `CANCELLED`：主动取消、支付超时取消、退款完成后统一进入。
- `PENDING`：枚举保留值，当前主流程未使用。

### PayOrderStatus

- `PAYING`：已创建支付单，等待支付结果。
- `SUCCESS`：支付成功。
- `FAILED`：支付单过期后转失败（下次创建支付单时惰性关闭）。
- `CLOSED`：预约取消时关闭未支付中的支付单。
- `REFUNDED`：成功支付后退款完成。

## 核心组件与职责

- Redis/Redisson
- `bf:sem:{slotId}`：`RSemaphore` 库存，当前实现每 slot `permits=1`。
- `bf:dedup:{slotId}`：`RSet<Integer>` 用户去重。
- `bf:slot:session:{slotId}`：slot 与 session 绑定关系，防串场。
- `bf:reserve:pending:{traceId}`：消息发布待确认的补偿上下文（`userId:slotId`）。
- `bf:gate:{sessionId}` / `bf:gate:time:{sessionId}`：开闸状态与闸门时间。
- RabbitMQ
- 主链路：`reserve.direct -> reserve.queue`。
- 重试失败入死信：`reserve.dlx -> reserve.dlq`。
- 消费端启用重试拦截：最多 3 次，失败后拒绝并不重回主队列，转 DLQ。
- MySQL
- `reservation(slot_id)` 唯一约束防重复占位。
- `pay_order(out_trade_no)` 唯一约束防重复支付单号。

## 端到端流程（含异常）

### 1) 预热与开闸

1. 定时任务预热：写入 slot->session、初始化 semaphore、初始化 dedup、写 warmup 标记、写 gate=`0`。
2. 定时任务开闸：到 `flash_time` 后将 `bf:gate:{sessionId}` 置 `1`。
3. 以上 key TTL 基本到当天结束。

### 2) 用户抢占 `/reserve/`

1. 校验 `slotId` 与 `sessionId` 匹配（Redis 中 `slot:session`）。
2. 用户维度限流（每分钟最多 5 次）。
3. 校验开闸：`gate != 1` 返回 `UNGATED`。
4. 去重：`dedup.add(userId)`，失败返回 `DUP_REQ`。
5. 抢库存：`sem.tryAcquire()`，失败回滚去重并返回 `OUT_OF_STOCK`。
6. 记录 `reserve:pending:{traceId}`（5 分钟 TTL）用于发布失败补偿。
7. 发送 MQ（带 `traceId`/`messageId`）。
8. 等待 publisher confirm：
- 明确 NACK：立即按 `traceId` 补偿（释放 dedup+库存），返回失败。
- 同步发送异常：立即补偿并返回失败。
- confirm 超时：视为状态未知，不立刻补偿（避免误释放）。
9. 接口同步返回 `traceId`，客户端可轮询查询结果：
- `GET /reserve/result/{traceId}` -> `PENDING | SUCCESS | FAILED`
- `SUCCESS` 时返回 `reservationId`

### 3) MQ 消费落库 `ReserveConsumer`

1. 插入 `reservation(user_id, slot_id, PENDING_PAYMENT)`。
2. 成功后清理 pending 补偿键。
3. `DuplicateKeyException`：
- 若同一 `slot` 已是同一用户：判定幂等重复消息，直接吞掉。
- 若同一 `slot` 是其他用户：业务冲突（不新增记录，保持现状）。
4. 其他异常：抛出异常触发重试，超过重试次数进入 DLQ。

### 4) 死信最终补偿 `ReserveDlqConsumer`

1. 从消息体/header/messageId 提取 `traceId`。
2. 调用 `compensateByTraceId(traceId, "dlq-final-fail")`：
- 若 pending 不存在：说明已被清理或已补偿，幂等返回。
- 若 DB 已存在该 `slot` 预约：跳过补偿（避免误释放）。
- 否则释放 dedup 与 semaphore。

## 取消流程（完整）

### A. 用户主动取消 `DELETE /reserve/{reservationId}`

前置条件：
- 预约必须属于当前用户；
- 预约状态必须是 `PENDING_PAYMENT`。

处理步骤：
1. CAS 更新 `reservation: PENDING_PAYMENT -> CANCELLED`。
2. 批量关闭该预约下 `PAYING` 的支付单：`PAYING -> CLOSED`。
3. 释放 Redis 资源：`dedup.remove(userId)` + `sem.release()`。

失败与并发：
- 状态已变化（如已支付）会更新 0 行并返回失败/参数错误；
- 事务包裹，DB 更新失败会回滚；
- Redis 释放在事务内调用，若 DB 未成功不会执行到释放步骤。

### B. 超时自动取消 `ReserveTimeoutScheduler`

1. 每分钟扫描：`status=PENDING_PAYMENT AND create_time <= now - PAY_TIMEOUT_MINUTE`。
2. 逐条调用 `cancelTimeoutPending(reservationId)`。
3. 内部同样执行：
- `PENDING_PAYMENT -> CANCELLED`；
- 关闭 `PAYING` 支付单为 `CLOSED`；
- 释放 dedup + semaphore。
4. 若记录不存在或状态非 `PENDING_PAYMENT`，直接跳过（幂等）。

## 支付与退款流程（完整）

### 1) 创建微信支付单 `POST /pay/wechat/{reservationId}`

1. 以 `reservationId` 加分布式锁（5 秒 lease）。
2. 校验预约属于当前用户且状态为 `PENDING_PAYMENT`。
3. 查找最近 `WECHAT + PAYING` 支付单：
- 未过期：直接复用并返回。
- 已过期：将该单置 `FAILED`，继续新建。
4. 读取配置 `PAY_TIMEOUT_MINUTE`、`PAY_AMOUNT`，创建新支付单 `PAYING`。
5. 当前实现为模拟下单，不调真实微信。

异常处理：
- 获取锁中断 -> `FAILED`；
- 配置非法/金额非法 -> `PARAM_ERROR`；
- 并发重复创建由锁与复用逻辑共同约束。

### 2) 支付成功回调（当前是 mock）

`POST /pay/wechat/mock-success/{outTradeNo}`（管理员）

1. 查询支付单与所属预约。
2. 若支付单已 `SUCCESS`：直接返回（幂等）。
3. 仅允许预约仍为 `PENDING_PAYMENT`。
4. CAS 更新：
- `pay_order: PAYING -> SUCCESS`；
- `reservation: PENDING_PAYMENT -> CONFIRMED`。

异常处理：
- 第二步更新失败（预约状态竞争）会抛 `FAILED`，避免出现“支付成功但预约未确认”被静默吞掉。

### 3) 退款 `POST /pay/refund/{reservationId}`

前置条件：
- 预约属于当前用户；
- 预约状态必须是 `CONFIRMED`（`CANCELLED` 直接返回）；
- 存在最近一笔 `WECHAT + SUCCESS` 支付单。

处理步骤：
1. 模拟微信退款（当前不调用真实接口）。
2. CAS 更新支付单：`SUCCESS -> REFUNDED`。
3. CAS 更新预约：`CONFIRMED -> CANCELLED`。
4. 释放 Redis 资源：`dedup.remove(userId)` + `sem.release()`。

幂等与并发：
- 若支付单状态已不是 `SUCCESS`，更新 0 行则直接返回，不重复退款；
- 若预约状态竞争导致第二步失败，抛 `FAILED`，由调用方感知。

## 异常处理矩阵（按场景）

- 参数/业务前置不满足：
- 抛 `BaseException(ResponseCode.PARAM_ERROR | UNGATED | DUP_REQ | OUT_OF_STOCK | TOO_MANY_REQUESTS)`。
- 最终由全局异常处理器统一返回标准响应体。
- 预约消息发布失败：
- 同步发送异常或 confirm 明确 NACK：立即补偿并返回 `FAILED`。
- confirm 超时：暂不补偿，等待消费端或 DLQ 最终裁决。
- 消费端异常：
- 自动重试（最多 3 次）-> 仍失败进入 DLQ -> 执行最终补偿。
- 取消/退款并发冲突：
- 通过条件更新（带状态条件）实现 CAS，更新 0 行即视为“状态已变化”，避免重复处理。

## 一致性与幂等总结

1. Redis 先扣减 + DB 唯一约束兜底，保证 slot 维度不超卖。
2. `traceId + reserve:pending` 使“发布失败补偿”可追踪、可重入。
3. 消费成功即清理 pending；DLQ 只处理最终失败场景。
4. 取消与退款都会释放 dedup+库存，使 slot 可再次预约。

## 当前接口清单（与本文对应）

- `POST /reserve/`：发起抢占，返回 `traceId`。
- `GET /reserve/result/{traceId}`：查询抢占结果（成功时返回 `reservationId`）。
- `DELETE /reserve/{reservationId}`：主动取消（仅待支付）。
- `POST /pay/wechat/{reservationId}`：创建/复用微信支付单。
- `POST /pay/wechat/mock-success/{outTradeNo}`：模拟支付成功（管理员）。
- `POST /pay/refund/{reservationId}`：退款并取消预约。
- `GET /pay/reservation/{reservationId}`：查询支付结果。

