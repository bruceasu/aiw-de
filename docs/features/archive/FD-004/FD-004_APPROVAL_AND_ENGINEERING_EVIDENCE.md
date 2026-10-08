# FD-004: Approval and Engineering Evidence

**Status:** Complete
**Revision:** 14
**Priority:** Medium
**Test policy:** Independent
**Evidence policy:** Dual

## Problem

对应 REQ00001 的 M4。审批者需要在决定前核对 Run、具体 result revision、review/verification/gate 和 task/attempt 来源，并区分待处理与已决定记录。当前审批表展示结果版本与决定操作，Run 的任务表没有完整工程结果/证据关联。

2026-10-04 用户授权先生成里程碑 FD；2026-10-05 明确请求本 FD 的 auto 生命周期。必须保留审批绑定具体版本、已决定记录不可逆以及既有 application service 权威。

## Options and decision

| 方案 | 判断 |
|---|---|
| 仅给审批表补文字 | 不足以核对版本、审查/验证与 gate 证据 |
| 待处理/历史分区，增加基于已存结果的 Run/task/attempt 证据关联 | 推荐；复用审批服务与现有持久化读取 |
| 直接抓取 publicationRef 指向的 Git/外部文档全文 | 访问权限、凭据、路径和失败策略未明确，不能默认纳入 |

采用第二种方案。2026-10-05 用户确认本期仅展示已存 summary/verdict/revision，审批历史仅提供有界最近列表；不提供外部全文/下载或完整历史分页。`publicationRef` 仅作已存标识显示，不读取其目标。

## Solution

Approvals 分为 PENDING 与已决定历史。分别按状态过滤、按时间和 ID 稳定倒序、再限量；pending 总数沿用 FD-001 的独立准确计数，不用有限列表长度充当总数。历史仅展示最近窗口，标出返回条数与上限，不承诺完整历史或新的保留策略。审批详情/Run 证据区组织具体审批版本、已有 review/verification/gate、对应 task/attempt、结果 revision/publicationRef 和决定归属/时间。

权威关联从 `approval_request.id` 出发，使用记录中的 `source_task_id`、`result_revision`、`engineering_gate_closed_at_epoch_ms` 定位被审批结果；按 source task 查找带 gate 的已完成 source attempt，并同时校验其 revision 和 gate 关闭时间。只有三者一致且 gate 为 `PASSED`，才将该 source attempt 上的 review/verification task ID 及对应已完成 attempt 的已存 verdict/summary 展示为“该审批的证据”。证据 DTO 至少携带 approval ID/状态、run ID、source task/attempt ID、result revision、gate 状态/关闭时间、review 与 verification 的 task/attempt ID、verdict/summary、每项来源时间和可用性原因。`JdbcEngineeringGateRepository.findBySourceTask` 当前返回某 source task 最新的已关闭 gate，且 review/verification 报告取各 task 最新已完成 attempt；该读取不足以单独证明历史审批版本，页面投影须做上述精确匹配，并核对 review/verification task 对 source task 的依赖。缺失、重复或不匹配时显示“证据不可核对”及具体原因，不用当前 HEAD、最新 gate 或最新 attempt 补齐。

持久化 summary/指令按普通文本转义渲染，长内容可展开并保留原始来源。publicationRef 首期作为已有发布标识显示，不自行 fetch、执行命令或请求外部 URL。外部访问若纳入，需要专门设计权限、凭据、路径验证、审计、失败与撤回策略。

请求审批、approve/reject 均沿用既有 WebConsoleActionController/ActionService/ApprovalService。保持 APPROVER/ADMIN 权限、CSRF、状态竞争与不可反转记录；审批前展示证据缺失，但不自行发明新的领域拒绝规则或修改 gate 政策。由权威服务决定动作是否允许。

复用 FD-001 的视觉与刷新，沿用 FD-002 的 Run/task 上下文和既有 URL；已决定记录只读。渲染时以同一个 approval ID 构造证据和表单，表单提交只引用这个 ID；刷新后整组替换，不能保留旧表单却展示新证据。提交时继续由 `ApprovalService.decide` 的 PENDING 条件更新裁决并发；冲突后重新读取并展示实际状态，不把过期操作报告为成功。证据投影不改变领域审批策略，缺失时清楚提示，动作是否允许仍由权威服务裁决。

候选文件：WebConsoleQueryService、私有审批/证据 DTO 与 JDBC 读取、`templates/ui/approvals.html`、`run-detail.html` 和证据 fragment。暂不更改 schema、权限、审批状态、公开 API、Git/外部网络或工程 gate 权威。

## Scope

包含待处理/历史分区、已保存审批与工程证据关联、具体结果版本、来源/时间、缺失反馈及已有决定动作体验。

不默认提供外部文档/Git 全文或下载、完整历史保留系统、审批政策编辑、新 Request Repair 动作、新权限或 deployment/PR/回滚自动化。首页准确 pending 总量由 FD-001 负责，本 FD 复用并深化读取。

## Work items

- [x] 4.1 以 approval ID → source task → 精确 source attempt/gate → review/verification task/attempt 盘点来源，核对 revision、gate 关闭时间与依赖，建立证据 DTO 和缺失/不匹配矩阵。
- [x] 4.2 建立待处理/已决定分别先过滤、稳定排序再限量的读取与最近窗口标记，复用 FD-001 的准确 pending 统计，不新增完整历史分页或保留策略。
- [x] 4.3 组织审批/Run 证据上下文，展示具体 revision、来源 ID/时间与已存 summary/verdict，不用最新结果替代审批绑定版本。
- [x] 4.4 保留已有 request-approval/approve/reject 权威，改善权限、不可逆说明、证据缺失与状态竞争/刷新反馈。
- [x] 4.5 仅读取已存 summary/verdict/revision；`publicationRef` 只显示标识，不触发外部文档、Git、下载或文件全文访问。
- [x] 4.6 应用共享视觉、文本转义、长内容/空态/异常态，更新中英文指南并准备独立验收证据。

依赖：FD-001 共享基础；FD-002 的上下文设计可复用，既有 Run URL 继续可用；4.1 先于 4.2–4.5，4.6 最后。以上勾选记录 Worker 已写入的实现与静态证据，不代表独立验收通过。

## Acceptance

| 编号 | 原需求 | 验收 |
|---|---|---|
| AC01 | R08、A10 | pending 与历史分开；审批前通过 approval ID、source task、result revision 与 gate 关闭时间核对对应 Run、source attempt、review/verification/gate 与来源，不混用别的版本 |
| AC02 | R08、A07 | 缺失或不匹配的 summary/attempt/gate 明确显示，不补造通过结论；历史为状态先过滤的有界最近列表，说明返回量和上限 |
| AC03 | R09、A09/A10 | 权限/CSRF/服务端校验正确；并发或过期决定显示真实反馈；已决定记录不能改成相反结论 |
| AC04 | R10、A11 | 同一 approval ID 的表单和证据在刷新时成组替换；并发决定后重读实际状态，不显示假成功或误导性的版本关联 |
| AC05 | R11、A12 | 长 revision/summary、空态/异常态、窄窗口可访问；不执行或解释证据内脚本 |
| AC06 | 制品边界 | 首期仅展示已存 summary/verdict/revision，publicationRef 不触发网络/Git/文件全文读取或下载 |

## Verification

Planner 静态核对了 `ApprovalService`、审批与工程 gate 的 JDBC 读取、source/review/verification attempt 的关联，以及现有 Web Console 查询和动作入口；据此写出版本匹配规则。Planner Session：`fd004-planner-20261005-8b8c077d`，来源事件：`FD-004-000005-decision-recorded`。尚未用真实数据验证唯一性与历史缺失矩阵；未实现、编译、测试、运行应用或浏览器。

版本绑定、来源/权限/转义/范围已完成 Worker 静态核对，并执行一次最窄 compile-only 检查。独立 Tester/Reviewer 遵循 Independent/Dual policy；具体测试、浏览器、外部网络或 Git 读取须另行授权。

Worker Session：`fd004-worker-20261005-85b8a0b8`，来源事件：`FD-004-000006-design-ready`。已新增私有审批证据 DTO/JDBC 只读投影、待决定/最近决定分区、审批与 Run 证据页面、中英文指南。静态核对了 `(task_id, attempt_no)` 唯一约束、`source_task_id` 唯一审批、review/verification 对 source task 的依赖与 base revision、已有 ActionService 权威和 CSRF。编辑中一次 `git diff --check` 无空白错误。以 JDK 21 执行 `C:/green/apache-maven-3.9.9/bin/mvn.cmd -o -DskipTests compile` 成功，181 个源文件编译。未运行测试、SQL 运行查询、Thymeleaf 渲染、浏览器、网络、格式器或最终构建；独立 Tester/Reviewer 尚待交接。

独立 Tester 报告 `FD-004-test-report-r8`：29 个场景中 1 个既有基本批准测试通过，28 个未执行；需求场景覆盖 3.45%，业务分支覆盖未测。PM 在 `FD-004-test-decision-r8-v1` 接受检查覆盖例外并交 Reviewer 静态审查，不豁免行为要求。首轮 Reviewer `FD-004-review-r9` 发出 `changes-requested`：Run 页局部刷新更新审批状态，但保留区域外的证据与表单，违反 AC04。Worker 修复 Session：`fd004-worker-20261005-40492535`，来源事件 `FD-004-000010-changes-requested`；审批权限角色的状态、证据、表单现处于同一 `data-refresh-region`，只读角色仍在 context 区同步看证据。编辑/聚焦时沿用共享脚本的保护，整组保留，后续刷新再整组替换。仅修改模板和中英文指南；未重跑编译、测试或浏览器，待新一轮独立审查。

拟定场景：pending、已决定历史、不同结果版本、多 attempt、repair 后新结果、缺失 gate/summary、并发决定、过期页面、长证据和包含 HTML 的纯文本。视觉与新鲜度场景复用 FD-001。

独立 Reviewer Session：`fd004-reviewer-20261005-00570883`，来源事件：`FD-004-000009-test-accepted`。审查报告：[FD-004-review-r9](reviews/FD-004-review-r9.md)。结论：`changes-requested`；Run 页局部刷新未将审批证据与操作表单成组替换，违反 AC04。Tester 的 29 个场景中仅 1 个已执行并通过，其余 28 个未执行；PM 的覆盖例外不豁免行为要求。

第二轮独立 Reviewer Session：`fd004-reviewer-20261005-59bbba38`，来源事件：`FD-004-000013-test-accepted`。审查报告：[FD-004-review-r13](reviews/FD-004-review-r13.md)。结论：`verification-passed`（静态审查）；AC04 区域归属修复与共享刷新脚本一致。当前 Tester 的 29 个场景全部未执行，需求覆盖 0.00%，业务分支覆盖未测；PM 的检查例外不豁免行为，真实浏览器交互仍有残余风险。

## TODO and design gates

2026-10-05 决定：制品范围为已存 summary/verdict/revision；历史范围为有界最近列表。Planner 的技术设计已写入 Solution 和 4.1；Worker 已按现有 schema 与服务契约完成私有投影。首轮 Reviewer 发现的 AC04 问题已修复，历史报告保留；28 个未运行场景与实际交互风险继续由下一轮独立 Reviewer 审查。

## Sources

- Issue: none
- Source requirement: [REQ00001-web-control-plane](../requirements/REQ00001-web-control-plane/requirement-plan.md)，M4；[差距分析](../requirements/REQ00001-web-control-plane/current-state.md)。
- CLI 的 `--issue` 当前只允许一个 FD 正式关联同一需求；此 FD 使用来源链接，未新增正式绑定或伪造批准记录。
- 依赖：[FD-001](FD-001_MISSION_CONTROL_OVERVIEW_AND_SHARED_VISUAL_FOUND.md)、[FD-002](FD-002_RUN_MISSION_DETAIL_AND_TIMELINE.md)；相关：[FD-003](FD-003_WORKER_FLEET_AND_SCHEDULING_EXPERIENCE.md)。
- 来源事件：`FD-004-000002-design-requested`；2026-10-04 用户要求剩余里程碑先生成 FD。
- [用户范围决定](reports/FD-004-scope-decision-r3.md)；`FD-004-000005-decision-recorded`。

**Completed:** 2026-10-05
