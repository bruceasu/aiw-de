# FD-028: 用户经秘书处理工作问题与人工控制

**Status:** Complete
**Revision:** 8
**Priority:** Medium
**Evidence policy:** Dual

## Problem

用户希望经个人秘书聊天、明确绑定的 Telegram 私聊及 Web Console 处理同一工作问题，不必长时间留在控制台。现有个人秘书有私库及有限授权信封，工作流问题与 Telegram 审批入口尚不能直接代表完整的同一问题/回答/继续合同。新增能力必须保持私人数据边界、身份及正式权限不被普通聊天扩大。

## Options and decision

| 选项 | 取舍 |
| --- | --- |
| 各渠道各存状态与独立推进 | 易重复消费、错关联或越权，不采用 |
| 平台正式问题 + 渠道适配 + 秘书最小投影 | 采用；一个问题/决定来源，渠道负责身份、送达和原始回答 |
| 将秘书私库与全部聊天同步至平台 | 改变已确认的私人边界，不采用 |

复用既有 WorkflowQuestion、个人秘书凭证/回执、Telegram 绑定及 Console 认证；差异通过明确扩展收敛，不恢复旧秘书 API 或共享 Worker bearer 代替个人身份。

## Solution

### 问题与继续执行

正式问题记录 questionId、所属 owner、planId/planVersion、来源 task/attempt、原问题、必要上下文、选项、回答要求、requestVersion 及状态。首先锚定现有计划/任务，图 node executionId 作为可选、版本化且经来源校验的扩展；不能要求尚未交付的新图引擎才可保存/回答问题。来源问题键幂等；同一问题在三渠道共享 ID，不能以展示文本匹配。授权申请另关联 requestId、动作、范围和新增额度，普通专业问答不自动构成授权。

问题状态区分 OPEN、ANSWERED、APPLIED、SUPERSEDED。回答保留原文、真实人类或代理身份、渠道消息 ID 和版本。重复同一答案返回原回执；不同答案、过期版本或错 owner 拒绝并展示冲突。人类优先规则与既有委托合同一致；已经生效的动作不能被迟到回答静默重写。

Agent 临时提问提交正式问题并让对应工作等待；只有当前执行已收尾或确认停止后才释放槽位和 Lease。不得把尚未完成的代码任务报告成成功完成。后续以固定输入/正式回答创建可追溯继续任务或恢复记录，不依赖原模型会话存活；继续执行不等于计划后继已完成。

ask 节点直接建立相同记录；回答先落库，再由 FD-026 的幂等推进合同消费。暂停中可以记录回答，但不得继续/新领任务。临时提问的安全 yield/继续载荷需与 FD-027/029 的生命周期及成果合同固定，不能凭一个新状态名绕过停止确认。

本 FD 拥有正式问题服务、现有任务来源校验和继续原语的消费；FD-026 拥有新图来源解析、ask 激活和新图回答消费。两者以版本化服务接口解耦，禁止未经校验的自由 sourceRef。暂停/取消界面先消费现有控制入口及 FD-029 的目标准入门禁；图执行的暂停/结束语义由 FD-026 接入后验收，不为等待新图而形成整 FD 交付环。

### 三渠道与身份

- Web Console 保留现有会话、CSRF、所有权与当前动作角色校验，展示同一问题/授权申请详情。
- 个人秘书通过专属凭证、进程 boot、有效 enablement 和精确目标范围办理；本地策略及聊天留在私库。outbox/原始信封与回执去重沿用既有约束。
- Telegram 只接受明确绑定的 owner、用户 ID 和私聊 chat ID；绑定/变更须经已认证的人类控制台。回调和文字答复使用短期、单问题、带版本的关联，不把用户 ID 单独当通用操作授权；去重 update/message/callback。群聊、多用户代答及其他渠道延期。

专业消息可直接转交展示，秘书可整理但保留原问题/回答，不能改写原意。展示进展、成果、分支与阻塞，不展示模型内部推理或凭证。

### 已确认的私人数据最小投影

仅提交用户明确选择的目标/需求片段、处理本问题必需的上下文和原始回答，以及工作关联标识。提交前本地可见本次投影范围；不自动带上整段聊天、私人策略原文、模板、其他项目记录、凭证或思维链。正文若本身含敏感信息，由明确提交动作决定，不扫描私库批量上传。平台只保存正式办理记录；私人模型输入不自动成为平台输入。

确认来源：2026-10-23 当前用户选择“采用上述边界”。这是首期产品合同，不是当前运行数据已上传或代理身份已启用。

### 暂停、取消、追加申请

暂停按钮解释为“停止新工作和推进，在途按已有授权收尾”，展示仍运行项目。单独取消/终止先记请求并阻止后续，保持 WAITING_STOP_CONFIRMATION；失联或不能判定显示 UNKNOWN，只有真实确认后显示停止。不自动回滚已经发生的外部操作。

循环默认初始+3 回跳；到限展示原因、已用额度、申请增加量、作用范围。秘书仅在有效审批委托及适用 policy 下提交代理决定，无权/冲突/无法判断则交人类；平台生效由 FD-029 所有。相同申请多渠道答复只生效一次。

## Scope

拥有 HI01～05 和 AC05/06/07；不重复计划语法、Factory、底层权限/预算或私人知识库治理。新运行的问题、回答、渠道去重和继续记录持久化，不迁移旧 DB，不自动启用旧身份/委托。不改变常驻/空闲回收策略。

### 就绪 Gates

- G1 已解决（2026-10-23）：用户明确确认最小投影、工具范围和 UNKNOWN 人工核对路径，不再保留该隐私问题为未答 Gate。
- 工程前置：固定 Telegram 绑定、单问题关联及临时提问 yield/恢复合同，与 FD-026/027/029 对齐；身份/持久化/控制设计需独立设计反馈。

### D028-01：安全等待及唯一继续

问题服务合同为 `aiw.questions.v1`。来源封闭为 LEGACY_PLAN_TASK 和 WORKFLOW_GRAPH_EXECUTION：前者验证既有 plan/task/attempt，后者由 FD-026 登记的来源解析器验证 graph version/execution。记录、收件箱和 applied 回执由本 FD 唯一拥有，execution.guard.v1 的安全挂起和 config.v1 的准备引用由上游提供。

execution 原语由 FD-029 拥有：YieldIntent 绑定 opId、task/attempt/Lease、固定 checkpoint、configRef 和来源问题键。任务 RUNNING→YIELDING 后保持 Lease/槽位，停止新的工具准入；问题可先建 OPEN，早到回答可保存但不继续。FD-027 确认模型执行及所有工具/子进程已静止后提交 yield-ack；FD-029 验证精确 Lease，CAS 到 WAITING_INPUT，Attempt 记 YIELDED 并释放 Lease/槽位，不能记为 COMPLETED。

无确认仍保持 YIELDING/UNKNOWN；失联不自动重放。重复 ack 返回原回执。取消先提交则不创建继续任务，迟到完成只保存事实；已经 yield 的 Attempt 不能再以旧 Lease 完成。继续任务以问题+被暂停任务+checkpoint 为唯一来源键，固定原配置/输入/回答并记录 resumesTaskId；它计入业务任务规模，但不消耗业务回跳额度、不冒充技术 Attempt。

只有已确认 yield、回答仍为当前版本且目标控制/预算允许时，唯一消费者在同一数据库事务创建继续任务并写 APPLIED 回执。既有任务由本 FD 的来源消费者办理，新图由 FD-026 的来源消费者办理；sourceKind 是封闭登记类型，不让两者同时消费同一来源。暂停中保留 ANSWERED，恢复后再应用。

该事务使用 FD-029 的 lineage 原语：旧 WAITING_INPUT 任务转为非成功终结 CONTINUED，唯一 successorTaskId 指向新任务，并原子转移现行计划步骤/逻辑输出槽绑定。依赖和输入沿 lineage 等待最终真实成果，不能把旧任务记 COMPLETED；用户看到“已由某续行任务接续”及当前业务状态。原任务、问题、checkpoint、配置与每段 Attempt 历史保留；最终续行失败/取消不能把整个业务槽误报成功。FD-026 消费相同有效任务绑定，不重新发明一套链。

### D028-02：回答及 Telegram 世代

收件箱以 questionId+requestVersion+渠道消息 ID 幂等落库，问题当前回答另有 answerRevision。OPEN→ANSWERED 使用 CAS；相同有效回答返回原回执，冲突不覆盖。APPLIED 前，符合现行委托规则的当前人类可显式覆盖代理答案，递增 answerRevision 并保留原答复及理由；应用事务校验 answerRevision，APPLIED 后只允许新决定/替代请求，不静默回改已发生动作。

Telegram token 为随机不可猜短期关联，绑定 owner/userId/privateChatId/questionId/requestVersion/bindingGeneration/到期，服务端保存摘要。解绑/重绑原子递增 bindingGeneration，使旧 token、待投递和排队消息失效；提交回答时重查当前绑定及权限。普通文本不携带有效单问题关联时只用于聊天，不能成为授权决定。

独立设计反馈：`reviews/FD-028-design-review-20261022-08211700.md` 的 D028-01/02 已补精确状态与世代合同，待有界独立复查；原报告的未答人工 Gate 已由当前会话用户实际回答解决。

## Work items

### 实施接口细化（2026-10-23）

- 临时问题由可信 broker 创建本地 Git checkpoint，再以精确 Lease 保存问题及 yield 意图。模型仅提供问题、必要上下文及选项。模型、工具和子进程确认静止后才 acknowledge；无法确认则保留资源和 UNKNOWN。续行基线只允许原基线或该任务已登记 checkpoint 的真实提交，不伪造已完成的 TaskInput。原配置、政策交集、上游成果和回答引用保持固定。
- 正式问题服务拥有原始问题、不可变回答历史及唯一 APPLIED 回执。现有计划来源验证真实 plan/task/attempt；图来源由 FD-026 注册解析器。续行与逻辑工作槽绑定在同一事务提交，原任务为 CONTINUED，工作/审查轮次均沿 lineage 查最终任务，Lease 操作仍精确定位原任务。
- 秘书普通回答新增 A19_ANSWER 动作，沿用当前专属身份、boot、enablement、目标读取范围、有限授权及原回执。精确授权参数保持不变。显式选择的原始投影先以 requestId 和摘要不可变登记；授权信封 evidence 绑定该摘要，动作执行时才写 ANSWERED。投影登记无业务效果，不能代表授权。外层不包事务，避免破坏现有 admission 失败回执事务。授权追加仍走 A18_BUDGET，并绑定具体额度申请，不由普通问答授权。
- Telegram 使用控制台显式建立的 owner/user/private-chat/generation 绑定；服务端只保存短期关联 token 摘要。送达采用持久 outbox，失去外部结果时 UNKNOWN，不自动重发。文本必须回复已记录的当前消息，回调须匹配当前 token；去重 update/message/callback。复用已有 Telegram poller，避免同一 bot 两个消费者。普通消息、群聊、过期绑定均不能推进。
- 新受控任务的取消只关闭新准入并提交精确停止请求，等待真实确认；不调用旧计划取消中的 Git 回滚。暂停允许保存回答，但推进检查当前控制和预算。恢复只消费已持久事实，不重放工具或模型调用。

相比将回答正文加入授权参数、将 checkpoint 冒充完成成果、或各渠道独立维护状态，上述接口保留既有权限等式、真实执行历史及单一业务决定。新增状态、表和路由不迁移旧数据。上述为工程设计，仍需静态独立核对和实现；未运行跨进程或渠道验收。

补充设计反馈 [I028-01～04](reviews/FD-028-interface-design-20261023-08211700.md) 已采纳为以下实施约束：

1. broker 首先关闭模型工具准入并排空已接纳调用，随后仅由可信适配器在已有 Git 权限下冻结最终 checkpoint，登记问题及 yield；模型和全部子进程确认静止后 ack。无更改复用原基线。checkpoint 固定源 task/attempt/Lease、仓库、提交、内容摘要及登记操作，保留对象库/工作区。首期取回限定本机受控持久 Git 对象库：登记主机与规范仓库路径摘要对应的 storage capability，续行只可追加该精确能力；另一个 Worker 或重启进程须使用同一对象库，经精确 Lease 取回已登记 checkpoint，再核对真实提交。不同物理对象库不兼容，不自动 fetch、上传或下载。
2. LEGACY_PLAN_TASK 精确定义为现有受控任务，封闭支持 configured-plan 与 flexible-plan。共同验证实际 execution policy/context/task/Attempt/配置；前者不要求旧 WorkflowPlan，后者额外验证旧计划与逻辑槽。图来源只由注册 resolver 接受。
3. A19 固定 PROFESSIONAL 参数及完整问题来源/requestVersion/answerRevision 快照。投影登记绑定平台实例、owner、个人 Worker、requestId、真实进程 boot、原信封和摘要。工作暂停仅为 execution_policy.paused，保存回答不推进；authority_target 的暂停、人类接管、限制、委托禁用和撤销仍拒绝，绝不增加统一暂停门禁豁免。新增授权参数仍精确全等。
4. 同一 bot 单实例运行，持久收件并提交后才推进 offset；重启先处理已存未消费收件。outbox READY→SENDING→DELIVERED/UNKNOWN/INVALIDATED，重启遗留 SENDING 视为 UNKNOWN。跨平台实例争抢 bot 不受支持，启动用本机独占锁拒绝重复消费者；多主机部署须由运维保证单实例。新问题前缀与旧审批分派封闭，未知前缀不得落入旧审批。

这些接口细化解决补充反馈的设计缺口；实现完整性仍由独立 Reviewer 判定，不将设计反馈标作实现通过。

每项 small、中难度、估算不超过半天；隐私和身份外部未知在实现前解除。

| ID | 依赖 | 独立结果及完成条件 |
| --- | --- | --- |
| 1.1 | G1、工程前置 | 固定问题/回答/申请/yield 合同及最小投影 |
| 1.2 | 1.1 | 问题、来源键、回答和渠道去重持久化 |
| 1.3 | 1.2 | 回答版本/owner/幂等/冲突及人类优先校验 |
| 2.1 | 1.3、FD-029 准入门禁 | 既有计划/任务的问题服务及回答消费接口，暂停中不继续；新图 ask 消费由 FD-026 实现 |
| 2.2 | 1.3、FD-027/029 yield 合同 | 临时提问安全释放槽与固定输入继续，历史完整 |
| 3.1 | 1.3 | 控制台展示原问题/回答及状态，复用既有样式与认证 |
| 3.2 | 1.3 | 秘书最小投影、问题送达及原信封回执重试 |
| 3.3 | 1.3 | 人类认证下的 Telegram 私聊精确绑定及解除 |
| 3.4 | 3.3 | Telegram 单问题送达/答复关联/去重，拒绝群聊与错身份 |
| 4.1 | 2.1、2.2、既有控制入口 | 暂停、取消和在途/未知展示，不假报停止；新图控制消费由 FD-026 拥有 |
| 4.2 | 3.2、3.4、FD-029 决定合同 | 具体授权申请展示/代理答复，重复不会追加两次 |
| 5.1 | 4.1、4.2 | 同步秘书/渠道/spec 文档，完成编译及供独立 Reviewer 审查的实施双份证据；审查与本地交付通过后续生命周期记录 |

- [x] 1.1 正式问题及隐私合同。
- [x] 1.2 问题和去重持久化。
- [x] 1.3 回答版本及身份校验。
- [x] 2.1 既有计划问题服务。
- [x] 2.2 安全 yield 与继续。
- [x] 3.1 控制台问题展示。
- [x] 3.2 秘书投影和回执。
- [x] 3.3 Telegram 私聊绑定。
- [x] 3.4 Telegram 送达和答复。
- [x] 4.1 暂停及取消展示。
- [x] 4.2 具体申请和代理答复。
- [x] 5.1 实施文档、编译及审查用双份证据；生命周期审查/交付仍待真实后续事件。

## Acceptance

| 来源 | 可观察结果 | 工作项 |
| --- | --- | --- |
| AC05 | 计划 ask/临时问题经秘书送达，回答恢复精确工作，旧会话可已退出 | 2.1、2.2、3.2 |
| AC06 | 三入口共用问题；重复、旧版本、错 owner 不产生额外推进 | 1.2、1.3、3.1～3.4 |
| AC07 | 暂停阻止后续并保留在途事实；取消未确认不能显示停止 | 2.1、4.1 |
| 授权与隐私 | 一句“可以”只对应本申请；无委托秘书不能批准；无私库批量上传 | 1.1、3.2、4.2 |

Telegram 解绑、消息重放、两个渠道不同答案及重启重投都必须保持正式记录，不根据最后到达的文本任意覆盖已生效决定。

## Verification

默认静态认证/投影/状态/恢复调用链核对及实现后的适用离线编译；不生成或执行测试，不访问 Telegram/模型/网络，不启动秘书/服务，不连接私库或删除数据库。设计需独立反馈，实现另需独立 Reviewer。实际跨渠道及进程中断时序尚无运行证据。

首轮 Worker 交接记录（历史）：实施工作项全部完成，最终离线编译和实施双份证据已齐备，当时待独立 Reviewer、受管本地交付和归档。未启用真实代理、上传私人数据或运行数据库。当前独立结论见下方第 2 轮复核。

独立实现审查第 1 / 3 轮：**changes-requested**。独立 Session `fd028-review-20261023-08211700-r1` 领取 `FD-028-000005-implementation-ready`，比较 `1b7e964..e1f9d14`（源实现 `307e5bc`），发现 R028-01/P1：实际 admission 未核对已登记原投影的完整信封；R028-02/P1：旧轮次授权/终态消费者未归并 lineage，resume 可膨胀 minimumRounds 且 CONTINUED 阻塞旧成果问答/恢复；R028-03/P2：Console 授权问题叠加操作与审批角色，普通 APPROVER 被拒。详见 [独立审查](reviews/FD-028-review-20261023-08211700-r1.md) 及同名 JSON、[阻断反馈](reports/FD-028-blocker-review-20261023-08211700-r1.md) 及同名 JSON。以上“全部完成/待审”是 Worker 交接时记录，不代表当前已通过；本轮后失败累计 1，上限 3 不重置。Reviewer 只作静态检查，未运行测试、编译、模型/后端、项目、服务、数据库/迁移、Telegram、沙箱、并发/恢复或网络，未修改实现、提交、合并或归档。三项修复前不得整体通过或本地交付。

独立修复复核第 2 / 3 轮：**verification-passed（静态证据范围）**。新独立 Session `fd028-review-20261023-08211700-r2` 领取 `FD-028-000007-implementation-ready`，复核 `e1f9d14..8fc159b`（父基线 `1b7e964`，修复源 `f62d392`）及直接影响，确认 R028-01/02/03 均 resolved，未发现新增阻断。详见 [第 2 轮独立复核](reviews/FD-028-review-20261023-08211700-r2.md) 及同名 JSON；原 blocker 双文件已保留历史并标为 resolved。原失败累计 1 次，上限 3 不重置；旧段落的待审/待编译与首轮阻断为阶段历史，以本结论及真实 CLI 回执为当前状态。待 PM 本地交付，Reviewer 不合并或归档。Reviewer 未运行测试、编译、项目、后端/模型、服务/HTTP、数据库/迁移、Telegram、checkpoint、沙箱、并发/崩溃/停止恢复或网络；运行行为及部署前置仍未验证，没有修改实现或提交 Git。

## 设计就绪记录（2026-10-23）

已取得独立定向复查 [FD-028 design recheck](reviews/FD-028-design-recheck-20261023-08211700.md) 及同名 JSON；本轮原 findings 全部 resolved，建议 ready。已确认的人类政策及工程合同作为当前设计，本文此前“候选/待复查”的阶段描述以本节为准；尚无实现通过或运行认证。本 FD 当前阶段由 CLI 状态与真实交接记录确定。

## Sources

- Issue: ISSUE-00012
- [批准的需求](../../../issues/ISSUE-00012/issue-plan.md)；[父 FD](../../FD-025/FD-025_AI_DEV_MANAGER_AI.md)。
- `workers/personal-secretary/README.md`、`platform_client.py`；`workflow/application/WorkflowQuestionService.java`、`approval/telegram/TelegramApprovalAdapter.java`；`openspec/specs/secretary/spec.md`、`delegated-decisions/spec.md`。
- 设计来源事件：`FD-028-000002-design-requested`；Planner 会话：`fd025-029-host-20261022-234846-08211700`。

## 当前实施记录（2026-10-23）

正式问题、回答历史、唯一续行、原投影授权、三渠道和真实停止显示已落实于 `questions/`、Python broker 及个人秘书。合同与部署前置见 [FORMAL-QUESTIONS](../../../FORMAL-QUESTIONS.md)。首轮编译于 08:37 JST 通过（Java 272 源文件、Python 12 文件、两份 JS）；之后补上 QUESTION 能力协商及 App Server 中断 schema 校验，需最终重新编译。新增接口设计反馈 I028-01～04 的修正已写入上文；实现接合另交 Reviewer，不将静态检查等同运行认证。

## 第 1 轮审查修复

修复源 `f62d392` 于 08:50:12 JST 离线 Java 272 源码及变更 JS 语法编译通过；详见 [第 2 份实施报告](reports/FD-028-implementation-20261023-08211700-r2.md) 及同名 JSON。原失败 1/3 保留，等待独立复核。

已领取 `FD-028-000006-changes-requested`（本周期失败 1/3）。R028-01：在 admission 自身事务读取已登记投影，核对完整原信封及唯一 evidence/hash，再允许用量或业务效果；A19 无投影拒绝，独立 A18 保留。R028-02：共享 TaskLineage.effective 投影供旧成果问答、reviewRecovery 和权限快照使用，业务 round 去重；A01 累计创建历史及 A03 实际技术重试消耗不因续行或取消而清零。R028-03：owned 读取、普通问答/控制和具体额度审批分别按当前动作角色校验；APPROVER 可读/批申请和绑定自身私聊，不取得执行控制。UI 同步隐藏/禁用不属于当前角色的动作。待修复源的离线编译和独立复核，不声称原 findings 已获通过。

**Completed:** 2026-10-22

## 本地交付与完成归档

真实独立通过事件为 `FD-028-000008-verification-passed`，三项 R028 finding 全部 resolved，本周期一次失败历史保留。`aiw git wt local-merge FD-028` 已将源 `a017a9b` squash 到 develop 的 `b27d23a`，并清理受管工作区和分支，保留回执。`aiw fd close FD-028 Complete` 已完成本归档。此前实施/审查待办为历史记录，以本节及真实 CLI 事件为准；未运行的跨渠道、数据库及真实后端检查仍未运行。
