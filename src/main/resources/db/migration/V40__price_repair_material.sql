-- Fixed public inputs only. This does not dispatch a Worker or install code.
CREATE TABLE model_price_repair_material (
    id VARCHAR(128) PRIMARY KEY,
    provider VARCHAR(64) NOT NULL,
    failure_fingerprint VARCHAR(64) NOT NULL,
    collector_sha256 VARCHAR(64) NOT NULL,
    collector_version VARCHAR(128) NOT NULL,
    source_url VARCHAR(2048) NOT NULL,
    reason VARCHAR(80) NOT NULL,
    original_module_base64 TEXT NOT NULL,
    last_success_sha256 VARCHAR(64) NOT NULL,
    last_success_sample_base64 TEXT NOT NULL,
    changed_page_sha256 VARCHAR(64) NOT NULL,
    changed_page_base64 TEXT NOT NULL,
    created_ms BIGINT NOT NULL,
    status VARCHAR(24) NOT NULL,
    CHECK (status IN ('PREPARED','CONSUMED','SUPERSEDED'))
);
CREATE UNIQUE INDEX idx_price_repair_one_prepared ON model_price_repair_material(provider) WHERE status='PREPARED';
