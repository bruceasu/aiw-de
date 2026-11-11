-- A public-page observation is not an accounting price or an effective interval.
CREATE TABLE model_price_collection_state (
    provider VARCHAR(64) PRIMARY KEY,
    lease_token VARCHAR(64),
    lease_until_ms BIGINT,
    last_attempt_ms BIGINT,
    last_success_ms BIGINT,
    status VARCHAR(32) NOT NULL,
    failure_code VARCHAR(64),
    source_url VARCHAR(2048),
    content_sha256 VARCHAR(64),
    collector_version VARCHAR(128),
    observation_json TEXT,
    CHECK (status IN ('PENDING','RUNNING','OBSERVED_INCOMPLETE','FAILED'))
);
