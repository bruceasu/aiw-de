# 个人秘书 Worker / Personal secretary

秘书是用户自己运行的进程和 SQLite 私库。平台不运行此代码、不打开私库，也不接收私人对话、策略原文、模板、草稿或模型推理。专业执行/评审 Worker 保持原协议。

旧平台秘书实现和双模式已移除。旧 API 不兼容；V8/V9 历史迁移和数据保留封存。数据库存在旧私人数据时，平台的新委托启用会拒绝；本次不执行实际迁移、切换或数据清理。

## 启动

需要 Python 3.11+，无第三方依赖。登录平台后，在 `/ui/authority` 选择“登记新秘书”，生成凭证并确认读取范围。平台自动关联当前账号并生成唯一短标识；在页面选择秘书和终端后复制启动命令，无需记忆或填写 Owner ID、Worker ID。命令不含凭证，启动脚本会隐藏询问已登记的凭证。已有标识继续有效，轮换凭证时直接选择已有秘书。再启动个人进程建立禁用状态的进程会话，随后在平台确认最小能力及有效期并明确启用，最后在私人 UI 明确启用本地处理。普通 Worker 注册、共享 bearer 或 capability 不代表个人身份。

在用户自己的终端配置 `AIW_PERSONAL_CREDENTIAL`。它不能出现在参数、URL、提示词、导出包或日志中。可选 `AIW_PERSONAL_MODEL_COMMAND` 是用户选择的模型程序 argv JSON 数组；程序从 stdin 读取私人上下文，将最多 16000 字符的答复写到 stdout。不会启用 shell，子进程不继承 `AIW_PERSONAL_*` 环境变量。未配置模型时仍能维护私人记录和手动提交固定能力，但不会伪称已调用模型。

在 `workers/personal-secretary` 目录中可直接使用交互启动脚本：

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

脚本提示输入平台登记的 owner ID、平台地址和凭证（隐藏输入）。默认 Worker ID 为 `my-secretary`，必须与平台登记一致；平台地址按 Enter 使用 `http://127.0.0.1:8080`，明文回环 HTTP 仍需明确确认。私人数据目录默认 `$HOME/.aiw-personal`，UI 默认 `127.0.0.1:8766`。退出后恢复原有凭证环境变量。

可预设 `AIW_PERSONAL_OWNER_ID`、`AIW_PERSONAL_WORKER_ID`、`AIW_PERSONAL_PLATFORM` 和 `AIW_PERSONAL_CREDENTIAL`，避免重复输入。脚本参数优先于对应环境变量；支持 `-OwnerId`、`-WorkerId`、`-Platform`、`-DataRoot`、`-HostAddress`、`-Port`、`-PublicOrigin`、`-AllowLoopbackHttp` 和 `-Python`。凭证只通过环境变量或隐藏输入提供。

```powershell
.\start.ps1 -OwnerId O-EXPLICIT-ID -WorkerId my-secretary -Platform https://platform.example -DataRoot D:/private/aiw
```

原有 Python 命令行继续要求显式身份和平台参数：

```powershell
python workers/personal-secretary/main.py --owner-id O-EXPLICIT-ID --worker-id my-secretary --platform https://platform.example --data-root D:/private/aiw
```

默认个人 UI 监听 `127.0.0.1:8766`，监听成功后自动尝试在默认浏览器打开 UI 地址，控制台打印五分钟有效、最多五次尝试、使用后失效的一次性配对码。浏览器打开失败不影响服务，可手动访问控制台显示的地址；配对码仍需手动输入，不放入 URL。无桌面或远程部署可通过 Python/Bash 的 `--no-browser` 或 PowerShell 的 `-NoBrowser` 关闭自动打开。每个服务有独立 HttpOnly/SameSite 会话与 CSRF token，严格验证 Host/Origin，不接收平台 cookie、不开放 CORS 或 iframe。远程监听必须明确配置 HTTPS 反向代理的 `--public-origin`。平台的明文 HTTP 仅在用户明确使用 `--allow-loopback-http` 且 URL 为回环地址时接受；禁止凭证随重定向转发。

每个 owner/Worker 使用独立目录与身份记录；复制他人的数据库会被拒绝。私人数据目录的操作系统访问权限由用户管理，尤其是 Windows ACL。平台启用、能力签发和个人 UI 本地启用相互独立。参考进程每次启动生成新 boot UUID，通过 `/sessions` 使旧许可失效并要求平台重新明确启用；平台重启同样失效。服务器能核验声明的 boot 与凭证，不能检测自定义实现隐瞒的物理进程重启；这不赋予其超出已授能力的权限。

自动打开会跳过检测到的 SSH 会话、Windows 服务会话，以及没有 `DISPLAY`/`WAYLAND_DISPLAY` 的 Linux 或其他 Unix 会话。桌面检测依据环境信息，不能保证浏览器一定可用；普通桌面终端仍会尝试打开。

## Windows 桌面秘书（可选 UI）

桌面秘书连接已经运行的个人服务，使用同一组私人记录和业务接口。它提供可拖动的透明女性角色、屏幕边缘吸附、可切换置顶、私人文字对话、工作安排/代理策略/目标提案弹窗，以及 Windows 托盘菜单。Web 保留完整功能。桌面端只支持 Windows，要求 Python 3.11+ 含 Tk；无需安装第三方 Python 包，也不会自动下载安装依赖。

首次更新到支持多界面配对的版本后，需要重新启动个人服务，让新接口生效；重启仍按现有规则使旧会话/平台进程许可失效，须重新配对并确认平台会话。日后单独启动、关闭或重新配对桌面 UI，不会重新启动 Worker，也不会自动启用动作申请。

1. 在已经登录的 Web 页面进入“状态与备份 → 连接桌面秘书”，选择“生成桌面配对码”。
2. 在 `workers/personal-secretary` 目录的另一个 PowerShell 终端运行：

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

Web 再次配对由独立的 `ui/pairing.js` 和 `ui/pairing.css` 提供，仅在用户显式生成时读取当前会话 CSRF 并请求新码；可与原有秘书 Web 页面或其新版布局共同使用，不依赖页面其它全局脚本。

桌面代码分为 `desktop.py`（Tk 窗口与用户操作）、`desktop_client.py`（私人服务会话/请求）和 `desktop_win32.py`（窗口位置/托盘）。人物素材及生成提示词见 [desktop-assets](desktop-assets/README.md)。窗口工作在主线程，单次服务请求在后台线程执行，结果通过队列返回；关闭 UI 不调用 `Runtime.cancel()`。

`scripts/compile-secretary-desktop.ps1` 仅做这次变化的 Python 内存编译、JavaScript 语法编译和启动脚本语法解析；不导入 Tk/Win32 模块、不打开窗口或启动服务，不运行测试、模型、网络、下载或打包。编译通过不代表透明窗口、托盘、多显示器或多界面配对已在桌面验收。

在包含其他未提交工作的目录中，可用 `-Revision HEAD` 编译实际提交版本中的 Python 和独立配对脚本。启动脚本必须与该版本逐字节一致，才继续做 PowerShell 语法解析。临时 JavaScript 源文件编译后删除，不保留可分发产物。

## Web 使用与恢复

个人页面使用与 Web Console 一致的深色配色、卡片和表单样式。配对登录后，通过固定导航切换工作总览、工作与安排、私人对话、代理策略、平台协作、私人资料以及状态与备份；每次只显示当前分区，窄屏导航改为横向排列。

工作安排和私人对话按工作标题选择，安排与取消操作自动带入当前私人版本。策略和资料可从已有记录载入编辑；修改策略仍须重新勾选确认。总览数量、对话内容和待处理请求均来自私人状态快照，页面显示读取时间；登录后的可见页面每五秒读取一次私人状态，也可手动刷新。完整状态和单条记录的 JSON 放在可展开的详情中。

分区切换不会读取平台问题、调用模型或启用动作申请。平台协作仍须先显式生成本地预览，再核对原文与摘要并确认提交；修改预览来源字段后须重新预览。页面不自动重发未确认请求，也不改变平台授权、本地启用或配对会话的边界。

私人资料中的草稿可点击“删除草稿”，确认后按当前版本删除，列表随即刷新；其他窗口已修改的草稿会拒绝删除，需刷新后核对。删除只移除本地当前草稿，历史版本及删除记录仍保留在私人备份中。复用已删除草稿的标识时，版本继续递增，旧窗口不能覆盖或删除重建后的草稿。

安排工作时若没有 Plan，已启用的秘书会将本工作明确填写的标题和说明提交到平台，自动创建并关联一个仅含需求分析步骤的普通 Plan 草稿。私人对话、策略、模板及其他记录不会随之提交。创建和查询使用本工作原 ID；发送结果未知或进程中断后仅查询原请求，避免重复创建。新入口为 `POST /api/r1/authority/personal-plans` 和 `GET /api/r1/authority/personal-plans/{原工作ID}`，均校验当前独立凭证、进程会话和启用状态，仅能创建自己的草稿或查询对应草稿，不授予读取其他目标、托管、执行、工具或预算权限。数据库结构不变，需同时更新平台与秘书代码。

自动创建的草稿尚未执行，工作报告会提示在平台核对、补充执行步骤、工具和输出位置，并配置读取范围、托管及执行权限。完成这些配置后重新安排。秘书不会将“创建 Plan”或“发送启动请求”显示为任务执行完成。

私人工作也可关联一个已在平台明确确认托管的干净 Plan 草稿。安排时固定业务版本和内容摘要，只接受未来整分钟；错过时间或内容变化后要求重新确认，不追赶。未执行或受阻时，工作卡片显示原因及报告时间；启动待确认、等待处理、等待补充信息、任务失败和后台异常也有对应报告，报告保存在私人版本历史中。旧的无 Plan 安排到期后报告“未执行”，不会再仅生成提醒后标记为完成，也不会自动补发旧私人说明。每次专业完成只在平台记录真实事实；个人协调读取当前源 attempt 后请求有界推进。额外修复、技术重试、仅重评、模型、审批、发布、删除等仍各自受固定能力约束。

私人策略关联已经人工确认的精确平台能力，模型只能建议是否申请，不能扩展动作/参数/预算。DENY 原文留私库，仅发送最小禁止投影及私有版本摘要；先在平台收窄并暂停，再落私库，失败继续暂停。解除禁止或交回须在平台明确操作。

决定与 outbox 同一私库事务提交。请求 ID 和原信封保持不变；响应丢失先查原回执，仅在确认不存在时重发原信封。UNKNOWN 只查询，不重放、不退款、不换 ID。平台人工对账需固定 attempt 和明确证据；不会自动启动重试。私人数据库/平台实例标识不匹配时停止处理。备份包含私人记录、outbox 与回执副本，包含业务参数但不包含平台凭证或登录会话；恢复后仍须人工重新启用。

## English operations

Run this Worker in the user's own environment. Use Python 3.11 or newer. No extra package is needed. Sign in to the platform and select **Register a new secretary** on `/ui/authority`. The platform creates short, unique IDs and links them to your signed-in account. Select the secretary and copy the start command. You do not need to remember or type the IDs. Existing IDs stay valid. Select an existing secretary to rotate its credential. Set the credential in `AIW_PERSONAL_CREDENTIAL` or enter it at the start script's hidden prompt; never put it in commands, URLs or model prompts.

Run `.\start.ps1` from this directory for interactive setup, or `bash ./start.sh` on Linux, macOS or Git Bash. Both scripts default to Worker ID `my-secretary`, offer the local platform at `http://127.0.0.1:8080`, ask for explicit loopback HTTP consent, and read missing credentials with hidden input. The Bash script defaults to `python3`; use `--python` to select another interpreter and `--help` to list options. Run it as a separate script, not with `source`. Set `AIW_PERSONAL_OWNER_ID`, `AIW_PERSONAL_WORKER_ID`, `AIW_PERSONAL_PLATFORM` and `AIW_PERSONAL_CREDENTIAL` to reuse your settings. Script parameters override the matching environment variables. The owner and Worker must match the enrolled platform identity. You can also start `main.py` with explicit owner, Worker, platform origin and private data root. A model command is optional and must be an explicit argv JSON array in `AIW_PERSONAL_MODEL_COMMAND`. It reads private context from stdin. Its output is advice, never platform authority. Dedicated credentials are removed from the model process environment.

The Worker attempts to open the private UI in the default browser after binding its listener. It skips this in detected SSH sessions, Windows service sessions, and Linux/other Unix sessions without DISPLAY or WAYLAND_DISPLAY. Desktop detection uses environment hints and cannot guarantee browser availability. If no browser opens, use the printed address. Use `--no-browser` with Python/Bash or `-NoBrowser` with PowerShell to disable this behavior. Enter the one-time pairing code from the terminal and explicitly enable local processing after platform enablement. The code is never included in the browser URL. Restart always leaves processing paused. Remote access needs an explicit HTTPS reverse proxy. The private UI has its own session and never imports the platform session database.

Check the original receipt after transport failure. Resend only the exact pending envelope when the server confirms that no receipt exists. Never replay UNKNOWN. Human takeover and revocation block new starts; truthful running results may still be recorded. A new credential, grant, Worker, process or private backup cannot reset platform consumption or deduplication.

Migration planning is documented in [the offline tool](../../scripts/secretary_migration/README.md). Actual migration, imports, cutover and cleanup remain disabled. No old secretary mode or compatibility bridge is provided.

## 验证范围

`scripts/compile-personal-secretary.ps1` 仅运行离线生产 Java 编译、Python 内存编译和浏览器 JavaScript 语法编译，不运行服务、模型、测试、网络或迁移。编译通过不代表并发、会话、SQLite/PostgreSQL、故障窗口或浏览器流程已运行验证。

## Formal work questions (FD-028)

The private UI can read platform questions within the exact PLAN read scope. Select the goal, necessary context and original answer, preview the projection locally, then explicitly queue it with a current A19_ANSWER or A18_BUDGET grant. The platform verifies the original projection digest and exact envelope; private chats, policies, templates and credentials stay local. The private outbox retains the original wrapper and request ID for receipt reconciliation. See [the formal question contract](../../docs/FORMAL-QUESTIONS.md). Ordinary answers do not grant execution or extra budget.
