# 安全、Authority 与信任边界

## 1. 当前定位

R8-S13 当前适合本地和受信任内部环境。它具备较强的工程 Authority/Ownership 控制，但 **还没有完整的 Production Edge Security**。

必须区分：

- 工程 Authority；
- Network Authentication/Authorization。

当前前者已经较完整，后者仍需生产加固。

## 2. Worker Protocol

设置 `AIW_WORKER_TOKEN` 后，所有 `/api/worker-protocol/...` 请求必须：

```text
Authorization: Bearer <token>
```

但这个 Filter **只保护 Worker Protocol**。

## 3. Management API 当前限制

以下 API 当前没有统一 Spring Security/RBAC：

```text
/api/r8/requirements
/api/r8/runs/...
/api/r8/fleet/...
/api/r7/approvals/...
```

所以：

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

R9 对 `/ui/**` 支持可选 Session Login、CSRF，以及 VIEWER/OPERATOR/APPROVER/ADMIN 角色。该机制只属于浏览器 Console 边界，不会自动给已有 Management REST API 增加 Auth/RBAC；因此默认 localhost 绑定仍是当前最安全的本地模式。
