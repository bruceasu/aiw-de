# FD-023 独立设计评审

<!-- aiw-data: FD-023-design-review.json -->

结论：**changes-needed**。需要补全两个现有控制台 API 根的迁移及认证设计，然后才具备 design-ready 条件。

本次是有界、只读的设计核验，不是实现评审，也不产生 Reviewer 验收事件。关联真实 Planner 事件 `FD-023-000002-design-requested`；独立评审会话引用 `fd023-design-review-20261021-6e85bc`，未 claim 主 Planner 的事件。

## 发现

**D1，高，阻断：四个控制台 API 根的清单不完整。** FD Solution 1–2 只点名 goals、secretary、delegation、task-outputs，但现有 `WorkflowPlanController` 是 `@RestController`，基础路径为 `/ui/workflow-plans`（24 行）；`WorkflowEditorController` 的 `/ui/plan-editor/**` 方法明确使用 `@ResponseBody`（136 行起）；`WorkflowQuestionController` 则是 `/ui/plan-editor/plans/{planId}/questions` 的 `@RestController`（19 行）。这些是 JSON API，不是需保留路径的 HTML 页面。仅按当前列举实施会留下 `/ui` API，违反全 API `/api/r1` 的验收；若只迁移路径而仍仅保护四个根，原受 `/ui/**` 拦截器保护的读接口会丢失登录边界，写接口也会丢失原有 CSRF 或遭通用管理权限误限。

建议将 `/api/r1/workflow-plans`、`/api/r1/plan-editor` 明确加入同一控制台路径分类。将 `WorkflowEditorController` 的 HTML 页面 `/ui/plans/...` 与 JSON 方法拆分路径，将问题 API 一并迁移到新的 plan-editor 根；原有 session、CSRF、角色、owner 行为继续保留；通用 DelegationManagement 的排除清单同步覆盖这六个根。同步 `workflow-editor.js`、`plan-questions.js` 及相应当前文档、测试 URL，并在验收中明确 `/ui` 下不存在正常 JSON/文件 API 映射。HTML 表单返回页面或 redirect、页面错误处理不应机械迁移。

## 已确认事实

- FD 已明确无旧路径 alias、外部客户端需同步切换，并保留 Worker 协议内部 `/v1`，兼容政策清晰。
- 当前 WebConsole 拦截器只注册 `/ui` 和 `/ui/**`；迁出 UI 的 API 确实需要额外注册，并需要同时修改 interceptor 内部的路径判定。
- 通用 DelegationManagement 在代理首次启用前为管理写入创建 ACTIVE receipt。首次启用 API 若也走该逻辑，会把自己的请求计入在途记录；FD 对已受 WebConsole 保护的根排除它是合理方案。
- DelegationController 中存在 Approver 可用的授予操作。保留 controller 的 action、writer 与 owner 校验，避免额外统一要求 Operator，符合现有权限契约。
- FD 明确使用应用相对路径、确定 interceptor 顺序，并同步 Worker bearer、专用身份以及机器白名单，覆盖当前已知认证迁移风险。
- `RecommendationConsoleController` 的正常 GET 返回 HTML、POST 返回 redirect；唯一 `@ResponseBody` 是 SecurityException 处理器，因此 `/ui/system/recommendations` 保持原路径，不需要增加推荐设置 JSON API 根。`WorkflowPlanController` 的所有正常方法则属于 RestController JSON API，应整体迁到 `/api/r1/workflow-plans`；问题 board、文件 preview 和 answers 均随 plan-editor 根迁移。

## 假设与未验证

本评审按用户要求将“API”解释为本系统正常 JSON/文件接口，HTML 页面和返回 redirect 的表单路径维持 `/ui`。未执行测试、编译、服务、HTTP、浏览器、网络、状态命令或子代理；认证顺序、非空 context path 和首次启用并发行为仅做静态设计分析，不能视为运行通过。当前无实现 diff，最终实现仍需独立 fd-review。

本次实际工具范围为 `Get-Content`、`rg`、`Select-Object`、`Select-String` 的本地读取以及 `apply_patch` 写入这份报告与 JSON。读取中有两处错误路径和一个不存在的可选 prompt，已据文件清单纠正到实际包路径；未因此执行运行验证。
