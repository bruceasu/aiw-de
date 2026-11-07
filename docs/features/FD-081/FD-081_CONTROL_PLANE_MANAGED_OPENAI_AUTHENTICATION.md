# FD-081: Control Plane managed OpenAI authentication

**Status:** In Progress
**Revision:** 1
**Review policy:** Independent
**Priority:** Medium
**Evidence policy:** Markdown

## Problem

用户要求由 Control Plane 统一处理 OpenAI 登录：显式导入已经登录的 Codex 凭证、发起登录获取凭证、更新凭证。FD-079 只有 OAuth 评估；现有 app-server 只是复用 Worker 本机已有登录，没有新登录或刷新实现。

本 FD 采用用户新决定，将长期凭证、登录与刷新集中在 Plane；覆盖旧评估中由 Worker 本机保管长期 OAuth 凭证的建议。Worker 的执行循环、工具权限和业务验收仍由原有边界管理。

## Options and decision

采用 Plane 凭据库与刷新协调者，Worker 绑定安全连接引用，按精确任务/attempt 租约获得短期访问凭证并直接调用模型。refresh token 不分发给多个 Worker，不通过共享 Codex auth.json 协调旋转令牌。模型原文不必经 Plane 转发，不改变 FD-079 的元数据与工作区隐私边界。

连接类型明确区分独立 ChatGPT OAuth 与 Codex app-server 账户。已有 Codex 凭证只能显式导入并按实际客户端、scope、账户及后端适配能力校验；不能据“已登录”断言可调用公开 Responses，也不能把 Pi/Codex 的凭证混用。无法验证或不适配时保留明确未就绪状态，引导正常独立登录。

## Solution

### 连接与身份

Plane 提供 Owner 范围内的连接记录：类型、客户端/资源/账户的已验证身份、安全展示名、状态、版本与 credentialRef。原始 access/refresh/ID token 只进入受保护的服务端凭据库，不出现在普通详情 API、模型输入、任务指令、handoff、日志或浏览器存储中。模型账户身份不授予平台 Owner、工具或审批权限。

实际存储需要受保护的主密钥/系统凭据设施；无保护能力时不以明文回退。使用可信 OIDC/JWKS 验签组件，核对 issuer、audience、有效期、nonce、sub 和实际模型使用 scope，不能仅解码 JWT。具体依赖与本地可用性在实施前核对，不提前安装或下载。

### 三个入口

1. 导入：操作者明确选择来源文件或内容，经 Owner/CSRF 校验后导入。Plane 不自动扫描 Worker、本机或远端用户目录。校验来源格式、签发客户端、resource/scope、账户与适配器；导入失败不得覆盖已用连接或把错误类型令牌拼成新凭证。
2. 登录：独立正常入口，不要求先安装 Codex 或 Pi。pending 登录绑定操作者、PKCE/state/nonce、完整回调 URI 和期限，一次性消费；成功后保存实际签发的客户端和对应令牌组。身份登录成功但缺少推理权限时单独显示，不启动模型。
3. 更新：Plane 根据实际 expiry/earliest_refresh_at 协调刷新，原子替换整组令牌；显式重新登录、撤销和失效恢复保留审计。失败不静默更换账户、引擎或计费来源。

### 多 Worker 与多 Plane 实例

按 registration/session/token-set 协调，不按 email 或 Worker ID 锁定。锁内重读当前代次；持久化 REFRESHING 意图及 generation，再发送一次刷新请求，成功后以 CAS 原子保存新令牌组。远端轮换成功但本地落盘未知、进程崩溃或网络超时后，进入 REFRESH_OUTCOME_UNKNOWN，不重发旧旋转令牌；通过明确重新授权恢复。多 Plane 实例使用持久协调和 fencing，不能只依赖进程内互斥。

Worker 的短期访问租约绑定当前注册 Worker、精确 Task/Attempt、固定连接/Owner 与模型配置，要求当前执行租约有效。返回短期 access token 时不附 refresh token，且不注入工具子进程环境或磁盘。凭证只解决认证，不扩大原任务、模型或工具授权。账户不可用/额度不足不是代码修复输入，不能触发无限 repair。

### 接入与上线

保留现有 API Key 与本机 app-server 路线；新的 Plane 连接由操作者明确选择并冻结到执行配置，不自动替换在途运行。只接入已核对合同的模型适配器；保存连接不等于真实推理已可用或已验证。

用户已确认 Plane 主要部署在本机。正常登录采用本机 loopback 回调，并验证发起会话、固定回调 URI、state、PKCE 和一次性消费。远端部署不在本轮登录入口范围内；不能把 localhost 回调改成任意远端 URI，也不能发明客户端或 scope。

## Scope

### 本机实施分解

本机部署已确认，按下面的接口边界实施。以下是设计合同，尚不是已存在的 API 或登录能力。

| 模块 | 职责与约束 |
| --- | --- |
| ModelConnections | Owner 隔离的连接元数据、显式选择、版本核对和撤销状态；详情只返回安全投影。 |
| OpenAiLogin | 生成 PKCE/state/nonce，绑定控制台会话与短时 loopback listener；验证一次性回调及 OIDC 身份。登录取消、超时和失败不得覆盖已有连接。 |
| CredentialImports | 仅接收操作者明确选定的凭据内容，限制大小并严格校验格式；区分 Codex app-server 注册与独立 Responses 注册，不自动扫描用户目录。 |
| CredentialVault | 凭据整体加密，密文认证绑定 Owner、连接、客户端及 generation；主密钥在数据库和工作区之外。配置缺失时返回未配置，不能生成一个无法恢复旧密文的新密钥后继续运行。 |
| RefreshCoordinator | 持久化刷新意图、generation CAS、跨实例排他及未知轮换恢复；远端请求期间不持有业务事务。只有协调者能使用 refresh token。 |
| WorkerCredentialLease | 每轮模型请求前校验当前 Worker/Task/Attempt 租约、固定连接、Owner 与模型配置，再返回短期 access token。响应禁止缓存，不返回 refresh/ID token。 |
| ResponsesTransport | 使用原有 Python loop 和 ToolHost；认证仅替换模型访问方式。完整响应确认前不执行工具，凭据错误不进入代码纠错循环。 |

控制台候选入口统一放入 `/api/r1/model-connections` 的会话与 CSRF 边界：列举安全投影、显式导入、发起/取消登录、更新、断开和核对状态。Worker 的领取接口使用已有机器认证与精确 execution lease，不复用浏览器会话。回调 listener 只接收固定路径的 OAuth 回调，不能提供连接列表、任意转发或 Worker 取凭据接口。

连接类型固定为 `CHATGPT_RESPONSES` 或 `CODEX_APP_SERVER`。导入的 Codex 凭据若无法核对其签发客户端、资源与适配器能力，应明确显示不兼容并引导正常登录；不能为了让导入成功而把 token 当成公开 Responses API 凭据，也不能把客户端元数据补成猜测的值。app-server 的外部认证接入合同核对完成前，原本的 Worker 本机登录路径保留。

建议持久化拆为连接元数据、加密 token-set、短期登录状态和刷新意图四部分。登录的 PKCE verifier 也属于受保护秘密；刷新代次和意图使用数据库约束协调，不能只依赖本机文件锁。主密钥与密文分别备份；任何 token 组替换都以连接版本和代次为条件，晚到的登录/刷新响应不能覆盖已撤销或更新的连接。

连接状态区分 `UNCONFIGURED`、`LOGIN_PENDING`、`IDENTITY_ONLY`、`READY`、`REFRESHING`、`AUTH_REQUIRED`、`REFRESH_OUTCOME_UNKNOWN` 与 `DISCONNECTED`。只有身份登录成功但缺少模型 scope 时进入 `IDENTITY_ONLY`；`READY` 仅表示已满足静态认证条件，不能宣称当前账户的额度或真实推理已验证。刷新结果未知时不重放旧 refresh token；显式重新登录以新代次恢复。

接入顺序是保护存储与安全投影、正常独立登录、严格导入、刷新协调、任务配置固定连接、Python 取短期凭据与 Responses 传输，最后接控制台交互。不能先显示“已登录可运行”而把 Worker 适配留为空实现。

关键回归覆盖：跨 Owner 访问、CSRF、回调重放/错误 state/nonce、错误客户端或账户、ID token 验签失败、取消登录不覆盖旧连接、并发刷新只发一次、旋转后本地结果未知、撤销与晚到响应竞争、过期 Worker lease、连接配置改变、日志脱敏和流未完成不执行工具。默认先完成用例与编译；运行遵守单独授权预算。

本 FD 先完成集中认证设计，并在关键部署/接口条件明确后实现导入、正常登录、刷新、受保护存储和精确执行引用。保留 app-server，不安装 Pi、不复制其 SDK，也不自动执行任何真实登录、导入现存凭证或模型调用。真实操作需要操作者在产品中明确发起；开发验证另守仓库预算。

当前 FD-080 评审策略继续独立收尾，本 FD 不改变其推进门槛或工具权限。

## Work items

- [x] 1.1 确认本机/远端部署与官方回调方案，核对可导入客户端及模型适配合同。
- [x] 2.1 实现 Owner 隔离的连接元数据、受保护存储和明确连接状态，选择固定引用。
- [x] 2.2 实现显式导入与正常登录，验证完整身份、scope、客户端和一次性回调。
- [x] 3.1 实现 token-set 刷新协调、代次 CAS、未知轮换恢复与本机断开；远端撤销区分确认和未确认。
- [x] 3.2 接入精确 Worker/Attempt 的短期访问租约，保持既有循环和权限边界。
- [ ] 4.1 UI、说明、升级/回滚与 mock/合同用例已写；完成 Java 编译与获授权检查并记录真实限制。
- [ ] 4.2 准备整项独立审查输入；正式 Reviewer 回执单独把关。

## Acceptance

1. Plane 能明确导入或登录并保存对应连接；不兼容来源不会假装可用于另一类后端。
2. 多 Worker 不并发刷新同一旋转令牌；未知刷新结果不会重放旧令牌。
3. 跨 Owner、旧租约、错误连接与已变配置不能领取访问凭证；refresh token 不离开 Plane。
4. 模型身份与执行权限分开，原文/文件/凭据不进入运行元数据，无静默账户或计费降级。
5. 所有登录、导入、刷新、失效与恢复有可核对状态；未验证的实际推理、订阅资格和费用保持未知。

## Verification

- 2026-11-07 用户明确要求开始实现。沿用当前工作区，不创建 worktree；FD-080 的已领取审查事件和未完成验证保持独立。
- 已再次核对官方注册、令牌、刷新、Responses 和 app-server 外部令牌接口。新注册使用 `dynamic_agent_client`，保存回调实际签发的客户端；公开 Responses 授权与 Codex app-server 的 `chatgptAuthTokens` 为两条适配路径。

- 已实现 Java modelauth 模块、V30 密文表、工厂连接选择与任务绑定、Python 短期凭据访问、Codex 外部令牌 RPC、独立 Responses host-action loop 及工厂页面。没有读取或导入实际凭据，也没有真实 OAuth、刷新或模型调用。
- Plan 首次使用 Plane 连接后冻结该连接；后续步骤与正式问答替换任务保留来源绑定。连接代次更新不改变账户绑定；工厂切换连接后，旧 Plan 可能等待原连接的兼容 Worker，而不会静默更换账户。
- 用户已确认：Plane 主要部署在操作者本机；设计以本机 loopback 登录入口为准。官方客户端、scope 与模型适配合同仍须在实现前核对。
- 声明本地已有 `com.nimbusds:nimbus-jose-jwt:9.37.3` 进行 RS256 验签，签名、issuer/audience、有效期、nonce、sub/client 和实际 scope 均核对；不使用仅解码的 JWT。未下载依赖。版本来自现有缓存，不宣称已做独立漏洞审计；网络仅核对公开官方文档，不访问认证或模型服务。
- `node scripts/compile-worker-runtime.mjs` 通过：Python/JavaScript/TypeScript 语法与 Go compiler，无测试、模型或网络。`git diff --check` 通过，已检查相关调用边界。
- Java `mvn -o -Dmaven.repo.local=D:/.m2/repository -DskipTests test-compile` 首次因 Nimbus 的缓存来源 ID 失败，未进入源码编译；任务专用离线设置第一次修正后，又发现 jcip 注解包来自另一缓存 ID。两项 jar/POM 都在本机，已补齐任务专用离线仓库别名，不改全局缓存、不下载；自动重试预算已用，尚未再次执行 Java 编译。
- 新增 Java `ModelCredentialsTest`、`OpenAiOAuthTest` 和 Python `test_model_credentials`，覆盖密文完整性、真实 RSA 验签、身份/scope、刷新串行/未知结果/断开竞争、精确租约和完整流终态。用例尚未执行，Java 用例尚未完成编译；不能将编译或静态证据写为测试通过。
- 所有后续 Worker/页面源码改动后，再次执行 `node scripts/compile-worker-runtime.mjs` 通过；最新 Python/JS 源码已经过 compile-only。Java 源码及测试仍待已发出的新增离线验证授权，不继续自动重试缓存识别失败的命令。
- %% NEEDS_INPUT: 是否允许所列离线 Java 编译重试及 Java/Python 定向测试？已异步询问。当前依赖缓存来源的修正尚未验证，4.1/4.2 未完成，未请求正式 Reviewer 或标记 Complete。
- [使用与升级说明](../../MODEL-AUTHENTICATION.md) 记录密钥配置、类型选择、Codex 实验性 RPC、原生直接 API 控制边界、订阅金额未知及本机断开与远端撤销的区别。
- 用户已明确授权集中管理方向；不因旧评估的 Worker 本地存储建议重复要求确认。
- Review policy Independent：涉及认证秘密、账户隔离、公开接口、持久化与跨进程/实例刷新。

## Sources

- 用户关于 Plane 统一导入、登录、更新凭证的新决定。
- [FD-079 OAuth 官方合同评估](../archive/FD-079/OAUTH-RESPONSES-DESIGN.md)，其中长期凭证位置改按本 FD 的集中式新决定。
- [Pi 本地源码核对](../../research/pi-local-source-worker-design-2026-11-07.md)，仅参考机制，不继承 legacy 客户端或简化身份验证。
