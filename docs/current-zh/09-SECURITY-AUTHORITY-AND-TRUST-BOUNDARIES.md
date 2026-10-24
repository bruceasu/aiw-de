# 安全、Authority 与信任边界

## 1. 当前定位

R8-S13 当前适合本地和受信任内部环境。它具备较强的工程 Authority/Ownership 控制，但 **还没有完整的 Production Edge Security**。

必须区分：

- 工程 Authority；
- Network Authentication/Authorization。

当前前者已经较完整，后者仍需生产加固。

## 2. Worker Protocol

设置 `AIW_WORKER_TOKEN` 后，所有 `/api/r1/worker-protocol/...` 请求必须：

```text
Authorization: Bearer <token>
```

但这个 Filter **只保护 Worker Protocol**。

## 3. Management API 当前限制

以下旧管理 API 未使用 Spring Security；这不代表应用层没有认证。自定义 MVC 拦截器保护 `/ui/**`，以及 `/api/r1/` 下六个控制台数据根（goals、secretary、delegation、task-outputs、workflow-plans、plan-editor），保留 session、CSRF、当前角色和所有者边界。

```text
/api/r1/requirements
/api/r1/runs/...
/api/r1/fleet/...
/api/r1/approvals/...
```

其余旧管理写入在首次代理启用前保留原本地信任策略；首次启用后持久要求人工 session、CSRF，审批决定需 Approver、删除需 Admin、其他写入需 Operator。普通旧 GET 保留原读取策略。Worker 租约操作保持机器认证，Drain/Offline 是管理写入并额外适用该持久规则。停用代理或重启不取消要求；激活后关闭登录会使控制台拒绝访问。这些自定义检查仍不是完整的生产边缘安全框架。

因此：

- 本地继续使用默认 `127.0.0.1`；
- 服务器部署应放在有认证的内部 Gateway/Reverse Proxy 后，或先补应用认证；
- 不要把 `0.0.0.0` 暴露到不可信网络并认为 Worker Token 会保护全部接口。

## 4. Task Ownership

Heartbeat/Complete/Fail 需要匹配 Task ID、Worker ID、Lease Token 和正确状态。Pause/Cancel/Lease Lost 后，旧 Worker 不应再能合法完成 Task。

## 5. Approval Authority

Approval 精确绑定 sourceTaskId、resultRevision 和 gate close time。AI Worker/Reviewer 不可以通过输出某句话来自行制造 Human Approval。

## 6. Telegram

启用时校验 Bot Token、Allowed User IDs、Approval Channel，并尽可能校验 Chat。最终 audit actor 为：

```text
telegram:user:<id>
```

Telegram 只是 Transport；ApprovalService 才是 Authority。

## 7. Git / Worker Security

Worker 可以运行 Git、构建脚本和 AI Runtime。因此应：

- 只给 Worker 所需 Repository 权限；
- 必要时使用独立 OS Account/Container；
- 谨慎设置 AIW_CODEX_NETWORK_ACCESS/Sandbox；
- 保护 Git Remote Credential；
- 不要把 Secret 写入 Task instruction/completionSummary；
- 视 Repository hook/build script 为可执行代码。

## 8. Secret

至少包括：DB password、Worker token、Telegram token、Git Credential、AI Runtime Credential。生产环境应该使用组织 Secret Manager，而不是写入 Repo。

## 9. Production Security 下一步

建议：Management API Auth/RBAC、TLS/mTLS/Network Policy、Requester/Operator/Approver 角色分离、Secret Rotation、Audit Retention、Worker Sandbox/Tool Permission、正式 Threat Model 与安全测试。

## Web Console 安全

个人秘书的[动作授权说明](../ACTION-PERMISSIONS.md)解释功能分组、可选业务范围、共享次数、旧能力迁入和原请求恢复。动作次数是人明确设置的上限，与默认仅记录的执行费用等预算分开。

所有 `/ui` 及 `/ui/**` 业务页面统一要求真实Session Login，并保留CSRF及VIEWER/OPERATOR/APPROVER/ADMIN角色。只有登录页和指定公共资源可匿名，旧enabled=false不再提供匿名认证或ADMIN。该机制只属于浏览器 Console 边界，不会自动给已有 Management REST API 增加 Auth/RBAC；因此默认 localhost 绑定仍是当前最安全的本地模式。

页头和侧栏角色标签按真实登录会话展示：未登录显示“未登录”，登录后显示当前角色。未登录会话不再获得内部ADMIN、actor或operator授权，不能访问平台业务页面；登录页仍可显示“未登录”。旧匿名数据保留原归属，不自动转给配置账号。
