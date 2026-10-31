# FD-072 独立复审 r2

**结论：`verification-passed`**

## 审查基线

- FD：`docs/features/FD-072/FD-072_AGENT_CONTROL_PLANE.md`
- 来源事件：`FD-072-000007-implementation-ready`
- Reviewer session：`fd072-reviewer-20261031-r2`
- 工作树：`D:\03_projects\AI-tools\aiw-de\.wt\FD-072`
- 比较基线：`HEAD 098690d974b025a28aefa8e414d8382b2a66d735` 加该提交上的当前工作树差异；实现尚未提交。
- 复审范围：r1 配对/授权/启用缺口及 r2 修复，相关 API、事务、迁移、UI、Agent 握手与安全调用路径；并参考 r1 报告及 Worker r2 说明。

## Findings

没有未解决的实现缺陷。复核期间发现并已修复两项 P2：

1. **待批准 Agent 的业务权限预置**：`BusinessActionPermissions.issue` 此前只检查 Agent 类型，隐藏 UI 之外仍可通过有效的人类 API 请求给 `PENDING` Agent 创建启用权限。当前实现在线程幂等回读前要求 owner/Agent 绑定同时满足 `identity_kind='AGENT'` 和 `approval_state='APPROVED'`（`src/main/java/me/asu/aidev/authority/BusinessActionPermissions.java:67-75`），所以新建和幂等重放均不能给待批准身份预置权限。
2. **LOCAL 档案的反代 loopback 误判**：仅检查 socket peer 地址可能把 loopback 反代来的外部请求当成本机。当前 LOCAL 配对与 Agent API 调用都拒绝常见转发头并继续校验 loopback（`src/main/java/me/asu/aidev/authority/AgentTransportSecurity.java:24-28,47-78`）；公网 `agent-mtls` 监听器默认关闭 LOCAL 档案（`src/main/resources/application-agent-mtls.yaml:14-17`）；部署说明要求 LOCAL API 仅直连 loopback、不得经公网反代发布，并说明头检查不能取代网络隔离（`docs/features/FD-072/AGENT_MTLS_DEPLOYMENT.md:20-24`）。这关闭了受支持部署配置中的旁路。被错误配置为剥除转发标记并转发 LOCAL API 的反代仍需由部署方隔离；该配置未在运行环境验证。

## 验收复核

- 配对服务创建 `approval_state='PENDING'` 身份，禁用进程并返回待人工批准状态（`AgentIdentityService.java:53-82`）。服务端 Agent 路由先认证专用凭据和传输，再限制待批准身份只能调用 `/sessions` 与 `/connection-state`；新会话保持禁用，其它机器操作返回拒绝（`AuthorityHttpConfiguration.java:26-70`、`AuthorityGrantService.java:58-70`）。
- 唯一批准路由属于人类 API：请求需登录和有效 CSRF；服务端还检查当前人类角色、owner、PENDING 状态、generation 与 Agent boot。该方法在一个 `@Transactional` 服务调用中签发精确 Grant、以 CAS 将身份改为 APPROVED 并绑定当前进程、写审计（`AuthorityHttpConfiguration.java:30-44,79-99`、`AgentIdentityService.java:84-109`）。Grant 通过注入的 `AuthorityGrantService` 代理创建，状态通知通过 `ObjectProvider<AgentMessageService>` 调用；二者为默认 REQUIRED 事务传播并使用同一 JDBC 事务。CAS 或后续异常是未捕获运行时异常，按 Spring 事务规则回滚 Grant、预算、审计、outbox 及身份变更。此事务结论是基于静态调用路径的判断，未通过故障注入或运行验证。
- UI 只对 PENDING 身份显示组合确认表单；尚无 worker boot 时禁用提交；提交携带 CSRF token、generation、boot 与精确 scope（`agents.html:35-47`、`agents.js:22-30,45-77`）。Agent 客户端先建立受限会话再读连接状态，待批准时保持本地处理未启用并引导回 Control Plane（`personal-secretary/platform_setup.py:138-162,164-181`）。
- V26 将历史身份保留为 `LEGACY`，已撤销身份保持 `REVOKED`；新配对显式建立 PENDING。旧身份不被迁移为 AGENT/APPROVED。撤销会轮换 credential hash、清空 boot、递增 generation 并撤销已有 Grant/Permission（`V26__agent_pairing_approval.sql:1-6`、`AgentIdentityService.java:112-132`）。
- REMOTE 请求必须使用 HTTPS、受信 X.509 客户端证书及已批准指纹；Agent 客户端验证服务端证书，拒绝 HTTP 降级。TLS profile 用 `client-auth: want` 保留浏览器页面访问，但远程 Agent 配对及机器 API 在应用边界要求实际已验证证书链和专用凭据（`AgentTransportSecurity.java:32-64`、`AuthorityHttpConfiguration.java:50-70`、`platform_client.py:38-55,71-105`）。
- outbox 消息仅承载固定类型、资源 ID、版本和投递所需元数据，Agent 拉取当前授权状态后 ACK。消费计数、use 记录、版本递增与 `PERMISSION_CHANGED` outbox 在事务路径中提交；ACK 在读取后及 CAS UPDATE 中检查 lease 与消息过期时间，已 ACK 的相同有效 token 保持幂等（`BusinessActionPermissions.java:124-138`、`AgentMessageService.java:28-40,74-89`）。这些 r1 已修复项在当前差异中仍成立。

## 命令与证据边界

- 本 Reviewer 执行：`aiw fd claim FD-072 FD-072-000007-implementation-ready --session fd072-reviewer-20261031-r2`（成功）；`git rev-parse HEAD`、`git status --short`、`git diff --stat HEAD`、`git diff --name-status HEAD`；对上述 Java、SQL、YAML、HTML、JavaScript、Python、FD/OpenSpec 和交接报告执行只读 `rg -n -C`、`Get-Content -Encoding UTF8` 检查。
- 一次初始读取错误地在 worktree 下寻找未物化的 `.agents` 文件；另一次 `rg` 使用了不存在的 `personal-secretary/platform/` 路径。均改用已安装 fd-review 技能路径及实际源码路径后继续，没有修改生产源码。
- Worker 报告在最终安全修复后以 JDK 25.0.1 / Maven 3.8.1 离线执行 `-o -DskipTests compile`，320 个 Java 源文件 `BUILD SUCCESS`，并报告 `git diff --check` 通过。本 Reviewer 未运行这些命令。
- 未运行测试、应用、浏览器、网络、EventBus、mTLS 证书/CA 配置、部署或运行验证。证书签发、私钥保护、反向代理路由隔离和真实 mTLS 握手仍需部署环境验证；本报告的结论基于静态代码与文档证据。
