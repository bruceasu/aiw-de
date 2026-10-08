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
