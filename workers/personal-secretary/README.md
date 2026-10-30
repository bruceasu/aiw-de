# 个人秘书 Worker / Personal secretary

秘书是用户自己运行的进程和 SQLite 私库。平台不运行此代码、不打开私库，也不接收私人对话、策略原文、模板、草稿或模型推理。专业执行/评审 Worker 保持原协议。

旧平台秘书实现和双模式已移除。旧 API 不兼容；V8/V9 历史迁移和数据保留封存。数据库存在旧私人数据时，平台的新委托启用会拒绝；本次不执行实际迁移、切换或数据清理。

## 启动

需要 Python 3.11+，无第三方依赖。先配置平台 Web Console 登录认证，在 `/ui/authority` 绑定当前人类身份、登记独立随机凭证和精确目标读取范围。再启动个人进程建立禁用状态的进程会话，随后在平台确认最小能力及有效期并明确启用，最后在私人 UI 明确启用本地处理。普通 Worker 注册、共享 bearer 或 capability 不代表个人身份。

在用户自己的终端配置 `AIW_PERSONAL_CREDENTIAL`。它不能出现在参数、URL、提示词、导出包或日志中。可选 `AIW_PERSONAL_MODEL_COMMAND` 是用户选择的模型程序 argv JSON 数组；程序从 stdin 读取私人上下文，将最多 16000 字符的答复写到 stdout。不会启用 shell，子进程不继承 `AIW_PERSONAL_*` 环境变量。未配置模型时仍能维护私人记录和手动提交固定能力，但不会伪称已调用模型。

```powershell
python workers/personal-secretary/main.py --owner-id O-EXPLICIT-ID --worker-id my-secretary --platform https://platform.example --data-root D:/private/aiw
```

默认个人 UI 监听 `127.0.0.1:8766`，控制台打印五分钟有效、最多五次尝试、使用后失效的一次性配对码。每个服务有独立 HttpOnly/SameSite 会话与 CSRF token，严格验证 Host/Origin，不接收平台 cookie、不开放 CORS 或 iframe。远程监听必须明确配置 HTTPS 反向代理的 `--public-origin`。平台的明文 HTTP 仅在用户明确使用 `--allow-loopback-http` 且 URL 为回环地址时接受；禁止凭证随重定向转发。

每个 owner/Worker 使用独立目录与身份记录；复制他人的数据库会被拒绝。私人数据目录的操作系统访问权限由用户管理，尤其是 Windows ACL。平台启用、能力签发和个人 UI 本地启用相互独立。参考进程每次启动生成新 boot UUID，通过 `/sessions` 使旧许可失效并要求平台重新明确启用；平台重启同样失效。服务器能核验声明的 boot 与凭证，不能检测自定义实现隐瞒的物理进程重启；这不赋予其超出已授能力的权限。

## 使用与恢复

私人工作可关联一个已在平台明确确认托管的干净 Plan 草稿。安排时固定业务版本和内容摘要，只接受未来整分钟；错过时间或内容变化后要求重新确认，不追赶。每次专业完成只在平台记录真实事实；个人协调读取当前源 attempt 后请求有界推进。额外修复、技术重试、仅重评、模型、审批、发布、删除等仍各自受固定能力约束。

私人策略关联已经人工确认的精确平台能力，模型只能建议是否申请，不能扩展动作/参数/预算。DENY 原文留私库，仅发送最小禁止投影及私有版本摘要；先在平台收窄并暂停，再落私库，失败继续暂停。解除禁止或交回须在平台明确操作。

决定与 outbox 同一私库事务提交。请求 ID 和原信封保持不变；响应丢失先查原回执，仅在确认不存在时重发原信封。UNKNOWN 只查询，不重放、不退款、不换 ID。平台人工对账需固定 attempt 和明确证据；不会自动启动重试。私人数据库/平台实例标识不匹配时停止处理。备份包含私人记录、outbox 与回执副本，包含业务参数但不包含平台凭证或登录会话；恢复后仍须人工重新启用。

## English operations

Run this Worker in the user's own environment. Use Python 3.11 or newer. No extra package is needed. The platform must use human login and an independently enrolled random machine credential. Set that credential in `AIW_PERSONAL_CREDENTIAL`; never put it in URLs or model prompts.

Start `main.py` with explicit owner, Worker, platform origin and private data root. A model command is optional and must be an explicit argv JSON array in `AIW_PERSONAL_MODEL_COMMAND`. It reads private context from stdin. Its output is advice, never platform authority. Dedicated credentials are removed from the model process environment.

Open the private UI, use its one-time pairing code and explicitly enable local processing after platform enablement. Restart always leaves processing paused. Remote access needs an explicit HTTPS reverse proxy. The private UI has its own session and never imports the platform session database.

Check the original receipt after transport failure. Resend only the exact pending envelope when the server confirms that no receipt exists. Never replay UNKNOWN. Human takeover and revocation block new starts; truthful running results may still be recorded. A new credential, grant, Worker, process or private backup cannot reset platform consumption or deduplication.

Migration planning is documented in [the offline tool](../../scripts/secretary_migration/README.md). Actual migration, imports, cutover and cleanup remain disabled. No old secretary mode or compatibility bridge is provided.

## 验证范围

`scripts/compile-personal-secretary.ps1` 仅运行离线生产 Java 编译、Python 内存编译和浏览器 JavaScript 语法编译，不运行服务、模型、测试、网络或迁移。编译通过不代表并发、会话、SQLite/PostgreSQL、故障窗口或浏览器流程已运行验证。
