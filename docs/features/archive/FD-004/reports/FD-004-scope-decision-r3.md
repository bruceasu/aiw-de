# FD-004 制品与审批历史范围决定

<!-- aiw-data: FD-004-scope-decision-r3.json -->

- 来源事件：`FD-004-000003-needs-decision`，FD revision 3。
- 决策人：用户；来源为本会话 2026-10-05 的明确回复。
- 原文：M4「仅展示已存 summary/verdict/revisio」；审批历史「仅有界最近列表」。按上下文将 `revisio` 解释为 `revision`。
- 决定：仅展示已存 summary/verdict/revision；`publicationRef` 可作为已存标识，但不读取外部文档、Git 全文或下载。审批历史只提供有界最近列表，不增加完整历史分页或保留策略。
- 技术关联：Planner 基于现有 `approval_request`、`execution_attempt` 和 gate 读取，建议同时匹配 source task、result revision 与 gate 关闭时间；详见 FD revision 4。
- 实施授权：用户随后明确请求 `$fd-workflow fd-004 auto`，按 FD 生命周期继续。
- 本决定不授予测试、浏览器、网络或最终构建预算。
