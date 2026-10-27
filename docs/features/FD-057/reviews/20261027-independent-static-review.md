# FD-057 独立静态审查

- Reviewer：`codex-fd057-review-20261027`。
- 来源事件：`FD-057-000002-review-requested`，已由本 Reviewer claim。
- 比较基线：`7a7b92f88b0b5874981d73c915e1d812a9a42980` 上的 FD-057 增量；不包含另行调查的重做任务 DRAFT 推进。
- 最终结论：**通过**。静态无阻断发现；已读取限定 23 条管理员 API 注销回执、在线保留和历史数量不变证据。

## 核对结果

- 新路由纳入 `WebConsoleApiRoutes`，沿用 WebConsole 会话/CSRF 拦截；Authority 拦截提供并清除当前 human 上下文。Controller 另要求 ADMIN，服务拒绝 delegated 和 actor/human 不匹配，普通 Worker token 不构成注销授权。
- 服务事务先取得 Authority 锁再变更边界；Worker UPDATE 同时比较指定心跳、OFFLINE 或过期状态、零活动容量以及无运行任务/尝试，避免先读取再无条件写入。注册路径也取得 Authority 锁，先更新 Worker 再移除人工注销标记。
- 手工注销与工厂归属分表：V23 引用 Worker 保留实体，不为普通验证进程伪造 factory 所有权；仅隐藏注册视图和选择项，未删除历史任务或执行尝试。重新 register 移除手工标记；普通 heartbeat 不取消人工注销。
- 已注销重试返回 UNREGISTERED，不重复写审计；首次注销、状态修改和审计在同一事务，CAS 失败抛出并回滚边界变更。
- 读取冻结的 23 个 OFFLINE、activeLeases=0 目标及日志路径；抽样读取三条真实注册行，ID 与清单一致。在线工厂 Worker 不在清理目标中，具体执行仍需逐项使用快照 CAS。
- 文档准确说明人工注销、控制台认证、快照/原因、恢复方式及历史保留。没有额外执行或修改数独项目。

## 证据及限制

- 本 Reviewer 实际仅执行 `git diff/status/rev-parse`、`rg`、`Get-Content` 静态读取及真实 `aiw fd show/claim`；未测试、编译、调用 API、模型或操作服务。
- 执行者报告 compile-only 成功（318 源）与 diff check 通过；本轮未新增或运行单元测试，不将静态调用链表述为认证拒绝、并发或重新注册场景已经运行通过。
- 尚未运行：并发注册/注销竞态实验、鉴权负面场景、注册恢复及完整浏览器测试。当前这些保护依赖已读取的路由、事务、CAS 与既有注册契约。

## 执行者实际清理证据复核

- 已读取本机 `aiw-goal-execution/fd057-worker-cleanup-targets.json` 最终回执，23 个日志关联目标逐条返回 UNREGISTERED，包括 `goal-v6-architect`；注册视图由 25 降至 2。
- historyBefore/historyAfter 均为 Worker 28、work_task 31、execution_attempt 37，未删除历史实体；剩余两条为仍在线的 factory 身份，未将其混入临时验证 Worker 清理。
- FD Verification 记录执行前备份 `D:/temp/data/aidev-r3.before-FD057-20261027-083234.db`、integrity_check=ok、重启加载 V23、HTTP 200，以及正式管理员会话与 CSRF 的逐条 API 调用。本轮未调用模型。
- Reviewer 只读取以上实证，没有重复调用注销 API 或操作数据库；运行成功不替代上述尚未执行的拒绝及并发场景验证。
