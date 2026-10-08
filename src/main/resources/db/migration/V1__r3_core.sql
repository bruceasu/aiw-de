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
