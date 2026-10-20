# FD-021 独立实现评审 R2

<!-- aiw-data: FD-021-review-r2.json -->

结论：**changes-requested**。R1-1/R1-2 已按原发现修复；对锁修复边界继续追踪到依赖解析器后，确认托管 Plan 的协调推进与原停止控制仍存在一项阻塞锁环。未授权测试缺席不是拒绝依据。

- Reviewer：`codex-reviewer-fd021-r2-20261021`，独立于 Worker。
- 已领取来源：`FD-021-000006-implementation-ready`；FD Revision 6。
- 当前实现：`3167fb7a78000c6b2866260edc8a14979eefdb9b`；本轮差异基线 `2ed44e6`，全 FD 基线 `4b61b2f`。
- 需求及全 FD 静态上下文沿用 R1，复核本轮源代码差异与报告，不改业务范围。

## R2-1 / high / blocking：协调推进的依赖锁与停止控制反序

位置：`src/main/java/me/asu/aidev/secretary/application/SecretaryCoordinator.java:75,107`；关联 `WorkflowPlanService.java:362,371`、`WorkTaskService.java:92`、`DependencyResolver.java:43`。

事实：协调消费先取得秘书全局锁及 Plan 行锁，然后调用原 advance 创建评审或下一步。`WorkTaskService.createInternal` 会调用 `DependencyResolver.refreshAfterCreation`，该方法逐一 `tasks.lock(dependencyId)`；因此不仅写新任务，还会锁已完成的源专业任务。此路径的相关锁序为 Plan → source task。

原 `WorkflowPlanService.stop/complete` 的 control 路径没有秘书全局锁：先 `lockedTasks(id)` 锁所有专业任务（包括已完成源任务），再 `plans.transition` UPDATE Plan 行，相关锁序为 source task → Plan。

可达交错：第一步专业任务已完成、独立协调正在准备为它创建评审；同时用户在原 Plan 页面请求停止。停止事务先锁该已完成任务；协调消费先锁 Plan。协调随后在依赖解析器等待该源任务，停止随后在 Plan transition 等待 Plan。PostgreSQL 出现双事务锁环，须回滚一方。该源任务已完成不妨碍 lockedTasks 取得其锁；停止入口只有最终评审已通过才拒绝，因此此例可达。

违反：FD D2 一致锁序、保留原停止控制与协调安全消费的实现约束。R1-1 的旧 Plan 指令修改锁环已经消除，但不能据此宣称所有交叉锁已解决。这里是本轮追踪实际依赖锁后确认的另一条路径，不是要求额外运行测试。

最小修正：统一托管 Plan 原控制入口与协调消费的事务串行化/锁序，确保在取得任何 task/Plan 锁前完成所需协调。例如对托管 Plan 的 stop/complete 控制先获取同一秘书全局锁；同时检查其他会先锁专业任务再写 Plan 的原控制路径，避免从 Worker 已持 task 锁的回调才获取秘书锁。保留旧未托管 Plan 兼容、取消后的真实终态和 D1 准入边界。

## R1 修复及当前全 FD 判断

- R1-1 已解决原发现：普通 check/checkTask 不取 Plan 锁；CONFIRMED 旧 Plan 的通用创建只尝试匹配 DRAFT 的 UPDATE，不锁旧 CONFIRMED 行。初始确认与关联仍锁 Plan；关联拒绝已有任务，专业领取需要 ACTIVE + 当前 generation 准入记录。该修复未恢复原初始确认旁路。
- R1-2 已解决：源完成缺报告/attempt 直接 NEEDS_HUMAN；消费端显式检查 task/input/report/attempt 缺失，返回空推进，不再以该缺失抛异常回滚协调终态。
- 本轮新增 `secretary_admitted_task` 为 V8 尚未部署的增量表，无历史回填；准入与任务创建同事务，generation 与确认修订一致。未发现需另列的当前静态行为缺陷。
- 对话、时间、actor/CSRF、取消、独立角色、版本与一次消费、UI 及运行角色的 R1 静态审查上下文仍适用；上述一项未解决前不能给全 FD 通过结论。

## 证据与限制

实际命令：`aiw fd show`、`aiw fd claim`、`git show --stat`、`git diff`、`git rev-parse HEAD`、`Get-Content`、`Select-Object`、`rg`；使用 apply_patch 写本双证据及 FD Verification。最终只读核对 JSON、aiw-data 和 diff；随后 emit changes-requested 并回读 receipt。

已读取 R2 Worker 报告及 compile-r2.log：记录离线 Maven 3.9.9 / JDK 25 编译 240 个生产源码成功，耗时 5.111 秒。Reviewer 未重跑编译；JS/TS 本轮未变，未重复语法检查。

假设：保持用户已批准的 A+B、独立协调和站内反馈范围。未验证：测试、迁移、服务、Worker/模型、网络、浏览器、并发运行、完整 TypeScript 类型检查及最终构建未运行；无子代理、无实现修改、无合并归档。R2-1 是实际 SQL、事务与依赖锁调用链的静态判断，未执行并发复现。
