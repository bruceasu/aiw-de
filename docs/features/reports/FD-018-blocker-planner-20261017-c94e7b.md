# FD-018：planner 配置含义待明确

<!-- aiw-data: FD-018-blocker-planner-20261017-c94e7b.json -->

- 阶段 / 角色：Design / Planner。
- 来源事件：FD-018-000002-design-requested。
- Session：fd018-planner-20261017-c94e7b。
- 状态：unresolved。

## 事实与未知

用户已明确批准 ISSUE-00005、晋升并执行新 FD auto with test。CLI 已完成 APPROVED，创建 FD-018；Planner 已领取来源事件。源文档曾存在捕获摘要漂移，已通过独立 draft 重新 capture；当前 `aiw issue show ISSUE-00005 --json` 返回 APPROVED。

需求明确管理员统一设置 planner、model、effort，但未定义 planner 是 CLI/Runtime、指定 Worker，还是现有 AI Profile。现有代码只固定 implementation-planner 角色及 EXTERNAL_WORK policy。不同含义会改变调度、可用性、guidance 和 start 校验，不能当作普通实现细节自行选择。当前已通过会话问题提供三个选项，等待用户回答。

已完成其余需求的设计草案，并启动一次只读独立设计审阅；尚未设计就绪，未开始实现、测试、编译、worktree 或提交。测试、模型结果及运行行为均未验证。

## 下一步

收到 planner 含义后更新 FD 和测试命令计划，整合独立设计发现；满足就绪条件后继续已授权自动流程，无须再次要求用户批准 Issue、晋升或 auto。

本 Gate 依据 fd-workflow 的 `Stop for a material %% NEEDS_INPUT decision`。它不撤销实现授权，也不要求重复确认已确定规则。

## 可复用改进

后续类似 Issue 的配置字段应提供一个明确取值示例及拥有者，区分执行 Runtime、Worker 实例、角色 Skill 和 AI Profile，避免工程阶段重新解释术语。本次不扩大范围修改开发 Skills。

所请求的两个 blocker 模板在仓库 `docs/templates/` 不存在；本报告沿用既有归档中的 `aiw.fd.evidence.v1` 双格式结构，并如实保留缺失情况。

## 解除记录
2026-10-17：用户明确选择现有 AI Profile（Runtime、模型、强度组合）。状态 resolved，继续原 auto with test 授权。
