# FD-025: AIW DE 软件开发闭环集成

**Status:** Complete
**Revision:** 8
**Priority:** Medium
**Evidence policy:** Dual

## Problem

用户希望从软件目标得到可审阅计划，经兼容 Worker 执行、问答及必要修复，最终收到版本明确、授权有效、证据真实的交付。目前四个子成果之间尚无完整集成合同。本 FD 只负责闭环装配、跨成果验收和使用说明，功能分别由 FD-026～029 唯一拥有。

用户已批准 ISSUE-00009 及四个子 Issue，本次明确要求五个 FD 自动推进完整本地生命周期。此前 Issue 的“止于 promote”是历史阶段限制，本次请求已扩展至实现、独立审查、本地交付和归档；不包括部署、数据库删除、网络、测试或最终构建。

事实：五个设计事件已由本会话领取；现有生产代码具有推荐计划、任务/Lease、个人秘书、授权和成果模块。上述存在性不是新闭环已实现的证据。

## Options and decision

| 选项 | 取舍 | 决定 |
| --- | --- | --- |
| 五个 FD 各实现一套完整闭环 | 重复调度、权限和问答状态，难以保持一致 | 不采用 |
| 四个子 FD 拥有能力，父 FD 只集成 | 保留用户确认的拆分，共享合同只有一个所有者 | 采用 |
| 把五个 FD 合并为单一实现 | 丢失已批准成果和独立审查边界 | 不采用 |

实施次序按可独立验收的版本化合同推进：FD-029 的版本/操作许可基础 → FD-027 的配置准备与 Python 消费 → FD-028 在既有计划/任务上的正式问答 → FD-026 的新图执行和消费者接入 → 本 FD 集成。设计可以同时收敛，完整交付不依赖尚未实现的下游。

FD-029 必须在现有任务/Runtime 的真实工具路径上独立验收，不等待新图。FD-027 可从既有任务入口验收配置切换。FD-028 先以现有 plan/task/attempt 锚定问题和继续工作，图 executionId 是后续经过校验的版本化扩展；其实现不等候 FD-026 的新引擎。FD-026 最后实现新图对这些已交付服务的消费，父 FD 核对最终闭环。不能以只交付未消费 API 来代替任何 FD 的实际行为。

## Solution

### 单一所有者与接合点

| 合同 | 所有者 | 本 FD 的集成责任 |
| --- | --- | --- |
| 目标、计划版本、节点激活、分支/回跳 | FD-026 | 从秘书/控制台目标进入同一计划入口 |
| 专业配置快照、能力检查、逐任务 Python 执行 | FD-027 | 验证同一兼容进程可依次接收不同配置 |
| 正式问题、渠道身份、回答、暂停/取消体验 | FD-028 | ask 及执行中提问接入同一问题记录 |
| 输入版本、实际工具许可、预算账本、去重/恢复、交付证据 | FD-029 | 检查上述接合点没有绕过正式合同 |

同一计划中的业务循环 ID、节点执行 ID、业务任务 ID、Attempt ID、配置摘要、问题 ID、操作请求 ID 分别使用各自所有者的正式标识；不得靠自然语言、数组下标或模型会话 ID 关联。

接合版本固定为 FD-029 生产的 `aiw.execution.guard.v1`（许可/账本/输入/安全挂起，027/028/026 消费）、FD-027 的 `aiw.execution.config.v1`（准备引用/能力快照，026/028 消费）、FD-028 的 `aiw.questions.v1`（正式问题/回答/消费回执，026 消费）、FD-026 的 `aiw.workflow.graph.v1`（图激活/控制/交付视图，本 FD 消费）。消费者拥有其新增接入及兼容核对，不要求上游实现尚未交付消费者。

父 FD 不另建任务引擎、授权引擎、私人数据库或结果账本。新增集成代码仅限入口连线、能力可用性提示和闭环展示缺口。若发现子合同缺失，回到所属 FD 补足，不在父 FD 再实现一份。

### 端到端场景

使用来源 Issue 的 FO API 幂等需求作为目标示例，不在本仓库另行开发 FO API。依次覆盖：生成草稿与审阅 → 必要澄清 → 准备需求/开发/审查/验证配置 → 条件分支与修复回跳 → 提交交付包。独立分支可并行；未选择路径不阻塞；失败必要分支不能算成功。

边界场景包括：三个回跳额度耗尽、两个渠道重复回答、暂停中收到完成、取消未确认、配置不兼容、重复完成消息、重启后恢复、外部结果 UNKNOWN、执行工具被拒绝。外部目标项目的测试要求与本次仓库测试执行授权分开，未执行检查必须如实展示。

### 工作区与版本

用户选择隔离实施，并授权先整理、单独提交既存改动。基线准备已产生 `95beefe`（授权 Repository 代理访问）、`d7f5bdf`（本地启动配置）和 `efb86e4`（批准的 Issue 来源）。`.env` 留在本地，通过 Git 本地 exclude 排除；没有删除或读取其值。

原始需求基线仍为用户声明的 `8e93fdd`/HEAD.zip，后续工作树从实际记录的父分支创建，不将两个基线混称。实现前提交就绪设计，再通过 `aiw git wt add` 创建各 FD 工作区并核验 workspace.json。

## Scope

包含软件开发闭环的装配、成果覆盖核对、操作说明与延期能力标识。不包含市场/交易业务、安全运营业务、全部职责实例化、常驻/回收策略、长期记忆、Worker Leader、旧运行数据迁移、部署、远程发布或数据库删除。

十项职责名称、知识归属、需求评审/行为审计区分沿用批准的 Issue；分类和模板均不构成实际授权。

### 决策依赖及就绪 Gates

- G1 已解决（2026-10-23）：用户选择预算政策 A，缺少所需上限先等待配置；到限停止新派发和推进，在途按已有授权收尾。工具、资源及 UNKNOWN 人工核对采用 FD-029 的边界。
- G2 已解决（2026-10-23）：用户明确要求 Python OpenAI Agents 与 Codex App Server 两种后端首期都支持；Codex 的 Unix/WSL 边界保留，不能删去该后端来规避工程问题。
- G3 已解决（2026-10-23）：用户确认 FD-028 的最小私人数据投影；未扩大真实委托或启用运行权限。
- 工程就绪条件：四个子设计的接合点一致，重大权限/持久化/并发设计取得独立设计反馈。当前均为设计候选，未 emit design-ready。

独立反馈 D025-01 已据此补入接合版本及消环顺序，待有界复查；报告为 `../reviews/FD-025-design-review-20261022-08211700.md`。2026-10-23 用户补充不限定 Codex/OpenAI Agents SDK 版本，兼容差异由适配层修正；接口能力不满足安全条件时明确阻塞，不降级权限。

## Work items

### 已交付子合同上的集成接口细化（2026-10-23）

FD-029/027/028 已归档交付；FD-026 已实现、正按真实 Reviewer 事件修复复核。以下细化只装配子模块，不拥有第二套图推进、授权、问题或交付账本。新端口/持久化/私人数据边界在实施前取得一次有界独立设计反馈。

1. **秘书的最小目标投影。** `GoalProjection(selectedGoal,necessaryContext,acceptance)` 为封闭数据；私聊全文、私人 policy、模板与凭证不进入该对象。私人秘书先只生成可见 JSON 及 SHA-256；用户确认精确 digest 后，才把 `GoalProposal(requestId,projectionJson,digest)` 提交到平台。该操作只保存惰性候选，既不创建正式计划，也不消耗执行授权、调用 Factory 或模型。读取/提交使用既有独立个人凭证、当前 worker boot、明确启用及 owner 身份；新机器路由 `/api/r1/authority/goal-proposals`（POST）、`/goal-proposals/{requestId}`（GET），严格路由和大小限制。V16 仅新增候选表，固定 owner/worker/原请求/raw digest/平台 instance 与 boot 世代；同 owner+UUID 原文不一致拒绝，重放只返回原收据。私人端发送前保存 SENDING，失去响应保留 UNKNOWN，只能查询原 ID，不自动重发；重启后的 SENDING 同样先核对。预览不发送网络或执行模型。
2. **人类采纳与同一草稿入口。** `/ui/software-development` 的当前 owner 操作角色读取自己的候选，并按精确 proposal/digest/目标 plan ID 采纳；服务构造原 `GraphPlans.change` 的 UUID+expectedVersion=0+source/reason+空不可执行 Spec，原子写采纳收据。新计划目标/范围/验收仅来自选定字段；缺范围/验收明确标为待澄清，图仍空 DRAFT，用户之后可用原图入口请求只读规划/审阅。重复采纳返回原 plan/version，不回写后续编辑；同 proposal 不允许换 plan。候选状态不成为委托批准，也不新增 A01 的语义。秘书无权通过本入口激活、修改或执行图。
3. **闭环只读投影。** `/api/r1/authority/software-development/read` 先复用 GraphPlans 的当前 owner 操作/审批读取边界，再组合版本/范围/选路/轮次/真实 Task/Attempt、Factory preparation 缺口、正式问题、policy/共享账本和角色分类。正文仅显示现有事实及对应控制台链接；执行控制/回答/审批仍去各原服务。职责分类是模板元数据，预留能力和 Go/TypeScript 旧 Runtime 明示未纳入新图首期，不改变私有数据归属。
4. **交付证据视图。** 只从最终有效 ROOT 出口的冻结成果槽收集真实 TaskInput（task/Attempt/commit/摘要），并读取相应 `execution_delivery` 与 `execution_context`。核对来源 owner/plan、实际 COMPLETED Task/Attempt、交付原文摘要、baseline/result commit 及固定输入/配置/来源摘要；不从旧轮、失败、CONTINUED 或“最新任务”猜结果。缺交付、缺明确版本、摘要/来源不一致分别标为缺失/冲突；Graph COMPLETED 也不伪造一份完整交付。实际命令与未执行检查显示原记录，没有记录则写未记录，不写通过。该视图不创建第二份正式 Delivery，也不发布、push 或部署。
5. **界面及证据。** 复用 Web Console 样式，为目标/候选、图编辑、配置、问答/控制、授权/UNKNOWN 核对和正式交付提供同一入口；私人秘书沿用既有本地 UI 的预览确认形式。最窄离线 Java compile、变更 JS 的语法检查和变更 Python 的内存 compile 仅为编译证据，不启动服务、DB、模型、Worker、渠道或网络。跨成果场景记录静态调用链及源码位置，未执行场景明确保留风险。

工程取舍：惰性提案加人类采纳，多一步明确审阅，但可复用已交付 GraphPlans 与现有身份，不把私人聊天或新授权机制塞进图执行。V16 是新库附加表，不迁移或删除用户 DB。自定义候选/实际角色模板不自动授权；所有运行权限仍由原 Guard/Factory/问题模块决定。

定向接口约束：提案事务调用 `admission.enabled(principal)` 并复验当前 owner/worker/generation/平台 boot/worker boot；不存在计划时不要求 read_scope/A01。记录的旧 boot 只是来源历史，重启后以当前启用的同 owner+worker 查询旧回执；SENDING/UNKNOWN 或查询 404 均不自动转为可重发。人类采纳同一 authority 锁/事务中先查不可变 adoption receipt，再调用 GraphPlans.change 和 CAS 登记；幂等重放返回原 plan/version，不重新构造 change 或覆盖后来的 head。交付只核对 ROOT 当前正式出口：重算出口/Fact/TaskInput、context.input_json、delivery 原文摘要，对照物理 COMPLETED Task/Attempt、完整 baseline/result、配置及 sources；命令是 Worker 原声明，不从其文本推断平台验证通过。

[集成接口设计反馈](../reviews/FD-025-integration-design-20261023-08211700.md) 已单次定向确认 I025-01～03 resolved、scoped ready，source_event=null（咨询），不替代实施审查。四个子成果均已按真实事件合入并归档：FD-029 `5d684e5`、FD-027 `e13cbc8`、FD-028 `b27d23a`、FD-026 `bd22b48`。当前工程任务是本 FD 隔离实施上述接合，运行预算仍为零。

实施接合发现并补足同 plan ID 的所有权闭合：GraphPlans 创建/修改及读取聚合前核对已有 execution_policy/authority target owner；Guard.confirm 反向核对已有 graph owner。修改原拥有者模块的窄入口，不建立第二套权限判断引擎或给予新权限。本 FD 独立实现审查覆盖该集成差异，保留 FD-026/029 原审查历史，不声称原归档报告已覆盖此后新增差异。

所有工作项为 small；估算不超过半天/项。难度均为中；遇到未解决的外部合同须先处理 Gate，不能靠拆小降级风险。

| ID | 依赖 | 独立结果与完成条件 |
| --- | --- | --- |
| 1.1 | G1～G3、四个子设计 | 固定接合合同及 AC 覆盖矩阵，消除重复所有者与矛盾 |
| 1.2 | 1.1、FD-026/027/028/029 相应入口 | 连通目标提交、草稿审阅与执行入口；不绕过准备及授权 |
| 1.3 | 1.2 | 连通 ask/临时问题/取消及恢复；身份、版本和来源关联完整 |
| 1.4 | 1.2、FD-029 交付包 | 展示统一进展及正式交付；缺失证据如实标注 |
| 1.5 | 1.3、1.4 | 完成正常及边界场景的静态调用链核对，记录实际证据和未运行项 |
| 1.6 | 1.5 | 更新首期操作、配置和延期说明及实现交接证据；独立审查/本地交付另记真实生命周期结果 |

- [x] 1.1 固定接合合同与验收覆盖。
- [x] 1.2 目标至执行入口接合。
- [x] 1.3 问答、控制与恢复接合。
- [x] 1.4 进展与交付展示。
- [x] 1.5 集成边界静态核对。
- [x] 1.6 文档、编译及实现交接证据；独立审查及交付另记真实结果。

## Acceptance

| 来源验收 | 唯一功能所有者 | 本 FD 要求的集成结果 |
| --- | --- | --- |
| AC01、02、03、08 | FD-026 | 目标生成可审阅计划、条件/并行/回跳按选中路径推进 |
| AC04 | FD-027 | 同一兼容 Worker 按不同固定配置执行，Lease 语义保持 |
| AC05、06、07 | FD-028 | 三渠道处理同一工作，控制真实反映在途状态 |
| AC09、10、11 | FD-029 | 实际工具边界拒权、去重恢复、交付版本及证据完整 |

任何子成果未满足跨成果安全条件时，不以“父 FD 已集成”宣称整体完成。正常结束等待全部已启动工作结束或取消确认；UNKNOWN 保留阻塞与明确人工路径。

## Verification

默认静态设计/实现审查及代码变更后的编译检查；没有请求测试支持，不生成或执行测试，不运行服务、模型、网络、数据库或目标项目。独立实现 Reviewer 必须检查真实差异及 AC 覆盖；设计反馈不能代替实现通过事件。

基线准备执行：`git diff --check` 通过。第一次 `mvn -o -DskipTests=true -Dmaven.test.skip=true compile` 因 mvn 不在 PATH 未启动；一次修正入口至 `C:/green/apache-maven-3.9.9/bin/mvn.cmd`，临时 JAVA_HOME=`C:/green/jdk-25.0.1+8` 后同一离线 compile 目标成功，输出 Nothing to compile。没有重新编译新 FD 代码，也没有运行测试或启动脚本。

TODO：收敛独立设计反馈 → 就绪交接 → 各子成果实现/审查/交付 → 集成/审查/交付/归档。G1～G3 已获当前会话用户回答，不再重复询问；依赖未满足时保留 Design，不把候选方案记为已实现。

当前实施记录覆盖上述历史阶段文字：四个子 FD 已真实交付归档，本 FD 使用 `feature/FD-025` 隔离实施，基线 `c877040`，正式 Worker 来源 `FD-025-000004-work-requested`，会话仍为 `fd025-029-host-20261022-234846-08211700`。`software/GoalProposals`、`SoftwareDelivery` 及控制台/私人 selected_goals 接合完成，V16 只新增惰性提案表。静态场景及延期/使用边界见 [操作说明](../../SOFTWARE-DEVELOPMENT.md)，用户实验及 .env 未改变。

实际离线 Java compile 于 2026-10-23 22:07:56 JST 成功编译 285 个生产文件；JDK25/Maven3.9.9，命令 `mvn -o -DskipTests=true -Dmaven.test.skip=true compile`。4 个变更 Python 文件仅标准库内存 compile，2 个变更 JS 仅 node --check，git diff --check 均通过。没有项目 import、测试、服务、数据库/迁移、HTTP/浏览器、模型/Worker、Telegram、真实并发/恢复/停止、网络、安装、push 或部署运行证据，不能把上述未执行项写成通过。原 GraphPlans 泛型解码提示及工具警告保留。“尚待独立实现审查、失败 0/3”为首轮 Worker 交接时阶段记录。

2026-10-23 第 1 轮独立实现审查：Reviewer `fd025-independent-review-20261023-08211700-r1-integration` 核对并领取真实 `FD-025-000005-implementation-ready`，完整静态审查 `c877040..06de05a`（实现 `47fb205`），结论 **changes-requested**。R025-01 为已有 Legacy Plan ID 的跨 owner 占用与反向确认阻断；R025-02 为交付视图文件顺序与原成果集合合同不一致。见 [双份审查报告](../reviews/FD-025-review-20261023-08211700.md) 及 [双份阻断反馈](../reports/FD-025-review-blocker-20261023-08211700.md)。累计失败 **1/3**，Reviewer 未编译、测试或执行运行检查，未改实现。真实交接回执由 CLI 记录。

2026-10-23 第 2 轮独立实现复核：Reviewer `fd025-independent-review-20261023-08211700-r2-integration` 精确领取 `FD-025-000007-implementation-ready`，静态核对 `06de05a..6f72124`（修复源码 `5007ed3`）及直接接合，R025-01/02 均 **resolved**，结论 **verification-passed**。见 [第 2 轮双报告](../reviews/FD-025-review-20261023-08211700-r2.md)，原 blocker 双文件保留首轮事实和修复历史并记为 resolved。累计失败仍为 **1/3**。本 Reviewer 未重跑 Worker 的 22:15:05 JST 编译或其他运行检查，未改实现；真实回执由 CLI 记录。

当前 TODO：PM 依据真实通过回执检查本地交付/归档条件。已确认人类政策不变，运行风险继续保留，不把未运行行为写为通过。

## 设计就绪记录（2026-10-23）

已取得独立定向复查 [FD-025 design recheck](../reviews/FD-025-design-recheck-20261023-08211700.md) 及同名 JSON；该轮原 findings 全部 resolved，建议 ready。已确认的人类政策及工程合同作为当前设计，本文此前“候选/待复查”的阶段描述以本节为准。“尚无实现通过”为设计阶段历史，当前实现结论见 Verification；仍无运行认证。本 FD 当前阶段由 CLI 状态与真实交接记录确定。

## Sources

### 第一轮实现审查修复

实际领取 `FD-025-000006-changes-requested`，失败累计 1/3。R025-01 在原 GraphPlans.change 同一 authority 锁内补查既有 flexible_workflow_plan.created_by，与当前 owner 的真实 actor 一致才允许接合，不能用缺 policy/target 的旧初稿 ID 抢占；同 owner 原合法行为保留。R025-02 按原 Guard/WorkspaceResult 的文件集合合同比较 Delivery.changes，允许顺序差异，仍拒绝多/少文件，原文 digest/Task/Attempt/版本/来源严格不变，不改历史 Delivery。修复 Java 于 2026-10-23 22:15:05 JST 离线编译 285 个生产文件通过；Python/JS 未改变，沿用首轮编译证据，不重复运行。此段“复核待交接”为 Worker 修复交接时历史；后续真实第 2 轮结论见 Verification。

- Issue: ISSUE-00009
- [批准的父需求](../../issues/ISSUE-00009/issue-plan.md)。
- [FD-026](../archive/FD-026/FD-026_AI.md)、[FD-027](../archive/FD-027/FD-027_WORKER.md)、[FD-028](../archive/FD-028/FD-028_FEATURE.md)、[FD-029](../archive/FD-029/FD-029_FEATURE.md)。
- 设计来源事件：`FD-025-000002-design-requested`；Planner 会话：`fd025-029-host-20261022-234846-08211700`。
