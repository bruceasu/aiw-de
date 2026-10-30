# FD-065 独立评审（revision 4）

<!-- aiw-data: FD-065-review-r1.json -->

## 结论与发现

**结论：changes-requested。** 发现多项影响配置可用性、API 契约和预算控制的缺陷；本轮没有运行测试或网络验证。

### F1 · P2 · 知识库配置 UI 与 FD 工作项文字损坏

位置：`personal-secretary/ui/index.html:76`；`docs/features/FD-065/FD-065_API.md:54-58`。

新增配置的 summary、Bearer token 标签、启用复选框标签和提示文本在文件中是 ASCII `?` 字符（逐字读取确认），用户无法从界面辨认这些控件。FD 的五条工作项描述也被替换为问号，却都标为完成，破坏了 FD 的可追溯性。

最小修正：恢复 UI 的真实中文标签和说明；从已确认设计恢复 FD 工作项文本，保留实际完成状态与未验证项的准确说明。

### F2 · P2 · 保存无关设置会重探测并关闭知识库

位置：`personal-secretary/ui/app.js:1136`、`personal-secretary/main.py:280`、`personal-secretary/assistant_settings.py:70-78`。

设置表单每次提交都会写入 `knowledgeEnabled`，后端因此每次都调用 `configure_knowledge()`。当知识库原本已启用但暂时不可达时，用户只修改模型设置也会先持久化 `knowledgeEnabled=False`，随后 capabilities 探测失败并返回错误；已有模型设置已保存，知识库却在后续重启中保持关闭。

最小修正：仅在知识库配置确有变更或用户显式启用/检查时运行配置探测；普通模型设置保存不得重置知识库启用状态。

### F3 · P2 · 只读请求的重试没有遵循契约

位置：`personal-secretary/knowledge_client.py:43-44, 65-69, 93-94`。

契约要求只读请求对连接故障、429、503、504 最多重试一次。当前只对 GET 的连接异常重试；429/503/504 直接作为失败返回，而搜索是安全只读的 POST，也完全不重试。代码也没有读取 `Retry-After`。服务短暂限流或不可用时，搜索和读取会比契约规定更早失败。

最小修正：将只读语义与 HTTP method 分开标记；对规定的失败类型最多重试一次，并遵守可解析的 `Retry-After`，创建笔记仍只按对账规则处理。

### F4 · P2 · 成功响应没有按 endpoint 校验必需字段

位置：`personal-secretary/knowledge_client.py:70-88`、`personal-secretary/assistant_tools.py:109-112`。

客户端只确认顶层 `data` 是对象。能力响应接受空 `serviceId`，这会削弱跨知识库切换时的身份绑定；搜索响应缺少 `items` 时被默认成空列表，`complete` 也可缺失，导致协议错误可能被呈现为“没有结果”；若 `items` 不是列表或元素不是对象，工具会抛出未映射的运行时异常。创建状态字段也没有校验。

最小修正：按各 endpoint 校验必需字段、类型、枚举和非空 ID；协议错误映射为受控 `KnowledgeError`，不能伪装成无命中或处理中。

### F5 · P2 · 搜索摘要绕过总上下文预算

位置：`personal-secretary/assistant_tools.py:109-119, 125-126`。

30,000 字符计数只累加正文读取内容，没有累加搜索摘要。一次搜索最多输出 20 个结果，而服务端每条摘要上限可达 2,000 字符，因此单次搜索可把最多约 40,000 个摘要字符送入模型；这超过 FD/契约对摘要和正文合计的预算。

最小修正：累计摘要与正文的字符数；在返回工具结果前按剩余预算限量或拒绝超额结果，同时保留 `complete`、`hasMore` 和诊断信息。

### F6 · P2 · HTTPS 非默认端口的可信来源链接被丢弃

位置：`personal-secretary/assistant_settings.py:40-44`。

链接校验把候选 URL 端口限制为省略或 443，再与配置的 `publicBaseUrl` 比较。契约允许配置 HTTPS API 主机及端口，且浏览器阅读入口可独立配置；当受信入口使用 HTTPS 非默认端口（例如 8443）时，合法来源链接一律返回 `None`，引用不能打开。

最小修正：校验候选 URL 的 scheme、凭证和规范化 origin，并允许其端口与已验证 `publicBaseUrl` 的端口一致；仍拒绝任何不同 origin。

### F7 · P2 · 未知写入结果未作为持久状态记录

位置：`personal-secretary/assistant_tools.py:148-160`。

本地请求在发送前持久化为 `pending`。POST/对账失败时，异常分支只向模型返回“结果未知”，没有把记录状态更新为 `unknown`；代码随后仅会从 `pending`/`unknown` 读取。重启后虽会重新 GET 对账，因此同 key 重放护栏仍在，但本地持久状态不符合 FD 对 `pending/unknown/succeeded/failed` 可恢复状态的要求，不能准确区分已受理与结果未确认。

最小修正：在对账无法确认结果时持久化 `unknown`，并保留 key、body、serviceId；仍然要求后续先查状态，404 才允许同 key、同 body 重发。

### F8 · P2 · 契约 fixture 验证项没有证据

位置：FD Work Item 1.5 与 Acceptance 最后一项；本次 diff 未新增知识库 fixture 或测试文件。

本次被审查的改动中没有同一组 API request/response fixtures，也没有覆盖字段校验、错误响应、失败恢复或重启对账的执行证据。因此 Work Item 1.5 标记完成缺少支撑。按本轮授权，Reviewer 未运行测试或网络验证；这不是测试通过，也不要求在本轮越权执行。

最小修正：按项目授权规则取得测试工作授权后补充/验证契约 fixtures；在可用证据到位前，不把该验收项记为已验证或完成。

## 独立身份、基线与范围

Reviewer session：`reviewer-FD065-20261031-a`。已领取 `FD-065-000004-review-requested`。receipt 对应 FD revision 4、digest `df8bf1fe5c78b2bd4543c4f878cbad87a2f7fcd8e9e81fe685d3ad1551a3a37e`。

审查基线 `aee3f7f4f9d4329493fb9ed491e7338e6bbdebb0` → `aa7caa94dcc526785aee7e41eeac78c2295368a7`。包括初始实现 `6342099`、fail-closed 修复 `7220c9d` 和 FD Verification 更新 `aa7caa9`。范围为 FD-065 的 personal-secretary 客户端、工具/设置/API/UI 调用路径、FD 和唯一 API 契约 `docs/personal-secretary-knowledge-handoff.md`。

正向静态核对：Bearer token 存于既有私密配置文件并通过 `credentialRef` 读取；TLS 使用默认证书/主机校验且没有跳过选项；客户端不自动跟随重定向；请求/响应字节上限与 timeout 有实现；来源链接限制到配置的 HTTPS 主机；写入在远端调用前保存 key/body/serviceId，重启时先查状态，只有 `CREATION_NOT_FOUND` 才用相同 key/body 重发；创建失败现在返回失败状态提示，不再显示处理中。fail-closed 修复会在 capabilities 探测前持久化 disabled 状态，解决旧连接在重配置失败后重启又启用的问题。

## 实际命令、未运行与残余风险

实际执行：`aiw fd show FD-065`；`aiw fd claim --session reviewer-FD065-20261031-a FD-065 FD-065-000004-review-requested`；`git rev-parse HEAD`；`git merge-base develop HEAD`；`git log -3 --oneline --decorate`；基线到 HEAD 的 `git diff --stat`、`git diff --name-status` 及针对客户端/工具/设置/UI 的 `git diff`；针对变更符号的 `rg` 与定向源码读取；只读 Python 源码读取以确认 UI/FD 中问号是文件字面字符而不是终端字体问题。

未运行测试、fixture、浏览器、compile、服务/网络请求、验证脚本或构建。残余风险包括上述缺陷、未验证的真实 API 响应和部署配置。静态审查确认部分安全护栏，但不能据此声称端到端行为已通过。
