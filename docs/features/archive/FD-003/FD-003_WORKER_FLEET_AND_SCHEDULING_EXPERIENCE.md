# FD-003: Worker Fleet and Scheduling Experience

**Status:** Complete
**Revision:** 11
**Priority:** Medium
**Test policy:** Independent
**Evidence policy:** Dual

## Problem

对应 REQ00001 的 M3。操作者需要把 Worker 管理状态、能力、容量、心跳、租约与真实 Run/task 联系起来，解释 READY 积压和准入瓶颈，并理解调整后的效果。当前 Workers 表和 System 数值/操作已经存在，但工作关联、影响范围和操作反馈分散。

2026-10-04 用户授权先生成里程碑 FD；2026-10-05 用户要求本 FD 自动实施且不做测试。首页与 Worker 基础机器形象由 FD-001 承担，本 FD 深化工作关联和调度体验。

## Options and decision

| 方案 | 判断 |
|---|---|
| 只增加 Worker 表列 | 改动小，不能组织瓶颈→影响→动作 |
| 在现有 Fleet/System 页面组合 Worker 身份、活动租约、等待任务和调度限制 | 推荐；复用 FleetDashboard 与既有应用服务，保持 Pull 权威 |
| 新中央调度器、健康分级/告警或历史 claim 回放 | 没有本期已确定的业务规则/完整证据，不默认引入 |

采用第二种方案。保持既有 Worker 状态、准入与自然排空规则，新增仅用于 UI 的有界关联读取。用户于 2026-10-05 确认 M3 仅交付可观测心跳、容量和租约事实，不加入自动 healthy/unhealthy 分级或告警；历史 claim 回放仍不在本期。

## Solution

Fleet 页面按 Worker 身份/管理状态、实际占用/可用容量、capabilities/runtime、心跳快照、活动租约/工作链接组织。保留真实 Worker ID，复用 FD-001 的稳定机械身份，不创建新 Worker/role/node 实体。

`FleetDashboard.WorkerSummary/ActiveLeaseSummary/ReadyTaskSummary/AdmissionBudget` 提供现有事实。需核对 task/subject 到 Run 的权威映射，批量关联 task/Run/attempt/lease expiry，不从 task 名称猜归属。显示列表数和范围，截断列表不冒充整个 Fleet。

Task→Run 只认 `work_task.subject_type='AUTONOMOUS_REQUIREMENT'` 且 `subject_id=autonomous_run.id`；Run 记录缺失时显示原始 subject ID 和“Run unavailable”，不给出失效详情链接。其他 subject 类型只显示原始类型/ID，不猜测 Run。活动租约沿用 `taskId/workerId/attemptNo/leaseExpiresAt`，没有记录的字段显示未知，不推断历史 claim 原因。需要补 Run 摘要时仅对当前最多 100 条详情的去重 ID 做一次有界批量读取，不逐 Worker 或逐任务查库。

READY 汇总沿用现有互斥主原因顺序：policy → Run admission → capability → Worker capacity → Fleet admission → 可领取；总数来自 `Backlog`，不能把详情筛选数当总数。每条任务独立展示所有当前不满足的条件，包含 `policyAllowed`、`admissionAllowed`、`compatibleActiveWorkers`、`claimableWorkers` 和 `fleetAdmissionAllowed`；若多个条件同时成立则并列显示，不声称它们是历史调度决策。仅在兼容 Worker 大于零且可领取 Worker 为零时显示容量限制；不把无兼容 Worker 写成容量限制。Run 或关联证据缺失时标记未知。

详情复用现有 `FleetDashboardService.dashboard(100)`：READY 按优先级降序、创建时间升序、task ID 升序；活动租约按过期时间升序、task ID 升序；Worker 按管理状态、Worker ID 稳定排序。三个列表各最多 100 条，标题同时写明“显示 N / 总数 M”和“当前快照，前 100 条”；本期不做跨快照分页，超出范围的工作通过已有关联 Run 详情继续查看。筛选只能作用于这 100 条，并明示局部范围。

READY 详情分别呈现能力不匹配、Worker 容量、Run/Fleet 准入和 policy 条件；多条件同时受限时全部显示可验证事实。分类是当前快照判断，不是过去 claim 决策回放。显示限制、实际占用、可用槽位与 overcommitted；0/无限和未知值严格沿用已有定义。

System/Fleet 调整与既有 Run 限额链接通过现有 action services 完成。降低限额说明自然排空、不取消已有租约；drain 停止新领取、继续已有执行；offline 的前提由服务校验。pause/cancel 与自然排空的不同后果保持可见。表单显示当前限制、租约占用、可用槽位、超额占用与 0=无限；降低到占用以下仍允许提交但须说明自然排空。只对有权限和当前快照可用的动作显示按钮，POST 仍由现有权限、CSRF 和服务端状态检查决定结果。提交后沿用现有 redirect/flash 显示成功或错误，状态竞争按实际错误反馈；局部刷新只更新只读区域，不覆盖编辑值、焦点或操作提示。

心跳时间、capturedAt、占用和会计差值显示事实。不用未定义阈值宣称 Worker unhealthy，未记录 runtime/node 信息不补造。自动健康/告警若后续被纳入，必须明确规则和范围。

主要候选文件：WebConsoleQueryService、私有 Fleet 关联 DTO/读取、`templates/ui/workers.html`、`system.html` 和关联 fragment；复用 FleetDashboardService、FleetAdmissionBudgetService 及现有 action controller。保持公开 API、权限、schema、调度/租约并发与重试不变。查询批量、有界，不能逐 Worker 扫描所有 Run。

## Scope

包含 Fleet 工作关联、能力/容量/心跳/租约事实、READY 积压影响、准入限制与已有调整体验；复用 M1 共享视觉与刷新。

不重复首页与机器身份基础，不修改中央调度权威、Worker runtime、租约算法、schema、管理权限或持久化；不默认实现自动健康分级/告警、历史 claim 决策回放或新的 Request Repair 动作。

## Work items

- [x] 3.1 核对 Worker/task/Run/attempt/lease 与准入字段，定义有界批量关联读取和缺失语义。
- [x] 3.2 深化 Fleet 的身份、能力、状态、占用、心跳与真实工作入口；复用 FD-001 形象。
- [x] 3.3 展示 READY 分类、受影响任务和多个当前阻挡条件，区分全量汇总与有限详情。
- [x] 3.4 改善 Fleet/Run 限额与 drain/offline 的编辑、后果提示、竞争和自然排空反馈。
- [x] 3.5 应用新鲜度、减少动效、空态/异常态和窄窗口规则，更新中英文指南并准备独立证据。

依赖：FD-001 共享基础；3.1 先于 3.2–3.4，3.5 最后。FD-002 的 Run 链接可复用既有详情 URL，不要求 M2 全部完成后才能设计 Fleet。

## Acceptance

| 编号 | 原需求 | 验收 |
|---|---|---|
| AC01 | R06/R12、A13 | 身份在首页/Fleet/执行入口一致；管理状态与租约占用分开，真实 ID/runtime/capability 可读 |
| AC02 | R03/R06、A03 | 能力、容量、Run/Fleet 准入及 policy 条件有事实与任务范围；不声称历史 claim 原因 |
| AC03 | R09、A08 | 限额收缩与 drain 显示自然排空；已有租约继续；pause/cancel 的不同后果明确 |
| AC04 | R09、A09 | 权限、CSRF、offline 前提和状态竞争仍由现有服务校验，页面不显示假成功 |
| AC05 | R06/R10、A07/A11 | 缺失关联、截断列表、过期心跳/快照明确；不推断健康等级；刷新保持输入 |
| AC06 | R11/R12、A12 | 空态、长 capability/ID、无可用容量、超额占用及减少动效时仍可读/可操作 |

## Verification

Planner 已静态核对 `JdbcFleetDashboardRepository`、`FleetDashboard`、`WebConsoleQueryService`、现有 action 路径与 Run 详情读取；以上字段、顺序、范围和反馈决定基于现有源码。Planner Session：`fd003-planner-20261005-f1715a28`，来源事件 `FD-003-000005-decision-recorded`。本阶段未实现、编译、测试、运行应用或浏览器。用户明确要求本轮不做测试；独立 Tester 如被路由，只记录未执行场景和风险，不把未运行写成通过。

Worker Session：`fd003-worker-20261005-176e2057`，来源事件 `FD-003-000006-design-ready`。3.1–3.5 的私有 Fleet 读取、页面、操作说明与中英文指南已写入独立工作树。静态核对了权威 `subject_type/subject_id`、READY 主原因优先级、活动租约排序、既有权限/CSRF/flash 和局部刷新边界；`git diff --check` 未发现空白错误。首次离线 `mvn -o -DskipTests compile` 因环境默认 Java 8 解析不了现有 Java 21 记录/文本块而失败；设置本机 JDK 21 后同一 compile-only 命令通过，179 个源文件编译成功。未运行测试、浏览器、模板实际渲染、网络、格式器或最终构建；编译通过不等于交互通过。

拟定场景：无 Worker、Idle/Executing/DRAINING/OFFLINE、能力不匹配、多个阻挡条件、无限/饱和/超额限额、限额收缩、drain 与租约继续、offline 竞争和编辑期间刷新。尺寸/可访问性矩阵复用 FD-001。

独立 Tester 报告：`docs/features/reports/FD-003-test-report-r8.md`；50 个场景均未执行，需求覆盖 0/50（0.00%），分支覆盖未测量。PM 在 `docs/features/reports/FD-003-test-decision-r9-v1.md` 根据用户“不用做测试”的决定接受覆盖例外，不将未执行场景记为通过。独立 Reviewer Session `fd003-reviewer-20261005-9c7e4d2b` 认领 `FD-003-000010-test-accepted`，按 `docs/features/reviews/FD-003-review-r10.md` 对提交与证据完成静态审查，结论 `verification-passed`；未运行测试或浏览器，实际交互风险保留。

## TODO and design gates

两个设计门槛已解决：用户确认健康与告警范围；Planner 依据现有权威字段确定 Task→Run、主原因与并列限制、稳定排序、100 条快照详情及操作反馈。`$fd-workflow FD-003 auto` 授权本 FD 实施流程；不授权测试、浏览器、网络或发布。

待独立 Tester 记录未运行场景与覆盖风险，PM 依用户不做测试的决定记录例外，随后由独立 Reviewer 检查实现与证据。页面布局/模板渲染、状态竞争和窄窗口行为尚无运行证据。

## Sources

- Issue: none
- Source requirement: [REQ00001-web-control-plane](../requirements/REQ00001-web-control-plane/requirement-plan.md)，M3；[差距分析](../requirements/REQ00001-web-control-plane/current-state.md)。
- CLI 的 `--issue` 只允许一个 FD 正式关联同一需求；本文件以来源链接追溯，不新增或伪造正式绑定。
- 依赖：[FD-001](FD-001_MISSION_CONTROL_OVERVIEW_AND_SHARED_VISUAL_FOUND.md)；相关：[FD-002](FD-002_RUN_MISSION_DETAIL_AND_TIMELINE.md)、[FD-004](FD-004_APPROVAL_AND_ENGINEERING_EVIDENCE.md)。
- 来源事件：`FD-003-000002-design-requested`；2026-10-04 用户授权先生成剩余里程碑 FD。
- [用户健康与告警范围决定](reports/FD-003-health-scope-decision-r3.md)；`FD-003-000005-decision-recorded`。

**Completed:** 2026-10-05
