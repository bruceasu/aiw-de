# 故障排查与恢复

## 1. 先看三个接口

```text
GET /api/r1/runs/{runId}
GET /api/r1/runs/{runId}/dashboard
GET /api/r1/runs/{runId}/timeline
```

Fleet 问题看：

```text
GET /api/r1/fleet/dashboard
GET /api/r1/fleet/scheduling
```

## 2. READY 但一直不执行

- `unmatchedReady`：没有匹配 Capability 的 ACTIVE Worker。
- `capacityBlockedReady`：Worker 满载。
- `admissionBlockedReady`：Run 达到 maxParallelism。
- `fleetAdmissionBlockedReady`：Fleet 达到全局 ceiling。
- `policyBlockedReady`：Run PAUSED/FAILED/CANCELLED 等。

## 3. Run FAILED

查 lastFailedTaskId/failureReason，解决问题后调用 `/recover`，不要手工更新 DB。

## 4. Worker 消失

等待 Lease Recovery。只有主动 `/fail` 才会变成明确 Run failure。

## 5. Worker 401

Control Plane 和 Worker 的 `AIW_WORKER_TOKEN` 不一致。

## 6. Worker 409

通常代表 Ownership Lost：Lease 过期、Pause、Cancel 或 stale replay。Worker 应停止本地 Attempt，不能强制 complete。

## 7. SQLite 请求约 30 秒后 500

优先检查是否在 RowMapper 内发起第二次 JdbcTemplate query。单连接 profile 会等待自己持有的唯一 Connection。正确做法是 JOIN/一次查询，不是增加 pool。

## 8. 所有 Spring IT 都 ApplicationContext Error

找到第一个 root `Caused by`，不要只看后面 failure threshold。已知问题之一：Spring Bean 有多个 constructor 但 production constructor 没 `@Autowired`。

## 9. 覆盖解压后出现旧行为

ZIP 覆盖不会删除新版中已经不存在的旧源码。`mvn clean` 也只删 target，不删 src。最好解压到新目录，再运行 `mvn clean verify`。

## 10. Accounting Delta 非 0

Run slot 或 Worker lease accounting delta 应正常为 0。非 0 是一致性问题，不应只修改 Dashboard 数字掩盖。

## 11. overcommittedSlots > 0

运行时把 limit 从高值降到低值后，这是允许的临时状态。现有 Lease 不会被强杀，新的 Claim 被阻止，直到自然 drain。

## 12. Telegram 启动失败

确认：

```text
AIW_TELEGRAM_BOT_TOKEN
AIW_TELEGRAM_ALLOWED_USER_IDS
```

并确保 Approval 能解析到 chat ID。

## 13. 并行实现冲突太多

说明 Planner 可能把有真实依赖的修改拆成了独立 WorkItem。宁可回退成一个较大的 Implementation Task，也不要为了并行而强拆。
