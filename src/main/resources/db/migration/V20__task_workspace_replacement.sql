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
