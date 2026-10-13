-- Development baseline for a fresh database. Historical V1-V21 DDL is kept in order.

-- V1__r3_core.sql
-- R3 clean-slate schema.
-- Deliberately does not migrate or preserve any R1/R2 table.
-- Epoch milliseconds are used so one migration works for SQLite and PostgreSQL.
-- R3-S2 keeps lease CURRENT STATE on work_task; no task_lease table is needed.

CREATE TABLE worker (
    id VARCHAR(128) PRIMARY KEY,
    runtime_type VARCHAR(64) NOT NULL,
    capabilities VARCHAR(2048) NOT NULL DEFAULT '',
    max_concurrency INTEGER NOT NULL,
    active_leases INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL,
    registered_at_epoch_ms BIGINT NOT NULL,
    last_heartbeat_at_epoch_ms BIGINT NOT NULL,
    updated_at_epoch_ms BIGINT NOT NULL,
    CHECK (max_concurrency >= 1),
    CHECK (active_leases >= 0 AND active_leases <= max_concurrency),
    CHECK (status IN ('ACTIVE','DRAINING','OFFLINE'))
);

CREATE TABLE work_task (
    id VARCHAR(128) PRIMARY KEY,
    task_type VARCHAR(64) NOT NULL,
    state VARCHAR(32) NOT NULL,
    subject_type VARCHAR(64) NOT NULL,
    subject_id VARCHAR(512) NOT NULL,
    required_capabilities VARCHAR(2048) NOT NULL DEFAULT '',
    skill_ref VARCHAR(512),
    priority INTEGER NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    attempt_no INTEGER NOT NULL DEFAULT 0,
    current_worker_id VARCHAR(128),
    lease_token VARCHAR(128),
    lease_acquired_at_epoch_ms BIGINT,
    lease_expires_at_epoch_ms BIGINT,
    lease_heartbeat_at_epoch_ms BIGINT,
    created_at_epoch_ms BIGINT NOT NULL,
    updated_at_epoch_ms BIGINT NOT NULL,
    FOREIGN KEY (current_worker_id) REFERENCES worker(id),
    CHECK (attempt_no >= 0),
    CHECK (state IN ('BLOCKED','READY','CLAIMED','RUNNING','COMPLETED','FAILED','CANCELLED')),
    CHECK (
        (lease_token IS NULL AND current_worker_id IS NULL
            AND lease_acquired_at_epoch_ms IS NULL AND lease_expires_at_epoch_ms IS NULL
            AND lease_heartbeat_at_epoch_ms IS NULL)
        OR
        (lease_token IS NOT NULL AND current_worker_id IS NOT NULL
            AND lease_acquired_at_epoch_ms IS NOT NULL AND lease_expires_at_epoch_ms IS NOT NULL
            AND lease_heartbeat_at_epoch_ms IS NOT NULL)
    )
);

CREATE TABLE work_task_dependency (
    task_id VARCHAR(128) NOT NULL,
    depends_on_task_id VARCHAR(128) NOT NULL,
    PRIMARY KEY (task_id, depends_on_task_id),
    FOREIGN KEY (task_id) REFERENCES work_task(id) ON DELETE CASCADE,
    FOREIGN KEY (depends_on_task_id) REFERENCES work_task(id),
    CHECK (task_id <> depends_on_task_id)
);

CREATE TABLE execution_attempt (
    id VARCHAR(128) PRIMARY KEY,
    task_id VARCHAR(128) NOT NULL,
    attempt_no INTEGER NOT NULL,
    worker_id VARCHAR(128) NOT NULL,
    lease_token VARCHAR(128) NOT NULL UNIQUE,
    state VARCHAR(32) NOT NULL,
    external_session_id VARCHAR(512),
    created_at_epoch_ms BIGINT NOT NULL,
    started_at_epoch_ms BIGINT,
    finished_at_epoch_ms BIGINT,
    FOREIGN KEY (task_id) REFERENCES work_task(id),
    FOREIGN KEY (worker_id) REFERENCES worker(id),
    UNIQUE (task_id, attempt_no),
    CHECK (attempt_no >= 1),
    CHECK (state IN ('CLAIMED','RUNNING','COMPLETED','FAILED','EXPIRED'))
);

CREATE INDEX idx_work_task_ready_order
    ON work_task(state, priority DESC, created_at_epoch_ms, id);
CREATE INDEX idx_work_task_subject
    ON work_task(subject_type, subject_id, created_at_epoch_ms);
CREATE INDEX idx_work_task_dependency_predecessor
    ON work_task_dependency(depends_on_task_id, task_id);
CREATE INDEX idx_work_task_lease_expiry
    ON work_task(state, lease_expires_at_epoch_ms, id);
CREATE INDEX idx_work_task_worker
    ON work_task(current_worker_id, state);
CREATE INDEX idx_execution_attempt_task
    ON execution_attempt(task_id, attempt_no);

-- V2__r4_task_instruction.sql
-- R4 adds executable task instructions without introducing a new aggregate/table.
ALTER TABLE work_task ADD COLUMN instruction TEXT NOT NULL DEFAULT '';

-- V3__r5_isolated_workspace.sql
-- R5 keeps workspace authority in existing Task/ExecutionAttempt aggregates.
-- No new table is introduced. repository_ref is a logical name, never a machine-local path.
ALTER TABLE work_task ADD COLUMN repository_ref VARCHAR(256);
ALTER TABLE work_task ADD COLUMN base_revision VARCHAR(128);

ALTER TABLE execution_attempt ADD COLUMN workspace_key VARCHAR(256);
ALTER TABLE execution_attempt ADD COLUMN branch_name VARCHAR(512);
ALTER TABLE execution_attempt ADD COLUMN base_revision VARCHAR(128);
ALTER TABLE execution_attempt ADD COLUMN result_revision VARCHAR(128);
ALTER TABLE execution_attempt ADD COLUMN changed_files TEXT;

CREATE INDEX idx_work_task_repository_ready
    ON work_task(repository_ref, state, priority DESC, created_at_epoch_ms);

-- V4__r6_result_publication.sql
-- R6-S1: result publication metadata stays on execution_attempt.
-- No new aggregate/table is introduced.
ALTER TABLE execution_attempt ADD COLUMN publication_ref VARCHAR(2048);

-- V5__r6_completion_report.sql
-- R6-S3: semantic review/verification result remains part of the execution attempt.
-- No new table is introduced.
ALTER TABLE execution_attempt ADD COLUMN completion_verdict VARCHAR(32);
ALTER TABLE execution_attempt ADD COLUMN completion_summary TEXT;

-- V6__r6_engineering_gate.sql
-- R6-S4: durable final engineering gate attached to the exact completed code-change attempt.
-- No new table is introduced. A later REPAIR has its own attempt and therefore its own gate.
ALTER TABLE execution_attempt ADD COLUMN engineering_gate_status VARCHAR(32);
ALTER TABLE execution_attempt ADD COLUMN engineering_gate_review_task_id VARCHAR(128);
ALTER TABLE execution_attempt ADD COLUMN engineering_gate_verification_task_id VARCHAR(128);
ALTER TABLE execution_attempt ADD COLUMN engineering_gate_closed_at_epoch_ms BIGINT;

-- V7__r7_human_approval.sql
-- R7-S1: human approval is an independent authority/lifecycle and therefore merits its own table.
-- One immutable code-result engineering gate can have at most one approval request.
CREATE TABLE approval_request (
    id VARCHAR(128) PRIMARY KEY,
    source_task_id VARCHAR(128) NOT NULL UNIQUE,
    subject_type VARCHAR(64) NOT NULL,
    subject_id VARCHAR(512) NOT NULL,
    result_revision VARCHAR(128) NOT NULL,
    engineering_gate_closed_at_epoch_ms BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    requested_by VARCHAR(256) NOT NULL,
    channel VARCHAR(32) NOT NULL,
    channel_ref VARCHAR(1024),
    requested_at_epoch_ms BIGINT NOT NULL,
    decided_by VARCHAR(256),
    decision_note VARCHAR(4096),
    decided_at_epoch_ms BIGINT,
    FOREIGN KEY (source_task_id) REFERENCES work_task(id),
    CHECK (status IN ('PENDING','APPROVED','REJECTED')),
    CHECK (channel IN ('API','TELEGRAM','TEAMS','WEB','CLI')),
    CHECK (
        (status='PENDING' AND decided_by IS NULL AND decided_at_epoch_ms IS NULL)
        OR
        (status IN ('APPROVED','REJECTED') AND decided_by IS NOT NULL AND decided_at_epoch_ms IS NOT NULL)
    )
);

CREATE INDEX idx_approval_request_subject
    ON approval_request(subject_type, subject_id, requested_at_epoch_ms);
CREATE INDEX idx_approval_request_status
    ON approval_request(status, requested_at_epoch_ms);

-- V8__r8_autonomous_run_authority.sql
-- R8-S4: an autonomous requirement run now has its own independent lifecycle/authority.
-- This is intentionally a sixth business table: the run has durable state, recovery ownership,
-- human-approval linkage, and a lifecycle distinct from any individual WorkTask.
CREATE TABLE autonomous_run (
    id VARCHAR(128) PRIMARY KEY,
    requirement_task_id VARCHAR(128) NOT NULL UNIQUE,
    status VARCHAR(32) NOT NULL,
    terminal_source_task_id VARCHAR(128),
    result_revision VARCHAR(128),
    approval_request_id VARCHAR(128),
    last_failed_task_id VARCHAR(128),
    last_failure_reason VARCHAR(4096),
    failure_count INTEGER NOT NULL DEFAULT 0,
    recovery_count INTEGER NOT NULL DEFAULT 0,
    last_recovered_by VARCHAR(256),
    last_recovered_at_epoch_ms BIGINT,
    started_at_epoch_ms BIGINT NOT NULL,
    status_updated_at_epoch_ms BIGINT NOT NULL,
    completed_at_epoch_ms BIGINT,
    FOREIGN KEY (requirement_task_id) REFERENCES work_task(id),
    FOREIGN KEY (terminal_source_task_id) REFERENCES work_task(id),
    FOREIGN KEY (approval_request_id) REFERENCES approval_request(id),
    FOREIGN KEY (last_failed_task_id) REFERENCES work_task(id),
    CHECK (status IN ('RUNNING','ENGINEERING_PASSED','WAITING_APPROVAL','SUCCEEDED','FAILED','REJECTED')),
    CHECK (failure_count >= 0),
    CHECK (recovery_count >= 0),
    CHECK (
        (status IN ('SUCCEEDED','REJECTED') AND completed_at_epoch_ms IS NOT NULL)
        OR
        (status NOT IN ('SUCCEEDED','REJECTED') AND completed_at_epoch_ms IS NULL)
    )
);

CREATE INDEX idx_autonomous_run_status
    ON autonomous_run(status, status_updated_at_epoch_ms);
CREATE INDEX idx_autonomous_run_approval
    ON autonomous_run(approval_request_id);

-- V9__r8_operator_control.sql
-- R8-S5: operator control is orthogonal to autonomous lifecycle status.
-- ACTIVE/PAUSED/CANCELLED remains on the existing autonomous_run authority row;
-- no separate operator-control table is required for current-state authority.
ALTER TABLE autonomous_run ADD COLUMN control_state VARCHAR(32) NOT NULL DEFAULT 'ACTIVE';
ALTER TABLE autonomous_run ADD COLUMN pause_count INTEGER NOT NULL DEFAULT 0;
ALTER TABLE autonomous_run ADD COLUMN resume_count INTEGER NOT NULL DEFAULT 0;
ALTER TABLE autonomous_run ADD COLUMN last_control_action VARCHAR(32);
ALTER TABLE autonomous_run ADD COLUMN last_controlled_by VARCHAR(256);
ALTER TABLE autonomous_run ADD COLUMN last_control_reason VARCHAR(4096);
ALTER TABLE autonomous_run ADD COLUMN last_controlled_at_epoch_ms BIGINT;
ALTER TABLE autonomous_run ADD COLUMN cancelled_at_epoch_ms BIGINT;

-- Active attempts revoked by pause/cancel use the existing terminal EXPIRED state.
-- termination_reason distinguishes natural lease expiry from explicit operator revocation.
ALTER TABLE execution_attempt ADD COLUMN termination_reason VARCHAR(64);

CREATE INDEX idx_autonomous_run_control
    ON autonomous_run(control_state, status, status_updated_at_epoch_ms);

-- V10__r8_run_timeline_events.sql
-- R8-S6: retain only run-level history that cannot be reconstructed from the existing
-- authoritative snapshots. Task/attempt/gate/approval timeline entries remain projections
-- of work_task, execution_attempt, approval_request, and autonomous_run.
-- event_sequence gives every run a strict audit order even when multiple actions share a millisecond.
ALTER TABLE autonomous_run ADD COLUMN event_sequence BIGINT NOT NULL DEFAULT 0;

CREATE TABLE autonomous_run_event (
    run_id VARCHAR(128) NOT NULL,
    sequence_no BIGINT NOT NULL,
    event_type VARCHAR(32) NOT NULL,
    actor VARCHAR(256),
    related_task_id VARCHAR(128),
    detail VARCHAR(4096),
    occurred_at_epoch_ms BIGINT NOT NULL,
    PRIMARY KEY (run_id, sequence_no),
    FOREIGN KEY (run_id) REFERENCES autonomous_run(id) ON DELETE CASCADE,
    FOREIGN KEY (related_task_id) REFERENCES work_task(id),
    CHECK (sequence_no >= 1),
    CHECK (event_type IN ('FAILED','RECOVERED','PAUSED','RESUMED','CANCELLED'))
);

CREATE INDEX idx_autonomous_run_event_run_time
    ON autonomous_run_event(run_id, occurred_at_epoch_ms, sequence_no);

-- V11__r8_run_concurrency_budget.sql
-- R8-S10: per-autonomous-run concurrency budget and atomic admission accounting.
-- max_parallelism=0 means unlimited. active_slots is a capacity ledger analogous to worker.active_leases.
ALTER TABLE autonomous_run ADD COLUMN max_parallelism INTEGER NOT NULL DEFAULT 0 CHECK (max_parallelism >= 0);
ALTER TABLE autonomous_run ADD COLUMN active_slots INTEGER NOT NULL DEFAULT 0 CHECK (active_slots >= 0);

-- Preserve lease/accounting authority when upgrading a database that was stopped with claimed/running work.
UPDATE autonomous_run
   SET active_slots=(
       SELECT COUNT(*)
         FROM work_task wt
        WHERE wt.subject_type='AUTONOMOUS_REQUIREMENT'
          AND wt.subject_id=autonomous_run.id
          AND wt.state IN ('CLAIMED','RUNNING')
   );

CREATE INDEX idx_autonomous_run_admission
    ON autonomous_run(control_state, status, max_parallelism, active_slots);

-- V12__r8_runtime_scheduling_control.sql
-- R8-S11: runtime maxParallelism changes are durable scheduling authority and require audit retention.
-- Rebuild the run-event table portably so the event_type CHECK admits the new event without
-- introducing a new business table. Existing run-event history and sequence numbers are preserved.
CREATE TABLE autonomous_run_event_v12 (
    run_id VARCHAR(128) NOT NULL,
    sequence_no BIGINT NOT NULL,
    event_type VARCHAR(32) NOT NULL,
    actor VARCHAR(256),
    related_task_id VARCHAR(128),
    detail VARCHAR(4096),
    occurred_at_epoch_ms BIGINT NOT NULL,
    PRIMARY KEY (run_id, sequence_no),
    FOREIGN KEY (run_id) REFERENCES autonomous_run(id) ON DELETE CASCADE,
    FOREIGN KEY (related_task_id) REFERENCES work_task(id),
    CHECK (sequence_no >= 1),
    CHECK (event_type IN ('FAILED','RECOVERED','PAUSED','RESUMED','CANCELLED','MAX_PARALLELISM_CHANGED'))
);

INSERT INTO autonomous_run_event_v12(
    run_id,sequence_no,event_type,actor,related_task_id,detail,occurred_at_epoch_ms
)
SELECT run_id,sequence_no,event_type,actor,related_task_id,detail,occurred_at_epoch_ms
  FROM autonomous_run_event;

DROP TABLE autonomous_run_event;
ALTER TABLE autonomous_run_event_v12 RENAME TO autonomous_run_event;

CREATE INDEX idx_autonomous_run_event_run_time
    ON autonomous_run_event(run_id, occurred_at_epoch_ms, sequence_no);

-- V13__r8_fleet_admission_budget.sql
CREATE TABLE fleet_admission_budget (
    id VARCHAR(32) PRIMARY KEY,
    max_parallelism INTEGER NOT NULL DEFAULT 0 CHECK (max_parallelism >= 0 AND max_parallelism <= 65535),
    updated_at_epoch_ms BIGINT NOT NULL
);

INSERT INTO fleet_admission_budget(id, max_parallelism, updated_at_epoch_ms)
VALUES ('GLOBAL', 0, 0);

-- V14__r8_runtime_fleet_scheduling_control.sql
CREATE TABLE fleet_admission_budget_event (
    budget_id VARCHAR(32) NOT NULL,
    sequence_no BIGINT NOT NULL,
    event_type VARCHAR(64) NOT NULL CHECK (event_type IN ('MAX_PARALLELISM_CHANGED')),
    actor VARCHAR(256) NOT NULL,
    previous_max_parallelism INTEGER NOT NULL CHECK (previous_max_parallelism >= 0 AND previous_max_parallelism <= 65535),
    max_parallelism INTEGER NOT NULL CHECK (max_parallelism >= 0 AND max_parallelism <= 65535),
    active_leases INTEGER NOT NULL CHECK (active_leases >= 0),
    reason VARCHAR(4096),
    occurred_at_epoch_ms BIGINT NOT NULL,
    PRIMARY KEY (budget_id, sequence_no),
    FOREIGN KEY (budget_id) REFERENCES fleet_admission_budget(id)
);

CREATE INDEX idx_fleet_admission_budget_event_time
    ON fleet_admission_budget_event(budget_id, occurred_at_epoch_ms);

-- V15__knowledge_documents.sql
CREATE TABLE knowledge_document (
    id VARCHAR(128) PRIMARY KEY,
    run_id VARCHAR(128) NOT NULL,
    document_type VARCHAR(32) NOT NULL,
    repository_ref VARCHAR(120),
    base_revision VARCHAR(128),
    result_revision VARCHAR(128),
    attempt_no INTEGER NOT NULL,
    content_hash VARCHAR(64) NOT NULL,
    markdown TEXT NOT NULL,
    created_at_epoch_ms BIGINT NOT NULL,
    FOREIGN KEY (id) REFERENCES work_task(id),
    CHECK (document_type IN ('REQUIREMENT_ANALYSIS','SOLUTION_ARCHITECTURE','IMPLEMENTATION_PLANNING')),
    CHECK (attempt_no >= 1)
);

CREATE INDEX idx_knowledge_document_repository ON knowledge_document(repository_ref, created_at_epoch_ms);
CREATE INDEX idx_knowledge_document_run ON knowledge_document(run_id, document_type);

-- V16__ai_attempt_profiles.sql
ALTER TABLE execution_attempt ADD COLUMN ai_profile_id VARCHAR(64);
ALTER TABLE execution_attempt ADD COLUMN ai_profile_version VARCHAR(64);
ALTER TABLE execution_attempt ADD COLUMN ai_provider VARCHAR(120);
ALTER TABLE execution_attempt ADD COLUMN ai_runtime VARCHAR(120);
ALTER TABLE execution_attempt ADD COLUMN ai_model VARCHAR(120);
ALTER TABLE execution_attempt ADD COLUMN ai_effort VARCHAR(120);
ALTER TABLE execution_attempt ADD COLUMN ai_fast BOOLEAN;
ALTER TABLE execution_attempt ADD COLUMN ai_selection_reason VARCHAR(512);
ALTER TABLE execution_attempt ADD COLUMN failure_category VARCHAR(32);

-- V17__workspace_registry.sql
CREATE TABLE workspace_registry (
    id VARCHAR(80) PRIMARY KEY,
    display_name VARCHAR(160) NOT NULL,
    root_path VARCHAR(2048) NOT NULL,
    target_mode VARCHAR(16) NOT NULL,
    enabled INTEGER NOT NULL DEFAULT 1,
    created_at_epoch_ms BIGINT NOT NULL,
    CHECK (target_mode IN ('ROOT','CHILD')),
    CHECK (enabled IN (0,1))
);

CREATE TABLE workspace_repo_registry (
    repository_ref VARCHAR(120) PRIMARY KEY,
    workspace_id VARCHAR(80) NOT NULL,
    repo_id VARCHAR(80) NOT NULL,
    relative_path VARCHAR(1024) NOT NULL,
    git_warning VARCHAR(2048),
    enabled INTEGER NOT NULL DEFAULT 1,
    FOREIGN KEY (workspace_id) REFERENCES workspace_registry(id),
    UNIQUE (workspace_id, repo_id),
    CHECK (enabled IN (0,1))
);

CREATE INDEX idx_workspace_repo_workspace ON workspace_repo_registry(workspace_id);

-- V18__direct_workspace_lock.sql
-- Stable row mutex for direct-directory claims, including implicit child directories.
CREATE TABLE direct_workspace_lock (
    repository_ref VARCHAR(120) PRIMARY KEY
);

-- V19__task_instruction_revision_control.sql
ALTER TABLE work_task ADD COLUMN instruction_revision INTEGER NOT NULL DEFAULT 1;

CREATE TABLE work_task_instruction_revision (
    task_id VARCHAR(128) NOT NULL,
    revision INTEGER NOT NULL,
    instruction TEXT NOT NULL,
    actor VARCHAR(256) NOT NULL,
    reason VARCHAR(2000) NOT NULL,
    predecessor_revision INTEGER,
    created_at_epoch_ms BIGINT NOT NULL,
    PRIMARY KEY (task_id, revision),
    FOREIGN KEY (task_id) REFERENCES work_task(id) ON DELETE CASCADE,
    CHECK (revision >= 1),
    CHECK (predecessor_revision IS NULL OR predecessor_revision = revision - 1)
);

INSERT INTO work_task_instruction_revision(
    task_id, revision, instruction, actor, reason, predecessor_revision, created_at_epoch_ms
)
SELECT id, 1, instruction, 'system:migration', 'Initial task instruction (migrated)', NULL, created_at_epoch_ms
  FROM work_task;

ALTER TABLE execution_attempt ADD COLUMN instruction_revision INTEGER NOT NULL DEFAULT 1;
ALTER TABLE execution_attempt ADD COLUMN stop_requested_at_epoch_ms BIGINT;
ALTER TABLE execution_attempt ADD COLUMN stop_acknowledged_at_epoch_ms BIGINT;

CREATE TABLE work_task_control_event (
    id VARCHAR(128) PRIMARY KEY,
    task_id VARCHAR(128) NOT NULL,
    attempt_id VARCHAR(128),
    action VARCHAR(32) NOT NULL,
    actor VARCHAR(256) NOT NULL,
    reason VARCHAR(2000) NOT NULL,
    warning_confirmed INTEGER NOT NULL DEFAULT 0,
    created_at_epoch_ms BIGINT NOT NULL,
    FOREIGN KEY (task_id) REFERENCES work_task(id),
    FOREIGN KEY (attempt_id) REFERENCES execution_attempt(id),
    CHECK (warning_confirmed IN (0,1))
);

CREATE TABLE work_task_correction (
    completed_task_id VARCHAR(128) NOT NULL,
    correction_task_id VARCHAR(128) NOT NULL,
    created_by VARCHAR(256) NOT NULL,
    reason VARCHAR(2000) NOT NULL,
    created_at_epoch_ms BIGINT NOT NULL,
    PRIMARY KEY (completed_task_id, correction_task_id),
    UNIQUE (correction_task_id),
    FOREIGN KEY (completed_task_id) REFERENCES work_task(id),
    FOREIGN KEY (correction_task_id) REFERENCES work_task(id)
);

CREATE INDEX idx_task_instruction_revision_history
    ON work_task_instruction_revision(task_id, revision);
CREATE INDEX idx_task_control_event_history
    ON work_task_control_event(task_id, created_at_epoch_ms, id);
CREATE INDEX idx_task_correction_parent
    ON work_task_correction(completed_task_id, created_at_epoch_ms);

-- V20__task_workspace_replacement.sql
-- Replacement preserves the original task and every attempt. One request owns one new task.
CREATE TABLE work_task_workspace_replacement (
    id VARCHAR(128) PRIMARY KEY,
    source_task_id VARCHAR(128) NOT NULL,
    replacement_task_id VARCHAR(128) UNIQUE,
    status VARCHAR(32) NOT NULL,
    actor VARCHAR(256) NOT NULL,
    reason VARCHAR(2000) NOT NULL,
    old_repository_ref VARCHAR(120),
    old_base_revision VARCHAR(64),
    new_repository_ref VARCHAR(120),
    new_base_revision VARCHAR(64),
    requested_revision VARCHAR(256),
    source_version BIGINT NOT NULL,
    source_instruction_revision INTEGER NOT NULL,
    lease_worker_id VARCHAR(128),
    lease_token VARCHAR(128),
    stop_acknowledged_at_epoch_ms BIGINT,
    conflict_reason VARCHAR(2000),
    requested_at_epoch_ms BIGINT NOT NULL,
    resolved_at_epoch_ms BIGINT,
    FOREIGN KEY (source_task_id) REFERENCES work_task(id),
    FOREIGN KEY (replacement_task_id) REFERENCES work_task(id),
    CHECK (status IN ('PENDING_STOP','COMPLETED','CONFLICT','REJECTED'))
);

CREATE INDEX idx_workspace_replacement_source
    ON work_task_workspace_replacement(source_task_id, requested_at_epoch_ms, id);

CREATE UNIQUE INDEX idx_workspace_replacement_pending
    ON work_task_workspace_replacement(source_task_id) WHERE status='PENDING_STOP';

-- V21__flexible_workflow_plans.sql
-- Independent plans preserve the existing WorkTask and Worker protocol contracts.
CREATE TABLE flexible_workflow_plan (
    id VARCHAR(128) PRIMARY KEY,
    state VARCHAR(32) NOT NULL CHECK (state IN ('DRAFT','CONFIRMED','WITHDRAWN','STOPPED')),
    version BIGINT NOT NULL CHECK (version >= 0),
    spec_json TEXT NOT NULL,
    created_by VARCHAR(256) NOT NULL,
    updated_by VARCHAR(256) NOT NULL,
    reason VARCHAR(2000) NOT NULL,
    created_at_epoch_ms BIGINT NOT NULL,
    updated_at_epoch_ms BIGINT NOT NULL
);

CREATE TABLE work_task_skill_preference (
    task_id VARCHAR(128) PRIMARY KEY,
    preferred_skills TEXT NOT NULL,
    FOREIGN KEY (task_id) REFERENCES work_task(id)
);

CREATE TABLE flexible_workflow_control (
    id VARCHAR(128) PRIMARY KEY,
    plan_id VARCHAR(128) NOT NULL,
    action VARCHAR(16) NOT NULL CHECK (action IN ('JUMP','STOP')),
    from_step_index INTEGER NOT NULL,
    target_step_index INTEGER,
    task_id VARCHAR(128),
    lease_worker_id VARCHAR(128),
    lease_token VARCHAR(128),
    status VARCHAR(16) NOT NULL CHECK (status IN ('PENDING_STOP','APPLIED','CONFLICT')),
    actor VARCHAR(256) NOT NULL,
    reason VARCHAR(2000) NOT NULL,
    requested_at_epoch_ms BIGINT NOT NULL,
    applied_at_epoch_ms BIGINT,
    FOREIGN KEY (plan_id) REFERENCES flexible_workflow_plan(id),
    FOREIGN KEY (task_id) REFERENCES work_task(id),
    CHECK ((action='JUMP' AND target_step_index IS NOT NULL) OR (action='STOP' AND target_step_index IS NULL))
);
CREATE UNIQUE INDEX idx_flexible_workflow_control_pending
    ON flexible_workflow_control(plan_id) WHERE status='PENDING_STOP';
CREATE TABLE flexible_workflow_skipped_step (
    plan_id VARCHAR(128) NOT NULL,
    step_index INTEGER NOT NULL,
    control_id VARCHAR(128) NOT NULL,
    PRIMARY KEY (plan_id, step_index),
    FOREIGN KEY (plan_id) REFERENCES flexible_workflow_plan(id),
    FOREIGN KEY (control_id) REFERENCES flexible_workflow_control(id)
);

CREATE TABLE web_console_plan_archive (
    plan_id VARCHAR(128) PRIMARY KEY,
    archived_by VARCHAR(256) NOT NULL,
    archived_at_epoch_ms BIGINT NOT NULL,
    delete_requested_by VARCHAR(256),
    delete_requested_at_epoch_ms BIGINT,
    delete_after_epoch_ms BIGINT,
    FOREIGN KEY (plan_id) REFERENCES flexible_workflow_plan(id),
    CHECK ((delete_requested_by IS NULL AND delete_requested_at_epoch_ms IS NULL AND delete_after_epoch_ms IS NULL)
        OR (delete_requested_by IS NOT NULL AND delete_requested_at_epoch_ms IS NOT NULL
            AND delete_after_epoch_ms > delete_requested_at_epoch_ms))
);
CREATE INDEX idx_web_console_plan_archive_delete_due
    ON web_console_plan_archive(delete_after_epoch_ms, plan_id);

-- Web Console presentation archive; Run and task authority remain in their existing tables.
CREATE TABLE web_console_run_archive (
    run_id VARCHAR(128) PRIMARY KEY,
    archived_by VARCHAR(256) NOT NULL,
    archived_at_epoch_ms BIGINT NOT NULL,
    delete_requested_by VARCHAR(256),
    delete_requested_at_epoch_ms BIGINT,
    delete_after_epoch_ms BIGINT,
    FOREIGN KEY (run_id) REFERENCES autonomous_run(id),
    CHECK ((delete_requested_by IS NULL AND delete_requested_at_epoch_ms IS NULL AND delete_after_epoch_ms IS NULL)
        OR (delete_requested_by IS NOT NULL AND delete_requested_at_epoch_ms IS NOT NULL
            AND delete_after_epoch_ms > delete_requested_at_epoch_ms))
);

CREATE INDEX idx_web_console_run_archive_delete_due
    ON web_console_run_archive(delete_after_epoch_ms, run_id);

-- The development baseline keeps the historical statements above intact, then removes
-- their retired fixed-Run authority tables. Only flexible plans are created in new databases.
DROP TABLE web_console_run_archive;
DROP TABLE autonomous_run_event;
DROP TABLE autonomous_run;
