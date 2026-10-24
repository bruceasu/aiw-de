-- Human action permissions are distinct from record-only execution accounting.
CREATE TABLE authority_action_permission (
 id VARCHAR(128) PRIMARY KEY, owner_id VARCHAR(64) NOT NULL, worker_id VARCHAR(128) NOT NULL,
 action VARCHAR(32) NOT NULL, scope_kind VARCHAR(16) NOT NULL, scope_id VARCHAR(128),
 maximum_uses BIGINT NOT NULL CHECK(maximum_uses>0), used_uses BIGINT NOT NULL DEFAULT 0 CHECK(used_uses>=0),
 state VARCHAR(16) NOT NULL, authority VARCHAR(64) NOT NULL, effective_ms BIGINT NOT NULL, expires_ms BIGINT NOT NULL,
 creation_request VARCHAR(36) NOT NULL, creation_digest VARCHAR(64) NOT NULL,
 UNIQUE(owner_id,creation_request)
);
CREATE UNIQUE INDEX authority_permission_current ON authority_action_permission(owner_id,worker_id,action) WHERE state='ENABLED';
CREATE TABLE authority_permission_child (
 grant_id VARCHAR(128) PRIMARY KEY REFERENCES authority_grant(id),
 permission_id VARCHAR(128) NOT NULL REFERENCES authority_action_permission(id),
 request_id VARCHAR(36) NOT NULL, source_plan VARCHAR(128), source_task VARCHAR(128), question_id VARCHAR(128)
);
CREATE TABLE authority_permission_preparation (
 owner_id VARCHAR(64) NOT NULL, worker_id VARCHAR(128) NOT NULL, request_id VARCHAR(36) NOT NULL,
 permission_id VARCHAR(128) NOT NULL REFERENCES authority_action_permission(id),
 selection_digest VARCHAR(64) NOT NULL, grant_id VARCHAR(128) NOT NULL REFERENCES authority_grant(id),
 envelope_json TEXT NOT NULL, projection_json TEXT, instance_id VARCHAR(64) NOT NULL,
 worker_generation BIGINT NOT NULL, platform_boot VARCHAR(36) NOT NULL, worker_boot VARCHAR(36) NOT NULL,
 PRIMARY KEY(owner_id,worker_id,request_id), UNIQUE(grant_id)
);
CREATE TABLE authority_permission_use (
 permission_id VARCHAR(128) NOT NULL REFERENCES authority_action_permission(id), request_id VARCHAR(36) NOT NULL,
 grant_id VARCHAR(128) NOT NULL REFERENCES authority_grant(id), accepted_ms BIGINT NOT NULL,
 PRIMARY KEY(permission_id,request_id)
);
