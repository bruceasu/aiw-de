CREATE TABLE worker_access_credential (
 id VARCHAR(36) PRIMARY KEY,
 worker_id VARCHAR(128) NOT NULL,
 label VARCHAR(120) NOT NULL,
 token_hash VARCHAR(64) NOT NULL,
 state VARCHAR(16) NOT NULL CHECK(state IN ('ACTIVE','DRAINING','REVOKED')),
 revision BIGINT NOT NULL,
 created_by VARCHAR(128) NOT NULL,
 created_ms BIGINT NOT NULL,
 last_used_ms BIGINT
);
CREATE INDEX worker_access_identity ON worker_access_credential(worker_id,state);
