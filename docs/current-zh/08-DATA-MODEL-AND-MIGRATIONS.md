# 数据模型与迁移

## 1. 设计原则

不会因为“规范化”就增加表。只有独立生命周期、并发 Ownership、独立关系索引、或无法从现有数据恢复的审计历史，才值得增加表。

当前：**9 张表、Flyway V1～V14**。

## 2. 9 张表

### `worker`

Worker 注册、runtime type、capabilities、maxConcurrency、activeLeases、status、heartbeat。

### `work_task`

Task 当前 Authority：Type/State/subject/capabilities/priority/lease/instruction/repositoryRef/baseRevision。

当前 Lease 状态就在此表，不另外创建 task_lease。

### `work_task_dependency`

DAG dependency：

```text
task_id -> depends_on_task_id
```

### `execution_attempt`

每次执行历史：worker/lease/session、start/finish、workspace/branch/base/result revision、changedFiles、publicationRef、completion report、engineering gate、termination reason。

Retry 创建新 Attempt。

### `approval_request`

独立 Human Approval lifecycle。

### `autonomous_run`

Run lifecycle/current authority，以及 failure/recovery、operator control、per-run maxParallelism/activeSlots。

### `autonomous_run_event`

保存无法从 snapshot 恢复的 Run 历史：FAILED/RECOVERED/PAUSED/RESUMED/CANCELLED/MAX_PARALLELISM_CHANGED。

### `fleet_admission_budget`

全局 scheduling ceiling authority。Fleet 活动任务数仍以真实 CLAIMED/RUNNING Task 为准，没有第二套 global counter。

### `fleet_admission_budget_event`

记录 Fleet ceiling runtime 修改历史。

## 3. Migration

```text
V1  core Worker/Task/Dependency/Attempt
V2  instruction
V3  isolated workspace/result revision
V4  publication ref
V5  completion verdict/summary
V6  engineering gate
V7  human approval
V8  autonomous run
V9  operator control
V10 run events
V11 run concurrency budget
V12 runtime run scheduling audit
V13 fleet budget
V14 runtime fleet scheduling audit
```

## 4. SQLite

```text
maximum pool size = 1
transaction_mode = IMMEDIATE
foreign_keys = ON
busy_timeout = 5000
```

时间统一使用 epoch milliseconds，便于 SQLite/PostgreSQL 使用同一核心 Migration。
