# FD-019 独立实现审查 R1

<!-- aiw-data: FD-019-review-r1.json -->

结论：**changes-requested**。发现一项冻结调度策略的一致性缺陷；未要求额外测试作为修复前提。

- Reviewer Session：`fd019-reviewer-20261021-c814fa`，与 Worker 分离。
- 真实来源事件：`FD-019-000005-implementation-ready`，已由本 Session 领取。
- 审查范围：`d1a0286..8314bee`，FD revision 5 及当前 Verification；Worker 报告 `reports/FD-019-implementation-r1.md` 和 JSON。Worker 报告中的代码 head `9ba3ff5` 是实现代码位置，后续报告提交已纳入本次比较。
- 业务依据：FD 已记载用户三项确认、file-convert help，以及未授权测试的边界；设计审阅 D1–D5 作为相关历史证据。

## 发现

### R1 中：领取仍受当前配置预过滤，破坏已排队讨论的冻结策略

位置：`src/main/java/me/asu/aidev/goal/application/GoalTaskPolicy.java:40` 与 `src/main/java/me/asu/aidev/execution/application/TaskClaimService.java:127`。

事实：`GoalTaskPolicy.canClaim(task, worker)` 按数据库请求的 frozen profiles 裁决，guidance/start 也使用同一冻结内容。但 `TaskClaimService` 在调用这些策略前先执行当前 `aiProfiles.canRun`，仅对 `recommendationPolicy.managed(candidate)` 跳过该过滤，没有跳过新的 goal-discussion。

具体触发：讨论在 OpenAI profile 被批准时排队；服务重启后，EXTERNAL_WORK 当前策略改成仅允许 Codex。保留的请求仍冻结 OpenAI，匹配的 OpenAI Worker 会被通用预过滤跳过；Codex 又被冻结策略拒绝。请求会一直 READY，虽然原冻结后端有在线 Worker，guidance/start 也仍接受该冻结配置。反向切换同理。这违反 FD 的领取、guidance、start 使用一致冻结策略要求，也使持久请求在配置变化后无法继续。

最小修正：将 goal-discussion 纳入通用当前配置检查的豁免，交给 GoalTaskPolicy 按冻结请求裁决，与现有 recommendation 的处理保持一致。保持普通任务的当前配置限制；静态核对排队后配置改变时三处策略仍一致即可，本报告不追加运行授权要求。

## 已核对的证据与边界

事实：静态追踪了 V7 的表、列数、唯一约束和外键；目标/附件/版本 actor 检查、revision CAS、delete/restore epoch、请求去重及终态报告接入；正式版本与编辑快照、AI Steps、旧计划校验、显式临时工作区准备；UI 的 CSRF、textContent、保存/切换及外发选择；TS 角色注册、独立空目录、OpenAI 工具限制和 Codex 只读配置。上述范围未发现第二项已确认阻断。

Worker 的 Maven 与 TS 编译结果仅作为其报告证据，本 Reviewer 未重跑，也没有将语法编译解释为类型或运行验证通过。报告明确保留的运维可见边界、视觉能力缺失与转换质量限制不作为新增业务决定。

实际命令：`Get-Content`、`rg`、`git diff --stat`、`git diff`、`git status --short`；`aiw fd show`、`aiw fd claim` 和 CLI help。若干初始共享资源路径及 PowerShell 通配符读取失败，随后按实际路径读取；未通过替代执行运行检查。报告/FD 用 `apply_patch` 编辑，之后仅作一次 `git diff --check` 静态校验，并按技能要求 emit、范围提交。

未执行：测试、编译、类型检查、应用/服务、浏览器、网络、迁移、真实模型、转换器及并发运行。剩余风险：数据库双后端、文件转换、宿主 Codex 权限及真实交互只获静态证据，均不宣称运行通过。未发现安全性证明不足之外的新确定漏洞，也未将未运行测试本身视为拒绝原因。
