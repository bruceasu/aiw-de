-- Explicit observation/control ownership, never an execution-policy or permission grant.
CREATE TABLE plan_run_owner (
    plan_id VARCHAR(128) PRIMARY KEY REFERENCES flexible_workflow_plan(id),
    owner_id VARCHAR(128) NOT NULL REFERENCES authority_owner(id),
    request_id VARCHAR(80) NOT NULL, request_digest VARCHAR(64) NOT NULL,
    source_digest VARCHAR(64) NOT NULL, source_version BIGINT NOT NULL,
    actor VARCHAR(256) NOT NULL, created_ms BIGINT NOT NULL,
    UNIQUE(owner_id,request_id)
);
ALTER TABLE execution_run_session ADD COLUMN configuration_capability VARCHAR(96);
