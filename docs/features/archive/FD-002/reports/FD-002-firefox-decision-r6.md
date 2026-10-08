# FD-002 人类决定：延期 Firefox 验证

<!-- aiw-data: FD-002-firefox-decision-r6.json -->

2026-10-05 用户针对 FD-002 明确回答「允许 FD-002 的 Firefox 检查延期」。
批准引用：`approved:conversation:call_GbKQIKiYeysoTKpcQOL96T3G`。

范围仅为本轮 Firefox 图/布局及刷新保持的运行验证，允许根据本地 Chromium
四种尺寸和实际测试结果继续独立审查；不减少页面功能要求，不移用 FD-001 的
决定，不下载 Firefox，也不把未运行记录为通过。

两项 Firefox 场景仍在 104 项库存的分母中，结果保持 uncovered，兼容风险保留。
源码分支、真实 Worker 租约等其他缺口不由此决定免除。

记录绑定当前 FD revision 6、来源事件 `FD-002-000006-implementation-ready`，
摘要 `7869eb2108ad9ac8811e65ea339ecde7d6c08d7394add7230ab85a2e5a178c8a`。
不在运行中修改 FD 内容，以维持本次测试校验；本次运行结束后、发布 Tester
交接之前，将 Verification 的 Firefox 策略同步到 FD，再请求独立 Reviewer。

记录人 `fd002-planner-20261005-96860f94`，时间 `2026-10-04T22:36:24+00:00`。
