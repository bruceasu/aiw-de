-- Additive platform authority only. V8/V9 and all private history stay untouched.
CREATE TABLE authority_lock (id INTEGER PRIMARY KEY CHECK(id=1), version BIGINT NOT NULL);
INSERT INTO authority_lock VALUES(1,0);
CREATE TABLE authority_metadata (id INTEGER PRIMARY KEY CHECK(id=1), instance_id VARCHAR(64), human_auth_required BOOLEAN NOT NULL, retired_data_present BOOLEAN NOT NULL);
-- Freeze only an inventory Gate. Do not copy private rows, infer owners or enable anything.
INSERT INTO authority_metadata
SELECT 1,NULL,legacy_auth_required,CASE WHEN EXISTS(SELECT 1 FROM secretary_work)
 OR EXISTS(SELECT 1 FROM delegation_grant) OR EXISTS(SELECT 1 FROM delegation_identity)
 THEN TRUE ELSE FALSE END FROM delegation_security WHERE id=1;
CREATE TABLE authority_owner (id VARCHAR(64) PRIMARY KEY, actor VARCHAR(256) NOT NULL UNIQUE);
CREATE TABLE authority_identity (
 worker_id VARCHAR(128) PRIMARY KEY, owner_id VARCHAR(64) NOT NULL, key_hash VARCHAR(64) NOT NULL,
 generation BIGINT NOT NULL, enabled_boot VARCHAR(64), changed_by VARCHAR(256) NOT NULL, changed_ms BIGINT NOT NULL,
 read_scope TEXT NOT NULL, worker_boot VARCHAR(64)
);
CREATE TABLE authority_target (
 target_key VARCHAR(1024) PRIMARY KEY, owner_id VARCHAR(64) NOT NULL,
 generation BIGINT NOT NULL, admitted_ms BIGINT NOT NULL, paused BOOLEAN NOT NULL,
 restriction_revision BIGINT NOT NULL
);
CREATE TABLE authority_grant (
 id VARCHAR(128) PRIMARY KEY, owner VARCHAR(64) NOT NULL, worker_id VARCHAR(128) NOT NULL,
 revision BIGINT NOT NULL, state VARCHAR(16) NOT NULL, scope_json TEXT NOT NULL,
 authority VARCHAR(64) NOT NULL, effective_ms BIGINT NOT NULL, expires_ms BIGINT NOT NULL, target_key VARCHAR(1024) NOT NULL
);
CREATE TABLE authority_budget (
 target_key VARCHAR(1024) NOT NULL, action VARCHAR(32) NOT NULL,
 limit_value BIGINT NOT NULL CHECK(limit_value>=0), used_value BIGINT NOT NULL CHECK(used_value>=0),
 reserved_value BIGINT NOT NULL CHECK(reserved_value>=0), PRIMARY KEY(target_key,action)
);
CREATE TABLE authority_restriction (
 id VARCHAR(128) PRIMARY KEY, owner_id VARCHAR(64) NOT NULL, target_key VARCHAR(1024) NOT NULL,
 scope_json TEXT NOT NULL, active BOOLEAN NOT NULL, created_ms BIGINT NOT NULL, private_digest VARCHAR(64) NOT NULL
);
CREATE TABLE authority_receipt (
 instance_id VARCHAR(64) NOT NULL, owner_id VARCHAR(64) NOT NULL, request_id VARCHAR(64) NOT NULL,
 digest VARCHAR(64) NOT NULL, worker_id VARCHAR(128) NOT NULL,
 grant_id VARCHAR(128) NOT NULL, grant_revision BIGINT NOT NULL, target_key VARCHAR(1024) NOT NULL,
 state VARCHAR(32) NOT NULL, result_json TEXT NOT NULL, created_ms BIGINT NOT NULL, updated_ms BIGINT NOT NULL,
 PRIMARY KEY(instance_id,owner_id,request_id)
);
CREATE TABLE authority_audit (
 id VARCHAR(128) PRIMARY KEY, target_key VARCHAR(1024), grant_id VARCHAR(128), request_id VARCHAR(128),
 actor VARCHAR(256) NOT NULL, event VARCHAR(64) NOT NULL, detail TEXT NOT NULL, created_ms BIGINT NOT NULL
);
CREATE TABLE authority_task (
 task_id VARCHAR(128) PRIMARY KEY, target_key VARCHAR(1024) NOT NULL, allowed_attempt BIGINT NOT NULL,
 reserved BOOLEAN NOT NULL, started BOOLEAN NOT NULL, settled BOOLEAN NOT NULL,
 grant_id VARCHAR(128), grant_revision BIGINT, worker_generation BIGINT, enabled_boot VARCHAR(64),
 target_generation BIGINT, restriction_revision BIGINT, request_id VARCHAR(64), post_version BIGINT,
 state VARCHAR(32) NOT NULL DEFAULT 'PREPARED'
);
CREATE TABLE authority_publication_plan (plan_id VARCHAR(128) PRIMARY KEY, target_key VARCHAR(1024) NOT NULL);
CREATE TABLE authority_publication_attempt (
 task_id VARCHAR(128) PRIMARY KEY, allowed_attempt BIGINT NOT NULL, grant_id VARCHAR(128),
 grant_revision BIGINT, worker_generation BIGINT, enabled_boot VARCHAR(64),
 target_key VARCHAR(1024) NOT NULL, owner_id VARCHAR(64) NOT NULL,
 target_generation BIGINT NOT NULL, restriction_revision BIGINT NOT NULL
);
CREATE TABLE authority_model_scope (
 target_key VARCHAR(1024) NOT NULL, role VARCHAR(16) NOT NULL, catalog_version VARCHAR(64) NOT NULL,
 profiles_json TEXT NOT NULL, grant_id VARCHAR(128) NOT NULL, grant_revision BIGINT NOT NULL,
 PRIMARY KEY(target_key,role)
);
CREATE TABLE authority_model_constraint (
 task_id VARCHAR(128) PRIMARY KEY, grant_id VARCHAR(128) NOT NULL, grant_revision BIGINT NOT NULL,
 catalog_version VARCHAR(64) NOT NULL, profiles_json TEXT NOT NULL
);
CREATE TABLE authority_managed_plan (
 plan_id VARCHAR(128) PRIMARY KEY, owner_id VARCHAR(64) NOT NULL, worker_id VARCHAR(128) NOT NULL,
 spec_digest VARCHAR(64) NOT NULL, version BIGINT NOT NULL, generation BIGINT NOT NULL,
 expires_ms BIGINT NOT NULL, authority VARCHAR(64) NOT NULL, paused BOOLEAN NOT NULL
);
CREATE TABLE authority_participant (
 plan_id VARCHAR(128) NOT NULL, worker_id VARCHAR(128) NOT NULL, role VARCHAR(16) NOT NULL,
 PRIMARY KEY(plan_id,worker_id)
);
CREATE TABLE authority_coordination_source (
 plan_id VARCHAR(128) NOT NULL, source_attempt VARCHAR(128) NOT NULL, request_id VARCHAR(64) NOT NULL,
 PRIMARY KEY(plan_id,source_attempt)
);
CREATE TABLE authority_effect_history (
 task_id VARCHAR(128) NOT NULL, attempt BIGINT NOT NULL, snapshot TEXT NOT NULL,
 PRIMARY KEY(task_id,attempt)
);
