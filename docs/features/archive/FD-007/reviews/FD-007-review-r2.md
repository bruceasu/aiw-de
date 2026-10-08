# FD-007 独立审查报告，第 2 轮
<!-- aiw-data: FD-007-review-r2.json -->

## 结论

**结果：changes-requested。** 上轮 F-01/F-02 在当前实现中已静态修复；本轮没有发现新的确定性 Java 源码缺陷。仍有一项实际验收要求无法由静态材料满足：SQLite 与 PostgreSQL 必须能成功应用 V19 迁移（S21、S22），但两种数据库的迁移均未执行。PM r2 明确允许在 22 个场景均未运行的情况下继续静态审查，并豁免覆盖率门槛；该决定没有把未运行的行为/迁移场景记作通过。当前无法据此确认 S21/S22，故不发 verification-passed。

## 审查范围与身份

- FD：`FD-007`，审查交接对应 revision 13，SHA-256 `4d6a6deed71a987ea45facf622a710de0a6ef00b08d90b3afb87c39c79174ae8`
- 来源事件：`FD-007-000013-test-accepted`
- Reviewer session：`fd007-reviewer-r2-20261007-8f3c2a`
- Worker：`fd007-worker-20261007-r2-31d7c9`
- Tester：`fd007-tester-20261007-r2-c41e8d`
- 完整 diff：`40493aa95f9e88b89fa990dc2509f0e47916db92..6746af2`
- 修复增量：`151c6f09a568c668a0048a431e32e196180b4de6..6746af2`
- 参考：Worker r2、Tester r2（22 个场景）、PM test decision r2、Reviewer r1 与对应事件回执。

## 上轮发现复核

- **F-01：已静态解决。** `TaskExecutor.executeWithStopSignal` 对旧单参/lambda executor 的默认适配在正常返回时给出 `COMPLETED`。`WorkerRuntime` 只有在本地已收到 stop request 且 executor 明确返回 `STOPPED` 时才提交 stop acknowledgement；完成/失败竞态路径沿用同一条件。普通返回不能触发安全重派。
- **F-02：已静态解决。** `TaskExecutionService.acknowledgeStop` 先锁任务，再按 task、worker、lease 精确查找已持久化 ACK。匹配的重复 ACK 直接返回；首次 ACK 才将 attempt 从 `RUNNING` 改为 `EXPIRED`、重派任务、释放 worker/并发资源并写审计。错误身份不能匹配该查询；首次 ACK 的条件更新和任务 lease 条件更新会拒绝不匹配身份。任务锁与事务边界使同一任务的并发 ACK/重派串行化。
- 对 S15-S20 的逻辑只作静态审查；未将源码推断写成行为测试通过。Worker/Executor 仍必须诚实遵守 `STOPPED` 契约；管理员强制重派后，外部副作用仍可能继续。

## 发现

### F-03 — 中：SQLite 与 PostgreSQL 迁移应用条件未获证据

- 位置：`src/main/resources/db/migration/V19__task_instruction_revision_control.sql:1-58`；FD Verification 的 SQLite/PostgreSQL 迁移要求；Tester r2 的 S21、S22。
- 要求：S21、S22 要求任务指令与控制状态迁移分别能成功应用于 SQLite 和 PostgreSQL。
- 证据：V19 使用两种数据库共有的常规 DDL；静态检查未发现明显方言专用语法或类型冲突。但 Worker、Tester 与 PM r2 均记录两种迁移都没有实际执行，也没有数据库输出。静态 SQL 读取不能证明迁移 runner 在现有数据库版本、配置和既有数据上成功应用。
- 所需处理：在获准的验证流程中分别应用 SQLite、PostgreSQL 迁移并保存结果；或由 PM 明确修订/豁免这两项验收要求。当前仓库指令禁止本 Reviewer 执行迁移命令，本轮未尝试。

## 测试与验证证据

Tester r2 清点 22 个适用场景，执行/通过/失败均为 0，未覆盖 22；需求覆盖率 `0%`，分支覆盖未测量，无原始覆盖数据或已执行命令。PM r2 接受覆盖率例外仅用于推进静态审查。该例外未使任何场景成为已执行或通过的行为测试。无测试文件、测试授权记录或 SQLite/PostgreSQL 迁移结果。

本 Reviewer 没有运行测试、编译、构建、数据库迁移、覆盖率、应用或网络命令。静态源码和 SQL 审阅不能替代这些运行证据。

## 命令记录

- 已运行：`aiw fd show FD-007`
- 已运行：`aiw fd claim FD-007 FD-007-000013-test-accepted --session fd007-reviewer-r2-20261007-8f3c2a`
- 已运行：`git -C .wt/FD-007 log --oneline --decorate -8`
- 已运行：`git -C .wt/FD-007 diff --stat 40493aa..6746af2`
- 已运行：`git -C .wt/FD-007 diff --stat 151c6f0..6746af2`
- 已运行：针对 Worker r2 修复文件及控制面/UI/迁移文件的 `git diff`、`rg -n` 与 UTF-8 静态读取。
- 另有一条读取命令误包含 `git -C .wt/FD-007 diff 40493aa..6746af2 --check`；执行后无输出。该命令不作为行为或迁移证据。
- 未运行：tests、compile/build、SQLite migration、PostgreSQL migration、coverage、application runtime、network。

## 剩余风险

22 个场景没有运行，包含授权/UI、版本并发、旧 attempt 隔离、停止/完成竞态、ACK 并发与资源计数。Worker r2 记载 Maven 不可用且无 Maven/Gradle wrapper；本 Reviewer 未尝试编译。S21/S22 迁移能否实际应用仍未知。协作停止依赖 executor 正确返回 `STOPPED`；ADMIN 强制重派不能终止外部副作用。

- 审查时间：2026-10-07 07:14 JST