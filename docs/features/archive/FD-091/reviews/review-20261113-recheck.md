# FD-091 修复聚焦复核

- 结论：verification-passed（独立静态审查通过；不代表运行验证通过）。
- Reviewer：`codex-review-fd091-20261113`。
- 来源事件：`FD-091-000004-implementation-ready`，已由本 Reviewer 认领。
- FD Revision：4。
- 修复提交：`c3e16b97e1ebe1841764251792f35be9f319eb2c`；基线 `bb2e3804224ee283f0324a46a711c7a6835bf0c2`。
- 本次仅复核首轮报告 P1/P2 及关联链路；首轮完整审查记录保留于 [review-20261113.md](review-20261113.md)，共同覆盖 FD-091 实现。

## 复核结果

未发现尚未解决的阻塞项。

1. P1 已修复。`TaskExecutionService.acknowledgeStop()` 保留原有精确租约、静止证据、停止 ACK、资源释放与历史记录，然后在相同事务中调用 `WorkflowRuns.stopped(taskId)`。路由仅处理 STOPPING 且当前任务匹配的统一工作流，将刚释放的 READY 任务原子取消、结束底层 Plan 并保存 STOPPED 事件。事务提交后不残留可领取的 READY 任务；失败时整体回滚。未设置旧 Plan control 时既有 `afterStopAcknowledged()` 直接返回，不干扰该回调。重复 ACK 由既有 ACK 记录拦截；实际完成或原生 CANCEL 先到达的路径仍可通过原有终态查询收敛，且不派发下一节点。`endPlan()` 对相同目标状态幂等。
2. P2 已修复。`workflows.html` 在各内容面板之外显示“只读文本任务与判断，不修改文件、不退款或发送消息”的共用说明；`workflows.js` 为相同键提供中文、日文，英文为默认文案，沿用已有翻译机制。

上述均为代码及事务边界的静态证据，没有执行任务或真实停止实验。

## 实际命令与未运行检查

实际执行：`aiw fd show FD-091`、`git show --stat --oneline c3e16b9`、修复提交的定向 `git show`、相关源码的 PowerShell `Get-Content`/`Select-Object` 与 `rg`、`git status --short`、FD 的 `git diff`；认领命令为 `aiw fd claim FD-091 FD-091-000004-implementation-ready --session codex-review-fd091-20261113`。本报告与 Verification 写入后以 `aiw fd emit FD-091 verification-passed --producer reviewer --artifact docs/features/FD-091/reviews/review-20261113-recheck.md --source-event FD-091-000004-implementation-ready` 记录结果。

Worker 已记录修复后的离线 Maven compile 与 node --check 通过；Reviewer 未复跑。未运行测试、构建、服务、数据库迁移、浏览器、外网或模型，未新建子 Agent，未修改产品源码。

剩余未验证范围沿用首轮：真实 Worker 循环、数据库迁移及并发事务实际行为、售后示例和浏览器交互未执行。本次授权接受静态审查，不新增测试门禁。完整知识库、动态改图与写文件能力不在本 FD 范围。
