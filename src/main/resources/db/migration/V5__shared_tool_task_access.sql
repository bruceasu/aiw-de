CREATE TABLE shared_tool_task_grant (
    task_id VARCHAR(128) NOT NULL REFERENCES work_task(id),
    tool_id VARCHAR(64) NOT NULL REFERENCES shared_tool(id),
    tool_revision BIGINT NOT NULL,
    revoked BOOLEAN NOT NULL,
    PRIMARY KEY (task_id, tool_id)
);

CREATE TABLE shared_tool_operation (
    id VARCHAR(36) PRIMARY KEY,
    task_id VARCHAR(128) NOT NULL REFERENCES work_task(id),
    attempt_id VARCHAR(128) NOT NULL REFERENCES execution_attempt(id),
    worker_id VARCHAR(128) NOT NULL,
    tool_id VARCHAR(64) NOT NULL,
    tool_revision BIGINT NOT NULL,
    request_digest VARCHAR(64) NOT NULL,
    result_digest VARCHAR(64),
    state VARCHAR(16) NOT NULL CHECK (state IN ('STARTED', 'COMPLETED', 'REJECTED', 'UNKNOWN', 'RECONCILED')),
    created_ms BIGINT NOT NULL,
    finished_ms BIGINT
);
CREATE INDEX shared_tool_operation_task ON shared_tool_operation(task_id,state);
