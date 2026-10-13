# FD-017: 灵活流程 Web Console：自定义、推荐模板与 AI 推荐

**Status:** Complete
**Revision:** 8
**Priority:** Medium
**Evidence policy:** Dual

## Problem

管理者创建 `AR-3037a9f63511417aaa7bb0ca09288d22` 后，不知道处理流程是否可选以及如何查看。现有 New Run 直接启动固定自主开发链，没有流程选择、编辑和推荐入口。

ISSUE-00001 的产品方向包含可选模板、自选步骤及 AI 推荐。FD-011 完成了灵活计划管理 API，但最终范围排除了交互表单、导航和自动 AI 调用。其 Complete 不代表完整创建体验已经交付。本 FD 补齐 UI 与必要的推荐接入；导入外部 AI JSON 不能代替实时推荐。

## Options and decision

1. 只做 JSON 导入表单：简单，但不能满足可视化编辑和实时推荐，不采用。
2. 重写 AR 完成处理器：影响历史任务、审批和恢复，超出 UI 目标，不采用。
3. 基于 FD-011 增加灵活计划创建、列表、详情与推荐 UI；原创建入口明确区分自选流程与固定自主开发：采用。

复用 Web Console 视觉、会话与 CSRF。现有 `/ui/workflow-plans` 返回 JSON，新 HTML 页面采用独立路径或明确的内容协商方案，保持原 API 客户端契约；具体路由在 1.1 固定。

## Solution

### 创建与推荐

- 导航提供灵活流程入口。New Run 提供模式选择，默认引导到可选择的灵活流程；固定自主开发须显式选择，不将灵活提交转回 AR 固定链。
- 用户输入需求/目标及可选 Workspace、Git revision，从推荐模板、自定义空白流程或 AI 推荐开始；无 Git 的目录工作区继续可用。
- 推荐模板至少覆盖单步分析、方案与规划、开发与审查验证。每个模板显示适用目标、步骤、技能、执行引用，可编辑、删步骤或不用模板。
- 模板是有界步骤图，不冒充 AR 自动修复、工程 Gate 或人工审批；页面说明适用边界。
- AI 推荐由用户点击触发，针对当前需求生成步骤、依赖、必需/偏好技能和推荐理由；展示处理中、成功、失败及不可用状态，允许再次请求。导入外部计划可作为辅助功能，不能算实时推荐通过。
- 新推荐不自动覆盖手改；用户主动应用后进入同一编辑器。需求变化后标识旧推荐，过期响应不覆盖新需求或新结果。
- 服务端校验 AI 输出的结构、依赖、技能和执行引用。无效、超限或不存在的引用不能执行，不能静默扩大范围或权限。失败后仍可手动继续。

### 编辑、预览与确认

- 支持增删步骤、编辑标识/指令、必需与偏好技能、skillRef、工作区/revision 和前置步骤。删除前置或调整顺序后提示修正依赖，不静默删除约束。
- 遵守 WorkflowPlanSpec 的长度、1–32 步、有序无环依赖和技能限制；服务端为最终校验，页面错误定位到字段/步骤。
- 流程图与可读表格展示同一组选定步骤、依赖和执行上下文。显示 Worker 能力匹配限制、无匹配及未知状态，匹配不等于执行保证。
- 保存草稿与确认执行分开。确认前完整预览，提交当前 version；AI 来源不授予确认权。草稿不创建业务执行任务。
- 草稿调整复用 FD-011 新建草稿机制。复制后在编辑器调整并另存，旧计划不变；不直接修改已确认图，提交失败保留未保存输入。

### 列表、详情与原 AR

- 列表显示目标、来源、状态、版本和时间，支持创建、查看、复制。
- 详情显示所选流程、实际任务/依赖、步骤指令、执行状态、可用执行记录与确认/收回审计。没有保存的事实明确未知，不推断批准或完成。
- 收回沿用 FD-011：存在 CLAIMED/RUNNING 时拒绝整个收回，否则收回非活动工作并保留完成结果；UI 明确失败原因且不改变原状态。
- 原 AR 详情标识“固定自主开发流程”，区分内置阶段说明与已经生成的真实任务图；不迁移已有 AR，包括用户提供的示例 Run。

### AI 接入与依赖 Gate

2026-10-13 用户确认采用现有只读 planner Worker 推荐边界。新增推荐开关默认关闭，且要求 EXTERNAL_WORK 已配置 AiProfileCatalog 策略；模型、凭据、预算与 attempt 期限由现有管理员配置控制，本 FD 不修改这些策略、不新增供应商连接。点击仅创建一个独立 subject=workflow-recommendation 的 EXTERNAL_WORK 分析任务，requiredSkills=implementation-planning，skillRef=implementation-planner，workspace/revision 为空。发送目标（推荐输入最多 8000 字符）及固定可信目录，不发送用户所选工作区或源码。浏览器提示可能产生既有模型费用。

推荐请求包含 UUID requestKey 和 inputVersion；使用确定性任务 ID 幂等创建，持久化关联和操作人放在既有任务指令信封，无 schema 变更。只读 GET 读取该任务及既有 CompletionReport，严格解析单个 JSON 对象 {requestKey,inputVersion,reason,plan}；关联不符、超限、非法图、未知技能或不符合执行上下文的候选返回可读失败，不能确认。候选 workspace 为空，应用后用户必须为代码/审查配置工作区；代码须完整 baseRevision，审查依赖代码产物且留空 baseRevision，沿用 TaskInput 结果传递。

页面每 5 秒轮询，最多等待 10 分钟；页面停止等待不等于终止分析任务，可手动刷新已有请求。Worker attempt 期限、取消和租约收尾沿用既有策略，不伪造全局期限；排队推荐可收回，CLAIMED/RUNNING 推荐拒绝收回，当前不强停。新点击生成新 requestKey；旧返回不能覆盖新目标/输入版本或手改。建议仅在主动应用后进入编辑器，业务草稿与确认仍完全分开。

若采用 Worker 推荐，显式点击只授权有界分析任务，须与待确认业务图区分展示；推荐分析可以执行，但推荐的编码等步骤必须等管理者确认后才能创建/领取。直接模型调用同样先明确预算和网络边界，不隐式启用。

FD-016 已完成本地交付并统一 EXTERNAL_WORK 的 Profile、skillRef 与结果契约。模板/AI 候选必须使用已落实的可信技能及结果策略，前置产物输入须能被后续步骤消费。定义已更新不等于 Worker 已可执行；发布前需要对应执行契约证据。

### 兼容、风险与发布

新增管理 UI、推荐 API、需求数据发送及超时/取消涉及权限/API/运行边界。沿用会话、Operator/Admin 和 CSRF；只读角色不能保存、推荐分析、确认或收回，浏览器或 AI 不能提高工具权限。

沿用 `aidev.flexible-workflow.enabled`。关闭时隐藏/禁用新入口并说明原因，原 AR 继续可用。保留 FD-011 JSON API 路径、语义与 CSRF 契约。不自动启用 AI 或执行迁移。优先复用持久化；若推荐关联确需 schema 变更，先修订迁移与回滚设计。关闭入口/回退 UI 不删除保存的计划和结果。

### UI 可信执行校验

新编辑器只支持 FD-016 可执行角色与精确 primarySkill 组合，不允许多个执行角色能力同列；允许合法技术技能。原 JSON 管理 API 的通用契约不变。新草稿/确认校验执行上下文：WORKSPACE 角色须 workspace 与完整 Git revision；VERDICT 角色须 workspace，代码前置存在时使用上游产物并留空 revision，不静默填入示例 commit。模板覆盖分析、分析→架构→规划、代码→审查→验证。未知 Worker 匹配明确为快照观察而非执行准入保证。新增端点沿用登录、Operator/Admin 与查询参数 CSRF；只读角色可读列表/详情但不能进入编辑或推荐。

## Files to Create/Modify

| 文件 | 用途 |
|---|---|
| workflow/application/WorkflowEditorService.java | 可信目录、模板、上下文校验与详情证据 |
| workflow/application/WorkflowRecommendationService.java | 独立推荐任务与有界结果 |
| workflow/protocol/http/WorkflowEditorController.java | 新 HTML 与管理接口 |
| templates/ui/plans*.html、static/ui/workflow-editor.js | 编辑、预览、推荐、列表/详情 |
| templates/ui/fragments.html、new-run.html、run-detail.html | 导航、固定模式和标识 |
| webconsole/protocol/http/WorkflowConsoleModelAdvice.java | 入口功能开关 |
| docs/current-zh/FLEXIBLE-WORKFLOW-PLANS.md | 操作与配置边界 |

## Scope

包含创建/编辑/预览/确认、推荐模板、真实 AI 推荐接入、列表/详情/复制/收回 UI、创建模式选择、原 AR 流程标识、必要页面读取接口及附近文档。

排除 Worker Runtime 改造（FD-016 负责）、历史 AR 自动迁移、运行中强制停止、自动扩展/修复流程、部署、默认启用 AI、依赖升级和本轮运行测试。用户已调用 Auto 并确认自行修复，授权隔离实施、独立审查、本地交付和归档；本轮不执行外部 AI 调用。

## Work items

每项为小（目标 ≤ 半日）；除标为低难度外均为中难度。1.2 为设计项；Gate 解决后细化其实施项，超过大小目标继续拆分。

- [x] 1.1 固定页面路由与交互。无依赖；小/低难度。HTML `/ui/plans`、`/ui/plans/new`、`/ui/plans/{id}`；新增 UI 读取/写入 `/ui/plan-editor/**`，保留 `/ui/workflow-plans/**` JSON API。复用 fragments/console.css，New Run 默认展示灵活入口，显式选择固定模式后展示原表单；功能关闭给出说明。
- [x] 1.2 明确 AI 推荐接入契约。依赖 1.1；小/中难度设计项。用户确认上述 Worker 边界，静态核对 FD-016 Profile、CompletionReport、AiProfileCatalog 和任务幂等 create；无未知外部直连接口。
- [x] 1.3 建立模板与可信技能映射。依赖 1.1、FD-016 契约；三个可编辑模板具备有效引用、输出/工作区依赖和适用边界。
- [x] 1.4 创建与模式选择入口。依赖 1.1；低难度。导航、需求/Workspace 输入、功能开关提示及显式固定 AR 模式。
- [x] 1.5 实现步骤字段编辑。依赖 1.3–1.4；增删/编辑、范围限制、未保存输入保留。
- [x] 1.6 实现依赖编辑与预览。依赖 1.5；顺序/依赖有效，图表同步，错误定位，无隐式补齐。
- [x] 1.7 接通草稿保存与复制编辑。依赖 1.5–1.6；新草稿不产生业务任务，错误可读，旧计划保留。
- [x] 1.8 接通版本确认。依赖 1.7；预览后单独确认，处理冲突/重复提交/权限错误，不重复派发。
- [x] 1.9a 实现推荐任务委派与关联。依赖 1.2–1.3；小/中难度。开关、策略、operator、幂等 UUID 信封、独立 subject 和只读 planner，无业务确认或新 schema。
- [x] 1.9b 实现推荐结果读取和收回。依赖 1.9a；小/中难度。结果有界、关联与可信图验证；排队可收回，活动任务明确冲突，无虚构终止。原 1.9 在实施开始前拆为 1.9a/1.9b，不遗漏边界覆盖。
- [x] 1.10 接通 AI 推荐页面。依赖 1.5、1.9b；理由和运行状态、过期响应隔离、主动应用、不丢失手改。
- [x] 1.11 实现计划列表与详情读取。依赖 1.8；低难度。真实步骤/状态/依赖/来源/审计，明确数据窗口与缺失信息。
- [x] 1.12 接通收回与匹配提示。依赖 1.11；显示能力匹配限制，活动任务收回冲突不改变事实，完成结果保留。
- [x] 1.13 标识原 AR 并更新文档。依赖 1.4、1.11；低难度。固定生命周期与已生成图区分，说明灵活创建与 AI 配置及限制。
- [x] 1.14 汇总静态与编译证据。依赖 1.3–1.13；低难度。Dual 实现报告记录实际检查与未运行项，再交独立 Reviewer。

## Acceptance

1. 创建入口可发现自定义、推荐模板、AI 推荐与显式固定自主模式；灵活提交不自动走完整 AR 链。
2. 单步分析可单独委派；模板可编辑或不用；执行图只含最终确认的步骤/依赖。
3. 输入需求并点击 AI 推荐后实际发起分析，展示对应建议与理由；导入 JSON 不算通过。失败/未配置时可手动继续，不直接启动推荐的业务工作。
4. 步骤/技能/上下文/依赖可编辑；非法图、引用、参数及超限被拒绝且错误可定位；图与表一致。
5. 草稿不产生业务执行任务；确认当前版本才创建选定图；冲突不丢输入，重复提交不重复创建。
6. 可查看来源、步骤、状态、真实依赖与审计；复制为新草稿；活动任务收回失败不改变原图。
7. 迟到推荐不覆盖新输入/手改；Worker 无匹配及未知状态明确显示，不虚构执行保证。
8. 未授权角色/无效 CSRF 拒绝写操作；功能关闭/AI 不可用可理解；推荐不扩大权限。
9. 原 API、AR、Pull/lease 契约保留，原 AR 标识固定流程；UI 沿用 Web Console 视觉与交互。
10. 模板/推荐引用与 FD-016 已落实执行契约一致；依赖产物能传到后续步骤，不只连接图线。

## TODO

1.1–1.2 设计已完成；1.3–1.14 源码、UI 与附近文档已在 feature/FD-017 实施并提交。R1 独立 Reviewer 请求两项修改，已针对性修复并重新交审；未宣称运行验收。2026-10-13 恢复：用户确认自行修正；PM 受管取消旧 Planner 收据并 force-emit 新 design-requested，不把恢复视为设计/实施验证；当前 Session fd017-auto-20261013-df9cdf16，来源 FD-017-000003-design-requested。

## Verification

- 独立 Reviewer R2 Session `fd017-reviewer-r2-20261013-a94d63` 领取 `FD-017-000007-implementation-ready`，复核 `8f4204e` 和 R1 两项修复后记录 [FD-017-review-r2.md](reviews/FD-017-review-r2.md)：静态 `verification-passed`；运行与浏览器检查未执行。
- 独立 Reviewer Session `fd017-reviewer-20261013-7c9e41` 领取 `FD-017-000005-implementation-ready`，静态审查 `dbe72bc` 后记录 [FD-017-review-r1.md](reviews/FD-017-review-r1.md)：`changes-requested`，待修复严格单对象 JSON 校验与创建中目标变更的过期请求入口。未运行测试或运行检查。
- Worker 修复来源 `FD-017-000006-changes-requested`：Jackson 开启尾随 token 拒绝；目标或步骤变动时标记当前请求 stale，迟到 POST 结果保留任务 ID 和单独查看/收回入口，多请求历史在本页保留，不将旧结果应用到新输入。修复后 `mvn -o -q -DskipTests compile`、`node --check src/main/resources/static/ui/workflow-editor.js`、`git diff --check` 均退出 0。未运行 HTTP、浏览器或真实模型。

- 设计静态证据：FD-011、已归档 FD-016、ISSUE-00001、WorkflowPlanSpec、管理 API、Worker 可信角色/结果契约与现有页面。Planner 当前 source event FD-017-000003-design-requested；Worker source event FD-017-000004-design-ready，Session fd017-auto-20261013-df9cdf16。
- 实施静态追踪：WorkflowEditorService 角色/技能/工作区/前置结果校验→新草稿/版本确认；WorkflowRecommendationService 独立只读任务→CompletionReport→关联/长度/可信候选校验；新页面 GET/POST、Web Console 会话/URL CSRF 与 textContent 输出、原 API 路径。对照现有 Worker profiles 和 TaskInput 结果路径。最终 `git diff --check master...HEAD` 初次无空白错误；最后两处源码/JS 修正已审阅但尚未经再次命令检查。
- 已执行 `mvn -o -q -DskipTests compile` 两次，均退出 0；第一次覆盖服务与页面端点，第二次覆盖当时全部 Java 源。第二次后又收紧同工作区结果依赖及 JS stale 状态，未作第三次编译。离线，无依赖下载或发行包。Maven 不检查浏览器 JS 行为。
- 浏览器、HTTP、真实模型、迁移、并发/期限/取消运行检查须另行授权。可选 focused 场景：单步委派、迟到推荐、非法依赖、确认冲突、权限拒绝及活动任务收回。
- 本轮未运行应用、测试、网络/真实模型、数据库迁移、浏览器、格式/lint/type 检查或最终发行构建；未发布。静态与 Java 编译证据不代表运行通过。

## Sources

- 当前用户请求：创建 FD 处理 UI；前文明确自定义流程、推荐选项和 AI 实时分析推荐。
- 产品背景：[ISSUE-00001](../issues/ISSUE-00001/issue-plan.md)。按本轮直接请求创建，不改写 Issue 已有 FD 关联。
- [FD-011](archive/FD-011/FD-011_WORKER.md)、[FD-016](archive/FD-016/FD-016_FD_011_WORKER.md)。
- `src/main/java/me/asu/aidev/workflow/domain/WorkflowPlanSpec.java`、`application/WorkflowPlanService.java`、`protocol/http/WorkflowPlanController.java`。
- `src/main/resources/templates/ui/new-run.html`、`fragments.html`、`run-detail.html`；[管理说明](../current-zh/FLEXIBLE-WORKFLOW-PLANS.md)。
- Planner Session：`fd017-planner-20261013-b5a8f2`；source event：`FD-017-000002-design-requested`。

**Completed:** 2026-10-13
