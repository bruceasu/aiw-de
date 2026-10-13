# FD-002: Run Mission Detail and Timeline

**Status:** Deferred
**Revision:** 18
**Priority:** Medium
**Test policy:** Independent
**Evidence policy:** Dual

## Problem

对应 REQ00001 的 M2。操作者需要在一个 Run 上下文中理解需求、任务依赖、执行者、重试/修复、失败原因、时间线及可用操作。当前 Run Detail 已有 dashboard、任务表与最近 timeline，但没有真实依赖图，任务指令阅读和失败/恢复证据的关联也不充分。

2026-10-04 用户授权先为剩余里程碑生成 FD；2026-10-05 用户请求 `$fd-workflow FD-002 auto $implement`，并明确决定“最近 300 条，明确显示总数和截断”。该决定解决 M2 历史范围 Gate。REQ00001 已有的正式 FD 关联为 FD-001；本 FD 使用显式来源链接保留 M2 归属。

## Options and decision

| 方案 | 判断 |
|---|---|
| 只扩充任务表与文本时间线 | 适合作为无图形时的可访问回退，单独采用不能满足真实依赖表达 |
| 沿用 Thymeleaf，在 Run Detail 组合摘要、真实依赖图、任务/attempt、失败/恢复与有限时间线 | 推荐；复用路由、权限与 FD-001 共享视觉，新增私有只读投影 |
| 新增 Mission 实体、独立任务页面和完整历史系统 | 不作为默认方案；会改变领域/数据范围 |

采用第二种方案。Mission 仅为一个真实 Run 的展示用语，保留 Run ID，不新增持久化实体。图形使用仓库内 SVG/HTML，提供表格回退；不能照搬 handoff 中的静态流程示意图。

## Solution

页面组织：需求和 Run 原始状态/控制状态 → 当前阶段与已有动作 → 实际任务依赖/执行 → 失败与修复 → 时间线。任务/attempt/Worker 使用真实 ID 联结；没有读取依据的字段显示不可用。

新增 webconsole 私有 Mission 详情 DTO、JDBC 读取和只读组合服务，复用 `WebConsoleQueryService.runDashboard`、`AutonomousRunDashboard`、`AutonomousRunTimelineService`。权威映射如下：

| 内容 | 权威来源 | 缺失/范围 |
|---|---|---|
| 原始需求 | autonomous_run.requirement_task_id → work_task.instruction | 保存的关联 ID；关联不存在或归属异常明确 unavailable，不使用第一条 task 代替 |
| 状态、阶段、计数 | AutonomousRunDashboard | lifecycle/control/effective 分别展示；当前 completed/total 无 ETA |
| 任务 | work_task 的 subject_type=AUTONOMOUS_REQUIREMENT、subject_id=runId | 最近 200 个，按 createdAt/id 稳定选取，再按正序展示；总量来自完整 Run |
| 依赖 | work_task_dependency.task_id / depends_on_task_id | prerequisite → dependent；属于展示 task 的边最多 1200 条，单批查询，无逐 task 查询 |
| 历史执行 | execution_attempt.task_id/attempt_no/worker_id | 最近 300 个 attempt，先按真实 Run 归属过滤；总量、返回数、截断明确 |
| 失败/人工恢复 | autonomous_run_event FAILED/RECOVERED 的 related_task_id/actor/detail/occurredAt | 最近 20 条及总数，另读取最新 FAILED；没有 attempt/Worker 关联时不补造 |
| 终止/执行结果 | attempt.state、created/started/finishedAt、termination_reason、failure_category、completion_summary | 原因缺失明确；summary 标为报告摘要，不冒充根因 |
| 时间线 | 原 TimelineResult | 最近 300 条，totalEntries/returnedEntries/truncated，原排序不变 |

依赖读取同时给出完整 Run 边总数和展示 task 的候选边数。前置 task 在窗口外时保留真实 ID；跨 Run/subject 关联明确标记，不创建伪任务。task/attempt/Worker 使用稳定安全锚点和原 Fleet 链接；窗口外 task 不链接不存在的锚点。SQL 参数化，所有新查询均只读。

图采用 Kahn 分层、稳定 task 排序和左到右 SVG，节点仅来自展示 task，边仅来自保存的依赖；TaskType 和列表顺序不生成边。有循环/不可分层数据时明确异常，真实依赖表仍可阅读。边截断或窗口外关系明确部分图，不能宣称完整 DAG。200 节点的密集图在内部滚动，不撑宽页面；表格支持完整 ID、状态和依赖阅读，图可关闭，状态文字不只依赖颜色。

图必须覆盖单实施、并行实施→integration→最终 review/integration verification、repair 新增任务的真实关系。显示已完成/当前任务数与阶段，不承诺 ETA。租约过期自动恢复与显式失败后人工 recover 分别表达。pause/resume/recover/cancel 沿用既有 action controller，保留 CSRF、权限、竞争反馈和不可逆提示。

用户已确认有限时间线为最近 300 条；显示总数、返回数、截断及首尾实际时间。类别/文本过滤只针对返回窗口，不冒充完整历史搜索；按 UTC 日期分组，原 occurredAt 排序保持。展开全文使用稳定事件 key，缺失来源字段明确未记录。现有 TimelineService 先读取单 Run 全部投影再排序截断，输出上限不是 SQL LIMIT；本期不宣称读取成本已经有界或性能已验证。

自动租约恢复会 requeue 并把 attempt 改为 EXPIRED，但不保存 termination_reason；pause/cancel 也产生 EXPIRED，并保存 OPERATOR_PAUSED/OPERATOR_CANCELLED。页面说明既有自动恢复机制，具体记录展示保存状态/时间/原因，不能仅从 EXPIRED 或空原因断言自动恢复。后续更高 attemptNo 可展示实际重试，不把其创建时间当恢复时间。人工 recover 依据 RECOVERED 事件展示 actor/time/detail。

详情复用 FD-001 已提交的战术组件和局部刷新：5 秒更新只读区域，操作表单和过滤控件在更新区外，保留焦点、选区、展开和滚动；过滤条件在替换后重新应用。动作入口来自页面加载时快照，标注需重新载入更新可用动作；提交由原服务重新检查。保留旧 `Autonomous run authority` 文案兼容现有测试。FD-001 已通过独立审查并归档，本 FD 仍须独立验收。

实施文件：新增 WebConsoleMissionDetail/JdbcWebConsoleMissionReadRepository/WebConsoleMissionQueryService，修改 WebConsoleController 的 Run GET 和 `templates/ui/run-detail.html`，新增 `static/ui/mission.js` 和 console.css 的局部样式，更新中英文 Web Console 指南。仅补充读取，不改变 task state、依赖生成、重试或 repair 权威。若发现需要 schema/公开 API 改变，返回设计决策，不能自动扩展。

## Scope

包含需求上下文、真实任务与依赖、执行者/attempt、失败与恢复、时间线阅读和已有 Run 操作体验；复用 FD-001 的视觉与刷新基础。

不包含首页/Fleet 重构、审批证据浏览器、完整历史保留策略、新 Mission 实体、新生命周期、Request Repair 新动作或 Worker Runtime 改写。M3/M4 各自由 FD-003/FD-004 承担。

## Work items

- [x] 2.1 私有 WebConsoleMissionDetail/JdbcWebConsoleMissionReadRepository/QueryService 已实现；字段来源见 Solution，Run 过滤先于 LIMIT，窗口总量和返回数分开。
- [x] 2.2 详情页展示完整需求、独立 lifecycle/control/effective、阶段、任务汇总及原动作；长文本转义输出，权限条件和 CSRF 沿用原契约。
- [x] 2.3 图由保存的 dependency 经 Kahn 分层生成，真实边/循环/窗口外/跨 Run 说明及完整任务表已实现；task/attempt/Worker 关联采用保存 ID。
- [x] 2.4 最新 FAILED 与最近 20 条 FAILED/RECOVERED 独立读取；attempt 原因/时间保留，自动机制与人工恢复分别说明，不补造事件关联。
- [x] 2.5 最近 300 条、总数/返回数/截断/首尾时间、UTC 分组、类别/文本过滤、稳定展开 key 已实现。
- [x] 2.6 复用共享局部刷新；表单/筛选在更新区域外，新增 mission.js 保持筛选、图开关和内部滚动；中英文 Mission 指南和 Dual Worker 报告已准备。

依赖：FD-001 已提交的共享基础；2.1 先于 2.2–2.5，2.6 最后。上述勾选表示实现与静态证据已完成，不表示独立测试或验收通过；编号保持稳定。

## Acceptance

| 编号 | 原需求 | 验收 |
|---|---|---|
| AC01 | R02、A04 | 需求、Run 生命周期/控制状态、阶段与真实任务计数可分别读取，不显示假 ETA |
| AC02 | R04、A05 | 依赖图匹配真实边；并行集成先于最终审查/集成验证；repair 动态变化不会丢失历史 |
| AC03 | R05/R07、A06 | 失败原因关联 task/attempt/Worker 与时间，租约自动恢复与人工 recover 可区分 |
| AC04 | R05、A07 | 有限历史明确总数、返回数、截断与查看范围，缺失关系不补造 |
| AC05 | R09、A09 | 已有动作权限、CSRF、不可逆提示与状态竞争反馈正确 |
| AC06 | R10/R11、A11/A12 | 长 ID/需求、空态/异常态、窄窗口、减少动效与阅读/编辑保持符合共享基础 |

## Verification

Planner 已完成权威字段、归属、依赖边、attempt 和恢复记录的静态核对，用户历史范围决定已记录。Planner Session：`fd002-planner-20261005-96860f94`，来源事件 `FD-002-000004-decision-recorded`。

Worker Session：`fd002-worker-20261005-88571fc5`，来源事件 `FD-002-000005-design-ready`。2.1–2.6 的代码/指南已实现。2026-10-05 以 JDK 21 执行一次 `C:/green/apache-maven-3.9.9/bin/mvn.cmd -o -DskipTests compile`，178 个源文件，BUILD SUCCESS，9.413 秒；原始输出 `.ai/fd/FD-002/compile-r5.txt`。未执行测试、Thymeleaf 渲染、浏览器、网络、格式器或最终构建。编译通过不证明页面交互通过。

后续按仓库预算做静态来源/边映射检查和一次最窄 compile-only 检查。独立 Tester/Reviewer 遵循 Independent/Dual policy；具体测试和浏览器命令需先记录授权，不由此草案授予执行预算。

拟定场景：单实施、并行集成、repair、失败/租约过期、暂停/恢复/取消、无任务、长指令、截断时间线和过期页面操作；增加 201 task、1201 依赖、301 attempt、循环、窗口外/跨 Run 依赖、长 ID 和空关联。视觉尺寸沿用 FD-001，另覆盖密集图与表格回退；测试和浏览器授权不得沿用 FD-001 的单次预算。2026-10-05 用户已单独批准 [FD-002 的 Firefox 检查延期](reports/FD-002-firefox-decision-r6.md)，本轮 Chromium 仍验证四宽度，Firefox 的两项库存保留为未运行和兼容风险，页面行为要求不变。

独立 Tester 已执行三次精确命令，当前不同场景通过 36/104（34.62%）、0 个已执行业务失败、60 项计划内阻塞及 8 项未覆盖；原始证据与 [第三次报告](reports/FD-002-test-report-r6-attempt3.md) 保留。PM 已按 [r7 决定](reports/FD-002-test-decision-r7-v1.md) 拒绝不完整报告，Worker 真实认领 `FD-002-000008-test-rejected`。临时 JVM 内存和连接清理已修正；内容章节定位器现逐一匹配公开语义标题及关联内容，排除当前 URL 的 Run 身份标题，全部候选无关联才失败，业务断言不变。未将测试错误归为产品故障，不累加三次重复成功，也不移除库存分母。下一次运行预算需要单独记录，不由恢复交接自动授权。

Worker 恢复修正见 [r8 报告](reports/FD-002-implementation-recovery-r8.md)：图滚动位置改为保存可见视口的最后位置，避免从隐藏或已移除节点读取零值；窄卡片的长阶段文字可折行。2026-10-05 再执行一次相同离线 compile-only 命令，BUILD SUCCESS，1.136 秒，Java classes 已是最新，资源已复制；证据 `.ai/fd/FD-002/compile-recovery-r8.txt`。修正后未执行新测试或浏览器，不能以编译代替交互证据。

r9 的新增一次复验和一次相关资源修正重试均已按用户批准执行。初验临时 JVM native 内存中断，仅 3/104 通过；条件重试实际执行 78 项，74 通过、4 失败、26 未运行，需求覆盖 71.15%，分支未测。四个失败为 attempt ID 窗口计数、时间线文本/类别筛选和刷新过滤；失败截图显示总量 7/7 但匹配 0/0 且条目不可见。Node 随后因 semi space 提交失败退出，四尺寸/图滚动/后续动作等 18 项计划内未到达；8 项原未覆盖保持。PM 依据 [r10 决定](reports/FD-002-test-decision-r10-v1.md) 拒绝，Worker 已领取 `FD-002-000011-test-rejected`。原五次报告与快照全部保留，不累加不同轮次成功。

Worker 将时间线条目的空表达式标记改为静态 `data-timeline-entry` 属性，类别继续由已保存数据生成，使筛选脚本有明确条目目标。独立测试观察器将改为逐行核对完整 attempt ID 集合及祖先可见性，并记录实际类别值；Node 调用将限制 semi space/old space，仍只影响本地测试环境。恢复修正未获得新运行证据；用户本次两次预算已用尽，下一 Tester 命令仍需新的明确授权。

本次 Worker 恢复及后续测试准备见 [r11 报告](reports/FD-002-implementation-recovery-r11.md)。2026-10-05 执行一次相同最窄离线 compile-only，BUILD SUCCESS，0.802 秒，Java classes 已最新、资源已复制，证据 `.ai/fd/FD-002/compile-recovery-r11.txt`。没有新模板渲染或浏览器结果。

r12 用户另行批准一次初验及一次相关修正重试，两次均已实际执行。初验 3通过、0业务失败、101未运行，CDP 在登录页超时；新增测试 Chrome GPU 限制和复用唯一初始页面后，条件重试完整执行96项，93通过、3失败、8未运行，需求覆盖89.42%，分支未测。[最新独立报告](reports/FD-002-test-report-r12-attempt2.md) 与全部七轮快照保持，不累加历史成功。完整 attempt ID 集合、时间线文本筛选、390/768/1280、真实控制/CSRF/刷新阅读保持等有本次断言证据；两个类别筛选/刷新断言在控件定位抛错前未完成，1440截图矩阵因capture失败未完成。现有截图可见类别控件，不能将定位器零候选写成产品缺失。

PM 已依据 [r13 决定](reports/FD-002-test-decision-r13-v1.md) 拒绝，Worker 真实领取 `FD-002-000014-test-rejected`。当前具体恢复为独立观察器从语义label中排除嵌套控件选项文本、保存所有select候选及排除依据，以及按真实可见视口逐屏截图并恢复滚动位置；严格类别/集合断言和全部纵向矩阵保持。静态依据与交接见 [r14 报告](reports/FD-002-implementation-recovery-r14.md)。这些测试修正尚在独立Tester准备阶段，不能写成通过。本次为验证记录与交接，无新增生产源码修改或重复编译；最近源码编译证据仍为r11。

## TODO and design gates

两个设计 Gate 均已解决：历史采用用户确认的最近 300 条；读取与图方案按上表和真实源码确定。Worker 实现已完成，下一步为独立 Tester，禁止将尚未运行的检查写成通过。

残余风险：原时间线全量读取成本；最新两个类别观察失败与1440完整截图未完成仍需相关修正后的实际复验；8项原未覆盖不能视为通过。系统commit/Chrome截图资源问题可能再次中断，测试资源设置不能代替生产性能验证。Firefox已单独延期，分支覆盖未测。FD-001归档复验不替代本FD。七次运行的既有预算均已使用；下一次命令须在具体修正、静态审阅和真实新Tester事件绑定完成后取得新的人类授权。

## Sources

- Issue: none
- Source requirement: [REQ00001-web-control-plane](../../../issues/ISSUE-00002/issue-plan.md)，M2；[差距分析](../../../issues/ISSUE-00002/current-state.md)。
- CLI 的 `--issue` 目前只允许一个 FD 正式关联同一需求；此处 `Issue: none` 表示没有新增正式绑定，不表示没有需求来源。
- 依赖：[FD-001](archive/FD-001/FD-001_MISSION_CONTROL_OVERVIEW_AND_SHARED_VISUAL_FOUND.md) 已通过独立审查并归档；同需求 M3/M4：[FD-003](FD-003_WORKER_FLEET_AND_SCHEDULING_EXPERIENCE.md)、[FD-004](FD-004_APPROVAL_AND_ENGINEERING_EVIDENCE.md)。
- 来源事件：`FD-002-000002-design-requested`；2026-10-04 用户要求剩余里程碑先生成 FD。
- [用户历史范围决定](reports/FD-002-history-decision-r3.md)；`FD-002-000004-decision-recorded`。
- [Worker 实施报告](reports/FD-002-implementation-r5.md)；[Worker r8 恢复报告](reports/FD-002-implementation-recovery-r8.md)；[Worker r11 恢复报告](reports/FD-002-implementation-recovery-r11.md)；[英文指南](../current/WEB-CONSOLE-RUN-MISSION.md)；[中文指南](../current-zh/WEB-CONSOLE-RUN-MISSION.md)。
- [Worker r14 恢复交接](reports/FD-002-implementation-recovery-r14.md)；[r12 最新独立测试](reports/FD-002-test-report-r12-attempt2.md)；[r13 PM 拒绝决定](reports/FD-002-test-decision-r13-v1.md)。

**Closed:** 2026-10-05
**Disposition reason:** 用户将自行测试，要求本轮结束 FD-002；测试未执行，验收未完成。保留现有未执行报告、历史失败证据及用户延期验证决定，后续如需继续可重新打开。

## 后续处置决定（2026-10-13）

用户阅读当前 UI 与验收适用性评估后，明确决定“保守处理，我就放弃这个任务了”。停止本 FD 的后续实现、测试与验收投入；保留已有代码、历史失败及未运行证据，不标记 Complete，也不声称验收通过。

评估见 [当前 UI 报告](../../../reports/FD-002-current-ui-assessment-20261013.md)。尝试通过 `aiw fd close FD-002 Closed --reason ...` 记录关闭时，CLI 返回 `FD is already archived`。保留原 Deferred 归档状态，不为状态变更重新打开或创建 Worker 交接；本节记录用户放弃决定，原延期原因保留为历史。
