# FD-007 Worker 实施报告，第 2 轮

<!-- aiw-data: FD-007-implementation-r2.json -->

## 结论

已修复 Reviewer r1 的两项问题：旧式 `TaskExecutor` 正常返回不再等同于确认停止；同一任务、Worker 和租约的停止确认重试现在幂等返回，不会再次重派或释放资源。静态 `git diff --check` 通过。此次没有运行测试、编译或数据库迁移。

## 修复内容

- `TaskExecutor` 增加显式 `ExecutionOutcome`。默认适配器即使收到停止信号后正常返回，也只报告 `COMPLETED`；只有明确支持协作停止的 executor 才能返回 `STOPPED`。
- `WorkerRuntime` 只有在控制面已请求停止且 executor 明确返回 `STOPPED` 时才发送停止确认。正常完成与停止请求竞态时，不再把完成返回误认成停止确认。Fake executor 通过观察剩余执行时长和停止信号返回相应结果。
- `ExecutionAttemptRepository` 增加按 task、worker、lease 核对已确认停止记录的查询。`TaskExecutionService` 在处理重复请求时先核验这组身份；匹配已确认记录则直接成功返回，避免重复重派、审计及容量/并发额度释放。身份或租约不匹配仍会被拒绝。
- FD 验证计划补充旧式/lambda executor 无法自动确认停止、协作式停止结果，以及重复确认与错误身份的覆盖场景。

## 验证

- `git diff --check`：通过，无 whitespace 错误。
- Worker 实现报告和本轮代码均未通过编译检查。之前的 `mvn -q -DskipTests compile` 因本机没有 Maven、仓库没有 Maven/Gradle wrapper 而无法启动；本轮没有重试。
- 单元/集成测试、旧 executor 停止行为、重复确认行为和 SQLite/PostgreSQL 迁移检查均未运行，仍由独立 Tester 阶段覆盖；不得视为通过。

## 追踪信息

- FD：revision 10，SHA-256 `e7e98c1b1824eba5c564a2cfce26f08d7bdc8bc3989f104e1df41db13bd3ba80`
- 来源事件：`FD-007-000010-changes-requested`
- Worker session：`fd007-worker-20261007-r2-31d7c9`
- 修复 Reviewer：`docs/features/reviews/FD-007-review-r1.md`

## 剩余风险

停止仍是协作式协议。旧式 executor 如果不能报告 `STOPPED`，任务会保留为未确认停止状态，需由管理员明确处理；如果 executor 错误地声称已停止，静态契约本身无法证明进程或外部副作用确实终止。没有运行时行为、并发或双数据库证据。
