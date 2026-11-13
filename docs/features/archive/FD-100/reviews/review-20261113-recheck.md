# FD-100 成员选择保留聚焦复核

- 结论：verification-passed（独立静态复核通过，不代表运行验收通过）。
- Reviewer：`codex-review-fd100-20261113`；来源事件 `FD-100-000004-implementation-ready`，已认领；FD Revision 4。
- 修复提交 `321bf3e0e5987eb76b767beba2d6ce2a8d66bbfb`，基线 `b49a737`；[首轮报告](review-20261113.md) 保留。

P1 已修复，无剩余阻塞。简单任务的 memberId 改为显式读取成员 select.value，已选 disabled 选项不再因 FormData 规则遗漏；只有用户明确选择空值才发送 null。非 ACTIVE 或已归档/未知成员继续带原 ID 交给既有服务端归属与 forRun 校验，不静默改为未指定成员。目录加载失败保留原选择的代码未改变，节点直接保留 memberId 的路径也未改变。新错误词条覆盖中英日，现有 act 经 t 显示静态拒绝原因，未增加自动重试或改派。

实际执行：`aiw fd show FD-100`、`git show --format=short 321bf3e -- src/main/resources/static/ui/workflows.js`、FD Verification 的 Get-Content/Select-Object；`aiw fd claim FD-100 FD-100-000004-implementation-ready --session codex-review-fd100-20261113`。以 `aiw fd emit FD-100 verification-passed --producer reviewer --artifact docs/features/FD-100/reviews/review-20261113-recheck.md --source-event FD-100-000004-implementation-ready` 提交结论。

Worker 记录 node --check 通过，本次没有 Java 变更；Reviewer 未复跑编译。未执行测试、迁移、浏览器、服务、数据库、网络或模型，没有新子 Agent，没有修改产品源码，仅写本报告与 Verification。真实表单提交与成员停接单并发尚未运行验证，首轮静态审查及其运行限制继续适用。
