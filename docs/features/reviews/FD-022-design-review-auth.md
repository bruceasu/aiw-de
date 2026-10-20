# FD-022 管理写入认证增量设计评审

<!-- aiw-data: FD-022-design-review-auth.json -->

结论：**changes-needed**。本报告仅评估用户已决定的“首次启用代理时收紧旧管理写入认证”增量；来源为 Worker 事件 `FD-022-000003-design-ready`。不替代全 FD 实现 Reviewer，不产生验证事件。

## 发现

### AUTH1：关闭认证配置后仍可经 UI 管理入口绕过持久认证（高）

事实：`DelegationManagementMvc` 仅注册 `/api/**`；持久 flag 为真而认证配置为 false 时，这些入口会拒绝写入。但 `WebConsoleSecurityInterceptor` 的 `/ui/**` 仍只依赖 `WebConsoleSecurityService.authenticated`，后者在 `enabled=false` 时返回 true，角色返回 ADMIN。首次启用并持久设置 flag 后，重启改为认证配置关闭，匿名访客仍可访问 UI 获得本会话 CSRF，并调用 `/ui/workers/{id}/offline`、`/ui/system/fleet-scheduling`、Plan 等管理入口。

影响：管理能力存在相同业务服务的另一条无认证路径，违背“关闭/重启不撤回认证”的已确认边界；CSRF 不能替代人工身份认证。

修复：持久 flag 开启后，全部 UI 和 API 管理入口都应 fail-closed。最小方案可在启动时发现 flag=true 且认证配置=false 就拒绝提供管理服务，或让 UI 拦截器同样读取持久状态并要求真实认证；不能只保护 `/api/**`。

### AUTH2：原始 URI 分类在 context path 部署下会降级审批权限（中）

事实：审批权限使用 `request.getRequestURI()` 匹配 `/api/r7/approvals/[^/]+/decision`，不包含 context path；不匹配时默认要求 OPERATOR。Spring 的 interceptor 路径匹配使用应用路径，requestURI 则包含 context path。

条件性影响：应用部署在 `/ctx` 等 context path 时，`/ctx/api/r7/approvals/X/decision` 可由 OPERATOR 通过，而实际应要求 APPROVER/ADMIN。机器白名单同样不匹配，正常 Worker 注册/领取/上报被错误要求人工会话。当前是否配置 context path 未验证；设计未排除此受支持部署方式。

修复：根据 `HandlerMethod`/明确路由元数据分类，或统一使用去除 context path 的规范应用路径。审批权限不得默认降级；机器白名单沿用同一分类方式。

### AUTH3：首次启用和旧请求存在认证检查的事务竞争窗口（高）

事实：拦截器在业务事务前读取 `legacyAuthRequired()`；`enabled` 事务先取得 delegation_lock，写入持久 flag；`ApprovalService.decide` 后续会取得相同锁，但取得后没有重新核验人工认证上下文。

静态可行时序：启用事务尚未提交，旧匿名审批请求读取到 flag=false，通过 preHandle，随后等待 delegation_lock；启用事务提交后，匿名请求获得锁，继续使用旧请求 decidedBy 完成审批。RequestBodyAdvice 在其入站时没有已验证 human 上下文，不能修复该时序。

修复：在取得与首次启用相同的串行化锁之后，对管理请求重新检查持久认证要求及已验证 human 上下文；只在 preHandle 提前读一次不足够。实现应明确 UI/旧 API 的可信人工上下文、机器白名单和内部 delegate/completion 上下文，不能要求机器租约拥有人工会话，也不能把 actor 名称当作认证。另一方案是明确定义并强制启用前全部旧管理请求排空，但不能仅以进站时间默许启用后仍提交匿名决定。

## 已成立的设计与身份字段检查

首次启用要求真实控制台认证配置；持久 flag 与 enabled 状态同事务写入，关闭时不删除 flag，方向正确。机器白名单精确列出注册/心跳/领取/租约/outputs，Drain/Offline 没有被列入，符合用户决定。

当前 `ApprovalController.DecisionBody.decidedBy`、`ApprovalRequestBody.requestedBy` 和 `FleetSchedulingRequest.controlledBy` 都是顶层 String record 字段，Advice 会替换为已验证会话 actor；不能再用这些字段冒充其他人。检查的相邻写入控制器未发现需依赖嵌套 actor 的既有管理 DTO。Advice 的一般性反射处理不构成所有未来 DTO 均安全的保证；新增 DTO 应明确字段来源。

## 决定归属和验证限制

上述均为已确认认证政策的工程修复，不需再次向用户询问兼容授权。假设：代理关闭后仍保留原持久认证要求；认证范围包括可执行相同管理业务的 UI 路径。

实际执行仅 `Get-Content`、`rg`、`Select-Object`、`Select-String`、文档 `apply_patch` 及 JSON 只读解析。读取了新增 MVC/Advice/identity/boundary、旧 console security/service、审批/Fleet/Worker/output 近邻路由和用户政策报告；路径猜测未命中后按实际 secretary 包定位。没有执行请求、测试、编译、网络、迁移或工作流命令。时序和 context path 结论是静态推导，没有声称成功复现。只新增本报告及 JSON。
