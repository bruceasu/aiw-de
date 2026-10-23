# FD-026: 从目标获得可审阅、可按结果推进的 AI 计划

**Status:** Complete
**Revision:** 7
**Priority:** Medium
**Evidence policy:** Dual

## Problem

用户只给目标时，现有推荐服务能提出步骤，但 `WorkflowPlanSpec` 要求最多 32 项、按依赖排序、提前绑定 skillRef；执行和人工跳转围绕序列位置。它不能直接表达批准的 task、if/else、goto、ask、局部结束与动态展开合同。目标是保留平台的正式状态与 Lease 基础，让条件、并行、回跳和人工控制有明确且可恢复的语义。

事实来源为当前 `WorkflowPlanSpec`、`WorkflowPlanService`、`WorkflowRecommendationService` 及批准的 ISSUE-00010；不是新能力的运行证明。

## Options and decision

| 选项 | 取舍 |
| --- | --- |
| 扩展现有序列索引、用重试模拟分支 | 难以区分业务回跳与技术 Attempt，未选分支容易永久阻塞 |
| 版本化声明式流程图，复用任务执行基础 | 选用；显式保存节点、选路、轮次和输入，但须新增持久化的激活记录 |
| 引入独立工作流服务/自由执行脚本 | 首期增加基础设施或动态代码权限，超出最小边界 |

采用有版本的封闭 JSON 数据模型，不执行表达式脚本或模型提供的代码。推荐服务继续通过已有 Worker 任务调用规划能力；AI 只返回候选草稿，平台校验和版本化后才可生效。不强制每个专业步骤再经过固定评审角色。

## Solution

### 候选流程与执行模型

计划记录 `schemaVersion`、稳定 nodeKey、目标/范围/验收、输入引用、节点和预算引用。节点 kind 为 task、if、goto、ask、end。task 可带 dependsOn；其配置意图与最终专业配置分开，进入可领取前由 FD-027 准备。现有人工草稿入口映射到相同的计划变更服务，不另建 AI 私有写入口。

if 只接受固定版本正式结果上的封闭谓词，如 `verdict == PASS` 或已声明的结构化工具字段；操作符与字段类型白名单由平台验证。缺失、未知、不合法和版本不匹配产生明确阻塞，不隐式转为成功。专业判断由正式 verdict/证据输入提供，不读取思维链。

goto 指向同一计划版本内存在的节点。向后边必须声明业务 loopId，按计划+loopId 共享回跳账本；向前跳转不消耗业务回跳。草稿校验拒绝目标不存在、无条件空转、不能确定汇合范围或越过未满足必要依赖的跳转，并返回节点级错误。不得用固定最大步骤数暗中代替用户的任务规模授权。

每次节点激活拥有独立 executionId、业务轮次、来源转移及固定计划版本。WorkTask 与 executionId 关联，技术失败重试仍沿用该业务任务的 Attempt 机制。对同一个来源完成/回答/控制决定，转移 ID 和任务创建使用数据库唯一键，在同一事务中记录选择及后继，不靠内存去重。

### 并行、汇合与局部结束

无依赖且准备/授权满足的任务可同时被调度。分支选择后持久化本次汇合的必要成员及“未选”理由，成员关联同一激活范围；汇合只等待选中的必要成员成功，不把已失败或取消的必要成员改成未选。跨轮次或跨并行范围的成员不能混用。

汇合范围及 goto 边界采用可验证的显式激活范围元数据；草稿期无法无歧义确定的图保留可编辑草稿并报告错误，不进入执行。该工程选择必须通过独立设计反馈，不能靠观察到任务完成顺序猜测范围。

必要任务失败只阻断依赖链；不相干任务继续。存在已定义失败路径时产生明确失败结果并按路径处理，否则等待人工。end 仅关闭当前路径；计划正常完成还要求必要成果满足，且全部已启动任务终态或已确认取消。

### 回跳、控制及变更

初始执行另计，默认最多回跳 3 次、最多 4 轮。同一计划同一 loopId 的计数不随 Worker、Attempt、重启或新授权清零。到限创建一次具体追加申请；新增额度来自 FD-029 的正式决定，数量和范围必须明确。秘书只有有效委托及适用 policy 时可代处理；本设计不签发委托。

暂停阻止新领取、新节点展开和后续推进，已有授权任务可收尾；完成、回答、心跳等事实仍保存。恢复从未消费的持久事件继续。单独取消请求进入等待确认；超时或失联保留待确认/UNKNOWN。终止阻止所有后续，汇总在途确认，不自动回滚外部操作。用户界面由 FD-028 拥有，执行门禁与本 FD 的推进入口共用。

人工/AI 变更均需 expectedVersion、来源、原因和请求 ID，只能修改未执行部分。对在途工作需先取消并确认、再建立替代关联；旧输入及历史不可覆盖。动态展开仅提交剩余图的候选补丁，校验、预算及授权后生效。

### 模块及持久化边界

- workflow/domain：版本化图、谓词、激活范围及转移不变量；不调用模型或写 SQL。
- workflow/application：推荐草稿、变更校验、节点激活、分支/回跳/结束与控制门禁；通过 FD-027 准备合同和 FD-029 预算/成果合同执行。
- workflow/port、jdbc：计划版本、execution、转移、选路与汇合成员持久化；事务更新和唯一键处理重放/并发。
- workflow/protocol、现有控制台：同一 /api/r1 下的版本化草稿/变更/状态操作，保留现有人类会话、CSRF 和所有权边界。

新运行 DB 不迁移旧计划数据。只新增后续 schema 迁移，不重写历史 Flyway 版本。现有无新图要求的任务/Worker 协议继续保留；新配置图不派给未广告相应版本的 Worker。具体 DTO 和 SQL 在就绪设计中固定，禁止借新库决定删除用户文件。

### D026-01：确定的作用域与汇合算法

图合同版本为 `aiw.workflow.graph.v1`。`regions[]` 定义 regionKey、parentRegion、nodeKeys、entryKeys、joinKey 及可选 loopId/loopHeader；ROOT 为唯一根，region 树无环，每节点只属于一个 region。条件的两个分支各属于独立子范围，在声明的汇合点前不得任意交叠；不能结构化的图在草稿期明确拒绝。并行由一个范围的多个 entryKeys 或 task 的多个后继表达，不增加隐式职业任务。

执行标识为 planVersion+regionKey+activationId+iteration。进入范围时按已固定的祖先输入建立激活；if 决定在同一事务中固定选中的子范围和结果依据，未选范围记录 NOT_SELECTED，不创建任务。范围内剔除回跳边后必须为 DAG；按依赖拓扑传播选择，只有全部潜在入口已决且均未选时才可标为 NOT_SELECTED。

汇合成员来自该激活中选中路径的必要输出槽，逐个绑定成员 executionId；必要成员 FAILED/CANCELLED 保持该事实并阻塞，不能改成未选。显式恢复路径可以产生带 replacementFor 的替代成果，满足原输出槽后才可继续，历史失败保留。依赖只读本激活本轮成员，或显式导入的固定祖先结果；不从“最新任务”猜输入。

前向 goto 只到当前范围的节点，目标必要输入必须满足或有正式未选依据；被跳过范围没有在途执行才可应用。向后 goto 的目标必须是该业务循环声明的 loopHeader；旧轮已启动的本循环工作全部终态/确认取消后，原子消费一次回跳额度并创建 iteration+1。外围独立分支不取消、不换轮次。进入兄弟/子范围、跳过必要输入或穿过在途并行范围的 goto 拒绝并返回节点级错误；需改图时使用统一变更/取消替代入口。

例：ROOT 中代码循环与文档分支并行，代码验证 FAIL 经 loopId=repair 回到 code，文档仍用原激活；最终汇合等待代码循环的正式出口成果及文档成果。if 的另一条未选路径不等待；文档失败不能因代码修复而变为未选。第三次回跳后再请求回跳只产生一次额度申请，不先创建第五轮。

### D026-02：事务和控制顺序

同一目标采用 FD-029 的目标准入锁，随后按计划/execution、worker、排序后的 task、Lease/账本顺序访问，保留既有领取的 run/target→worker→task 顺序；不持数据库事务执行模型、Git 或外部命令。推进事务检查当前控制 generation/计划版本/execution 版本、消费唯一来源、固定输入、产生后继意图和应用回执，任何一步失败整笔回滚。跨进程消息与渠道投递使用持久 outbox；同库正式回答和图推进同事务提交。

暂停与 claim 的线性化点均为目标准入锁内的正式变更/领取提交。先提交的有效 claim 属于在途，可在既有授权内开始及收尾；后提交的 claim 被暂停门禁拒绝。pause generation 与授权撤销世代分开，暂停不撤销已领取工作，撤销仍阻止新的未授权工具操作。预算到限采用用户确认的相同准入语义，不取消已经在途的执行。

完成/回答先保存真实事实；只有 RUNNING 且未取消/替代的当前 execution 能消费来源并推进。暂停期间不写 APPLIED 或新后继。恢复只消费尚未应用的来源；终止后的迟到完成保留结果但不推进。取消确认必须匹配 task/attempt/Lease；旧 Lease 或旧 execution 的确认不能停止新工作。APPLIED 回执由选定消费者在产生唯一继续/转移结果的同一事务写入，不由两个模块分别宣布成功。

## Scope

拥有 WF01～06、AC01/02/03/08；不重复实现 Factory、渠道身份/问题存储、底层工具授权或成果账本。正式问题由 FD-028 提供，配置由 FD-027 提供，许可/预算和输入固定由 FD-029 提供。

### 就绪决策

- G1 已解决（2026-10-23）：用户确认 FD-029 的计量建议和政策 A；所需上限未配置先等待。到限阻止新派发及推进，在途按已有授权收尾，限额是准入阈值，不保证最终消耗绝不超出。
- 工程前置：固定图 DTO、作用域/汇合算法及跨并行 goto 的合法性规则，完成独立设计反馈；这些不是需要用户代做的编码选择。
- 接合合同待 FD-027 的配置准备、FD-028 的问题恢复及 FD-029 的操作许可候选设计协调。本文是候选，尚未设计就绪。

独立设计反馈：`reviews/FD-026-design-review-20261022-08211700.md` 的 D026-01/02 已补入上述算法与顺序，待有界独立复查；不改写原 changes-needed 历史结论。

## Work items

### 实施接口细化：实际接合与范围快照（2026-10-23）

已交付依赖为 FD-029/027/028，当前父基线 `f7cd526`。本 FD 只拥有图计划、激活、选择、来源消费及相应 UI，不再实现权限、配置、问答或 Attempt 账本。以下具体接口和工程限制需要一次有界独立设计核对；不重开已确认政策。

1. **封闭数据与入口。** `GraphSpec(protocol, goal, scope, acceptance, regions, nodes)`；每个 Node 有 key、region、kind、dependsOn、next 和唯一匹配 kind 的 payload。task 固定 repository/base 或 baseFrom、runtime、approved AI profile、defaultRole/configRef/candidate 及 instruction；if 固定正式来源节点、字段/操作符/字面量和两个子 region；goto 固定目标及可选 loopId；ask 固定问题/必要上下文/选项；end 固定路径结论。只允许正式 verdict、resultRevision、changedFileCount、success 等已定义字段，拒绝脚本/未知属性及来源版本缺失。HTTP 请求有显式大小界限，不以 32 个步骤或其他隐藏节点数替代 TASKS 授权。
2. **结构与激活。** Region 树和节点唯一归属保持。ROOT.entryKeys 可以列出本范围入口或直接子范围的完整入口集合；if 则显式选择两个子范围之一，不能同时由另一个入口启动同一条件范围。子范围 joinKey 位于其直接父范围。每个范围激活固定 specVersion、parentActivation/parentIteration、导入的祖先 execution ID，以及自己的 iteration；祖先输入不查“最新任务”。条件的未选范围有 NOT_SELECTED 激活及理由，不创建 Worker 任务。普通边形成 DAG；回跳边单独验证。
3. **选择与汇合。** 范围激活先建立该轮节点槽和潜在边。每条边以来源 execution/转移 ID 持久记 TAKEN 或 SKIPPED。目标只有全部潜在来源已决且至少一条 TAKEN 时才入选；全部 SKIPPED 才为 NOT_SELECTED 并传播正式理由。条件子范围、并行直接子范围和父范围汇合成员均绑定实际 activation/iteration/执行槽；未选成员不等待，已选失败/取消不能改成未选。子范围的输出聚合只含该有效轮真实成功的正式成果。失败依赖阻断自己的路径，独立入口继续；可声明失败出口，或由显式技术重试/替代恢复必要输出槽，保留原失败及 replacementFor。
4. **回跳及局部结束。** 前向 goto 只在自身范围，目标输入满足或有真正未选依据；跳过的片段有在途/等待工作时拒绝。回跳的 loopId 必须是当前范围或祖先范围明确声明的循环，目标为该范围的 loopHeader；不进入兄弟/子范围。回跳等该循环旧轮全部已启动工作终态/确认取消，再在同一事务消费 ledger.backjump 的唯一转移 ID 并创建新 iteration。外层独立分支不换轮；旧轮结果不充当新轮输入。初始加三次回跳后，创建一次固定额度申请，等 FD-028/A18/ledger 正式决定，不预建第五轮。end 关闭当前路径；范围/全局完成仍核对必要成果及所有已启动实际任务，UNKNOWN 保持阻塞。
5. **单一实际消费。** 图 task 用 FD-027 AgentFactory.prepareAuthorized 生成精确意图/业务任务，同事务保存 graph execution→task/准备决定映射；依赖只传真实完成 TaskInput，失败事实通过单独固定的来源上下文传递，不冒充成功输入。受控任务完成事实由既有 Task/Attempt/Guard 保存，图的纯数据库恢复器按唯一 task+Attempt/结果事实捕获、消费；不增加第二个 EXTERNAL_WORK completion handler、不直接调用 Worker。重放返回原转移/任务。暂停中可捕获事实，只有当前可推进的执行才消费；不自动重放模型、工具或 UNKNOWN。
6. **正式问题。** 注册 FD-028 SourceResolver，优先识别映射中的图任务（包括其真实 lineage 后继），验证 plan/specVersion/execution/owner。图 ask 调 openGraph，同事务消费回答、推进和写 APPLIED；临时图任务使用同一个可信 continuation 实现的窄公开端口，随后原子更新图逻辑槽及 APPLIED，禁止复制另一套续行。预算到限只从真实图/ledger 缺口创建具体追加申请，未知用量先等待人工核对；新增申请入口不签发授权。
7. **版本化修改与推荐。** 人工/AI 使用同一 expectedVersion、UUID、来源和原因接口。DRAFT 可修改全部图；运行中每个已激活范围的版本固定，只允许修改尚未激活子范围的内部图，保留其 parent/entry/join/loop 对外端口，旧版本及已有执行不覆盖。需要改变活跃范围时先取消并确认，再通过有 replacementFor 的明确替代接管；不能静默改在途输入。按需展开使用同一未激活范围补丁。此约束在 UI 和拒绝原因中明示。推荐通过受控的只读 implementation-planner 配置任务返回正式报告内 `AIW_GRAPH_V1` 候选；解析/校验错误只保留候选及诊断，不执行，缺授权/预算/Worker 时保留人工编辑和配置恢复。
8. **控制和 UI。** owner 下读取允许当前操作/审批角色；激活、修改、技术重试/替代及执行控制仍需操作角色，额度审批仍由 FD-028 要求审批角色。整图暂停复用 execution policy；取消/终止关闭新准入并等待实际在途结果。单执行取消用精确 Attempt/Lease 的停止请求及图推进门禁，保留独立分支；未知结果不重试、不回滚。控制台使用现有样式显示草稿诊断、版本、选路、成员、轮次、预算、实际任务/来源和待确认状态。

代表场景：ROOT 同时进入代码循环范围和文档任务；代码范围内 code→verify→if，失败子范围的 goto 回到祖先代码范围 loopHeader，成功子范围 end；文档保持原 ROOT 激活，最终 join 绑定代码范围真实出口及文档成果。未选分支不建任务；文档失败始终保留失败，不因代码新轮而被跳过。第三次回跳后只申请一次明确新增额度，重复完成/回答/恢复扫描不重复扣账或派发。

选择每个范围固定快照而限制运行期改动，可以使来源/汇合/预算不因重排而混用；代价是活跃范围内尚未开始的单节点也不能原位改写，需要显式取消替代，未来范围仍可展开。相比任意改写运行中 DAG 或引入独立引擎，该接口将复杂性限定在持久激活和明确端口。此为工程设计说明，尚无图实现或运行验证。

### 接口反馈定案（I026-01～03）

以下合同消除前节的省略处；由 Planner 根据实际邻接接口决定，不新增业务政策。

- **稳定出口与显式 carry（I026-01）。** 循环 activation ID 不变，iteration 单增；父汇合绑定该 activation 的稳定出口槽，回跳期间一直 WAITING。回跳事务把旧轮记为 REPEATED，触发控制节点记为 TRANSFERRED；旧失败保持失败，既不成功也不改未选。出口只在当前轮所有必要选中槽成功、已启动任务真实静止且无 UNKNOWN 后发布一次。父槽固定最终出口的 iteration、execution/Task/Attempt/结果摘要。显式 `carry[]` 将上轮指定完成节点导入下一轮指定 task 的输入或 base；转移中冻结精确 execution/Task/Attempt/commit/digest，初轮使用原 base。旧结果不满足新轮普通 dependsOn 或汇合。回跳控制节点不等待自身完成；外部独立分支及其已固定输入不变。
- **编辑 head 与激活定义（I026-02）。** plan.editVersion 与 activation.definitionVersion 分开。每个父激活/轮次为直接子范围保存版本绑定槽；首次进入时在同一准入锁/事务下固定子定义版本与对外接口摘要。未激活子树补丁必须保持 parent、entry、join、loop、跨边界 dependsOn/baseFrom、谓词来源及导入/出口合同，且子树无任何激活（含 NOT_SELECTED 决定）。patch 先提交则更新未绑定槽，激活先提交则拒绝 patch；后续循环使用该已固定子版本，不能临时读取最新版本。旧完成/回答验证自身冻结版本、映射和控制状态，不与全局 editVersion 简单相等。DRAFT 可整体修改；运行中只能显式指定未激活子树。
- **事实类型与恢复（I026-03）。** COMPLETED 的 FAIL/REPAIR_REQUIRED verdict 是真实正式结果；技术 FAILED 则保存 FailureFact（精确 task/Attempt、状态、错误与证据摘要），不能作为 Factory 成功 dependencies。只有 task 的显式 failureNext 消费确认失败；UNKNOWN、CANCELLED 均不等同 false，不走普通 if。失败上下文由图拥有者登记为冻结事实，再写入平台生成的 Intent.instruction，依赖仍只传成功 TaskInput；PREP/图 execution 绑定及原始 Intent 摘要是唯一来源，不接受外部自报 FailureFact。FAILED_BLOCKED 且失败未被任何后继消费时，操作角色可用 UUID、expected execution/task version 和精确 Attempt 重试同一 Task，状态 RETRY_PENDING，保留输入/配置/iteration，实际 claim 继续核算新 Attempt。失败已消费则拒绝原位重试。替代要求旧槽真实静止、无 UNKNOWN、无已消费出口及无已运行的依赖后继；创建带 replacementFor 的新 execution/Task，以 CAS 转移逻辑槽，保留旧失败和旧边决定，独立分支不重跑。取消后同样只能在已确认静止时替代。需要改变已被消费的历史则明确拒绝；不能 forceRequeue。
- **接合细节。** Factory intentId 固定为 graph execution 摘要，intentVersion 固定，准备 generation 独立单增；BLOCKED 的重试只能来自显式恢复决定，扫描不会制造新任务身份。图捕获事实与推进分开，持久消费键包含真实 task/Attempt/摘要，问题消费键包含 questionId/answerRevision/execution。SourceResolver 覆盖原任务及真实 continuation lineage，旧被替代执行拒绝，绝不回落 LEGACY。临时任务续行仅窄公开 FD-028 原 continuation，图逻辑槽与 APPLIED 仍同事务。预算新增内部 request-only 端口只验证真实 owner/policy/ledger 缺口并建立具体申请，审批仍由 A18/ledger；未知计量不申请替代核对。预算阻塞保留原 transitionId，批准后用原 ID 回跳。
- **端口与恢复。** 人类 HTTP 固定 `/api/r1/authority/workflow-graphs`，共用会话/CSRF、当前 owner 与操作/审批角色。纯数据库恢复扫描不调用外部服务，只捕获任务事实及消费尚未应用的合法转移；控制台显式恢复负责重试 BLOCKED preparation。计划、范围、节点、边决定、事实、出口、carry、来源消费、补丁/控制请求均保留持久记录。技术错误与不可计量状态显示具体原因，不能标成运行通过。

反馈证据：[接口设计反馈](reviews/FD-026-interface-design-20261023-08211700.md)（历史 changes-needed 保留）；[定向复查](reviews/FD-026-interface-recheck-20261023-08211700.md) 已确认三项 resolved、scoped ready，随后实施。5.2 的完成条件为文档、静态与编译证据及交接准备；独立审查和交付按生命周期另行记录，不把未来 Reviewer/PM 结果写成已完成。

实现位置为 `workflow/graph`，图版本/激活聚合/事件/请求/任务映射由 V15 保存；Node/Region 是封闭数据，pure-DB Runner/Recovery 复用 Factory/Guard/正式问题。`aiw.workflow.graph.v1` 在平台解释；Python Worker 只消费既有配置/许可/问题版本，不在 Worker 增加另一套图解释器。正常 claim 仍通过既有能力快照校验。控制台 `/ui/workflow-graphs` 展示版本、选路、真实任务/Attempt、候选、预算及恢复；部署约束见 [图计划说明](../../../WORKFLOW-GRAPHS.md)。

每项 small、中难度、估算不超过半天；发现高难度或外部未知时先解 Gate 并继续拆分。

| ID | 依赖 | 独立结果及完成条件 |
| --- | --- | --- |
| 1.1 | G1、工程前置 | 固定图/激活/版本 DTO、合法图示例和拒绝例，与依赖 FD 一致 |
| 1.2 | 1.1 | 新库计划版本及激活记录落库，旧记录不被覆盖 |
| 1.3 | 1.2 | 来源事件/转移唯一键及事务，重复输入返回已有结果 |
| 2.1 | 1.3、FD-027 准备合同 | task 节点准备后创建任务，失败准备不进入领取 |
| 2.2 | 1.3、FD-029 固定输入 | 封闭 if 谓词选路，未知结果明确阻塞 |
| 2.3 | 2.1、2.2 | 独立分支激活与选中成员汇合，不串轮次、不误等未选路径 |
| 2.4 | 2.3 | 失败依赖隔离、显式失败路径及局部 end/全局完成判定 |
| 3.1 | 1.3、FD-029 账本 | goto 和 loopId 计数，三次回跳后的追加申请/幂等续行 |
| 3.2 | 2.4、3.1 | 暂停/恢复门禁，完成事实与推进分开持久化 |
| 3.3 | 3.2 | 终止/取消确认及在途 UNKNOWN 处理，不假报停止 |
| 4.1 | 1.3 | expectedVersion 统一补丁入口，保护在途输入和历史 |
| 4.2 | 4.1、2.1 | 推荐服务产生新图草稿/按需展开，畸形输出只报错不执行 |
| 4.3 | 3.2、FD-028 问题合同 | ask 挂起及回答恢复，同一回答只消费一次 |
| 5.1 | 2.4、3.3、4.2、4.3 | 复用控制台样式展示草稿、选路、回跳消耗与阻塞恢复 |
| 5.2 | 5.1 | 同步相关 spec/操作文档，静态/编译证据及实现交接准备；独立审查和交付另记生命周期结果 |

- [x] 1.1 固定图及激活合同。
- [x] 1.2 持久化计划版本与激活。
- [x] 1.3 来源去重和事务转移。
- [x] 2.1 task 准备与任务创建。
- [x] 2.2 if 谓词与选路。
- [x] 2.3 并行激活与汇合。
- [x] 2.4 失败隔离与结束判定。
- [x] 3.1 goto、回跳账本与追加。
- [x] 3.2 暂停与恢复门禁。
- [x] 3.3 终止与取消确认。
- [x] 4.1 统一版本化变更。
- [x] 4.2 AI 草稿与按需展开。
- [x] 4.3 ask 与回答消费。
- [x] 5.1 控制台展示。
- [x] 5.2 文档、静态/编译证据及实现交接准备。

## Acceptance

| 验收 | 可观察条件 | 工作项 |
| --- | --- | --- |
| AC01 | 仅提交目标可得草稿，审阅/调整后按实际授权执行；模型失败保留人工编辑 | 4.1、4.2、5.1 |
| AC02 | if/else、前向 goto、向后 goto 直接表达；条件 UNKNOWN 不默认为成功 | 1.1、2.2、3.1 |
| AC03 | 必要依赖未满足不领取；未选路径不等；失败必要成员不能成功汇合 | 2.1～2.4 |
| AC08 | 三次回跳产生独立业务轮次并保留历史；技术重试不冒充业务轮次 | 1.2、3.1 |
| 共用控制 | 暂停中结果保存不推进，终止等待真实取消，重复事件不生成重复任务 | 1.3、3.2、3.3、4.3 |

反例：被选中后失败的分支不得通过“跳过”变成未选；循环重启不能重新获得三次额度；旧版本计划变更拒绝并返回当前版本。

## Verification

已核对图结构/作用域、来源绑定、失败隔离、回跳/账本、正式问题、Factory、控制及持久状态的静态调用链。`git diff --check` 与变更的 `workflow-graphs.js` 的 `node --check` 通过。离线 `mvn -o -DskipTests=true -Dmaven.test.skip=true compile`（JDK25/Maven3.9.9）于 2026-10-23 21:40:55 JST 成功编译 282 个生产源文件；较早一轮 21:38:42 后有源码修正，故末轮才覆盖交接源码。存在 Maven/JDK 依赖工具警告及 GraphPlans 泛型解码 unchecked 提示，没有编译失败。

未生成或运行测试、模型、Worker、服务、HTTP/浏览器、DB/Flyway、实际并发/重启恢复、Telegram、命令 sandbox/cgroup 或网络。静态与编译不能替代这些运行证据。上述“独立 Reviewer 尚待交接”为首轮 Worker 交接时阶段事实；设计 ready 不等于实现 verification-passed。

2026-10-23 第 1 轮独立实现审查：Reviewer `fd026-independent-review-20261023-08211700-r1-graph` 精确领取 `FD-026-000004-implementation-ready`，比较 `f7cd526..0110820`（实现 `cd194ad`），结论 **changes-requested**。R026-01～04 分别为显式失败边未决定、祖先回跳误等未选中父 join、整图终态漏规划任务、ASK 可选字段与正式入口不一致。见 [双份审查报告](reviews/FD-026-review-20261023-08211700.md) 及 [阻断反馈](reports/FD-026-review-blocker-20261023-08211700.md)。累计失败 **1/3**；仅静态检查，未重跑 Worker 编译或任何运行验证，未修改实现。真实回执由 CLI 记录。

2026-10-23 第 2 轮独立实现复核：Reviewer `fd026-independent-review-20261023-08211700-r2-graph` 领取 `FD-026-000006-implementation-ready`，静态核对 `0110820..4b23f25`（修复源码 `88a8161`），R026-01～04 全部 **resolved**，结论 **verification-passed**。见 [第 2 轮双报告](reviews/FD-026-review-20261023-08211700-r2.md)；原 blocker 双文件保留发现和修复历史并记为 resolved。累计失败仍为 **1/3**。本 Reviewer 未重跑 Worker 的 21:51:01 JST 编译或其他运行检查；上述未运行风险全部保留。真实通过回执由 CLI 记录。

TODO：PM 按真实通过回执检查本地交付/归档条件。G1 及接口设计 Gate 已解决；运行验证保持未授权、未执行，不记作通过。

## 设计就绪记录（2026-10-23）

已取得独立定向复查 [FD-026 design recheck](reviews/FD-026-design-recheck-20261023-08211700.md) 及同名 JSON；该轮原 findings 全部 resolved，建议 ready。已确认的人类政策及工程合同作为当前设计，本文此前“候选/待复查”的阶段描述以本节为准。“尚无实现通过”属于设计阶段历史，当前独立实现结论见 Verification；仍无运行认证。本 FD 当前阶段由 CLI 状态与真实交接记录确定。

## Sources

## 第一轮实现修复（R026-01～04）

已领取真实 `FD-026-000005-changes-requested`，失败累计 1/3。显式失败分流同时固定成功 SKIPPED/失败 TAKEN，原必要失败槽不变未选；替代不重写已决边。回跳静止只检查实际启动 Task 和已打开 ASK，旧轮未启动后继不再自锁。整图取消和 ROOT 正常出口改为检查同 plan 所有受控物理任务（规划及续行）、真实 Attempt/Lease/效果及未知计量；循环继续按局部范围检查。公开 ASK 与 formal openGraph 的可选字段正规化对齐，通用授权 text 校验不变。原失败审查保留；Worker 修复交接时“独立复核待交接”为历史，随后真实第 2 轮结论见 Verification。

- Issue: ISSUE-00010
- [批准的需求](../../../issues/ISSUE-00010/issue-plan.md)；[父 FD](../../FD-025/FD-025_AI_DEV_MANAGER_AI.md)。
- `src/main/java/me/asu/aidev/workflow/domain/WorkflowPlanSpec.java`、`workflow/application/WorkflowPlanService.java`、`WorkflowRecommendationService.java`；`openspec/specs/task-orchestration/spec.md`。
- 设计来源事件：`FD-026-000002-design-requested`；Planner 会话：`fd025-029-host-20261022-234846-08211700`。

**Completed:** 2026-10-23

## 本地交付与归档

真实复核事件 `FD-026-000007-verification-passed`，来源 `FD-026-000006-implementation-ready`，修复源码 `88a8161`，受审 HEAD `4b23f25`，最终工作树提交 `c901490`。PM 已按默认生命周期 squash 到 develop `bd22b48`，通过 aiw CLI 清理工作树/分支并 close Complete。原 blocker 已由独立复核据实 resolved；保留初次失败及四项修复历史，失败累计 1/3。没有运行测试、服务、DB 或外部后端认证。归档仅调整本 FD 的相对链接，不改 Reviewer 历史证据或其摘要。
