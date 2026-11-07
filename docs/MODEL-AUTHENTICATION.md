# Plane 管理 OpenAI 凭证

本机 Plane 的 **Agent 工厂设置 → 执行与登录** 按当前执行方式显示登录来源；**已保存的登录连接** 管理各个连接的更新、重新登录和断开。模型账户不授予工具、计划、审批或文件权限。现有 Worker 本机 Codex 登录和 API Key 路径仍可选择。

页面将“模型执行方式”和“登录与凭证来源”分别设置。两种执行方式的内部标识保持原样，OAuth 是凭证来源，不增加第三个执行方式：

| 页面执行方式 | 登录与凭证来源 | 实际调用方式 |
| --- | --- | --- |
| Codex App Server（通过本机 Codex） | Worker 本机 Codex 登录，或 Plane 导入的 Codex 凭证 | 通过 Codex app-server 调用模型；受控主机动作仍遵循原工具准入。 |
| Python Worker（直接调用 OpenAI Responses） | Plane 的 ChatGPT OAuth 连接 | Python Worker 管理执行循环和工具调用，直接调用公开 Responses；无需 Codex 或 API Key。 |
| Python Worker（直接调用 OpenAI Responses） | Worker 本机 API Key | Python Worker 通过已有 OpenAI Agents SDK 路径调用 Responses。 |

## 动态配置与模型目录

页面按“执行方式 → 登录方式 → 对应设置 → 模型”排列。新的安装不预选执行或登录来源；载入已保存设置时恢复原选择。

- Codex App Server：选择 Worker 本机登录或 Plane 保存的 Codex 凭证。仅此方式显示 Codex 程序路径；本机登录时才显示 Codex 登录目录。
- Python Worker：选择 ChatGPT OAuth 或运行环境 API Key。OAuth 显示连接选择与新账户登录；API Key 显示本机是否已配置及是否使用自定义 API 地址，不回显密钥、不新增浏览器密钥存储。
- 新建 ChatGPT 连接始终创建新连接，不会把当前工厂选中的连接作为覆盖目标。每张已有连接卡片分别绑定重新登录、更新文件、刷新和断开；替换凭据仍校验同一账户和客户端。

完成登录设置后自动读取目录，也可手动刷新。`POST /api/r1/model-connections/models` 只接受与执行方式匹配的登录来源，要求当前控制台管理员权限；浏览器不接收模型凭据。

| 登录来源 | 目录来源 | 已知限制 |
| --- | --- | --- |
| Plane 的 ChatGPT OAuth | 用该账户 token 请求公开 `/v1/models`，保留可见模型的实际 slug | 尚未执行真实推理或核对额度。 |
| 本机运行环境 API Key | 对既有 `OPENAI_BASE_URL` 或默认地址请求 `/models` | 返回的是账户目录，可能包含其他用途模型；不据名称猜测 Responses 兼容性。 |
| Codex 本机登录或 Plane Codex 凭证 | 受限本机探针调用 app-server `model/list`，使用返回的模型和推理强度 | 目录可能来自 Codex 内置/缓存；外部 Worker 的环境与账户可用性可能不同。 |

Codex 探针只初始化协议、读取账户和模型目录，不创建 thread/turn、业务任务或 Worker 注册，不执行工具。目录查询使用已保存的程序路径；未保存的路径不用于启动进程。Plane 凭据只通过 stdin/RPC 传给受限探针，不进入命令参数或环境；不传 refresh token。

登录方式的四个选项直接写在页面模板中，脚本会按执行方式筛选；不再等待凭证或目录接口成功后才生成选项。配置读取期间可先选择，迟到的初始化结果不会覆盖正在编辑的选择。保存程序路径后会失效旧目录并重新读取；服务端从同一个保存快照取得配置与版本，拒绝旧环境迟到结果污染新版本缓存。

目录有 60 秒短期缓存和最多两个并发查询。切换执行方式、登录方式或连接会清除旧模型选择，并丢弃旧请求的迟到结果；新目录不自动选择第一个模型。目录失败或模型不在列表时，可明确选择手动填写。模型 ID 仍遵守既有 Worker 格式，未开放其他格式。推理强度优先使用目录返回的能力，缺失时明确显示为通用选项。

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

Plane 连接当前授权给由工厂启动并在服务端绑定的 Python Worker。公开的连接引用或能力 hash 不是授权凭据：工厂另外发放私有 Worker proof，服务端保存摘要与密文，注册、领取和取模型令牌均验证固定绑定及 proof。proof 只留在 Worker 控制通道，不传给 Codex、Git 或工具进程。外部 Worker 继续使用自身登录/API Key；不能仅自报能力来领取 Plane 凭证。

## 正常独立登录

1. 点击 **Continue with ChatGPT**。浏览器可能要求允许打开登录窗口。
2. Plane 启动短期 `127.0.0.1` 回调监听器，执行动态注册或对所选独立连接重新授权。不会安装或启动 Codex CLI/Pi。
3. 在 Python Worker 下选择 ChatGPT OAuth 登录方式及该连接，从当前账户目录选择模型，或明确手动填写，再保存工厂设置。界面不会自动切换执行方式或选择其他账户。

每次登录都使用独立的 state、PKCE 和 nonce，并绑定当前控制台会话和权限。Plane 验证签名、issuer、客户端 audience、有效期、nonce、账户连续性及实际使用 scope。登录取消/失败不覆盖旧连接。仅身份授权成功时显示“已登录，尚未获得模型权限”，不能发起推理。

独立连接使用公开 `https://api.openai.com/v1/responses`；Python 管理循环、上下文和工具执行，使用显式上下文、`store=false` 和 `stream=true`。完整 `response.completed` 之前不执行模型提议的工具；断流、额度不足或认证失败不会变成“修复代码”反馈。不会静默切换至 API Key、其他账户或其他执行引擎。

模型由操作者从目录选择或明确手动填写。认证就绪或目录可见不表示当前订阅资格、额度或实际推理已验证。原生直接 Responses 任务保留 API 路径已有的控制边界，不宣称获得 Codex 原生回合控制；已有 GuardBroker 任务继续使用原控制通道与工具准入。

## 导入已有 Codex 凭证

在“显式导入已有凭证”中选择文件并提交；Plane 不会扫描 `~/.codex` 或其他目录。当前支持 Codex `auth.json` 的 `tokens` 结构，以及官方独立 OAuth 凭据记录。导入时校验签名、有效期、实际签发客户端、scope、账户与资源。过期或无法验证的来源会被拒绝。

Codex 凭据是独立连接类型，只用于 Codex app-server 的实验性 `chatgptAuthTokens` RPC。需要支持该接口的 Codex 安装；不支持时明确失败，不回退到其他登录。Worker 使用独立临时 Codex home，通过 RPC 接收短期 access token，并处理 app-server 的外部刷新请求；refresh/ID token 保留在 Plane。

独立 Responses 连接无需 Codex。Codex 导入失败时，可以独立登录并选择 OpenAI 路径；不会把旧 Codex 注册改造成动态客户端。更新所选 Codex 连接可使用新凭据文件；同一旋转令牌组不能重复导入到多个连接或 Owner。

## 更新、并发与恢复

Plane 在请求前按 expiry 和 `earliest_refresh_at` 决定是否刷新。“更新所选凭证”也受服务端最早刷新时间约束。刷新前先提交持久化 `REFRESHING` 意图，再发送一次远端请求；原子更换整组令牌并递增 generation。并发调用者等候同一刷新结果，不同时使用旧旋转令牌。

如果响应丢失、签发后本地保存失败或进程崩溃，连接显示 `REFRESH_OUTCOME_UNKNOWN`。其他 Worker 不重发旧令牌。独立连接通过显式重新登录恢复；Codex 连接通过其正常登录工具取得新的可验证凭据再导入。账户/客户端不能借恢复而改变。

崩溃遗留的过期 `REFRESHING` 意图可由验证成功的新授权按原版本和代次 CAS 接管；保存新代次会阻止旧请求的迟到响应覆盖恢复结果，不通过重放旧 refresh token 进行恢复。

任务领取时冻结连接身份、版本及工厂模型；模型选择已由任务正式记录时以该选择为准。更换工厂连接不更换在途任务账户。跨 Owner、不同 Worker 能力、过期/停止的精确任务租约、已断开连接或变化后的连接身份均不能领取令牌。长期凭据仅密文存储，短期 access token 只在 Worker 内存及模型认证通道使用。

订阅用量不等于 API Key 货币价格。默认 record-only 会记录实际可得的 token usage，金额保持未知；开启货币预算强制执行的 GuardBroker 任务暂不接受订阅连接，避免套用错误价格或把未知成本当零。

“断开”先提交本机令牌清除并阻止新请求，再尝试远端撤销。网络失败、正在轮换或结果未知时不会显示已确认撤销；可在 ChatGPT 设置中手动断开应用。正在进行的外部模型请求无法通过本机删除凭证撤销，但不会因此获得后续工具权限。升级新增 V30 表和 V31 Worker proof 列，不改已发布迁移的校验和；旧的 Plane 凭证 Worker 需排空并由工厂重新启动取得 proof。回滚先恢复旧的工厂登录选项，排空相关 Worker，再回退程序，保留或受控清理加密表，不能把密文转为明文。

## 开发证据

本功能的设计、实际编译/测试与独立审查状态在 [FD-081](features/FD-081/FD-081_CONTROL_PLANE_MANAGED_OPENAI_AUTHENTICATION.md)。开发期间没有读取或导入实际凭据，也没有执行真实登录、刷新或模型请求。

实现合同来自官方[注册与登录](https://developers.openai.com/siwc/token-sharing-open-source/sign-in)、[令牌字段](https://developers.openai.com/siwc/token-sharing-open-source/token-reference)、[账户与刷新](https://developers.openai.com/siwc/token-sharing-open-source/profiles-and-sessions)、[Responses 限制](https://developers.openai.com/siwc/token-sharing-open-source/preview-limitations)及 [Codex 外部令牌 RPC](https://learn.chatgpt.com/docs/app-server)。
