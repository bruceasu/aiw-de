# FD-080: Risk based milestone review policy

**Status:** Pending Verification
**Revision:** 2
**Review policy:** Independent
**Priority:** Medium
**Evidence policy:** Markdown

## Problem

默认 flexible Plan 在每个执行步骤完成后都创建独立评审任务，只有评审 PASS 才推进下一步。对较细的普通任务，这会重复加载相同材料、增加调用和等待。用户已同意调整为普通小任务自检、阶段成果集中评审、高风险改动独立评审，并在 Plan 确认时固定，Worker 不得自行降低。

## Options and decision

采用确认者明确选择的每步评审策略，而非 Worker 完成后临时决定，也不按固定 token 数或 Worker 退出次数触发评审。高风险和风险未明确的步骤保守要求独立评审；只有确认者明确标为普通低风险的步骤可以自检或作为里程碑。

保持既有 Plan 已确认的语义：缺失新字段的旧规范解释为逐步独立评审，不在执行中静默降低门槛。新 UI 提供“普通阶段工作”明确设置，除阶段末尾里程碑外可以自检；高风险单步独立评审。自检通过不会显示为独立评审通过。

## Solution

### 固定策略与风险声明

在 `WorkflowPlanSpec.Step` 增加 `reviewMode`（SELF、MILESTONE、INDEPENDENT）和 `reviewRisk`（LOW、HIGH、UNKNOWN）。字段保存在既有 spec_json，不新增业务状态或重写历史数据。缺失字段保守归一为 INDEPENDENT/UNKNOWN；HIGH/UNKNOWN 配合 SELF 或 MILESTONE 明确拒绝。风险来源是确认者对工作范围的结构化声明，平台不声称能自动识别所有高风险代码；权限、持久化、迁移、并发、计费、部署等未知影响应保留独立评审。

管理接口和编辑器只在 DRAFT 修改；确认后规范固定。报告正文、Worker 提交内容和补充回答都不能改变策略。现有 Owner、Operator、CSRF、版本、执行权限和原生绑定限制保持有效；策略不是新工具授权。

### 推进与交付物

- SELF：只有实际完成且报告 PASS 才推进，不创建 Reviewer；报告 REPAIR_REQUIRED 或缺失时不推进、不升级为 PASS。正式缺输入问题继续复用 FD-079 的 WAITING_INPUT/确认答案恢复。
- MILESTONE：在该阶段边界创建一个 Reviewer，固定输入覆盖自上一个评审边界后的普通步骤成果及当前成果，并保留先前已接纳内容作为背景。评审依据包含各被覆盖步骤的原目标和验收条件。
- INDEPENDENT：该步骤单独独立评审；必要上下游背景仍可读取，范围和权限不扩大。
- Plan 只在所有所需步骤按已确认策略通过、必要评审 PASS、无活动资源或待决控制时自动完成。最终 SELF 可以依明确策略完成自检交付，不能伪装为独立验收；既有管理者明确停止或手工结束仍记录为单独控制事实。

复用既有 work/review task ID、评审报告和修正授权。普通自检失败可以通过当前任务的显式人工恢复入口请求一次独立评审；恢复核验 Plan/task 版本和当前槽，不自动整 Plan 重跑。独立评审不通过仍遵循既有有限修正/人工授权；追加评审围绕具体发现与受影响范围，不能因为一次 Worker 返回而额外再审一次。

### 界面与说明

编辑器与确认前预览显示每步风险和评审方式，提供明确的普通阶段设置动作。未知风险不默认为低风险；选择高风险时固定为独立评审。详情页清楚区分自检、里程碑评审与单步独立评审，既有页面颜色、布局和交互保持一致。工作指令告知固定策略及真实报告要求，不允许执行 Worker 自行调整。

### 影响与回滚

这是公共 spec JSON、持久化解释和任务推进规则的变更，必须整项独立审查。保留旧 Java 构造器/缺失字段的保守解释，既有确认 Plan 不迁移。升级先 drain；旧服务不认识新策略，不能在仍有新策略可领取 Plan、活动 attempt 或待恢复任务时降级。保留成果、问题与审查历史，不通过删记录回滚。

## Scope

在当前工作区实施默认 flexible Plan 的策略、阶段推进、人工恢复、编辑器/详情和相邻说明。configured-plan 的既有独立工厂验收门禁保留，不自动降低。图执行引擎与已归档 FD-079 不重开；不更改工具权限、计费、Worker 类型或任务并发，也不引入 Pi/OAuth 依赖。测试执行仍需单独授权。

## Work items

- [x] 1.1 定义可持久化的评审方式/风险及保守默认，保留构造与旧规范读取。
- [x] 2.1 实现自检推进、里程碑材料范围和固定独立评审；统一接纳输入/Plan 完成门禁。
- [x] 2.2 自检未通过保留阻塞，支持精确版本的人工评审恢复，不自动放大重试。
- [x] 3.1 编辑器、模板、确认预览与详情显示固定策略和真实验收来源。
- [x] 4.1 补充关键回归用例、相邻文档与稳定规范，执行适用编译；记录实际验证与跳过项。
- [x] 4.2 汇总独立审查输入和升级/回滚核对；整项 Reviewer 结果由 Review policy 单独把关。

## Acceptance

1. 普通阶段中的多个 SELF 步骤完成并 PASS 后不派发 Reviewer，里程碑仅创建一个有固定来源的评审任务。
2. HIGH/UNKNOWN 不能通过接口或报告降为 SELF/MILESTONE；旧规范不改变已有门槛，确认后不可改策略。
3. SELF 的 REPAIR_REQUIRED、缺失成果、待回答问题和不确定资源不产生成功推进；人工恢复保持原任务/版本与权限。
4. 里程碑评审包含覆盖成果及原验收条件，未覆盖的未来步骤不混入；独立评审仍阻断下一阶段直到 PASS。
5. 最终完成检查与已确认策略一致；自检、阶段评审和独立评审状态不混淆，重复完成事件不重复创建任务。
6. 相关编译与实际获授权检查如实记录，整项通过独立审查后才本地交付/归档。不宣称真实服务、模型或部署验证通过。

## Verification

- 采用 fd-workflow，当前工作区直接实施，不创建 worktree。只读检查了 WorkflowStepCompletedHandler、WorkflowPlanService/Spec、工厂评审门禁、编辑器入口和既有回归。
- Review policy 为 Independent：变更涉及持久化 JSON、公共接口、跨模块推进和验收门禁。实现工作项准备就绪后请求一次整项独立审查，不按每个 Worker 产出分别派审查代理。
- 默认静态检查与 compile-only；后续获授权的两轮定向测试实际结果见下方接续证据。未运行服务、模型、网络、迁移或部署。
- 已实现三种固定方式与保守风险解释。旧字段保持缺失并在重新编码时省略，避免仅因升级插入默认字段而改变既有 managed Plan 冻结摘要。新字段在显式选择后保存于 spec_json，不新增 SQL 迁移。
- 自检 PASS 直接推进、REPAIR_REQUIRED 停止自动派发；精确人工恢复可请求当前成果评审或一轮修正。已有评审失败或明确授权修正的后继轮次必须独立复查，不能退回 SELF。修正评审固定携带上一份发现，不重做整个 Plan。
- 里程碑评审范围由确认规范中的评审边界确定，输入保留实际成果与原验收条件。新增 claim policy 排除参与覆盖阶段任一步执行的历史 Worker；检查只针对所覆盖阶段，不因更早已评审的背景阶段禁止复用独立身份。Factory configured-plan 的原有审查独立性规则继续生效，自动准备进程也检查这项准入。
- 使用已完成的正式问答 successor 解析实际成果，避免把已 yield 的原任务当成 SELF 的完成记录。UI 显示风险/验收方式，普通阶段设置需明确确认；确认弹窗保存并核对原 Plan 版本，防止异步刷新悄悄替换确认内容。
- `node scripts/compile-worker-runtime.mjs` 成功（包含 workflow-editor/i18n JavaScript 语法）；初始离线 `mvn -o -Dmaven.repo.local=D:/.m2/repository -DskipTests test-compile` 成功，330 个生产源、62 个测试源，10.905 秒。修复后的最新编译与测试状态见下方接续证据。
- 用户另外要求 Plane 集中管理登录/导入/刷新，已记录为 FD-081 的独立设计，部署方式已回答为本机；不属于本 FD 的实现/审查范围。没有读取、导入或刷新任何实际凭据。
- 独立审查仍待正式 Reviewer 回执；实现项的勾选不代表审查通过、真实联调或发布完成。部署、浏览器、真实模型与旧服务回滚未运行。

### 独立静态复查与当前接续证据（2026-11-07）

- Reviewer `fd080-reviewer-20261107-policy` 已领取真实来源事件 `FD-080-000002-review-requested`，对基线 `a2c77f4`、初始实现 `038e609` 与当前修复 `f0fcde428c5bb322a09f8b85134ca3bd10a8093d` 做独立静态复查。记录见 [FD-080_REVIEW_20261107.md](reviews/FD-080_REVIEW_20261107.md)。当前无剩余静态阻断，正式 Reviewer 事件仍保持在途，不冒充 runtime PASS/Complete。
- R1：原生问答实际成果的解析假设与真实 Jdbc/TaskLineage 不符，已补严格 APPLIED/Owner/source/slot 解析，统一实际纠错依赖，并保留 guarded-v1 解析路径；真实 SQL 用例替代单纯 mock 假设。R2：SELF 人工评审接纳与最终门禁不一致，已修复且将例外收窄到 SELF，原 Independent/Milestone 执行 PASS 门槛保留。反馈来源标签也区分实际 self/independent 来源。
- 用户授权的八组首跑 49 项：44 通过、1 failure、4 errors；前者为旧依赖预期未包含固定上轮发现，后者为测试夹具装配缺依赖，修复没有放松生产 DI。同范围一次修复重跑 54 项：53 通过、1 failure；native SQL 来源/Owner-source拒绝/纠错依赖用例通过，剩余有效旧 Independent 执行 REPAIR 守卫指出前次完成门修复过宽。
- 最新 `f0fcde4` 已收窄 SELF 例外并增加 guarded-v1 兼容用例；当前 55 项 runtime 尚未获额外运行授权，未运行。最终离线 test-compile 成功（330 生产源、62 测试源，10.628 秒），不能将编译或 53/54 记为全通过，也不复用 FD-079 的结果证明新行为。
- 按当前父流程要求保留 Pending Verification 和已领取的审查事件，不发未经完成的正式 PASS 或 Complete。额外同范围测试授权问题仍待回复，授权后以真实结果续接；未增加未经用户授权的真实服务、模型、浏览器、网络、部署或回滚检查。FD-081 仅为独立本机认证设计，不在本 FD 范围。

## Sources

- 用户确认“小任务自检、阶段集中评审、高风险独立评审，并在确认 Plan 时固定”的建议。
- `WorkflowStepCompletedHandler` 现有逐步评审路径；`WorkflowPlanService` 既有修正授权、输入接纳和完成门禁。
- 已归档 FD-079 的原生 Owner/问答恢复与未知资源边界。
- [Plan 评审策略说明](../../WORKFLOW-REVIEW-POLICY.md)
