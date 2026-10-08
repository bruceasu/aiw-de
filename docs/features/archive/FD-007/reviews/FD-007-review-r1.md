# FD-007 独立审查报告，第 1 轮
<!-- aiw-data: FD-007-review-r1.json -->

## 结论

**结果：changes-requested。** 两项静态可确认的问题违反停止与重派的接受条件：现有 `TaskExecutor` 可以忽略停止信号，但运行时仍会据此确认停止；重复提交 Worker 停止确认不具幂等性。修复并补足相应行为证据后需要重新审查。

PM 已明确豁免覆盖门槛及未运行测试，只允许流程进入静态 Reviewer 阶段。我未将豁免视为测试通过或行为验收。Tester 报告列出的 14 个场景均未运行，需求覆盖率为 0%，分支覆盖未测量；编译与 SQLite/PostgreSQL 迁移检查也未运行。

## 审查范围与身份

- FD：`FD-007`，revision 9，SHA-256 `3f27e314b5cfae9be80d29cf6f620a6a60e8b80fd03cc8c5bf2200c77f0ec13a`
- 来源事件：`FD-007-000009-test-accepted`
- Reviewer session：`fd007-reviewer-20261007-a83c1d`
- 差异基线：`40493aa95f9e88b89fa990dc2509f0e47916db92`
- 审查提交：`151c6f09a568c668a0048a431e32e196180b4de6`（`feature/FD-007`）
- 独立 Tester：`fd007-tester-20261007-b6e91d`
- Worker：`fd007-worker-20261007-5f12bd`
- 已查看实际代码差异、Worker 实现报告、Tester 场景清单、PM 决策及 AIW 事件回执。

## 发现

### F-01 — 高：忽略停止信号的 Executor 仍会被确认停止并安全重派

位置：`src/main/java/me/asu/aidev/worker/runtime/TaskExecutor.java:10-12`、`src/main/java/me/asu/aidev/worker/runtime/WorkerRuntime.java:173,186-188`、`src/main/java/me/asu/aidev/worker/runtime/WorkerRuntimeFactory.java:22-26`。

`TaskExecutor` 保留单参数抽象方法，并以默认双参数方法直接调用它，因此旧实现和 lambda 都会静默忽略 `stopRequested`。`WorkerRuntime` 将信号传给该方法，但在方法正常返回后只要标志为 true，就立即发送停止确认并放弃此次完成结果。控制面收到确认后会将任务放回 READY。对忽略信号的 Executor 来说，这个“确认”可能发生在工作和外部副作用已经完整执行之后；随后正常领取的 Worker 会再次执行任务。由此，默认的“等待停止确认再重派”路径仍可能造成重复副作用，而 FD 将其作为区分于 ADMIN 强制重派的安全路径。

要求：使停止能力成为明确且不可被默认实现静默忽略的契约；运行时只能在 Executor 确认已停止后确认租约，不得把普通执行返回当作停止确认。增加覆盖旧式/lambda Executor 的行为场景，证明无法协作停止时不会被标记为安全停止并重派。

### F-02 — 中：停止确认重试不是幂等操作

位置：`src/main/java/me/asu/aidev/execution/application/TaskExecutionService.java:183-194`、`src/main/java/me/asu/aidev/execution/jdbc/JdbcExecutionAttemptRepository.java:129-137`。

第一次确认会把 attempt 状态改为 `EXPIRED` 并把任务重派为 `READY`。相同确认重试时，服务在第 186 行因任务不再是 `RUNNING` 而抛出异常；即使任务后来再次运行，旧 attempt 的更新也因 SQL 只接受 `state='RUNNING'` 而失败。Worker 在响应丢失后无法安全重试。FD Work Item 1.3 和 Tester 场景 S07 明确要求确认幂等。

要求：对同一 task、worker 和 lease 的已成功确认重试返回成功，但不重复重派、不重复释放容量或并发额度；对于不匹配的身份和令牌仍须拒绝。增加重复确认及响应丢失重试的行为场景。

## 验收与证据状态

- 两项发现直接关联 FD 的停止确认、正常重派与幂等接受条件，故本轮不能通过 Verification。
- Tester 报告：适用场景 14，覆盖 0，已执行行为测试 0，通过 0，失败 0，未运行 14；需求覆盖率 `0%`，分支覆盖 `not measured`，未提供原始覆盖数据。
- PM 决策明确豁免 70% 覆盖门槛，仅推进静态审查；该决定未声称测试或实现行为通过。本报告尊重该豁免，不因覆盖率单独判定拒绝，但要求修复上述静态缺陷。
- Worker 报告称 `git diff --check` 通过；本 Reviewer 未重跑该命令。Worker 报告称 `mvn -q -DskipTests compile` 因本机无 Maven 且无 Maven/Gradle wrapper 未能启动；SQLite/PostgreSQL 迁移检查未执行。本轮没有编译、测试或迁移证据。

## 本轮命令

- 已运行只读状态/回执读取：`aiw fd show FD-007`；并成功认领 `aiw fd claim FD-007 FD-007-000009-test-accepted --session fd007-reviewer-20261007-a83c1d`。
- 已运行静态差异与源码搜索：`git diff --stat 40493aa..feature/FD-007`、针对实现文件的 `git diff 40493aa..feature/FD-007 -- ...`、`rg -n` 对 `TaskExecutor`、`acknowledgeStop` 和停止调用链的定位，以及对 FD、报告、JSON 和事件回执的 UTF-8 静态读取。
- 未运行：测试、编译/构建、数据库迁移、覆盖率、网络或应用运行命令。

## 剩余风险

权限场景、任务状态转换与乐观并发、旧 attempt 结果隔离、模板状态、Worker 协议兼容性、SQLite/PostgreSQL SQL 兼容性没有行为或迁移运行证据。SQL 静态审查不能代替双数据库验证。Worker 的外部副作用仍然只能协作式停止，尤其 ADMIN 强制重派存在明确的重复副作用风险。
