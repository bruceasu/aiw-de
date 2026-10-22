CREATE TABLE execution_asset (
    kind VARCHAR(16) NOT NULL, ref VARCHAR(96) NOT NULL, version VARCHAR(64) NOT NULL,
    digest VARCHAR(64) NOT NULL, owner_id VARCHAR(128) NOT NULL, repository_ref VARCHAR(120) NOT NULL,
    asset_json TEXT NOT NULL, PRIMARY KEY(kind,ref,version,owner_id,repository_ref)
);
CREATE TABLE execution_configuration (
    owner_id VARCHAR(128) NOT NULL REFERENCES authority_owner(id), id VARCHAR(96) NOT NULL, revision BIGINT NOT NULL,
    digest VARCHAR(64) NOT NULL, repository_ref VARCHAR(120) NOT NULL, content_json TEXT NOT NULL,
    PRIMARY KEY(owner_id,id,revision)
);
CREATE TABLE execution_preparation (
    intent_id VARCHAR(96) NOT NULL, intent_version BIGINT NOT NULL, owner_id VARCHAR(128) NOT NULL REFERENCES authority_owner(id),
    generation BIGINT NOT NULL, state VARCHAR(32) NOT NULL, intent_digest VARCHAR(64) NOT NULL, intent_json TEXT NOT NULL,
    config_json TEXT, task_id VARCHAR(128) UNIQUE REFERENCES work_task(id), error_code VARCHAR(48), details_json TEXT NOT NULL,
    PRIMARY KEY(intent_id,intent_version), CHECK(state IN ('NEEDS_CONFIGURATION','PREPARING','READY','BLOCKED'))
);
CREATE TABLE execution_task_configuration (
    task_id VARCHAR(128) PRIMARY KEY REFERENCES work_task(id), binding_json TEXT NOT NULL
);
CREATE TABLE execution_worker_configuration (
    worker_id VARCHAR(128) PRIMARY KEY REFERENCES worker(id), capabilities_json TEXT NOT NULL, capabilities_digest VARCHAR(64) NOT NULL
);
CREATE TABLE execution_attempt_configuration (
    attempt_id VARCHAR(128) PRIMARY KEY REFERENCES execution_attempt(id), task_id VARCHAR(128) NOT NULL REFERENCES work_task(id),
    worker_id VARCHAR(128) NOT NULL, capabilities_json TEXT NOT NULL, capabilities_digest VARCHAR(64) NOT NULL
);
