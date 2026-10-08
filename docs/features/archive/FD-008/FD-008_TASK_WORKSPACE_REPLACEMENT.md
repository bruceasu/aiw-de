# FD-008: 任务 Workspace 替换

**Status:** Complete
**Revision:** 14
**Priority:** Medium
**Test policy:** Independent
**Evidence policy:** Dual

## Problem

Run 中已创建的 Work Task 没有更换 Workspace 或 repository 的安全入口。直接修改任务行会让历史执行记录与当前任务配置脱节：attempt 固定了执行时的 instruction revision 和 base revision，但历史页面仍可能从当前任务读取 repositoryRef。操作员需要把任务改派到另一个 Workspace，同时保留原始任务、Worker 执行记录和操作审计。

影响 Run 操作员、Worker 执行以及依赖该任务的后续任务。修改正在执行的任务环境可能让旧 Worker 与替代任务并行工作，因此不能仅修改当前任务字段。

## Options and decision

1. **原地修改 Workspace 字段。** 实现直接，但会重写任务当前执行上下文，历史 attempt 可能显示错误的 repository，且不能说明 Worker 实际收到的目标。拒绝。
2. **立即取消原任务并创建新任务。** 对没有活动租约的任务可安全执行；对已 CLAIMED/RUNNING 任务则可能与 Worker 并行或产生重复副作用。不能作为通用流程。拒绝。
3. **创建受审计的替换请求，确认旧 Worker 停止后原子取消原任务并创建替代任务。** 保留原记录；运行任务收到停止确认之前不创建可领取的替代任务。选择此方案。

该选择落实了用户决定：“取消原任务，创建新任务”。对活动 Worker 等待明确停止确认；不提供绕过确认的管理员强制替换。若任务在停止确认前自行完成、失败或租约状态发生冲突，则关闭本次请求，不自动创建替代任务，操作员检查最新状态后再发起。

## Solution

- 在 Run 任务详情提供“替换 Workspace”操作。沿用 FD-007 的 OPERATOR/ADMIN 任务控制权限；要求填写原因，并以任务版本/当前租约作并发保护。
- 目标必须来自已注册的 Workspace repository。Git 目标要校验并固定不可变 commit；若未指定 commit，则在受理时解析并记录目标当前 commit。非 Git 目标保持 `baseRevision` 为空。同步替换任务中的 `repo:` capability，避免 capability 与实际目标不一致。
- 对 BLOCKED、READY，以及无活动租约且 attempt 已终态的 FAILED/CANCELLED 任务，可在一个事务内记录替换关系、取消源任务并创建替代任务。对 CLAIMED/RUNNING 任务先持久化替换意图并请求 Worker 停止；Worker 确认停止时，在一个事务内取消源任务、创建替代任务并完成替换关系。确认前替代任务不可领取。
- 替代任务属于同一 Run，复制任务类型、subject、当前 instruction 内容、skill/profile、priority 及非 repository 执行能力；替代任务生成自己的初始 instruction revision。复制源任务的前置依赖。将源任务的后续依赖改接到替代任务，但仅当这些后续任务尚未被领取/执行。若存在已推进的后续任务，则拒绝替换并说明冲突，不改写既有执行图。
- 原任务及其 attempts、instruction revisions、Worker/lease 证据保持不变。替换关系记录源/替代 task ID、操作者、原因、时间、旧/新 repository 与 revision、请求状态。使用新增的兼容迁移和持久化记录；需同时保持 SQLite 与 PostgreSQL 方言兼容。
- 替换请求、Worker stop acknowledgement 和普通完成/失败之间必须幂等且互斥。重复提交不得创建多个替代任务。停止确认前若 Worker 自行结束，替换请求转为未完成/冲突，不自动重试；操作员可在页面看到原因与最新任务状态。
- COMPLETED 任务不允许替换；沿用 FD-007 的 linked correction 流程。此 FD 不给 correction task 增加 Workspace 编辑能力。

## Scope

Included:

- Run 任务详情中的 Workspace/repository 与 Git commit 选择、原因输入、替换状态及源/替代任务链接。
- 无活动 Worker 时的立即替换，以及 CLAIMED/RUNNING 时持久化、停止确认后的替换流程。
- 替换关系审计、任务和依赖图更新、租约与并发保护。
- SQLite/PostgreSQL 兼容的增量迁移及独立验证。

Excluded:

- 原地修改 instruction、status、step、priority、skill 或其他通用字段；instruction 继续使用 FD-007 的版本化控制。
- COMPLETED 任务和既有执行证据的编辑；completed correction 仍按 FD-007 处理。
- 创建、注册或管理 Workspace/Git repository；复用 FD-006 的 Workspace registry 和 repository 选择能力。
- 停止确认前的强制替换、自动重试或自动决定副作用可逆性。

## Work items

- [x] 1.1 增加替换审计表与仓储。**Size:** Small. **Difficulty:** Medium. **Dependencies:** none. **Completion:** SQLite/PostgreSQL 兼容的增量迁移与仓储记录来源任务、替代任务、操作者、原因、时间、前后 repository/revision、请求状态及原租约标识；同一来源只有一个未终结请求。
- [x] 1.2 固定替换目标与任务快照。**Size:** Small. **Difficulty:** Medium. **Dependencies:** 1.1. **Completion:** 只接受已登记 Workspace target；Git commit 在受理时校验并固定，目录模式 revision 为空；替代任务保留源任务规定的内容并同步 repo capability，具有自己的初始 instruction revision。
- [x] 1.3 保护依赖图重接。**Size:** Small. **Difficulty:** Medium. **Dependencies:** 1.1. **Completion:** 在同一事务内锁定来源和下游任务，复制前置依赖，将尚未领取或执行的下游指向替代任务；任何下游已推进则整体拒绝，无部分重接。
- [x] 1.4 实现无活动租约替换。**Size:** Medium. **Difficulty:** Medium. **Dependencies:** 1.1–1.3. **Completion:** BLOCKED、READY 及无活动租约的 FAILED/CANCELLED 来源任务通过版本校验，在单事务内取消旧任务、创建唯一替代任务、重接依赖并记录审计；COMPLETED 拒绝。
- [x] 1.5 持久化活动任务的待停止请求。**Size:** Small. **Difficulty:** Medium. **Dependencies:** 1.1, 1.2. **Completion:** CLAIMED/RUNNING 以任务版本和 lease fencing 创建一个待处理请求并向原 Worker 发停止信号；确认前原任务不被标记停止、不创建替代任务；重复提交返回原请求。
- [x] 1.6 接入停止确认的原子替换。**Size:** Medium. **Difficulty:** Medium. **Dependencies:** 1.3–1.5. **Completion:** 正确 Worker/lease ACK 后在同一事务内终结原 attempt/lease、取消原任务、创建替代任务和重接依赖；旧 lease 后续完成、发布和依赖触发均被 fencing 拒绝；重复 ACK 不重复创建。
- [x] 1.7 处理 ACK 与自然终态竞争。**Size:** Small. **Difficulty:** Medium. **Dependencies:** 1.6. **Completion:** ACK 前自然完成、失败、租约变化或下游推进使请求转为冲突，不自动创建替代任务；状态与原因可供操作员检查。
- [x] 1.8 提供 Run 任务替换入口与审计展示。**Size:** Medium. **Difficulty:** Medium. **Dependencies:** 1.4–1.7. **Completion:** OPERATOR/ADMIN 选择已登记目标、提供原因、提交版本/lease 校验；页面展示待停止、已替换、冲突状态和源/替代任务链接，其他角色不能提交，沿用 Web Console 视觉样式。
- [x] 1.9 更新操作文档与 Worker 实施证据。**Size:** Small. **Difficulty:** Low. **Dependencies:** 1.1–1.8. **Completion:** 文档说明立即与待确认替换、冲突和已完成任务的限制；Worker 报告真实编译、未运行的测试及迁移状态。

独立 Tester 阶段负责状态矩阵、ACK 竞争、依赖重接、权限、审计与数据库迁移验证；未经授权的运行检查不得算作通过。

## Acceptance

- 原任务的 Workspace、attempt、instruction revision 和执行结果证据不会因替换而被覆盖。
- 没有活动 lease 的可替换任务在单个事务中变为 CANCELLED，并且恰好创建一个关联的新任务；其目标 repository 与已验证 commit（若为 Git）一致。
- CLAIMED/RUNNING 任务在 Worker 明确确认停止前，旧任务不被标记为已停止，替代任务也不能被 claim。ACK 与自然完成/失败并发时不会重复创建；自然终态胜出则请求显示冲突且不创建替代任务。
- 任何旧 attempt/lease 在源任务取消后都不能发布结果、完成任务或触发其后续依赖。
- 新任务复制规定的任务内容与前置依赖；未开始的后续任务指向新任务。发现已推进的后续任务时，操作被拒绝且原任务图保持不变。
- 每次成功、待处理或拒绝的替换请求均有操作者、原因、时间和可追踪的前后任务/Workspace 信息；重试不会产生重复替代任务。
- BLOCKED、READY、CLAIMED、RUNNING、FAILED、CANCELLED 和 COMPLETED 的允许行为在服务端强制执行；COMPLETED 继续使用 FD-007 correction 路径。
- UI 显示替换请求状态、停止确认状态、原因及新旧任务链接；授权仅沿用 OPERATOR/ADMIN，不新增角色。
- 数据迁移与 SQL 在 SQLite、PostgreSQL 上兼容；迁移执行状态必须按实际验证证据报告。

## Verification

- Independent tests：可替换状态矩阵、权限、原因校验、版本/lease 冲突、幂等提交、事务回滚、替换审计与任务字段复制。
- Worker 协议测试：ACK 前不可领取替代任务；ACK 与自然完成/失败竞争；重复或错误 lease ACK；旧 lease 无法完成、发布结果或触发依赖。
- 依赖图测试：前置依赖复制、未开始后续任务重接、已开始后续任务拒绝且无部分写入。
- UI/controller 测试：可见性、无权限拒绝、Git revision 校验、请求中/冲突/完成展示及链接。
- SQLite 和 PostgreSQL 均需执行增量迁移并验证既有数据读取；静态 SQL 检查不能替代迁移执行证据。
- Worker 实施见 `docs/features/reports/FD-008-implementation-r1.md`。`git diff --check` 通过；两次离线 compile-only 均在 JVM 启动阶段因原生内存不足失败，未进入源码编译。未运行测试、迁移或运行时验证；Tester 与 PM 必须按实际证据判定。
- 用户于 2026-10-07 明确要求“现在内存紧张，就豁免测试”。Tester r1 的 36 个场景与 Tester r2 的 38 个场景均未运行（r2 为 0/38、0%），分支覆盖未测量；PM 的覆盖例外仅允许继续独立静态审查，不豁免本 FD 的任何行为验收条件。编译失败与 SQLite/PostgreSQL 迁移未执行仍为残余风险。
- Reviewer r1 对事件 `FD-008-000008-review-requested` 提出 F-01（待停止期间指令变更）和 F-02（页面缺少 ACK 证据），见 `docs/features/reviews/FD-008-review-r1.md`。Worker r2 在 `cc513c9` 保存并校验受理时的 instruction revision，在同一 ACK 事务中记录确认时间，并于页面区分待确认、已确认与无需确认；普通心跳会增加 task version，因此不能用 version 相等判定指令未变。本轮仅完成静态修复，未重新编译、运行测试或迁移。

## Sources

- 用户决定：更换 Workspace 前取消原任务并创建新任务；本 FD 选择活动 Worker 必须先确认停止。
- `docs/features/archive/FD-006/FD-006_WORKSPACE_GIT.md`（Workspace registry 与 Git 目标能力）
- `docs/features/archive/FD-007/FD-007_TASK_INSTRUCTION_REVISION_AND_OPERATOR_CONTROL.md`（task revision、租约 fencing、stop acknowledgement、权限与 correction 约定）
- `src/main/java/me/asu/aidev/work/domain/WorkTask.java`
- `src/main/java/me/asu/aidev/work/application/WorkTaskService.java`
- `src/main/java/me/asu/aidev/work/port/TaskDependencyRepository.java`
- `src/main/java/me/asu/aidev/execution/application/TaskExecutionService.java`
- `src/main/java/me/asu/aidev/worker/runtime/WorkerRuntime.java`
- `src/main/resources/db/migration/V19__task_instruction_revision_control.sql`

**Completed:** 2026-10-07
