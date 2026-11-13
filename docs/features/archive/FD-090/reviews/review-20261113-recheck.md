# FD-090 定位与权限修复聚焦复核

- 结论：verification-passed（独立静态复核通过，不代表页面或运行验收通过）。
- Reviewer：`codex-review-fd090-20261113`；来源事件 `FD-090-000004-implementation-ready`，已认领；FD Revision 4。
- 修复提交 `d2dc072247327838b4b4fde6aed0797b189c1054`，基线 `06ff53c`；[首轮报告](review-20261113.md) 保留。

## 修复结论

三项 finding 均已解决，无剩余阻塞。

- native 深链接通过 deferGuarded 在专业区 refresh 入口提前返回，展示明确提示并经 sync 禁用无选择的任务/输入控件，不发送默认专业任务读取请求。只有用户明确选择 guarded 来源才解除等待并使用原专业选择流程，避免同页自动选择另一工作。
- worker-run 同时识别 explicit native/guarded 参数并设置控制区来源，guarded 的 task/Attempt 选择继续来自原专业区，原控制身份对齐检查保留。
- 精确 visiblePlan(id) 先确认当前人类 Owner，再验证 Plan 的实际 createdBy 与当前 actor 一致；目录读取将已核验摘要补入展示列表，不再把100条展示上限当授权边界。原 task/Attempt 查询限制与实际读取/控制的 Owner 校验没有放宽。
- 关闭 drawer 时原行不存在则回退焦点到搜索框，局部修复了无返回目标的情况。

## 命令与边界

实际执行：`aiw fd show FD-090`，对相关 Java/JS 的 `git show --format=short d2dc072`，FD Verification 的 Get-Content/Select-Object；`aiw fd claim FD-090 FD-090-000004-implementation-ready --session codex-review-fd090-20261113`。以 `aiw fd emit FD-090 verification-passed --producer reviewer --artifact docs/features/FD-090/reviews/review-20261113-recheck.md --source-event FD-090-000004-implementation-ready` 记录结论。

Worker 记录修复后离线 Java compile 与三个 JS 的 node --check 通过；Reviewer 未复跑。未运行测试、浏览器、数据库查询验证、迁移、服务、网络或模型，未创建子 Agent，未修改产品源码，仅写本报告与 Verification。实际跨来源切换、旧 Plan 直达、焦点与权限请求未运行验证；沿用首轮静态证据和运行限制，不新增架构或测试门禁。
