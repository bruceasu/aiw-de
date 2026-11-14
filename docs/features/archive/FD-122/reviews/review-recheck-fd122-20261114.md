# FD-122 ASK 路由修复聚焦复核

- **来源事件：** `FD-122-000005-implementation-ready`
- **审查会话：** `fd122-review-recheck-20261114`
- **审查差异：** `b4aa3d7d..f106769e`
- **结论：** Verification passed

## 复核结果

- 首轮报告的问题已修复。`WorkflowRuns.startedNode` 同时检查 `run.visits` 和目标节点已有的 `DISPATCHED`/`QUESTION` 事件。`pendingNode` 使用它拒绝重新选择已提问的 ASK 节点；`visitedRoute` 也使用它拒绝新增指向这些节点的路线。若候选保留原有目标，判定不把已有路线当作新路由，符合 Worker 报告记录的边界。
- 确认仍会复核完整候选集合：`decideProposal` 在同一事务中加载并锁定执行记录、检查版本和 proposal ID、要求 `proposalView` 为 READY，然后将 `view.changes` 的所有原节点 key 交给 `pendingNodes`。只有所有节点仍满足待选条件后，代码才构造更新后的 Spec、记录应用事件并一次写回。
- `b4aa3d7d..f106769e` 的实现源码差异仅修改 `WorkflowRuns.java` 中这项共用判定及其两个调用点；其它差异是 FD 状态／验收说明、索引和 Worker／Reviewer 文档，没有无关实现改动。

## 证据与命令

读取了首轮 [P2 报告](review-fd122-20261114.md)、[Worker 修复报告](../reports/worker-fix-20261114.md)、当前 FD Verification 和相关 `WorkflowRuns` 调用路径。实际执行命令：`aiw fd show FD-122`；`aiw fd claim FD-122 FD-122-000005-implementation-ready --session fd122-review-recheck-20261114`；`git diff --name-status b4aa3d7d..f106769e`；`git diff --stat b4aa3d7d..f106769e`；`git diff b4aa3d7d..f106769e -- src/main/java/me/asu/aidev/workflow/application/WorkflowRuns.java src/main/java/me/asu/aidev/workflow/protocol/http/WorkflowConsole.java src/main/resources/static/ui/workflows.js src/main/resources/templates/ui/workflows.html docs/features/FD-122/FD-122_PROPOSE_COORDINATED_UPDATES_TO_PENDING_WORKFLOW_.md docs/features/FD-122/reports/worker-fix-20261114.md`；`git diff b4aa3d7d..f106769e -- docs/features/FD-122/index.md`；`git diff -- docs/features/FD-122/FD-122_PROPOSE_COORDINATED_UPDATES_TO_PENDING_WORKFLOW_.md docs/features/FD-122/index.md`；`git diff --check`。

本轮未运行测试、编译、`node --check`、服务、模型、迁移或浏览器。Worker 报告记载的离线编译结果未由 Reviewer 重跑；运行时行为仍未验证。