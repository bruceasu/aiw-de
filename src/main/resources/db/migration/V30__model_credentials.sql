-- Model identity is separate from execution authority. Secrets are AES-GCM ciphertext only.
CREATE TABLE model_connection (
 id VARCHAR(80) PRIMARY KEY, owner_actor VARCHAR(256) NOT NULL, kind VARCHAR(32) NOT NULL,
 label VARCHAR(160) NOT NULL, client_id VARCHAR(256) NOT NULL, subject_id VARCHAR(256) NOT NULL,
 account_id VARCHAR(256), plan_type VARCHAR(80), state VARCHAR(40) NOT NULL,
 version BIGINT NOT NULL, generation BIGINT NOT NULL, expires_ms BIGINT NOT NULL,
 earliest_refresh_ms BIGINT NOT NULL, secret_cipher TEXT, refresh_started_ms BIGINT,
 updated_ms BIGINT NOT NULL, UNIQUE(owner_actor,kind,client_id,subject_id)
);
CREATE TABLE model_auth_host (id INTEGER PRIMARY KEY, host_id VARCHAR(80) NOT NULL);
CREATE TABLE model_token_seen (token_digest VARCHAR(64) PRIMARY KEY, connection_id VARCHAR(80) NOT NULL);
CREATE TABLE model_auth_task (
 task_id VARCHAR(128) PRIMARY KEY, connection_id VARCHAR(80) NOT NULL,
 connection_version BIGINT NOT NULL, owner_actor VARCHAR(256) NOT NULL,
 runtime VARCHAR(80) NOT NULL, model VARCHAR(128) NOT NULL
);
CREATE TABLE model_auth_worker (
 worker_id VARCHAR(128) PRIMARY KEY, connection_id VARCHAR(80) NOT NULL,
 connection_version BIGINT NOT NULL, runtime VARCHAR(80) NOT NULL, model VARCHAR(128) NOT NULL
);
CREATE TABLE model_auth_plan (
 subject_type VARCHAR(80) NOT NULL, subject_id VARCHAR(128) NOT NULL,
 connection_id VARCHAR(80) NOT NULL, connection_version BIGINT NOT NULL,
 PRIMARY KEY(subject_type,subject_id)
);
