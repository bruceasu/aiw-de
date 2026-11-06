-- Additive observations and command receipts. Task/Lease/operation authority stays unchanged.
CREATE TABLE execution_run_session (
    attempt_id VARCHAR(128) PRIMARY KEY REFERENCES execution_attempt(id),
    task_id VARCHAR(128) NOT NULL REFERENCES work_task(id),
    owner_id VARCHAR(128) NOT NULL REFERENCES authority_owner(id),
    instruction_revision INTEGER NOT NULL, capabilities_json TEXT NOT NULL,
    open_digest VARCHAR(64) NOT NULL, phase VARCHAR(32) NOT NULL,
    revision BIGINT NOT NULL, ack_sequence BIGINT NOT NULL DEFAULT 0,
    event_bytes BIGINT NOT NULL DEFAULT 0, last_contact_ms BIGINT NOT NULL
);
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
