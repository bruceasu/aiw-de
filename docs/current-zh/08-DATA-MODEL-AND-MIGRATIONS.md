# 数据模型、数据库字典与迁移

本文描述 Flyway 当前迁移在**空数据库上创建的结构**。若本文与实现有差异，以 `src/main/resources/db/migration` 中的 SQL 为准。

## 1. 当前 Schema 与迁移基线

当前应用 Schema 共 **28 张表**（不含 Flyway 自己的 `flyway_schema_history`）。空库执行五个 Flyway migration：V1～V5。`V1__initial_schema.sql` 是开发用全新基线，按顺序保留历史 V1–V21 DDL，最后删除已退役的固定 Run 表。文件内部的历史版本号不是独立 Flyway migration。

因此，当前 Schema **不包含** `autonomous_run` 和 `autonomous_run_event`；它们在基线中创建后又被删除，属于历史迁移过程。当前计划模型是 Flexible Workflow Plan、执行任务及其控制记录。除非已有经过评审的迁移/发布方案明确支持，否则不要把该基线直接用于已有数据库的原地升级。

| Flyway migration | 当前作用 |
| --- | --- |
| V1 `initial_schema` | 创建核心任务、Worker、工作流计划、任务控制、工作区、审批、Fleet、Knowledge 等表；文件同时包含历史 DDL，并在结束前删除退役的固定 Run 表。 |
| V2 `workflow_human_outcome` | 保存对 Flexible Workflow Plan 的人工完成/取消决定。 |
| V3 `task_outputs` | 保存输出 bundle/文件、发布计划、生产者/消费者关系及评审来源。 |
| V4 `workflow_repair_authorization` | 保存单轮修复授权、可选代理决策及保留的授权历史。 |
| V5 `workflow_question_decision` | 追加保存绑定不可变来源问题和计划版本的已确认答复。 |

所有 `_epoch_ms` 时间字段均为 Unix epoch 毫秒。部分 JSON 和列表序列化为文本；具体编码契约以对应 DTO/Repository 为准。

## 2. 表与字段字典

下表中的类型为 SQL 声明类型。`PK`、`FK`、`UQ`、`NN` 分别表示主键、外键、唯一、非空。未标 `NN` 的字段允许为空。约束和索引在表后概述。

### Worker 与任务执行

#### `worker` — Worker 注册与容量快照

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | VARCHAR(128), PK | 稳定 Worker ID。 |
| `runtime_type` | VARCHAR(64), NN | Worker 运行时类型。 |
| `capabilities` | VARCHAR(2048), NN | 序列化后的能力集合。 |
| `max_concurrency` | INTEGER, NN | 最大并发租约数。 |
| `active_leases` | INTEGER, NN | 事务维护的当前租约数。 |
| `status` | VARCHAR(32), NN | `ACTIVE`、`DRAINING` 或 `OFFLINE`。 |
| `registered_at_epoch_ms` | BIGINT, NN | 注册时间。 |
| `last_heartbeat_at_epoch_ms` | BIGINT, NN | 最近心跳时间。 |
| `updated_at_epoch_ms` | BIGINT, NN | 最近注册/状态变更时间。 |

#### `work_task` — 当前任务权威与租约

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | VARCHAR(128), PK | Task ID。 |
| `task_type` | VARCHAR(64), NN | 任务类型。 |
| `state` | VARCHAR(32), NN | `BLOCKED`、`READY`、`CLAIMED`、`RUNNING`、`COMPLETED`、`FAILED` 或 `CANCELLED`。 |
| `subject_type` | VARCHAR(64), NN | 所属工作流/subject 类型。 |
| `subject_id` | VARCHAR(512), NN | 所属计划、run 或 subject 标识。 |
| `required_capabilities` | VARCHAR(2048), NN | 序列化的能力要求。 |
| `skill_ref` | VARCHAR(512) | 可选的 skill/指令引用。 |
| `priority` | INTEGER, NN | 调度优先级。 |
| `version` | BIGINT, NN | 乐观并发版本。 |
| `attempt_no` | INTEGER, NN | 当前/最近一次执行序号。 |
| `current_worker_id` | VARCHAR(128), FK → `worker.id` | 当前租约所有者；未租用时为空。 |
| `lease_token` | VARCHAR(128) | 当前不透明租约凭证。 |
| `lease_acquired_at_epoch_ms` | BIGINT | 租约取得时间。 |
| `lease_expires_at_epoch_ms` | BIGINT | 租约过期时间。 |
| `lease_heartbeat_at_epoch_ms` | BIGINT | 最近租约心跳时间。 |
| `created_at_epoch_ms` | BIGINT, NN | 任务创建时间。 |
| `updated_at_epoch_ms` | BIGINT, NN | 最近任务更新时间。 |
| `instruction` | TEXT, NN | 当前可执行指令。 |
| `repository_ref` | VARCHAR(256) | 逻辑仓库标识，不是机器本地路径。 |
| `base_revision` | VARCHAR(128) | 分配给任务的不可变源代码版本。 |
| `instruction_revision` | INTEGER, NN | 当前指令版本号。 |

租约字段必须全部为空或全部有值。当前租约状态保存在本表，不另设 `task_lease` 表。

#### `work_task_dependency` — 任务 DAG 依赖边

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `task_id` | VARCHAR(128), PK 组成部分, FK → `work_task.id` | 依赖其他任务的任务。 |
| `depends_on_task_id` | VARCHAR(128), PK 组成部分, FK → `work_task.id` | 前置任务。 |

#### `execution_attempt` — 每次执行的历史与结果证据

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | VARCHAR(128), PK | Attempt ID。 |
| `task_id` | VARCHAR(128), NN, FK → `work_task.id` | 执行的任务。 |
| `attempt_no` | INTEGER, NN | 从 1 开始的执行序号；与 `task_id` 组合唯一。 |
| `worker_id` | VARCHAR(128), NN, FK → `worker.id` | 执行该 Attempt 的 Worker。 |
| `lease_token` | VARCHAR(128), NN, UQ | 该次 Attempt 使用的租约凭证。 |
| `state` | VARCHAR(32), NN | `CLAIMED`、`RUNNING`、`COMPLETED`、`FAILED` 或 `EXPIRED`。 |
| `external_session_id` | VARCHAR(512) | 可选的外部 AI Runtime session。 |
| `created_at_epoch_ms` | BIGINT, NN | Claim/Attempt 创建时间。 |
| `started_at_epoch_ms` | BIGINT | 开始时间。 |
| `finished_at_epoch_ms` | BIGINT | 结束时间。 |
| `workspace_key` | VARCHAR(256) | 逻辑工作区 key。 |
| `branch_name` | VARCHAR(512) | Attempt 分支名。 |
| `base_revision` | VARCHAR(128) | 源 Git revision。 |
| `result_revision` | VARCHAR(128) | 产出的 Git revision。 |
| `changed_files` | TEXT | 序列化的变更文件列表。 |
| `publication_ref` | VARCHAR(2048) | 可移植的结果发布引用。 |
| `completion_verdict` | VARCHAR(32) | 完成/评审结论。 |
| `completion_summary` | TEXT | 完成/评审摘要。 |
| `engineering_gate_status` | VARCHAR(32) | 绑定到该精确结果的 Engineering Gate 状态。 |
| `engineering_gate_review_task_id` | VARCHAR(128) | Review 任务证据引用。 |
| `engineering_gate_verification_task_id` | VARCHAR(128) | Verification 任务证据引用。 |
| `engineering_gate_closed_at_epoch_ms` | BIGINT | Gate 关闭时间。 |
| `termination_reason` | VARCHAR(64) | Attempt 被显式/隐式终止的原因。 |
| `ai_profile_id` | VARCHAR(64) | 选中的 AI profile。 |
| `ai_profile_version` | VARCHAR(64) | 选中的 profile 版本。 |
| `ai_provider` | VARCHAR(120) | AI provider 名称。 |
| `ai_runtime` | VARCHAR(120) | Runtime 名称。 |
| `ai_model` | VARCHAR(120) | 模型名称。 |
| `ai_effort` | VARCHAR(120) | 推理/effort 设置。 |
| `ai_fast` | BOOLEAN | 可选的 fast 模式设置。 |
| `ai_selection_reason` | VARCHAR(512) | Profile 选择原因。 |
| `failure_category` | VARCHAR(32) | 结构化失败类别。 |
| `instruction_revision` | INTEGER, NN | 本次执行所用指令版本。 |
| `stop_requested_at_epoch_ms` | BIGINT | 停止请求时间。 |
| `stop_acknowledged_at_epoch_ms` | BIGINT | Worker 确认停止时间。 |

Attempt 是历史证据。Retry 会新增一行，不覆盖之前的执行记录。

#### `work_task_instruction_revision` — 不可变指令版本

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `task_id` | VARCHAR(128), PK 组成部分, FK → `work_task.id` | 所属任务；任务删除时级联删除。 |
| `revision` | INTEGER, PK 组成部分 | 从 1 开始的版本号。 |
| `instruction` | TEXT, NN | 此版本指令内容。 |
| `actor` | VARCHAR(256), NN | 创建该版本的操作者。 |
| `reason` | VARCHAR(2000), NN | 修改原因。 |
| `predecessor_revision` | INTEGER | 前一版本；初始版本为空。 |
| `created_at_epoch_ms` | BIGINT, NN | 版本创建时间。 |

#### `work_task_control_event` — 任务操作审计

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | VARCHAR(128), PK | 操作事件 ID。 |
| `task_id` | VARCHAR(128), NN, FK → `work_task.id` | 被操作任务。 |
| `attempt_id` | VARCHAR(128), FK → `execution_attempt.id` | 可选的相关 Attempt。 |
| `action` | VARCHAR(32), NN | 请求的控制动作。 |
| `actor` | VARCHAR(256), NN | 操作者或系统 actor。 |
| `reason` | VARCHAR(2000), NN | 必填原因。 |
| `warning_confirmed` | INTEGER, NN | 是否明确确认警告（`0`/`1`）。 |
| `created_at_epoch_ms` | BIGINT, NN | 事件时间。 |

#### `work_task_correction` — 已完成任务与修正任务的关系

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `completed_task_id` | VARCHAR(128), PK 组成部分, FK → `work_task.id` | 原已完成任务。 |
| `correction_task_id` | VARCHAR(128), PK 组成部分, UQ, FK → `work_task.id` | 后续修正任务；最多属于一个修正关系。 |
| `created_by` | VARCHAR(256), NN | 发起者。 |
| `reason` | VARCHAR(2000), NN | 修正原因。 |
| `created_at_epoch_ms` | BIGINT, NN | 关系创建时间。 |

#### `work_task_workspace_replacement` — 替换仓库/基准版本流程

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | VARCHAR(128), PK | 替换请求 ID。 |
| `source_task_id` | VARCHAR(128), NN, FK → `work_task.id` | 原任务。 |
| `replacement_task_id` | VARCHAR(128), UQ, FK → `work_task.id` | 替换成功后创建/关联的新任务。 |
| `status` | VARCHAR(32), NN | `PENDING_STOP`、`COMPLETED`、`CONFLICT` 或 `REJECTED`。 |
| `actor` | VARCHAR(256), NN | 请求操作者。 |
| `reason` | VARCHAR(2000), NN | 替换原因。 |
| `old_repository_ref` | VARCHAR(120) | 原逻辑仓库。 |
| `old_base_revision` | VARCHAR(64) | 原基准版本。 |
| `new_repository_ref` | VARCHAR(120) | 目标逻辑仓库。 |
| `new_base_revision` | VARCHAR(64) | 目标基准版本。 |
| `requested_revision` | VARCHAR(256) | 用户提供的 revision 请求。 |
| `source_version` | BIGINT, NN | 请求时记录的源任务版本。 |
| `source_instruction_revision` | INTEGER, NN | 请求时记录的指令版本。 |
| `lease_worker_id` | VARCHAR(128) | 如有租约，记录需停止的 Worker。 |
| `lease_token` | VARCHAR(128) | 用于安全匹配停止确认的租约凭证。 |
| `stop_acknowledged_at_epoch_ms` | BIGINT | 停止确认时间。 |
| `conflict_reason` | VARCHAR(2000) | 冲突详情。 |
| `requested_at_epoch_ms` | BIGINT, NN | 请求时间。 |
| `resolved_at_epoch_ms` | BIGINT | 解决时间。 |

### 工作流计划与控制

#### `flexible_workflow_plan` — 持久化计划规格与状态

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | VARCHAR(128), PK | 计划 ID。 |
| `state` | VARCHAR(32), NN | `DRAFT`、`CONFIRMED`、`WITHDRAWN` 或 `STOPPED`。 |
| `version` | BIGINT, NN | 乐观并发版本。 |
| `spec_json` | TEXT, NN | 计划目标、来源和有序步骤的序列化内容。 |
| `created_by` | VARCHAR(256), NN | 创建者。 |
| `updated_by` | VARCHAR(256), NN | 最近更新者。 |
| `reason` | VARCHAR(2000), NN | 当前/最近操作原因。 |
| `created_at_epoch_ms` | BIGINT, NN | 创建时间。 |
| `updated_at_epoch_ms` | BIGINT, NN | 最近更新时间。 |

#### `work_task_skill_preference` — 任务可选偏好技能

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `task_id` | VARCHAR(128), PK, FK → `work_task.id` | 设置偏好的任务。 |
| `preferred_skills` | TEXT, NN | 序列化的偏好技能列表。 |

#### `flexible_workflow_control` — Plan 跳转/停止与待确认租约停止

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | VARCHAR(128), PK | 控制请求 ID。 |
| `plan_id` | VARCHAR(128), NN, FK → `flexible_workflow_plan.id` | 被控制的 Plan。 |
| `action` | VARCHAR(16), NN | `JUMP` 或 `STOP`。 |
| `from_step_index` | INTEGER, NN | 请求时当前步骤序号。 |
| `target_step_index` | INTEGER | 跳转目标；仅 `JUMP` 必填。 |
| `task_id` | VARCHAR(128), FK → `work_task.id` | 受影响的当前任务。 |
| `lease_worker_id` | VARCHAR(128) | 持有受影响租约的 Worker。 |
| `lease_token` | VARCHAR(128) | 用于匹配停止确认的租约凭证。 |
| `status` | VARCHAR(16), NN | `PENDING_STOP`、`APPLIED` 或 `CONFLICT`。 |
| `actor` | VARCHAR(256), NN | 发起者。 |
| `reason` | VARCHAR(2000), NN | 控制原因。 |
| `requested_at_epoch_ms` | BIGINT, NN | 请求时间。 |
| `applied_at_epoch_ms` | BIGINT | 执行控制时间。 |

#### `flexible_workflow_skipped_step` — 跳转时跳过的步骤

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `plan_id` | VARCHAR(128), PK 组成部分, FK → `flexible_workflow_plan.id` | Plan。 |
| `step_index` | INTEGER, PK 组成部分 | 被跳过的有序步骤序号。 |
| `control_id` | VARCHAR(128), NN, FK → `flexible_workflow_control.id` | 触发跳过的跳转操作。 |

#### `flexible_workflow_human_outcome` — 人工完成/取消决定

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `plan_id` | VARCHAR(128), PK, FK → `flexible_workflow_plan.id` | 决策所属 Plan。 |
| `outcome` | VARCHAR(16), NN | `COMPLETE` 或 `CANCEL`。 |
| `status` | VARCHAR(16), NN | `PENDING` 或 `APPLIED`。 |
| `actor` | VARCHAR(256), NN | 决策人。 |
| `reason` | VARCHAR(2000), NN | 决策原因。 |
| `decided_at_epoch_ms` | BIGINT, NN | 决策时间。 |

#### `web_console_plan_archive` — 计划归档与延迟删除标记

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `plan_id` | VARCHAR(128), PK, FK → `flexible_workflow_plan.id` | 已归档的计划。 |
| `archived_by` | VARCHAR(256), NN | 归档操作者。 |
| `archived_at_epoch_ms` | BIGINT, NN | 归档时间。 |
| `delete_requested_by` | VARCHAR(256) | 请求延迟删除的管理员。 |
| `delete_requested_at_epoch_ms` | BIGINT | 删除请求时间。 |
| `delete_after_epoch_ms` | BIGINT | 最早允许删除的时间。 |

三个删除标记字段必须全部为空或全部填写，且 `delete_after_epoch_ms` 晚于请求时间。

#### `workflow_repair_authorization` — 单轮修复授权及历史

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | VARCHAR(128), PK | 授权 ID，由服务以 `RA-` 前缀生成。 |
| `plan_id` | VARCHAR(128), NN | 逻辑计划引用；刻意不设外键，使历史能在计划清理后保留。 |
| `plan_version` | BIGINT, NN | 授权时预留的计划版本，消费授权前再次检查。 |
| `source_review_id` | VARCHAR(128), NN | 需要修复的来源评审任务；逻辑引用，无外键。 |
| `source_task_version` | BIGINT, NN | 来源评审任务的版本快照，用于并发检查。 |
| `scope` | TEXT, NN | 人类批准的修复范围；服务接受 1～4000 字符。 |
| `reason` | VARCHAR(2000), NN | 必填授权原因。 |
| `granted_by` | VARCHAR(256), NN | 授权的人类操作者。 |
| `delegate_worker_id` | VARCHAR(128) | 指定的独立决策 Worker；为空表示直接按人工决定消费。 |
| `decision_task_id` | VARCHAR(128), UQ | 代理决策任务；直接消费时为空。 |
| `repair_task_id` | VARCHAR(128) | 授权消费后创建的修复执行任务。 |
| `round_budget` | INTEGER, NN | 固定为 `1`，由 SQL CHECK 约束。 |
| `status` | VARCHAR(16), NN | `PENDING`、`CONSUMED`、`DENIED`、`REVOKED`、`EXPIRED` 或 `STALE`。 |
| `created_at_epoch_ms` | BIGINT, NN | 授权创建时间。 |
| `expires_at_epoch_ms` | BIGINT, NN | 截止时间；当前服务设置为创建后一小时。 |
| `decided_at_epoch_ms` | BIGINT | 待处理授权结束的时间，包括撤销或识别到过期。 |

`idx_workflow_repair_pending` 保证每个计划最多一条 `PENDING` 授权；`idx_workflow_repair_history` 索引 `(plan_id, created_at_epoch_ms)`。计划、任务、Worker 引用均无数据库外键，保留的历史可能引用已删除实体。当前实现是单轮修复机制，尚不等于需求草案中的可编辑委托策略和可复用授权模板。

#### `workflow_question_decision` — 追加保存的已确认问题答复

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | VARCHAR(128), PK | 决定 ID，由服务以 `QD-` 前缀生成。 |
| `plan_id` | VARCHAR(128), NN, FK → `flexible_workflow_plan.id` | 所属计划；随计划级联删除。 |
| `question_key` | VARCHAR(64), NN | 来源任务 ID、换行符和问题 ID 拼接后的 SHA-256。 |
| `source_task_id` | VARCHAR(256), NN | 产出问题的任务；逻辑引用，无外键。 |
| `source_file_id` | VARCHAR(128), NN | 不可变输出文件 ID；逻辑引用，无外键。 |
| `source_hash` | VARCHAR(64), NN | 来源文件内容的 SHA-256。 |
| `question_id` | VARCHAR(160), NN | 来源输出中的问题 ID。 |
| `question_text` | TEXT, NN | 答复时的问题文本快照。 |
| `answer` | TEXT, NN | 已确认答复；服务接受 1～2000 字符，保存时去除首尾空白。 |
| `actor` | VARCHAR(256), NN | 从 Web Console 登录会话取得的确认人。 |
| `plan_version` | BIGINT, NN | 本次答复生效后的计划版本，即提交版本加一。 |
| `decided_at_epoch_ms` | BIGINT, NN | 确认时间。 |

`UNIQUE (plan_id, plan_version)` 限制每个计划版本只能对应一条答复，并非每个问题只能答复一次。重新答复会插入新行。`idx_workflow_question_decision` 索引 `(plan_id, question_key, plan_version)`。Repository 按计划版本倒序读取历史；问题面板仅在文件 ID 与哈希仍匹配时，使用该 key 最新的答复。历史报告和输出文件保持不变。

追加语义由服务/Repository 的写入路径实现，SQL 没有禁止 UPDATE/DELETE。计划保留期清理会显式删除这些记录，外键也声明了 `ON DELETE CASCADE`；这与清理后仍保留的修复授权历史不同。

### 审批、Fleet 调度、Knowledge 与工作区

#### `approval_request` — 独立人工审批生命周期

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | VARCHAR(128), PK | 审批请求 ID。 |
| `source_task_id` | VARCHAR(128), NN, UQ, FK → `work_task.id` | 来源任务/结果；每个来源任务最多一条审批。 |
| `subject_type` | VARCHAR(64), NN | Subject 类型。 |
| `subject_id` | VARCHAR(512), NN | Subject 标识。 |
| `result_revision` | VARCHAR(128), NN | 提交审批的精确结果版本。 |
| `engineering_gate_closed_at_epoch_ms` | BIGINT, NN | 创建请求时记录的 Gate 关闭时间。 |
| `status` | VARCHAR(32), NN | `PENDING`、`APPROVED` 或 `REJECTED`。 |
| `requested_by` | VARCHAR(256), NN | 请求人。 |
| `channel` | VARCHAR(32), NN | `API`、`TELEGRAM`、`TEAMS`、`WEB` 或 `CLI`。 |
| `channel_ref` | VARCHAR(1024) | 渠道引用。 |
| `requested_at_epoch_ms` | BIGINT, NN | 请求时间。 |
| `decided_by` | VARCHAR(256) | 决策人；待审批时为空。 |
| `decision_note` | VARCHAR(4096) | 决策说明。 |
| `decided_at_epoch_ms` | BIGINT | 决策时间；待审批时为空。 |

#### `fleet_admission_budget` — 全局并发上限单例

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | VARCHAR(32), PK | 单例 key；当前行是 `GLOBAL`。 |
| `max_parallelism` | INTEGER, NN | Fleet 并发上限；0 表示不限。 |
| `updated_at_epoch_ms` | BIGINT, NN | 最近更新时间。 |

该行用于串行化 Fleet admission。活动任务数由 `work_task` 当前状态统计，不另外保存全局活动数计数器。

#### `fleet_admission_budget_event` — Fleet 上限变更追加审计

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `budget_id` | VARCHAR(32), PK 组成部分, FK → `fleet_admission_budget.id` | Budget 行。 |
| `sequence_no` | BIGINT, PK 组成部分 | Budget 内事件顺序。 |
| `event_type` | VARCHAR(64), NN | 当前为 `MAX_PARALLELISM_CHANGED`。 |
| `actor` | VARCHAR(256), NN | 操作者/系统 actor。 |
| `previous_max_parallelism` | INTEGER, NN | 变更前上限。 |
| `max_parallelism` | INTEGER, NN | 变更后上限。 |
| `active_leases` | INTEGER, NN | 变更时观察到的活动租约数。 |
| `reason` | VARCHAR(4096) | 变更原因。 |
| `occurred_at_epoch_ms` | BIGINT, NN | 事件时间。 |

#### `knowledge_document` — 持久化分析/规划文档

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | VARCHAR(128), PK, FK → `work_task.id` | 所属任务/文档 ID。 |
| `run_id` | VARCHAR(128), NN | 工作流 run/plan 上下文。 |
| `document_type` | VARCHAR(32), NN | `REQUIREMENT_ANALYSIS`、`SOLUTION_ARCHITECTURE` 或 `IMPLEMENTATION_PLANNING`。 |
| `repository_ref` | VARCHAR(120) | 逻辑仓库。 |
| `base_revision` | VARCHAR(128) | 源版本。 |
| `result_revision` | VARCHAR(128) | 可用时记录结果版本。 |
| `attempt_no` | INTEGER, NN | 产出文档的 Attempt 序号。 |
| `content_hash` | VARCHAR(64), NN | 文档内容 hash。 |
| `markdown` | TEXT, NN | 文档正文。 |
| `created_at_epoch_ms` | BIGINT, NN | 创建时间。 |

#### `workspace_registry` — 工作区根目录配置

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | VARCHAR(80), PK | Workspace ID。 |
| `display_name` | VARCHAR(160), NN | 面向操作者的名称。 |
| `root_path` | VARCHAR(2048), NN | 本地工作区根路径。 |
| `target_mode` | VARCHAR(16), NN | `ROOT` 或 `CHILD`。 |
| `enabled` | INTEGER, NN | 是否启用（`0`/`1`）。 |
| `created_at_epoch_ms` | BIGINT, NN | 创建时间。 |

#### `workspace_repo_registry` — 逻辑仓库到本地工作区映射

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `repository_ref` | VARCHAR(120), PK | 任务中使用的逻辑仓库名。 |
| `workspace_id` | VARCHAR(80), NN, FK → `workspace_registry.id` | 所属 Workspace。 |
| `repo_id` | VARCHAR(80), NN | Workspace 内仓库 key。 |
| `relative_path` | VARCHAR(1024), NN | 相对 Workspace 根目录的路径。 |
| `git_warning` | VARCHAR(2048) | 配置/检查警告。 |
| `enabled` | INTEGER, NN | 是否启用（`0`/`1`）。 |

`(workspace_id, repo_id)` 唯一。

#### `direct_workspace_lock` — 直接目录 Claim 互斥锁

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `repository_ref` | VARCHAR(120), PK | 需要串行化的逻辑仓库标识。 |

### 任务输出

#### `task_output_bundle` — 已登记的 Attempt 输出清单

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `attempt_id` | VARCHAR(128), PK | 产出该 Bundle 的 Attempt。 |
| `task_id` | VARCHAR(128), NN | Task ID。 |
| `repository_ref` | VARCHAR(256), NN | 仓库引用。 |
| `base_revision` | VARCHAR(64) | Git 基准版本。 |
| `result_revision` | VARCHAR(64) | Git 结果版本。 |
| `directory` | TEXT, NN | 输出目录/引用。 |
| `created_at_epoch_ms` | BIGINT, NN | 登记时间。 |
| `manifest_json` | TEXT, NN | 序列化 Bundle 清单。 |
| `execution_state` | VARCHAR(32), NN | Bundle 执行/登记状态，默认 `REGISTERED`。 |

#### `task_output_file` — 输出文件元数据与内容

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | VARCHAR(128), PK | 文件 ID。 |
| `attempt_id` | VARCHAR(128), NN, FK → `task_output_bundle.attempt_id` | 所属 Bundle/Attempt。 |
| `relative_path` | VARCHAR(512), NN | 相对输出路径。 |
| `kind` | VARCHAR(32), NN | 文件种类。 |
| `content_hash` | VARCHAR(64), NN | 内容摘要。 |
| `byte_count` | BIGINT, NN | 原始字节数。 |
| `content_base64` | TEXT, NN | 持久化在数据库中的 Base64 文件内容。 |

`(attempt_id, relative_path)` 唯一。

#### `task_output_publication` — 受控发布计划

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `plan_id` | VARCHAR(128), PK | Workflow Plan ID。 |
| `file_id` | VARCHAR(128), NN, FK → `task_output_file.id` | 计划发布的文件。 |
| `review_task_id` | VARCHAR(128), NN | Review 任务引用。 |
| `target_path` | VARCHAR(512), NN | 目标路径。 |
| `actor` | VARCHAR(256), NN | 创建计划的操作者。 |
| `reason` | VARCHAR(2000), NN | 发布原因。 |
| `created_at_epoch_ms` | BIGINT, NN | 创建时间。 |

#### `task_output_input` — 消费输出的来源追踪

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `consumer_attempt_id` | VARCHAR(128), PK 组成部分 | 消费输出的 Attempt。 |
| `source_attempt_id` | VARCHAR(128), PK 组成部分 | 产出源输出的 Attempt。 |

#### `task_output_review` — 输出评审来源记录

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `source_attempt_id` | VARCHAR(128), PK 组成部分 | 被评审输出所属 Attempt。 |
| `review_task_id` | VARCHAR(128), PK 组成部分 | Review Task。 |
| `review_attempt_id` | VARCHAR(128), NN | Review Attempt。 |
| `review_worker_id` | VARCHAR(128), NN | 执行评审的 Worker。 |
| `source_worker_id` | VARCHAR(128), NN | 产出源结果的 Worker。 |
| `reviewed_at_epoch_ms` | BIGINT, NN | 评审时间。 |

## 3. 表关系、约束与索引

- Worker 可先后执行多个任务；`work_task.current_worker_id` 只表示当前租约所有者，`execution_attempt` 保留历史分配记录。
- `work_task_dependency` 保存前置关系。任务仅在其前置任务满足工作流规则后才可执行。
- `approval_request.source_task_id` 唯一。审批保存精确的 `result_revision` 与 Gate 关闭时间，后续结果不会改变审批对象。
- `flexible_workflow_plan` 保存计划规格；生成后的执行权威在 `work_task` 与 `work_task_dependency`。技能偏好、控制请求、跳过步骤、人工结论和归档各自单独存储，以支持各自更新/审计需要。
- Workspace registry 将任务中的逻辑仓库引用映射到本地目录。逻辑 repository ref 不携带机器路径。
- Output bundle/file 保存交付内容及来源；任务结果版本仍指向 Git，与文件发布记录分离。
- SQL CHECK 约束状态枚举、非负计数、租约字段完整性、决定状态一致性、步骤/控制组合和版本关系。准确条件见迁移 SQL。
- 索引支持 READY 排序、subject 查询、依赖遍历、租约过期、Worker 租约、Attempt 历史、审批查询、Fleet 事件、Knowledge 查询、Workspace 查询、任务审计、替换/控制 pending 唯一约束和归档到期扫描。准确索引定义见迁移文件。

## 4. 数据在数据库中的流转

### 确认来源问题

1. 问题面板选择每个步骤最新已完成的执行/评审任务，读取其已登记的 `task_output_bundle` / `task_output_file`。问题取自执行任务 `.json` 文件的 `openQuestions` 或 `payload.openQuestions`；评审文件可查看，但不作为问题提取来源。
2. Operator 提交当前计划版本、问题 key、来源文件 ID/哈希、答复及显式确认；确认人由登录会话取得。服务锁定计划任务并检查当前来源和状态。未确认、归档或已关闭计划、待处理计划控制、未结束任务，以及当前版本未过期的待处理修复授权，都会阻止答复。
3. 在同一事务中，通过乐观版本更新保持计划为 `CONFIRMED`、版本加一，再追加一条对应新版本的 `workflow_question_decision`。过期提交返回冲突；旧答复不被覆盖。
4. 后续执行/评审任务、代理修复决策，以及失败任务重试时的新指令版本，会带入适用答复的文本快照。已有输出报告保持不可变。答复本身不授予执行、测试、网络或部署权限。确认内容上下文超过 8000 字符时，服务拒绝写入，避免静默丢弃答复。

### 消费修复授权

人类确认后预留计划/评审任务版本，插入 `PENDING` 授权。不指定代理时，服务立即尝试消费并创建一轮修复执行；指定代理时，创建 subject 为 `workflow-repair-authorization` 的决策 `work_task`，只允许指定的独立 Worker 决策。匹配 Worker 返回 `PASS` 后，仍需检查截止时间和计划/版本才能消费；非 PASS 或 Worker 不匹配会结束为 `DENIED`。结束操作仅更新仍为 `PENDING` 的记录，消费成功时写入 `repair_task_id`，同时记录 `decided_at_epoch_ms`。

人类撤销转为 `REVOKED`；截止时间或计划状态/版本变化在检查时可转为 `EXPIRED` 或 `STALE`。不能由截止字段推断存在自动过期定时器：尚未检查的记录可能仍显示 pending。历史授权在计划/任务清理后保留，问题答复则随所属计划删除。

### 其他持久化流程

```text
Operator 创建 Plan
  -> flexible_workflow_plan（DRAFT，spec_json）
  -> 确认并更新版本（CONFIRMED）
  -> 创建 work_task + work_task_dependency + 可选 work_task_skill_preference
  -> 前置条件满足后，任务进入 READY

Worker 注册/心跳
  -> worker
  -> Claim 事务检查准入、能力与容量
  -> work_task 从 READY 原子变为 CLAIMED 并写入租约字段
  -> 新增 execution_attempt（一条记录对应一次尝试）
  -> start/heartbeat 更新任务与 Attempt；记录停止请求/确认时间
  -> complete/fail/过期使任务与 Attempt 进入终态并释放 Worker 容量
  -> completion handler 保存证据和/或创建后续任务及依赖

实现/评审结果
  -> execution_attempt 保存 Git revision、变更文件、完成证据和 Gate 引用
  -> 可选 knowledge_document 保存分析/规划 Markdown
  -> approval_request 针对精确结果保存独立人工决定
  -> task_output_* 保存交付 Bundle/文件及生产、消费、评审来源

计划/操作者控制
  -> flexible_workflow_control / flexible_workflow_human_outcome
  -> work_task_control_event 与指令版本表保留任务级历史
  -> Workspace replacement 保存替换请求；安全停止后再创建/关联替换任务
```

事务服务决定实际写入顺序；上图表示持久化职责，不保证每个箭头都在同一事务中。Worker 不直接写 Control Plane 数据库。Git object/worktree 和 Workspace 文件位于关系型 Schema 之外；已登记的输出文件内容也可能以 Base64 存在 `task_output_file`。

## 5. 调度与权威

任务/租约状态和依赖分别以 `work_task`、`work_task_dependency` 为准；执行历史在 `execution_attempt`；Worker 容量在 `worker` 上事务预留。Autonomous/Flexible 工作的准入顺序为 Run/Plan policy、Fleet budget、Worker capacity、原子任务所有权。Fleet admission 锁定 `GLOBAL` 行并统计当前 `CLAIMED`/`RUNNING` 任务，不维护重复的实时活动计数。

旧固定 autonomous Run 表只出现在 V1 的历史 DDL 段，并在 migration 完成前删除。当前 Plan 生命周期与任务执行不能再描述为写入 `autonomous_run`。

## 6. 数据库兼容性与运维

V4、V5 追加表和索引，不改写已有报告。已与本仓库 V1～V3 历史对齐的数据库可由 Flyway 顺序应用新版本；这不代表兼容基线调整前的旧迁移历史。删除计划会删除问题答复，但保留修复授权。本次文档更新未执行迁移，也未验证实际 SQLite/PostgreSQL 数据库。

Schema 面向 SQLite 和 PostgreSQL。时间使用 epoch 毫秒；数据库专属锁/准入 SQL 由数据库 dialect 隔离。

本地 SQLite profile 使用单连接 Hikari pool、`IMMEDIATE` transaction mode、外键和 5 秒 busy timeout。Repository 代码必须遵守单连接限制。PostgreSQL 支持共享数据库操作；多 Control Plane 生产就绪仍需完成[测试与限制](12-TESTING-VALIDATION-AND-LIMITATIONS.md)中说明的并发/Chaos 验证。

Schema 变更必须新增 migration，不要修改已在环境中执行过的 migration。变更前备份数据。当前 V1 是全新数据库基线；已有数据库需先明确兼容与迁移方案，不能默认兼容。
