# k6 抢票压测说明

本目录用于压测“抢票 -> 查询结果 -> 支付/取消/重试”链路。

## 1. 准备 token

### 方式 A：自动批量登录（推荐）

```powershell
.\scripts\perf\fetch-tokens.ps1 `
  -BaseUrl "http://localhost:25001" `
  -Start 19120100 `
  -End 19122100 `
  -Password "123456" `
  -Concurrency 20 `
  -OutputFile "scripts/perf/tokens.json"
```

说明：
- 该脚本是 append/upsert 模式：会与已有 `tokens.json` 合并。
- 同一 `studentId` 会用本次新 token 覆盖旧 token。

### 方式 B：手工准备

1. 复制 `tokens.example.json` 为 `tokens.json`。  
2. 写入真实 token，格式如下：

```json
[
  { "studentId": "19120100", "token": "xxx" },
  { "studentId": "19120101", "token": "yyy" }
]
```

## 2. 运行压测

### Windows（PowerShell）

```powershell
k6 run .\scripts\perf\reserve-flow.js `
  -e BASE_URL=http://localhost:25001 `
  -e TOKENS_FILE=.\tokens.json `
  -e SLOT_ID_START=1 `
  -e SLOT_ID_END=120 `
  -e SESSION_ID=1 `
  -e RATE=2000 `
  -e TIME_UNIT=1m `
  -e DURATION=1m `
  -e PREALLOCATED_VUS=200 `
  -e MAX_VUS=2000 `
  -e SUCCESS_PAY_RATIO=0.4 `
  -e SUCCESS_CANCEL_RATIO=0.3 `
  -e FAIL_RETRY_RATIO=0.8 `
  -e RESERVE_MAX_RETRIES=3 `
  -e RESULT_POLL_MAX=20 `
  -e RESULT_POLL_INTERVAL_MS=400
```

提示：
- 建议在 `scripts/perf` 目录运行，`TOKENS_FILE=.\tokens.json` 最直观。
- 如果在项目根目录运行，`TOKENS_FILE` 可写成 `.\scripts\perf\tokens.json`。

## 3. 参数说明

- `BASE_URL`：后端地址。
- `TOKENS_FILE`：token 文件路径。
- `SLOT_ID`：单 slot 模式的 slotId（可选）。
- `SLOT_ID_START`：多 slot 起始 id。
- `SLOT_ID_END`：多 slot 结束 id。
- `SESSION_ID`：场次 id。
- `RATE`：每个 `TIME_UNIT` 发起的迭代数。
- `TIME_UNIT`：到达率时间单位（如 `1s`、`1m`）。
- `DURATION`：压测持续时间。
- `PREALLOCATED_VUS`：预分配 VU 数。
- `MAX_VUS`：VU 上限。
- `SUCCESS_PAY_RATIO`：抢到后走支付分支比例。
- `SUCCESS_CANCEL_RATIO`：抢到后走取消分支比例。
- `FAIL_RETRY_RATIO`：失败后重试比例。
- `RESERVE_MAX_RETRIES`：最大重试次数。
- `RESULT_POLL_MAX`：`/reserve/result/{traceId}` 最大轮询次数。
- `RESULT_POLL_INTERVAL_MS`：轮询间隔（毫秒）。

## 4. 关键结论（避免误读）

- `http_req_failed=0` 只表示网络层成功，不代表业务成功。
- 请结合自定义指标看业务：  
  `reserve_submit_fail`、`reserve_result_success`、`reserve_result_fail`。
- 如果出现 `connect refused`，是服务未监听/进程崩溃，不是业务 `UNGATED`。

## 5. 常见问题

### Q1：`invalid character 'ï' looking for beginning of value`
`tokens.json` 带 BOM。当前脚本已兼容去 BOM；若仍报错，重新生成一次 token 文件。

### Q2：大量 `reserve_submit_fail`，但 `http_req_failed=0`
说明是业务失败（如 `UNGATED`、`OUT_OF_STOCK`、`DUP_REQ`、`TOO_MANY_REQUESTS`），不是网络失败。

### Q3：并发一高就 `connect refused`
通常是服务被打挂（进程退出/依赖连接耗尽），先降 `RATE` 再逐步升压排查。
