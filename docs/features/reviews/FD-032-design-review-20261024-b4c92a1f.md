# FD-032 独立设计审阅

结论：**changes-needed**。默认 `record-only`、显式 `enforce` 的统一计量模式符合用户要求，现有表和事务足以实现，无需新增持久化模型。设计需要补齐图执行器中的直接预算判断，并明确历史未知与真实在途的判断范围。下列意见针对设计覆盖；不是实现缺陷判决或运行验收门槛。

<!-- aiw-data: FD-032-design-review-20261024-b4c92a1f.json -->

- FD：`docs/features/FD-032/FD-032_FEATURE.md`，审阅 Revision 2。
- 来源事件：`FD-032-000002-design-requested`。
- 独立 Reviewer：`fd032-design-review-20261024-b4c92a1f`。
- 范围：一次本地静态设计审阅；未修改 FD、源码、工作流状态、提交或其它个人秘书文件。

## 发现

### D032-01 · P1 · 图执行器仍有绕开 Ledger 准入的预算和未知计量判断

事实：`workflow/graph/GraphRunner.java:218–219` 直接读取回跳预算并提前返回 `LOOP_INCREMENT_REQUIRED`；`:258` 的 `planQuiescent` 以 `unknown_count` 阻止整计划完成；`:260–275` 的 `requestGaps`、`available`、`requestIncrement` 根据额度产生正式预算授权问题。仅放开 `ExecutionLedger`、`ExecutionGuard` 和 `AgentFactory`，无法实现 FD Acceptance 1、4 所述的回跳与正常推进。

建议：将 GraphRunner 明确纳入 1.3、1.4 的调用路径。复用统一模式的公开只读判断，`record-only` 跳过回跳额度预检及自动预算问题生成，`planQuiescent` 仅跳过最后的计量未知判断。保留 `:247–256` 的真实任务终态、Lease、Attempt 和 `guard.unknownTask` 检查。不要把整个 `planQuiescent` 或 `allQuiescent` 改为无条件 true。

验收示例：同一循环已经超过三次，且有已结算但费用未知的历史 Attempt；无真实在途或未知外部操作时，仍记录下一回跳和任务并可完成计划，且不自动要求 A18 预算授权。

### D032-02 · P1 · 历史 UNKNOWN 放开应依据实际执行状态区分，不能仅改一个许可状态条件

事实：`authority/ActionAdmissionService.java:117–118` 与 `CoordinationService.java:66–67` 对全 Plan 的任何 UNKNOWN 阻断新动作/协调。它们与 `ManagedPlanAdmission.java:24–28` 的同任务替换保护、`ActionAdmissionService.java:126–127` 的精确 task UNKNOWN 保护、`ExecutionGuard.java:269–271` 的同任务未确认工具操作检查具有不同目的。

`AuthorityEffectObserver.java:22–26` 可把已经 FAILED 等终态任务对应的许可标为 UNKNOWN，这种纯历史状态不应继续冻结同 Plan 的无关新工作。但 UNKNOWN 本身并不证明进程已静止：`TaskExecutionService.java:544` 遇到受控失联任务会调用 `ExecutionGuard.retainExpired` 后直接 continue，保留其 Lease、CLAIMED/RUNNING 的 Task/Attempt 和容量；`ExecutionGuard.java:302–309` 同时保留未知操作与真实消耗。

建议：只在 `record-only` 取消跨任务的纯历史 UNKNOWN 阻塞；继续拒绝已有真实在途的相关执行。跨任务真实在途判断应依据 `work_task` 的 CLAIMED/RUNNING、现存 Lease、`execution_attempt` 的 CLAIMED/RUNNING 等原有事实，不能只依赖 `authority_task.state='IN_PROGRESS'`。同一原 task 的 UNKNOWN/IN_PROGRESS 替换、固定 Attempt、工具 UNKNOWN、图回跳前的受影响任务静止及资源占用保护保持原状。可将两个重复的全 Plan 检查收敛为 `ManagedPlanAdmission` 中一个小型方法，不新增通用策略框架。

验收示例：旧 FAILED task 的 UNKNOWN 动作回执不阻止新无关工作；旧 guard task 失联但 Lease 和 Attempt 仍在途时，不能创建会重复其工作的并行替代执行，不能自动 stopped、释放容量、修改 UNKNOWN 或重放原请求。

限制：非 guard 的旧失联流程原本就会回收 Lease；现有数据不能凭 UNKNOWN 证明旧外部进程已经静止。本 FD 不需要改造该恢复协议，也不能借取消计量限制自动重试原未知任务。

### D032-03 · P2 · 缺省预算、零预留、定价变化和严格模式恢复需写成同一明确兼容规则

事实：`GuardContract.Budget` 当前强制每 Attempt 正预留，`Policy` 拒绝空 budget；`ExecutionGuard.java:55,143`、`AgentFactory.java:79` 单独调用 `budget.configured()`；`ExecutionGuard.java:171` 在 Ledger 之外比较原始和当前价格。`ExecutionLedger.reserve` 保存原政策价格，`settle` 按该快照算费用；当前 `cost` 假设价格存在。

父会话提出的最小方案可行：增加一个统一模式 Bean，配置 `aidev.execution.accounting-mode`，默认 `record-only`，显式 `enforce`，非法值拒绝启动；向 AuthorityBudgets、ExecutionLedger 和页面注入。Policy 的空 budget 规范化为空预算，Budget 允许零预留，但 `enforce` 的 configured 仍要求完整限额、定价和正预留。将 Guard/AgentFactory 的 configured 与价格兼容判断统一经 Ledger 的模式判断。模式是部署配置，不由 Worker 请求选择。

建议把上述规则及以下计量语义加入 FD 1.1/1.3：原始任务政策快照与当前价格不同，在 `record-only` 不阻止领取；计量仍使用该 Attempt 原来冻结的价格，不追溯改写。缺价格的已启动 Attempt 费用写 null/UNKNOWN，不调用 null 价格、不写伪零；任务唯一记账、Attempt 预留/结算、回跳 transition 唯一键和 unknown_count 持续保留。切回 `enforce` 后原缺省配置、余额、价格兼容与未知消费约束重新生效，不重置历史、不重新计算旧费用。已有命令资源限制与 Fleet/Worker 并发均不属于累计预算开关。

## 其它静态结论

动作额度的实际拒绝点在 `AuthorityBudgets.consume:35–39`，可以只按统一模式决定是否应用额度谓词，仍原子递增 used/reserved 并写审计。原请求去重在 `ActionAdmissionService.apply` 中发生在消耗之前，应保持此顺序。授权、固定参数/版本、DENY、人工暂停和撤权不应接预算放行分支。

执行准备/创建/绑定/领取/推进应覆盖 `AgentFactory.prepareAuthorized`、`ExecutionGuard.beforeTaskCreation`、`bindPrepared`、`canClaim`、`preparationBudget`、`mayAdvance`、`mayAdvancePlan` 和 `ExecutionLedger.task/reserve/backjump`；实际 start 对已提交在途 claim 的既有语义可保留。`ExecutionLedger.settle` 对同 Attempt STARTED/UNKNOWN 工具操作的拒绝属于外部效果防重复与静止保护，不等同于 unknown_count 的预算限制。

稳定规格至少涉及 `openspec/specs/task-orchestration/spec.md` 的准备、预算、图回跳和未知恢复条款，以及 `openspec/specs/delegated-decisions/spec.md` 的累计额度、未知消费和后续推进措辞。授权页应显示真实配置模式；仅记录模式下的预算和历史核对不再描述为开工前置条件，仍说明原任务未知外部效果不得自动重放。

## 证据与未验证项

实际执行：`Get-Content -Encoding UTF8`、`rg/rg --files`、`Select-Object` 的本地读取；`apply_patch` 只写本报告及 JSON。最初读取缺失的 `java/` 子树、`review.md` 和已归档的 FD-031 报告路径失败，后续根据实际路径读取；未以失败命令推断业务行为。

未执行：编译、测试、构建、数据库、应用/浏览器、模型、网络、依赖下载、工作流命令、Git 提交或子代理。没有运行时通过结论；未新增人类未请求的运行验收 Gate。Planner 可直接将以上明确的最小规则纳入 FD 后继续实施，无需为这些既定范围内的例行取舍另求确认。
