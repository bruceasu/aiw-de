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
