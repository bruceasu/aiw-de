# 计划与 Worker 委派

当前产品只提供计划流程。FD-011 最初以独立入口实现，FD-017 增加页面；现在 `/ui`、创建入口、列表和详情统一使用 `/ui/plans`。固定自主 Run 的 HTTP 创建与管理入口已移除，旧 `/ui/runs` 只重定向。`/api/r1/workflow-plans` 保留为计划 JSON API。开发库按单文件 V1 基线从空数据库初始化；旧 Run 数据不会自动转换。

页面提供“Core development”核心开发模板（需求分析→方案设计→实施规划→编码与必要测试）、单步分析、分析→架构→规划、单步编码模板及空白编辑。核心开发模板的终点是代码通过阶段评审，不包含代码专项审查、独立测试验证、集成、集成验证、专项修复、部署或生产发布；编码步骤保存前必须选择有效工作区，并按实际需要设置语言能力。Git 基准提交可选：非 Git 目录保持为空，直接使用工作区目录；填写时必须为完整不可变 Git commit，并使用隔离 Git worktree。模板可修改和删除步骤；步骤按列表顺序执行，删除或改序后须由管理者修正声明的依赖。每一步可独立设置角色、必需技能及偏好技能，步骤指令同时写明验收条件。保存草稿不创建业务任务；确认后只创建第一步。每步执行完成后生成独立 `workflow-review` 评审任务；PASS 才生成下一步，REPAIR_REQUIRED 则将评审意见与原产出交给同一步的下一次执行。每步最多三次执行；第三次评审仍需返工时停止自动推进，详情显示 NEEDS_ATTENTION，由管理者另行决定。后续步骤接收此前已接受的产出，代码步骤在同一工作区使用最新已接受的结果 revision。显式 Worker 执行失败仍按现有任务失败规则处理，不伪装成评审返工。复制会创建新草稿，不改原计划。模板不包含固定 AR 的工程 Gate 或人工批准。

New Plan 的 `Workspace reference` 从已启用的 Workspace 目标中选择，可输入名称、引用或相对路径过滤；过滤文字不作为提交值，当前已选目标在过滤时仍保留显示。没有可用目标时，先在 Workspaces 注册，再刷新编辑页。复制计划中的失效引用显示为不可用，必须重新选择或清空后才能保存；需要工作区的角色不能清空。列表是页面加载时的快照，后续禁用或删除仍以服务端现有校验为准。

### 验证 Worker 的覆盖范围

当前核心模板可以实际验证 `requirement-analysis`、`solution-architecture`、`implementation-planning`、`coding` 四种执行角色，以及每一步自动生成的 `workflow-review`。给编码步骤设置 `typescript,node` 等必需能力，并给阶段评审 Worker 配置相同技术专长，可验证按阶段能力匹配。每个执行任务完成后应出现独立评审任务；只有评审 PASS 才进入下一步。用可修复的评审意见触发 REPAIR_REQUIRED，可验证带反馈的重试和三轮上限；**重试仍由原步骤角色领取，并非 `repair` Worker**。

现有计划执行器不能通过单个模板验证所有 Worker：编辑器尚不接受 `code-review`、`verification`、`integration-verification` 作为步骤角色；虽然可手动增加 `integration` 和 `repair` 步骤，当前流程把它们当普通线性工作，无法根据专项审查或验证的 REPAIR_REQUIRED 判定自动路由到 `repair`。因此不要把“阶段评审通过”当作代码专项审查或独立测试通过。要做全角色端到端验证，需先实现专项审查与验证的判定分支、修复后重新审查/验证，以及同一代码 revision 的传递；再分别运行成功路径和返工路径。单个成功路径无法触发只在失败时需要的 `repair` Worker。

计划页面只接受可执行角色能力与精确 `skillRef`；自动评审使用专属 `workflow-review` 角色，执行步骤不得声明该角色。必需技能影响 Worker 领取；页面的舰队匹配是最多 100 个 Worker 的当前快照，无匹配或窗口外状态均不保证未来执行。详情展示保存的图、真实执行与评审任务、报告、判定及创建/确认/收回审计字段。未记录的报告显示未知。存在 CLAIMED/RUNNING 的计划整体拒绝收回，完成结果保留。固定自主 Run 页面已退出当前产品。

确认后的计划可由操作员填写原因，从当前步骤跳到更靠后的步骤，或提前停止。跳转会取消尚未完成的旧任务并记录被跳过的步骤；目标步骤只引用已有且通过独立评审的产出，缺失的产出不会被伪造成通过。提前停止使计划进入 `STOPPED`，已完成的任务与产出保留。若当前任务已被 Worker 领取，操作先进入 `PENDING_STOP`，请求 Worker 停止；只有收到对应租约的停止确认后才应用。Worker 在确认前自然完成或失败时，该请求记为 `CONFLICT`，正常完成流程继续；租约到期后可由操作员把滞留的请求记为冲突，再重新决策。详情展示每次决定及状态。不能跳回较早步骤，也不能修改最终评审已通过的计划。

TypeScript 全角色启动器会额外启动一个 `stage-reviewer`。角色能力自动注册；只有实际具备技术工具链的角色才需声明相应技能。例如使用 `AIW_WORKER_CAPABILITIES_CODER=node,typescript` 为编码角色添加技术能力，需求分析角色无需声明它们。`AIW_WORKER_CAPABILITIES_<ROLE>` 中的角色名使用大写及下划线，例如 `STAGE_REVIEWER`；未设置的角色不继承共享的 `AIW_WORKER_CAPABILITIES`。Go 和 Python Worker 也提供 `workflow-review` profile。统一能力名称、分配与限制见 [Worker 能力与分配](WORKER-CAPABILITIES.md)。

### AI 推荐边界

管理员从 **System → Configure AI recommendation**（`/ui/system/recommendations`）调整系统级推荐设置。设置在服务端持久化，保存后无需重启，并记录操作者、时间和前后值；普通用户不能修改。无历史设置时默认启用。旧环境变量 `AIW_FLEXIBLE_WORKFLOW_RECOMMENDATIONS_ENABLED` 已退出控制，也不作为迁移输入；升级后请检查系统设置。

planner 选择现有 `aidev.ai` 的 EXTERNAL_WORK policy 中的 AI Profile（包含 Runtime、模型和强度）。model/effort 可空；填写后作为精确筛选条件，与所选 Profile 不符或无可用组合时明确拒绝，不自动回退。全部留空时由 Worker 在批准的 Profile 中选择；没有 policy 时使用 Worker 本地默认值。凭据仍在 Worker，设置页不保存凭据。每次请求冻结完整 Profile 候选内容及版本，配置编辑或重启不会重新解释已排队请求。

推荐只接受现有 Worker 的共同支持组合：provider 为 openai；codex-app-server 要求 fast=false，openai-api/openai-agents 还要求支持的 effort。显式选择不兼容组合会说明原因，不会切换到其他 Profile。此限制不改变普通业务任务的 Profile 策略。

全部适配的分析 Worker 离线时拒绝新请求，并说明原因；在线但忙碌时接受并排队。关闭功能会拒绝新请求并取消 READY/BLOCKED 分析，已领取和运行中的分析继续完成。关闭后已有结果及随后完成的结果仍可查看、手动应用。

点击推荐会创建一个 `workflow-recommendation` 的只读规划分析任务，可能产生模型费用；仅发送最多 8000 字符目标和固定可信角色目录，不附带工作区或源码。任务使用现有 Pull/lease、报告与审计机制，建议不能自行确认业务图。

推荐返回必须是单个关联当前 requestKey/inputVersion 的 JSON 对象，包含理由与合法 WorkflowPlanSpec。未知角色能力、越界字段、不一致目标或自行指定工作区会拒绝应用。页面等待最多 10 分钟；停止等待不代表终止 Worker。仅排队时显示撤回，已领取/运行及结束后不提供撤回。需求或编辑内容变化后旧建议失效；管理者主动应用并补足工作区后，仍需保存草稿和另行确认。推荐失败时可继续手动编辑。

已提交目标及任务关联保存在服务端；刷新或离开不会取消分析，原 URL 的 `context` 标识及编辑页的 **Resume a previous analysis** 可以恢复。未提交的本地编辑不保证恢复。保存 Plan 后，详情页 **View recommendation analysis** 可查看关联状态和结果；从已保存上下文应用建议再保存会创建新草稿，不会改写原 Plan。恢复入口与结果按操作者隔离。升级前没有上下文关联的历史任务仍可按原任务 ID 查询，不伪造恢复入口。

分析失联后等待有效租约到期（当前 60 秒租约，默认每 5 秒执行恢复；停机或积压可能延迟）即失败，不自动重新派发；旧 token 的迟到完成不再成为可应用结果。手动重试创建新的请求，原模型调用未必已经停止，可能产生重复调用及费用。

写操作沿用 Web Console 会话 OPERATOR/ADMIN 和 URL 查询参数 `_csrf`，只读角色不能保存、推荐、确认或收回；系统设置另限 ADMIN。`/api/r1/plan-editor/**` 是页面专用接口；`/api/r1/workflow-plans/**` 使用相同的步骤角色校验和逐步评审执行语义。AI 推荐须有在线适配 Worker 并由用户显式点击，页面不会自行发起模型任务。

数据库迁移 V6 仅新增推荐设置、审计、上下文、请求快照及 Plan 关联表；原任务与 Plan 数据保留。原 Plan 到期删除时清除其上下文关联和映射，推荐上下文与历史结果保留。回滚应用时保留这些表，不自动删除审计；旧应用仍使用其旧启动配置语义，因此回滚后应重新核对开关。

管理者可以只委派一次分析或自己选择的有限步骤。技能说明 Worker 能做什么；步骤与依赖说明执行流程。技能名称使用现有 Worker capabilities，`skillRef` 是实际执行技能引用；必需技能决定资格，偏好技能用于同一有效优先级下的匹配排序。管理者须确保引用能被所选 Worker 执行，系统不会安装新技能或扩大内部工具权限。

编辑页的能力指南列出可选步骤角色、最多 100 个 Worker 快照中 ACTIVE Worker 声明的技术能力及独立评审 Worker 数量；完整的当前 Worker 声明和 READY 任务匹配诊断可在 `/ui/workers` 查看。技术能力是 Worker 自报标签，不能证明工具已安装。AI 推荐按钮已存在；推荐按每一步的实际需要分配技术能力，独立评审由系统自动插入，推荐结果仍需人工检查当前 Worker 匹配并保存、确认。

## 查看任务详情与处理失败

在计划详情页的“执行与独立审核”列，点击具体任务的“查看任务详情”。
同一步骤内的执行与评审任务按创建时间倒序排列，并标注创建时间；时间相同时按任务 ID 倒序稳定排列。
表格只显示最新一条记录，多条记录时提供“查看全部记录”，弹窗内展示该步骤的完整倒序记录、报告及任务详情链接。
弹窗沿用控制台样式，支持关闭按钮、Esc 和点击背景关闭；打开时暂停自动刷新，关闭后恢复。
计划表格中的报告默认折叠；展开后在最高 320px（不超过半屏）的区域内滚动阅读，
表格列宽不随报告长度变化。自动刷新保留报告展开状态及报告内滚动位置。
Plan 详情每 5 秒读取一次最新快照，显示最近成功更新时间；从任务详情返回并恢复浏览器缓存页面时，
会重新启动刷新。只读快照请求超过 10 秒会中断并在下一次刷新重试，原有数据继续显示。
后台页面、编辑输入、确认弹窗和选中文字时仍暂停自动刷新，手动刷新按钮可主动读取最新状态。
详情页显示每次执行的 Worker、开始与结束时间、指令版本、失败分类、结构化错误码、
审核判定、结果版本及完成报告；没有记录的数据明确显示“未记录”。
任务必须属于当前计划，页面沿用控制台认证，不展示租约令牌。

计划详情每 5 秒更新状态；编辑输入、打开确认弹窗、选择文本或切换到后台时暂停。
任务详情在任务仍处于等待、领取或执行状态时每 8 秒刷新，终态可手动刷新。
任务详情页对长时间 CLAIMED/RUNNING 的执行显示黄色耗时提醒，默认15分钟；耗时提醒不会停止 Worker。
Control Plane 可通过 `aidev.worker.attempt-warning-ms` 调整页面阈值；默认读取
`AIW_WORKER_ATTEMPT_TIMEOUT_MS`、`AIW_AGENT_TASK_TIMEOUT_MS`，再回落到900000ms。
远端 Worker 使用不同阈值时需要同步配置；页面按执行开始（尚未开始时按领取时间）计算，
不是对模型进度或 Worker 健康状态的判断。Worker 本地也记录提醒日志及执行证据。
整体硬期限默认关闭，需要时设置 Worker 的 `AIW_WORKER_ATTEMPT_HARD_TIMEOUT_MS`。
后续失败上报把结构化错误码保存到已有 `execution_attempt.termination_reason` 字段；
只保存大写字母、数字与下划线组成的最多 120 字符错误码，不保存原始工具输出。
本次不做数据库迁移，也不回填历史失败；之前未保存的命令输出无法从页面恢复。

TypeScript Codex Worker 后续会在日志和 `sdk-tool-failed` 证据中记录工具类型、调用 ID、
退出码及固定诊断码，已识别的诊断码同时作为失败原因上报。
诊断从有限输出中识别常见沙箱、工具、权限、路径和连接错误，不保存原始命令或输出。
轮询与心跳日志会带 HTTP 状态或连接错误码。
`manage-workers.mjs logs <role>` 只返回文件路径，需用 `tail` 查看内容；
前台启动的 Worker 输出在原终端，不自动生成管理器日志。

遇到 `FAILED`：

1. 查看最新执行详情，并在运行 Worker 的机器上查看其日志与执行证据。
   TypeScript Worker 管理器用 `node scripts/manage-workers.mjs logs requirements`
   获取需求分析日志路径；其他角色替换 `requirements`。执行证据由
   `AIW_WORKER_EVIDENCE_ROOT` 配置，默认在 Worker 仓库根目录或备用工作目录的父目录
   下的 `.aidev-attempt-evidence` 中，可用 `owner.json` 的 `attemptId` 匹配。
2. 修复失败命令、工具、权限或运行时配置。单纯启动或重启 Worker 不会让这个失败任务重试。
3. 确认执行角色和独立 `workflow-review` Worker 都已注册且能力匹配；
   核心模板还需 requirements、architect、planner、coder、stage-reviewer。
4. 在失败任务详情填写重试原因，点击“重试失败任务”并确认。仍使用原计划与原任务，
   下一次领取会新增执行尝试，旧失败记录保留。已验收的前置产出、依赖与结果版本不变；
   执行任务成功后继续创建独立审核，审核任务失败时只重试审核任务。
   已完成的前置步骤不会重跑。失败执行可能留下本地改动，确认前须检查已有文件与副作用。

重试仅允许尚未结束、未归档计划中的当前 `FAILED` 任务，要求 OPERATOR/ADMIN、有效 CSRF、
当前计划和任务版本、非空原因及显式风险确认。服务在一个事务内锁定任务、校验控制状态、
更新计划审计、重新入队并记录人工重试事件；重复点击或过期页面不能重复入队。
待停止确认、已跳过、已推进到后续任务、存在其他待执行或活动任务、人工完成/取消和最终审核通过
都会阻止重试。人工重试不绕过 `REPAIR_REQUIRED` 的三轮纠正上限；已完成的审核任务不能用此入口重排。
只有需要修改目标或步骤设计时，才复制为新草稿并重新确认。

### 三轮耗尽后补正并重新评审

评审任务会收到当前执行的精确产出，以及此前所有已接受步骤的产出和独立 PASS 评审。
这些依赖让评审能够读取需求、架构、实施计划正文及其接受依据，不能仅依赖执行者的摘要。

如果第三轮评审仍返回 `REPAIR_REQUIRED`，先修复报告指出的原因。在原 Plan 详情的“步骤控制”中
填写决策原因，点击“补正后重新评审”并确认。系统在原 Plan 创建
`~review-3-resume-1`（后续递增）任务，重新评审第三轮的现有执行产出；不会重新编码或重跑已接受步骤。
原来的三轮执行、报告和判定保留，通过现有纠正关联与 `RESUME_REVIEW` 事件记录操作者、原因和时间，
无需修改数据库结构。

新的评审 PASS 后继续原 Plan；最终步骤 PASS 则完成。新评审仍需补正时再次等待管理者处理，
不会自动增加编码轮数；新评审若执行 FAILED，使用其任务详情中的失败重试入口。
计划已结束、归档、待停止，或存在待执行、运行、失败任务时禁止创建恢复评审。
提交会检查 Plan、最新评审 ID 和任务版本，防止过期页面或重复提交创建多条任务。

接口为 `POST /api/r1/plan-editor/plans/{id}/resume-review`，请求包含 `planVersion`、`taskId`、
`taskVersion`、`reason`、`warningConfirmed: true`；沿用 Operator 权限和 CSRF 校验。详情接口的 `reviewRecovery` 提供当前可恢复性。

### 授权继续修复及独立 AI 决策

需要修改代码或补充验证证据时，使用原 Plan 的“授权一轮修复”，而不是直接重新评审。
管理者填写决策原因、固定修复范围及允许执行的检查，选择“人工直接批准”，即可创建下一轮执行，
例如 `~work-4`；完成后生成对应 `~review-4`。已接受步骤及旧报告保留，Git 工作区使用上一轮结果 revision，
DIRECT 模式继续使用原工作区。新评审 PASS 后继续原 Plan；REPAIR_REQUIRED 再次等待管理者，
不会自动产生第 5 轮。补正后重新评审、失败重试、后续步骤输入及归档均支持额外授权轮次。

管理者也可选择一个在线 `recovery-decider`，委托它决定是否使用这一次额度。授权固定 Plan、
最新评审和任务版本、范围、指定 Worker、一次执行及其独立评审，有效期为一小时。
决策 Worker 必须不同于来源执行 Worker 和来源评审 Worker；其他同能力 Worker 不能领取这份决策任务。
AI 返回 PASS 仅使用既有授权；REPAIR_REQUIRED 表示拒绝，应由管理者补充决策。AI 报告不能修改授权范围，
也不能追加轮数、创建新授权或改变需求。管理者确认的范围会保存并传入执行与评审指令，
用于区分明确批准的需求补充与执行者自行作出的假设。

Plan 页面显示授权历史和 AI 决策报告，允许撤销尚未使用的授权。撤销不会中断已经运行的只读决策，
但其后续结果不能触发修复。AI 决策执行失败时，可在历史中查看错误，撤销未使用授权，处理原因后重新授权。
授权过期、计划版本变化、来源评审变化、计划结束或已归档时，不会创建修复任务；每份授权只能使用一次。
同一 Plan 同时只允许一份待决授权，重复提交会被版本及数据库约束拒绝。

接口为 `POST /api/r1/plan-editor/plans/{id}/repair-authorizations`，请求包含 `planVersion`、`taskId`、
`taskVersion`、`reason`、`scope`、可选 `delegateWorkerId` 和 `warningConfirmed: true`。
撤销使用 `POST /api/r1/plan-editor/plans/{id}/repair-authorizations/{authorizationId}/revoke`。
两者只接受现有 Operator 会话及 CSRF，Worker bearer token 不能创建或扩大授权。
AI 通过正常领取、租约、完成协议交回决策，控制平面校验指定身份后使用授权。

V4 迁移只新增授权表和索引，不修改已有计划及执行记录。授权保留来源 ID、范围、操作者、状态、
一次性额度和到期时间，计划历史清理后仍保留授权记录；回滚应用时保留新增表，不回写或删除旧报告。
需先重启 Control Plane 应用迁移，再加载新的 Worker profiles、Skills 和启动程序。

当前问候程序可采用以下已确认范围：

```text
Continue this implementation for one repair round.
Keep the Node.js 22.x and npm 10.x requirement.
With no arguments, print exactly: Hello，I'm AI Worker.
With one non-empty argument, print: Hi, <argument>. Nice to meet you.
Reject multiple arguments and an empty string with stderr and a nonzero exit code.
Preserve the supplied non-empty argument text, including spaces and Unicode.
Update the implementation, documentation and focused boundary tests.
Run the build and focused checks in the target environment. Report exact commands and results.
Do not change acceptance criteria, install a toolchain or download dependencies without separate authorization.
```

## 管理接口

沿用 Web Console 登录会话；写操作要求 OPERATOR/ADMIN，并在查询参数中携带该会话的 `_csrf`（JSON body 不含 CSRF 参数）。生产环境应启用既有控制台认证；默认本地免认证模式仍使用 local-console 身份和 CSRF。外部流程/Worker 的提交令牌不能调用这些管理操作。

| 方法 / 路径 | 行为 |
|---|---|
| GET `/api/r1/workflow-plans` | 最近 100 个计划，包含版本、来源和审计信息 |
| GET `/api/r1/workflow-plans/{id}` | 计划及创建后的任务状态 |
| POST `/api/r1/workflow-plans` | 保存草稿；body 为下方计划格式 |
| POST `/api/r1/workflow-plans/{id}/copy` | 复制为全新草稿，可复用模板；不会启动 |
| POST `/api/r1/plan-editor/plans/{id}/tasks/{taskId}/retry` | 页面重试当前失败任务；body 为 `{"planVersion":1,"taskVersion":6,"reason":"已修复运行环境","warningConfirmed":true}`；保留原计划、任务与执行历史 |
| POST `/api/r1/workflow-plans/{id}/confirm` | body `{"version":0}`，明确确认当前草稿版本 |
| POST `/api/r1/workflow-plans/{id}/withdraw` | body `{"version":1,"reason":"收回剩余委派"}` |
| POST `/api/r1/workflow-plans/{id}/jump` | body `{"version":1,"targetStep":2,"reason":"跳过中间步骤"}`；`targetStep` 从 0 开始 |
| POST `/api/r1/workflow-plans/{id}/stop` | body `{"version":1,"reason":"已有产出足够"}`；提前停止 |
| POST `/api/r1/workflow-plans/{id}/resolve-stop` | body `{"version":2}`；仅在原租约不再活动且未确认停止时，将滞留请求记为冲突 |
| POST `/api/r1/workflow-plans/{id}/archive` | 已结束计划从当前列表隐藏 |
| POST `/api/r1/workflow-plans/{id}/schedule-delete` | 管理员标记 30 天后删除数据库记录 |
| POST `/api/r1/workflow-plans/{id}/undo-delete` | 管理员在到期前撤销删除标记 |

示例仅委派分析，不自动增加代码、审查或部署阶段：

```json
{
  "goal": "分析报表慢查询的原因并提供建议",
  "source": "AI",
  "steps": [{
    "key": "analysis",
    "requiredSkills": ["requirement-analysis"],
    "preferredSkills": ["postgresql"],
    "skillRef": "software-requirements-analyst",
    "instruction": "分析现有材料并提出优化建议，不修改生产数据。",
    "repositoryRef": null,
    "baseRevision": null,
    "dependsOn": []
  }]
}
```

`source` 为 MANAGER 或 AI，表示来源，不表示批准。AI 生成的建议由管理者导入，确认前不会创建可领取任务。本轮不调用 AI 生成器。草稿不可原地修改；调整目标、技能或步骤时提交新草稿，旧草稿保持未执行状态。

每个计划有 1–32 个有序步骤。key 唯一，依赖须指向更早步骤，禁止循环和缺失前置工作。工作区规则复用现有任务：`repositoryRef` 可为工作区目标或旧仓库 ID，`baseRevision` 可为空，此时沿用直接目录执行模式，不要求目录具有 Git；若提供必须为完整不可变 commit ID，且必须同时指定 `repositoryRef`。必需技能为空、非法技能、空指令和不合法版本均拒绝。参数错误返回 400，未知计划 404，版本变化/活动工作/状态冲突 409，权限不足 403；未认证与 CSRF 失败由既有拦截器处理。

## 确认、保留与收回

计划确认、跳转、停止、人工完成和取消使用控制台主题弹窗，并跟随当前语言。点击取消、按 Esc 或点击弹窗外部均不会提交操作；只有点击确认才会继续。打开确认弹窗时，控制台自动刷新暂停，关闭后恢复。

创建计划时仅保存 DRAFT。管理者确认后以同一事务变更 CONFIRMED 并创建第一步 EXTERNAL_WORK；失败回滚，不留下部分任务。每个后续执行或评审任务由前一步完成事务幂等创建，评审未 PASS 不创建下一步。同一已确认计划重试原确认版本或当前版本不会重复创建工作。

管理者保留的工作留在自己手中，执行图只列本次实际委派部分。若 Worker 工作需要这些人工工作的结果，管理者先准备输入，再提交有界计划；系统不伪造人工步骤完成结果。每个新任务沿用现有 Pull/lease 机制；无满足该步必需技能或 `workflow-review` 的 Worker 时任务等待，偏好不足不阻止领取。

收回要求计划 CONFIRMED 及当前 version。存在任一 CLAIMED/RUNNING 工作时整体拒绝，租约、计划和所有任务不变。否则取消 READY/BLOCKED/FAILED 工作并记录原因及操作人，计划成为 WITHDRAWN；完成结果保留。收回不实现停止运行中的 Worker。重新委派需复制或创建新的草稿，并再次确认。

## 兼容和运维

当前单文件 V1 基线包含计划、偏好、控制与归档表，并在末尾删除旧固定 Run 表。无偏好任务保持原排序；偏好位于有效优先级后、同 subject 活跃租约/等待时间排序前，不绕过准入、并发预算或原子领取。

升级按既有运维流程排空/停止应用、备份数据库、启动应用执行 Flyway、确认启动后恢复 Worker。回滚须先停用入口并排空新增工作，按备份恢复兼容的数据库/应用组合；不要删表、修改历史迁移或 Flyway history。本次未执行迁移或数据库集成测试；SQLite/PostgreSQL 兼容性依据 SQL 静态检查，不能作为实际运行通过。

## Step 模型与推理强度

每个 Step 可选填 `model` 和 `effort`。留空的字段由 Worker 自行决定；无效或不可用的配置回退到 Worker 自己的配置。
执行、返工及独立评审使用同一 Step 偏好。已有 `aidev.ai` 模型策略仍限制可选范围，不能通过 Step 扩权。
模型参数被明确拒绝且工具尚未执行时，最多用 Worker 配置再尝试一次，并记录本地证据；不因普通代码失败重跑任务。
旧 Plan 的 JSON 无需补字段，本次不增加迁移。新字段需要更新 Control Plane 和 Worker 后生效。

配置优先级、回退边界及独立环境变量文件见 [Worker 环境变量](../../workers/ENVIRONMENT.md)。

### 完成事件分发

`EXTERNAL_WORK` 统一由 `WorkflowStepCompletedHandler` 注册完成事件处理权。
普通 Plan 步骤按 `flexible-plan` 主题推进；恢复授权决策按 `workflow-repair-authorization` 主题委托
`WorkflowRepairDecisionHandler` 处理。后者不独立注册为 `TaskCompletionHandler`，避免重复声明同一任务类型导致应用启动失败。
调度器对重复处理权的启动检查继续保留。

### 管理员任务指挥台

Manager actions 以任务卡片呈现当前可用操作：待确认草稿展示启动入口，评审要求补正且符合恢复条件时展示修复和重新评审卡片。
顶部状态徽章及提示说明当前任务阶段；停止、取消、跳转等操作收在“其他任务操作”中。
修复授权卡片独立填写授权原因、固定范围和决策责任方，校验提示与提交状态在按钮旁显示。
确认及提交期间禁用重复点击；15 秒请求超时会提示先核对授权历史，不自动重新提交。

Plan 页的“决策历史”和管理员任务指挥台现通过右侧固定工具栏打开浮动窗口，支持拖动、隐藏及最大化；主页面仅保留计划概况和步骤。
`NEEDS_ATTENTION` 在页面警示、状态及指挥台工具图标上同时突出显示。窗口中的操作规则与授权范围不变。

### 回答阻塞问题后继续

当评审指出 Q-001 等未决策略时，打开右侧“待决问题与文件”，阅读问题上下文和来源附件，填写并确认结论。
回答会绑定来源版本并进入后续任务输入，不必再在修复范围里重复粘贴同一策略。随后打开 Mission Control，
授权针对既有评审意见的一轮修复，或对已补正的成果发起重新评审。
若来源文件已更新、任务仍在执行或有待处理授权，界面会提示先处理对应状态；不会默默复用旧答案或自动再开一轮执行。

## AgentFactory 委派执行记录

AgentFactory 在授权范围内将原草案确认为正式计划，具体任务成为正式步骤，原临时分析草案保存为历史快照。实际完成时，`plan.state` 持久保存为 `COMPLETED`，不再仅覆盖显示状态。详情 API 的 `factoryPlan` 提供确认信息与原草案，`steps` 关联实际任务；列表、首屏及右侧状态使用同一生命周期结果。旧记录须经显式历史修复后进入正式生命周期，迁移不会触发任务重跑。

计划列表和详情关联 `configured-plan` 的工厂任务。尚未修复的历史记录可能仍有 DRAFT 的原步骤图，此时保留委派执行记录供核对；经历史修复后，原草案成为历史快照，原计划具有正式执行步骤和真实的完成状态。

详情页的“委派执行记录”展示准备失败原因、每次 Worker 尝试、时间、原始判定、报告、结果提交、文件、分支和工作区，以及最近 500 条工具审计操作（工具、资源、状态、时间和证据位置）。不返回租约令牌和模型私有推理。展开记录时保持内容稳定，收起后才更新。

历史失败保留；旧的 PASS 报告如明确表示阻塞或未完成，不作为成功依据。委派完成不等同于原图的独立审核通过或已同步到原目录，页面不会为调整显示修改原始审批状态。
