# FD-005: Worker 公共执行基础与 pi 机制融合

**Status:** Complete
**Revision:** 56
**Priority:** Medium
**Test policy:** Independent
**Evidence policy:** Dual

**Design readiness:** Ready — 全会话 G2 及固定原生启动适配器已获用户批准；Python 固定 wheel 已按授权完成源码核对。真实平台与端点效果不由源码或编译推导。
**Planning session:** fd005-planner-20261004-cf390565
**Source event:** FD-005-000002-design-requested

本 FD 承接整个 ISSUE-001，以多角色公共执行基础为交付目标。用户于 2026-10-04 请求 fd-workflow FD-005 auto 与 implement；当前 Planner 已落实设计决定，后续角色交接以 CLI 收据为准。

## Problem

Requirements、Architect、Planner、Coder、Integrator、Reviewer、Verifier、Integration Verifier 和 Repairer 都需要处理任务期限、租约、工作区、上下文、工具权限、并行、输出及报告。多个运行时分别实现这些机制，维护者需要重复修复，操作和验收人员也难以判断失败、副作用及结果版本是否一致。

API Worker 目前将多项职责集中在 `index.ts`。任务超时信号主要在模型执行内部建立，Git 收尾尚未接入同一个取消信号；工具返回主要是文本，Skill 与参考材料整体展开。源码已经启用模型并行工具调用，并对同路径写入与编辑设置进程内队列，但读取、shell、Git 和进程外写入尚不在同一个协调边界内。这些是静态发现，不是已证明的运行故障。

用户要求融合 pi 的核心机制，建设覆盖所有现有可执行角色的公共基础，并按价值迁移 aiw 算法或调用 CLI/插件。控制平面继续负责调度、模型政策、租约、重试、审批和验收；Worker 负责一次 attempt 的执行。

## Options and decision

| 方案 | 收益与代价 | 决策 |
|---|---|---|
| 各角色和运行时分别继续扩展 | 初期局部修改少，但生命周期、权限和输出行为继续分散 | 不采用为整体交付路径 |
| 整体替换成 pi Agent 并沿用其会话和工具体系 | 可以取得较多现成功能，但扩大 SDK、模型政策、工作区和会话契约变化 | 不作为首期方案；只有必需能力缺口经基线确认后才另行比较 |
| 保留当前运行时，融合确定性算法和执行机制 | 可以渐进迁移，保持协议与角色契约；需要明确能力矩阵和公共边界 | 采用，先 API TypeScript，再按依赖对齐其他运行时 |

公共模块先作为 API Worker 内部职责边界落地，第二个真实消费者出现后再提取 TypeScript 公共库。跨语言共享行为契约、角色样例和验收场景，不要求所有 SDK 实现无法观察的内部工具事件。

pi 优先提供工具钩子、并行结果关联、不完整调用保护、输出截断、多区域编辑、等待 I/O 结束再释放的队列、上下文预算和生命周期事件的参考。复制或改编源码保留来源与 MIT 许可。aiw 优先提供来源与证据校验思路，以及身份固定的 CLI 查询适配。

### 已批准政策

- G1：接受取消或租约丢失竞争窗口内可能已经完成的远端发布，必须记录与对账。停止新增副作用，过期结果不得成为有效完成；远端状态无法确认时记录未知。首期不承诺零远端写入或自动回滚。
- G2：必存执行元数据，访问沿用主机文件权限；新增 attempt 日志保留 30 天。关键证据保存失败不回报成功，默认不保存完整提示词、源码或密钥。密钥和 leaseToken 延续禁止记录规则。用户于 2026-10-05 进一步明确「G2 覆盖全部会话；先补充 Codex 禁存设计」，因此包括 SDK/CLI 自身及派生会话，不豁免既有 Codex session 持久化。
- 批准来自 2026-10-04 当前会话用户回答“批准上述政策并交接 FD”，完整提问保存在 Issue 计划。具体接口、SDK 变更、实施批次和额外验证授权不由该回答推导。

## Solution

### 公共职责

| 模块 | 对外职责 | 内部负责 |
|---|---|---|
| AttemptRunner | 执行一次已领取 attempt，产生符合当前协议的终态 | start、心跳、整体期限、取消原因、错误归类和资源收尾 |
| WorkspaceSession | 准备、校验、发布和关闭指定工作区 | 基线版本、只读检查、Git 提交、发布记录和清理 |
| ContextAssembler | 按角色和预算生成有来源的输入 | 必需规则、冻结 Skill、材料目录、预算、历史与派生摘要 |
| ToolHost | 执行一次经校验和授权的工具调用 | 参数、资源冲突、取消、并发与输出上限、结果分类 |
| AgentExecution | 在批准的模型路径上执行当前运行时 | SDK 调用、回合、流事件、usage 和能力差异 |
| EvidenceRecorder | 保存有界执行元数据和结果引用 | 关联、事件顺序、脱敏、保存失败、容量与保留政策 |

角色政策和报告校验作为这些模块的输入与输出规则。模块名称用于划定职责，不预设六个独立包或所有运行时都必须实现相同接口。

### 生命周期与资源协调

整体期限覆盖任务准备、上下文、模型、工具、报告校验和 Git 发布；清理使用独立的有界期限。取消区分租约丢失、整体超时、操作超时和显式终止。drain 停止领取并允许在途任务结束。

取消后停止新增副作用，等待活动 I/O 和子进程结束后再释放队列或删除工作区。Git 收尾等待工具终态；发布前后检查所有权。上报失败不触发 Worker 自行重新执行整个任务，恢复和重试继续由控制平面管理。

独立读取和不同文件操作有界并行；同一文件的读改写全过程串行，读取与写入明确一致性规则。无法声明文件范围的 shell 按工作区副作用协调，插件按操作声明资源和服务额度。提示词不承担资源协调的执行保证。

### 工具、上下文与证据

工具结果区分模型可见内容、状态、完整性、截断范围和可选结构字段。行、字节及提示文字共同计入上限；JSON 完整返回或明确拒绝，搜索未完成不能表示为完整结果。编辑先在同一原始内容上校验全部匹配及重叠，再一次写入并返回有界 diff；保留 BOM 和换行，首期保持严格匹配。

普通可修正工具错误可以交还模型；租约丢失、未授权副作用或资源状态不确定需要终止相关执行。模型最终文本与执行证据分别校验，继续使用当前 manifest、报告字段、failureCategory 和 verdict。

首期保留现有 Skill 展开方式与 Agents SDK，先记录初始输入组成、来源版本、回合和可获得的 usage。精确值、估算和未知分开记录，不把字符除以四当作可靠中文计数。按需加载和历史压缩在必需材料、窗口、预算及 SDK 历史契约确认后加入；摘要可关闭，不拆散工具调用与结果，不替代人工确认。

执行元数据包含 task/attempt/role、来源和运行时版本、turn/tool call 关联、状态、时间、错误、截断、副作用与结果版本。必需关联、输入版本、工具终态、取消、发布状态和结果校验属于关键证据；额外进度展示等观察信息可以降级。具体事件字段与容量限制在 2.4 中形成清单，完整内容证据另定最小范围。

证据放在 Git 工作区外的 attempt 专属目录，不随 worktree 清理丢失，不被 `git add -A` 提交。30 天针对新增 attempt 日志，不改变已有控制平面最终报告、需求审批和 FD 验收工件的保存政策。关键证据保存失败时不回报成功；已有副作用仍记录为需要对账，不能声称已撤销。

aiw 插件由 ToolHost 适配。固定可执行入口、插件版本或摘要、解释器、参数、输出契约和工作目录；使用参数数组、必要环境变量与有界输出。查询先行，网络与外部写入逐操作授权，插件输出经 Worker 校验后才成为证据。

### 兼容、迁移与回滚

首期保留 Worker Protocol v1、角色名、环境配置、模型路径、旧 Profile、规划 manifest 和 verdict。新增内部字段不能让现有严格配置校验无提示失效；需要公共配置、协议、存储或 API 变化时先形成单独兼容决策。

按新 Worker 身份试运行，先只读角色，再到编码、集成和修复。升级前 drain，保存旧包及配置；回滚后由控制平面处理未完成 attempt 和租约。外部副作用单独对账，回滚包不等于撤销发布。

## Scope

覆盖原整体计划的五阶段、十二个工作项及九个现有可执行过程角色。先落在 `workers/process-typescript-with-api`，再对齐 Codex TypeScript、Go 和 Python 的公共行为。旧 `codex-*` 示例的维护定位在角色盘点中明确。

测试范围决定（用户 2026-10-05 明确）：后续独立 Tester 仅以 `workers/process-typescript-with-api` 的生产入口和九个角色为受测对象；其依赖的 `workers/runtime-core` 可经 API Worker 路径间接覆盖，但不单独运行公共核心、Codex TypeScript、Go、Python、原生 launcher 或平台入口的测试。测试代码仍按 FD 契约放在仓库根 `tests/`。这是测试范围限制，不削减上述实现范围、G2 政策或验收要求；其他运行时和真实平台的行为未获测试证明，应在报告与审查中列为未验证风险。

Release Readiness 与 Orchestrator 保留现状，不新增可执行 TaskType。demo-deployer 的部署执行能力不默认取得公共授权。

本 FD 不建设另一套任务调度、租约或审批系统，不搬入 pi 界面、会话树或自由模型选择，不默认替换 SDK、引入多 Provider 或修改控制平面数据库与公开 API。aiw 的 FD/Task 管理不进入产品运行时任务推进。

### 设计门槛

最初门槛已通过源码与以下具体决定落实；新增全会话 G2 门槛见下文，运行验证没有被描述为已通过。

1. 九个可执行角色、Verifier 的集成别名及报告契约见 [公共执行契约](../../workers/WORKER_RUNTIME_CONTRACT.md)。实际 Agents SDK 0.3.9 的接口和缺口见 [SDK 基线](../../workers/SDK_CAPABILITIES.md)。包装 Model 的完整终态检查先于工具执行，未知或不兼容 Responses 明确失败；真实部署端点尚未调用。
2. TaskExecutionService 的 complete/fail 只接受 RUNNING 且有效的所属租约，不提供重复完成返回成功的幂等接口。Worker 在上报响应不确定时记录未知，由控制平面恢复，不自行重跑。沿用原 WorkspaceResult、report 和六种 failureCategory，不更改协议或数据库。
3. 公共期限从领取后的 attempt 执行开始，默认 15 分钟；API 的既有 taskTimeout 配置继续作为别名。清理独立有界。取消后等待活动 I/O 和子进程终态，无法确定资源已结束时保留工作区并报告缺口。平台进程树限制显式记录，不视为沙箱保证。
4. 关键元数据为输入版本、调用关联、操作终态、取消、发布状态、报告校验和上报状态；新增日志单 attempt 默认上限 4 MiB，超限或关键写入失败阻止成功。日志在工作区外，终态后保留 30 天，只清理拥有本实现标记的终态目录；不自动清理未知活动日志或用户目录。
5. 初期保留完整 protocolSkills、primarySkill 及现有 references/agents 展开。输入按 UTF-8 字节设显式容量上限并记录组成；不能适配时失败，不悄悄丢弃必需材料。历史压缩默认关闭，启用需显式预算，使用当前批准模型、同一期限，最多两次摘要；不拆散调用对，原始可见来源在 attempt 内有界保留，关闭后不落盘保存正文。未知模型窗口和 usage 明确标记。
6. TypeScript 第二个消费者出现时提取无外部依赖的本地 ESM 公共模块；运行时桥接同时支持仓库和发布目录，发布脚本显式携带公共模块。Go/Python 共享行为契约和样例。旧 codex-* 保持参考示例定位，demo-deployer 保持独立范围。

7. 全部 Worker 会话必须满足 G2，包括 Codex session/rollout、history、派生线程、memory、诊断和导出通道。用户已批准固定原生启动适配器保留 SDK，并允许仅下载 Python 0.157.0 wheel 核对源码。已确认公开 `launch_args_override`、`thread_start(ephemeral=True)` 和线程 ephemeral/path 回执。完整方案见 [Codex 全会话禁存设计](../worker-codex-no-store-design.md)。未验证版本/平台/配置组合在注册前拒绝；不以临时目录或结束后删除替代禁存。

用户于 2026-10-05 明确回答「采用推荐方案，继续实施」和「允许只下载固定包核对源码」。设计与下载 Gate 已解决，授权不包括 SDK 升级、安装 Python 依赖、模型或业务服务调用。实际部署证据缺失时维持运行时拒绝准入，不虚构已验证清单。

用户另行明确批准：在 API Worker 执行 npm install --ignore-scripts --no-audit --no-fund；将直接 openai 依赖对齐到 SDK 使用的 6.x 并更新 lockfile。安装成功后已读取 SDK 0.3.9 与其 OpenAI 6.49.0 源码；实施采用这一已观察版本，不扩大到 SDK 替换或其他依赖升级。

本次采用主工作区顺序实施，保留开始时已有的 Worker/Skills 迁移和其他改动，不自动提交不属于 FD 的修改。独立 Tester/Reviewer 在各自阶段接手；没有隔离触发或工作区记录，不执行工作树合并。

## TODO

公共 ESM 核心、API 九角色接入、Codex TS 公共生命周期、Go/Python 对齐和全会话禁存入口源码已编写；角色样例、来源与运维文档已补齐。首轮源码的一次无最终产物编译检查已通过。第二轮独立离线测试执行 261 个用例，256 个原始 pass-case、5 个失败，完整场景通过 255/285=89.47%；PM 已退回，Worker 领取 FD-005-000011-test-rejected 后修复工具参数验证、资源终态和领取循环，并补充公开接口。重开后的 Worker 领取 FD-005-000017-work-requested，修复 r13 独立静态审查发现的工作树清理失败仍可能上报成功问题；一次无最终产物编译检查通过，尚无针对新修复的行为测试。Worker 勾选代表源码落实，行为验收仍由独立 Tester、PM 和 Reviewer 处理。业务模型和真实端点未运行；没有实际 Codex 平台的禁存验证清单，新 Codex Worker 默认拒绝注册，不能据此声明真实部署通过。

r19 独立 Tester 的唯一获批命令 15/15 通过，含真实本地 Git 清理失败；但适用 API 场景仅 15/33 覆盖（45.45%）、分支未测，PM r20 因证据不足退回。Worker 已领取 `FD-005-000021-test-rejected`；核对 API 公开入口与该决策后，未发现需为覆盖缺口修改生产源码的具体失败，本轮仅准备下一次独立 Tester 交接。r23 独立测试 9 项中 7 项通过、2 项失败（API-27/29），综合完整场景 18/33（54.55%）；PM r24 按失败与覆盖缺口再次退回。Worker 静态发现 r23 夹具将 `maxOutputChars` 设为 256，低于生产配置至少 512 的门槛，结构化工具输出预算可能因此导致两项 `UNRESOLVED_TOOL_FAILURE`；这是待新测试验证的原因判断，不更改旧结果或生产代码。

r27 独立 Tester 使用合法输出预算执行唯一获批离线命令，10/10 通过；API-28/29 完整新增覆盖，API-27 的多字节读取子行为通过，但搜索部分完成仍未测。累计完整场景 20/33（60.61%），分支未测。PM r28 未批准低于 70% 的例外并退回；Worker 已领取 `FD-005-000029-test-rejected`。本轮仅更新证据与下一次 Tester 交接，生产源码不变，优先补齐 API-27、20、16、33 的完整可观察行为。

r30 独立 Tester 唯一获批命令 exit 1，17 项中 15 通过、API-16/33 两项失败；新增完整覆盖 API-22/27，累计 22/33（66.67%），分支未测。PM r31 因实际失败和低覆盖退回，Worker 已领取 `FD-005-000032-test-rejected`。静态核对确认 `complete` 的第 3 个实参是 workspaceResult、第 4 个才是 report；API-33 断言读取第 3 个位置属于测试夹具错误，保留 r30 失败证据。API-16 的生产入口在 `client.register()` 前调用 `assertExecutableRole()`，但 r30 未保存子进程 stderr，不能确定退出根因。下一轮独立 Tester 应在新文件中校正 API-33 断言并保存 API-16 有界、脱敏的入口错误类别与角色标识，不改写旧结果或执行其他 Worker。

r33 独立 Tester 唯一获批命令 exit 1：API-33 按第 4 实参报告契约通过，API-16 首个保留角色在模块或语法加载阶段退出，第二角色未执行；累计完整场景 23/33（69.70%），分支未测。PM r34 退回，Worker 已领取 `FD-005-000035-test-rejected`。生产入口源码的角色检查顺序仍显示在注册前，但独立夹具直接运行 `src/index.ts` 且使用自定义 TS 加载器，而包正式启动命令运行 `dist/index.js`；r33 仅保存宽泛加载错误类别，尚无法判断加载器、依赖或生产入口问题。下一轮只在 API Worker 范围内采集脱敏的具体 Node 错误码和模块类别，并对照真实入口构建约定；不改写 r33 原始失败。

r36 独立 Tester 唯一获批命令 exit 1、脱敏输出为空，API-16 没有可确认的行为断言；累计仍为 23/33（69.70%），分支未测。PM r37 因证据缺口退回，Worker 已领取 `FD-005-000038-test-rejected`。静态检查 r36 输出过滤器只接收以 `# FD005_ENTRY_META` 开头的行和 TAP 计数，但历史 r33 Node 输出的元数据行无 `#` 前缀，且当前 Node test 默认输出采用 `✔/✖` 风格；过滤器可能将真实诊断全部丢弃。下一轮新夹具应从**测试子进程退出前**生成受控 JSON 诊断文件或接受这两种行前缀，保留仅含允许字段的证据，不能修改 r36 原始结果。

r39 独立 Tester 唯一获批命令 exit 1：受控诊断确认测试进程启动、断言到达，两个保留角色子进程均在模块或语法加载阶段退出，没有 Node 错误码或安全模块 basename；API-16 仍未验证。PM r40 退回，Worker 已领取 `FD-005-000041-test-rejected`。此类别由测试正则中的 `SyntaxError`、缺失导出或缺失模块等多种情况合并而成；空错误码/模块名不能反推具体生产根因。下一轮仅在 API Worker 入口范围内保留允许名单加载错误子类别（如语法、缺失导出、缺失模块）和安全标识，先定位夹具/加载链路，再决定代码修复。

## Work items

- [x] 1.1 角色与能力契约（原 W01）。盘点九个可执行角色、报告字段、模型路径、只读边界和旧示例定位；交付逐角色矩阵及兼容输入样例。验收：旧 Profile 保持可加载，不支持角色明确拒绝，角色名称不自动授权操作。
- [x] 1.2 SDK 与端点基线（原 W02；依赖 1.1）。核对实际版本的并行、取消、长度终止保护、usage、流事件和钩子；交付支持矩阵、来源与缺口决策。验收：不支持能力明确标记，不静默恢复全部工具串行，不凭声明版本声称端点通过。
- [x] 2.1 AttemptRunner 与 WorkspaceSession（原 W03；依赖 1.1，SDK 接入遵循 1.2）。统一准备至发布的期限、取消与资源终态，遵循 G1；追踪当前控制平面租约和上报契约。验收：停止新增副作用、活动操作结束后清理、过期结果不被接受，未知远端结果可以对账。
- [x] 2.2 ToolHost 与资源协调（原 W04；依赖 1.2、2.1）。统一参数、授权、并发上限及文件/shell/Git 冲突边界；覆盖嵌套工具调用。验收：独立操作可以并行，同文件修改不丢更新，等待中取消及前次失败都能正确释放资源。
- [x] 2.3 输出与编辑算法（原 W05；依赖 2.2）。融合 UTF-8 截断、完整性标记和多区域编辑校验，保留严格匹配与文件格式。验收：预算含提示文字，长单行与多字节不破坏结构，重复或重叠编辑不产生部分计划修改，diff 有界。
- [x] 2.4 事件、证据与结果校验（原 W07；依赖 2.1 至 2.3）。落实 G2 的关键事件、故障处理、容量和 30 天保留机制；分开校验工具终态与模型报告。验收：工具关联、输入与结果版本可追踪，关键证据失败不回报成功，只读变脏或无效 verdict 按当前契约拒绝。
- [x] 3.1 ContextAssembler 与初始预算（原 W06；依赖 1.1、1.2）。建立来源、Skill 版本和首轮输入预算；按材料政策保留必需内容。验收：超限与未知 usage 明确处理，原展开方式可兼容，未经决定不省略冻结材料。
- [x] 3.2 历史压缩（原 W08；依赖 3.1、2.4）。设计有界且可关闭的摘要机制，沿用批准模型、同一期限与预算。验收：工具关联和关键约束不丢失，摘要标记为派生信息，有原始来源可回查，摘要取消和失败不引入无界重试。
- [x] 4.1 全角色 API 对齐（原 W09；依赖 2.1 至 2.4、3.1）。九个角色分别接入公共基础；自动摘要关闭时仍成立。验收：逐角色覆盖成功、失败、能力拒绝和报告兼容，公共行为由同一实现提供。
- [x] 4.2 aiw 插件工具适配（原 W10；依赖 2.2、2.4）。盘点安装环境中的内建与插件能力，先固定 Skills 查询及经授权的 GitHub 查询，写入能力后续单独决定。验收：身份与输出可校验，缺失/错误版本/超时/非法输出/无网络授权都有明确失败，插件不获取 leaseToken。
- [x] 5.1 Codex TypeScript 对齐（原 W11；依赖 4.1）。第二个消费者出现后提取确有共享价值的 TS 模块，保留 SDK 内部循环的能力差异。验收：原注册和模型政策兼容，不能观察的工具细节标记未知，公共生命周期与上下文来源行为一致。
- [x] 5.2 Go、Python 与运维对齐（原 W12；依赖 5.1）。按共享行为契约迁移适合的逻辑，完善多语言角色样例、版本、升级与回滚说明。验收：运行时差异可见，维护范围明确，旧配置可回退，外部副作用需要对账。

里程碑一：1.1、1.2、2.1 至 2.4；里程碑二：3.1、3.2、4.1；里程碑三：4.2、5.1、5.2。checkbox 记录源码交付进度，整个 FD 的独立验收尚未完成，阶段进度不能代表整个 Issue 完成。

本轮测试只对 4.1 的 API Worker 九角色及其实际调用到的公共机制建立行为证据。5.1、5.2 和全会话 G2 的非 API 平台部分仍保留实现与审查要求，但不列入本轮可执行测试场景或测试覆盖率分母；不得把这些历史勾选解释为运行验证通过。

## Acceptance

1. 同一公共基础用于九个角色，Reviewer 的写请求被拒绝，Coder 只在指定工作区按授权编辑；保留但不可执行角色继续拒绝注册。
2. 多个独立读取或不同文件操作能够有界并行；同文件编辑、shell 冲突、取消等待及失败后下一操作遵循一致的资源规则。
3. 准备、模型、工具或发布期间取消、超时或租约丢失，终态和清理可追踪，活动操作结束前不删除工作区；drain 与强制终止行为明确。
4. push 期间租约丢失时不回报有效完成；已完成或未知远端写入有目标引用、版本及对账信息，不能声称自动回滚。
5. 中文、多字节、长单行、截断、未完成搜索和超限 JSON 的模型可见结果明确完整性；被长度截断的工具调用不得执行。
6. 多区域编辑先完成全部匹配和重叠校验；重复、重叠或基线变化时拒绝，保留 BOM/CRLF，返回有界差异。
7. 首轮超限、usage 未知或历史压缩时，任务规则、验收、来源和工具关联保持有效；自动摘要可独立关闭。
8. 工具失败被模型忽略、只读工作区变脏、缺少 verdict 或关键证据保存失败时，不能仅凭模型宣称 done 通过。证据不进入 Git 工作区，不泄露密钥或 leaseToken；全部 Worker 会话遵守 G2，不能把 SDK/CLI 自身持久化排除。新增元数据日志保留 30 天，既有明确业务工件保留契约独立处理。
9. 插件缺失、错误版本、非法输出、超时或缺少网络/写入授权时给出明确错误；插件输出不授予新的权限。
10. 旧 Profile、manifest、报告和模型路径保持兼容；其他运行时未知或不支持的能力明确表示。
11. 升级先 drain，再按角色逐步启用；旧包及配置可以回退，未完成 attempt 与外部副作用分别恢复和对账。

本轮独立测试的通过或覆盖率只证明 `workers/process-typescript-with-api` 的可观察行为。跨运行时、原生入口、真实端点、升级与回滚等验收点仍须在审查结论中分别说明静态证据和未验证部分，不得借 API Worker 测试结果外推为已通过。

## Verification

新公共核心位于 `workers/runtime-core/`；四个 process Worker 已编写适配源码，Codex 全会话禁存入口和固定原生适配器已接入。当前具备静态源码证据，独立验收尚未完成。已读取 Issue、整体计划、aiw 评估、FD 模板、近邻 Worker 源码、实际安装 SDK 源码、Python 固定 wheel、控制平面上报契约和 CLI 本地帮助。

2026-10-06 PM 决策（用户指令“跳过 API-16”）：不再执行 API-16 测试，并在本轮审查中将其记录为跳过/未验证；保留 r39 的 exit 1 原始结果，不把两个保留角色的拒绝行为记为通过。Acceptance 1 的行为要求仍保留，当前没有运行证据证明该行为。接受 69.70% 场景覆盖率和未测分支作为流程例外；剩余风险由新的独立 Reviewer 按本次修订重新评估。此决定不改变其他验收项，也不伪造测试或审查结果。

2026-10-06 PM 决策：现有测试量足以进入独立 Reviewer 阶段，不追加测试；明确接受需求覆盖率 69.70%（23/33）低于 70% 的例外，业务代码分支覆盖率未测量。保留 Acceptance 1 中“保留但不可执行角色继续拒绝注册”的要求。r39 的 API-16 用例执行失败，两个角色在模块或语法加载阶段退出，未证明该要求通过；此失败不改写为通过，API-16 继续列为未验证风险并交 Reviewer 复核。该决定仅批准进入审查，不代表 FD 完成或豁免所需行为。

Reviewer r44 对 `FD-005-000044-review-requested` 的独立静态审查结论为 `changes-requested`；详见 [Reviewer 报告 r44](reviews/FD-005-review-r44.md) 及同名 JSON。Finding F-1：Acceptance 1 的 API-16 仍无证据证明两个保留角色在生产入口注册前被拒绝；r39 执行失败保持失败，覆盖率例外不豁免该行为。r42 Tester 领取结果未知，本次不推断其执行情况。Reviewer session：`fd005-reviewer-20261006-7c31f9a2`。

本轮独立 Reviewer 对 `FD-005-000047-review-requested` 的审查结果为 `changes-requested`，详见 [Reviewer 报告 r47](reviews/FD-005-review-r47.md) 及同名 JSON。Acceptance 1/API-16 行为仍未获运行证据；PM r46 明确允许跳过测试和接受覆盖率例外进入审查，但没有豁免 Acceptance 1。r47 还发现旧“后续验证计划”与本轮不追加测试的 PM 决定冲突，且旧文“Reviewer outcome 仍为 0”已过期。本段 supersede 该旧计数：当前历史结果包括 r44/r45 的 `changes-requested` 和本轮 r47 的 `changes-requested`。本轮不再执行 API-16 或其他测试；旧验证计划仅作为历史记录，不构成本轮测试授权。

2026-10-06 PM 覆盖决定 r50（用户要求跳过 API-16，并确认现有测试足够）：接受 r39 现有测试证据作为本 FD 的测试阶段决策，豁免 API-16 行为验证门槛，不再运行 API-16 或其他测试。该决定接受 69.70%（23/33）场景覆盖率、未测分支及 Acceptance 1/API-16 行为未验证的风险；它不证明保留角色已被拒绝注册，也不改写 r39 的 exit 1 或 r47 的 `changes-requested`。PM 直接覆盖旧 Reviewer 阻塞，继续交由独立 Reviewer 按本次修订后的验收政策检查其余范围。详见 [PM 覆盖决定 r50](reports/FD-005-pm-override-r50.md) 和同名 JSON。

Reviewer r52 的正式结论为 `changes-requested`（事件 `FD-005-000053-changes-requested`），唯一 finding 是本节后部一条旧 Reviewer outcome 计数未标为历史。PM 已将其更正为早期历史快照，并保留 r52 原报告及事件，不将 changes-requested 改写为通过。用户授权 PM 直接覆盖流程门槛；本次仅因该 finding 是可直接更正的文档计数、没有产品代码问题，PM 覆盖三次失败后通常暂停 Worker 的门槛，直接恢复 `Pending Verification` 并请求独立 Reviewer 复核。Worker pending handoff `FD-005-000053-changes-requested` 未被领取或伪造为已处理；本轮没有新增测试。详见 [PM 覆盖决定 r54](reports/FD-005-pm-override-r54.md) 及同名 JSON。

PM 已直接将状态改为 `Pending Verification`。尝试通过 CLI 从原 `In Progress` 状态发出 `test-accepted` 被拒绝（该迁移在当前状态无效），因此没有创建该事件，也未手改 receipt。当前状态依据 PM 最终权限直接更新；Reviewer 交接将由新的真实 `request-review` 事件建立。

实际交接命令：`aiw issue capture ISSUE-001 requirement-plan --file .ai/requirements/drafts/issue-001-approved-requirement-plan.md`、`aiw issue approve ISSUE-001 APPROVED --by conversation-user --reason ...`、`aiw fd new "Worker 公共执行基础与 pi 机制融合" --issue ISSUE-001`、`aiw fd claim FD-005 FD-005-000002-design-requested --session fd005-planner-20261004-cf390565`。批准 reason 的完整原文见 Issue metadata 和 decision-log。

按用户两次明确授权执行了 API Worker 的 `npm install --ignore-scripts --no-audit --no-fund`，直接 openai 对齐至 6.49.0；实际 Agents SDK 为 0.3.9。已通过 CLI 产生 `FD-005-000005-design-ready` 并由 `fd005-worker-20261004-2916aea6` 领取。补充 G2 时执行本地 `codex exec --help`、`codex app-server --help`，并获取官方 OpenAI 文档。按新增授权下载固定 `openai-codex==0.157.0` wheel，只读取源码、不安装/导入；SHA-256 `e97c61d4fda9540d3db309e27bcf46cae6e12293c5b9d74e98fe3d285f8477e3`。

2026-10-05 执行 `node scripts/compile-worker-runtime.mjs`，exit 0：两个 TS Worker 生产源码内存语法转译、公共/脚本 JS --check、Python 内存 compile、Go Worker 与 launcher 临时编译均通过，Go 产物已删除；没有重试。它不是 TS 类型检查或行为测试。当时静态查看 FD 范围的 git status，确认已有 skills 迁移及 README/发布配置等脏文件保留，不重置或统一提交。此编译为首轮源码证据；后续修复尚未据此声明编译或行为通过。

独立 Tester 依据 [Planner 授权 r5](reports/FD-005-test-authorization-r5.md) 仅运行一次 `node tests/fd005/run-offline.mjs --authorization docs/features/reports/FD-005-test-authorization-r5.json`，exit 1。222 适用场景中 138 个 Node 用例执行，78 通过、60 失败、84 未运行；需求覆盖 35.14%。公共核心 V8 278/393=70.74% 仅为局部计数，完整 FD 分支未测量。[测试报告 r5](reports/FD-005-test-report-r5.md) 保留 51 个证据前置失败、7 个部分执行与 2 项目标断言失败，原始证据未重跑或改写。

[PM 决策 r5](reports/FD-005-test-decision-r5.md) 退回，未豁免失败或覆盖缺口。修复补充 attemptNo 元数据、Recorder 与预算的公开契约、日志期间取消检查、资源等待未知标记，修正 native DEBUG 等环境和 start/read 回执身份/ephemeral/path 检查。API 角色适配从生产入口提取为同一公开 createApiAttemptAdapter，便于独立验证实际角色路由与报告。新一轮测试另行绑定实现事件、命令和授权；没有真实模型、业务服务、最终构建或平台禁存认证。

独立 Tester 依据 [Planner 授权 r8](reports/FD-005-test-authorization-r8.md) 仅运行一次 `node tests/fd005/r8/run-offline.mjs --authorization docs/features/reports/FD-005-test-authorization-r8.json`，exit 1，耗时 30.334 秒；三项临时 Go 编译 exit 0。285 适用场景中 261 个用例执行，256 个原始 pass-case、5 个失败；REPORT-manifest 仅部分执行，因此完整场景为 255/285=89.47%。24 个场景未运行，部分场景也保留在分母。core V8 629/777=80.95%、内存转译 API 94/116=81.03% 仅为局部插桩，完整 FD 分支未测量。[测试报告 r8](reports/FD-005-test-report-r8.md) 与全部原始证据保留，不重跑或改写；没有真实模型、业务网络或平台认证。

[PM 决策 r8](reports/FD-005-test-decision-r8.md) 按实际 5 个失败退回，未豁免失败。非法额外工具参数未阻止成功上报已到达目标断言；普通 Error 用例未建立可修正错误前提；两个摘要用例只发出普通请求、未到达摘要；Git finalize 缺少保留的 argv/stderr 与临时终态诊断。不能推断这些未到达的行为已经成功或失败，后续新增完整前提及诊断后独立复测。

本轮 Worker 从 FD-005-000011-test-rejected 合法接手。已按 Agents 0.3.9 实际源码使用 errorFunction=null，将原始 JSON 参数在 SDK 解析前交给公共 ToolHost 验证；不可恢复异常不会由 SDK 默认错误字符串吞掉。两个 TS Worker 共用 runWorkerSlot，未知资源后永久停止领取。初始化、阶段日志、报告验证、成功准备也纳入期限；catch/finally 共用有界清理窗口。正常成功记录 settled，未知终止保留协调器租约、工作区和 adapter；控制平面错误枚举保持不变。对应源码与公开契约尚未据此声明行为通过。

后续验证计划（按 2026-10-05 用户限定的测试范围）：独立 Tester 重新盘点仅适用于 `workers/process-typescript-with-api` 生产入口及九角色的可观察场景，使用该入口覆盖相关公共核心行为，特别包括 r13 静态审查提出的工作树清理失败与成功上报一致性。原 r5/r8/r11 的完整场景清单、失败与未运行记录保留为历史事实，不改写，也不沿用 305 作为本轮覆盖率分母。不要运行其他 Worker、公共核心独立入口、原生 launcher 或平台测试。入口与用例冻结后，由 Planner 检查所有调用代码，另行绑定新实现事件、FD revision/digest、Tester session、文件摘要与唯一精确命令；未经明确授权不执行测试。没有新的依赖或联网授权；2026-10-05 本轮执行 `node scripts/compile-worker-runtime.mjs`，exit 0，仅提供 TS/JS/Python/Go 语法或编译证据，不证明修复的行为；r8 临时编译仍是历史结果。非 API 运行时、真实端点、升级回滚及平台 G2 效果保持未验证，并在 Tester、PM 与 Reviewer 结论中分别列明。

本 FD 使用 Independent 测试与 Dual 证据政策：Tester 独立编写授权范围内的用例；实施、测试与审查报告提供中文 Markdown 和同名 JSON，关联 FD、来源事件、代码版本与实际命令。早期历史快照（早于 r44/r45、r47 和 r52）曾记录 Reviewer outcome 为 0；当前三次 Reviewer outcome 均为 `changes-requested`，详见 Verification 上述记录。不记录虚构覆盖率或平台证明。

Worker 首轮实施与编译证据：[实施报告 r5](reports/FD-005-implementation-r5.md) 和同名 JSON；Worker session fd005-worker-20261004-2916aea6，来源事件 FD-005-000005-design-ready。第二轮修复见 [实施报告 r8](reports/FD-005-implementation-r8.md)，来源为 FD-005-000008-test-rejected。本轮修复见 [实施报告 r11](reports/FD-005-implementation-r11.md)，来源为 FD-005-000011-test-rejected。历史报告保持原始事实；新报告与独立证据按新交接记录。

重开后的收尾修复见 [实施报告 r18](reports/FD-005-implementation-r18.md) 及同名 JSON；Worker session `fd005-worker-20261005-69fe2eb5`，来源事件 `FD-005-000017-work-requested`。r13 直接静态审查仍作为历史发现，正式 Reviewer 需依据本轮新交接和独立 Tester/PM 证据复核，不以历史报告替代当前 Verification。

[独立测试报告 r19](reports/FD-005-test-report-r19.md) 和 [PM 决策 r20](reports/FD-005-test-decision-r20.md) 保留 15/15 实际通过、15/33 场景覆盖、18 项未覆盖和分支未测的事实；原始证据在 `tests/fd005/evidence-r19/`。PM 未给予覆盖率例外，退回原因是 API Worker 行为证据不足，并非这 15 项测试失败。下一轮 Worker 交接见 [实施报告 r22](reports/FD-005-implementation-r22.md) 及同名 JSON。

[独立测试报告 r23](reports/FD-005-test-report-r23.md) 与 [PM 决策 r24](reports/FD-005-test-decision-r24.md) 保留唯一命令 exit 1、新测试 7 通过 2 失败、综合 18/33 覆盖和未测分支的事实。API-27/29 的原始失败保存在 `tests/fd005/evidence-r23/`；本轮 Worker 的静态夹具诊断见 [实施报告 r26](reports/FD-005-implementation-r26.md)，不能替代新的独立运行结果。

[独立测试报告 r27](reports/FD-005-test-report-r27.md) 与 [PM 决策 r28](reports/FD-005-test-decision-r28.md) 记录唯一命令 exit 0、10/10 新用例通过、累计 20/33 完整场景覆盖及未测分支；原始证据保存在 `tests/fd005/evidence-r27/`。r23 的失败仍为历史事实，r27 的合法夹具结果支持而不篡改原因判断。下一轮按 [实施报告 r30](reports/FD-005-implementation-r30.md) 仅补 API Worker 可观察缺口，需独立 Tester 新授权。

[独立测试报告 r30](reports/FD-005-test-report-r30.md) 与 [PM 决策 r31](reports/FD-005-test-decision-r31.md) 保留 17 项中 2 项实际失败、累计 22/33 完整场景、未测分支的事实；原始证据位于 `tests/fd005/evidence-r30/`。API-33 参数位置的静态诊断见 [实施报告 r33](reports/FD-005-implementation-r33.md)，API-16 的退出原因待新一轮独立证据；不能把源码检查或旧通过当作 r30 失败用例的通过结果。

[独立测试报告 r33](reports/FD-005-test-report-r33.md) 与 [PM 决策 r34](reports/FD-005-test-decision-r34.md) 记录 2 项中 1 通过、1 失败、累计 23/33 完整场景与未测分支；原始证据位于 `tests/fd005/evidence-r33/`。API-16 的宽泛模块加载失败需要更细的脱敏诊断，下一轮 Worker 说明见 [实施报告 r36](reports/FD-005-implementation-r36.md)。

[独立测试报告 r36](reports/FD-005-test-report-r36.md) 与 [PM 决策 r37](reports/FD-005-test-decision-r37.md) 记录唯一命令 exit 1、脱敏输出为空、无可确认新增行为断言；原始证据在 `tests/fd005/evidence-r36/`。输出过滤规则的静态定位见 [实施报告 r39](reports/FD-005-implementation-r39.md)，待新独立 Tester 在新夹具中验证。

[独立测试报告 r39](reports/FD-005-test-report-r39.md) 与 [PM 决策 r40](reports/FD-005-test-decision-r40.md) 记录两个角色加载阶段失败、1 项行为测试失败、累计 23/33 完整场景、分支未测；受控诊断在 `tests/fd005/evidence-r39/diagnostic.json`。下一轮错误子类别分析见 [实施报告 r42](reports/FD-005-implementation-r42.md)，不把宽泛加载错误当作角色拒绝。

效果指标沿用整体计划的初始输入大小、回合、调用、耗时、截断和可获得 usage；同任务与政策比较，未采集前不承诺改善百分比。

## Sources

- Issue: ISSUE-001

- [批准的需求计划](../../../issues/ISSUE-00003/issue-plan.md)；metadata revision 4，approval=APPROVED。
- [批准记录](../../../issues/ISSUE-00003/issue.toml)与 [决策日志](../../../issues/ISSUE-00003/decision-log.md)。approval source digest：`7ff4e1c8aff561ac06e4303ae0b911afc1d674c52c9dfb18766ecacc6881f835`；requirement-plan SHA-256：`fbd09ddac173968bf34c6aa233c99823b1ee8a91976fe11f373f07f5d7a942bc`。
- [Worker 整体融合计划](../worker-pi-integration-plan.md)，SHA-256：`8c0bcde838945fdf45f77464a3481db51e07de17913557546953328f2619289f`。
- [aiw 能力复用评估](../aiw-review.md)。
- [API Worker 生命周期](../../workers/process-typescript-with-api/src/index.ts)、[API 运行时](../../workers/process-typescript-with-api/src/api-runtime.ts)、[工具与队列](../../workers/process-typescript-with-api/src/tools.ts)、[角色](../../workers/process-typescript-with-api/src/profiles.ts)。
- pi 算法及其他运行时的具体来源链接沿用整体计划的“源码依据与验证状态”。

CLI 当前 Issue 的 promotion 字段记录旧 Task 交接。此处通过 `- Issue: ISSUE-001` 与 design-requested 收据记录新的 Issue-to-FD 交接，不手工伪造 PROMOTED 或 Task 状态。

**Closed:** 2026-10-05
**Disposition reason:** 用户决定自行测试并结束FD流程；r11未执行测试、305个场景未覆盖，r8有5项历史失败，独立静态审查发现工作区清理失败仍可能上报成功；未获正式验证通过。

**Reopened:** 2026-10-05
**Reopen reason:** 用户要求重开 FD-005，继续处理此前因暂停测试而以 Closed 归档的 Worker 执行及验证事项；保留历史未运行测试与失败记录。

**Completed:** 2026-10-05
