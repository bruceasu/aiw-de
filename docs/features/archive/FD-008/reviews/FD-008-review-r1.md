# FD-008 独立审查报告 r1

<!-- aiw-data: FD-008-review-r1.json -->

## 结论

**changes-requested。** 用户已豁免本轮测试，PM 对 0/36 场景覆盖和未测量的分支覆盖作了明确例外；这不豁免待停止请求与随后任务变更之间的并发行为。静态审查发现一个可复现的状态路径会让替代任务使用请求受理之后修改的指令。

## 发现

1. **高：待停止期间修改指令后，ACK 仍按过期的替换意图创建任务。** `TaskWorkspaceReplacementService.request` 将 `source_version` 写入替换记录，但 `queryRow` 不读取该字段，`afterStopAcknowledged` 只检查源任务变为 READY、租约已释放和下游未推进，再用当前 `source.instruction()` 创建替代任务。`TaskExecutionService.reviseInstruction` 允许 RUNNING 任务在替换请求为 PENDING_STOP 时更新 instruction 和 version，并向同一 attempt 请求停止。随后该 Worker ACK，替换流程便会用**新指令**创建任务，且请求审计仍声称基于旧版本受理。该路径违反 FD 1.2 的任务快照、1.7 的状态变化冲突和 Acceptance 中版本保护、可追踪审计要求。修复时应在 ACK 前核对受理时任务版本或指令修订；若发生非 ACK 自身导致的变化，将请求置为 CONFLICT 并不创建替代任务。相关位置：`src/main/java/me/asu/aidev/work/application/TaskWorkspaceReplacementService.java` 的 `request`、`afterStopAcknowledged`、`queryRow`、`complete`；`src/main/java/me/asu/aidev/execution/application/TaskExecutionService.java` 的 `reviseInstruction`、`acknowledgeStop`。

2. **中：UI 无法明确区分 Worker 已确认停止与无租约立即替换。** FD Acceptance 要求页面显示“停止确认状态”。现有 `Replacement` 仅有 PENDING_STOP/COMPLETED/CONFLICT/REJECTED 与通用 resolvedAt，Run 页面只显示 status 和冲突原因。立即替换和 ACK 后替换均显示 COMPLETED，没有 ACK 时间或确认标记，用户无法从审计判断是否收到过 Worker ACK。应展示活动请求的 lease/ACK 状态或明确区分完成方式，并保留对应持久化证据。相关位置：`TaskWorkspaceReplacementService.Replacement`、`V20__task_workspace_replacement.sql`、`src/main/resources/templates/ui/run-detail.html`。

## 审查范围和证据

- FD revision 8，来源事件 `FD-008-000008-review-requested`，Reviewer session `fd008-reviewer-20261007-7c9e42`；审查基线 `master`，审查提交 `2df52c7`。
- 阅读 FD、Worker r1、Tester r1、PM 测试决策、迁移 V20、替换服务与任务/attempt 仓储、Java/JavaScript/Python/Go Worker、Web Console controller 和模板；审查 `master...HEAD` 实现 diff。未发现 V20 文本中明显的 SQLite/PostgreSQL 专用 SQL 语法。行锁和待处理唯一索引为并发提供静态保护，但运行行为尚未验证。
- Tester 的 36 个适用场景全部未运行，覆盖 0/36、分支未测量；无测试授权、命令或原始运行结果。Worker 两次 compile-only 均在 JVM 启动时因内存不足失败，未进入源码编译。SQLite/PostgreSQL 迁移均未执行。PM 的例外只允许静态审查，不把任何未运行项记为通过。

实际运行的命令：`Get-Content`、`rg`、`git status --short`、`git log -6 --oneline`、`git diff --stat master...HEAD`、`git diff master...HEAD -- <实现路径>`、`git rev-parse HEAD`、`git rev-parse master`、`aiw fd claim FD-008 FD-008-000008-review-requested --session fd008-reviewer-20261007-7c9e42`。未运行测试、编译、构建、迁移、格式化、覆盖率、应用服务或网络请求。

剩余风险：状态竞争、旧租约 fencing、Worker 中断与清理、UI 授权及双数据库迁移缺少运行证据；本轮发现的问题须由 Worker 修复并再次提交独立审查。静态 SQL 检查不证明迁移成功。
