# 个人秘书 Worker / Personal secretary

秘书是用户自己运行的进程和 SQLite 私库。平台不运行此代码、不打开私库，也不接收私人对话、策略原文、模板、草稿或模型推理。专业执行/评审 Worker 保持原协议。

旧平台秘书实现和双模式已移除。旧 API 不兼容；V8/V9 历史迁移和数据保留封存。数据库存在旧私人数据时，平台的新委托启用会拒绝；本次不执行实际迁移、切换或数据清理。

## 日常使用（默认）

需要 Python 3.11+，无需安装第三方 Python 包。Windows 用户在秘书目录运行 `./start-desktop.ps1`，GUI 会自动连接本机个人服务；尚未运行时会尝试在后台启动。Web 用户运行 `./start.ps1`，Linux/macOS 使用 `bash ./start.sh`。无参数即可使用日常模式，不需要 Owner ID、Worker ID、平台凭证或手工创建 Plan。现有用户首次升级可先使用原来的启动命令启动一次，记住原私库身份，以后直接无参数启动；旧数据库不会被搬移、合并或删除。

首次打开“设置模型”（Web 中为“设置”），填写兼容 Chat Completions 的服务地址、模型名称和密钥。服务地址填写 API 基址，程序会追加 `/chat/completions`；本机无认证模型可不填密钥。可选择常用资料目录，也可留空、在具体工作中指定文件位置。保存一次后自动恢复，无需设置环境变量或重启 GUI。模型服务可能按用量计费；每项工作最多 12 次模型请求，不自动购买、充值或增加额度。高级用户仍可使用原来的模型程序 argv 适配器。

直接在 GUI 或 Web 对话框说出需要帮助的事，不必先创建工作。保存工作时填写“做什么”和可选的结果文件位置，然后点击“现在处理”或安排时间。秘书在本机自动建立日常计划并执行公开信息查询、资料整理、授权文件读取和文本保存；Web 每五秒、GUI 每三秒刷新状态，界面不必等待整个模型调用完成。无后台服务运行期间不能到点执行；尚未开始的安排在服务恢复后处理，运行中断的工作报告结果待核对，不自动重放。

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

高级启动仍可传入平台登记的 owner ID 和平台地址。默认 Worker ID 为 `my-secretary`，应与平台登记一致。即使传入这些参数，启动也不再强制询问 `Dedicated platform credential`：优先使用环境变量或已保存的凭证，没有凭证仍可使用本机日常秘书。需要首次输入或更换专业平台凭证时，PowerShell 显式加 `-PromptCredential`，Bash 加 `--prompt-credential`；明文平台连接仍遵循原有回环 HTTP 确认规则。私人数据目录默认 `$HOME/.aiw-personal`，UI 默认 `127.0.0.1:8766`。退出后恢复原有凭证环境变量。

可预设 `AIW_PERSONAL_OWNER_ID`、`AIW_PERSONAL_WORKER_ID`、`AIW_PERSONAL_PLATFORM` 和 `AIW_PERSONAL_CREDENTIAL`，避免重复输入。脚本参数优先于对应环境变量；支持 `-OwnerId`、`-WorkerId`、`-Platform`、`-DataRoot`、`-HostAddress`、`-Port`、`-PublicOrigin`、`-AllowLoopbackHttp` 和 `-Python`。凭证只通过环境变量或隐藏输入提供。

```powershell
.\start.ps1 -OwnerId O-EXPLICIT-ID -WorkerId my-secretary -Platform https://platform.example -DataRoot D:/private/aiw
```

原有 Python 命令行继续要求显式身份和平台参数：

```powershell
python workers/personal-secretary/main.py --owner-id O-EXPLICIT-ID --worker-id my-secretary --platform https://platform.example --data-root D:/private/aiw
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

## English operations

Run this Worker in the user's own environment. Use Python 3.11 or newer. No extra package is needed. Sign in to the platform and select **Register a new secretary** on `/ui/authority`. The platform creates short, unique IDs and links them to your signed-in account. Select the secretary and copy the start command. You do not need to remember or type the IDs. Existing IDs stay valid. Select an existing secretary to rotate its credential. Set the credential in `AIW_PERSONAL_CREDENTIAL` or enter it at the start script's hidden prompt; never put it in commands, URLs or model prompts.

Run `.\start.ps1` from this directory for interactive setup, or `bash ./start.sh` on Linux, macOS or Git Bash. Both scripts default to Worker ID `my-secretary`, offer the local platform at `http://127.0.0.1:8080`, ask for explicit loopback HTTP consent, and read missing credentials with hidden input. The Bash script defaults to `python3`; use `--python` to select another interpreter and `--help` to list options. Run it as a separate script, not with `source`. Set `AIW_PERSONAL_OWNER_ID`, `AIW_PERSONAL_WORKER_ID`, `AIW_PERSONAL_PLATFORM` and `AIW_PERSONAL_CREDENTIAL` to reuse your settings. Script parameters override the matching environment variables. The owner and Worker must match the enrolled platform identity. You can also start `main.py` with explicit owner, Worker, platform origin and private data root. A model command is optional and must be an explicit argv JSON array in `AIW_PERSONAL_MODEL_COMMAND`. It reads private context from stdin. Its output is advice, never platform authority. Dedicated credentials are removed from the model process environment.

The Worker attempts to open the private UI in the default browser after binding its listener. It skips this in detected SSH sessions, Windows service sessions, and Linux/other Unix sessions without DISPLAY or WAYLAND_DISPLAY. Desktop detection uses environment hints and cannot guarantee browser availability. If no browser opens, use the printed address. Use `--no-browser` with Python/Bash or `-NoBrowser` with PowerShell to disable this behavior. Direct loopback access from localhost, 127.0.0.0/8 or ::1 connects automatically without a pairing code. Remote access still requires the one-time pairing code from the terminal. Host, Origin, session cookies and CSRF checks remain enforced. The code is never included in the browser URL. Restart always leaves processing paused. Remote access needs an explicit HTTPS reverse proxy. The private UI has its own session and never imports the platform session database.

Check the original receipt after transport failure. Resend only the exact pending envelope when the server confirms that no receipt exists. Never replay UNKNOWN. Human takeover and revocation block new starts; truthful running results may still be recorded. A new credential, grant, Worker, process or private backup cannot reset platform consumption or deduplication.

Migration planning is documented in [the offline tool](../../scripts/secretary_migration/README.md). Actual migration, imports, cutover and cleanup remain disabled. No old secretary mode or compatibility bridge is provided.

## 验证范围

`scripts/compile-personal-secretary.ps1` 仅运行离线生产 Java 编译、Python 内存编译和浏览器 JavaScript 语法编译，不运行服务、模型、测试、网络或迁移。编译通过不代表并发、会话、SQLite/PostgreSQL、故障窗口或浏览器流程已运行验证。

## Formal work questions (FD-028)

The private UI can read platform questions within the exact PLAN read scope. Select the goal, necessary context and original answer, preview the projection locally, then explicitly queue it with a current A19_ANSWER or A18_BUDGET grant. The platform verifies the original projection digest and exact envelope; private chats, policies, templates and credentials stay local. The private outbox retains the original wrapper and request ID for receipt reconciliation. See [the formal question contract](../../docs/FORMAL-QUESTIONS.md). Ordinary answers do not grant execution or extra budget.
