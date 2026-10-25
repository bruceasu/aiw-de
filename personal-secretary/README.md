# 个人秘书 Worker / Personal secretary

秘书是用户自己运行的进程和 SQLite 私库。平台不运行此代码、不打开私库，也不接收私人对话、策略原文、模板、草稿或模型推理。专业执行/评审 Worker 保持原协议。

旧平台秘书实现和双模式已移除。旧 API 不兼容；V8/V9 历史迁移和数据保留封存。数据库存在旧私人数据时，平台的新委托启用会拒绝；本次不执行实际迁移、切换或数据清理。

## 日常使用（默认）

专业任务准备受阻后，`POST /api/professional/recheck` 可按原专业记录 `id` 与 `revision` 重新读取回执并恢复结果跟踪；接口仍要求本机会话、同源和 CSRF，不新建任务、不重新发送原提交，也不继续已取消或移除的工作。执行已有专业计划时，模型仅查询选项却宣称完成，会收到一次明确的委派纠正；仍未提交时报告“尚未提交 Worker”，不再误报为文件保存失败。

个人秘书按需读取自己的 `skills/` 目录。目录中的 Skill 面向日常助手职责，包括日常偏好梳理、个人任务规划和资料研究；不加载专业 Worker 的 Skills。模型先查看名称与简介，再读取当前请求相关的 `SKILL.md`，不会预先加载整个目录。Skill 只提供指导文本，不会增加秘书的工具、文件授权或执行权限；脚本不会执行。筛选依据和来源见 [`skills/README.md`](skills/README.md)。

### 是否需要安装 Qt？

**不需要单独安装 Qt Desktop、Qt Creator 或 Qt SDK。** 通过 pip 安装的 PySide6 预编译包已包含 Qt 运行库，详见 [Qt 官方安装说明](https://doc.qt.io/qtforpython-6/gettingstarted.html)。

| 使用方式 | Qt 依赖 | HUD / RPG 双主题 |
| --- | --- | --- |
| 浏览器中的 Web 控制台与秘书 | 不需要 Qt | 支持 |
| 新版桌面秘书 | 安装 `requirements-desktop.txt` | 支持 |
| 原 Windows Tk 桌面秘书 | 不需要 Qt；Python 需包含 Tkinter | 使用原界面，不支持新版双主题 |

在本目录执行以下命令安装新版桌面依赖并启动，安装和启动应使用同一 Python 环境：

```powershell
python -m pip install -r requirements-desktop.txt
.\start-desktop.ps1
```

暂时不安装 PySide6 时，可以运行 `.\start.ps1` 使用浏览器界面，或运行 `.\start-desktop.ps1 -LegacyTk` 使用原 Windows 桌面。缺少 PySide6 只影响新版桌面启动，不影响浏览器使用。Linux 的图形运行库要求与当前验证范围见 [双主题说明](../../docs/APPEARANCE.md)。

### 界面与工作方式

Qt 工作台默认仅保留轻微透明感：完整工作区底色约 94% 不透明，卡片和侧栏叠加后更实，空白区域也遮挡桌面内容；文字和按钮保持清晰。`-Opaque` 为工作台和人物提供不透明回退；浏览器页面保持原背景。托盘与人物右键菜单共用中英日翻译，启动时先恢复上次保存的语言，再同步网页语言。更新后须重新打开 Qt 桌面；这些行为尚未实际运行验收。

Web 和 Qt 内嵌工作台不再显示悬浮人物或大幅人物插画。秘书改用圆形 Q 版头像：左上角品牌区显示约 40px 头像，秘书回复旁显示 32px 头像；HUD 使用银蓝发智能助理，RPG 使用棕发卡通伙伴，切换主题时同步替换。头像使用独立透明素材，不播放全身人物动作。独立桌面 PET 仍由托盘入口控制，其主题和透明背景沿用原实现。重要状态保留在页面状态栏与工作计数中。主题与素材详见 [双主题说明](../../docs/APPEARANCE.md)。这些是源码实现说明，实际桌面显示效果尚未运行验收。

秘书 Web 的“设置 → 外观与语言”中可切换 HUD / RPG / 自动，以及简体中文、English、日语；右上角不再放置这些偏好控件。Qt 桌面菜单跟随页面语言。日期与排序也使用所选语言。用户输入、模型回复和原始记录保留原文。首次使用按浏览器语言选择，之后使用已保存的偏好；不同服务地址分别保存。旧 Tk 界面暂不支持这套语言切换。

Qt PET 托盘菜单提供“启动本地服务”和“停止本地服务”：启动会连接已有服务，服务未运行时才启动；停止需要本机有效会话和 CSRF 校验，并提示会中断当前请求。服务正常取消处理、清理资源并释放监听端口，保留私库和设置；桌面仍继续运行，可再次启动服务。停止确认最多等待 30 秒；超时会报告尚未确认，不会强制结束进程。旧服务不支持停止 API 时会明确提示，首次升级仍需手动关闭旧服务一次。

“退出桌面界面（服务继续运行）”只退出 Qt，服务保持运行；退出会停止 UI 刷新、收起托盘，并允许窗口真正关闭，无需再次点击。关闭工作台的 × 仍只隐藏窗口。若要全部关闭，先选“停止本地服务”，再退出桌面。当前仅进行了编译和静态检查，未运行托盘或服务启停验收。

升级后若国际化没有效果，需要重启**个人服务和 Qt 桌面**，不能只刷新页面或只重启桌面。旧 Python 服务可能读取新版页面，却无法提供新增的 `/i18n.js` 资源。页面会明确提示语言组件加载或初始化失败；详情见 [升级排查说明](../../docs/APPEARANCE.md#升级后语言没有变化)。

Web 与新的 Qt 桌面工作台提供 **HUD / RPG / 自动** 双主题，页面顶部可随时切换。自动默认白天 08:00 使用 HUD、夜间 19:00 使用 RPG，“时间设置”可以调整。Windows 先在当前 Python 环境安装 `requirements-desktop.txt` 再运行 `start-desktop.ps1`；Linux 使用 `sh start-desktop.sh`。Qt 人物悬浮窗与工作台复用 Web 功能、提供托盘和真实工作计数。透明效果异常可使用 `-Opaque` / `--opaque`。原 Windows Tk 入口为 `start-desktop.ps1 -LegacyTk`。安装和平台限制见 [双主题说明](../../docs/APPEARANCE.md)。

Web 与桌面聊天会将模型回复中的常用 Markdown 显示为格式化文本：标题、加粗、斜体、列表、引用与代码块；Web 还提供表格和 HTTP/HTTPS 链接，桌面表格以等宽文本展示，链接显示来源地址。用户输入仍按原文展示。不会解释原始 HTML、执行代码或自动加载图片。支持常用语法子集，不保证完整 CommonMark 或复杂嵌套。内容不变时自动刷新不会重建聊天；阅读旧消息时新回复不会强制滚到底部。

工具请求使用 JSON 动作；最终回答可以是普通文字，在搜索或读取工具执行后也同样接受。普通回答仍需通过原有结果校验：要求保存文件时须有实际写入记录，天气、汇率和换汇等当前信息须有查询来源，未完成的专业任务不能仅凭文字标为完成。格式损坏的 JSON 工具指令不会执行；这类接入错误不再要求用户换模型或简化问题。换汇回答应给出查询日期、汇率来源、计算过程及手续费或点差的影响。

### 私人会话、历史消息与记忆

Web 与 Qt 内嵌界面使用同一套会话入口。“新对话”在首次发送时建立独立本地会话，标题取首条消息的第一行；选择已有会话则继续原来的讨论。旧“日常对话”和工作关联消息按原 ID 读取，不自动搬移或删除。底层保留兼容旧桌面的 workId 作为存储锚点，新建的纯会话锚点不出现在 Web/Qt 工作列表中。模型通过 `create_work` 动作判断是否需要将当前会话关联为可追踪的工作；闲聊、简单问答和一次性查询无需用户预先创建工作。一个会话当前最多关联这一项工作；讨论另一项独立工作时可新建对话。

消息区默认只显示最近 **10 轮**，点击“显示更早消息”每次展开最多 **20 轮**，可随时收起。早期消息按需分页读取，展开历史不会扩大传给模型的原始对话条数。`/api/state` 的消息数组只返回全局最近 60 轮，另提供总数；完整原文保存在私库，可通过当前会话的分页接口读取。工作记录、执行详情和备份仍保留各自的数据，不是整个状态接口的分页改造。旧 Tk 客户端没有分页入口，建议使用 Web/Qt 查看完整历史。

当未摘要的成功对话超过 12 轮时，下一次请求会先整理早期消息，再附带摘要与最近 8 轮成功对话。每次摘要最多处理 12 轮，保留最近 8 轮上下文（每轮用户文字最多 600 字符、回复最多 1400 字符，并标明截断）；旧会话积压较多时会随后续请求逐步整理，也可在“会话摘要与记忆”中手动更新。摘要最长 2000 字符，每轮摘要输入最多取用户消息 1000 字符、回复 2000 字符，并标明截断；摘要不等于完整原文，也不证明执行成功。自动摘要失败会保留原文与已有摘要，并显示可重试提示，不阻止普通对话。摘要会额外调用一次当前 LLM，受相同的模型权限和超时约束。

摘要可提取稳定偏好和已确认的长期事实，单条最长 400 字符，总量最多 200 条。后续会话按文字相关性选取最多 4 条作为不可信背景数据，不默认附带全部历史。没有向量数据库或额外模型依赖；措辞差异可能使相关记忆未被选中。可在“查看长期记忆”中查看和删除单条记忆。模型被要求不保存凭证、临时查询和未经确认的推测，但自动提取仍可能不准确，应以用户更正为准。

删除对话需明确选择“保留记忆”或“同时删除派生记忆”，并确认。处理中、定时待执行或仍有待确认操作的会话不能删除。删除会清理该会话的消息、日常处理记录及其历史版本；纯会话锚点也会删除。已经建立的实际工作、工作结果和专业平台回执保留，工作记录中携带的会话摘要会清除。保留的记忆仍可被其他会话引用；同时删除记忆只影响以该会话为来源的条目。已有备份、SQLite WAL 或磁盘残留不属于本操作范围，这不是安全擦除。更新后需要重启本地个人服务并刷新页面。

### 保存工作与安排同步

“工作与安排”保存私人工作后，会根据服务端保存回执立即更新工作列表，并自动在“安排这项工作”中选中新工作、带入版本，无需等待五秒刷新或调整窗口大小。后台状态读取用请求版本过滤过期结果，避免保存前发出的读取覆盖新回执。下拉框按需更新原有 option，不在每次轮询时整批替换；工作内容相同的刷新不会重建选项。此修正经过静态及编译检查，尚未运行 Qt 原生下拉弹窗的重绘验收。

### 已删除工作：恢复与永久删除

Web 与 Qt 的“设置 → 已删除的工作”支持恢复，也支持永久删除。每项工作显示已有派生记忆条数，并提供“保留已保存的记忆”（默认）和“同时删除派生记忆”；确认对话框会再次说明删除范围。普通状态刷新不重建未变化的列表，也会在必要重建时恢复记忆选项，避免刷新把用户选择改回默认值。

永久删除只接受当前版本、状态为 `TRASHED` 的工作。入口 `/api/work-delete` 沿用个人服务会话和 CSRF 校验，同时检查本机忙碌状态、专业任务、待确认操作以及关联的待提交／待核对平台请求；不满足时拒绝，并提示恢复后核对。通过检查后，在同一个私库事务中删除工作、会话消息、日常处理与确认记录、关联提醒及这些记录的历史版本。选择删除记忆时，一并删除以该工作为来源的记忆和版本；选择保留时，已有记忆仍可供其他会话相关性检索。删除时不自动生成摘要或新记忆，也不调用模型。

结果文件、其它独立工作、专业平台任务／回执及已有备份保留；永久删除本地工作不会向平台发取消或删除请求，也不是磁盘安全擦除。本地工作不可从已删除列表恢复。此为源码实现说明，实际删除、恢复、并发保护和 UI 交互尚未运行验收；更新后需重启本地个人服务并刷新页面。

Codex 负责决定下一步，秘书执行宿主搜索与文件工具，并把结果交回模型。模型回合确认完成后，Runtime 优先读取最终回复，也接受仅包含单个 JSON 动作的 `commentary`，并兼容终态 `turn.items` 携带的文本；普通进度说明不会被当作完成结果，多个不同动作不会执行。当前每个模型步骤仍启动独立 app-server 并握手，因此多步工作有额外启动开销；未测量实际耗时。

联网与文件操作由秘书的宿主工具执行：模型返回 `search`、`read_url`、`read_file`、`write_file` 或 `delete_file` JSON 动作，秘书按既有范围与确认规则执行，再把结果放入下一轮 `steps`。这不要求安装额外插件，也不依赖 Codex 原生工具。Codex 与 Agents SDK 共用该宿主动作说明；不会因原生工具列表为空而禁用宿主工具。连接失败会显示对应步骤、固定错误码及可用的 JSON-RPC 数字错误码，不显示原始协议正文、私人上下文或凭证。

需要 Python 3.11+；个人服务与浏览器界面不需要第三方 Python 包，Qt 桌面另需 `requirements-desktop.txt`。Windows 用户在秘书目录运行 `./start-desktop.ps1`，GUI 会自动连接本机个人服务；尚未运行时会尝试在后台启动。Linux Qt 桌面使用 `sh ./start-desktop.sh`。Web 用户运行 `./start.ps1`，Linux/macOS 使用 `bash ./start.sh`。无参数即可使用日常模式，不需要 Owner ID、Worker ID、平台凭证或手工创建 Plan。现有用户首次升级可先使用原来的启动命令启动一次，记住原私库身份，以后直接无参数启动；旧数据库不会被搬移、合并或删除。

默认从 PATH 启动 Codex，沿用本机配置和已有登录，无需先填写模型设置。切换后端或覆盖模型时，打开“设置模型”（Web 中为“设置”）：

- `codex-app-server`：使用秘书目录内的 Codex App Server 适配器。通过 PATH（或已有的 `AIW_CODEX_PATH`）找到已安装的 Codex，沿用本机 `CODEX_HOME`、配置和登录，无需专用目录或重复登录。模型、服务地址和密钥可以留空；程序路径仅为高级可选覆盖项。Windows npm 的 `codex.cmd` / `codex.ps1` 会解析到已安装的原生程序，不通过 cmd/PowerShell 执行 shim。
- `openai-agents`：使用秘书目录内的 OpenAI Agents SDK 适配器，调用 Responses API。先在运行个人服务的 Python 环境中安装本目录 `requirements.txt`；填写模型、服务地址和密钥。地址留空使用 `https://api.openai.com/v1`，密钥也可由 `OPENAI_API_KEY` 提供。不会自动安装依赖。
- 原有 Chat Completions 和自定义 argv 程序仍可显式选择，旧设置不会自动切换后端。Chat 服务地址仍会追加 `/chat/completions`。

秘书目录内维护自己的 Runtime、RPC、期限和取消实现；运行与分发不依赖 `workers/process-python` 或其它 Worker 源码。私人上下文、业务工具、文件授权和平台身份留在秘书。每次模型调用使用独立生命周期和临时工作目录，已有私库负责对话连续性。SDK 不提供原生文件/命令工具；Codex 沿用本机配置，当前请求使用只读沙箱、不接受审批，并关闭 shell、网页搜索及多 Agent。秘书不再生成或检查专用 permission profile，不修改用户配置文件。旧设置中的 `codexHome` 不再参与调用，原专用目录不会被自动删除。如果之前按旧说明在启动终端设置了临时 `CODEX_HOME`，请从平时能直接运行并已登录 Codex 的正常终端重启秘书。

原生 Windows 使用不可 breakaway、kill-on-close 的 Job Object：bootstrap 纳入 Job 后才启动 Codex；正常完成、超时或取消均终止整个 Job 并检查活动进程归零，终止结果不明则暂停秘书。不能把父进程退出或 taskkill 的返回码当作进程树终止证据。此为源码实现说明，真实 Windows 取消竞争、CLI 协议、登录刷新与模型调用尚未运行验证。

桌面默认使用 `pythonw.exe`，但 Codex 的管道引导进程使用同目录的 `python.exe`，并以 `CREATE_NO_WINDOW` 隐藏启动，避免 GUI 解释器的标准句柄启动差异。此调整不切换 Python 环境，也不改变 Codex 路径、登录目录或 Job Object 管理。`initialize` 的 `CODEX_RPC_TIMEOUT` 表示未在期限内收到握手回执；不能据此判断登录或模型服务是否正常。2026-10-25 经授权进行了同机初始化对比：命令行引导 0.28 秒成功，旧 `pythonw` 引导在 7 秒诊断期限内超时，修正后的后台引导 0.19 秒成功；三次均确认进程树已清理。该检查未调用模型，完整对话仍未验证。更新后须退出并重新启动本地个人服务，让它加载修正后的 Runtime；只刷新网页或重开桌面窗口不会更新已有服务进程。

可选择常用资料目录，也可留空、在具体工作中指定文件位置。保存一次后自动恢复，无需重启 GUI。模型服务可能按用量计费；每轮处理最多 12 次模型请求，达到长对话阈值时最多另加一次自动摘要请求，不自动购买、充值或增加额度。一次秘书模型调用最多 120 秒，不自动重试或切换提供方。可使用 `../../scripts/compile-secretary-runtime.ps1` 仅编译本次涉及的 Python 与浏览器 JS，不导入 SDK、不启动服务或访问网络。

直接在 GUI 或 Web 对话框说出需要帮助的事，不必先创建工作。保存工作时填写“做什么”和可选的结果文件位置，然后点击“现在处理”或安排时间。Web 的“安排这项工作”默认选择“马上执行”，无需填写时间；只有选择“定时执行”才要求填写未来时间，并按浏览器当前时区处理。两种方式都保留工作版本校验与原有重复任务保护；秘书暂停时需先继续工作，提交成功不等于已执行完成。秘书在本机自动建立日常计划并执行公开信息查询、资料整理、授权文件读取和文本保存；Web 每五秒、GUI 每三秒刷新状态，界面不必等待整个模型调用完成。无后台服务运行期间不能到点执行；定时任务仅在约定的执行分钟内启动，超过一分钟窗口即标记已错过，不自动补跑。马上执行任务在同一次服务运行中正常排队无需反复确认；若停机前尚未开始，服务恢复后需选择现在执行、改期或取消。运行中断的工作先核对已有结果，不自动重放。旧安排没有执行方式记录时也先等待确认，不推测原意。Web 的“待你确认”列出这些工作，桌面“安排执行与恢复确认”显示工作报告，并提供现在处理、取消或重新定时入口。

移除工作：桌面 GUI 在聊天窗口的工作选择框下点击“移除所选工作”，也可在“工作台 → 工作与安排 → 选择工作”下点击“移除工作”。聊天窗口和工作台均提供“恢复已移除的工作”。Web 在“工作与安排 → 我的工作”的对应卡片点击“移除工作”，在“设置 → 已删除的工作”恢复。更新 GUI 后需退出并重新启动桌面程序，已有窗口不会自动增加按钮。

只有超范围文件访问、代码文件写入或不可恢复文件删除需要在“待你确认”中核对完整目标及内容，允许仅适用于这一次。普通保存到指定位置不逐次确认；覆盖现有文件前，旧内容保存在私库旁的 `file-backups` 中。普通“移除工作”可在“已移除/已删除的工作”恢复，无需额外确认。没有付款、发布或命令执行连接器，相关动作会明确报告未执行，不会假装完成。

“暂停秘书/继续工作”控制日常处理，不是平台权限开关。模型、工具失败及未保存目标文件都会报告原因和下一步，不能仅生成提醒后标为完成。公开网页工具只使用 HTTPS GET，阻止本机/内网地址及重定向到这些地址；没有浏览器登录会话或任意 Shell。付款、发布和代码命令不属于默认日常工具范围。

Windows 设置和连接凭据使用当前系统账号的 DPAPI 保护；其他系统使用仅当前用户可读写的文件。模型密钥、平台凭证和 UI 会话不进入私人 JSON 备份。本机通过 `localhost`、`127.0.0.0/8`（包括 `127.0.0.*`）或 `::1` 直连回环服务时，Web 和 GUI 自动建立会话，不需要输入临时 token 或配对码；会话过期后读取状态即可自动恢复。此策略信任本机其他进程，并同时核对实际连接来源、Host 和 Origin，保留内部 Cookie 与 CSRF 校验。公开 HTTPS 反向代理及其他设备仍须临时配对，不依据转发头授予本机信任。旧 GUI 的本机连接文件仍兼容。

新模块为 `assistant_settings`、`assistant_model`、`assistant_tools`、`daily_assistant` 和 `private_config`。业务记录、版本历史和确认记录仍在原 SQLite 私库，不变更表结构。新设置及会话文件单独保存；回退前应先暂停日常处理并核对在途工作和已保存文件，再恢复旧程序，保留私库及文件备份，不自动恢复旧的待执行安排。

使用 `scripts/compile-secretary-desktop.ps1` 可进行本子目录 Python 内存编译、JavaScript 语法编译及 PowerShell 解析，不调用模型、网页、服务、桌面窗口或测试。编译不能证明真实模型、网页访问、DPAPI、桌面自动连接或文件操作已在运行中验证。

## 专业平台连接（高级，可选）

平台导航中的“秘书平台授权（高级）”管理秘书访问专业平台的身份和权限；“专业 Worker 任务配置（高级）”供人手动配置或排查准备记录。秘书已接入专业任务准备接口：直接交代软件实现、需求分析或代码评审等工作，秘书从平台读取自己获准管理的计划、仓库基线、默认工作方法和已批准模型，自动准备任务，交给在线兼容 Worker 领取，并在原工作及对话中跟踪结果。日常聊天、资料查询和文件保存无需配置这两个页面。

专业任务需要已有的秘书平台连接及当前进程授权、计划托管和读取范围、有效执行策略、明确的仓库提交基线、已批准模型及在线兼容 Worker。只有一个可用目标时自动选择；多个目标含糊时询问具体目标。没有可用授权计划时，秘书会用当前工作内容自动创建计划草稿，记住草稿 ID 并报告需要确认的执行范围；创建草稿不会授予权限或启动任务。确认该范围后重新安排即可，无需手填任务配置表。

新增机器接口为 `GET /api/r1/authority/professional-tasks/options`、`POST /api/r1/authority/professional-tasks` 和 `GET /api/r1/authority/professional-tasks/{原请求ID}`。机器身份来自现有独立凭证及进程会话；不能由请求指定 owner，也不能通过此接口授予权限、扩大仓库范围、修改方法或增加预算。执行使用原 AgentFactory、执行策略和 Worker 领取协议，不创建新的 Worker 进程。秘书只发送选定任务及必要上下文，不发送整个私人对话历史。

提交前先在私库保存固定请求 ID。未确认的提交及重启后的在途任务只查询原准备记录，不自动重发或重复创建；缺 Worker、模型、授权或执行预算时报告具体缺项，不把任务创建标记成完成。收到 Worker 的 PASS 报告才标记完成，REPAIR_REQUIRED、失败、取消及等待输入均分别报告。平台等待输入的问题在“工作问题与人工控制”处理。移除工作或暂停秘书会停止本地跟踪和后续工具操作，已经提交的平台任务不会因此撤销；停止专业任务需要在平台操作。

升级需同时重启平台和秘书；Web 刷新，GUI 退出后重开。新增委派记录使用现有私库 decision/work/message 记录，不修改数据库表结构。回退前先核对并停止或等待已提交的平台任务，保留原请求记录；回退代码不会撤销已创建的专业任务。

需要 Python 3.11+，默认 Codex 后端无第三方 Python 依赖；openai-agents 后端按需安装本目录 requirements.txt。登录平台后，在 `/ui/authority` 选择“登记新秘书”，生成凭证并确认读取范围。平台自动关联当前账号并生成唯一短标识；在页面选择秘书和终端后复制启动命令，无需记忆或填写 Owner ID、Worker ID。命令不含凭证，启动脚本会隐藏询问已登记的凭证。已有标识继续有效，轮换凭证时直接选择已有秘书。再启动个人进程建立禁用状态的进程会话，随后在平台确认最小能力及有效期并明确启用，最后在私人 UI 明确启用本地处理。普通 Worker 注册、共享 bearer 或 capability 不代表个人身份。

在用户自己的终端配置 `AIW_PERSONAL_CREDENTIAL`。它不能出现在参数、URL、提示词、导出包或日志中。可选 `AIW_PERSONAL_MODEL_COMMAND` 是用户选择的模型程序 argv JSON 数组；程序从 stdin 读取私人上下文，将最多 16000 字符的答复写到 stdout。不会启用 shell，子进程不继承 `AIW_PERSONAL_*` 环境变量。未配置模型时仍能维护私人记录和手动提交固定能力，但不会伪称已调用模型。

在 `personal-secretary` 目录中可直接使用交互启动脚本：

```powershell
.\start.ps1
```

Linux、macOS 或 Git Bash 使用 Bash 脚本（需要 Bash 和 Python 3.11+）：

```bash
bash ./start.sh
```

Bash 版本使用相同的交互流程、默认值和 `AIW_PERSONAL_*` 配置变量，参数采用 `main.py` 的长选项名称，另有 `--python` 指定解释器（默认 `python3`）。运行 `bash ./start.sh --help` 查看完整选项。请作为独立脚本运行，不要用 `source` 加载。

```bash
bash ./start.sh --owner-id O-EXPLICIT-ID --worker-id my-secretary --platform https://platform.example --data-root "$HOME/private/aiw"
```

高级启动仍可传入平台登记的 owner ID 和平台地址。默认 Worker ID 为 `my-secretary`，应与平台登记一致。即使传入这些参数，启动也不再强制询问 `Dedicated platform credential`：优先使用环境变量或已保存的凭证，没有凭证仍可使用本机日常秘书。需要首次输入或更换专业平台凭证时，PowerShell 显式加 `-PromptCredential`，Bash 加 `--prompt-credential`；明文平台连接仍遵循原有回环 HTTP 确认规则。私人数据目录默认 `$HOME/.aiw-personal`，UI 默认 `127.0.0.1:8766`。退出后恢复原有凭证环境变量。

可预设 `AIW_PERSONAL_OWNER_ID`、`AIW_PERSONAL_WORKER_ID`、`AIW_PERSONAL_PLATFORM` 和 `AIW_PERSONAL_CREDENTIAL`，避免重复输入。脚本参数优先于对应环境变量；支持 `-OwnerId`、`-WorkerId`、`-Platform`、`-DataRoot`、`-HostAddress`、`-Port`、`-PublicOrigin`、`-AllowLoopbackHttp` 和 `-Python`。凭证只通过环境变量或隐藏输入提供。

```powershell
.\start.ps1 -OwnerId O-EXPLICIT-ID -WorkerId my-secretary -Platform https://platform.example -DataRoot D:/private/aiw
```

原有 Python 命令行继续要求显式身份和平台参数：

```powershell
python personal-secretary/main.py --owner-id O-EXPLICIT-ID --worker-id my-secretary --platform https://platform.example --data-root D:/private/aiw
```

默认个人 UI 监听 `127.0.0.1:8766`，监听成功后自动尝试在默认浏览器打开 UI 地址，本机访问直接进入工作空间。浏览器打开失败不影响服务，可手动访问控制台显示的地址。远程模式在终端打印五分钟有效、最多五次尝试、使用后失效的一次性配对码，码不放入 URL。无桌面或远程部署可通过 Python/Bash 的 `--no-browser` 或 PowerShell 的 `-NoBrowser` 关闭自动打开。每个服务有独立 HttpOnly/SameSite 会话与 CSRF token，严格验证 Host/Origin，不接收平台 cookie、不开放 CORS 或 iframe。远程监听必须明确配置 HTTPS 反向代理的 `--public-origin`。平台的明文 HTTP 仅在用户明确使用 `--allow-loopback-http` 且 URL 为回环地址时接受；禁止凭证随重定向转发。

每个 owner/Worker 使用独立目录与身份记录；复制他人的数据库会被拒绝。私人数据目录的操作系统访问权限由用户管理，尤其是 Windows ACL。平台启用、能力签发和个人 UI 本地启用相互独立。参考进程每次启动生成新 boot UUID，通过 `/sessions` 使旧许可失效并要求平台重新明确启用；平台重启同样失效。服务器能核验声明的 boot 与凭证，不能检测自定义实现隐瞒的物理进程重启；这不赋予其超出已授能力的权限。

自动打开会跳过检测到的 SSH 会话、Windows 服务会话，以及没有 `DISPLAY`/`WAYLAND_DISPLAY` 的 Linux 或其他 Unix 会话。桌面检测依据环境信息，不能保证浏览器一定可用；普通桌面终端仍会尝试打开。

## 自定义模型程序（高级兼容）

日常模式优先使用界面中保存的模型连接。自定义程序可在 Web 设置中选择高级方式，或在启动前设置 `AIW_PERSONAL_MODEL_COMMAND` argv JSON 数组，首项为程序路径；程序从 stdin 读取 UTF-8 上下文，将答复写入 stdout。程序自身的权限由用户管理，不能将其视为受限日常工具沙箱。Web 和 GUI 会显示未配置模型、程序启动失败、调用失败、超时或回复格式错误等明确提示；错误响应只含固定诊断，不回传私人上下文、原始模型输出或子进程 stderr。

## 平台启动认证失败

高级连接出现 `/sessions` 的 401 表示平台未接受当前 Worker ID 与独立凭证的组合；不会阻止本机日常助理启动。需要专业功能时，再核对平台地址、Worker 登记及凭证是否提交保存。点击“生成并复制”仅填充及复制，不执行登记或轮换；轮换后旧凭证失效。凭证丢失时在平台选择“轮换已有秘书的凭证”，提交后用新值启动。此阶段不要求先启用专业秘书，也不使用个人服务的一次性配对码。不要将凭证发送到聊天或日志中。

## Windows 桌面秘书（高级及旧版兼容）

新版本桌面秘书可自动启动并连接本机个人服务，使用同一组私人记录和业务接口。它提供可拖动的透明女性角色、屏幕边缘吸附、可切换置顶、文字对话、模型设置、待确认操作和工作安排，以及 Windows 托盘菜单。Web 保留完整功能。桌面端只支持 Windows，要求 Python 3.11+ 含 Tk；无需安装第三方 Python 包，也不会自动下载安装依赖。以下手动配对方式仅用于旧服务、手动指定连接等兼容场景；正常使用不需要这些步骤。

更新后需要重新启动个人服务，让本机免配对策略生效；本机 Web 和 GUI 此后直接连接。专业平台的进程许可仍遵循原规则。日后单独启动或关闭桌面 UI，不会重新启动 Worker，也不会自动启用专业动作申请。下面步骤仅供旧服务兼容使用。

1. 在已经登录的 Web 页面进入“状态与备份 → 连接桌面秘书”，选择“生成桌面配对码”。
2. 在 `personal-secretary` 目录的另一个 PowerShell 终端运行：

   ```powershell
   .\start-desktop.ps1
   ```

3. 在桌面连接窗口填写与服务启动终端一致的地址（默认 `http://127.0.0.1:8766`），粘贴新配对码。桌面端不填写平台凭证、Owner ID 或 Worker ID。
4. 点击人物打开聊天，选择工作或点击“日常对话”。复杂内容通过“工作台”弹窗填写。聊天需要个人服务已配置 `AIW_PERSONAL_MODEL_COMMAND`；未配置模型时，仍可保存工作和维护表单。

使用不同端口或解释器时：

```powershell
.\start-desktop.ps1 -Service http://127.0.0.1:8877 -Python C:/Python312/pythonw.exe
```

桌面端仅连接带显式端口的本机 HTTP/HTTPS 服务，不使用环境代理，不跟随重定向；HTTPS 使用系统默认的证书验证。首次也可先用尚未使用的服务启动配对码连接桌面，再在桌面“连接与设置”生成新码，供 Web 登录。每个界面有独立的内存会话；配对码五分钟有效、最多五次尝试且只能使用一次。生成新码会替换前一枚未使用码，现有登录会话保持有效。Cookie、CSRF token、配对码不会保存到桌面配置文件或放入启动参数/URL。

右键人物或托盘图标可打开对话/工作台、刷新状态、暂停动作申请、切换置顶、隐藏/显示人物、打开 Web 或退出桌面界面。托盘不可用时禁用隐藏人物，保留退出入口。关闭对话或表单窗口会隐藏该窗口并保留当前录入；退出桌面界面不会停止服务或回滚已发送操作，等待结果时退出会明确提示核对原回执。

两种 UI 的状态以最近一次读取为准，可手动刷新。版本冲突不会覆盖他人修改；刷新后重新载入相应记录再保存。目标提案须先本地预览并核对精确原文/摘要；编辑来源字段后原确认失效。提交尝试后只提供原 ID 回执查询，UNKNOWN/网络异常不会触发自动重发。平台问题、资料、取消和备份仍可通过菜单打开 Web 完成。

安排表单展示并固定载入时的私人正文、Plan 引用、状态及版本。普通刷新发现记录变化时会禁用安排确认，必须点击“重新载入所选工作”并核对内容。目标窗口的“原请求回执与历史”从已持久化的 SELECTED_GOAL 决策载入原请求；退出重开或重新配对后仍可选择原 SENDING/UNKNOWN/RECORDED 请求查询。历史查询与当前预览分别展示，历史入口不会启用重发。

Web 再次配对使用原有的 `ui/app.js` 和 `ui/console.css`，避免旧进程不认识新增静态资源路由而导致按钮未绑定。配对处理在其它页面初始化前绑定；仅在用户显式生成时读取当前会话 CSRF 并请求新码。点击后立即显示等待信息，十五秒未完成会报告超时，不自动重试。旧进程缺少 `/api/pairing` 时明确提示更新并重启；脚本未加载时保留“配对组件尚未就绪”的提示。更新后先强制刷新页面；若提示接口尚不支持，需重启个人服务并重新配对。独立的 `pairing.js`/`pairing.css` 保留兼容旧页面引用，按钮绑定标记防止重复请求。

桌面代码分为 `desktop.py`（Tk 窗口与用户操作）、`desktop_client.py`（私人服务会话/请求）和 `desktop_win32.py`（窗口位置/托盘）。人物素材及生成提示词见 [desktop-assets](desktop-assets/README.md)。窗口工作在主线程，单次服务请求在后台线程执行，结果通过队列返回；关闭 UI 不调用 `Runtime.cancel()`。

`scripts/compile-secretary-desktop.ps1` 仅做这次变化的 Python 内存编译、JavaScript 语法编译和启动脚本语法解析；不导入 Tk/Win32 模块、不打开窗口或启动服务，不运行测试、模型、网络、下载或打包。编译通过不代表透明窗口、托盘、多显示器或多界面配对已在桌面验收。

在包含其他未提交工作的目录中，可用 `-Revision HEAD` 编译实际提交版本中的 Python 和独立配对脚本。启动脚本必须与该版本逐字节一致，才继续做 PowerShell 语法解析。临时 JavaScript 源文件编译后删除，不保留可分发产物。

## Web 使用与恢复

个人页面使用与 Web Console 一致的深色配色、卡片和表单样式。配对登录后，通过固定导航切换工作总览、工作与安排、私人对话、代理策略、平台协作、私人资料以及状态与备份；每次只显示当前分区，窄屏导航改为横向排列。

工作安排和私人对话按工作标题选择，安排与取消操作自动带入当前私人版本。策略和资料可从已有记录载入编辑；修改策略仍须重新勾选确认。总览数量、对话内容和待处理请求均来自私人状态快照，页面显示读取时间；登录后的可见页面每五秒读取一次私人状态，也可手动刷新。完整状态和单条记录的 JSON 放在可展开的详情中。

分区切换不会读取平台问题、调用模型或启用动作申请。平台协作仍须先显式生成本地预览，再核对原文与摘要并确认提交；修改预览来源字段后须重新预览。页面不自动重发未确认请求，也不改变平台授权、本地启用或配对会话的边界。

私人资料中的草稿可点击“删除草稿”，确认后按当前版本删除，列表随即刷新；其他窗口已修改的草稿会拒绝删除，需刷新后核对。删除只移除本地当前草稿，历史版本及删除记录仍保留在私人备份中。复用已删除草稿的标识时，版本继续递增，旧窗口不能覆盖或删除重建后的草稿。

草稿删除入口在 Web 的“私人资料 → 资料列表 → 草稿”，选中类型后在对应卡片点击“删除草稿”；桌面 GUI 目前不提供私人资料列表。工作列表里状态为 DRAFT 的私人工作是另一种记录，当前可在 Web“状态与备份 → 取消安排或撤销私人策略”选择“私人工作”取消，取消不等同于删除。

未关联专业 Plan 的新安排走本机日常助理执行器，使用私有日常计划，不需要平台 API 或专业 Worker。`POST /api/r1/authority/personal-plans` 和 `GET /api/r1/authority/personal-plans/{原工作ID}` 仍作为高级平台草稿创建和查询入口保留，但不参与默认日常安排。它们原有的身份、进程和权限契约不变。

新安排和“现在处理”均由秘书统一规划；显式关联的专业平台 Plan 会作为准备任务的目标。已存在的旧版高级安排仍保留原协调处理方式。托管、读取范围、执行权限及专业 Worker 链路继续有效；不会把“创建 Plan”或“发送启动请求”显示为任务执行完成。

私人工作也可关联一个已在平台明确确认托管的干净 Plan 草稿。安排时固定业务版本和内容摘要，只接受未来整分钟；错过时间或内容变化后要求重新确认，不追赶。未执行或受阻时，工作卡片显示原因及报告时间；启动待确认、等待处理、等待补充信息、任务失败和后台异常也有对应报告，报告保存在私人版本历史中。旧的无 Plan 安排到期后报告“未执行”，不会再仅生成提醒后标记为完成，也不会自动补发旧私人说明。每次专业完成只在平台记录真实事实；个人协调读取当前源 attempt 后请求有界推进。额外修复、技术重试、仅重评、模型、审批、发布、删除等仍各自受固定能力约束。

私人策略关联已经人工确认的精确平台能力，模型只能建议是否申请，不能扩展动作/参数/预算。DENY 原文留私库，仅发送最小禁止投影及私有版本摘要；先在平台收窄并暂停，再落私库，失败继续暂停。解除禁止或交回须在平台明确操作。

决定与 outbox 同一私库事务提交。请求 ID 和原信封保持不变；响应丢失先查原回执，仅在确认不存在时重发原信封。UNKNOWN 只查询，不重放、不退款、不换 ID。平台人工对账需固定 attempt 和明确证据；不会自动启动重试。私人数据库/平台实例标识不匹配时停止处理。备份包含私人记录、outbox 与回执副本，包含业务参数但不包含平台凭证或登录会话；恢复后仍须人工重新启用。

## 引导连接与代理授权

确认操作统一使用项目的 `workspace-confirm` 布局：秘书和 Control Plane 共用网页确认组件，显示说明、操作对象、取消和确认按钮，删除操作使用危险色；布局跟随 HUD/RPG 主题和当前语言。Qt 托盘停止服务的确认使用同样的分区和主题色，不使用系统原生确认框。默认聚焦取消，按 Esc 取消，不提交操作。尚未进行浏览器和 Qt 视觉运行检查。

入口为秘书的 **设置 → 专业平台连接与代理 → 打开连接与授权向导**。在平台登录后，选择连接本机秘书；在打开的秘书页面确认连接，再回到平台选择计划、允许代理的动作、期限和每种动作的累计次数。身份和连接凭证由向导处理，不需要填写内部 ID 或复制启动命令。浏览器窗口被拦截时，可使用折叠区域的备用连接码；连接码应当作为凭证保密。

Qt 桌面通过系统浏览器完成平台登录和连接确认；浏览器中的秘书设置与桌面共用同一本地服务。跨窗口连接只接受对应窗口及来源的消息，并需要本地显式确认。凭证不放入 URL、浏览器 Storage 或模型提示；连接文件使用既有私有配置保护（Windows DPAPI）。连接过程不移动原 SQLite 私库，也不改动其物理目录身份；已绑定私库只能重新连接原平台身份，不能混用另一位秘书。备份仍不包含连接凭证。

初次可以只确认当前秘书进程，允许建立计划草稿。已有计划可配置范围内的计划推进、失败任务重试和工作问题答复；审批批准、拒绝和停止计划可单独选择。每项设置仍由平台检查当前登录人的角色、所属账号和原授权版本。确认新的代理设置会按原权限契约替换所选动作的上一期授权，应核对范围和次数。进程重启后需重新确认，可使用“确认当前秘书进程，保留原授权”，不会新增授权或重置累计次数。已有托管计划的期限和暂停状态不会由向导自动更新。

秘书发现连接或代理范围缺失时，会在 **待你确认** 保留申请、原任务和上下文。平台保存授权时在同一事务中持久记录通知；仅存在等待授权的申请时，秘书后台才约每 5 秒读取当前授权并继续，申请全部继续或被拒绝后停止授权轮询；没有申请时不为授权检查发出请求。已受理操作的回执和工作问题按实际跟进需要单独处理。不要求浏览器打开或再次点击按钮。“重新检查授权”仅用于手动重试。离线秘书上线后，如存在等待授权的申请，会自动恢复检查并读取通知：同一身份、同一凭证、同一代次且仍有效的人类确认，可承接一次新进程会话；承接、手动停用、轮换凭证会改变代次，不能重复使用旧确认。该承接不扩大计划、文件或动作范围，也不延长授权期限。普通连接检查保留现有凭证与会话，修复连接时才明确轮换。拒绝申请则停止等待。已受理任务继续跟踪原回执；结果未知或继续过程中断时要求核对，不会换新标识自动重复提交。

跟进专业任务时，秘书可以使用有效范围内的 `A19_ANSWER` 回答原任务已经明确说明的问题；每个问题版本最多追加一次模型判断。原请求没有的信息、需要人类判断的问题或权限不足，会显示“工作需要你补充信息”，链接到平台的原问题。当前通知在秘书界面内显示，没有发送邮件或外部消息。

选择计划后，向导现在可以同时确认 **文件执行范围**：选择已登记项目，核对允许访问的文件或子目录，决定是否允许修改和本地 Git 操作。页面尝试匹配请求中的项目路径和唯一文件名；有歧义时必须选择项目。保存前从所选仓库解析当前 `HEAD` 为完整提交号，在项目统一确认框中展示仓库、文件范围、固定基线、工具和有效期。确认后在同一事务内保存托管、读取范围、代理动作及执行政策，不要求填写 JSON。有效的原计划代理权限默认保留，不重新发放或重置累计次数。已保存请求使用完整内容摘要核对，重试原申请不会重新授权。

保存按钮固定显示“确认代理与文件执行范围”，不再随勾选状态改名；旁边明确提示本次是否包含文件范围及不能保存的原因。标题旁“前往保存授权”定位到保存区域。授权数据未加载完成时不能保存，避免使用不完整的权限目录。

新建计划的版本号 `0` 是有效版本，保存时必须原样传递；缺少计划时才发送 `null`。前端不会将 `0` 按空值处理，避免平台以“请先读取并核对计划内容”拒绝并回滚整次授权。

日常连接和授权使用独立页面 `/ui/secretary-setup`；`/ui/authority` 保留身份、执行控制和审计等高级管理。原来带 `secretarySetup` 或 `planId` 参数的高级页面链接会转到新页面并保留参数。两个页面的桌面布局均由整个内容区域滚动，滚动条覆盖设置表单与高级面板，不再只滚动标签页内部。保存失败原因保留在保存按钮旁，不会被请求结束后的状态刷新覆盖；失败不代表已授权。

执行政策的规则增加可选 `baseRevision`，记录经人工确认的不可变 Git 提交（40 或 64 位）；缺省时沿用旧规则，序列化不输出该空字段。原计划业务说明和草稿内容摘要不变。专业准备目录从政策读取该基线，AgentFactory 和工具操作同时核对基线。仓库路径只用于匹配已登记的真实引用，不将任意本地路径当成有效仓库标识。授权通过后重新读取当前模型、方法和仓库选择，继续原工作，不沿用授权前缺少依据的模型选择。准备基线时检查所选路径是否存在于提交、是否有已跟踪文件的未提交修改；不自动提交。专业任务使用已提交内容，不包含未跟踪文件。

“已完成授权，检查并继续”分别显示缺少进程启用、计划托管/文件范围、Git 基线、模型、执行暂停/到期、原请求未核对或工作忙碌，不再统一显示“授权条件尚未满足或秘书正在忙碌”。自动检查也会更新申请卡片中的具体原因。

计划检查会一次列出当前进程启用、计划托管、计划读取范围、文件执行政策和模型的缺项；托管不存在、由其他秘书管理、暂停/到期分别提示。后台异常导致的本地停止与平台实际启用状态分开显示，不将本地缓存的 `enabled=false` 当作平台撤权。启动时已有相同身份和凭证的客户端会保留原进程会话，不再额外替换客户端。私有 `GET /api/platform/diagnostics?planId=...` 经本地会话认证，只使用已有进程会话执行 GET，不启用、不创建会话、不授予权限、不提交任务；输出缺项及最近后台异常类型，不输出凭证。平台端对应 `GET /api/r1/authority/professional-tasks/diagnostics` 只允许当前秘书读取所属账号创建的计划。平台版本未更新返回 404 时，明确提示重启平台后端。

**当前边界：**项目必须先在平台登记并有 Git 提交；向导不会初始化仓库、创建首次提交、解除暂停或撤销、启动专业 Worker、添加模型，也不会授权任意命令。既有执行预算及用量保留；强制预算模式缺少预算/定价时明确阻止保存并指向高级设置。更新文件范围会替换此计划的旧工具和命令范围，确认框会提示；高级命令配置仍在原入口。模型与专业 Worker 实际可用性继续由平台在准备任务时检查。创建草稿或完成代理授权不代表文件执行已经完成。

上述为静态实现。更新后需重启平台和秘书服务才能加载；尚未进行真实登录、浏览器跨窗口、授权续接、故障恢复或模型代答的端到端验证。`scripts/compile-secretary-setup.ps1` 仅离线编译 Java、Python 和 JavaScript，不执行这些流程。

## English operations

Platform setup confirmations are durable. Only pending authorization requests trigger background approval checks, about every five seconds. Checks stop when all requests resume or are rejected, and restart after an offline period only if a pending request exists. No browser or second confirmation click is needed. Receipt tracking and task questions run only when relevant work exists. A valid, unused confirmation can reach one returning process with the same identity, credential and generation. Disablement, credential rotation, expiry or earlier delivery prevents reuse. Task and file permissions remain independently enforced.

Web 的“查看工作详情”以独立窗口展示工作说明、最新结果或失败原因、安排、已保存文件及本地处理步骤；原始 JSON 收在“原始数据（排查时使用）”中。打开详情后后台仍读取最新状态，但不会替换正在阅读的内容；有变化时提示“有新记录”，点击“更新详情”后才切换。关闭详情后列表显示当前状态。失败原因在工作卡片中也会单独突出显示。

Run this Worker in the user's own environment. Use Python 3.11 or newer. No extra package is needed. For the guided flow, open **Platform connection and delegation** in secretary settings. Connect through the platform page, confirm locally, then choose a plan, actions, expiry and usage limits. You can confirm repository, file access and a fixed Git baseline in the same form without JSON. Existing IDs stay valid. Reconnecting an existing secretary rotates its credential. Advanced startup scripts still accept `AIW_PERSONAL_CREDENTIAL` or a hidden prompt; never put credentials in commands, URLs or model prompts. Arbitrary commands and mandatory budget settings still use advanced configuration.

Run `.\start.ps1` from this directory for interactive setup, or `bash ./start.sh` on Linux, macOS or Git Bash. Both scripts default to Worker ID `my-secretary`, offer the local platform at `http://127.0.0.1:8080`, ask for explicit loopback HTTP consent, and read missing credentials with hidden input. The Bash script defaults to `python3`; use `--python` to select another interpreter and `--help` to list options. Run it as a separate script, not with `source`. Set `AIW_PERSONAL_OWNER_ID`, `AIW_PERSONAL_WORKER_ID`, `AIW_PERSONAL_PLATFORM` and `AIW_PERSONAL_CREDENTIAL` to reuse your settings. Script parameters override the matching environment variables. The owner and Worker must match the enrolled platform identity. You can also start `main.py` with explicit owner, Worker, platform origin and private data root. A model command is optional and must be an explicit argv JSON array in `AIW_PERSONAL_MODEL_COMMAND`. It reads private context from stdin. Its output is advice, never platform authority. Dedicated credentials are removed from the model process environment.

The Worker attempts to open the private UI in the default browser after binding its listener. It skips this in detected SSH sessions, Windows service sessions, and Linux/other Unix sessions without DISPLAY or WAYLAND_DISPLAY. Desktop detection uses environment hints and cannot guarantee browser availability. If no browser opens, use the printed address. Use `--no-browser` with Python/Bash or `-NoBrowser` with PowerShell to disable this behavior. Direct loopback access from localhost, 127.0.0.0/8 or ::1 connects automatically without a pairing code. Remote access still requires the one-time pairing code from the terminal. Host, Origin, session cookies and CSRF checks remain enforced. The code is never included in the browser URL. Restart always leaves processing paused. Remote access needs an explicit HTTPS reverse proxy. The private UI has its own session and never imports the platform session database.

Check the original receipt after transport failure. Resend only the exact pending envelope when the server confirms that no receipt exists. Never replay UNKNOWN. Human takeover and revocation block new starts; truthful running results may still be recorded. A new credential, grant, Worker, process or private backup cannot reset platform consumption or deduplication.

Migration planning is documented in [the offline tool](../../scripts/secretary_migration/README.md). Actual migration, imports, cutover and cleanup remain disabled. No old secretary mode or compatibility bridge is provided.

## 验证范围

### 手工停止 HttpServer（Windows）

在仓库根目录运行：

```powershell
.\personal-secretary\stop.ps1
```

如运行的是尚不支持停止接口的旧服务，或正常停止无法完成，可显式强制停止：

```powershell
.\personal-secretary\stop.ps1 -Force
```

自定义端口或 Python：`stop.ps1 -Port 8766 -Python 'C:\green\python312\python.exe'`。默认正常停止，控制会话不读取私人记录，也不会自动启动服务。`-Force` 只在正常停止未确认时，核对指定端口对应的本仓库 Python 秘书进程和当前 Windows 用户，然后终止它；不批量停止其他 Python 或 Qt，不删除私库和设置。强制停止会中断当前操作，重新启动后请核对已有结果，避免重复执行。

Qt 托盘的“退出秘书并停止本地服务”在监听端口关闭后退出桌面；“退出桌面界面（服务继续运行）”继续保留后台服务。正常停止先关闭 HTTP 监听，再做后台清理（可能约 25 秒），端口关闭不代表后台清理已经全部结束。已运行的旧代码须先停止，再启动才能加载修复。以上为静态实现，尚未实际运行退出验证。

`scripts/compile-personal-secretary.ps1` 仅运行离线生产 Java 编译、Python 内存编译和浏览器 JavaScript 语法编译，不运行服务、模型、测试、网络或迁移。编译通过不代表并发、会话、SQLite/PostgreSQL、故障窗口或浏览器流程已运行验证。

## Formal work questions (FD-028)

The private UI can read platform questions within the exact PLAN read scope. Select the goal, necessary context and original answer, preview the projection locally, then explicitly queue it with a current A19_ANSWER or A18_BUDGET grant. The platform verifies the original projection digest and exact envelope; private chats, policies, templates and credentials stay local. The private outbox retains the original wrapper and request ID for receipt reconciliation. See [the formal question contract](../../docs/FORMAL-QUESTIONS.md). Ordinary answers do not grant execution or extra budget.
