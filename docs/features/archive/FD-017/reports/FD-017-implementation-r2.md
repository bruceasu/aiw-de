# FD-017 Worker 审查修复报告 R2

<!-- aiw-data: FD-017-implementation-r2.json -->

Worker Session `fd017-auto-20261013-df9cdf16`，来源 `FD-017-000006-changes-requested`；对照独立审查 `reviews/FD-017-review-r1.md` 的两项中风险发现。

1. `WorkflowRecommendationService` 的 ObjectMapper 开启 `FAIL_ON_TRAILING_TOKENS`，使 `readTree` 拒绝首个 JSON 对象之后的额外 JSON 或文本，原有关联、来源、长度和可信候选检查保留。
2. 编辑目标或步骤时，无论推荐创建 POST 是否已返回，均立即标记当前请求过期。迟到 POST 返回后仍把任务 ID 放入本页历史区域，提供单独的查看/排队收回按钮；旧结果不覆盖新输入或新建议。多次请求在当前页面均保留入口。页面卸载后不保留客户端历史；任务及报告仍按服务端任务 ID 保存。

修复后实际运行：`mvn -o -q -DskipTests compile` 退出 0；`node --check src/main/resources/static/ui/workflow-editor.js` 退出 0；`git diff --check` 退出 0。以上为编译/语法和静态证据，未运行测试、浏览器、HTTP、模型、网络、数据库迁移、最终构建或部署。真实推荐输出与并发状态仍未经运行验证，交独立 Reviewer 重审。
