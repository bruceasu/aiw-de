-- Initial schema for a fresh database. Existing database history and data are not migrated.

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
    CHECK (state IN ('BLOCKED','READY','CLAIMED','RUNNING','YIELDING','WAITING_INPUT','CONTINUED','COMPLETED','FAILED','CANCELLED')),
    CHECK (
        (lease_token IS NULL AND current_worker_id IS NULL
            AND lease_acquired_at_epoch_ms IS NULL AND lease_expires_at_epoch_ms IS NULL
            AND lease_heartbeat_at_epoch_ms IS NULL)
        OR
        (lease_token IS NOT NULL AND current_worker_id IS NOT NULL
            AND lease_acquired_at_epoch_ms IS NOT NULL AND lease_expires_at_epoch_ms IS NOT NULL
            AND lease_heartbeat_at_epoch_ms IS NOT NULL)
    ),
    instruction TEXT NOT NULL DEFAULT '',
    repository_ref VARCHAR(256),
    base_revision VARCHAR(128),
    instruction_revision INTEGER NOT NULL DEFAULT 1);

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
    CHECK (state IN ('CLAIMED','RUNNING','YIELDED','COMPLETED','FAILED','EXPIRED')),
    workspace_key VARCHAR(256),
    branch_name VARCHAR(512),
    base_revision VARCHAR(128),
    result_revision VARCHAR(128),
    changed_files TEXT,
    publication_ref VARCHAR(2048),
    completion_verdict VARCHAR(32),
    completion_summary TEXT,
    engineering_gate_status VARCHAR(32),
    engineering_gate_review_task_id VARCHAR(128),
    engineering_gate_verification_task_id VARCHAR(128),
    engineering_gate_closed_at_epoch_ms BIGINT,
    termination_reason VARCHAR(64),
    ai_profile_id VARCHAR(64),
    ai_profile_version VARCHAR(64),
    ai_provider VARCHAR(120),
    ai_runtime VARCHAR(120),
    ai_model VARCHAR(120),
    ai_effort VARCHAR(120),
    ai_fast BOOLEAN,
    ai_selection_reason VARCHAR(512),
    failure_category VARCHAR(32),
    instruction_revision INTEGER NOT NULL DEFAULT 1,
    stop_requested_at_epoch_ms BIGINT,
    stop_acknowledged_at_epoch_ms BIGINT);

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

CREATE INDEX idx_work_task_repository_ready
    ON work_task(repository_ref, state, priority DESC, created_at_epoch_ms);

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
    ),
    control_state VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    pause_count INTEGER NOT NULL DEFAULT 0,
    resume_count INTEGER NOT NULL DEFAULT 0,
    last_control_action VARCHAR(32),
    last_controlled_by VARCHAR(256),
    last_control_reason VARCHAR(4096),
    last_controlled_at_epoch_ms BIGINT,
    cancelled_at_epoch_ms BIGINT,
    event_sequence BIGINT NOT NULL DEFAULT 0,
    max_parallelism INTEGER NOT NULL DEFAULT 0 CHECK (max_parallelism >= 0),
    active_slots INTEGER NOT NULL DEFAULT 0 CHECK (active_slots >= 0));

CREATE INDEX idx_autonomous_run_status
    ON autonomous_run(status, status_updated_at_epoch_ms);

CREATE INDEX idx_autonomous_run_approval
    ON autonomous_run(approval_request_id);

CREATE INDEX idx_autonomous_run_control
    ON autonomous_run(control_state, status, status_updated_at_epoch_ms);

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
    CHECK (event_type IN ('FAILED','RECOVERED','PAUSED','RESUMED','CANCELLED','MAX_PARALLELISM_CHANGED'))
);

CREATE INDEX idx_autonomous_run_event_run_time
    ON autonomous_run_event(run_id, occurred_at_epoch_ms, sequence_no);

CREATE INDEX idx_autonomous_run_admission
    ON autonomous_run(control_state, status, max_parallelism, active_slots);

CREATE TABLE fleet_admission_budget (
    id VARCHAR(32) PRIMARY KEY,
    max_parallelism INTEGER NOT NULL DEFAULT 0 CHECK (max_parallelism >= 0 AND max_parallelism <= 65535),
    updated_at_epoch_ms BIGINT NOT NULL
);

INSERT INTO fleet_admission_budget(id, max_parallelism, updated_at_epoch_ms)
VALUES ('GLOBAL', 0, 0);

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

CREATE TABLE direct_workspace_lock (
    repository_ref VARCHAR(120) PRIMARY KEY
);

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

CREATE TABLE flexible_workflow_plan (
    id VARCHAR(128) PRIMARY KEY,
    state VARCHAR(32) NOT NULL CHECK (state IN ('DRAFT','CONFIRMED','COMPLETED','WITHDRAWN','STOPPED')),
    version BIGINT NOT NULL CHECK (version >= 0),
    spec_json TEXT NOT NULL,
    created_by VARCHAR(256) NOT NULL,
    updated_by VARCHAR(256) NOT NULL,
    reason VARCHAR(2000) NOT NULL,
    created_at_epoch_ms BIGINT NOT NULL,
    updated_at_epoch_ms BIGINT NOT NULL
);

CREATE TABLE workflow_factory_plan (
    plan_id VARCHAR(128) PRIMARY KEY REFERENCES flexible_workflow_plan(id) ON DELETE CASCADE,
    original_draft_json TEXT NOT NULL,
    confirmed_intent_json TEXT NOT NULL,
    confirmed_by VARCHAR(256) NOT NULL,
    confirmed_ms BIGINT NOT NULL
);

CREATE TABLE workflow_factory_task (
    task_id VARCHAR(128) PRIMARY KEY REFERENCES work_task(id) ON DELETE CASCADE,
    plan_id VARCHAR(128) NOT NULL REFERENCES workflow_factory_plan(plan_id) ON DELETE CASCADE,
    step_key VARCHAR(32) NOT NULL,
    result_kind VARCHAR(32) NOT NULL,
    current_task BOOLEAN NOT NULL,
    created_ms BIGINT NOT NULL
);

CREATE INDEX idx_workflow_factory_task_plan ON workflow_factory_task(plan_id,current_task);

CREATE UNIQUE INDEX idx_workflow_factory_current_step ON workflow_factory_task(plan_id,step_key) WHERE current_task=TRUE;

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

CREATE TABLE flexible_workflow_human_outcome (
    plan_id VARCHAR(128) PRIMARY KEY,
    outcome VARCHAR(16) NOT NULL CHECK (outcome IN ('COMPLETE','CANCEL')),
    status VARCHAR(16) NOT NULL CHECK (status IN ('PENDING','APPLIED')),
    actor VARCHAR(256) NOT NULL,
    reason VARCHAR(2000) NOT NULL,
    decided_at_epoch_ms BIGINT NOT NULL,
    FOREIGN KEY (plan_id) REFERENCES flexible_workflow_plan(id)
);

CREATE TABLE task_output_bundle (
    attempt_id VARCHAR(128) PRIMARY KEY,
    task_id VARCHAR(128) NOT NULL,
    repository_ref VARCHAR(256) NOT NULL,
    base_revision VARCHAR(64),
    result_revision VARCHAR(64),
    directory TEXT NOT NULL,
    created_at_epoch_ms BIGINT NOT NULL,
    manifest_json TEXT NOT NULL,
    execution_state VARCHAR(32) NOT NULL DEFAULT 'REGISTERED'
);

CREATE INDEX idx_task_output_task ON task_output_bundle(task_id, created_at_epoch_ms);

CREATE TABLE task_output_file (
    id VARCHAR(128) PRIMARY KEY,
    attempt_id VARCHAR(128) NOT NULL,
    relative_path VARCHAR(512) NOT NULL,
    kind VARCHAR(32) NOT NULL,
    content_hash VARCHAR(64) NOT NULL,
    byte_count BIGINT NOT NULL,
    content_base64 TEXT NOT NULL,
    FOREIGN KEY (attempt_id) REFERENCES task_output_bundle(attempt_id),
    UNIQUE (attempt_id, relative_path)
);

CREATE TABLE task_output_publication (
    plan_id VARCHAR(128) PRIMARY KEY,
    file_id VARCHAR(128) NOT NULL,
    review_task_id VARCHAR(128) NOT NULL,
    target_path VARCHAR(512) NOT NULL,
    actor VARCHAR(256) NOT NULL,
    reason VARCHAR(2000) NOT NULL,
    created_at_epoch_ms BIGINT NOT NULL,
    FOREIGN KEY (file_id) REFERENCES task_output_file(id)
);

CREATE TABLE task_output_input (
    consumer_attempt_id VARCHAR(128) NOT NULL,
    source_attempt_id VARCHAR(128) NOT NULL,
    PRIMARY KEY (consumer_attempt_id, source_attempt_id)
);

CREATE TABLE task_output_review (
    source_attempt_id VARCHAR(128) NOT NULL,
    review_task_id VARCHAR(128) NOT NULL,
    review_attempt_id VARCHAR(128) NOT NULL,
    review_worker_id VARCHAR(128) NOT NULL,
    source_worker_id VARCHAR(128) NOT NULL,
    reviewed_at_epoch_ms BIGINT NOT NULL,
    PRIMARY KEY (source_attempt_id, review_task_id)
);

CREATE TABLE workflow_repair_authorization (
    id VARCHAR(128) PRIMARY KEY,
    plan_id VARCHAR(128) NOT NULL,
    plan_version BIGINT NOT NULL,
    source_review_id VARCHAR(128) NOT NULL,
    source_task_version BIGINT NOT NULL,
    scope TEXT NOT NULL,
    reason VARCHAR(2000) NOT NULL,
    granted_by VARCHAR(256) NOT NULL,
    delegate_worker_id VARCHAR(128),
    decision_task_id VARCHAR(128) UNIQUE,
    repair_task_id VARCHAR(128),
    round_budget INTEGER NOT NULL CHECK (round_budget = 1),
    status VARCHAR(16) NOT NULL CHECK (status IN ('PENDING','CONSUMED','DENIED','REVOKED','EXPIRED','STALE')),
    created_at_epoch_ms BIGINT NOT NULL,
    expires_at_epoch_ms BIGINT NOT NULL,
    decided_at_epoch_ms BIGINT
);

CREATE UNIQUE INDEX idx_workflow_repair_pending ON workflow_repair_authorization(plan_id) WHERE status='PENDING';

CREATE INDEX idx_workflow_repair_history ON workflow_repair_authorization(plan_id,created_at_epoch_ms);

CREATE TABLE workflow_question_decision (
    id VARCHAR(128) PRIMARY KEY,
    plan_id VARCHAR(128) NOT NULL,
    question_key VARCHAR(64) NOT NULL,
    source_task_id VARCHAR(256) NOT NULL,
    source_file_id VARCHAR(128) NOT NULL,
    source_hash VARCHAR(64) NOT NULL,
    question_id VARCHAR(160) NOT NULL,
    question_text TEXT NOT NULL,
    answer TEXT NOT NULL,
    actor VARCHAR(256) NOT NULL,
    plan_version BIGINT NOT NULL,
    decided_at_epoch_ms BIGINT NOT NULL,
    FOREIGN KEY (plan_id) REFERENCES flexible_workflow_plan(id) ON DELETE CASCADE,
    UNIQUE (plan_id, plan_version)
);

CREATE INDEX idx_workflow_question_decision ON workflow_question_decision(plan_id, question_key, plan_version);

CREATE TABLE workflow_recommendation_settings (
    id INTEGER PRIMARY KEY,
    enabled BOOLEAN NOT NULL,
    planner VARCHAR(64), model VARCHAR(120), effort VARCHAR(120),
    revision BIGINT NOT NULL,
    updated_by VARCHAR(256) NOT NULL,
    updated_at_epoch_ms BIGINT NOT NULL
);

INSERT INTO workflow_recommendation_settings VALUES (1, TRUE, NULL, NULL, NULL, 0, 'system', 0);

CREATE TABLE workflow_recommendation_audit (
    id VARCHAR(128) PRIMARY KEY, actor VARCHAR(256) NOT NULL,
    occurred_at_epoch_ms BIGINT NOT NULL, previous_json TEXT NOT NULL, current_json TEXT NOT NULL
);

CREATE TABLE workflow_recommendation_context (
    id VARCHAR(128) PRIMARY KEY, actor VARCHAR(256) NOT NULL,
    goal TEXT NOT NULL, input_version BIGINT NOT NULL, revision BIGINT NOT NULL,
    plan_id VARCHAR(128), updated_at_epoch_ms BIGINT NOT NULL,
    FOREIGN KEY (plan_id) REFERENCES flexible_workflow_plan(id) ON DELETE SET NULL
);

CREATE INDEX idx_recommendation_context_actor ON workflow_recommendation_context(actor, updated_at_epoch_ms);

CREATE TABLE workflow_recommendation_request (
    task_id VARCHAR(256) PRIMARY KEY, request_key VARCHAR(64) NOT NULL UNIQUE,
    context_id VARCHAR(128) NOT NULL, actor VARCHAR(256) NOT NULL,
    goal TEXT NOT NULL, input_version BIGINT NOT NULL,
    settings_revision BIGINT NOT NULL, frozen_json TEXT NOT NULL, context_revision BIGINT NOT NULL,
    created_at_epoch_ms BIGINT NOT NULL,
    FOREIGN KEY (context_id) REFERENCES workflow_recommendation_context(id),
    FOREIGN KEY (task_id) REFERENCES work_task(id)
);

CREATE INDEX idx_recommendation_request_context ON workflow_recommendation_request(context_id, created_at_epoch_ms);

CREATE TABLE workflow_recommendation_plan_context (
    plan_id VARCHAR(128) PRIMARY KEY, context_id VARCHAR(128) NOT NULL,
    FOREIGN KEY (plan_id) REFERENCES flexible_workflow_plan(id) ON DELETE CASCADE,
    FOREIGN KEY (context_id) REFERENCES workflow_recommendation_context(id)
);

CREATE TABLE goal_draft (
 id VARCHAR(64) PRIMARY KEY, actor VARCHAR(256) NOT NULL, title VARCHAR(160) NOT NULL,
 revision BIGINT NOT NULL, epoch BIGINT NOT NULL, deleted BOOLEAN NOT NULL,
 payload TEXT NOT NULL, updated_at BIGINT NOT NULL
);

CREATE INDEX idx_goal_actor ON goal_draft(actor, updated_at);

CREATE TABLE goal_attachment (
 id VARCHAR(64) PRIMARY KEY, goal_id VARCHAR(64) NOT NULL,
 filename VARCHAR(256) NOT NULL, content_base64 TEXT NOT NULL, sha256 VARCHAR(64) NOT NULL,
 status VARCHAR(64) NOT NULL, extracted TEXT NOT NULL, detail TEXT NOT NULL,
 FOREIGN KEY(goal_id) REFERENCES goal_draft(id)
);

CREATE TABLE goal_request (
 id VARCHAR(64) PRIMARY KEY, goal_id VARCHAR(64) NOT NULL, actor VARCHAR(256) NOT NULL,
 source_revision BIGINT NOT NULL, epoch BIGINT NOT NULL, input_hash VARCHAR(64) NOT NULL,
 frozen_json TEXT NOT NULL, consumed BOOLEAN NOT NULL,
 FOREIGN KEY(goal_id) REFERENCES goal_draft(id), FOREIGN KEY(id) REFERENCES work_task(id)
);

CREATE TABLE goal_version (
 id VARCHAR(64) PRIMARY KEY, goal_id VARCHAR(64) NOT NULL, source_revision BIGINT NOT NULL,
 snapshot TEXT NOT NULL, editor_json TEXT NOT NULL, editor_revision BIGINT NOT NULL,
 recommendation_id VARCHAR(128), plan_id VARCHAR(128),
 UNIQUE(goal_id, source_revision), FOREIGN KEY(goal_id) REFERENCES goal_draft(id),
 FOREIGN KEY(plan_id) REFERENCES flexible_workflow_plan(id) ON DELETE SET NULL
);

CREATE TABLE secretary_work (
 id VARCHAR(64) PRIMARY KEY, actor VARCHAR(256) NOT NULL,
 revision BIGINT NOT NULL, generation BIGINT NOT NULL, state VARCHAR(32) NOT NULL,
 plan_id VARCHAR(128) UNIQUE, due_ms BIGINT, payload TEXT NOT NULL, updated_ms BIGINT NOT NULL
);

CREATE INDEX idx_secretary_due ON secretary_work(state,due_ms);

CREATE INDEX idx_secretary_actor ON secretary_work(actor,updated_ms);

CREATE TABLE secretary_request (
 id VARCHAR(128) PRIMARY KEY, work_id VARCHAR(64) NOT NULL, generation BIGINT NOT NULL,
 phase VARCHAR(128) NOT NULL, plan_version BIGINT NOT NULL, source_attempt VARCHAR(128),
 consumed BOOLEAN NOT NULL, FOREIGN KEY(work_id) REFERENCES secretary_work(id),
 UNIQUE(work_id,generation,phase)
);

CREATE TABLE authority_lock (id INTEGER PRIMARY KEY CHECK(id=1), version BIGINT NOT NULL);

INSERT INTO authority_lock VALUES(1,0);

CREATE TABLE authority_metadata (id INTEGER PRIMARY KEY CHECK(id=1), instance_id VARCHAR(64), human_auth_required BOOLEAN NOT NULL, retired_data_present BOOLEAN NOT NULL);

INSERT INTO authority_metadata VALUES(1,NULL,FALSE,FALSE);

CREATE TABLE authority_owner (id VARCHAR(64) PRIMARY KEY, actor VARCHAR(256) NOT NULL UNIQUE);

CREATE TABLE authority_identity (
 worker_id VARCHAR(128) PRIMARY KEY, owner_id VARCHAR(64) NOT NULL, key_hash VARCHAR(64) NOT NULL,
 generation BIGINT NOT NULL, enabled_boot VARCHAR(64), changed_by VARCHAR(256) NOT NULL, changed_ms BIGINT NOT NULL,
 read_scope TEXT NOT NULL, worker_boot VARCHAR(64),
    identity_kind VARCHAR(32) NOT NULL DEFAULT 'LEGACY_PERSONAL',
    connection_profile VARCHAR(16) NOT NULL DEFAULT 'LOCAL',
    client_cert_sha256 VARCHAR(64),
    approval_state VARCHAR(24) NOT NULL DEFAULT 'LEGACY');

CREATE TABLE authority_target (
 target_key VARCHAR(1024) PRIMARY KEY, owner_id VARCHAR(64) NOT NULL,
 generation BIGINT NOT NULL, admitted_ms BIGINT NOT NULL, paused BOOLEAN NOT NULL,
 restriction_revision BIGINT NOT NULL
);

CREATE TABLE authority_grant (
 id VARCHAR(128) PRIMARY KEY, owner VARCHAR(64) NOT NULL, worker_id VARCHAR(128) NOT NULL,
 revision BIGINT NOT NULL, state VARCHAR(16) NOT NULL, scope_json TEXT NOT NULL,
 authority VARCHAR(64) NOT NULL, effective_ms BIGINT NOT NULL, expires_ms BIGINT NOT NULL, target_key VARCHAR(1024) NOT NULL
);

CREATE TABLE authority_budget (
 target_key VARCHAR(1024) NOT NULL, action VARCHAR(32) NOT NULL,
 limit_value BIGINT NOT NULL CHECK(limit_value>=0), used_value BIGINT NOT NULL CHECK(used_value>=0),
 reserved_value BIGINT NOT NULL CHECK(reserved_value>=0), PRIMARY KEY(target_key,action)
);

CREATE TABLE authority_restriction (
 id VARCHAR(128) PRIMARY KEY, owner_id VARCHAR(64) NOT NULL, target_key VARCHAR(1024) NOT NULL,
 scope_json TEXT NOT NULL, active BOOLEAN NOT NULL, created_ms BIGINT NOT NULL, private_digest VARCHAR(64) NOT NULL
);

CREATE TABLE authority_receipt (
 instance_id VARCHAR(64) NOT NULL, owner_id VARCHAR(64) NOT NULL, request_id VARCHAR(64) NOT NULL,
 digest VARCHAR(64) NOT NULL, worker_id VARCHAR(128) NOT NULL,
 grant_id VARCHAR(128) NOT NULL, grant_revision BIGINT NOT NULL, target_key VARCHAR(1024) NOT NULL,
 state VARCHAR(32) NOT NULL, result_json TEXT NOT NULL, created_ms BIGINT NOT NULL, updated_ms BIGINT NOT NULL,
 PRIMARY KEY(instance_id,owner_id,request_id)
);

CREATE TABLE authority_audit (
 id VARCHAR(128) PRIMARY KEY, target_key VARCHAR(1024), grant_id VARCHAR(128), request_id VARCHAR(128),
 actor VARCHAR(256) NOT NULL, event VARCHAR(64) NOT NULL, detail TEXT NOT NULL, created_ms BIGINT NOT NULL
);

CREATE TABLE authority_task (
 task_id VARCHAR(128) PRIMARY KEY, target_key VARCHAR(1024) NOT NULL, allowed_attempt BIGINT NOT NULL,
 reserved BOOLEAN NOT NULL, started BOOLEAN NOT NULL, settled BOOLEAN NOT NULL,
 grant_id VARCHAR(128), grant_revision BIGINT, worker_generation BIGINT, enabled_boot VARCHAR(64),
 target_generation BIGINT, restriction_revision BIGINT, request_id VARCHAR(64), post_version BIGINT,
 state VARCHAR(32) NOT NULL DEFAULT 'PREPARED'
);

CREATE TABLE authority_publication_plan (plan_id VARCHAR(128) PRIMARY KEY, target_key VARCHAR(1024) NOT NULL);

CREATE TABLE authority_publication_attempt (
 task_id VARCHAR(128) PRIMARY KEY, allowed_attempt BIGINT NOT NULL, grant_id VARCHAR(128),
 grant_revision BIGINT, worker_generation BIGINT, enabled_boot VARCHAR(64),
 target_key VARCHAR(1024) NOT NULL, owner_id VARCHAR(64) NOT NULL,
 target_generation BIGINT NOT NULL, restriction_revision BIGINT NOT NULL
);

CREATE TABLE authority_model_scope (
 target_key VARCHAR(1024) NOT NULL, role VARCHAR(16) NOT NULL, catalog_version VARCHAR(64) NOT NULL,
 profiles_json TEXT NOT NULL, grant_id VARCHAR(128) NOT NULL, grant_revision BIGINT NOT NULL,
 PRIMARY KEY(target_key,role)
);

CREATE TABLE authority_model_constraint (
 task_id VARCHAR(128) PRIMARY KEY, grant_id VARCHAR(128) NOT NULL, grant_revision BIGINT NOT NULL,
 catalog_version VARCHAR(64) NOT NULL, profiles_json TEXT NOT NULL
);

CREATE TABLE authority_managed_plan (
 plan_id VARCHAR(128) PRIMARY KEY, owner_id VARCHAR(64) NOT NULL, worker_id VARCHAR(128) NOT NULL,
 spec_digest VARCHAR(64) NOT NULL, version BIGINT NOT NULL, generation BIGINT NOT NULL,
 expires_ms BIGINT NOT NULL, authority VARCHAR(64) NOT NULL, paused BOOLEAN NOT NULL
);

CREATE TABLE authority_participant (
 plan_id VARCHAR(128) NOT NULL, worker_id VARCHAR(128) NOT NULL, role VARCHAR(16) NOT NULL,
 PRIMARY KEY(plan_id,worker_id)
);

CREATE TABLE authority_coordination_source (
 plan_id VARCHAR(128) NOT NULL, source_attempt VARCHAR(128) NOT NULL, request_id VARCHAR(64) NOT NULL,
 PRIMARY KEY(plan_id,source_attempt)
);

CREATE TABLE authority_effect_history (
 task_id VARCHAR(128) NOT NULL, attempt BIGINT NOT NULL, snapshot TEXT NOT NULL,
 PRIMARY KEY(task_id,attempt)
);

CREATE TABLE execution_policy (
    plan_id VARCHAR(128) PRIMARY KEY, owner_id VARCHAR(128) NOT NULL REFERENCES authority_owner(id),
    revision BIGINT NOT NULL, generation BIGINT NOT NULL DEFAULT 0, digest VARCHAR(64) NOT NULL,
    policy_json TEXT NOT NULL, expires_ms BIGINT NOT NULL, revoked BOOLEAN NOT NULL DEFAULT FALSE,
    paused BOOLEAN NOT NULL DEFAULT FALSE, confirmed_by VARCHAR(256) NOT NULL, confirmed_ms BIGINT NOT NULL
);

CREATE TABLE execution_context (
    task_id VARCHAR(128) PRIMARY KEY REFERENCES work_task(id), plan_id VARCHAR(128) NOT NULL REFERENCES execution_policy(plan_id),
    policy_revision BIGINT NOT NULL, generation BIGINT NOT NULL, decision_id VARCHAR(128) NOT NULL,
    config_digest VARCHAR(64) NOT NULL, policy_json TEXT NOT NULL, input_json TEXT NOT NULL, input_digest VARCHAR(64) NOT NULL,
    task_version BIGINT NOT NULL, resumes_task_id VARCHAR(128) UNIQUE REFERENCES work_task(id),
    successor_task_id VARCHAR(128) UNIQUE REFERENCES work_task(id)
);

CREATE TABLE execution_budget (
    plan_id VARCHAR(128) NOT NULL REFERENCES execution_policy(plan_id), dimension VARCHAR(32) NOT NULL,
    loop_id VARCHAR(128) NOT NULL DEFAULT '', limit_value BIGINT, used_value BIGINT NOT NULL DEFAULT 0,
    reserved_value BIGINT NOT NULL DEFAULT 0, unknown_count BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY(plan_id,dimension,loop_id),
    CHECK(used_value>=0 AND reserved_value>=0 AND unknown_count>=0), CHECK(limit_value IS NULL OR limit_value>=0)
);

CREATE TABLE execution_increment (
    decision_id VARCHAR(128) PRIMARY KEY, plan_id VARCHAR(128) NOT NULL REFERENCES execution_policy(plan_id),
    request_digest VARCHAR(64) NOT NULL, decision_json TEXT NOT NULL, actor VARCHAR(256) NOT NULL, created_ms BIGINT NOT NULL
);

CREATE TABLE execution_meter (
    attempt_id VARCHAR(128) PRIMARY KEY REFERENCES execution_attempt(id), task_id VARCHAR(128) NOT NULL REFERENCES work_task(id),
    plan_id VARCHAR(128) NOT NULL REFERENCES execution_policy(plan_id), started_ms BIGINT,
    reserved_time BIGINT NOT NULL, reserved_cost BIGINT NOT NULL, input_tokens BIGINT NOT NULL DEFAULT 0,
    cached_tokens BIGINT NOT NULL DEFAULT 0, output_tokens BIGINT NOT NULL DEFAULT 0,
    usage_unknown BOOLEAN NOT NULL DEFAULT FALSE, settled BOOLEAN NOT NULL DEFAULT FALSE,
    time_unknown BOOLEAN NOT NULL DEFAULT FALSE,
    elapsed_ms BIGINT, cost_micros BIGINT, price_json TEXT NOT NULL
);

CREATE TABLE execution_meter_reconciliation (
    attempt_id VARCHAR(128) PRIMARY KEY REFERENCES execution_attempt(id), decision_json TEXT NOT NULL,
    actor VARCHAR(256) NOT NULL, created_ms BIGINT NOT NULL
);

CREATE TABLE execution_operation (
    operation_id VARCHAR(128) PRIMARY KEY, task_id VARCHAR(128) NOT NULL REFERENCES work_task(id),
    attempt_id VARCHAR(128) NOT NULL REFERENCES execution_attempt(id), worker_id VARCHAR(128) NOT NULL,
    lease_token VARCHAR(128) NOT NULL, plan_id VARCHAR(128) NOT NULL REFERENCES execution_policy(plan_id),
    request_digest VARCHAR(64) NOT NULL, tool_id VARCHAR(32) NOT NULL, repository_ref VARCHAR(120) NOT NULL,
    resource VARCHAR(1024) NOT NULL, state VARCHAR(32) NOT NULL,
    started_ms BIGINT NOT NULL, finished_ms BIGINT, finish_digest VARCHAR(64), result_digest VARCHAR(64), evidence_ref VARCHAR(1024),
    reconciliation_json TEXT, CHECK(state IN ('STARTED','SUCCEEDED','FAILED','UNKNOWN','REJECTED','RECONCILED'))
);

CREATE INDEX idx_execution_operation_task_state ON execution_operation(task_id,state);

CREATE TABLE execution_yield (
    task_id VARCHAR(128) PRIMARY KEY REFERENCES work_task(id), operation_id VARCHAR(128) NOT NULL UNIQUE,
    attempt_id VARCHAR(128) NOT NULL REFERENCES execution_attempt(id), worker_id VARCHAR(128) NOT NULL,
    lease_token VARCHAR(128) NOT NULL, checkpoint_json TEXT NOT NULL, request_digest VARCHAR(64) NOT NULL,
    state VARCHAR(16) NOT NULL, acknowledged_ms BIGINT, CHECK(state IN ('PREPARED','ACKNOWLEDGED'))
);

CREATE TABLE execution_delivery (
    task_id VARCHAR(128) NOT NULL REFERENCES work_task(id), attempt_id VARCHAR(128) PRIMARY KEY REFERENCES execution_attempt(id),
    digest VARCHAR(64) NOT NULL, delivery_json TEXT NOT NULL, created_ms BIGINT NOT NULL
);

CREATE TABLE execution_completion (
    attempt_id VARCHAR(128) PRIMARY KEY REFERENCES execution_attempt(id), task_id VARCHAR(128) NOT NULL REFERENCES work_task(id), workspace_json TEXT, report_json TEXT,
    event_type VARCHAR(16) NOT NULL DEFAULT 'COMPLETED', failure_reason VARCHAR(4096),
    applied BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE execution_backjump (
    transition_id VARCHAR(128) PRIMARY KEY, plan_id VARCHAR(128) NOT NULL REFERENCES execution_policy(plan_id),
    loop_id VARCHAR(128) NOT NULL, created_ms BIGINT NOT NULL
);

CREATE TABLE execution_task_charge (
    task_id VARCHAR(128) PRIMARY KEY REFERENCES work_task(id), plan_id VARCHAR(128) NOT NULL REFERENCES execution_policy(plan_id)
);

CREATE TABLE execution_stop_evidence (
    attempt_id VARCHAR(128) PRIMARY KEY REFERENCES execution_attempt(id), decision_json TEXT NOT NULL
);

CREATE TABLE execution_asset (
    kind VARCHAR(16) NOT NULL, ref VARCHAR(96) NOT NULL, version VARCHAR(64) NOT NULL,
    digest VARCHAR(64) NOT NULL, owner_id VARCHAR(128) NOT NULL, repository_ref VARCHAR(120) NOT NULL,
    asset_json TEXT NOT NULL, PRIMARY KEY(kind,ref,version,owner_id,repository_ref)
);

CREATE TABLE execution_configuration (
    owner_id VARCHAR(128) NOT NULL REFERENCES authority_owner(id), id VARCHAR(96) NOT NULL, revision BIGINT NOT NULL,
    digest VARCHAR(64) NOT NULL, repository_ref VARCHAR(120) NOT NULL, content_json TEXT NOT NULL,
    PRIMARY KEY(owner_id,id,revision)
);

CREATE TABLE execution_preparation (
    intent_id VARCHAR(96) NOT NULL, intent_version BIGINT NOT NULL, owner_id VARCHAR(128) NOT NULL REFERENCES authority_owner(id),
    generation BIGINT NOT NULL, state VARCHAR(32) NOT NULL, intent_digest VARCHAR(64) NOT NULL, intent_json TEXT NOT NULL,
    config_json TEXT, task_id VARCHAR(128) UNIQUE REFERENCES work_task(id), error_code VARCHAR(48), details_json TEXT NOT NULL,
    PRIMARY KEY(intent_id,intent_version), CHECK(state IN ('NEEDS_CONFIGURATION','PREPARING','READY','BLOCKED'))
);

CREATE TABLE execution_task_configuration (
    task_id VARCHAR(128) PRIMARY KEY REFERENCES work_task(id), binding_json TEXT NOT NULL
);

CREATE TABLE execution_worker_configuration (
    worker_id VARCHAR(128) PRIMARY KEY REFERENCES worker(id), capabilities_json TEXT NOT NULL, capabilities_digest VARCHAR(64) NOT NULL
);

CREATE TABLE execution_attempt_configuration (
    attempt_id VARCHAR(128) PRIMARY KEY REFERENCES execution_attempt(id), task_id VARCHAR(128) NOT NULL REFERENCES work_task(id),
    worker_id VARCHAR(128) NOT NULL, capabilities_json TEXT NOT NULL, capabilities_digest VARCHAR(64) NOT NULL
);

CREATE TABLE formal_question (
 id VARCHAR(128) PRIMARY KEY, owner_id VARCHAR(128) NOT NULL, plan_id VARCHAR(128) NOT NULL,
 source_key VARCHAR(128) NOT NULL, source_digest VARCHAR(64) NOT NULL, request_digest VARCHAR(64) NOT NULL,
 payload_json TEXT NOT NULL, state VARCHAR(24) NOT NULL, request_version BIGINT NOT NULL,
 answer_revision BIGINT NOT NULL DEFAULT 0, answer_json TEXT, successor_task_id VARCHAR(128),
 blocked_reason VARCHAR(128), created_ms BIGINT NOT NULL, updated_ms BIGINT NOT NULL,
 UNIQUE(owner_id,plan_id,source_key)
);

CREATE TABLE formal_question_answer (
 question_id VARCHAR(128) NOT NULL, answer_revision BIGINT NOT NULL, answer_json TEXT NOT NULL,
 PRIMARY KEY(question_id,answer_revision)
);

CREATE TABLE formal_question_inbox (
 owner_id VARCHAR(128) NOT NULL, channel VARCHAR(32) NOT NULL, message_id VARCHAR(128) NOT NULL,
 question_id VARCHAR(128) NOT NULL, request_digest VARCHAR(64) NOT NULL, receipt_json TEXT NOT NULL,
 PRIMARY KEY(owner_id,channel,message_id)
);

CREATE TABLE formal_question_proposal (
 owner_id VARCHAR(128) NOT NULL, worker_id VARCHAR(128) NOT NULL, request_id VARCHAR(128) NOT NULL,
 projection_json TEXT NOT NULL, projection_digest VARCHAR(64) NOT NULL,
 envelope_json TEXT NOT NULL, instance_id VARCHAR(128) NOT NULL, worker_generation BIGINT NOT NULL,
 worker_boot VARCHAR(128) NOT NULL, platform_boot VARCHAR(128) NOT NULL, PRIMARY KEY(owner_id,request_id)
);

CREATE TABLE formal_question_slot (
 source_task_id VARCHAR(128) PRIMARY KEY, question_id VARCHAR(128) NOT NULL UNIQUE,
 checkpoint_json TEXT NOT NULL, successor_task_id VARCHAR(128)
);

CREATE TABLE question_telegram_binding (
 owner_id VARCHAR(128) PRIMARY KEY, actor VARCHAR(256) NOT NULL, user_id VARCHAR(32) NOT NULL,
 chat_id VARCHAR(32) NOT NULL, generation BIGINT NOT NULL, enabled BOOLEAN NOT NULL,
 UNIQUE(user_id,chat_id)
);

CREATE TABLE question_telegram_outbox (
 id VARCHAR(128) PRIMARY KEY, owner_id VARCHAR(128) NOT NULL, question_id VARCHAR(128) NOT NULL,
 request_version BIGINT NOT NULL, binding_generation BIGINT NOT NULL, token_digest VARCHAR(64) NOT NULL,
 expires_ms BIGINT NOT NULL, state VARCHAR(24) NOT NULL, message_id VARCHAR(32),
 UNIQUE(token_digest)
);

CREATE TABLE question_telegram_inbox (
 update_id BIGINT PRIMARY KEY, identity_key VARCHAR(256) NOT NULL UNIQUE,
 received_ms BIGINT NOT NULL, outcome VARCHAR(32) NOT NULL
);

CREATE TABLE question_telegram_poll (
 bot_id VARCHAR(64) PRIMARY KEY, next_offset BIGINT NOT NULL
);

CREATE TABLE question_telegram_update (
 bot_id VARCHAR(64) NOT NULL, update_id BIGINT NOT NULL, payload_json TEXT NOT NULL,
 state VARCHAR(24) NOT NULL, PRIMARY KEY(bot_id,update_id)
);

CREATE TABLE workflow_graph (
 id VARCHAR(96) PRIMARY KEY, owner_id VARCHAR(128) NOT NULL, edit_version BIGINT NOT NULL,
 state VARCHAR(32) NOT NULL, runtime_revision BIGINT NOT NULL DEFAULT 0, runtime_json TEXT NOT NULL
);

CREATE TABLE workflow_graph_version (
 plan_id VARCHAR(96) NOT NULL, version BIGINT NOT NULL, spec_json TEXT NOT NULL,
 digest VARCHAR(64) NOT NULL, source VARCHAR(128) NOT NULL, reason TEXT NOT NULL,
 PRIMARY KEY(plan_id,version)
);

CREATE TABLE workflow_graph_request (
 owner_id VARCHAR(128) NOT NULL, request_id VARCHAR(128) NOT NULL, plan_id VARCHAR(96) NOT NULL,
 digest VARCHAR(64) NOT NULL, receipt_json TEXT NOT NULL, PRIMARY KEY(owner_id,request_id)
);

CREATE TABLE workflow_graph_event (
 plan_id VARCHAR(96) NOT NULL, event_id VARCHAR(128) NOT NULL, execution_id VARCHAR(128),
 kind VARCHAR(64) NOT NULL, payload_json TEXT NOT NULL, created_ms BIGINT NOT NULL,
 PRIMARY KEY(plan_id,event_id)
);

CREATE TABLE workflow_graph_task (
 task_id VARCHAR(128) PRIMARY KEY, plan_id VARCHAR(96) NOT NULL, execution_id VARCHAR(128) NOT NULL
);

CREATE INDEX workflow_graph_task_execution ON workflow_graph_task(plan_id,execution_id);

CREATE TABLE workflow_graph_recommendation (
 request_id VARCHAR(128) PRIMARY KEY, plan_id VARCHAR(96) NOT NULL, plan_version BIGINT NOT NULL,
 task_id VARCHAR(128), intent_json TEXT NOT NULL, state VARCHAR(32) NOT NULL,
 candidate_json TEXT, diagnostic TEXT
);

CREATE TABLE selected_goal_proposal (
 owner_id VARCHAR(128) NOT NULL, request_id VARCHAR(128) NOT NULL, worker_id VARCHAR(128) NOT NULL,
 projection_json TEXT NOT NULL, digest VARCHAR(64) NOT NULL, instance_id VARCHAR(128) NOT NULL,
 generation BIGINT NOT NULL, worker_boot VARCHAR(128) NOT NULL, platform_boot VARCHAR(128) NOT NULL,
 receipt_json TEXT NOT NULL, adopted_plan VARCHAR(96), adoption_digest VARCHAR(64), adoption_receipt TEXT,
 PRIMARY KEY(owner_id,request_id)
);

CREATE TABLE execution_task_skills (
    task_id TEXT PRIMARY KEY REFERENCES work_task(id),
    binding_json TEXT NOT NULL,
    digest TEXT NOT NULL,
    configured INTEGER NOT NULL CHECK(configured IN (0,1))
);

CREATE TABLE authority_action_permission (
 id VARCHAR(128) PRIMARY KEY, owner_id VARCHAR(64) NOT NULL, worker_id VARCHAR(128) NOT NULL,
 action VARCHAR(32) NOT NULL, scope_kind VARCHAR(16) NOT NULL, scope_id VARCHAR(128),
 maximum_uses BIGINT NOT NULL CHECK(maximum_uses>0), used_uses BIGINT NOT NULL DEFAULT 0 CHECK(used_uses>=0),
 state VARCHAR(16) NOT NULL, authority VARCHAR(64) NOT NULL, effective_ms BIGINT NOT NULL, expires_ms BIGINT NOT NULL,
 creation_request VARCHAR(36) NOT NULL, creation_digest VARCHAR(64) NOT NULL,
 UNIQUE(owner_id,creation_request),
    revision BIGINT NOT NULL DEFAULT 1);

CREATE UNIQUE INDEX authority_permission_current ON authority_action_permission(owner_id,worker_id,action) WHERE state='ENABLED';

CREATE TABLE authority_permission_child (
 grant_id VARCHAR(128) PRIMARY KEY REFERENCES authority_grant(id),
 permission_id VARCHAR(128) NOT NULL REFERENCES authority_action_permission(id),
 request_id VARCHAR(36) NOT NULL, source_plan VARCHAR(128), source_task VARCHAR(128), question_id VARCHAR(128)
);

CREATE TABLE authority_permission_preparation (
 owner_id VARCHAR(64) NOT NULL, worker_id VARCHAR(128) NOT NULL, request_id VARCHAR(36) NOT NULL,
 permission_id VARCHAR(128) NOT NULL REFERENCES authority_action_permission(id),
 selection_digest VARCHAR(64) NOT NULL, grant_id VARCHAR(128) NOT NULL REFERENCES authority_grant(id),
 envelope_json TEXT NOT NULL, projection_json TEXT, instance_id VARCHAR(64) NOT NULL,
 worker_generation BIGINT NOT NULL, platform_boot VARCHAR(36) NOT NULL, worker_boot VARCHAR(36) NOT NULL,
 PRIMARY KEY(owner_id,worker_id,request_id), UNIQUE(grant_id)
);

CREATE TABLE authority_permission_use (
 permission_id VARCHAR(128) NOT NULL REFERENCES authority_action_permission(id), request_id VARCHAR(36) NOT NULL,
 grant_id VARCHAR(128) NOT NULL REFERENCES authority_grant(id), accepted_ms BIGINT NOT NULL,
 PRIMARY KEY(permission_id,request_id)
);

CREATE TABLE execution_workbench_session (
    attempt_id VARCHAR(128) PRIMARY KEY REFERENCES execution_attempt(id),
    task_id VARCHAR(128) NOT NULL REFERENCES work_task(id),
    owner_id VARCHAR(128) NOT NULL REFERENCES authority_owner(id),
    instruction_revision INTEGER NOT NULL, runtime VARCHAR(32) NOT NULL,
    phase VARCHAR(32) NOT NULL, updated_ms BIGINT NOT NULL
);

CREATE TABLE execution_workbench_message (
    id VARCHAR(96) PRIMARY KEY, owner_id VARCHAR(128) NOT NULL REFERENCES authority_owner(id),
    task_id VARCHAR(128) NOT NULL REFERENCES work_task(id),
    attempt_id VARCHAR(128) NOT NULL REFERENCES execution_workbench_session(attempt_id),
    instruction_revision INTEGER NOT NULL, request_id VARCHAR(80) NOT NULL,
    mode VARCHAR(24) NOT NULL, state VARCHAR(24) NOT NULL, body TEXT NOT NULL,
    digest VARCHAR(64) NOT NULL, sequence_no BIGINT NOT NULL, batch_id VARCHAR(96),
    created_ms BIGINT NOT NULL, updated_ms BIGINT NOT NULL,
    UNIQUE(owner_id,request_id), UNIQUE(attempt_id,sequence_no)
);

CREATE TABLE execution_workbench_batch (
    attempt_id VARCHAR(128) NOT NULL REFERENCES execution_workbench_session(attempt_id),
    batch_id VARCHAR(96) NOT NULL, mode VARCHAR(24) NOT NULL, digest VARCHAR(64) NOT NULL,
    contents_json TEXT NOT NULL, operation_id VARCHAR(128) REFERENCES execution_operation(operation_id),
    state VARCHAR(24) NOT NULL, created_ms BIGINT NOT NULL,
    PRIMARY KEY(attempt_id,batch_id)
);

CREATE TABLE execution_workbench_receipt (
    owner_id VARCHAR(128) NOT NULL REFERENCES authority_owner(id), request_id VARCHAR(80) NOT NULL,
    command_digest VARCHAR(64) NOT NULL, result_json TEXT NOT NULL,
    PRIMARY KEY(owner_id,request_id)
);

CREATE TABLE execution_exploration (
    id VARCHAR(96) PRIMARY KEY, owner_id VARCHAR(128) NOT NULL REFERENCES authority_owner(id),
    task_id VARCHAR(128) NOT NULL REFERENCES work_task(id),
    attempt_id VARCHAR(128) NOT NULL REFERENCES execution_attempt(id), instruction_revision INTEGER NOT NULL,
    parent_id VARCHAR(96) REFERENCES execution_exploration(id), title VARCHAR(120) NOT NULL,
    body TEXT NOT NULL, result_revision VARCHAR(64), actor VARCHAR(128) NOT NULL, created_ms BIGINT NOT NULL
);

CREATE TABLE execution_exploration_choice (
    owner_id VARCHAR(128) NOT NULL REFERENCES authority_owner(id), request_id VARCHAR(80) NOT NULL,
    node_id VARCHAR(96) NOT NULL REFERENCES execution_exploration(id),
    decision VARCHAR(24) NOT NULL, reason TEXT NOT NULL, actor VARCHAR(128) NOT NULL, created_ms BIGINT NOT NULL,
    PRIMARY KEY(owner_id,request_id)
);

CREATE INDEX execution_workbench_message_scope ON execution_workbench_message(owner_id,attempt_id,sequence_no);

CREATE INDEX execution_exploration_scope ON execution_exploration(owner_id,task_id,created_ms);

CREATE TABLE plan_git_delivery (
    plan_id VARCHAR(128) NOT NULL REFERENCES flexible_workflow_plan(id) ON DELETE CASCADE,
    repository_ref VARCHAR(256) NOT NULL,
    repository_path TEXT NOT NULL,
    result_revision VARCHAR(64) NOT NULL,
    request_id VARCHAR(64) NOT NULL UNIQUE,
    state VARCHAR(16) NOT NULL CHECK (state IN ('READY','BLOCKED','MERGING','DELIVERED','UNKNOWN')),
    actor VARCHAR(256) NOT NULL,
    target_branch VARCHAR(512) NOT NULL,
    before_revision VARCHAR(64) NOT NULL,
    after_revision VARCHAR(64),
    message TEXT NOT NULL,
    created_ms BIGINT NOT NULL,
    updated_ms BIGINT NOT NULL,
    PRIMARY KEY (plan_id, repository_ref, result_revision)
);

CREATE INDEX plan_git_delivery_ready ON plan_git_delivery(state,created_ms);

CREATE TABLE factory_worker_registration (
    worker_id VARCHAR(128) PRIMARY KEY,
    compatibility_key VARCHAR(64) NOT NULL,
    retired_at_epoch_ms BIGINT
);

CREATE TABLE worker_registration_retirement (
    worker_id VARCHAR(128) PRIMARY KEY REFERENCES worker(id),
    actor VARCHAR(256) NOT NULL,
    reason TEXT NOT NULL,
    retired_at_epoch_ms BIGINT NOT NULL
);

CREATE TABLE factory_plan_review (
    plan_id VARCHAR(128) PRIMARY KEY REFERENCES workflow_factory_plan(plan_id) ON DELETE CASCADE,
    source_task_id VARCHAR(128) REFERENCES work_task(id) ON DELETE SET NULL,
    review_task_id VARCHAR(128) REFERENCES work_task(id) ON DELETE SET NULL,
    state VARCHAR(32) NOT NULL,
    updated_at_epoch_ms BIGINT NOT NULL
);

CREATE UNIQUE INDEX idx_factory_plan_review_task ON factory_plan_review(review_task_id);

CREATE TABLE agent_pairing_invitation (
 id VARCHAR(36) PRIMARY KEY, owner_id VARCHAR(64) NOT NULL, code_hash VARCHAR(64) NOT NULL UNIQUE,
 profile VARCHAR(16) NOT NULL, server_origin VARCHAR(512) NOT NULL,
 state VARCHAR(16) NOT NULL, created_by VARCHAR(256) NOT NULL, created_ms BIGINT NOT NULL,
 expires_ms BIGINT NOT NULL, consumed_ms BIGINT, agent_id VARCHAR(128)
);

CREATE INDEX agent_pairing_expiry ON agent_pairing_invitation(state,expires_ms);

CREATE TABLE agent_certificate_rotation (
 id VARCHAR(36) PRIMARY KEY, agent_id VARCHAR(128) NOT NULL,
 client_cert_sha256 VARCHAR(64) NOT NULL, state VARCHAR(16) NOT NULL,
 requested_ms BIGINT NOT NULL, decided_ms BIGINT, decided_by VARCHAR(256),
 FOREIGN KEY(agent_id) REFERENCES authority_identity(worker_id)
);

CREATE INDEX agent_certificate_rotation_pending ON agent_certificate_rotation(agent_id,state,requested_ms);

CREATE TABLE agent_message_outbox (
 id VARCHAR(36) PRIMARY KEY, agent_id VARCHAR(128) NOT NULL, event_type VARCHAR(64) NOT NULL,
 resource_id VARCHAR(256) NOT NULL, resource_version BIGINT NOT NULL,
 dedupe_key VARCHAR(64) NOT NULL, state VARCHAR(24) NOT NULL,
 attempts INTEGER NOT NULL, created_ms BIGINT NOT NULL, available_ms BIGINT NOT NULL,
 expires_ms BIGINT NOT NULL, lease_until_ms BIGINT, lease_hash VARCHAR(64), acked_ms BIGINT,
 last_error VARCHAR(256), UNIQUE(agent_id,dedupe_key)
);

CREATE INDEX agent_message_ready ON agent_message_outbox(agent_id,state,available_ms,created_ms);

CREATE INDEX agent_message_expiry ON agent_message_outbox(state,expires_ms);

CREATE TABLE plan_execution_location (
    scope_id VARCHAR(512) PRIMARY KEY,
    location VARCHAR(4096) NOT NULL,
    temporary INTEGER NOT NULL,
    created_at_epoch_ms BIGINT NOT NULL,
    cleaned_at_epoch_ms BIGINT,
    CHECK (temporary IN (0,1))
);

CREATE TABLE execution_handoff (
    attempt_id VARCHAR(128) PRIMARY KEY REFERENCES execution_attempt(id) ON DELETE CASCADE,
    task_id VARCHAR(128) NOT NULL REFERENCES work_task(id) ON DELETE CASCADE,
    location VARCHAR(4096) NOT NULL,
    manifest_path VARCHAR(1024) NOT NULL,
    sha256 VARCHAR(64) NOT NULL
);

CREATE TABLE execution_run_session (
    attempt_id VARCHAR(128) PRIMARY KEY REFERENCES execution_attempt(id),
    task_id VARCHAR(128) NOT NULL REFERENCES work_task(id),
    owner_id VARCHAR(128) NOT NULL REFERENCES authority_owner(id),
    instruction_revision INTEGER NOT NULL, capabilities_json TEXT NOT NULL,
    open_digest VARCHAR(64) NOT NULL, phase VARCHAR(32) NOT NULL,
    revision BIGINT NOT NULL, ack_sequence BIGINT NOT NULL DEFAULT 0,
    event_bytes BIGINT NOT NULL DEFAULT 0, last_contact_ms BIGINT NOT NULL,
    configuration_capability VARCHAR(96));

CREATE TABLE execution_run_event (
    attempt_id VARCHAR(128) NOT NULL REFERENCES execution_run_session(attempt_id),
    sequence_no BIGINT NOT NULL, event_type VARCHAR(48) NOT NULL,
    occurred_ms BIGINT NOT NULL, payload_json TEXT NOT NULL, digest VARCHAR(64) NOT NULL,
    PRIMARY KEY(attempt_id,sequence_no)
);

CREATE TABLE execution_run_command (
    owner_id VARCHAR(128) NOT NULL REFERENCES authority_owner(id), request_id VARCHAR(80) NOT NULL,
    task_id VARCHAR(128) NOT NULL REFERENCES work_task(id),
    attempt_id VARCHAR(128) NOT NULL REFERENCES execution_run_session(attempt_id),
    sequence_no BIGINT NOT NULL, action VARCHAR(16) NOT NULL, command_digest VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL, actor VARCHAR(256) NOT NULL,
    created_ms BIGINT NOT NULL, updated_ms BIGINT NOT NULL,
    PRIMARY KEY(owner_id,request_id), UNIQUE(attempt_id,sequence_no)
);

CREATE INDEX execution_run_owner ON execution_run_session(owner_id,last_contact_ms);

CREATE TABLE plan_run_owner (
    plan_id VARCHAR(128) PRIMARY KEY REFERENCES flexible_workflow_plan(id),
    owner_id VARCHAR(128) NOT NULL REFERENCES authority_owner(id),
    request_id VARCHAR(80) NOT NULL, request_digest VARCHAR(64) NOT NULL,
    source_digest VARCHAR(64) NOT NULL, source_version BIGINT NOT NULL,
    actor VARCHAR(256) NOT NULL, created_ms BIGINT NOT NULL,
    UNIQUE(owner_id,request_id)
);

CREATE TABLE model_connection (
 id VARCHAR(80) PRIMARY KEY, owner_actor VARCHAR(256) NOT NULL, kind VARCHAR(32) NOT NULL,
 label VARCHAR(160) NOT NULL, client_id VARCHAR(256) NOT NULL, subject_id VARCHAR(256) NOT NULL,
 account_id VARCHAR(256), plan_type VARCHAR(80), state VARCHAR(40) NOT NULL,
 version BIGINT NOT NULL, generation BIGINT NOT NULL, expires_ms BIGINT NOT NULL,
 earliest_refresh_ms BIGINT NOT NULL, credential_file TEXT, refresh_started_ms BIGINT,
 updated_ms BIGINT NOT NULL, UNIQUE(owner_actor,kind,client_id,subject_id)
);

CREATE TABLE model_auth_host (id INTEGER PRIMARY KEY, host_id VARCHAR(80) NOT NULL);

CREATE TABLE model_token_seen (token_digest VARCHAR(64) PRIMARY KEY, connection_id VARCHAR(80) NOT NULL);

CREATE TABLE model_auth_task (
 task_id VARCHAR(128) PRIMARY KEY, connection_id VARCHAR(80) NOT NULL,
 connection_version BIGINT NOT NULL, owner_actor VARCHAR(256) NOT NULL,
 runtime VARCHAR(80) NOT NULL, model VARCHAR(128) NOT NULL
);

CREATE TABLE model_auth_worker (
 worker_id VARCHAR(128) PRIMARY KEY, connection_id VARCHAR(80) NOT NULL,
 connection_version BIGINT NOT NULL, runtime VARCHAR(80) NOT NULL, model VARCHAR(128) NOT NULL,
    proof_digest VARCHAR(64),
    proof_cipher TEXT);

CREATE TABLE model_auth_plan (
 subject_type VARCHAR(80) NOT NULL, subject_id VARCHAR(128) NOT NULL,
 connection_id VARCHAR(80) NOT NULL, connection_version BIGINT NOT NULL,
 PRIMARY KEY(subject_type,subject_id)
);

CREATE TABLE model_vault_local (
 id INTEGER PRIMARY KEY, key_id VARCHAR(36) NOT NULL, key_digest VARCHAR(64)
);

CREATE TABLE execution_tool_approval (
 id VARCHAR(128) PRIMARY KEY, task_id VARCHAR(128) NOT NULL REFERENCES execution_context(task_id),
 plan_id VARCHAR(128) NOT NULL REFERENCES execution_policy(plan_id), owner_id VARCHAR(128) NOT NULL,
 attempt_id VARCHAR(128) NOT NULL REFERENCES execution_attempt(id), operation_id VARCHAR(128) NOT NULL UNIQUE,
 binding_json TEXT NOT NULL, binding_digest VARCHAR(64) NOT NULL, request_digest VARCHAR(64) NOT NULL,
 policy_digest VARCHAR(64) NOT NULL, generation BIGINT NOT NULL,
 state VARCHAR(24) NOT NULL, revision BIGINT NOT NULL DEFAULT 1, expires_ms BIGINT NOT NULL,
 decision_json TEXT, decided_by VARCHAR(256), created_ms BIGINT NOT NULL, decided_ms BIGINT,
 CHECK(state IN ('PENDING','APPROVED','REJECTED','EXPIRED','INVALIDATED','CONSUMED')),
    consumption_json TEXT,
    start_digest VARCHAR(64),
    consumed_task_id VARCHAR(128) REFERENCES execution_context(task_id),
    consumed_ms BIGINT,
    successor_task_id VARCHAR(128) REFERENCES work_task(id));

CREATE INDEX idx_tool_approval_plan ON execution_tool_approval(plan_id,created_ms);

CREATE UNIQUE INDEX idx_tool_approval_successor ON execution_tool_approval(successor_task_id) WHERE successor_task_id IS NOT NULL;

CREATE TABLE model_usage_request (
    attempt_id VARCHAR(128) NOT NULL REFERENCES execution_attempt(id),
    request_id VARCHAR(128) NOT NULL,
    task_id VARCHAR(128) NOT NULL REFERENCES work_task(id),
    worker_id VARCHAR(128) NOT NULL,
    provider VARCHAR(64) NOT NULL,
    model VARCHAR(200) NOT NULL,
    protocol VARCHAR(64) NOT NULL,
    purpose VARCHAR(32) NOT NULL,
    connection_kind VARCHAR(32) NOT NULL,
    price_scope VARCHAR(32) NOT NULL,
    service_tier VARCHAR(64) NOT NULL,
    started_ms BIGINT NOT NULL,
    finished_ms BIGINT,
    state VARCHAR(16) NOT NULL,
    input_tokens BIGINT,
    cached_tokens BIGINT,
    output_tokens BIGINT,
    cache_write_tokens BIGINT,
    request_digest VARCHAR(64) NOT NULL,
    terminal_digest VARCHAR(64),
    PRIMARY KEY (attempt_id, request_id),
    CHECK (state IN ('STARTED', 'COMPLETED', 'UNKNOWN')),
    CHECK (input_tokens IS NULL OR input_tokens >= 0),
    CHECK (cached_tokens IS NULL OR cached_tokens >= 0),
    CHECK (output_tokens IS NULL OR output_tokens >= 0),
    CHECK (cache_write_tokens IS NULL OR cache_write_tokens >= 0)
);

CREATE INDEX idx_model_usage_task_time ON model_usage_request(task_id, started_ms);

CREATE INDEX idx_model_usage_price_match ON model_usage_request(provider, model, started_ms);

CREATE TABLE model_price_snapshot (
    snapshot_id VARCHAR(128) PRIMARY KEY,
    provider VARCHAR(64) NOT NULL,
    source_url VARCHAR(2048) NOT NULL,
    content_sha256 VARCHAR(64) NOT NULL,
    snapshot_digest VARCHAR(64) NOT NULL,
    parser_version VARCHAR(128) NOT NULL,
    observed_ms BIGINT NOT NULL,
    published_ms BIGINT NOT NULL
);

CREATE TABLE model_price_head (
    provider VARCHAR(64) PRIMARY KEY,
    snapshot_id VARCHAR(128) NOT NULL REFERENCES model_price_snapshot(snapshot_id)
);

CREATE TABLE model_price_rule (
    snapshot_id VARCHAR(128) NOT NULL REFERENCES model_price_snapshot(snapshot_id),
    rule_id VARCHAR(128) NOT NULL,
    provider VARCHAR(64) NOT NULL,
    model VARCHAR(200) NOT NULL,
    currency VARCHAR(16) NOT NULL,
    service_tier VARCHAR(64) NOT NULL,
    valid_from_ms BIGINT NOT NULL,
    valid_until_ms BIGINT,
    zone_id VARCHAR(128) NOT NULL,
    days_json VARCHAR(128) NOT NULL,
    start_time VARCHAR(32),
    end_time VARCHAR(32),
    only_dates_json TEXT NOT NULL,
    excluded_dates_json TEXT NOT NULL,
    calendar_from VARCHAR(10),
    calendar_until VARCHAR(10),
    input_per_million BIGINT NOT NULL,
    cached_per_million BIGINT NOT NULL,
    output_per_million BIGINT NOT NULL,
    cache_write_included BOOLEAN NOT NULL,
    cache_write_per_million BIGINT,
    min_context BIGINT,
    max_context BIGINT,
    PRIMARY KEY (snapshot_id, rule_id),
    CHECK (valid_until_ms IS NULL OR valid_until_ms > valid_from_ms),
    CHECK (input_per_million >= 0 AND cached_per_million >= 0 AND output_per_million >= 0),
    CHECK (cache_write_per_million IS NULL OR cache_write_per_million >= 0)
);

CREATE INDEX idx_model_price_match ON model_price_rule(provider, model, valid_from_ms, valid_until_ms);

CREATE TABLE model_usage_estimate (
    attempt_id VARCHAR(128) NOT NULL,
    request_id VARCHAR(128) NOT NULL,
    estimate_revision BIGINT NOT NULL,
    snapshot_id VARCHAR(128) NOT NULL,
    rule_id VARCHAR(128) NOT NULL,
    currency VARCHAR(16) NOT NULL,
    cost_micros BIGINT NOT NULL,
    calculated_ms BIGINT NOT NULL,
    PRIMARY KEY (attempt_id, request_id, estimate_revision),
    FOREIGN KEY (attempt_id, request_id) REFERENCES model_usage_request(attempt_id, request_id),
    FOREIGN KEY (snapshot_id, rule_id) REFERENCES model_price_rule(snapshot_id, rule_id),
    CHECK (estimate_revision > 0 AND cost_micros >= 0)
);

CREATE TABLE model_price_collection_state (
    provider VARCHAR(64) PRIMARY KEY,
    lease_token VARCHAR(64),
    lease_until_ms BIGINT,
    last_attempt_ms BIGINT,
    last_success_ms BIGINT,
    status VARCHAR(32) NOT NULL,
    failure_code VARCHAR(64),
    source_url VARCHAR(2048),
    content_sha256 VARCHAR(64),
    collector_version VARCHAR(128),
    observation_json TEXT,
    CHECK (status IN ('PENDING','RUNNING','OBSERVED_INCOMPLETE','FAILED')),
    collector_sha256 VARCHAR(64),
    last_success_sample_base64 TEXT);

CREATE TABLE model_price_collection_failure (
    provider VARCHAR(64) PRIMARY KEY REFERENCES model_price_collection_state(provider),
    fingerprint VARCHAR(64) NOT NULL,
    collector_version VARCHAR(128) NOT NULL,
    reason VARCHAR(80) NOT NULL,
    source_url VARCHAR(2048) NOT NULL,
    content_sha256 VARCHAR(64) NOT NULL,
    sample_base64 TEXT NOT NULL,
    last_seen_ms BIGINT NOT NULL,
    failure_count INTEGER NOT NULL,
    active BOOLEAN NOT NULL,
    CHECK (failure_count >= 0),
    collector_sha256 VARCHAR(64));

CREATE TABLE model_price_repair_material (
    id VARCHAR(128) PRIMARY KEY,
    provider VARCHAR(64) NOT NULL,
    failure_fingerprint VARCHAR(64) NOT NULL,
    collector_sha256 VARCHAR(64) NOT NULL,
    collector_version VARCHAR(128) NOT NULL,
    source_url VARCHAR(2048) NOT NULL,
    reason VARCHAR(80) NOT NULL,
    original_module_base64 TEXT NOT NULL,
    changed_page_sha256 VARCHAR(64) NOT NULL,
    changed_page_base64 TEXT NOT NULL,
    created_ms BIGINT NOT NULL,
    status VARCHAR(24) NOT NULL,
    CHECK (status IN ('PREPARED','CONSUMED','SUPERSEDED'))
);

CREATE UNIQUE INDEX idx_price_repair_one_prepared ON model_price_repair_material(provider) WHERE status='PREPARED';

CREATE TABLE model_price_repair_result (
    attempt_id VARCHAR(128) PRIMARY KEY REFERENCES execution_attempt(id),
    task_id VARCHAR(128) NOT NULL REFERENCES work_task(id),
    material_id VARCHAR(128) NOT NULL REFERENCES model_price_repair_material(id),
    worker_id VARCHAR(128) NOT NULL,
    module_sha256 VARCHAR(64) NOT NULL,
    module_base64 TEXT NOT NULL,
    checks_sha256 VARCHAR(64) NOT NULL,
    checks_base64 TEXT NOT NULL,
    summary TEXT NOT NULL,
    payload_digest VARCHAR(64) NOT NULL,
    received_ms BIGINT NOT NULL,
    source_url VARCHAR(2048));

CREATE TABLE model_price_repair_activation (
    material_id VARCHAR(128) PRIMARY KEY REFERENCES model_price_repair_material(id),
    provider VARCHAR(64) NOT NULL,
    state VARCHAR(24) NOT NULL,
    lease_token VARCHAR(128),
    lease_until_ms BIGINT,
    candidate_sha256 VARCHAR(64),
    verified_page_sha256 VARCHAR(64),
    failure_code VARCHAR(80),
    updated_ms BIGINT NOT NULL,
    CHECK (state IN ('VERIFYING','INSTALLING','INSTALLED','FAILED')),
    decision_by VARCHAR(64),
    decision_ms BIGINT,
    reviewed_candidate_sha256 VARCHAR(64),
    reviewed_page_sha256 VARCHAR(64),
    approved_by VARCHAR(64),
    approved_ms BIGINT,
    approved_candidate_sha256 VARCHAR(64),
    approved_page_sha256 VARCHAR(64),
    approved_page_base64 TEXT,
    approved_source_url VARCHAR(2048),
    approved_page_observed_ms BIGINT,
    execution_started_ms BIGINT,
    collection_lease_token VARCHAR(128));

CREATE TABLE workflow_definition (
    id VARCHAR(80) PRIMARY KEY,
    actor VARCHAR(128) NOT NULL,
    version BIGINT NOT NULL,
    spec_json TEXT NOT NULL,
    updated_ms BIGINT NOT NULL
);

CREATE TABLE workflow_execution (
    id VARCHAR(80) PRIMARY KEY,
    definition_id VARCHAR(80),
    actor VARCHAR(128) NOT NULL,
    version BIGINT NOT NULL,
    state VARCHAR(32) NOT NULL,
    run_json TEXT NOT NULL,
    updated_ms BIGINT NOT NULL,
    request_id VARCHAR(80) NOT NULL,
    request_digest VARCHAR(64) NOT NULL,
    UNIQUE(actor, request_id)
);

CREATE INDEX workflow_definition_actor ON workflow_definition(actor, updated_ms);

CREATE INDEX workflow_execution_actor ON workflow_execution(actor, updated_ms);

CREATE TABLE worker_team_member (
 id VARCHAR(80) PRIMARY KEY, actor VARCHAR(128) NOT NULL,
 name VARCHAR(80) NOT NULL, purpose TEXT NOT NULL, skills_json TEXT NOT NULL, notes TEXT NOT NULL,
 revision BIGINT NOT NULL, state VARCHAR(24) NOT NULL,
 created_ms BIGINT NOT NULL, updated_ms BIGINT NOT NULL,
 CHECK(state IN ('ACTIVE','DRAINING','ARCHIVED')),
    settings_json TEXT NOT NULL DEFAULT '{}');

CREATE INDEX worker_team_actor ON worker_team_member(actor,state,updated_ms);

CREATE TABLE worker_team_assignment (
 run_id VARCHAR(80) NOT NULL REFERENCES workflow_execution(id), node_key VARCHAR(32) NOT NULL,
 member_id VARCHAR(80) NOT NULL REFERENCES worker_team_member(id), profile_json TEXT NOT NULL,
 PRIMARY KEY(run_id,node_key)
);

CREATE INDEX worker_team_assigned_member ON worker_team_assignment(member_id,run_id);

CREATE TABLE worker_team_task (
 task_id VARCHAR(128) PRIMARY KEY REFERENCES work_task(id),
 run_id VARCHAR(80) NOT NULL, node_key VARCHAR(32) NOT NULL,
 FOREIGN KEY(run_id,node_key) REFERENCES worker_team_assignment(run_id,node_key)
);

CREATE INDEX worker_team_task_assignment ON worker_team_task(run_id,node_key);

CREATE TABLE worker_access_credential (
 id VARCHAR(36) PRIMARY KEY,
 worker_id VARCHAR(128) NOT NULL,
 label VARCHAR(120) NOT NULL,
 token_hash VARCHAR(64) NOT NULL,
 state VARCHAR(16) NOT NULL CHECK(state IN ('ACTIVE','DRAINING','REVOKED')),
 revision BIGINT NOT NULL,
 created_by VARCHAR(128) NOT NULL,
 created_ms BIGINT NOT NULL,
 last_used_ms BIGINT
);

CREATE INDEX worker_access_identity ON worker_access_credential(worker_id,state);

CREATE TABLE workflow_schedule (
 id VARCHAR(36) PRIMARY KEY,
 actor VARCHAR(128) NOT NULL,
 owner_id VARCHAR(64) NOT NULL REFERENCES authority_owner(id),
 request_digest VARCHAR(64) NOT NULL,
 spec_json TEXT NOT NULL,
 repeat_kind VARCHAR(16) NOT NULL,
 zone_id VARCHAR(80) NOT NULL,
 local_time VARCHAR(8) NOT NULL,
 weekday INTEGER NOT NULL,
 next_ms BIGINT NOT NULL,
 state VARCHAR(32) NOT NULL,
 last_execution VARCHAR(128),
 failure_checked BOOLEAN NOT NULL DEFAULT FALSE,
 message VARCHAR(1000) NOT NULL,
 version BIGINT NOT NULL,
 updated_ms BIGINT NOT NULL
);

CREATE INDEX workflow_schedule_due ON workflow_schedule(state,next_ms);

CREATE TABLE worker_team_experience (
    task_id VARCHAR(128) PRIMARY KEY REFERENCES work_task(id),
    actor VARCHAR(128) NOT NULL,
    member_id VARCHAR(80) NOT NULL REFERENCES worker_team_member(id),
    run_id VARCHAR(80) NOT NULL,
    experience TEXT NOT NULL,
    created_ms BIGINT NOT NULL
);

CREATE INDEX worker_team_experience_owner ON worker_team_experience(actor, created_ms DESC, task_id);

CREATE TABLE worker_team_skill_revision (
    member_id VARCHAR(80) NOT NULL REFERENCES worker_team_member(id),
    skill_ref VARCHAR(96) NOT NULL,
    version INTEGER NOT NULL CHECK(version >= 0),
    content TEXT NOT NULL,
    content_hash VARCHAR(64) NOT NULL,
    reason VARCHAR(1000) NOT NULL,
    source_task_id VARCHAR(128) REFERENCES work_task(id),
    source_run_id VARCHAR(80),
    created_ms BIGINT NOT NULL,
    PRIMARY KEY(member_id,skill_ref,version),
    UNIQUE(source_task_id,skill_ref)
);
