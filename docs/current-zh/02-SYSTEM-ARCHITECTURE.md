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

当前运行代码仍使用 `autonomous_run`、`autonomous_run_event` 和 `web_console_run_archive`，因此新基线会保留这些表。

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

当前 Schema 使用一个 Flyway V1 基线初始化全新空数据库，只包含当前 Java 运行代码引用的表及必要的约束、索引和初始记录。已有数据库不会被迁移或重置；请为该基线使用新数据库。详见[数据模型与初始 Schema](08-DATA-MODEL-AND-MIGRATIONS.md)。

初始 Schema 和数据持久化边界见[数据模型与初始 Schema](08-DATA-MODEL-AND-MIGRATIONS.md)。

## 7. Local 与 Shared 模式

### SQLite

默认 profile 为 `sqlite`，Hikari pool 固定为 1，transaction mode 为 `IMMEDIATE`。适合开发、演示和单 Control Plane。

### PostgreSQL

启用非 sqlite profile 并配置 `AIW_DB_URL/USER/PASSWORD` 后使用基础 PostgreSQL 配置。代码已实现 PostgreSQL dialect/locking，但正式多实例部署前仍应完成专门的并发/Chaos 压测。
