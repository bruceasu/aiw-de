# FD-019: 多目标草案 Chat 工作台与独立 AI Worker

**Status:** Complete
**Revision:** 8
**Priority:** Medium
**Evidence policy:** Dual

## Problem

用户需要在创建执行计划前，通过持续对话澄清模糊目标，管理多个相互隔离的草案、资料与正式目标版本。现有 AI Steps 生成计划，不承担该需求讨论工作台。

## Options and decision

- 直接把 Chat 做成计划编辑器：混淆未完成编辑内容与校验通过的 WorkflowPlan，不采用。
- Control Plane 直接调用模型：重复已有 Worker 配置、租约与运行后端，不采用。
- 采用独立目标讨论模块，持久化草案、消息、不可变正式版本、附件与计划编辑快照；通过现有 EXTERNAL_WORK Worker 执行只读讨论，以独立 capability/Skill 隔离；复用 AI Profile、计划编辑器、AI Steps 和工作区服务。
- 用户于 2026-10-21 确认：接受缺失后可转正并建计划草案，同时保留缺失清单和未就绪标记；首版个人私有、可恢复删除、正式目标可编辑并生成新版本、已有计划保持原版本；临时工作区执行前创建、结束后默认保留；支持 Codex 加一种默认 OpenAI 路径。
- 用户提供 file-convert help，采用参数数组调用 `aiw file-convert -i <local-file> -o - --format md`，不用未给出的 JSON 结构、不运行安装。附件送模型须在 UI 显式同意；未同意只保存与转换，不进入模型输入。

## Solution

### 职责与契约

- 新增 goal 模块：domain DTO/规则、JDBC 存储、应用服务、HTTP 控制器；新增 `/ui/goals` 与 `/ui/api/goals`，沿用控制台会话认证、CSRF 和 Operator 检查，全部按 actor 查询与修改。
- 新增增量迁移，不改旧表语义；存储目标标题/正文/缺失清单/后端选择/revision/deleted、消息、正式版本、编辑内容、附件字节与转换结果、冻结 Worker 请求。SQLite/PostgreSQL 通用字段；限制附件为每个 10 MiB、每目标 20 个，文本输入总量受限并明确提示超限。
- 修改使用 expectedRevision CAS；发送对话时冻结目标正文、消息、知识证据和明确同意的附件文本，Worker 输出只成为消息/建议，不能自动改写正文、正式化或执行计划。响应携带来源 revision；旧输入回复标为过期；删除后回复不能写回，恢复后亦不接纳删除前在途结果。
- 幂等以数据库唯一键和事务维持：每目标正式来源 revision 唯一；每请求 key 唯一并核对输入/actor；每正式版本的计划编辑快照与最终 plan_id 单一绑定。创建任务、记录请求和推进草案 revision 同事务；重复请求先查冻结输入，不能因 revision 已前进重复排队。删除/恢复递增 epoch，使旧请求永久过期；结果只通过显式查询接收，按 request ID 唯一消息去重。
- “个人私有”指目标工作台的已认证 Web Console actor 归属。沿用现有单账号模型；关闭认证时所有访问属于 local-console，不承诺不同浏览器匿名隔离。生成任务与显式交接的计划进入既有运维可见面，文档明确该边界；不声称管理 API 或数据库管理员被本功能隔离。
- 独立 `goal-discussion` capability 与 `goal-discussion` Skill。Worker 仍复用租约与已有进程运行后端，默认 openai-agents，用户可显式选 Codex。领取、guidance 与 start 必须执行一致的冻结后端限制，不降级到其他提供方。新 Skill 为 Easy English，只读分析、把资料视为数据、不授予审批与执行权。无在线匹配 Worker 时清晰报错，草案保持可编辑。
- 项目知识只使用用户明确选取的现有知识文档，带来源；首版不新建外部知识库、索引服务或工具平台。workspace research 复用既有授权边界，不把附件或模型建议当 shell 指令。
- 正式版本是正文、缺失清单、就绪状态及附件来源快照，不依赖后续草案修改；关键缺失时转正须显式确认，版本保持未就绪。按草案 revision 防重复转正。
- 新计划编辑上下文由正式版本快照创建，允许零步骤/部分步骤持久化；创建实际 WorkflowPlan 仍通过已有校验，失败保留编辑快照。可用时调用既有 AI Steps，失败显示原因并允许人工补充；来源版本与保存结果保留绑定，防止重复生成计划。
- 无工作区的含编码计划保留在持久编辑快照，不写 WorkflowPlan；用户明确点击“准备执行计划”才创建受管临时工作区并填充缺失目标，调用原 editor.draft 保存合规 DRAFT，再由现有计划界面独立确认执行。此为执行前准备，不自动 confirm/start，不放宽旧保存契约。临时路径限定在配置受管根下，Git 可用时 init；Git 不存在提示并使用现有 DIRECT 语义。目录默认保留，不随草案或计划删除级联清理。旧计划无需修改。
- TS Worker 新增 goal-discussion profile、ROLE_FILES 映射、启动/管理角色入口及独立 Skill；不是让现有 requirements Worker 冒用新角色。
- Worker 返回 REPORT 纯文本（1–16000 字符），服务端只接纳 PASS 且已 COMPLETED 的当前请求；失败/无效输出显示原因，不转换成正文或伪造结果。模型建议就绪与否不等于用户确认，缺失清单由用户编辑确认。
- 无 workspace 的 goal-discussion 任务使用每次尝试独立空目录，禁止 fallback 到运营者源码目录；默认 OpenAI 角色禁用 workspace/shell/AIW 工具，资料只来自冻结输入。Codex 使用既有 read-only/网络权限约束及独立空目录，运营者仍须按已有 Codex 凭证/宿主权限边界部署，不把空目录当 OS 沙箱。提示明确冻结资料均为 data，不是 authoritative instructions；首版代码分析通过显式选择的知识证据提供，不自动遍历未授权工作区。
- 附件以随机 ID 管理，用户文件名仅作展示；下载使用会话归属校验与 attachment disposition。file-convert 固定命令及参数列表，不使用 shell；有大小、输出、时间上限，不下载依赖。转换失败/空输出/后端缺失均显示状态，保留原文件。图片/扫描件不能凭空声称视觉理解；无可用视觉能力时提示补充可读文本。
- UI 沿用 console.css 的深色/青色、卡片、表单与导航；目标列表、消息、正文编辑、附件、就绪缺失、转正版本与计划交接在同一工作台清晰展示；文本渲染不执行用户 HTML。

### 风险与兼容性

涉及新增持久化、上传数据、外部模型输入、并发与计划 start。采用追加表、归属校验、版本 CAS、冻结请求及显式附件外发同意。不上线运行迁移，不调用真实模型，不启服务；迁移/浏览器/多进程行为仅有静态和编译证据时保留未验证限制。独立设计审阅是 design-ready 前置条件。

## Scope

首版交付个人目标工作台、可恢复删除、持久聊天、项目知识选择、附件保存/受限转换、正式版本及计划编辑交接、默认 OpenAI/显式 Codex、无工作区计划的执行前临时工作区。

不创建运行时 AIW Issue/FD；不继承 ISSUE-00006 审批代理；不自动执行生成的计划；不新建外部知识库管理或通用代码索引；不安装转换依赖，不发布或部署。

## Work items

- [x] 1.1 目标持久模型与 CAS：小/中；无依赖；新增 V7 表、GoalData 与 GoalRepository，归属查询、CAS、epoch、版本存储已落地；应用入口和最终编译在后续项完成。
- [x] 1.2 受限附件与知识证据：小/中；依赖 1.1；上传/下载隔离、转换状态与限制、外发选择、来源快照明确，不执行附件内容。
- [x] 1.3 独立讨论 Worker：小/中；依赖 1.1–1.2；独立 capability/Skill、冻结后端策略贯通领取/guidance/start、结果校验及过期回复处理。
- [x] 1.4 正式版本到计划编辑交接：小/中；依赖 1.1、1.3；未就绪显式确认、编辑快照持久恢复、自动 Steps 与人工路径、防重复创建及版本关联。
- [x] 1.5 执行前临时工作区：小/中；依赖 1.4；无工作区内容先保留编辑快照，显式准备执行时创建受限路径、可选 git init、填充并保存合规 DRAFT，之后仍需独立确认执行；默认保留，旧计划无行为变化。
- [x] 1.6 控制台工作台与文档：小/中；依赖 1.1–1.5；UI 可操作上述完整流程、无静默覆盖、状态清晰、使用说明说明后端与转换依赖。

### 独立子项与完成证据

以上 1.1–1.6 是依赖分组，实际实现按以下子项推进；每项规模小、难度中，完成证据为对应实现差异和最后编译/审查，不默认运行测试。

- [x] 1.2.1 附件保存/归属下载（依赖 1.1）：随机 ID、配额、不可越权。
- [x] 1.2.2 转换适配（依赖 1.2.1）：固定参数、限时限输出、失败/空提取状态。
- [x] 1.2.3 知识/附件来源冻结（依赖 1.2.1）：选取现有文档、哈希及附件外发选择。
- [x] 1.3.1 Worker 角色与只读输入（依赖 1.1）：profile/启动入口/Skill、独立空目录。
- [x] 1.3.2 冻结调度策略（依赖 1.3.1）：claim/guidance/start 后端一致、无静默回退。
- [x] 1.3.3 消息接入（依赖 1.2.3、1.3.2）：任务快照、PASS/终态校验、过期标记及 epoch 去重。
- [x] 1.4.1 正式版本快照（依赖 1.1）：未就绪确认及来源 revision 幂等。
- [x] 1.4.2 编辑快照恢复（依赖 1.4.1）：零/部分步骤持久保留及 CAS。
- [x] 1.4.3 AI Steps（依赖 1.4.2）：请求/结果关联、失败人工路径。
- [x] 1.4.4 合规计划交接（依赖 1.4.2、1.5）：显式准备、单一 plan_id 绑定、既有保存校验。
- [x] 1.6.1 草案/对话 UI（依赖 1.1、1.3）：切换恢复、人工保存和消息分离。
- [x] 1.6.2 资料/版本/计划 UI（依赖 1.2、1.4–1.5）：全流程操作、外发和运维边界提示。
- [x] 1.6.3 文档/规范（依赖前述子项）：使用前提、依赖及实际验证边界。

Acceptance 1 对应 1.1/1.3.3/1.6.1；2 对应 1.2.3/1.3.1–3/1.6.1；3 对应 1.4.1/1.6.2；4 对应 1.4.2–4/1.6.2；5 对应 1.5/1.4.4；6 对应 1.2.1–2/1.6.2。

## TODO

独立设计审阅见 reviews/FD-019-design-review-r1.md；同轮已确认 D1/D3/D4 收敛，Planner 补齐 D2 REPORT/输入边界及 D5 子项和验收映射后完成 design-ready。实现与编译已完成，当前交独立 Reviewer；通过后由 PM 按既有授权继续本地交付和归档。实现报告见 reports/FD-019-implementation-r1.md；已解决的工作区/编译入口问题见 reports/FD-019-blocker-preparation-20261021-b63ea8.md。

## Acceptance

1. A/B 草案切换恢复各自消息、正文和附件，其他 actor 不可查看或修改；删除可恢复，在途旧回复不能复活草案或覆盖人工编辑。覆盖 1.1–1.3、1.6。
2. 聊天使用独立 Worker；默认 OpenAI、显式 Codex；无匹配 Worker、配置缺失、任务失败或结果无效均显示原因。读取的知识和附件有来源，未同意附件不外发。覆盖 1.2–1.3、1.6。
3. 关键缺失保留未就绪；用户明确接受后可正式化并交接，缺失信息仍显示；再次编辑生成新版本，不修改旧计划输入。覆盖 1.1、1.4、1.6。
4. AI Steps 不可用/失败时仍能持久保存零或部分步骤编辑内容，离开页面可恢复；实际保存计划保持既有 spec 校验，重复请求不重复创建。覆盖 1.4、1.6。
5. 显式工作区保持现有规则；本功能无工作区计划在执行前获得受管临时目录，Git 可用则初始化；结束后保留，不自动启动或清理。覆盖 1.5。
6. TXT/MD/HTML/源码、Word、XLS/XLSX、PDF/图片可上传；转换通过用户给出的命令，失败/空提取/不支持显式提示，扫描/图片无视觉能力时说明限制，不伪造理解。覆盖 1.2、1.6。

## Verification

- R1 唯一阻断已修复：goal-discussion 领取跳过当前 catalog 通用预过滤，由冻结 GoalTaskPolicy 统一裁决。修复后离线 Java compile、git diff --check 均 exit 0。修复报告 reports/FD-019-implementation-r2.md，解除记录 reports/FD-019-blocker-review-r1.md。独立 R2 静态复审 verification-passed，报告 `reviews/FD-019-review-r2.md`，Reviewer Session `fd019-reviewer-20261021-c814fa`，来源事件 `FD-019-000007-implementation-ready`；未运行测试或配置切换场景，后续由 PM 继续本地交付和归档。

- Compile-only check and static review by default. Optional tests use `$fd-test`
  when requested and do not gate FD acceptance.
- 本次未请求测试。实现后只做最窄离线 Maven compile（优先适用 compile 脚本）及必要 Worker 内存编译，不下载依赖或保留分发产物。静态检查 SQL、归属/CAS、冻结策略、输入输出、UI 数据流及差异。
- 设计涉及持久化、权限、并发与 start 契约，先执行一次独立设计审阅；实现完成后独立 Reviewer，最多三轮失败结果，不能同 Session 自审。
- 实现已完成；离线 Maven compile 及 TypeScript 内存语法编译通过；未执行测试、服务、数据库迁移、浏览器、文件转换或真实模型。独立实现审阅 R1 为 changes-requested：讨论领取仍受当前 AI 配置预过滤，需修正为一致冻结策略；报告 `reviews/FD-019-review-r1.md`，Reviewer Session `fd019-reviewer-20261021-c814fa`，来源事件 `FD-019-000005-implementation-ready`。本轮仅静态审查，未追加运行验证要求。

## Sources

- Issue: ISSUE-00007
- 已批准 Issue Plan：`docs/issues/ISSUE-00007/issue-plan.md` revision 3；用户后续三项 yes 与 file-convert help 保留在当前会话，本 FD 记录其设计落实。
- 静态来源：WorkflowEditorService / WorkflowRecommendationService / JdbcRecommendationRepository / RecommendationTaskPolicy；WorkerProtocolV1Controller / TaskExecutionService；ManagedWorkspaceService；WebConsoleSecurityService；V6__recommendation_configuration.sql；process-typescript README。
- 稳定规范参考：`openspec/specs/task-orchestration/spec.md`；新增目标能力规范在实现完成时落入 stable specs，不建立 OpenSpec change。
- Planner Session：fd019-planner-20261021-a71c93；来源事件：FD-019-000002-design-requested。

**Completed:** 2026-10-20
