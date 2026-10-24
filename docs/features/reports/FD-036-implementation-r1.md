# FD-036 Worker 实现 r1

<!-- aiw-data: FD-036-implementation-r1.json -->

Worker Session：secretary-desktop-20261024-a6529740；真实来源 FD-036-000003-design-ready。比较基线 3d8f026，源码 70cc7bd，主工作区范围提交，独立实现审阅失败数 0。

## 结果与边界

列表默认当前目标，每行直接删除；已删除视图每行恢复。视图按钮显示最近接口返回集合数量，提供刷新、空状态、选中标记。详情删除/恢复复用同一函数与原 revision。确认包含目标名称、可恢复含义、旧回复失效与执行计划保留；当前未发送消息若会丢弃，也明确确认。取消不写入，未保存目标/编辑需先处理，删除请求期间锁定输入。

删除已确认成功就先更新本地列表；清空被删当前详情，但不切换其它当前目标。刷新失败单独提示，失败/未知写请求不自动重复。恢复后到当前目标查看。补充本页 hidden 样式，避免共享按钮/布局样式覆盖隐藏状态；所有目标标题经 textContent，不拼接 HTML。docs/current-zh/GOAL-WORKSHOP.md 更新使用步骤。

只改三个本页资源与使用说明，不改 GoalController、GoalDiscussionService.deleted、GoalRepository 或 V7。既有 actor、CSRF、revision 和 epoch 沿用，附件/正式版本/执行计划/工作区不永久删除。UI 入口复用既有删除语义，没有新增稳定能力契约、数据库迁移或权限设计，因此不另做设计审阅；最终独立实现审阅仍须真实完成。

## 实际证据

静态读取目标模板/共享样式、列表/详情状态与原删除/恢复调用链、Controller/Service/Repository 归属及乐观版本、V7 deleted/epoch 事实。执行 git diff --check -- 本次路径和最终范围 diff 检查，通过；node --check src/main/resources/static/ui/goals.js 通过，源码与 70cc7bd 相同。前期辅助资料路径及 rg glob 错误没有作为通过证据，未重复未改失败命令。

未改 Java，未执行 Maven。未运行测试、Thymeleaf/浏览器、数据库/迁移、服务/HTTP/真实删除或恢复、模型、网络/下载、最终构建/打包、安装、重启或发布。旧列表最多 100 条的限制明确展示，没有宣称全库计数。运行交互、布局和错误恢复仍未验证。

排除其它正在修改的秘书/Python/模型/启动器、专业任务/配置 Java、authority 路由、configurations/questions/fragments 模板；不得整工作区暂存。无未解决 blocker。下一步由独立 Reviewer 核对 3d8f026..70cc7bd 实际变更与全部验收，通过后由 host 正常范围提交审阅证据并归档。
