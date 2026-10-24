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
