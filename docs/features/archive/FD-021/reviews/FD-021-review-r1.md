# FD-021 独立实现评审 R1

<!-- aiw-data: FD-021-review-r1.json -->

结论：**changes-requested**。存在两项行为缺陷，返回 Worker 修复。未运行的测试不构成本次拒绝依据。

- Reviewer：`codex-reviewer-fd021-r1-20261021`，独立于实施 Worker。
- 已领取来源事件：`FD-021-000004-implementation-ready`。
- 审查基线：`develop` / `4b61b2f`；实现 HEAD：`2ed44e626c06921d3c652305bfee895df2773b9c`，包括 `4386d70` 与 `2ed44e6`。
- 需求：ISSUE-00008 第 10 节、FD-021 Revision 4、`openspec/specs/secretary/spec.md`；设计 D1/D2 作为实现约束。

## R1-1 / high / blocking：新增 gate 为旧 Plan 引入反向行锁

位置：`src/main/java/me/asu/aidev/secretary/application/SecretaryPlanGate.java:14`（尤其第 16 行），以及 `TaskExecutionService.java:183,348`、`WorkflowPlanService.java:591`。

事实：`check` 无条件先执行 `repo.lockPlan(plan)`，未托管的旧 Plan 同样获取 Plan 行锁。`reviseInstruction` 在 `tasks.lock` 之前调用此 gate，因此持锁顺序为 Plan → task。与此同时，旧 Plan 的完成事务先由 `tasks.completeOwned` 更新并锁定 task，再由 `WorkflowStepCompletedHandler.advance` 调用 `createReview`，后者新增的 gate 又请求 Plan 锁，顺序为 task → Plan。PostgreSQL 的 `lockTask` 是 `SELECT ... FOR UPDATE`，Plan 锁是同一事务中的 UPDATE。

可达交错：旧 Plan 的 RUNNING 执行任务正在上报完成；另一操作同时修改其指令。修改事务先锁 Plan，完成事务先锁 task；修改事务等待 task，完成事务等待 Plan。数据库须中止一个事务，可能回滚真实完成上报或用户操作。指令修改明确允许 RUNNING，因此不是不可达的状态组合。

违反：FD 验收 4 的旧 Plan 兼容，以及 D2 的一致锁序。此问题由新增 gate 改变旧路径引入；不依赖秘书关联或新增 Worker。

最小修正：在所有会进入该 Plan gate 的存量任务写路径上统一锁序，或调整关联串行化方式，避免未托管 Plan 的 Plan → task 与完成路径的 task → Plan 并存。保留“关联与旧入口并发时不能提前启动托管计划”的 D1 边界；不能仅删除锁而恢复关联竞态。静态追踪同时覆盖完成、指令修改、重排队和原 Plan 控制。

## R1-2 / medium / blocking：缺失源报告使协调完成回滚而不转人工

位置：`src/main/java/me/asu/aidev/secretary/application/SecretaryCoordinator.java:66,94,98`。

事实：受托专业任务的完成分支没有检查源 `completionReport` 是否存在，即保存 source attempt 并排入协调。现有 `TaskExecutionService.complete` 对 EXTERNAL_WORK 允许空报告；`JdbcExecutionAttemptRepository.findCompletedInput` 也返回该已完成 attempt（verdict/summary 可以为空）。若协调返回 PASS + PROCEED，消费分支先标记 consumed，随后 `findCompletionReport(source.id()).orElseThrow()` 抛错；整个完成事务随之回滚，包括协调任务终态、容量释放与 consumed 标记。秘书仍可能显示 ACTIVE，协调任务保持 RUNNING，不能如要求转 NEEDS_HUMAN。

违反：FD 验收 6、稳定规范 Cancellation and truthful results 中的无效/失败证据保留及人工处理。协调 Worker 请求继续不能代替平台对源证据完整性的检查。

最小修正：在专业完成创建协调前和消费时对源输入/报告作显式有效性判断。缺失证据须记录 NEEDS_HUMAN 并返回空推进结果，让真实完成与协调终态提交；不要用 `orElseThrow` 回滚这一已知业务异常。保留源任务/attempt 事实，不重做专业工作。

## 其余静态结论

已追踪秘书 actor 过滤、CSRF 和 operator 检查、分钟与偏移核对、generation/CAS 与唯一 phase、取消后的在途完成、持久参与角色、协调消费与内部 exact Plan scope、原始报告推进、旧入口 gate、Spring 注入依赖、导航与页面、TypeScript role/profile/skill 加载及依赖输入传递。未发现需另列的确定缺陷；这些结论不代表运行通过。设计层的 D1 gate 已存在，D2 在旧 Plan 路径上仍有上述实现缺口。

Worker 编译日志记录 Maven 3.9.9 / JDK 25 离线编译 240 个生产源码成功；报告将 node --check 明确限定为语法检查。未重新执行这些命令，也未将其视为服务、迁移或 Worker 集成证明。

## 实际操作与限制

实际使用 `Get-Content`、`Select-Object`、`rg`、`git diff --stat/--name-only`、指定文件 `git diff`、`git rev-parse HEAD`、`git status --short`、CLI `aiw fd show`、`aiw fd claim` 与 `aiw fd emit --help`；使用 apply_patch 写评审双证据并更新 FD Verification。少量最初猜测的共享文件/源码路径不存在，随后以 rg --files 定位实际路径；一次不存在的 `aiw fd status` 子命令返回帮助，改用已有 show，不重复失败命令。最终静态核对与正式事件结果见 JSON。

假设：保留已批准的 A+B、独立协调 Worker 和站内反馈范围，不引入新业务决定。

未验证：测试、迁移执行、服务启动、真实 Worker/模型、网络、浏览器、并发运行、完整 TypeScript 类型检查及最终构建均未运行；本 Reviewer 未运行编译、未派生子代理、未实现修复、未合并或归档。PostgreSQL 交错是依据实际 SQL 与事务边界的静态证明，未执行并发复现。
