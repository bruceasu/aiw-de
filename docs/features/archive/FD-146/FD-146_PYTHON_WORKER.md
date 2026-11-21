# FD-146: 对齐 Python Worker 模型连接的执行状态

**Status:** Complete
**Revision:** 7
**Review policy:** Independent
**Priority:** Medium
**Evidence policy:** Markdown

## Problem

API Key 和 ChatGPT 连接已经共用 Python Worker、任务上下文、SDK Runner、工具执行和最终报告校验。ChatGPT 的模型适配层却可能把普通 JSON 交付判为工具调用错误，还会在第一个有效模型回合前将运行标为已开工。

## Options and decision

保留现有 Runner。统一有效模型回合的状态边界，并在 ChatGPT 模型适配器中区分纯 JSON 交付和工具协议。真实对照仍按离线基线约定记录在线数据。

## Solution

普通 JSON 对象和数组原样返回到 SDK，动作封装仍按同一封闭规则验证。含 `action` 字段的报告使用已有 `finish` 封装。流式与按回合的连接都在校验出有效结果后记录已开工。未确定的远程结果保持原有不确定状态。工具权限、取消信号和完成检查沿用同一执行链。

OAuth 保留明确拒绝的 HTTP 分类。已知的模型偏好拒绝只保留固定诊断文字。普通任务走共用的 1 MiB 报告门槛，OAuth 流另限制传输体积，并允许终止事件包含完整报告和有限的缩短空间。纯模型调用保留较小的独立报告预算。

## Work items

- [x] 1.1 校验模型连接、宿主循环和实际工具责任边界。
- [x] 1.2 修复普通 JSON 交付和已开工状态的连接差异。

## Acceptance

两种连接都能交付结构化报告。有效工具调用仍进入 SDK 及既有宿主校验，无效动作失败。已开工状态取决于成功的有效回合，无法确认的调用不进入配置回退。

## Verification

Worker 编译及 `python -m unittest test_api_connection` 的九条离线用例通过。新增用例模拟 OAuth 原样交付 JSON、工具声明校验及流式和按回合的状态一致性。未调用真实模型或联网。

独立静态审查要求修正 OAuth 已知拒绝分类、普通报告预算和既有用例的构造状态。见 [FD-146-000002 审查报告](reviews/FD-146-000002-review.md)。

上述问题已修正。Worker 编译及 `python -m unittest test_api_connection test_oauth_sdk_model test_evidence_usage` 的 20 条离线用例通过，覆盖原样交付 JSON、工具校验、两种传输的开工状态、HTTP 安全分类和 1 MiB 报告。未调用真实模型或联网。

复审指出的两个余留分支已修正。流式和回合执行都只在首个有效结果前遇到已确认的 400、404、422 偏好拒绝时才允许回退。OAuth 诊断正文中断也保留原 HTTP 状态。Worker 编译及同一组 21 条离线用例通过。

独立复审确认报告预算和用量用例已修正。要求在共有回退路径过滤未明确拒绝的 HTTP 状态，同时在读取 OAuth 可选错误正文失败时保留已取得的状态码。见 [FD-146-000004 复审报告](reviews/FD-146-000004-review.md)。

独立复审通过。已核对未确认请求不进入连接回退，OAuth 诊断截断保留已取得的拒绝状态。见 [FD-146-000006 复审报告](reviews/FD-146-000006-review.md)。

## Sources

- [改进清单](../../../AGENT-IMPROVEMENT-BACKLOG.md)
- [质量对照](../../../WORKER-QUALITY.md)

**Completed:** 2026-11-21
