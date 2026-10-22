-- Platform facts only. All quantities are integers; NULL limits mean waiting for explicit configuration.
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
