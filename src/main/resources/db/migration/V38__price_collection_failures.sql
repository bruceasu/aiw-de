-- The bounded sample is a public provider page, not a model request or credential.
CREATE TABLE model_price_collection_failure (
    provider VARCHAR(64) PRIMARY KEY REFERENCES model_price_collection_state(provider),
    fingerprint VARCHAR(64) NOT NULL,
    collector_version VARCHAR(128) NOT NULL,
    reason VARCHAR(80) NOT NULL,
    source_url VARCHAR(2048) NOT NULL,
    content_sha256 VARCHAR(64) NOT NULL,
    sample_base64 TEXT NOT NULL,
    last_seen_ms BIGINT NOT NULL,
    failure_count INTEGER NOT NULL,
    active BOOLEAN NOT NULL,
    CHECK (failure_count >= 0)
);
