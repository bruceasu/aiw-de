-- Human authority and consumption remain separate from Worker liveness and V4 grants.
CREATE TABLE delegation_lock (id INTEGER PRIMARY KEY CHECK (id=1), version BIGINT NOT NULL);
INSERT INTO delegation_lock VALUES (1,0);
CREATE TABLE delegation_security (id INTEGER PRIMARY KEY CHECK(id=1), legacy_auth_required BOOLEAN NOT NULL);
INSERT INTO delegation_security VALUES(1,FALSE);
CREATE TABLE delegation_legacy_request (
    id VARCHAR(128) PRIMARY KEY,
    server_boot VARCHAR(64) NOT NULL,
    method VARCHAR(16) NOT NULL,
    path VARCHAR(1024) NOT NULL,
    state VARCHAR(16) NOT NULL,
    started_ms BIGINT NOT NULL,
    finished_ms BIGINT
);
CREATE TABLE delegation_identity (
    worker_id VARCHAR(128) PRIMARY KEY,
    key_hash VARCHAR(64) NOT NULL,
    generation BIGINT NOT NULL,
    enabled_boot VARCHAR(64),
    changed_by VARCHAR(256) NOT NULL,
    changed_ms BIGINT NOT NULL
);
CREATE TABLE delegation_target (
    target_key VARCHAR(1024) PRIMARY KEY,
    generation BIGINT NOT NULL,
    admitted_ms BIGINT NOT NULL
);
CREATE TABLE delegation_grant (
    id VARCHAR(128) PRIMARY KEY,
    owner VARCHAR(256) NOT NULL,
    worker_id VARCHAR(128) NOT NULL,
    revision BIGINT NOT NULL,
    state VARCHAR(16) NOT NULL,
    original_text TEXT NOT NULL,
    rule_json TEXT NOT NULL,
    authority VARCHAR(64) NOT NULL,
    template_id VARCHAR(128),
    template_revision BIGINT,
    effective_ms BIGINT NOT NULL,
    expires_ms BIGINT NOT NULL
);
-- Publication admission belongs to a fixed task attempt, never an automatic round.
CREATE TABLE delegation_publication_plan (
    plan_id VARCHAR(128) PRIMARY KEY,
    target_key VARCHAR(1024) NOT NULL
);
CREATE TABLE delegation_publication_attempt (
    task_id VARCHAR(128) PRIMARY KEY,
    allowed_attempt BIGINT NOT NULL,
    grant_id VARCHAR(128),
    grant_revision BIGINT,
    worker_generation BIGINT,
    enabled_boot VARCHAR(64)
);
CREATE TABLE delegation_history (
    grant_id VARCHAR(128) NOT NULL,
    revision BIGINT NOT NULL,
    snapshot TEXT NOT NULL,
    changed_by VARCHAR(256) NOT NULL,
    changed_ms BIGINT NOT NULL,
    PRIMARY KEY(grant_id,revision)
);
CREATE TABLE delegation_budget (
    target_key VARCHAR(1024) NOT NULL,
    action VARCHAR(32) NOT NULL,
    limit_value BIGINT NOT NULL CHECK (limit_value>=0),
    used_value BIGINT NOT NULL CHECK (used_value>=0),
    reserved_value BIGINT NOT NULL CHECK (reserved_value>=0),
    PRIMARY KEY(target_key,action)
);
CREATE TABLE delegation_request (
    id VARCHAR(128) PRIMARY KEY,
    grant_id VARCHAR(128) NOT NULL,
    grant_revision BIGINT NOT NULL,
    worker_id VARCHAR(128) NOT NULL,
    worker_generation BIGINT NOT NULL,
    target_key VARCHAR(1024) NOT NULL,
    target_generation BIGINT NOT NULL,
    snapshot TEXT NOT NULL,
    fingerprint VARCHAR(64) NOT NULL UNIQUE,
    task_id VARCHAR(128) NOT NULL UNIQUE,
    state VARCHAR(16) NOT NULL,
    detail TEXT NOT NULL,
    created_ms BIGINT NOT NULL,
    updated_ms BIGINT NOT NULL
);
CREATE TABLE delegation_audit (
    id VARCHAR(128) PRIMARY KEY,
    target_key VARCHAR(1024),
    grant_id VARCHAR(128),
    request_id VARCHAR(128),
    actor VARCHAR(256) NOT NULL,
    event VARCHAR(64) NOT NULL,
    detail TEXT NOT NULL,
    created_ms BIGINT NOT NULL
);
CREATE TABLE delegation_template (
    id VARCHAR(128) PRIMARY KEY,
    owner VARCHAR(256) NOT NULL,
    revision BIGINT NOT NULL,
    original_text TEXT NOT NULL,
    rule_json TEXT NOT NULL,
    source_grant VARCHAR(128),
    source_revision BIGINT,
    created_ms BIGINT NOT NULL
);
CREATE TABLE delegation_draft (
    id VARCHAR(128) PRIMARY KEY,
    owner VARCHAR(256) NOT NULL,
    original_text TEXT NOT NULL,
    created_ms BIGINT NOT NULL
);
CREATE TABLE delegation_template_history (
    template_id VARCHAR(128) NOT NULL,
    revision BIGINT NOT NULL,
    owner VARCHAR(256) NOT NULL,
    snapshot TEXT NOT NULL,
    created_ms BIGINT NOT NULL,
    PRIMARY KEY(template_id,revision)
);
CREATE TABLE delegation_model_constraint (
    task_id VARCHAR(128) PRIMARY KEY,
    grant_id VARCHAR(128) NOT NULL,
    grant_revision BIGINT NOT NULL,
    catalog_version VARCHAR(64) NOT NULL,
    profiles_json TEXT NOT NULL
);
CREATE INDEX idx_delegation_grants_worker ON delegation_grant(worker_id,state);
CREATE TABLE delegation_task (
    task_id VARCHAR(128) PRIMARY KEY,
    target_key VARCHAR(1024) NOT NULL,
    allowed_attempt BIGINT NOT NULL,
    reserved BOOLEAN NOT NULL,
    started BOOLEAN NOT NULL,
    settled BOOLEAN NOT NULL
);
CREATE TABLE delegation_model_scope (
    target_key VARCHAR(1024) NOT NULL,
    role VARCHAR(16) NOT NULL,
    catalog_version VARCHAR(64) NOT NULL,
    profiles_json TEXT NOT NULL,
    grant_id VARCHAR(128) NOT NULL,
    grant_revision BIGINT NOT NULL,
    PRIMARY KEY(target_key,role)
);
CREATE INDEX idx_delegation_requests_state ON delegation_request(state,created_ms);
CREATE INDEX idx_delegation_audit_target ON delegation_audit(target_key,created_ms);
