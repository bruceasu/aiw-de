-- Tracks independent candidate verification and recovery-safe local activation.
CREATE TABLE model_price_repair_activation (
    material_id VARCHAR(128) PRIMARY KEY REFERENCES model_price_repair_material(id),
    provider VARCHAR(64) NOT NULL,
    state VARCHAR(24) NOT NULL,
    lease_token VARCHAR(128),
    lease_until_ms BIGINT,
    candidate_sha256 VARCHAR(64),
    verified_page_sha256 VARCHAR(64),
    failure_code VARCHAR(80),
    updated_ms BIGINT NOT NULL,
    CHECK (state IN ('VERIFYING','INSTALLING','INSTALLED','FAILED'))
);
