# FD-005 实施报告 r18

<!-- aiw-data: FD-005-implementation-r18.json -->

- Worker session：`fd005-worker-20261005-69fe2eb5`
- 来源事件：`FD-005-000017-work-requested`
- 本轮 FD 修订：18；审查基点：`f5b65b4`；收尾修复已进入提交 `0e8b35c`

## 修改与原因

1. `WorkspaceSession.cleanup()` 在 Git 工作树删除失败时，记录 `workspace-cleanup: preserved` 后继续抛出原错误。成功删除后的 `completed` 证据移到异常处理之后，避免证据写入失败被误记为删除失败。
2. `runClaimedAttempt()` 仅在清理成功且资源终态已确认后标记工作区已清理；成功上报前的清理失败转入失败路径，不写 `ready-to-report`，不调用 `/complete`。该次失败的删除不在 `finally` 中重复尝试，以保留现场和原错误。
3. 更新公共契约，明确上述清理失败、终态证据与上报关系。此前已完成的 Work Item 勾选保留为历史源码进度；本轮针对 r13 静态审查 P1 修复。

## 证据与实际命令

- `aiw fd claim FD-005 FD-005-000017-work-requested --session fd005-worker-20261005-69fe2eb5`：领取与当前修订匹配的 Worker 交接。
- `node scripts/compile-worker-runtime.mjs`：exit 0；仅为 TS/JS/Python/Go 语法或编译检查，无保留的发布产物。
- `git diff --check -- workers/runtime-core/workspace.mjs workers/runtime-core/attempt.mjs workers/runtime-core/PUBLIC_CONTRACT.md`：exit 0；静态空白检查。修复已由工作区的 `Update` 提交纳入 HEAD，实际改动以 `git diff f5b65b4..0e8b35c -- <上述路径>` 核对。
- 静态对照 [r13 独立审查](../archive/FD-005/reviews/FD-005-review-direct-r13.md) 的 P1、[公共契约](../../../workers/runtime-core/PUBLIC_CONTRACT.md) 与 attempt 清理/上报调用链。r13 P2 是上报已接受后证据故障的对账风险，不作为本轮源码变更。

## 未运行与风险

本轮未运行测试、覆盖、类型检查、格式化、最终构建、模型或网络调用。编译通过不能证明 Git 删除失败的行为；独立 Tester 需仅通过 `workers/process-typescript-with-api` 的生产入口构造该场景，核对没有 `resource-terminal: settled`、`ready-to-report` 或 `/complete`。原 r5/r8/r11 测试与失败记录保持历史事实；本轮适用场景数和覆盖率待独立 Tester 重新确定。其他运行时、真实端点与全会话 G2 平台效果没有行为验证，正式 Reviewer 应分别列示风险。
