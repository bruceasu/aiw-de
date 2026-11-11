-- A submitted collector is an untrusted proposal, not a validation or installation.
CREATE TABLE model_price_repair_result (
    attempt_id VARCHAR(128) PRIMARY KEY REFERENCES execution_attempt(id),
    task_id VARCHAR(128) NOT NULL REFERENCES work_task(id),
    material_id VARCHAR(128) NOT NULL REFERENCES model_price_repair_material(id),
    worker_id VARCHAR(128) NOT NULL,
    module_sha256 VARCHAR(64) NOT NULL,
    module_base64 TEXT NOT NULL,
    checks_sha256 VARCHAR(64) NOT NULL,
    checks_base64 TEXT NOT NULL,
    summary TEXT NOT NULL,
    payload_digest VARCHAR(64) NOT NULL,
    received_ms BIGINT NOT NULL
);
