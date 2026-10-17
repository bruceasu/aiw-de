# FD-018：App Server Fast 兼容修订

<!-- aiw-data: FD-018-blocker-profile-fast-20261017-c94e7b.json -->

- 阶段 / 角色：独立实现评审返修 / Worker。
- 来源事件：FD-018-000005-changes-requested。
- 状态：resolved；实际修正与独立回归通过，整体实现复审仍走正常 Reviewer 交接。

评审第 1 轮 R1 发现：控制面允许 codex-app-server + fast=true，但现有 Go chooseAIProfile 仅 codex-cli 接受 fast，导致错误的就绪判断和领取后拒绝。既有 9 项聚焦测试没有覆盖此组合，不能用通过计数否定实际缺陷。

Worker 已领取真实返修交接，保守要求推荐的 codex-app-server profile fast=false，保持 Go/TypeScript 共同支持范围；不修改普通任务策略或 Runtime，不静默回退。错误信息列出不兼容 Profile 的 runtime/fast/effort，并说明限制。将由原独立 fd-test 会话补回归，再执行一次原限定命令，之后重新发起独立评审。未更改原测试 oracle 或删除旧报告。

可复用改进：新增跨 Runtime 配置判断时以各实际 Worker 选择器的共同集合为依据，并测试同一 Runtime 下会影响资格的字段，不能只按 Runtime 名推断。

解除证据：修复提交 27f17259，独立验收 r2 的 A09 及原 9 项共 10 项通过，exit 0、8.964 秒，无新增意外失败；详见 FD-018-acceptance-r2.md。本反馈的解除不冒充 Reviewer pass。
