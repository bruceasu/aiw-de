# FD-120 P1 聚焦复核

- Reviewer：`codex-review-fd120-20261114`
- 来源：`FD-120-000004-implementation-ready`，已独立领取。
- 范围：`f260436d..3d7dd197` 的选型修复与直接启动链。
- 结论：**Pass**。首轮唯一 P1 已关闭，保留一次失败报告。

讨论任务不再先被 AuthorityTaskPolicy 的全局 catalog 版本核验；后续仍执行正式 `GoalTaskPolicy.select`，按已与冻结成员范围取交集的 Frozen profiles/version 校验，并将真实选择附到 attempt。范围外 ID 仍拒绝。非讨论任务保留原 selection；worker 身份、租约、beforeStart/starting、执行允许和停止检查未被移除。NativeQuestions 的全局 guidance 仅在其原本实际适用的 flexible-plan 类型构造，避免无关讨论提前计算错误上下文。本修复范围未发现新的阻塞。

新增 DiscussionMemberStartTest 源码覆盖实际 start 路径、工厂版本可用及范围外选择拒绝；Reviewer 仅静态读取，未执行。Worker 已记录修复后离线 test-compile 成功，不能据此宣称运行通过。

实际操作：`aiw fd show/claim`、定向 `git diff`、`Get-Content`、`rg`。未运行测试、编译、验证脚本、浏览器、API、SDK、服务、网络、安装或子代理；未改实现。真实讨论与页面交互仍未经运行验收。
