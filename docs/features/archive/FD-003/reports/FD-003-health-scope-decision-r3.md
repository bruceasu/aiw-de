# FD-003 健康与告警范围决定

<!-- aiw-data: FD-003-health-scope-decision-r3.json -->

- 来源事件：`FD-003-000003-needs-decision`，FD revision 3。
- 决策人：用户；来源为本会话 2026-10-05 的明确回复。
- 原文：**M3 仅交付可观测心跳/容量/租约事实，不包含自动 healthy/unhealthy 分级或告警**。
- 决定：展示权威快照与时间、容量、占用及租约事实；不定义健康阈值、自动分级、告警或处置规则。
- 技术设计：Planner 依据现有 `FleetDashboard`、`JdbcFleetDashboardRepository` 与 Web Console 路径确定 Task→Run 关联、READY 主原因与并列限制、稳定排序、前 100 条快照范围及动作反馈，详见 FD revision 4。
- 实施授权：此前用户请求 `$fd-workflow FD-003 auto`；本回复解决设计范围门槛。用户明确要求不做测试。
- 本决定不授予测试、浏览器、网络或最终构建预算。
