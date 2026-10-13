# FD-017 独立审查：请求修改

<!-- aiw-data: FD-017-review-r1.json -->

Reviewer Session：`fd017-reviewer-20261013-7c9e41`；来源事件：`FD-017-000005-implementation-ready`。审查 `master...feature/FD-017`，提交 `dbe72bc`，对照 FD-017 Revision 5、Worker 报告及 FD-016 角色/结果契约。

## 发现

1. **中：推荐结果未严格限制为单个 JSON 对象。** `WorkflowRecommendationService.java:98` 使用默认 `ObjectMapper.readTree(summary)`。默认解析未启用尾随 token 检查，因此完整合法对象后附加第二个对象或文本时，前一个对象仍可能成为 `READY_TO_APPLY`。这与 FD 中“严格解析单个 JSON 对象”及验收 3、4 不符。请启用并核实尾随 token 拒绝，保留现有结构、关联、长度校验。
2. **中：请求创建期间编辑目标会留下过期推荐请求而没有可用入口。** `workflow-editor.js:54-58` 的 `markEdit` 只有存在轮询定时器时才显示过期提示；`workflow-editor.js:192-198` 在 POST 未返回时定时器尚未创建，编辑目标后仍显示“Requesting…”；POST 回来后直接 `return`，既不轮询也不显示已有请求 ID 或手动查看/收回按钮。用户已发起的有费用分析可能继续运行，却无法从本页查看/收回，且旧请求没有标为过期。请在任何输入变更时标记当前请求过期，并在迟到 POST 返回后保留可访问的旧请求状态或明确提供查看/收回入口，不能让该响应覆盖新输入或手改。

## 其余验收的静态证据

三套模板和编辑器使用 FD-016 的角色能力及 `skillRef`；服务端限制执行角色组合、代码工作区/完整 revision 及同工作区直接代码结果依赖。`WorkflowPlanService` 的草稿只入库计划，确认版本后才创建有依赖的任务；领取时 `TaskClaimService` 从前置结果构造 `TaskInput`。新页面路径与原 `/ui/workflow-plans` JSON API 分离；Web Console 拦截器及 controller 沿用会话、Operator 和查询参数 CSRF。详情从真实任务与报告读取，静态模板及导航沿用 Console 样式。上述判断仅基于源码，未推断运行效果。

实际执行：读取 FD、Issue/FD-016 相关契约、Worker 报告、`git diff master...HEAD`、相关 Java/JS/模板/配置及 `aiw fd show`；用本 Session 领取事件；查看 `aiw fd emit --help`。未运行测试、浏览器、HTTP、模型、网络、数据库、编译、lint、最终构建或部署。Worker 报告称两次离线 Maven compile 均成功；最后的 JS 修改未经过该编译检查，且 Maven 不验证浏览器行为。真实任务链和模型输出质量仍未验证。

结论：`changes-requested`。修复上述两点后对当前差异重新做独立审查。
