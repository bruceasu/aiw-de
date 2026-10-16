# 系统架构

## 1. 总体架构

AI Worker R8 是 **Modular Monolith Control Plane + 外部 AI Workers**。

```text
                        用户 / Operator / Approver
                                  |
                                  v
+----------------------------------------------------------------+
|                    Java Control Plane                           |
|                                                                |
| autonomous | work | execution | development | approval | fleet |
|                           |                                    |
|                       JDBC + Flyway                            |
+---------------------------+------------------------------------+
                            |
                      SQLite / PostgreSQL

                            ^
                            |
                    Worker Protocol v1
                            |
         +------------------+-------------------+
         |        |         |        |          |
      Python      TS        JS       Go        Java
         |        |         |        |          |
         +------------------+-------------------+
                            |
                     Codex SDK / CLI
                            |
                  Git repo + isolated worktree
```

Control Plane 不会在一个 Handler 里“调用下一个 Agent”。上游完成后只创建新的 Task/Dependency，后续由空闲 Worker 自己 Pull。

## 2. 主要模块

### `work`

管理 WorkTask 与 DAG：Task Type/State、dependency readiness、required capabilities、repository/base revision，以及任务当前 lease ownership。

### `worker`

管理 Worker 注册、runtime type、capabilities、`ACTIVE/DRAINING/OFFLINE`、maxConcurrency 与 active lease accounting。

### `execution`

管理 Pull claim、Lease Token、ExecutionAttempt、start/heartbeat/complete/fail、lease expiry recovery、result handoff、completion handler dispatch。

### `workflow`

管理 Flexible Workflow Plan 的 draft/version/confirmation、有序步骤及任务/依赖生成、jump/stop 与人工 complete/cancel 控制、任务执行和替换协调。

当前 Schema 不再持久化单独的固定 `autonomous_run` aggregate。V1 中历史 `autonomous_run` DDL 会在基线结束前删除。

### `development`

管理 Code Review、Verification、Repair Loop 与 Engineering Gate。

### `approval`

管理 Human Approval、Approval Policy 与 Telegram Adapter。

### `fleet`

管理 Fleet Dashboard、全局并发上限以及运行时 Fleet scheduling control/audit。

## 3. Authority 分离

| 事项 | Authority |
|---|---|
| Task 状态和依赖 | Java Control Plane + DB |
| Task ownership | `READY -> CLAIMED` 原子更新 + Lease Token |
| Worker capacity | worker row + transaction |
| Plan 规格和生命周期 | `flexible_workflow_plan` |
| Plan 步骤和执行 ownership | `work_task` + `work_task_dependency` |
| Plan 控制/人工结果 | `flexible_workflow_control` + `flexible_workflow_human_outcome` |
| 来源问题的已确认答复 | `workflow_question_decision`（绑定来源文件/哈希及计划版本） |
| 单轮修复授权 | `workflow_repair_authorization`（人工确定范围，可委托独立 Worker 决策） |
| 代码结果 | Git `resultRevision` |
| Review/Verification | ExecutionAttempt Completion Report |
| Engineering acceptance | Engineering Gate |
| Human authorization | `approval_request` |
| Fleet ceiling | `fleet_admission_budget` |
| AI 执行过程 | Worker / AI Runtime |

因此模型说“完成了”并不等于系统接受；必须通过正确 Lease 完成 Task。

## 4. Worker Claim 路径

```text
READY tasks
   ↓ capability match
   ↓ priority + aging + run fairness
   ↓ run execution policy
   ↓ per-run admission
   ↓ fleet admission
   ↓ worker maxConcurrency
   ↓ atomic READY -> CLAIMED
```

排序决定“先尝试谁”，最终 DB 原子更新决定“谁真正获得 Ownership”。

## 5. 并发获取顺序

Autonomous Task 的 admission 顺序固定为：

```text
Run Budget -> Fleet Budget -> Worker Capacity -> Task Ownership
```

Run 使用持久化 `active_slots` 作为 semaphore；Fleet 不保存第二套 active counter，而是在全局 budget row 锁定期间统计真实 `CLAIMED/RUNNING` task。

## 6. 数据库

当前为 28 张应用表，Flyway V1～V5。V1 是全新数据库基线，文件中包含历史 DDL，并在完成前删除退役的固定 Run 表。V4 新增清理后保留的单轮修复授权；V5 新增追加保存、随计划删除的已确认问题答复。字段、约束和数据流见[数据库字典](08-DATA-MODEL-AND-MIGRATIONS.md)。

完整表/字段字典、表关系、迁移基线和数据流转见[数据模型与迁移](08-DATA-MODEL-AND-MIGRATIONS.md)。

## 7. Local 与 Shared 模式

### SQLite

默认 profile 为 `sqlite`，Hikari pool 固定为 1，transaction mode 为 `IMMEDIATE`。适合开发、演示和单 Control Plane。

### PostgreSQL

启用非 sqlite profile 并配置 `AIW_DB_URL/USER/PASSWORD` 后使用基础 PostgreSQL 配置。代码已实现 PostgreSQL dialect/locking，但正式多实例部署前仍应完成专门的并发/Chaos 压测。
