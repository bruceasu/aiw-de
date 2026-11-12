CREATE TABLE workflow_definition (
    id VARCHAR(80) PRIMARY KEY,
    actor VARCHAR(128) NOT NULL,
    version BIGINT NOT NULL,
    spec_json TEXT NOT NULL,
    updated_ms BIGINT NOT NULL
);
CREATE TABLE workflow_execution (
    id VARCHAR(80) PRIMARY KEY,
    definition_id VARCHAR(80),
    actor VARCHAR(128) NOT NULL,
    version BIGINT NOT NULL,
    state VARCHAR(32) NOT NULL,
    run_json TEXT NOT NULL,
    updated_ms BIGINT NOT NULL,
    request_id VARCHAR(80) NOT NULL,
    request_digest VARCHAR(64) NOT NULL,
    UNIQUE(actor, request_id)
);
CREATE INDEX workflow_definition_actor ON workflow_definition(actor, updated_ms);
CREATE INDEX workflow_execution_actor ON workflow_execution(actor, updated_ms);
