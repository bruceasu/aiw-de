# ChatGPT OAuth 与 Python 自主管理循环

状态：官方文档评估与设计，尚未实现登录、刷新或模型调用。2026-11-07 核对官方公开文档；没有读取任何用户凭据，没有登录、导入、刷新或调用模型。用户最新要求是评估并纳入方向，不以本文冒充已交付的 OAuth 功能。

## 已确认的官方合同

官方支持开源/本机客户端独立注册并授权使用 ChatGPT 计划，适用资格由服务端决定，不能把成功登录当作已经获得模型权限。当前快速入门列出符合条件的 Plus/Pro；商业或远程托管应用另有接入条件。此应用是否符合实际部署条件、用户工作区是否允许，仍需真实接入验证。[接入范围](https://developers.openai.com/siwc/quickstart)、[开源应用说明](https://developers.openai.com/siwc/token-sharing-open-source)

首次注册使用 dynamic_agent_client 入口，之后保存并使用实际签发的 client_id；稳定 host ID 与注册分开。流程要求 PKCE、state、nonce 和本机回调，核验 ID token 的签名、issuer、audience、有效期和 nonce。返回的计划使用 scope 必须另行检查；仅有身份 scope 不够。[注册与登录](https://developers.openai.com/siwc/token-sharing-open-source/sign-in)

模型请求使用公开 POST https://api.openai.com/v1/responses，Bearer 为已授权 access token，不使用 ChatGPT backend-api。只在 response.completed 后接受模型结果；事件流中途也可能失败。账户模型列表须使用同一账户 token 查询并保留实际 slug。[模型调用](https://developers.openai.com/siwc/token-sharing-open-source/models-and-inference)

该路径有预览限制：HTTP 使用 store=false、stream=true，历史由 input 显式携带；不使用 previous_response_id 或 background。部分普通 API 参数不可用，函数/自定义工具也有组织形式要求。因此现有 Agents SDK 请求包装不能未经核对就直接替换 credential。[能力限制](https://developers.openai.com/siwc/token-sharing-open-source/preview-limitations)

刷新使用签发的 client_id 和对应 refresh token，原子保存替换后的令牌组。官方明确要求串行刷新同一 session，防止旋转令牌竞争。各账户注册、workspace/client、host/session 不能混用；失效、额度及临时故障分开处理。[账户与刷新](https://developers.openai.com/siwc/token-sharing-open-source/profiles-and-sessions)、[令牌字段](https://developers.openai.com/siwc/token-sharing-open-source/token-reference)、[错误恢复](https://developers.openai.com/siwc/token-sharing-open-source/errors-and-recovery)

## 本项目设计决定

用户补充了本地 Pi checkout，已完成[源码核对](../../../research/pi-local-source-worker-design-2026-11-07.md)。该快照将公开 `openai` provider 的独立 ChatGPT OAuth 与 legacy `openai-codex` 分开，进一步支持引擎/认证分离。参考其请求前取凭据、跨进程锁内重读、规范历史到模型投影与完整响应后派发工具的机制；不沿用 token 前缀判别认证类型、legacy URL、简化 ID token 校验、无上限工具并行或缺少崩溃记录的刷新写回。源码分析是静态证据，尚未证明效率或成本优势，也没有执行真实 OAuth。

参考 Pi 的循环和上下文机制，不安装 Pi，不引入 Pi/其 JS SDK。认证凭据只解决“如何认证模型请求”；工具权限、Plan Owner 和业务验收仍是独立边界。

| 执行引擎 | 模型认证 | 循环与工具所有者 | Codex CLI |
| --- | --- | --- | --- |
| Python 自主循环 | API Key | Python；沿用明确的工具宿主和任务权限 | 不需要 |
| Python 自主循环 | ChatGPT OAuth | Python；复用同一循环、上下文与工具边界 | 不需要 |
| Codex app-server | 已有受支持登录配置 | Codex 内部循环，按真实边界观测/控制 | 需要 |

新主接口为 ModelTransport 与 CredentialProvider，避免把 OAuth 变成 app-server 包装。Transport 只发送请求、解析流和报告终态；不得执行工具。CredentialProvider 返回受保护的短期访问租约，不包含任务目标。Python 的 LoopPolicy/ToolHost 决定工具派发、上下文工作集、有限纠错、暂停与完成。app-server 路径保留，不能在故障时静默切换引擎、账户或计费来源。

初期 HTTP/SSE，不依赖服务端持久会话。每轮保留规范输入、完整工具调用/结果配对和来源引用，投影后记录元数据。可先复用已有 host-action 协议获得无原生工具的模型传输；原生 function/custom 调用须在精确核对该 OAuth 路径的 namespace/additional_tools 合同后启用。工具动作必须等待整个模型响应完成，再逐动作核权执行，避免中途额度失败后仍执行部分计划。

## 正常登录与显式导入

独立“Continue with ChatGPT”是正常入口，不要求先安装 Codex/Pi。每次登录有独立、短期、一次性的 pending 状态；先启动本机回调，再打开系统浏览器。回调绑定 state/PKCE/nonce/完整 redirect URI；成功后才替换所选连接，不覆盖其他注册。

模型账户身份与 CP Owner 身份分开。已认证操作者明确为自己的执行配置选定 credentialRef；ChatGPT 登录不创建 CP Owner、文件访问权、审批权或任务执行授权。CP 只保存安全的连接引用/状态与选定配置，不保存 access/refresh/ID token。

凭据库存放在源码、工作区和工具可访问根目录之外；采用受保护的本机存储。模型输入、命令子进程环境、handoff、证据、日志和浏览器存储不接收原始凭据。JWT 验证必须使用受信任的 OIDC/JWKS 与实际验签库，不能只解码 payload 后标记可信。加密/系统凭据库依赖需单独确认，不暗中安装。

不自动扫描 ~/.codex、Pi 或其他应用的凭据文件。导入须用户明确选择，校验来源格式、签发客户端、resource/audience、授予 scope 和经过验证的账户身份；不把 bearer token 或刷新令牌跨客户端拼装。第一阶段可只导入符合条件的注册元数据并重新完成官方 OAuth；原始令牌导入保持高级、明确且可拒绝的路径。来源不匹配时引导独立登录。

## 多 Worker 刷新协调

同一 host 的多个 Worker 使用一个 CredentialCoordinator，协调键是注册与 session/token-set ID，不能只用 email 或账户名。不同 host 使用独立 host ID 和自己的 session；不通过共享一份旋转 refresh token 来解决跨主机登录。

1. 读取账户/客户端/host/session 和 credential generation；按有效期决定是否需要刷新。
2. 取得该 token-set 的跨进程锁；锁内重新读取，其他 Worker 已完成刷新时直接使用新代次。
3. 先持久化 REFRESHING 意图与期望 generation，再发送一次刷新请求；锁中不执行模型或工具调用。
4. 成功后校验账户/客户端连续性，以原子替换或事务 CAS 同时保存新 access token、refresh token、scope、expiry 和 generation。
5. 崩溃或网络丢失使刷新结果未知时标记 REFRESH_OUTCOME_UNKNOWN；其他 Worker 不重发旧旋转令牌。通过明确的重新授权恢复，不把过期文件锁当作令牌未使用的证据。
6. 终端失效转 AUTH_REQUIRED；临时基础设施故障保留连接。退出先停止新请求，尝试撤销再清理；不把未确认的远程撤销显示为成功。

刷新日志只保存状态、代次和请求引用，不保存令牌。access token 不进入 URL；授权 URL 如携带身份 hint，也不输出到日志。

## 自动化运行与额度

账户可登录但缺少计划使用 scope 时，只保留身份连接，不启动推理。未就绪认证、账户不符合资格、额度不足、请求参数不支持、暂时不可用和模型响应未完成是不同故障，不进入普通“修复代码”循环。

订阅额度不显示为免费、无限或独立 Worker 额度。使用实际 token usage 做观测；缺少货币计价时保持未知，不能套用 API Key 价格或填零。若原任务要求可核验的货币预算，需明确支持的计量政策，不能绕过它。

同一账户/注册的额度错误关闭新模型请求，并引导用户查看账户用量；不能在后台换另一个账户或改用 API Key 继续扣费。认证/额度恢复不是问题答案，也不是新增工具授权。流中断不执行未确认的工具计划；未知请求结果不盲目重发，适用原期限、调用次数和显式重试政策。

## 实施前需落实

- 为独立 Python loop 提供稳定 ModelTransport；将当前工具执行与 SDK 包装分开，复用受控工具或明确冻结的角色权限。
- OAuth 登录、账户选择、保护存储、凭据引用、scope/ID-token 验证与撤销。
- Coordinator 的跨进程锁、崩溃日志、CAS 和轮换恢复；覆盖多个 Worker 争抢及未知刷新结果。
- OAuth Responses 专用序列化、流终态、用量/错误、工具格式与完整上下文配对。
- 模型/账户/计费/权限冻结与可用能力匹配；默认不自动降级。
- 经授权的 mock/合同验证与独立审查；实际 OAuth、订阅资格和真实推理另行验证。

这里没有验证当前用户账户资格、可用模型、真实 OAuth 或真实推理，也没有运行登录和刷新。FD-079 的原生 Plan 控制/问答已完成本地实现和独立审查；本评估未替代其范围，也不作为已实现的 OAuth 功能。
