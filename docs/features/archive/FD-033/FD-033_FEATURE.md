# FD-033: 个人秘书动作授权按功能分组并简化范围设置

**Status:** Complete
**Revision:** 5
**Priority:** Medium
**Evidence policy:** Dual

## Problem

用户指出个人 Worker 授权页的动作列表没有功能分组，且授权粒度过细，人类需要逐一填写精确目标、步骤、允许状态、轮次和 JSON 参数，无法方便地给秘书安排工作。用户提出：对一种动作设定授权时间和累计执行次数即可，指定某种事务或任务应为可选范围。

代码事实：ActionScope、AuthorityActions 和动作准入当前依赖精确目标/步骤/固定参数，平台和私人 Worker 均消费该结构；只给下拉框增加分组或隐藏必填项，不能实现“不选具体事务/任务也能授权”。FD-032 的预算仅记录不应被误用于取消这次用户提出的动作授权次数上限。

## Options and decision

- 仅将现有字段折叠：可降低视觉密度，但仍要求用户或页面事先指定精确业务目标，不满足可选范围。
- 将现有精确目标改成通配符：会破坏目标归属、状态快照、读范围、版本和禁止规则校验，不采用。
- 业务层动作授权与具体执行分开：页面只配置动作、有效期和累计次数，可选择收窄事务/任务；平台在具体操作时自动固定目标、参数、版本和请求身份，并复验当前授权。采用此设计方向，按已确认范围实施下述接口与兼容设计。

## Solution

### 面向人的授权表单

先按功能分组展示可选动作，分组用于查找与选择，不把整个分组默认为已授权：

| 功能分组 | 已有动作示例 |
| --- | --- |
| 计划管理 | 确认、跳转、停止、撤回、完成、取消 |
| 任务执行与恢复 | 修复轮次、执行/评审模型、技术重试、独立评审 |
| 审批与工作问题 | 批准、拒绝、回答工作问题 |
| 交付与发布 | 创建发布计划、执行发布 |
| Worker 与运行管理 | 调整并发、排空、离线 |
| 记录管理 | 归档、排期删除、撤销删除 |

主要设置仅保留：秘书、动作、授权有效期、累计执行次数。“限制适用范围（可选）”默认收起，可选择事务或具体任务；不要求记忆或手填 ID。页面展示授权动作、范围、到期、已用/总次数、剩余和撤销入口。状态、世代、步骤号、快照、版本、固定参数 JSON 不再由人填写；业务操作实际需要的选择在操作发生时处理。

例：允许秘书在七天内执行“确认计划”最多二十次，可不指定某个计划；实际确认时由平台核对当前目标归属、状态和版本。选择某个事务/任务时只收窄本条授权。

### 授权次数与执行预算的区别

动作授权次数是这次明确提出的能力边界，设计为真实上限；同一授权下的多个适用目标共享计数，不能通过换目标重置。使用原业务请求身份保证一个被受理的操作只计一次；拒绝、查询和相同请求重放不增加次数。时间、token、费用等执行预算仍保持 FD-032 的默认仅记录，不因缺少这些预算阻止运行。

### 内部执行与兼容

业务层授权不能绕过现有身份、角色、有效期、所属账号、禁止/暂停/撤销、固定原请求或未知外部效果防重复。授权规则由人确认，具体执行参数由操作选择或系统确定，平台仍校验动作本身的业务条件；不得通过填入任意 targetId 访问他人记录。

旧精确授权、已有请求和记录须保留兼容；新增或替换授权时旧授权与计数的适用关系要明确，避免同时存在的旧路径绕过用户看到的新次数上限。采用下述增量存储、平台/私人 Worker 接口和旧授权过渡方案；不修改已有历史迁移，不把通配符塞入旧结构。

### 已确认的范围规则

用户已明确选择：未指定事务或任务时，覆盖当前账号现有及后续新增的全部适用目标，在这些目标之间共用本条授权的累计执行次数。选择某个事务或任务只会收窄范围。该决定不授权跨账号访问，也不代替具体操作的业务条件与版本核对。

### 工程方案

新增业务动作权限表，与旧 ActionScope 精确执行结构分开。每条权限固定 Owner、秘书、单一 action、ALL/PLAN/TASK/WORKER 可选范围、到期、总次数和已受理次数。ALL 明确包含当前账号现有及后续适用目标。增量迁移仅新建表/索引，不改历史迁移或删数据，本次不实际执行迁移。

人类同一秘书/动作只有一个当前业务授权期；明确创建新授权时显示将替代该秘书同动作的旧授权（包括旧精确路径），旧记录/计数/回执保留，新授权期从零开始。替代不是改目标自动重置次数，必须经新的显式人类确认。创建用 requestId 幂等，并核对 expectedPriorId 防并发替代。旧精确授权在未被替代前继续原协议；已有当前业务授权时，旧创建接口不能另建同秘书/动作的平行能力绕开上限。

具体操作分“固定”与“受理”：机器提交权限 ID、业务目标/可选任务与该动作必要选择，平台核对真实归属，生成仅用于本次操作的旧 ActionScope/子 Grant 和完整不可变原 Envelope。自动带入状态、轮次条件、目标版本/快照、世代和限制版本；业务选择如模型、重试源、发布文件/路径、授权申请/回答必须来自具体操作，不在授权表单预填。继续调用 AuthorityActions 的封闭字段/业务校验，不凭动作名称合成业务成功。

固定阶段用原业务 requestId 幂等，保存选择摘要、子 Grant 和原 Envelope；重试同选择返回原 Envelope，不用新快照悄悄重建。固定只写平台候选，不计次数、不执行业务。新路径最后仍走原 /actions 或 /question-replies 受理；在其重复回执检查之后、同一业务事务内核对父权限/当前角色/实际归属/可选范围。计数统一放在 AuthorityBudgets.consume 的父权限钩子：同一父权限/业务 requestId 仅计一次，包括 requirePublicationAuthority 的间接 A12_EXECUTE；外层动作与发布是不同动作权限，各核对自己的额度。业务拒绝/回滚不计次数，成功受理占一次且不因后续技术失败/取消返还；既有原请求重放只回原回执。父额度始终是硬上限，不受 FD-032 record-only 开关影响。旧 AuthorityBudgets/执行预算继续原计量模式。

子 Grant 不作为独立可续期能力；撤销/到期/替代父权限使子能力不再接受新动作/Claim，沿用原撤销与在途停止语义，不改写已受理回执。所有 legacy 子 Grant 消费也必须经过父核对，不能复制旧信封避开父计数。候选与实际目标的关联必须保留到受理阶段；不能只在固定时查所属账号。新历史回执可在身份重新启用后按原 Owner/Worker 查询，不依赖父权限仍可用，且不能借此读取其它当前业务数据。

目标归属按正式业务记录证明：本人创建的 WorkflowPlan、本人 execution_policy、任务的真实计划/上下文；审批追溯源任务，发布追溯源成果任务与正式发布记录。TASK 范围要保留精确来源任务并约束其对应操作/步骤，不等同同仓库或同计划任意任务。没有自然账号归属的 Fleet/普通 Worker 使用人类确认的 authority_target 归属；A13 人工登记时可确认未绑定的固定 GLOBAL:FLEET，A14/A15 显式选 WORKER 范围时可确认未绑定的普通 Worker。均先核对当前角色与 Owner，禁止覆盖其它 Owner。ALL 的普通 Worker 操作只包含已经确认归属的对象；机器准备不得首次认领。该细化沿用现有人类绑定边界，不增加机器认领权。

读取保持最小：新动作权限允许固定该动作所必需的本人目标状态，不自动把整个账号加入旧 read_scope，也不开放私人内容。旧读取协议不扩大。固定时完整旧 snapshot 仅在服务端求 digest，不向新机器接口回传其中的 spec、任务 instruction/report 或其它正文，只回完整原 Envelope 与必要结果摘要。新增机器权限目录与固定接口、平台人类管理/选择项接口均显式检查当前身份、Owner、角色与 CSRF；新路由须经 AuthorityHttpConfiguration 的现有机器白名单映射，不能落入不受保护的新控制器。计划/任务选择项只返回当前人类 Owner 可见的标题与引用。

私人 Worker 增加独立小模块，把新权限的具体选择固定后送入原 durable outbox；先保存原 requestId 和选择，再调用固定接口，响应未知只查询/重试同一固定请求，不生成新 ID。固定成功保存原 Envelope 后复用原 /actions 或 /question-replies 和回执核对。旧 queue_action/旧精确 Grant 保持兼容。需要修改的现有 Python 文件若已有其它任务差异，仅提交本 FD 的精确补丁，不整文件覆盖或暂存。私人资料不传入授权表单或候选，正式问答仍使用原用户确认投影。

### 设计审阅约束的具体落实

- 分离 parentActive 与 parentAdmissible：前者只检查授权期/当前角色/未撤销/未到期，供旧子能力效力、已受理 permit、模型和发布依据验证；后者在新受理事务内检查剩余并加计数。最后一次受理使剩余为零时，不使其已经 ADMITTED 的任务 Claim 失效。父计数独立于旧 reserved/settle，不因旧预算退回而减少。
- 任一秘书/动作首次迁入业务权限后保留 regime 标记（历史父记录即可证明），旧 issue 不能再为其创建同动作平行授权；所有旧同动作 Grant 在显式迁入/替代时撤销，已记录回执不改。父到期/撤销后不会回退到旧权限。CoordinationService 的 sourceTask=null START 对已迁入 A05 的秘书明确拒绝，要求先走新 A05 固定/受理路径；已确认计划的后续托管推进保持原规则，不新造 confirm/retry/repair。其独立托管授权在页面与说明中另列，不能把它描述为父动作次数。
- 动作/范围矩阵：ALL 对每个动作只覆盖可证明当前 Owner 的适用目标；PLAN 仅适用于确实属于该计划的动作。TASK 只开放 A03（精确重试源 taskId）、A11_APPROVE/REJECT（审批 sourceTask）、A12_CREATE/EXECUTE（文件的真实源任务）、A19_ANSWER（问题 source.taskId）；其它动作作用于整个计划、步骤、全局或 Worker，拒绝 TASK，UI 不提供无意义选择。A01/A02/A04 可选 PLAN，具体步骤在发生操作时确定，不能冒称只作用于单个物理任务。A18 仅 ALL/PLAN；A13 仅 ALL，A14/A15 为 ALL 或 WORKER。
- Owner 证明必须从正式计划 creator、execution_policy 和源任务/发布关系取得；多条可核对来源有冲突即拒绝，没有来源即拒绝。authority_target 不是任意认领正式业务对象的捷径；Fleet/普通 Worker 无自然账号归属，机器只能操作人类已经确认归属的键。人工 A13 确认固定全局归属，A14/A15 的 WORKER 选择可明确绑定未归属的普通 Worker；不允许接管其它 Owner。UI 对这类动作解释绑定影响。
- 新准备在 boundary.as(principal,...) 内进行。完整具体选择、源任务和 questionId 保存在父子关联，不把它们伪装成 Envelope.taskId/step。原 /actions 的副作用/版本/停止检查完全保留；接收任意 legacy 子 Grant 的路径均核对关联的固定 requestId，不能用子 Grant 另造请求。
- 新正式问答候选固定用户原 projectionJson/digest 与完整原 Envelope。A19 仍经 /question-replies，A18 按原具体申请 UUID/范围处理；PersonalQuestions 对具有真实新关联、同 Owner/Worker、精确问题/源任务/原投影的候选使用专用读取准入，旧路径仍检查原 read_scope。机器另以 permission-questions 最小读取所选权限/计划的适用正式问题，不改变旧通用读范围。不扩大通用 readable、state 或 target-state；原投影登记的实例/boot/世代绑定与逐字段信封相等检查保持。TASK 范围独立关联 sourceTask，不能填 Envelope.taskId（旧问答会拒绝非空）。
- 模型/发布关联若需另一动作权限，只能用该动作当前有效的父权限固定对应正式目标的子能力；间接发布的实际授权计数通过同一 consume 钩子，未授权或额度不足拒绝整个事务，不自行补权限。不得只选择旧同名 Grant 绕过新 A12 权限期。
- 新历史回执专用读取仅同 Owner 且同 Worker 的原 requestId，读取不依赖父 active/剩余；原 state/target-state 不因此开放。固定候选 GET 查询同样不调用业务、不换快照；丢失响应后只能回同原固定请求，不生成新 identity。
- 私人 Worker 的固定请求先保存到本地正式 record（不是既有动作 outbox），网络失败返回原 requestId/待核对状态，显式恢复先查原固定结果；有原 Envelope 后才幂等放入旧 outbox。新模块和少量 HTTP/路由白名单接线支持新操作 API；本 FD 不重写正在其它任务中调整的私人秘书页面或模型逻辑。

## Scope

原设计阶段只完成问题核对与草案，尚未修改源码。用户现已确定缺省范围，本次继续工程范围：动作分组、简化表单、可选业务范围、跨目标次数、具体执行解析、最小问答读取及旧路径兼容；采用当前主工作区范围提交，保持其它任务的差异。独立设计审阅发现 D1–D5，已在本 FD 逐项落实为工程约束和子项，无遗留人类范围决定。

不恢复时间/费用预算限制，不以审计或历史计量作为新工作门槛，不自动启用新动作或整组动作，不改变其它任务的个人秘书/模型/启动脚本改动。

## Work items

- [x] 1.1 核对现状与用户体验，提出功能分组、主要字段和授权/执行边界。规模小、难度低；证据为本草案及 ActionScope/AuthorityActions/授权页/私人 Worker 调用代码的静态读取。
- [x] 1.2 确认缺省范围，明确新规则与旧授权/累计次数关系和增量存储/接口方案，完成独立设计审阅。规模小、难度中，依赖 1.1；用户已确定 ALL，FD-033-design-review-20261024-5d1a7275 的 changes-needed D1–D5 已逐项纳入上述工程约束，不把原报告伪称 ready，无遗留未决策略。
- [x] 1.3 实现业务动作授权、当前归属检查、跨目标次数和具体执行解析。拆分为以下独立子项；父项在子项全部真实完成后标记。
- [x] 1.3.1 增量父权限/候选/消费存储与人类登记/撤销/目录。规模小、难度中，依赖 1.2；明确新授权期、旧 regime 和幂等替代，完成证据为单一授权服务与新迁移。
- [x] 1.3.2 实现正式 Owner/范围矩阵与具体候选固定。规模小、难度中，依赖 1.3.1；负责唯一目标解析，输出原 Envelope 和最小摘要，保存精确问答关联。
- [x] 1.3.3 接通统一父消费、子能力效力、旧并行路径及历史回执。规模小、难度中，依赖 1.3.2；间接发布亦计数，协调 START 不绕过，额度用尽不破坏最后已受理 permit。
- [x] 1.3.4 接通私人 Worker 新固定/排队 API，保留本地原 request/候选及旧 outbox。规模小、难度中，依赖 1.3.2、1.3.3；只提交新模块及现有文件的本 FD 精确补丁，旧接口保持兼容。
- [x] 1.4 实现功能分组和简化授权表单，提供可选事务/任务选择及清晰授权列表。规模小、难度中，依赖 1.2、1.3。
- [x] 1.5 同步使用说明与稳定规格，提交适用编译及独立实现审阅所需的 Worker 证据。规模小、难度中，依赖前述实现；证据 pair 已提交，独立 Reviewer 结论属于随后交接阶段，归档前仍须真实通过；未授权的运行检查保持未运行。

## TODO

- 全部实现项及 1.5 文档/Worker 证据已完成；独立实现审阅 r1 对 8ce1590..8628bda 静态审阅通过，由 host 核对真实结论事件后继续范围交付与归档。最初 1.5 将“准备审阅证据”与下阶段结论混写，现澄清阶段归属，未省略独立审阅。

## Acceptance

1. 人能先按功能找到动作，不必理解内部动作编号、状态、轮次或 JSON（1.1、1.4）。
2. 创建动作授权只需选择秘书、动作、有效期和累计次数，事务/任务可不选；未选范围遵守用户确认规则（1.2–1.4）。
3. 选择范围只会收窄授权，不跨当前账号；多个目标共享授权次数，重复请求不重复计数，到期或用尽后不再接受新操作（1.2、1.3）。
4. 预算仍默认仅记录，身份/角色、禁止、人工暂停、撤销、原请求及未知效果防重复保持有效（1.2、1.3）。
5. 旧精确授权和已提交请求继续有真实可解释的兼容关系，不能绕过新授权展示的累计上限；升级/迁移不丢失历史（1.2、1.3、1.5）。
6. 页面显示清晰的动作、范围、剩余次数和到期状态，撤销不抹去历史执行记录（1.4、1.5）。

## Verification

- Compile-only check and static review by default. Optional tests use `$fd-test`
  when requested and do not gate FD acceptance.
- 原草案阶段仅静态读取；本次范围决定已写入，独立设计审阅及约束纳入已完成，平台、私人适配与分组表单源码已实施，8628bda 离线编译已通过，独立实现静态审阅 r1 通过。使用 C:/green/bin/setup-jdk25.ps1 进行离线 Maven compile，并针对受影响 JS/Python 做语法编译；不下载依赖、不跑测试、数据库、服务、浏览器、模型或发布。
- 增量迁移计划新建 authority_action_permission、authority_permission_child、authority_permission_preparation、authority_permission_use：人类创建以 owner+creation_request 幂等，当前期按 owner+worker+action 唯一；根候选以 owner+worker+request 唯一，子 Grant ID 唯一，消费以 permission+request 唯一。原表/历史迁移/已受理回执不修改，本次不运行迁移。
- 独立设计双证据：../reviews/FD-033-design-review-20261024-5d1a7275.md 与同名 JSON，原结论 changes-needed，D1–D5 均已纳入本设计，未执行运行验证。原范围反馈 pair 状态 resolved。
- 独立实现双证据：../reviews/FD-033-implementation-review-r1-20261024-7f8ca4.md 与同名 JSON；Reviewer Session fd033-implementation-review-20261024-r1-7f8ca4，真实领取 FD-033-000004-implementation-ready。审阅 8ce1590..8628bda 实际源码、原调用链与证据，未发现阻塞项，结论 verification-passed；Reviewer 未重跑编译或任何运行验证。其它私人秘书/模型/启动脚本及 configurations/fragments/questions 模板差异排除。

## Sources

- Issue: none
- 用户要求（2026-10-24）：动作先按功能分组；授权以有效期和累计执行次数为主；指定事务/任务为可选项。
- 来源事件：FD-033-000002-design-requested；Planner Session: secretary-desktop-20261024-a6529740。
- src/main/java/me/asu/aidev/authority/ActionScope.java、AuthorityGrantService.java、AuthorityActions.java、ActionAdmissionService.java、AuthorityController.java、AuthorityRepository.java。
- src/main/resources/templates/ui/authority.html、static/ui/authority.js；workers/personal-secretary/runtime.py、platform_client.py。
- FD-032：执行预算默认仅记录，动作授权上限应与执行资源预算区分。

- 实现来源：8ce1590；平台存储/范围 d92e7ca、具体受理 3b009f4、私人适配精确补丁 6bf15ee；当前 Worker 沿用真实事件 FD-033-000003-design-ready。

- Worker 证据：../reports/FD-033-implementation-r1.md 与同名 JSON，覆盖 8ce1590..8628bda；git diff --check 无差异错误（一次 PowerShell 花括号解析错误在执行前修正），mvn -o -Dmaven.test.skip=true compile 通过（297 个生产源，2026-10-24T12:25:33+09:00），authority.js/action-permissions.js 的 node --check 与 Python 内存 compile 通过。既有并行 Python 文件只编译已提交 HEAD；未运行测试、模板/浏览器、数据库迁移、服务、网络/下载、模型、打包或重启。

**Completed:** 2026-10-24
