# 数据模型与迁移

## 1. 设计原则

不会因为“规范化”就增加表。只有独立生命周期、并发 Ownership、独立关系索引、或无法从现有数据恢复的审计历史，才值得增加表。

当前开发基线：**单个 Flyway V1 文件**，仅用于空数据库初始化。

## 2. 主要表

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

### `web_console_plan_archive`

此表属于旧迁移历史，在单文件 V1 末尾与旧 Run 权威表一并删除；当前产品不再创建固定自主 Run。新的计划归档使用
`web_console_plan_archive`，保存归档操作人、时间和可撤销的 30 天删除标记。
到期清理仅删除 Control Plane 数据库中的计划及关联记录，外部工作目录和 Git 成果保留。

计划归档只控制当前列表的显示。只有已停止、已收回，或最终步骤通过评审且所有任务结束的计划可归档。
管理员可撤销未到期的删除标记；到期后后台按计划独立清理，若有未结束任务或外部引用，事务回滚并重试。

## 3. Migration

`src/main/resources/db/migration/V1__initial_schema.sql` 按原 V1～V21 顺序保留建表和变更 SQL，
最后加入计划控制与归档表，并删除旧固定 Run 表。开发库必须以**空数据库**重新初始化；已有 Flyway
历史或数据不能直接沿用这个新基线。重建前自行备份需要保留的数据。

## 4. SQLite

```text
maximum pool size = 1
transaction_mode = IMMEDIATE
foreign_keys = ON
busy_timeout = 5000
```

时间统一使用 epoch milliseconds，便于 SQLite/PostgreSQL 使用同一核心 Migration。
