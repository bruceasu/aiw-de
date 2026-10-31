-- Preserve legacy identity and authorization rows as audit history. New identities are explicitly typed.
ALTER TABLE authority_identity ADD COLUMN identity_kind VARCHAR(32) NOT NULL DEFAULT 'LEGACY_PERSONAL';
ALTER TABLE authority_identity ADD COLUMN connection_profile VARCHAR(16) NOT NULL DEFAULT 'LOCAL';
ALTER TABLE authority_identity ADD COLUMN client_cert_sha256 VARCHAR(64);
ALTER TABLE authority_action_permission ADD COLUMN revision BIGINT NOT NULL DEFAULT 1;

CREATE TABLE agent_pairing_invitation (
 id VARCHAR(36) PRIMARY KEY, owner_id VARCHAR(64) NOT NULL, code_hash VARCHAR(64) NOT NULL UNIQUE,
 profile VARCHAR(16) NOT NULL, server_origin VARCHAR(512) NOT NULL,
 state VARCHAR(16) NOT NULL, created_by VARCHAR(256) NOT NULL, created_ms BIGINT NOT NULL,
 expires_ms BIGINT NOT NULL, consumed_ms BIGINT, agent_id VARCHAR(128)
);
CREATE INDEX agent_pairing_expiry ON agent_pairing_invitation(state,expires_ms);

CREATE TABLE agent_certificate_rotation (
 id VARCHAR(36) PRIMARY KEY, agent_id VARCHAR(128) NOT NULL,
 client_cert_sha256 VARCHAR(64) NOT NULL, state VARCHAR(16) NOT NULL,
 requested_ms BIGINT NOT NULL, decided_ms BIGINT, decided_by VARCHAR(256),
 FOREIGN KEY(agent_id) REFERENCES authority_identity(worker_id)
);
CREATE INDEX agent_certificate_rotation_pending ON agent_certificate_rotation(agent_id,state,requested_ms);

CREATE TABLE agent_message_outbox (
 id VARCHAR(36) PRIMARY KEY, agent_id VARCHAR(128) NOT NULL, event_type VARCHAR(64) NOT NULL,
 resource_id VARCHAR(256) NOT NULL, resource_version BIGINT NOT NULL,
 dedupe_key VARCHAR(64) NOT NULL, state VARCHAR(24) NOT NULL,
 attempts INTEGER NOT NULL, created_ms BIGINT NOT NULL, available_ms BIGINT NOT NULL,
 expires_ms BIGINT NOT NULL, lease_until_ms BIGINT, lease_hash VARCHAR(64), acked_ms BIGINT,
 last_error VARCHAR(256), UNIQUE(agent_id,dedupe_key)
);
CREATE INDEX agent_message_ready ON agent_message_outbox(agent_id,state,available_ms,created_ms);
CREATE INDEX agent_message_expiry ON agent_message_outbox(state,expires_ms);
