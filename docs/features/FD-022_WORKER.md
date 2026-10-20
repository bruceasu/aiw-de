# FD-022: 有限授权的代理决策 Worker：可编辑授权、人工启停、授权查看与模板复用

**Status:** Open
**Revision:** 3
**Priority:** Medium
**Evidence policy:** Dual

## Problem

ISSUE-00006 已由用户在 2026-10-21 明确批准并要求晋级后 auto 实现。秘书当前仅支持人工确认后的定时安排和只读协调；V4 仅提供单轮、一小时续修授权，不能承担 A01–A17 的通用代理决策。需要在人工可控、可撤权的边界内处理真实人工卡点。

## Options and decision

不采用仅在提示词中描述权限、复用浏览器身份或把普通 Worker capability 当作认证的方案。拟新增独立的服务端代理授权模块：人工管理授权与凭证；专用代理通过独立认证入口读取待办并提交候选；服务端通过固定动作适配器执行，禁止任意 shell/API。原有秘书讨论和协调保持其边界，代理权限不由聊天自动开启。

已完成一次独立设计评审及针对实质修订的有限复核。首轮 changes-needed；修订后 ready（仅设计）。依据：reviews/FD-022-design-review-r2.md。实现仍须证明各项设计实际落地，不代表运行时通过。

## Solution

1. 专用代理凭证由管理员配置并绑定稳定 Worker ID；服务端只保存摘要。专用入口不信任客户端 actor、角色、capability 或所代表授权人。普通 Worker 的注册不授予代理身份。执行、评审参与记录用于批准独立性检查，不能因后续获得代理资格消除历史参与。
2. 新表保存授权版本、原始自然语言、结构化规则、有效期、允许/禁止、明确替代关系和启用快照。缺少作用对象、条件、额度、期限或动作字段的规则仅可保存草稿。启用与续期必须人工确认明确版本。代理总体开关默认关闭，与进程在线、规则有效性分离；技术重启不得恢复代办权限。
3. 候选绑定待办版本、精确目标、授权版本和参数摘要。提交时在同一数据库事务重新校验当前人工归属、开关、凭证、规则、业务条件及额度；相同幂等键只能绑定同一请求。授权修改/撤销、人工接管、到期使旧候选失效。禁止规则优先；只允许显式替代已知禁止版本，时间以服务端为准。
4. 步骤额度键为 Plan ID＋步骤索引＋动作类别；非步骤为服务端验证的目标稳定身份＋动作类别。完整执行、技术重试、仅重评分别记账；复制授权或换 Worker 不重置额度。预留执行轮次在任务创建时消耗；技术中断不返还已开始执行的轮次，独立重试另扣对应额度，避免免费循环。人工补充采用增加量审计。
5. 所有执行通过既有 application services 保留业务保护。A01 使用完整执行及独立评审，不以 resumeReview 代替；A02 仅选择获授权的现有 profile/model/effort 并限定执行或评审角色；A03 retry；A04 resumeReview；A05 confirm；A06 jump；A07 stop；A08 withdraw；A09 complete；A10 cancel；A11 工程 Gate 的 approve/reject；A12_CREATE 创建发布计划，A12_EXECUTE 对已绑定输出文件、受评审源和目标路径的发布计划单独启用执行；A13 fleet scheduling；A14 drain；A15 offline；A16 archive；A17 schedule/undo deletion。
6. 人工入口需同步使相关候选失效，不能只依赖 Plan 乐观版本：归档、删除、Fleet 与 Worker 动作也有稳定目标版本/快照。人工接管待办持久化；有权限的人类明确交回才允许生成新候选。无法判断保留待办、证据及建议，不自动拒绝或超时通过。
7. 自然语言仅用于形成结构化草稿，不能在执行时解释为新权限。授权中心复用控制台样式，显示当前规则、开关、期限、已用/剩余额度、来源模板、审计和人工待办；Worker 详情及 Plan 提供相关入口。模板保存规则及来源版本，不复制凭证、启用状态、消耗和决策日志；目标重新绑定、期限及明确确认必需。
8. 增量迁移不修改 V4/V5。新策略仅可在目标无旧 PENDING 授权且无其已创建但未完成的执行/评审时启用，否则拒绝纳入并展示原待办，不取消或扩展旧授权。纳入时导入该步骤已有续修及重试/重评消耗基线；纳入之后旧 authorize/consume 不再拥有独立代理入口，必须经过同一新策略校验；人类仍可通过有审计的人工动作处理。历史记录不删除，旧来源答复仍不构成权限。

### 设计评审后必须闭合的工程细节

- D1 身份封闭：专用凭证绑定的 Worker ID 被普通 register/claim/start 等协议保留；同一凭证须在该 ID 的普通协议写入上认证，不能只保护新候选 API。凭证更新关闭开关并使旧候选失效，不自动启用。旧参与记录仍用于独立性校验。
- D2 共享写入顺序：需要确定精确的事务入口与锁序。当前普通 claim 使用 fleet/run→worker→task，秘书路径使用 secretary→plan/task；直接在这些路径内部追加全局锁可能引入倒序。必须在实现前列出所有需要新锁的入口与获取位置，不能用“同事务”代替互斥证明。
- D3 managed Plan 接入：代理不能直接调用 SecretaryPlanGate.advance 绕过秘书当前快照。需增加受校验的代理协调入口，同步 Work 的版本、generation 与 admitted task，并保留独立 coordinator 确认。人工取消与未确认安排仍不得被代理恢复。
- D4 发布身份：服务端从 task_output_publication 验证发布计划、源文件、源哈希、PASS 评审与目标路径。A05 遇到发布计划时必须同时满足 A12_EXECUTE，不允许用普通 confirm 绕过独立发布权限。
- D5 模型执行：A02 写入按任务/角色和授权版本冻结的模型约束；claim guidance 仅返回允许配置，start 强制校验实际 selection，不能靠 aiPreferences 或 Worker 自选回退保证权限。
- D6 动作粒度：A11 approve/reject、A12 create/execute、A17 schedule/undo 分成独立子动作及账本键；禁止条件按所有适用授权检查，不能只读取提交者选择的一份 grant。
- D7 验收追踪：1.1 交付明确的 30 条验收例→Work Item→证据映射，以及每个动作的目标身份、参数、版本、业务 Gate、人工失效入口表后才可 design-ready。

### 具体接入方案（供有限复核）

候选使用既有 EXTERNAL_WORK 任务及新增 `decision-delegate` 只读 profile，任务绑定指定的已认证 Worker。Worker 只返回 PROCEED 或 NEEDS_HUMAN 及依据，不执行业务工具。独立的调度事务读取已完成的候选任务，再校验并执行固定动作；完成上报事务本身不执行代理动作，避免 task→代理锁的反向锁序。结构化授权草稿可由只读秘书讨论生成 JSON，界面解析后仅保存草稿，用户须编辑并明确确认才能启用；自然语言不是运行时权限来源。

所有代理事务先获取新增 delegation_lock 单例行，再调用秘书或业务服务。人工变更在 application service 的入口、任何既有业务锁之前获取同一行并推进目标 generation：WorkflowPlanService 的 confirm/retry/resume/repair/jump/stop/complete/withdraw，WorkflowCancellationService.cancel，ArchiveService 三个变更，ApprovalService.decide，FleetAdmissionBudgetService.updateMaxParallelism，WorkerService 的 register/drain/offline，SecretaryService 的 edit/confirm/cancel。代理内部使用不可从 HTTP 设置的 server-local scope，防止自身动作被误判为人工接管。普通心跳与自动完成不获取新锁；其业务版本与任务行锁由动作适配器在提交前冻结，并使用原服务的版本/CAS；它们不调用新的代理动作处理器。TaskExecutionService 的原完成路径仅记录候选真实报告，新的 scheduler 之后另开事务处理。

入口锁规则有两处显式 server-local 完成上下文例外：SecretaryCoordinator.completed 内的 START→plans.confirm，以及 V4 consume→plans.continueAuthorizedRepair。它们在原完成事务已取得 task/secretary/plan 锁之后运行，必须由各自已校验的服务内部包裹 `legacyCompletion` scope，使边界钩子不再获取 delegation_lock 或标记人工接管；不得靠客户端 actor 名称识别此例外。其他调用仍在入口先取新锁。例外只允许既有固定 continuation，不授予新通用代理权限。目标纳入排除旧 pending/在途修复和秘书在途协调请求，并在原 secretary/task/plan 锁下复核；新代理候选完成统一延后处理。这样不存在完成路径持 task 锁再等 delegation_lock 的反向边。

V4 authorize 在其原 secretary/task 锁之前取得 delegation_lock；已纳入新策略的目标拒绝创建新的旧式代理授权，人工无代理的续修仍记录共享账本。V4 consume 不获取新锁：新目标纳入先检查旧 PENDING 和该授权产生的未完成执行/评审，并在锁住目标任务集合后复核；旧 authorize 同时受新锁保护，因此纳入与旧授权创建互斥。旧 PENDING 消费期间不能被纳入，消费事务创建任务与状态改变原子提交，纳入随后会看到未结算任务并拒绝。不会在旧完成的 task 锁内再获取 delegation_lock。

托管 Plan 的代理 admission 由 SecretaryCoordinator 新增唯一受控入口承担。入口在 delegation_lock→secretary_lock 顺序下确认 Work 未取消/STOP_REQUESTED、当前人工 authority 有效、快照匹配且无旧协调请求在途；通过 SecretaryRepository.participant 将该专用代理绑定为 COORDINATOR，拒绝历史 SPECIALIST。同一候选的独立 PROCEED 作为该次有限动作的协调结果，执行前验证完整授权，不复用旧协调的 PROCEED。成功后更新 Work 的 Plan version/hash、generation、当前 ACTIVE 状态和该次 admitted task；下一次专业完成仍走原 coordinator。普通 chat 及 coordinator 无法调用这一代理入口。

专用凭证存储于独立表、仅保存 SHA-256，人工 enrollment 绑定既有 Worker ID 并保留全部历史身份记录；不允许存在活动租约时提升。对该 ID 的普通协议由 controller 入口校验专用 header（包括 URL Worker ID、body Worker ID、输出注册/读取），凭证不交给模型。原共享 bearer 仍按原规则另行校验。执行 Worker 进程重注册会关闭代理开关；开关还绑定本次服务启动 nonce，重启不自动恢复。secret 更新或禁用推进代理 generation 并使旧候选失效。非代理 ID 保留旧协议。

新策略为有类型的固定动作规则，每份明确 action、目标、可用状态/触发轮次、固定或白名单参数、effect、expiresAt、累计限额。所有适用规则共同判定，DENY 优先；显式 supersedes 绑定被替代版本。模板仅保存规则草稿。目标纳入后使用独立累计账本，替换规则不改账本；导入旧消耗基线并以人类明确增加量补充。候选保存 generation/授权版本/目标快照/参数摘要。执行、扣减、幂等结果与审计同事务；业务异常由事务外捕获，再开新事务保留 HUMAN 待办与原因，不能让失败证据随业务回滚丢失。

A02 的约束保存为精确任务/执行或评审角色对应的 model/effort/profile/catalog version；新 policy 在 claim guidance 筛选、TaskExecutionService.start 验证实际 AiSelection。禁止偏好回退扩大白名单；无法匹配配置或缺少可核验预算时转人工。A01 每次只准入一轮，但同一授权可持续处理多次直到用户配置总上限，执行创建即预留消耗，PASS/REPAIR_REQUIRED 评审都结算为一轮，失败执行不释放已开始的预留，技术重试和仅重评各用自己的账本。还未开始且被平台明确取消的预留以补偿事件释放，保留历史，不清零。

### 动作与稳定目标

| 动作 | 服务端稳定目标/版本 | 原服务及必要约束 | Work Item |
| --- | --- | --- | --- |
| A01 | Plan ID＋step index；Plan/task version、源评审 | continueAuthorizedRepair，精确修复范围、独立评审，单次一轮 | 2.1 |
| A02 | 同上＋角色；catalog version | 冻结模型约束、claim guidance/start 校验，非偏好 | 2.2 |
| A03 | 同一步；failed task version | retryFailedTask，失败种类/副作用确认、独立技术重试额度 | 2.3 |
| A04 | 同一步；原产物/评审 version | resumeReview，产物不重新执行、独立重评额度 | 2.4 |
| A05 | Plan ID；spec hash/version | confirm，发布关联额外要求 A12_EXECUTE | 3.1 |
| A06 | Plan ID；version、target step | jump，保留原跳步证据，不伪造 PASS | 3.2 |
| A07 | Plan ID；version/control generation | stop，原租约停止规则 | 3.3 |
| A08 | Plan ID；version | withdraw，原剩余委派处理 | 3.4 |
| A09 | Plan ID；version/outcome | complete，人工结果与独立评审分开 | 3.5 |
| A10 | Plan ID；version/任务集合快照 | cancel，保留原停工与本地回退条件；不可回滚副作用如实记录 | 3.6 |
| A11_APPROVE/REJECT | 来源任务＋result revision；approval ID/status | decide，PASSED Gate、全部参与历史独立性、授权人当前 APPROVER/ADMIN | 3.7 |
| A12_CREATE/EXECUTE | 可信 repository/workspace 身份＋规范目标路径；file ID/hash、attempt、PASS review、关联 Plan version | publication / confirm；CREATE、EXECUTE 分账，发布重试也需 EXECUTE，不能以新 Plan ID 重置 | 3.8 |
| A13 | GLOBAL Fleet；当前 maxParallelism/配置版本 | updateMaxParallelism；全局作用范围、区间及次数 | 4.1 |
| A14/A15 | 既有 Worker ID；状态/active leases/目标 generation | drain/offline；原安全条件，不启停代理 | 4.2 |
| A16 | Plan ID；结束状态/归档 generation | archive；原 archivable Gate | 4.3 |
| A17_SCHEDULE/UNDO | Plan ID；归档/deleteAfter/generation | scheduleDeletion/undoDeletion；授权人当前 ADMIN、原延迟窗口，分别分账 | 4.4 |

每个动作执行前锁定表中对应业务行及所需任务集合，复核 generation/快照；原服务抛错或 CAS 失败均不标记成功。Plan 级新增限额不是强制产品默认，用户仍可配置，已确认的累计键上限始终必填。

## Scope

包括 ISSUE-00006 第 1 节已确认规则、A01–A17 全部动作、自然语言与结构化授权编辑、人工启停/接管/交回、审计查看及系统内模板复用。不得以动作目录展示代替实际接入。排除跨系统模板分享、实际环境发布、push、部署、自动检测用户离线以及自动启用。业务数值由授权使用者配置。

## Work items

各项预期规模为小、难度中；未解决的接口问题仍阻止该项就绪，不因拆小而豁免。证据均为对应静态调用/状态/异常路径与实际编译结果，不能标作运行时验收通过。

- [x] 1.1 完成工程决定、验收映射及有界独立设计复核；产出稳定规则。依赖：设计发现全部闭合。
- [ ] 1.2 增量持久化、独立代理凭证与普通协议身份保护；证据：所有 Worker-ID 写入/输出入口覆盖。依赖 1.1。
- [ ] 1.3 默认关闭、启动 nonce、人工启停/凭证更新及旧候选失效；证据：重注册/重启不自动恢复。依赖 1.2。
- [ ] 1.4 有类型的版本化授权、禁止/显式替代、到期/撤销和当前人工权限；证据：所有适用规则一起校验。依赖 1.3。
- [ ] 1.5 目标纳入、旧 V4 pending/在途互斥及历史消耗基线；证据：旧路径不可绕过纳入边界。依赖 1.4。
- [ ] 1.6 同目标累计账本、预留结算/释放及人工增加量补充；证据：换授权不改已用量。依赖 1.5。
- [ ] 1.7 版本化待办、幂等结果及失败独立事务审计；证据：同一键不重复执行、异常保留 HUMAN。依赖 1.6。
- [ ] 1.8 人工入口 generation/锁、接管与明确交回；证据：全部受影响服务入口及锁序。依赖 1.7。
- [ ] 1.9 托管 Plan 的有限代理协调 admission 与快照更新；证据：取消/失效授权不可恢复，独立 coordinator 后续仍有效。依赖 1.8。
- [ ] 2.1 A01 完整续修轮次接入；证据：执行与独立评审、预留及轮次上限。依赖 1.9。
- [ ] 2.2 A02 精确角色模型约束与 guidance/start 强制检查；证据：无白名单配置转人工，不回退扩权。依赖 2.1。
- [ ] 2.3 A03 失败重试接入；证据：失败类别、副作用确认及独立技术重试账本。依赖 2.2。
- [ ] 2.4 A04 仅重评接入；证据：绑定旧产物且不新建执行轮次。依赖 2.3。
- [ ] 3.1 A05 计划确认，发布关联要求额外 EXECUTE；证据：固定 spec/version。依赖 2.4。
- [ ] 3.2 A06 跳步接入；证据：原业务条件及不伪造 PASS。依赖 3.1。
- [ ] 3.3 A07 停止接入；证据：原租约停止流程。依赖 3.2。
- [ ] 3.4 A08 收回委派接入；证据：原收回条件。依赖 3.3。
- [ ] 3.5 A09 人工完成接入；证据：原成果约束、明确代理身份。依赖 3.4。
- [ ] 3.6 A10 取消接入；证据：原停工与本地回退检查、失败副作用如实记录。依赖 3.5。
- [ ] 3.7 A11 approve/reject 接入；证据：当前 APPROVER/ADMIN、PASSED Gate 及历史参与独立性。依赖 3.6。
- [ ] 3.8 A12 CREATE/EXECUTE 接入；证据：可信发布目标、源哈希/PASS review、分别授权/记账及所有准入路径防绕过。依赖 3.7。
- [ ] 4.1 A13 Fleet 调整接入；证据：固定 GLOBAL 目标、区间/次数及旧人工入口。依赖 3.8。
- [ ] 4.2 A14/A15 Worker Drain/Offline 接入；证据：稳定 ID、租约条件及不启用代理。依赖 4.1。
- [ ] 4.3 A16 结束 Plan 归档接入；证据：原 archivable Gate。依赖 4.2。
- [ ] 4.4 A17 schedule/undo 接入；证据：当前 ADMIN、原延迟窗口及独立账本。依赖 4.3。
- [ ] 5.1 真实 Worker `decision-delegate` profile、凭证传输、定向任务与服务器处理循环；证据：只读候选与业务执行分事务。依赖 4.4。
- [ ] 5.2 自然语言→结构化草稿及显式人工确认；证据：原文保留、模糊字段不能启用。依赖 5.1。
- [ ] 5.3 授权中心、Worker/Plan 入口、人工待办和消耗/审计查看；证据：复用控制台组件，无秘密回显。依赖 5.2。
- [ ] 5.4 规则模板另存/复制/重新绑定；证据：独立新授权、不复制凭证/启用/消耗。依赖 5.3。
- [ ] 5.5 更新近旁使用/兼容文档，完成 compile-only 和静态证据；依赖 5.4。
- [ ] 5.6 独立实现评审后本地交付/清理/Complete 归档；依赖 5.5 及当前 passed receipt。

## Acceptance

以 ISSUE-00006 第 5 节全部 30 个验收例为依据，逐项映射以上 Work Items。特别要求：伪造 ID/capability 不能代理；同一成果参与者不能批准；到期/撤销/下线/人工接管后旧候选失败；禁止优先及明确替代可追溯；同一目标额度不因替换授权而重置；三种计数分离；A12 创建与执行分别授权；失败/无法判断保留人工待办；模板仅复制规则，不自动启用。全部动作实际实现前不能标记 full-FD ready。

| Issue 验收例 | 对应 Work Items 与静态证据 |
| --- | --- |
| 1 | 1.4、1.7、2.1–4.4：匹配作用域、动作后审计 |
| 2 | 1.4、1.7：目标不匹配/缺条件转人工 |
| 3 | 1.3、1.4、1.7：提交时重新校验关闭/撤权/到期 |
| 4 | 1.2、1.3：注册关闭而非启用 |
| 5 | 1.7、1.8：目标 generation/版本与人工入口 |
| 6 | 1.7：唯一键及事务消费 |
| 7 | 1.7、5.1：候选无法判断持久化 HUMAN |
| 8 | 1.7、5.3：原授权人/真实代理及人工维护审计 |
| 9 | 1.4–1.6、2.2：配置版本/独立历史账本 |
| 10 | 2.1、2.2：追加轮次与升级约束原子准入 |
| 11 | 1.4、5.4：动作编辑/草稿不启用 |
| 12 | 5.3：状态、不可用原因、每次消耗 |
| 13 | 1.6、5.4：重新绑定及历史累计键 |
| 14 | 1.4、5.4：模板/授权/决策版本隔离 |
| 15 | 5.2：自然语言仅生成草稿，必填字段 |
| 16 | 2.1–4.4：动作表全部适配及缺授权拒绝 |
| 17 | 1.2、1.4：认证与动作授权分别校验 |
| 18 | 3.7：全部历史参与记录、相同模型可用 |
| 19 | 1.7、1.8：接管状态不因重新上线改变 |
| 20 | 1.4：禁止优先、撤销版本失效 |
| 21 | 1.4：显式替代及服务端生效时间 |
| 22 | 1.6、2.1、2.3、2.4：三类账本和结算 |
| 23 | 1.5、1.6：既有消耗基线、增加量而非清零 |
| 24 | 1.4、1.6：必填到期时间及续期不重置 |
| 25 | 1.3：默认关闭、nonce、重注册与心跳 |
| 26 | 1.7：业务异常与不确定均保留原证据 HUMAN |
| 27 | 3.1、3.8：分别授权及发布关联防绕过 |
| 28 | 1.8、5.3：权限检查、持久待办不超时通过 |
| 29 | 1.8：显式交回重建候选，重新检查所有条件 |
| 30 | 1.6、3.8、4.1–4.4：非步骤稳定对象＋子动作累计键 |

## Verification

- Compile-only check and static review by default. Optional tests use `$fd-test`
  when requested and do not gate FD acceptance.
- 本次未请求测试支持。只运行一次适用的离线编译，不执行测试、最终构建、网络、依赖下载或发布。设计评审不代表运行时通过。

## TODO

- 设计就绪，1.2 起尚未实现。实现须核对普通协议身份保护、秘书 admission、旧 V4 纳入互斥和两处 legacyCompletion 的实际封闭边界。
- 设计证据：reviews/FD-022-design-review.md（changes-needed）、reviews/FD-022-design-review-r2.md（ready）。稳定规格：openspec/specs/delegated-decisions/spec.md。

## Sources

- Issue: ISSUE-00006
