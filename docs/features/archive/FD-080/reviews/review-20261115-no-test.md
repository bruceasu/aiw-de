# FD-080 独立静态审查

- FD：FD-080，实际审查版本：4
- 来源事件：`FD-080-000004-review-requested`
- Reviewer 会话：`fd080-reviewer-no-test-20261115`
- 审查基线：`5293cae0d30a0f47874fb229e4b0d5dcf0d84803` 加当前工作区差异
- 结论：**PASS（静态审查；按用户要求未运行测试）**

## 发现

未发现阻断 FD-080 验收的实现问题。

审查事件的自由文本理由写了 revision 3；实际回执的 `fd_revision` 是 4，当前 FD 文件头也是 revision 4，文件 SHA-256 与回执中的 `fd_sha256` 一致。本审查以 revision 4 和该文件哈希为准。这是非阻断的元数据文字不一致。

## 核对结果

- `WorkflowPlanSpec.Step` 将缺失策略解释为 INDEPENDENT/UNKNOWN；HIGH/UNKNOWN 与 SELF/MILESTONE 的组合会被拒绝。缺失字段保持为空且序列化时省略，既有确认规范不会因读取再编码而被静默改写。
- 草稿编辑由 DRAFT 状态和版本条件约束；确认时先验证规范，再转为 CONFIRMED 并创建首个执行任务。Worker 报告不能改变确认后的策略。
- `WorkflowStepCompletedHandler` 只让实际 PASS 的 SELF 结果推进；INDEPENDENT 和 MILESTONE 会创建评审任务，评审不通过会进入受限修正流程。`WorkflowPlanService.acceptedInputs` 和最终完成门禁使用当前策略对应的实际结果，未通过或缺失的结果不能伪装成接受。
- `WorkflowReviewPolicy.scope` 让里程碑覆盖上一个评审边界后的连续 SELF 步骤及当前步骤。评审任务包含这些步骤原始指令/验收条件和已接受成果；`WorkflowReviewClaimPolicy` 拒绝让执行过本次覆盖步骤的 Worker 领取该评审。
- 编辑器显示评审方式和风险；确认前展示将固定的策略，并在保存前核对 Plan 版本，避免确认过期内容。
- 本次删除的两个专属测试类及 `WorkflowWorkerProvisioningTest` 均未进入生产代码。删除后没有重跑编译或测试。`WorkflowWorkerProvisioningTest` 与 FD-080 无关，删除它会移除独立的 Worker provision 回归覆盖；这是用户明确要求的变更，作为未验证风险记录，不构成 FD-080 生产行为缺陷。

## 验证边界

- 本 Reviewer 只进行静态审查。未运行测试、编译、构建、服务、模型、浏览器、迁移或网络命令。
- 实现方本轮先前的 Maven 命令报告 365 个生产源编译成功；随后测试编译在 `WorkflowWorkerProvisioningTest` 的四处参数数量不匹配处失败，未进入测试执行。该文件之后按用户要求删除，未重跑；这不构成测试通过证据。
- 用户明确取消本 FD 的测试执行并要求删除专属测试。因此本审查按该决定评估静态证据，并保留缺少运行行为证据的风险。

## 审查命令

- `aiw fd claim FD-080 FD-080-000004-review-requested --session fd080-reviewer-no-test-20261115`
- 只读检查：`aiw fd show`、`git status/diff`、`Get-FileHash`、`rg` 与源码/FD/稳定规范阅读。
- 未运行验证命令；本报告不会把编译或测试写成 Reviewer 执行结果。
