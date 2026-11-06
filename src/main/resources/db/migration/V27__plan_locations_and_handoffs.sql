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
