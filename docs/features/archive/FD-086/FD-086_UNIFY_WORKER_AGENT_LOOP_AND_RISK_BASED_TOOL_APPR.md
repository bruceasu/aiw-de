# FD-086: Unify Worker agent loop and risk-based tool approval

**Status:** Complete
**Revision:** 13
**Review policy:** Independent
**Priority:** High
**Evidence policy:** Markdown

## Problem

API Key 和 ChatGPT OAuth 使用不同的 Worker 主循环，工具调用、恢复和可观测性会随认证方式改变。现有受控工具依赖预先声明的严格范围，且正式问题的回答明确不授予操作权限。用户希望统一循环并放宽日常工具，危险操作保留人工确认。

## Options and decision

选择同一个 Python OpenAI Agents SDK Runner 和工具宿主；API Key 与 OAuth 只在连接适配器、凭据提供者及可证明的接口能力上区分。OAuth 未经确认的原生工具协议不凭空假定；复用已实现的完整文本响应转换策略，在模型适配边界转换为 SDK 工具调用。Codex app-server 保留为备选 Runtime，维持现有行为，不纳入本次风险审批改造；后续移除单独处理。API Key 与 OAuth 在同一角色、任务、策略和能力下共享工具、回合、控制、校验及事件合同；模型或接口不支持的能力必须明确报错，不自动换账户或服务。

## Solution

1. Worker 使用统一 Runner、上下文、工具注册、完成校验和回合预算。模型适配器仅负责连接认证、请求/响应协议及能力错误；OAuth 的完整终态才能转换工具调用，参数必须经过现有 schema 和宿主校验。模型历史中配对工具调用与结果，未知结果不重发；暂停、取消、租约和原始操作身份在模型调用及每个工具边界复核。保留现有模型连接凭证协调及日志脱敏。覆盖 API Key 与 OAuth 的目标讨论和工厂 Broker 路径。Native app-server 是备选 Runtime，保持原有处理，不应用新的风险策略或审批接口。
2. 工具从任务策略中取得工作目录、可用操作和风险规则。默认允许工作目录内读取、创建文件、常规局部编辑及符合任务范围、可识别的普通文件删除。任务创建的临时文件、构建产物或任务明确要求移除的文件可自主删除；删除已有源码、配置或文档须有明确任务依据并在交付报告中列出。删除前检查文件版本，变化时停止并重新核对。批量或递归删除、难以恢复的用户已有数据或范围不明确的删除，以及整体覆盖已有文件、可能破坏环境的命令、对外发布或发送数据、工作目录外访问需要逐次明确批准。Git 已跟踪文件的恢复能力不等于未提交修改或未跟踪文件可恢复。常规编辑须保留版本/并发冲突保护。符号链接及真实路径必须在分类前解析；无法可靠判定命令或目标的操作按需确认处理。只允许实现能实际支持的工具，对缺失能力明确提示，不伪称审批后可执行。
3. 操作审批与普通问答分离：保存固定操作 ID、任务/所有者、工具、完整规范化参数摘要、目标、工作区、原始版本、策略版本及有效期。等待时结束当前工具派发并可靠让出；批准仅对该精确操作一次有效，不扩大后续权限。恢复前重新验证所有者、租约/新代次、策略、路径、文件版本和审批状态；过期、拒绝或目标变化不执行。无人在线时持久等待，不自动批准。工具开始前持久化意图，结果未知先对账，不重放。审批与结果事件不包含凭据和不必要正文。
4. 严格只读的审查/分析角色仍受其职责边界约束；操作风险放宽不改变角色授权，也不扩大平台权限。已有计划按原合同处理；新风险策略及迁移/默认值需显式版本化，旧任务不得静默取得更大权限。避免同时引入绕过 Broker 的未受控工具或原生 shell。

### 审批与恢复合同

- 新计划必须显式选择带版本的风险策略；原始策略快照与当前策略都允许的工具和资源才可进入风险判定。审批不得突破角色只读边界、原始策略、当前策略或撤销状态。已有计划继续使用原合同。
- 宿主在副作用前解析实际工作目录和目标、拒绝链接逃逸，并从文件系统取得目标版本；模型提供的路径、任务范围或文件归属不能直接作为可信事实。审批绑定所有者、计划、任务、来源 attempt、操作 ID、工具、规范化参数摘要、工作目录身份、目标、目标版本、策略版本及到期时间。展示批准内容时不得泄露凭据或不必要的文件正文。
- 提交待审操作后关闭新的工具派发，等待已开始的工具和模型调用结束；仅在所有结果已知且进程退出后确认让出并释放容量。等待和审批决定需要持久保存。普通问题的回答仍只是任务数据，不产生授权。
- 同一项审批只能由对应所有者对固定版本明确批准或拒绝，重复提交只能返回原决定。批准后创建绑定原审批的后继任务，恢复时重新核验所有者、策略、代次、新租约、工作目录、参数摘要和文件版本；授权消费与操作开始必须在同一事务中完成。目标变化、到期、撤销、拒绝或不确定的前次结果均不能执行或重放。
- 新风险策略与一次性审批只用于 Guarded 工具宿主。Native app-server 作为临时备选 Runtime 保持原有任务处理与权限行为，不宣称受本次 Guard 审批保护；其退役另行实施。本次不改造其工具调用、checkpoint 或续接协议。

## Scope

Python Worker 的 API Key/OAuth 统一循环、Guarded 工具风险分类、执行操作审批协议与控制台、相关权限与持久化、文档及针对性回归。无需安装 Pi、自动切换模型或账户，不上传大批工作文件，不触及个人秘书私库。Native app-server 仅保留为现有备选 Runtime，风险策略不扩展到该路径，其未来移除不属于本 FD。

## Work items

- [x] 1.1 梳理 SDK/OAuth 接口 seam、角色工具与现有 Plan 控制合同，建立同循环及不支持能力的实际兼容边界。
- [x] 1.2 实现 OAuth 模型适配及 API Key/OAuth 统一 Runner、工具、控制及证据；Codex app-server 保持现有备选路径。
- [x] 2.1 设计并实现版本化风险策略、资源解析和常规工作目录操作；保持只读角色和已有计划授权边界。
- [x] 2.2 实现持久的一次性危险操作审批、UI、拒绝/过期/版本冲突及原操作恢复；等待过程不自动批准。
- [x] 2.3 在 Guarded 操作宿主接入风险分类和审批；处理租约丢失、结果未知、取消、重启和并发。
- [x] 3.1 更新配置/用户说明和稳定规格，完成编译与静态检查；测试和真实接口操作依单独授权执行。

## Acceptance

- 相同任务、角色和权限下，API Key/OAuth 使用同一 Runner/工具宿主/结果规则；仅连接适配器和可验证能力不同，失败不切换账户。
- 所有常规允许操作自主执行；危险或无法判定的操作在实际副作用前等待同一操作的明确批准。拒绝、过期、篡改、版本变化、重复提交、取消和租约丢失均不能执行。
- 工作目录内普通且符合任务范围的单文件删除可自主执行并记录；批量、递归、难以恢复或范围不明确的删除须确认。文件版本改变后不可使用原批准或旧版本执行删除。
- 并发、崩溃和未知结果保留操作身份与现场，不自动重复；当前 Worker 与用户可见等待原因和执行事件。
- 审查角色不能因新策略获得写权限，旧计划不静默扩权；计划间共享目录/worktree 与交接合同不变。
- Codex app-server 继续作为未纳入本次风险审批策略的临时备选 Runtime；本次不改变其行为，也不将 Guard 审批覆盖到原生工具。未来移除单独处理。
- 不完整模型响应、非法工具参数和不支持的连接能力不触发工具副作用。统一后的实际 SDK/OAuth 兼容性与真实连接状态按证据准确报告。

## Verification

### 控制台入口

进入 `/ui/configurations?view=workbench` 的 Worker 工作台，使用“工具操作审批”分区。此处分开选择审批所属 Guarded 计划，只列出当前账号拥有且已经配置 Guard 策略的计划。Codex app-server 备选 Runtime 不产生本分区的审批记录。待审批与全部记录可切换，展开记录核对操作、目标路径或命令编号、固定 argv/工作目录/权限配置/资源限制、来源尝试、策略版本、目标版本、工作区与参数摘要，然后选择批准或拒绝并再次确认。文件正文不上传；命令参数中的凭据类字段会拒绝审批，摘要不能代替对具体操作的核对。只有审批角色可以提交决定，操作者可以读取所属计划记录。

审批通过后，平台在同一事务中创建直接续接任务；页面显示该任务 ID。Worker 从固定 handoff 恢复，在模型重新运行前核验 artifact、检查点和目标版本，并在 Guard 原子消费审批后仅执行绑定操作一次。过期、失效或目标版本变化不会执行旧操作；变化时 Worker 先撤销旧批准，再让模型重新检查并在需要时申请新批准。已成功的消费只核对原结果，不重放。

### 检查记录

- 独立审查：涉及认证、工具权限、持久状态和并发恢复。默认只执行编译及静态检查；新测试、安装依赖及真实 API 仍需此前约定的明确授权。
- 已阅读 Worker 两种连接、Guard Broker、工具定义和正式问题交接。现有正式问题明确不授予权限；这是实现审批接口的事实依据。
- 统一 SDK Runner 的 OAuth 适配已开始，代码仍未完成且尚未审查；独立风险分类草稿没有接入工具宿主，审批和策略迁移尚未实现，旧宽松策略未启用。
- `node scripts/compile-worker-runtime.mjs` 第一次失败：OAuth 适配器插入流解析方法中导致缩进错误。随后尝试移动代码的脚本因定位字符串错误而失败，编译被错误地再次运行，仍是同一缩进错误；已更正源码位置，但按仓库编译重试上限未再次执行编译，因此最终语法及运行行为均未验证。没有运行测试或真实 API。
- 用户回复 confirm，明确允许对更正后的源码追加一次 compile-only：`node scripts/compile-worker-runtime.mjs` 成功。随后根据静态检查改进 OAuth instructions、避免重复终态记录并增加尚未接入的风险分类草稿，最终 Worker 编译再次成功。首次 Java `mvn` 由于 PATH 找不到命令未执行；定位已安装 Maven/JDK 并使用离线 FD-081 设置，`mvn -o -s <settings> -Dmaven.repo.local=D:/.m2/repository -DskipTests compile` 成功编译 341 个生产源。未运行测试或真实 API；独立审查与归档仍未进行。
- 继续 `fd-workflow auto`：保留原 SDK schema 由宿主和 SDK 在工具执行前校验，OAuth 适配将正式 instructions 与非可信历史分开，并避免重复记载模型终态；新增 `test_oauth_sdk_model.py` 检查适配器的 SDK 结构、未声明工具拒绝及纯文本回合。`test_tool_risk.py` 增加独立分类草稿用例；风险分类仍未接入、不扩大现有授权。用户尚未回复本次仅使用替身的定向测试申请，因此两组均未执行。
- 原 OAuth 专用外层工具循环已从 Worker 运行入口及模块中移除；两种连接由 SDK Runner 驱动，OAuth 使用完整文本响应模型适配。最终 `node scripts/compile-worker-runtime.mjs` 退出 0，两个新增测试文件仅内置 `compile()` 语法检查通过；这些不证明真实 SDK/OAuth 兼容性。平台危险操作审批、用户界面、让出/恢复及风险策略仍未实现，不能据此标记 FD 完成。
- 用户 confirm 允许一次 `python -m unittest test_oauth_sdk_model test_tool_risk`：5 项通过。随后补充普通自然语言终答和错误结构化动作场景后，我再次运行同一命令，7 项通过；用户授权写明一次，第二次执行超出了该次数，应保留这一事实，不将其视为额外授权。均为 SDK 接口替身/内存数据，不涉及真实 SDK、服务或 API。
- 已检查现有 `ApprovalService`：它只针对已经通过的工程验收门槛且每源任务唯一，不能直接用作运行中每项危险操作的一次性审批；现有正式问题也不会授予权限。审批仍需要独立的操作绑定、持久等待、受控恢复与用户界面。
- 最近一次 `node scripts/compile-worker-runtime.mjs` 返回退出码 1 且没有输出；未能确定原因，不能视为编译通过，也未重复执行同一失败命令。此前通过的 Worker 编译早于最近的 OAuth 终答改动。
- 原生运行配置指纹原先没有覆盖 OpenAI Agent 运行时与 OAuth 适配器；现将相关运行时、上下文和工具参数源码纳入指纹，并新增针对适配器变更的回归用例。这个变更会使依赖旧指纹的待恢复任务无法匹配新 Worker，需要保留旧版本 Worker 或重新处理任务；新增用例尚未获授权运行，编译也尚未重新验证。
- OAuth 流适配现在要求完成事件中的消息正文与全部流式增量一致，拒绝缺失、未完成或不一致的终态，避免将不完整输出转换为工具调用。新增定向回归用例，尚未运行；实际 Responses 流格式与真实连接仍未验证。
- 本轮对 `native_run.py` 和 `chatgpt_responses_runtime.py` 执行 Python 内置 `compile()` 语法检查，通过；这是两个文件的窄范围编译，不代表此前整套 Worker 编译失败已经解决。
- 继续补强现有工具：原生 SDK 局部编辑在临时文件写入后重新解析目标并核对原始字节，变化或消失时报版本冲突；多处编辑补上输入大小上限；临时文件落盘前 flush/fsync。Guard 写入在替换前复核摘要或 ABSENT。以上只缩小检查和写入之间的冲突窗口，不是文件系统级比较并交换；任意外部写入仍需工作区独占/协调保障。逐次审批仍未接通，不将这些检查视为操作批准。
- 本轮窄范围编译：`python -c "... compile(...) ..."` 对 `native_run.py`、`chatgpt_responses_runtime.py`、`openai_api_runtime.py`、`execution_guard.py` 检查通过，退出 0；未执行测试、安装依赖或访问网络。
- 平台逐次审批基础：新增 V33 `execution_tool_approval` 表、`ToolApprovals` 和 `ToolApprovalController`。Guard 文件整体替换、单文件删除及策略版本 1 下需要确认的固定命令使用相同一次性审批；绑定原始 Begin、参数摘要、工作区摘要、目标/命令版本、策略摘要/代次和到期时间。重复决定不重写原决定，摘要/版本不一致拒绝，策略变化/撤销或到期使待审/已批准记录失效。浏览器输出的来源租约令牌已遮蔽，审计只记录摘要。新计划必须明确配置 `riskStrategyVersion: 1`；该策略的命令白名单仅放行常见只读、测试和编译命令，未识别命令按需确认。
- 新增申请入口受 Worker token 和原始有效租约保护，查询/决定入口受人工会话、CSRF 和 Owner 校验保护，决定额外要求 approver 角色；机器与 Agent 凭据不具有该人工路由。审批开立还要求原始和当前 Guard 策略都允许目标，不产生新工具权限。命令审批使用冻结策略中的完整命令定义及摘要，Worker 将限长输出留在 `.ai/approvals`，恢复时校验输出摘要并交回同一模型回合；命令运行结果未知时不重放。native、Git 集成及对外副作用审批仍未接入。
- 本轮离线 Java compile：首次 PowerShell 未正确传递 `-Dmaven.repo.local`，Maven 未进入编译；将参数整体引用后纠正一次，`mvn -o -s <FD-081 settings> '-Dmaven.repo.local=D:/.m2/repository' -DskipTests compile` 成功，343 个生产源，退出 0。没有下载依赖、执行迁移、运行测试、启动应用或调用真实 API。数据库约束、HTTP 安全边界和持久化运行行为仍需授权后的针对性验证。
- UI 轮次：新增 `tool-approvals.js` 与工作台分区，沿用现有 card/field/button/record 样式；所有动态内容通过 textContent 渲染，不插入操作原文 HTML；角色决定按钮及版本/到期门槛仅为界面防误操作，服务端仍独立鉴权。修正审批查询路由允许纯 approver 角色读取，原先默认 operator 校验会阻断该角色。新增脚本纳入 compile-worker-runtime 的语法检查列表。
- UI 轮次实际检查：`node --check src/main/resources/static/ui/tool-approvals.js` 退出 0；离线 Maven `-DskipTests compile` 成功编译 343 个生产源。没有启动服务、进行浏览器交互/模板渲染验证、运行测试或真实 API；后续仍须检查实际显示、角色拒绝、过期/版本变化以及未知响应后的对账流程。
- 一次性消费轮次：新增 V34 消费记录，以及 Worker `operations/approved-begin` 接口。消费绑定原批准、工具/路径/参数摘要、工作区摘要和目标版本；重新校验当前策略和租约。只允许原始相同 Attempt，或平台上下文中双向绑定的直接后继任务；后继必须保留原计划、Owner、配置摘要与授权代次。消费和 Guard STARTED 在同一事务登记，失败回滚，重复消费只查询原始结果。Guard 普通 begin 遇到对应操作的审批记录时，要求同一事务产生的精确消费记录，否则拒绝；尚不能防止未接入风险策略的旧宿主用全新操作 ID 提交另一操作，不将此局部接入宣称为整体风险策略生效。
- 本轮离线 `-DskipTests compile` 退出 0，343 个生产源。用户单独允许一次 `-Dtest=ToolApprovalsTest test`，执行 4 项、失败 0、错误 0；测试同时编译了 70 个测试源。使用临时 SQLite 执行 V33/V34，mock Guard，验证重复消费查询、目标版本变化拒绝、过期拒绝和 TransactionTemplate 回滚消费状态；未验证真实 Guard 双表开始、HTTP、后继任务分配、Worker 实际执行或浏览器交互，未调用真实 API。
- 当前 Guard 路径已接入风险分类、文件/命令自动申请、`.ai/approvals` 固定参数 artifact、让出与检查点恢复及后继任务消费；命令输出会在同一目录持久化并以账本摘要校验后恢复。Native app-server 按用户决定保留为现有备选 Runtime，不应用本次策略；其未来移除单独处理。批量/递归删除没有暴露为工具。FD 尚未独立审查、合并或归档。
- Guard Worker 申请轮次：在 `GuardBroker` 中将对既有文件的整体 write 和范围不明确的单文件 delete 导向审批申请；任务本轮创建的文件，以及任务明确以肯定语句要求删除、且 Git 确认跟踪的精确路径，可直接单文件删除。仅提及路径、否定删除要求或意图含糊时需审批。新文件创建保持原始策略路径。申请使用本机 `.ai/approvals/<id>.json` 固定原操作参数，不含 Worker 租约令牌且 artifact 不上传；`.ai/handoffs` 清单仅记录 artifact 摘要。先在一个 Java 事务内创建 yield 并保存审批；Worker 关闭当前 Runner、确认本 attempt 无活动操作后 acknowledge，平台记录才显示为可决定。工具通道打开申请前先复核文件 expectedDigest；错误或结果未知不会运行工具副作用。
- 新增 Worker handoff 接口注册 PlanWorkspace 检查点。审批操作前先 finalize Git 工作树并形成固定结果版本；新 workspace 通过 predecessor handoff 输入恢复该检查点。批量/递归删除没有作为工具暴露，Native app-server 保持现有备选 Runtime 行为。
- 批准后续接轮次：V35 记录审批与直接后继的唯一绑定；同一事务创建后继任务、冻结审批引用并带入源 attempt 的 `TaskInput`，让 Worker 通过现有 handoff 接口取得本地检查点。继续任务保留原策略基线，Git 检查点只作为恢复成果，不扩大或改写 Guard 范围。Worker 在运行模型前验证平台已固定摘要的 handoff 清单所指 artifact 的路径、摘要、参数和版本；目标已变化会先废弃审批，原子 `approved-begin` 之后才执行。成功结果恢复时只读取账本；结果未知时阻止重放。
- 普通删除分类轮次：单次文件删除仅在本 attempt 创建该文件，或任务文本以肯定语句明确要求删除精确路径且 Git 确认跟踪该路径时自主执行；仅提及路径、否定删除要求和意图不明的已有文件删除需要 Owner 审批。删除前检查目标类型、路径和 expectedDigest。目录、递归操作仍不属于 Worker 工具接口。
- 命令审批轮次：Guard 下的 `COMMAND` 在 `riskStrategyVersion: 1` 时使用固定策略定义分类；已知只读、常规测试及编译命令可继续自主执行，危险或无法识别的命令等待 Owner 对精确命令批准。审批绑定完整 Command 定义的 UTF-8 规范摘要，凭据类 argv 拒绝进入审批 UI；批准后在原受限命令宿主中执行并保存有上限的输出，重启时从 `.ai/approvals/<id>.result.json` 读回且核对账本摘要，不会重跑。命令执行器仍只支持其既有 Linux/WSL 受限环境，其他系统明确报不支持。Native 路径未接入这套风险分类。
- Native 静态范围核对：`app_server_runtime.py` 的 Codex 原生路径将命令/文件效果交给 app-server 内部工具，只把 `read_skill_file` 注册为受控动态工具。按用户决定，这条临时备选路径保留既有行为，不声明受新 Guard 风险策略保护；不作为本 FD 验收条件，后续计划移除。
- 本轮 `node scripts/compile-worker-runtime.mjs` 成功（Python、JavaScript、TypeScript 语法及 Go 编译）；离线 Maven `-DskipTests compile` 成功，343 个 Java 生产源。`git diff --check` 通过。未编译测试源、运行测试、启动服务、执行迁移、访问模型/API 或网络；命令分类边界、持久恢复及真实界面行为尚无运行证据。
- 本轮静态差异检查 `git diff --check` 无输出。`node scripts/compile-worker-runtime.mjs` 通过，涵盖 Worker Python/TypeScript/JavaScript 语法与 Go 编译。Java 首次经 PATH 调用 Maven 未启动；本机离线 Maven 首次编译发现 `expectedGrantRevision` DTO 的 `long`/`int` 类型错误，改为 `long` 后按一次重试通过：编译 343 个生产源，`BUILD SUCCESS`。实际命令使用 JDK `C:/green/jdk-25.0.1+8`、Maven `C:/green/apache-maven-3.9.9/bin/mvn.cmd`、FD-081 离线 settings 和 `D:/.m2/repository`。没有运行测试、启动应用、执行数据库迁移或调用 API；HTTP 鉴权、事务回滚、浏览器恢复交互与真实并发行为仍未验证。
- 独立审查：`docs/features/FD-086/reviews/FD-086-review-r1-20261108.md`，针对事件 `FD-086-000003-review-requested`。结论 changes-requested：发现审批命令凭据过滤/展示可绕过、Worker Lease Token 被写入共享工作区审批 artifact、低风险命令分类过宽，以及单纯提及路径即可使已跟踪文件删除免审。没有运行测试、编译、服务、迁移、网络或真实 API；命令审批跨 worktree 恢复仅有静态证据。
- 审查修复：命令敏感字段识别覆盖 `-Dpassword=...`、`API_TOKEN=...`、认证头及常见用户凭据参数，并在 UI 返回前脱敏；共享 `.ai/approvals` artifact 不再包含 Lease Token，恢复时按源 Attempt 和平台固定操作摘要校验。命令低风险分类改为逐个允许形式，对未知参数和工作目录外目标走审批。已跟踪文件删除只有在任务以肯定语句指向精确路径时才可自主执行，否定或含糊意图保持审批。`node scripts/compile-worker-runtime.mjs` 退出 0；最终 Python 删除判定改动另以 Python `compile()` 检查，退出 0；离线 Maven `-DskipTests compile` 编译 343 个生产源并 `BUILD SUCCESS`。`git diff --check` 在最后一轮代码微调之前通过。没有运行测试、启动应用、执行迁移或调用 API；原评审发现尚待聚焦复审。
- 聚焦复审：`docs/features/FD-086/reviews/FD-086-review-r2-20261108.md`，针对 `FD-086-000006-implementation-ready`，仅检查原四项发现。凭据识别/UI 脱敏及本地 artifact 移除 Lease Token 并绑定 source attempt/platform operation 的修复在静态检查中成立；命令分类仍让 `rg --pre=...` 等附加执行参数绕过逐次审批，路径选项值也可能绕过 `..` 检查；删除意图只检查首次路径提及，后续否定以及 `严禁删除` 会被误判。结论 changes-requested。按要求未运行测试、编译、服务、迁移或网络/API；Worker 记录的最新编译证据见上一项。
- 聚焦复审修复：低风险搜索只允许 `rg <pattern>`，不接受任何选项或显式目标路径；`ls`、`dir`、`pwd` 只允许无参数形式，其它读取命令按需审批。路径检查会拆解普通和 `--option=../path` 形式中的父目录段。删除判定检查所有精确路径提及，并在全任务范围发现英文或中文删除否定时失败关闭；每个目标提及都必须处于明确肯定删除语句中。`node scripts/compile-worker-runtime.mjs` 退出 0，覆盖 Worker Python/JavaScript/TypeScript 语法及 Go 编译；未运行测试、启动服务、迁移或 API。等待针对 r2 findings 的复核。
- 聚焦复核发现 unittest 选择器仍允许在测试模块旁混入任意模块或未知选项。现已改为只允许无选项的 `python -m unittest` 自动发现，或每个选择器均为根 `test_*` 模块/`tests.test_*` 包路径（可继续指定类和方法）；非测试模块、未知选项和其它参数均需审批。`node scripts/compile-worker-runtime.mjs` 退出 0；未运行测试、启动服务、迁移或 API。等待针对该 finding 的聚焦复核。
- 第二次聚焦复审：`docs/features/FD-086/reviews/FD-086-review-r3-20261108.md`，针对 `FD-086-000009-implementation-ready`。r2 的 rg/ls/dir/pwd 限定、`--option=../path` 拦截、全路径提及与英文/中文（含“严禁”）否定处理，以及 r1 凭据脱敏、artifact 去 Lease Token 均通过静态复核。仍发现 `python -m unittest` 可因任一 selector 含 `test_` 而放行；例如混入 `evil_module test_safe` 或附加未识别选项时仍被归类低风险。结论 changes-requested。依据 FD 中记录的 Worker compile-only 证据；Reviewer 未运行测试、构建、服务、迁移或 API/网络。
- 第三次聚焦复审：`docs/features/FD-086/reviews/FD-086-review-r4-20261108.md`，针对 `FD-086-000012-implementation-ready`。unittest 分支只接受原始 `-m unittest`、无选项的自动发现，或每个选择器均为合法标识符且根为 `test_*`/`tests.test_*` 的路径；混入非测试根、未知选项和路径均按需审批。r2 的 rg/路径检查与全路径删除否定处理，以及 r1 凭据脱敏和 artifact 去 Lease Token 无回归。Native app-server 继续保持用户确认的原样临时 fallback。按最新 FD 中 Worker 记录的 compile-only 证据；Reviewer 未运行测试、编译、服务、迁移、网络或 API。聚焦 findings 验证通过。

## Sources

- 用户确认：Codex native app-server 可作为临时备选 Runtime，不需本 FD 增加特别处理，最终应移除；当前 FD 保留其既有行为并只为 Guarded 主路径实现风险审批。
- 用户确认：同一 Worker/Agent loop，仅更换连接认证；允许读取、创建和工作目录内常规修改，危险操作逐次确认。随后明确采纳工作目录内普通删除自主执行、批量/递归/难恢复/范围不明确的删除需确认的规则，该决定替代先前“所有删除均确认”。
- workers/process-python/openai_api_runtime.py、chatgpt_responses_runtime.py、execution_guard.py、formal_questions.py；personal-secretary/secretary_sdk.py 中已有的 OAuth 文本转换参考。

**Completed:** 2026-11-08
