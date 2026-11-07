# Plane 管理 OpenAI 凭证

本机 Plane 的 **Agent 工厂设置 → 模型登录与凭证** 提供登录、显式导入、更新和本机断开。模型账户不授予工具、计划、审批或文件权限。现有 Worker 本机 Codex 登录和 API Key 路径仍可选择。

## 配置受保护存储

Plane 需要环境变量 `AIW_MODEL_CREDENTIAL_KEY`，内容为随机 32 字节的 Base64。密钥必须保存在工作区和数据库之外，按本机操作系统账户权限保护，并与数据库分别备份。丢失密钥将无法解密已有凭证；不要通过每次启动时重新生成密钥来恢复旧数据库。

可在启动 Plane 的 PowerShell 会话中生成新安装的密钥，命令不会输出密钥：

```powershell
$modelCredentialBytes = New-Object byte[] 32
$modelCredentialRandom = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$modelCredentialRandom.GetBytes($modelCredentialBytes)
$modelCredentialRandom.Dispose()
$env:AIW_MODEL_CREDENTIAL_KEY = [Convert]::ToBase64String($modelCredentialBytes)
```

这只设置当前会话；请通过自己的受保护配置保留同一密钥再启动 Plane。工厂不会将密钥、Java 启动环境或 Spring JSON 配置传给 Python Worker。密钥缺失或错误时，新认证入口关闭，不能明文回退；原有登录路径不受影响。

Worker 凭证接口必须配置 `AIW_WORKER_TOKEN`。HTTP 仅用于本机 loopback；外部 Worker 需要 HTTPS。远端浏览器回调不属于本轮本机入口。

## 正常独立登录

1. 点击 **Continue with ChatGPT**。浏览器可能要求允许打开登录窗口。
2. Plane 启动短期 `127.0.0.1` 回调监听器，执行动态注册或对所选独立连接重新授权。不会安装或启动 Codex CLI/Pi。
3. 登录完成后，选择该连接、OpenAI 运行时及账户实际支持的模型，保存工厂设置。界面不会自动改变默认模型或账户。

每次登录都使用独立的 state、PKCE 和 nonce，并绑定当前控制台会话和权限。Plane 验证签名、issuer、客户端 audience、有效期、nonce、账户连续性及实际使用 scope。登录取消/失败不覆盖旧连接。仅身份授权成功时显示“已登录，尚未获得模型权限”，不能发起推理。

独立连接使用公开 `https://api.openai.com/v1/responses`；Python 管理循环、上下文和工具执行，使用显式上下文、`store=false` 和 `stream=true`。完整 `response.completed` 之前不执行模型提议的工具；断流、额度不足或认证失败不会变成“修复代码”反馈。不会静默切换至 API Key、其他账户或其他执行引擎。

模型名称目前由操作者明确填写；未调用账户模型目录来证明可用性。认证就绪不表示当前订阅资格、模型可用、额度或实际推理已验证。原生直接 Responses 任务保留 API 路径已有的控制边界，不宣称获得 Codex 原生回合控制；已有 GuardBroker 任务继续使用原控制通道与工具准入。

## 导入已有 Codex 凭证

在“显式导入已有凭证”中选择文件并提交；Plane 不会扫描 `~/.codex` 或其他目录。当前支持 Codex `auth.json` 的 `tokens` 结构，以及官方独立 OAuth 凭据记录。导入时校验签名、有效期、实际签发客户端、scope、账户与资源。过期或无法验证的来源会被拒绝。

Codex 凭据是独立连接类型，只用于 Codex app-server 的实验性 `chatgptAuthTokens` RPC。需要支持该接口的 Codex 安装；不支持时明确失败，不回退到其他登录。Worker 使用独立临时 Codex home，通过 RPC 接收短期 access token，并处理 app-server 的外部刷新请求；refresh/ID token 保留在 Plane。

独立 Responses 连接无需 Codex。Codex 导入失败时，可以独立登录并选择 OpenAI 路径；不会把旧 Codex 注册改造成动态客户端。更新所选 Codex 连接可使用新凭据文件；同一旋转令牌组不能重复导入到多个连接或 Owner。

## 更新、并发与恢复

Plane 在请求前按 expiry 和 `earliest_refresh_at` 决定是否刷新。“更新所选凭证”也受服务端最早刷新时间约束。刷新前先提交持久化 `REFRESHING` 意图，再发送一次远端请求；原子更换整组令牌并递增 generation。并发调用者等候同一刷新结果，不同时使用旧旋转令牌。

如果响应丢失、签发后本地保存失败或进程崩溃，连接显示 `REFRESH_OUTCOME_UNKNOWN`。其他 Worker 不重发旧令牌。独立连接通过显式重新登录恢复；Codex 连接通过其正常登录工具取得新的可验证凭据再导入。账户/客户端不能借恢复而改变。

任务领取时冻结连接身份、版本及工厂模型；模型选择已由任务正式记录时以该选择为准。更换工厂连接不更换在途任务账户。跨 Owner、不同 Worker 能力、过期/停止的精确任务租约、已断开连接或变化后的连接身份均不能领取令牌。长期凭据仅密文存储，短期 access token 只在 Worker 内存及模型认证通道使用。

订阅用量不等于 API Key 货币价格。默认 record-only 会记录实际可得的 token usage，金额保持未知；开启货币预算强制执行的 GuardBroker 任务暂不接受订阅连接，避免套用错误价格或把未知成本当零。

“断开”先提交本机令牌清除并阻止新请求，再尝试远端撤销。网络失败、正在轮换或结果未知时不会显示已确认撤销；可在 ChatGPT 设置中手动断开应用。正在进行的外部模型请求无法通过本机删除凭证撤销，但不会因此获得后续工具权限。升级新增 V30 表；回滚先恢复旧的工厂登录选项，排空相关 Worker，再回退程序，保留或受控清理加密表，不能把密文转为明文。

## 开发证据

本功能的设计、实际编译/测试与独立审查状态在 [FD-081](features/FD-081/FD-081_CONTROL_PLANE_MANAGED_OPENAI_AUTHENTICATION.md)。开发期间没有读取或导入实际凭据，也没有执行真实登录、刷新或模型请求。

实现合同来自官方[注册与登录](https://developers.openai.com/siwc/token-sharing-open-source/sign-in)、[令牌字段](https://developers.openai.com/siwc/token-sharing-open-source/token-reference)、[账户与刷新](https://developers.openai.com/siwc/token-sharing-open-source/profiles-and-sessions)、[Responses 限制](https://developers.openai.com/siwc/token-sharing-open-source/preview-limitations)及 [Codex 外部令牌 RPC](https://learn.chatgpt.com/docs/app-server)。
