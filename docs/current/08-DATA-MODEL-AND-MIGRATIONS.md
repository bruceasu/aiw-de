# Data Model, Database Dictionary, and Migrations

This document describes the **current database created from an empty database** by the Flyway migrations in `src/main/resources/db/migration`. The SQL migrations are authoritative when this document and implementation differ.

## 1. Current schema and migration baseline

The current application schema has **28 tables** (excluding Flyway's own `flyway_schema_history` table). An empty database is initialized by five Flyway migrations: V1–V5. `V1__initial_schema.sql` is a development baseline that contains historical V1–V21 DDL in sequence and then removes retired fixed-Run tables. Those historical labels inside the file are not separately applied Flyway versions.

The current schema therefore does **not** include `autonomous_run` or `autonomous_run_event`; their creation and later removal in the baseline are historical implementation steps. Flexible workflow plans, their tasks, and related control tables are the current plan model. Do not apply this baseline over an existing database as an in-place upgrade unless a separately reviewed migration/rollout plan explicitly supports it.

| Flyway migration | Current effect |
| --- | --- |
| V1 `initial_schema` | Creates the clean-slate core, task control, flexible workflow, workspace, approval, fleet, and knowledge tables. Also contains historical DDL and removes retired fixed-Run tables before completing. |
| V2 `workflow_human_outcome` | Adds durable human complete/cancel decisions for flexible plans. |
| V3 `task_outputs` | Adds output bundles/files, publication plans, producer-consumer links, and review provenance. |
| V4 `workflow_repair_authorization` | Adds one-round repair grants, optional delegated decisions, and retained authorization history. |
| V5 `workflow_question_decision` | Adds append-only confirmed answers tied to immutable source questions and plan versions. |

All timestamp columns ending in `_epoch_ms` store Unix epoch milliseconds. JSON and serialized lists are stored as text in several columns; see the owning DTO/repository for their encoding contract.

## 2. Table and field dictionary

Types below are the SQL declaration types. `PK`, `FK`, `UQ`, and `NN` mean primary key, foreign key, unique, and not null. Fields not marked NN may be null. Check constraints and indexes are summarized after the tables.

### Worker and task execution

#### `worker` — registered Worker and capacity snapshot

| Field | Type | Meaning |
| --- | --- | --- |
| `id` | VARCHAR(128), PK | Stable Worker ID. |
| `runtime_type` | VARCHAR(64), NN | Worker runtime implementation/type. |
| `capabilities` | VARCHAR(2048), NN | Serialized capability set. |
| `max_concurrency` | INTEGER, NN | Maximum simultaneous leases. |
| `active_leases` | INTEGER, NN | Transactionally maintained current lease count. |
| `status` | VARCHAR(32), NN | `ACTIVE`, `DRAINING`, or `OFFLINE`. |
| `registered_at_epoch_ms` | BIGINT, NN | Registration time. |
| `last_heartbeat_at_epoch_ms` | BIGINT, NN | Most recent Worker heartbeat. |
| `updated_at_epoch_ms` | BIGINT, NN | Last registration/state update. |

#### `work_task` — current task authority and lease

| Field | Type | Meaning |
| --- | --- | --- |
| `id` | VARCHAR(128), PK | Task ID. |
| `task_type` | VARCHAR(64), NN | Task kind. |
| `state` | VARCHAR(32), NN | `BLOCKED`, `READY`, `CLAIMED`, `RUNNING`, `COMPLETED`, `FAILED`, or `CANCELLED`. |
| `subject_type` | VARCHAR(64), NN | Owning workflow/subject kind. |
| `subject_id` | VARCHAR(512), NN | Owning plan/run/subject identifier. |
| `required_capabilities` | VARCHAR(2048), NN | Serialized capability requirements. |
| `skill_ref` | VARCHAR(512) | Optional skill/instruction reference. |
| `priority` | INTEGER, NN | Scheduling priority. |
| `version` | BIGINT, NN | Optimistic concurrency/version value. |
| `attempt_no` | INTEGER, NN | Current/latest attempt number. |
| `current_worker_id` | VARCHAR(128), FK → `worker.id` | Current lease owner; null when not leased. |
| `lease_token` | VARCHAR(128) | Current opaque lease credential. |
| `lease_acquired_at_epoch_ms` | BIGINT | Lease acquisition time. |
| `lease_expires_at_epoch_ms` | BIGINT | Lease expiry deadline. |
| `lease_heartbeat_at_epoch_ms` | BIGINT | Last lease heartbeat. |
| `created_at_epoch_ms` | BIGINT, NN | Task creation time. |
| `updated_at_epoch_ms` | BIGINT, NN | Last task update. |
| `instruction` | TEXT, NN | Current executable instruction. |
| `repository_ref` | VARCHAR(256) | Logical repository identifier, not a machine-local path. |
| `base_revision` | VARCHAR(128) | Immutable source revision assigned to the task. |
| `instruction_revision` | INTEGER, NN | Current instruction revision number. |

Lease columns are all null or all populated. Current lease state lives on this row; there is no `task_lease` table.

#### `work_task_dependency` — task DAG edges

| Field | Type | Meaning |
| --- | --- | --- |
| `task_id` | VARCHAR(128), PK part, FK → `work_task.id` | Dependent task. |
| `depends_on_task_id` | VARCHAR(128), PK part, FK → `work_task.id` | Prerequisite task. |

#### `execution_attempt` — per-execution history and result evidence

| Field | Type | Meaning |
| --- | --- | --- |
| `id` | VARCHAR(128), PK | Attempt ID. |
| `task_id` | VARCHAR(128), NN, FK → `work_task.id` | Executed task. |
| `attempt_no` | INTEGER, NN | One-based attempt number; unique with `task_id`. |
| `worker_id` | VARCHAR(128), NN, FK → `worker.id` | Worker assigned to this attempt. |
| `lease_token` | VARCHAR(128), NN, UQ | Lease credential recorded for this attempt. |
| `state` | VARCHAR(32), NN | Attempt state: `CLAIMED`, `RUNNING`, `COMPLETED`, `FAILED`, or `EXPIRED`. |
| `external_session_id` | VARCHAR(512) | External AI runtime session, if supplied. |
| `created_at_epoch_ms` | BIGINT, NN | Claim/attempt creation time. |
| `started_at_epoch_ms` | BIGINT | Start time. |
| `finished_at_epoch_ms` | BIGINT | Terminal time. |
| `workspace_key` | VARCHAR(256) | Logical workspace key. |
| `branch_name` | VARCHAR(512) | Attempt branch name. |
| `base_revision` | VARCHAR(128) | Source Git revision. |
| `result_revision` | VARCHAR(128) | Produced Git revision. |
| `changed_files` | TEXT | Serialized changed-file list. |
| `publication_ref` | VARCHAR(2048) | Portable reference to a published result. |
| `completion_verdict` | VARCHAR(32) | Completion/review verdict. |
| `completion_summary` | TEXT | Completion/review summary. |
| `engineering_gate_status` | VARCHAR(32) | Gate status attached to this exact result. |
| `engineering_gate_review_task_id` | VARCHAR(128) | Review task evidence reference. |
| `engineering_gate_verification_task_id` | VARCHAR(128) | Verification task evidence reference. |
| `engineering_gate_closed_at_epoch_ms` | BIGINT | Gate closure time. |
| `termination_reason` | VARCHAR(64) | Why an attempt was explicitly/implicitly terminated. |
| `ai_profile_id` | VARCHAR(64) | Selected AI profile. |
| `ai_profile_version` | VARCHAR(64) | Selected profile version. |
| `ai_provider` | VARCHAR(120) | AI provider name. |
| `ai_runtime` | VARCHAR(120) | Runtime name. |
| `ai_model` | VARCHAR(120) | Model name. |
| `ai_effort` | VARCHAR(120) | Reasoning/effort setting. |
| `ai_fast` | BOOLEAN | Fast-mode setting, if selected. |
| `ai_selection_reason` | VARCHAR(512) | Profile selection explanation. |
| `failure_category` | VARCHAR(32) | Structured failure category. |
| `instruction_revision` | INTEGER, NN | Instruction revision executed by this attempt. |
| `stop_requested_at_epoch_ms` | BIGINT | Time a stop was requested. |
| `stop_acknowledged_at_epoch_ms` | BIGINT | Time Worker acknowledged the stop. |

An attempt is historical evidence: retry creates another row instead of overwriting the previous attempt.

#### `work_task_instruction_revision` — immutable instruction revisions

| Field | Type | Meaning |
| --- | --- | --- |
| `task_id` | VARCHAR(128), PK part, FK → `work_task.id` | Owning task; revisions cascade-delete with the task. |
| `revision` | INTEGER, PK part | Revision number starting at 1. |
| `instruction` | TEXT, NN | Instruction text at this revision. |
| `actor` | VARCHAR(256), NN | Actor who created the revision. |
| `reason` | VARCHAR(2000), NN | Change reason. |
| `predecessor_revision` | INTEGER | Previous revision; null for initial revision. |
| `created_at_epoch_ms` | BIGINT, NN | Revision creation time. |

#### `work_task_control_event` — operator task control audit

| Field | Type | Meaning |
| --- | --- | --- |
| `id` | VARCHAR(128), PK | Control event ID. |
| `task_id` | VARCHAR(128), NN, FK → `work_task.id` | Controlled task. |
| `attempt_id` | VARCHAR(128), FK → `execution_attempt.id` | Related active/historical attempt, if any. |
| `action` | VARCHAR(32), NN | Requested control action. |
| `actor` | VARCHAR(256), NN | Operator/system actor. |
| `reason` | VARCHAR(2000), NN | Required reason. |
| `warning_confirmed` | INTEGER, NN | Whether warning was explicitly confirmed (`0`/`1`). |
| `created_at_epoch_ms` | BIGINT, NN | Event time. |

#### `work_task_correction` — completed-task correction relation

| Field | Type | Meaning |
| --- | --- | --- |
| `completed_task_id` | VARCHAR(128), PK part, FK → `work_task.id` | Original completed task. |
| `correction_task_id` | VARCHAR(128), PK part, UQ, FK → `work_task.id` | Follow-up correction task; can belong to only one correction relation. |
| `created_by` | VARCHAR(256), NN | Requesting actor. |
| `reason` | VARCHAR(2000), NN | Correction reason. |
| `created_at_epoch_ms` | BIGINT, NN | Relation creation time. |

#### `work_task_workspace_replacement` — repository/base replacement workflow

| Field | Type | Meaning |
| --- | --- | --- |
| `id` | VARCHAR(128), PK | Replacement request ID. |
| `source_task_id` | VARCHAR(128), NN, FK → `work_task.id` | Original task. |
| `replacement_task_id` | VARCHAR(128), UQ, FK → `work_task.id` | Replacement task after successful resolution. |
| `status` | VARCHAR(32), NN | `PENDING_STOP`, `COMPLETED`, `CONFLICT`, or `REJECTED`. |
| `actor` | VARCHAR(256), NN | Requesting actor. |
| `reason` | VARCHAR(2000), NN | Replacement reason. |
| `old_repository_ref` | VARCHAR(120) | Original repository. |
| `old_base_revision` | VARCHAR(64) | Original base revision. |
| `new_repository_ref` | VARCHAR(120) | Requested repository. |
| `new_base_revision` | VARCHAR(64) | Requested base revision. |
| `requested_revision` | VARCHAR(256) | User-supplied revision request. |
| `source_version` | BIGINT, NN | Source task version captured at request. |
| `source_instruction_revision` | INTEGER, NN | Source instruction revision captured at request. |
| `lease_worker_id` | VARCHAR(128) | Worker whose lease must stop, if any. |
| `lease_token` | VARCHAR(128) | Lease token captured for safe stop acknowledgement. |
| `stop_acknowledged_at_epoch_ms` | BIGINT | Stop acknowledgement time. |
| `conflict_reason` | VARCHAR(2000) | Conflict detail. |
| `requested_at_epoch_ms` | BIGINT, NN | Request time. |
| `resolved_at_epoch_ms` | BIGINT | Resolution time. |

### Workflow planning and control

#### `flexible_workflow_plan` — durable plan specification and state

| Field | Type | Meaning |
| --- | --- | --- |
| `id` | VARCHAR(128), PK | Plan ID. |
| `state` | VARCHAR(32), NN | `DRAFT`, `CONFIRMED`, `WITHDRAWN`, or `STOPPED`. |
| `version` | BIGINT, NN | Optimistic concurrency version. |
| `spec_json` | TEXT, NN | Serialized plan goal, source, and ordered steps. |
| `created_by` | VARCHAR(256), NN | Creator. |
| `updated_by` | VARCHAR(256), NN | Last updater. |
| `reason` | VARCHAR(2000), NN | Current/latest reason. |
| `created_at_epoch_ms` | BIGINT, NN | Creation time. |
| `updated_at_epoch_ms` | BIGINT, NN | Last update time. |

#### `work_task_skill_preference` — optional preferred skills by task

| Field | Type | Meaning |
| --- | --- | --- |
| `task_id` | VARCHAR(128), PK, FK → `work_task.id` | Task with preferences. |
| `preferred_skills` | TEXT, NN | Serialized preferred skill list. |

#### `flexible_workflow_control` — plan jump/stop control and pending lease stop

| Field | Type | Meaning |
| --- | --- | --- |
| `id` | VARCHAR(128), PK | Control request ID. |
| `plan_id` | VARCHAR(128), NN, FK → `flexible_workflow_plan.id` | Controlled plan. |
| `action` | VARCHAR(16), NN | `JUMP` or `STOP`. |
| `from_step_index` | INTEGER, NN | Current step index when requested. |
| `target_step_index` | INTEGER | Jump destination; required only for `JUMP`. |
| `task_id` | VARCHAR(128), FK → `work_task.id` | Current task affected by the control. |
| `lease_worker_id` | VARCHAR(128) | Worker holding the affected lease. |
| `lease_token` | VARCHAR(128) | Lease credential used to match stop acknowledgement. |
| `status` | VARCHAR(16), NN | `PENDING_STOP`, `APPLIED`, or `CONFLICT`. |
| `actor` | VARCHAR(256), NN | Requesting actor. |
| `reason` | VARCHAR(2000), NN | Control reason. |
| `requested_at_epoch_ms` | BIGINT, NN | Request time. |
| `applied_at_epoch_ms` | BIGINT | Application time. |

#### `flexible_workflow_skipped_step` — steps skipped by a jump

| Field | Type | Meaning |
| --- | --- | --- |
| `plan_id` | VARCHAR(128), PK part, FK → `flexible_workflow_plan.id` | Plan. |
| `step_index` | INTEGER, PK part | Skipped ordered step index. |
| `control_id` | VARCHAR(128), NN, FK → `flexible_workflow_control.id` | Jump control that skipped it. |

#### `flexible_workflow_human_outcome` — human complete/cancel decision

| Field | Type | Meaning |
| --- | --- | --- |
| `plan_id` | VARCHAR(128), PK, FK → `flexible_workflow_plan.id` | Plan receiving the decision. |
| `outcome` | VARCHAR(16), NN | `COMPLETE` or `CANCEL`. |
| `status` | VARCHAR(16), NN | `PENDING` or `APPLIED`. |
| `actor` | VARCHAR(256), NN | Human decision maker. |
| `reason` | VARCHAR(2000), NN | Decision reason. |
| `decided_at_epoch_ms` | BIGINT, NN | Decision time. |

#### `web_console_plan_archive` — plan archive and delayed deletion marker

| Field | Type | Meaning |
| --- | --- | --- |
| `plan_id` | VARCHAR(128), PK, FK → `flexible_workflow_plan.id` | Archived plan. |
| `archived_by` | VARCHAR(256), NN | Archiving actor. |
| `archived_at_epoch_ms` | BIGINT, NN | Archive time. |
| `delete_requested_by` | VARCHAR(256) | Admin requesting delayed deletion. |
| `delete_requested_at_epoch_ms` | BIGINT | Deletion request time. |
| `delete_after_epoch_ms` | BIGINT | Earliest deletion time. |

The three deletion-marker fields are either all null or all populated, with `delete_after_epoch_ms` later than the request time.

#### `workflow_repair_authorization` — one-round repair authority and history

| Field | Type | Meaning |
| --- | --- | --- |
| `id` | VARCHAR(128), PK | Authorization ID, generated with the `RA-` prefix. |
| `plan_id` | VARCHAR(128), NN | Logical plan reference; deliberately no FK so history survives plan cleanup. |
| `plan_version` | BIGINT, NN | Plan version reserved when authority is granted; checked before consumption. |
| `source_review_id` | VARCHAR(128), NN | Review task that requires a repair. Logical reference, no FK. |
| `source_task_version` | BIGINT, NN | Source review task version captured for concurrency checks. |
| `scope` | TEXT, NN | Human-approved repair scope; service accepts 1–4000 characters. |
| `reason` | VARCHAR(2000), NN | Required grant reason. |
| `granted_by` | VARCHAR(256), NN | Human actor granting authority. |
| `delegate_worker_id` | VARCHAR(128) | Designated independent decision Worker; null means immediate human-directed consumption. |
| `decision_task_id` | VARCHAR(128), UQ | Delegated decision task; null for direct consumption. |
| `repair_task_id` | VARCHAR(128) | Repair execution created when the grant is consumed. |
| `round_budget` | INTEGER, NN | Exactly `1`, enforced by SQL CHECK. |
| `status` | VARCHAR(16), NN | `PENDING`, `CONSUMED`, `DENIED`, `REVOKED`, `EXPIRED`, or `STALE`. |
| `created_at_epoch_ms` | BIGINT, NN | Grant creation time. |
| `expires_at_epoch_ms` | BIGINT, NN | Deadline; current service sets creation time plus one hour. |
| `decided_at_epoch_ms` | BIGINT | Time a pending grant is finalized, including revocation or expiry recognition. |

`idx_workflow_repair_pending` permits at most one `PENDING` grant per plan. `idx_workflow_repair_history` indexes `(plan_id, created_at_epoch_ms)`. Plan, task, and Worker references have no database FKs; retained history may refer to deleted entities. This implemented one-round mechanism is narrower than the proposed editable delegation policies and reusable authorization templates.

#### `workflow_question_decision` — append-only confirmed answers

| Field | Type | Meaning |
| --- | --- | --- |
| `id` | VARCHAR(128), PK | Decision ID, generated with the `QD-` prefix. |
| `plan_id` | VARCHAR(128), NN, FK → `flexible_workflow_plan.id` | Owning plan; answers cascade-delete with the plan. |
| `question_key` | VARCHAR(64), NN | SHA-256 of source task ID, a newline, and question ID. |
| `source_task_id` | VARCHAR(256), NN | Task that produced the question; logical reference, no FK. |
| `source_file_id` | VARCHAR(128), NN | Immutable output file ID; logical reference, no FK. |
| `source_hash` | VARCHAR(64), NN | SHA-256 of the source file content. |
| `question_id` | VARCHAR(160), NN | Question ID within the source output. |
| `question_text` | TEXT, NN | Question text snapshot when answered. |
| `answer` | TEXT, NN | Confirmed answer; service accepts 1–2000 characters and stores stripped text. |
| `actor` | VARCHAR(256), NN | Authenticated Web Console actor confirming the answer. |
| `plan_version` | BIGINT, NN | Plan version after this answer, equal to submitted version plus one. |
| `decided_at_epoch_ms` | BIGINT, NN | Confirmation time. |

`UNIQUE (plan_id, plan_version)` permits one answer per plan version, not one answer per question. Re-answering inserts a new row. `idx_workflow_question_decision` indexes `(plan_id, question_key, plan_version)`. The repository reads history by descending plan version; the board uses the newest answer for a key only when file ID and hash still match. Historical reports and output files remain unchanged.

Append-only behavior is enforced by the service/repository write path, not a SQL UPDATE/DELETE prohibition. Plan retention cleanup explicitly deletes these rows; the FK also specifies `ON DELETE CASCADE`. This differs from repair authorization history, which survives cleanup.

### Approval, fleet scheduling, knowledge, and workspace

#### `approval_request` — independent human approval lifecycle

| Field | Type | Meaning |
| --- | --- | --- |
| `id` | VARCHAR(128), PK | Approval request ID. |
| `source_task_id` | VARCHAR(128), NN, UQ, FK → `work_task.id` | Source task/result; at most one approval per source task. |
| `subject_type` | VARCHAR(64), NN | Subject type. |
| `subject_id` | VARCHAR(512), NN | Subject identifier. |
| `result_revision` | VARCHAR(128), NN | Exact result revision submitted for approval. |
| `engineering_gate_closed_at_epoch_ms` | BIGINT, NN | Gate closure time captured with request. |
| `status` | VARCHAR(32), NN | `PENDING`, `APPROVED`, or `REJECTED`. |
| `requested_by` | VARCHAR(256), NN | Requesting actor. |
| `channel` | VARCHAR(32), NN | `API`, `TELEGRAM`, `TEAMS`, `WEB`, or `CLI`. |
| `channel_ref` | VARCHAR(1024) | Channel-specific reference. |
| `requested_at_epoch_ms` | BIGINT, NN | Request time. |
| `decided_by` | VARCHAR(256) | Decision maker; absent while pending. |
| `decision_note` | VARCHAR(4096) | Decision explanation. |
| `decided_at_epoch_ms` | BIGINT | Decision time; absent while pending. |

#### `fleet_admission_budget` — singleton global scheduling ceiling

| Field | Type | Meaning |
| --- | --- | --- |
| `id` | VARCHAR(32), PK | Singleton key; current row is `GLOBAL`. |
| `max_parallelism` | INTEGER, NN | Fleet concurrency ceiling; zero means unlimited. |
| `updated_at_epoch_ms` | BIGINT, NN | Last update time. |

This row serializes fleet-wide admission. Active work is counted from current `work_task` states; no second fleet active-slot counter is stored.

#### `fleet_admission_budget_event` — append-only fleet ceiling audit

| Field | Type | Meaning |
| --- | --- | --- |
| `budget_id` | VARCHAR(32), PK part, FK → `fleet_admission_budget.id` | Budget row. |
| `sequence_no` | BIGINT, PK part | Event order within the budget. |
| `event_type` | VARCHAR(64), NN | Currently `MAX_PARALLELISM_CHANGED`. |
| `actor` | VARCHAR(256), NN | Operator/system actor. |
| `previous_max_parallelism` | INTEGER, NN | Previous ceiling. |
| `max_parallelism` | INTEGER, NN | New ceiling. |
| `active_leases` | INTEGER, NN | Live leases observed when changed. |
| `reason` | VARCHAR(4096) | Change reason. |
| `occurred_at_epoch_ms` | BIGINT, NN | Event time. |

#### `knowledge_document` — durable planning/analysis documents

| Field | Type | Meaning |
| --- | --- | --- |
| `id` | VARCHAR(128), PK, FK → `work_task.id` | Owning task/document ID. |
| `run_id` | VARCHAR(128), NN | Workflow run/plan context. |
| `document_type` | VARCHAR(32), NN | `REQUIREMENT_ANALYSIS`, `SOLUTION_ARCHITECTURE`, or `IMPLEMENTATION_PLANNING`. |
| `repository_ref` | VARCHAR(120) | Logical repository. |
| `base_revision` | VARCHAR(128) | Source revision. |
| `result_revision` | VARCHAR(128) | Result revision, when available. |
| `attempt_no` | INTEGER, NN | Producing attempt number. |
| `content_hash` | VARCHAR(64), NN | Document content hash. |
| `markdown` | TEXT, NN | Document content. |
| `created_at_epoch_ms` | BIGINT, NN | Creation time. |

#### `workspace_registry` — configured workspace roots

| Field | Type | Meaning |
| --- | --- | --- |
| `id` | VARCHAR(80), PK | Workspace ID. |
| `display_name` | VARCHAR(160), NN | Operator-facing name. |
| `root_path` | VARCHAR(2048), NN | Local workspace root path. |
| `target_mode` | VARCHAR(16), NN | `ROOT` or `CHILD`. |
| `enabled` | INTEGER, NN | Enabled flag (`0`/`1`). |
| `created_at_epoch_ms` | BIGINT, NN | Creation time. |

#### `workspace_repo_registry` — logical repository to local workspace mapping

| Field | Type | Meaning |
| --- | --- | --- |
| `repository_ref` | VARCHAR(120), PK | Logical repository name sent in tasks. |
| `workspace_id` | VARCHAR(80), NN, FK → `workspace_registry.id` | Owning workspace. |
| `repo_id` | VARCHAR(80), NN | Repository key within workspace. |
| `relative_path` | VARCHAR(1024), NN | Path relative to workspace root. |
| `git_warning` | VARCHAR(2048) | Configuration/inspection warning. |
| `enabled` | INTEGER, NN | Enabled flag (`0`/`1`). |

`(workspace_id, repo_id)` is unique.

#### `direct_workspace_lock` — mutex for direct-directory claims

| Field | Type | Meaning |
| --- | --- | --- |
| `repository_ref` | VARCHAR(120), PK | Logical repository being serialized. |

### Task outputs

#### `task_output_bundle` — registered attempt output manifest

| Field | Type | Meaning |
| --- | --- | --- |
| `attempt_id` | VARCHAR(128), PK | Producing attempt. |
| `task_id` | VARCHAR(128), NN | Task ID. |
| `repository_ref` | VARCHAR(256), NN | Repository reference. |
| `base_revision` | VARCHAR(64) | Base Git revision. |
| `result_revision` | VARCHAR(64) | Result Git revision. |
| `directory` | TEXT, NN | Output directory/reference. |
| `created_at_epoch_ms` | BIGINT, NN | Registration time. |
| `manifest_json` | TEXT, NN | Serialized bundle manifest. |
| `execution_state` | VARCHAR(32), NN | Bundle execution/registration state; defaults to `REGISTERED`. |

#### `task_output_file` — output file metadata and stored bytes

| Field | Type | Meaning |
| --- | --- | --- |
| `id` | VARCHAR(128), PK | File ID. |
| `attempt_id` | VARCHAR(128), NN, FK → `task_output_bundle.attempt_id` | Owning bundle/attempt. |
| `relative_path` | VARCHAR(512), NN | Relative output path. |
| `kind` | VARCHAR(32), NN | File kind. |
| `content_hash` | VARCHAR(64), NN | Content digest. |
| `byte_count` | BIGINT, NN | Original byte length. |
| `content_base64` | TEXT, NN | Base64-encoded bytes persisted in the database. |

`(attempt_id, relative_path)` is unique.

#### `task_output_publication` — controlled publication plan

| Field | Type | Meaning |
| --- | --- | --- |
| `plan_id` | VARCHAR(128), PK | Workflow plan ID. |
| `file_id` | VARCHAR(128), NN, FK → `task_output_file.id` | File proposed for publication. |
| `review_task_id` | VARCHAR(128), NN | Review task reference. |
| `target_path` | VARCHAR(512), NN | Intended destination path. |
| `actor` | VARCHAR(256), NN | Creating operator. |
| `reason` | VARCHAR(2000), NN | Publication reason. |
| `created_at_epoch_ms` | BIGINT, NN | Plan creation time. |

#### `task_output_input` — consumed output provenance

| Field | Type | Meaning |
| --- | --- | --- |
| `consumer_attempt_id` | VARCHAR(128), PK part | Attempt that consumed an output. |
| `source_attempt_id` | VARCHAR(128), PK part | Attempt that produced the source output. |

#### `task_output_review` — review provenance for an output

| Field | Type | Meaning |
| --- | --- | --- |
| `source_attempt_id` | VARCHAR(128), PK part | Attempt whose output was reviewed. |
| `review_task_id` | VARCHAR(128), PK part | Review task. |
| `review_attempt_id` | VARCHAR(128), NN | Review attempt. |
| `review_worker_id` | VARCHAR(128), NN | Reviewing Worker. |
| `source_worker_id` | VARCHAR(128), NN | Worker that produced source output. |
| `reviewed_at_epoch_ms` | BIGINT, NN | Review time. |

## 3. Relationships, constraints, and indexes

- A Worker can own many tasks over time; `work_task.current_worker_id` is the current lease owner. `execution_attempt` retains each historical assignment.
- `work_task_dependency` stores prerequisite edges. A task becomes eligible only after its prerequisites satisfy workflow rules.
- `approval_request.source_task_id` is unique. Approval captures the exact `result_revision` and gate-close time so later results do not change the decision target.
- A `flexible_workflow_plan` stores the plan specification; generated execution authority is in `work_task` and `work_task_dependency`. Skill preferences, control requests, skipped steps, human outcome, and archive markers are separate records because they have distinct update/audit needs.
- Workspace repository refs map logical task values to local directories through the two workspace registry tables. Paths are not sent as logical repository IDs.
- Output bundles/files retain delivery content and provenance; task result revisions still refer to Git and remain separate from file publication.
- SQL checks constrain status enums, nonnegative counts, lease-field completeness, outcome consistency, step/control combinations, and valid version relationships. See migration SQL for the exact predicates.
- Indexes support ready-task ordering, subject lookup, dependency traversal, lease expiry, Worker leases, attempt history, approval lookup, fleet events, knowledge lookup, workspace lookup, task audit history, replacement/control pending uniqueness, and archive expiry. Their exact definitions are in the migration files.

## 4. How data moves through the database

### Confirming a source question

1. The question board selects the latest completed execution/review tasks per step and reads their registered `task_output_bundle` / `task_output_file` outputs. Questions come from execution `.json` files at `openQuestions` or `payload.openQuestions`; review files are documents, not question sources.
2. An operator submits the current plan version, question key, source file ID/hash, answer, and explicit confirmation. The actor comes from the authenticated session. The service locks plan tasks and checks current sources and state. Decisions are blocked for non-confirmed/archived/closed plans, pending controls, unfinished tasks, or a current unexpired pending repair grant.
3. In one transaction, an optimistic plan transition keeps the plan `CONFIRMED`, increments its version, and appends a `workflow_question_decision` row at that new version. Stale submissions conflict; prior answers are not overwritten.
4. Later execution/review tasks, delegated repair decisions, and failed-task retry instruction revisions include a textual snapshot of applicable answers. Existing output reports stay immutable. Confirmed answers do not grant execution, testing, network, or deployment permissions. The service rejects a decision context over 8000 characters instead of silently dropping answers.

### Consuming repair authority

Human confirmation reserves plan/review versions and inserts a `PENDING` grant. Without a delegate, the service immediately tries to consume it and create one repair execution. With a delegate, it creates a decision `work_task` with subject `workflow-repair-authorization`; only the designated independent Worker may decide. A matching Worker's `PASS` permits consumption after deadline and plan/version checks; a non-PASS or mismatched Worker finalizes it as `DENIED`. Finalization updates only a still-`PENDING` row, recording `repair_task_id` when consumed and `decided_at_epoch_ms`.

Human revocation sets `REVOKED`; deadline or state/version changes can produce `EXPIRED` or `STALE` when evaluated. Do not infer an automatic expiry timer from the stored deadline: pending rows can remain pending until evaluated. Historical grants remain after plan/task cleanup, while question answers are removed with their plan.

### Other persistence flows

```text
Operator creates plan
  -> flexible_workflow_plan (DRAFT, spec_json)
  -> confirmation/version update (CONFIRMED)
  -> work_task + work_task_dependency + optional work_task_skill_preference
  -> eligible task state becomes READY

Worker registers/heartbeats
  -> worker
  -> claim transaction checks admission/capability/capacity
  -> work_task READY -> CLAIMED + lease fields
  -> execution_attempt inserted (one row per attempt)
  -> start/heartbeat update task + attempt; stop request/ack times are recorded
  -> complete/fail/expiry terminalizes task + attempt and releases Worker capacity
  -> completion handlers persist evidence and/or create downstream tasks/dependencies

Implementation/review results
  -> execution_attempt stores Git revisions, changed files, completion evidence and gate references
  -> optional knowledge_document stores analysis/planning Markdown
  -> approval_request stores a separate human decision against the exact result
  -> task_output_* stores deliverable bundle/files and producer/consumer/review provenance

Plan/operator controls
  -> flexible_workflow_control / flexible_workflow_human_outcome
  -> work_task_control_event and task instruction revisions retain task-level history
  -> workspace replacement creates a request, waits for safe stop, then creates/references replacement task
```

The transaction boundaries and services own actual write order; the diagram shows durable responsibility, not a promise that every arrow is one database transaction. Workers never write the Control Plane database directly. Git objects/worktrees and workspace files are external to the relational schema, although registered output file bytes may also be stored as Base64 in `task_output_file`.

## 5. Scheduling and authority

Task/lease state and dependencies are authoritative in `work_task` and `work_task_dependency`; attempt history is in `execution_attempt`; Worker capacity is reserved on `worker`. Admission order for autonomous/flexible work is run/plan policy, fleet budget, Worker capacity, then atomic task ownership. Fleet admission serializes on the `GLOBAL` row and counts live `CLAIMED`/`RUNNING` tasks; it does not maintain a duplicate live counter.

The older fixed autonomous-run tables appear in the historical DDL portion of V1 but are dropped before the migration completes. Current plan lifecycle and task execution must not be documented as `autonomous_run` persistence.

## 6. Database portability and operations

V4 and V5 add tables/indexes without rewriting existing reports. For a database already aligned with this repository's V1–V3 history, Flyway can apply the new versions in sequence; this does not resolve compatibility with an older pre-baseline history. Question decisions are removed on plan deletion, while repair grants remain. This documentation update did not execute migrations or validate a live SQLite/PostgreSQL database.

The schema is designed for SQLite and PostgreSQL. Timestamp storage uses epoch milliseconds. Database-specific lock/admission SQL is isolated behind the database dialect.

The local SQLite profile uses a one-connection Hikari pool, `IMMEDIATE` transaction mode, foreign keys, and a 5-second busy timeout. Repository code must respect the single-connection rule. PostgreSQL supports shared database operation, but multi-Control-Plane production readiness requires the concurrency/chaos validation documented in [Testing, Validation, and Limitations](12-TESTING-VALIDATION-AND-LIMITATIONS.md).

All schema changes require a new migration; do not edit a migration already applied to an environment. Back up data before applying schema changes. The current V1 is a clean-slate baseline, so existing databases require an explicit compatibility/migration decision rather than being assumed compatible.
