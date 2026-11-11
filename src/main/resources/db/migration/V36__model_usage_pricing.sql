-- Independent observation and estimate records; existing guarded budgets and
-- manually reconciled execution_meter values are not modified.
CREATE TABLE model_usage_request (
    attempt_id VARCHAR(128) NOT NULL REFERENCES execution_attempt(id),
    request_id VARCHAR(128) NOT NULL,
    task_id VARCHAR(128) NOT NULL REFERENCES work_task(id),
    worker_id VARCHAR(128) NOT NULL,
    provider VARCHAR(64) NOT NULL,
    model VARCHAR(200) NOT NULL,
    protocol VARCHAR(64) NOT NULL,
    purpose VARCHAR(32) NOT NULL,
    connection_kind VARCHAR(32) NOT NULL,
    price_scope VARCHAR(32) NOT NULL,
    service_tier VARCHAR(64) NOT NULL,
    started_ms BIGINT NOT NULL,
    finished_ms BIGINT,
    state VARCHAR(16) NOT NULL,
    input_tokens BIGINT,
    cached_tokens BIGINT,
    output_tokens BIGINT,
    cache_write_tokens BIGINT,
    request_digest VARCHAR(64) NOT NULL,
    terminal_digest VARCHAR(64),
    PRIMARY KEY (attempt_id, request_id),
    CHECK (state IN ('STARTED', 'COMPLETED', 'UNKNOWN')),
    CHECK (input_tokens IS NULL OR input_tokens >= 0),
    CHECK (cached_tokens IS NULL OR cached_tokens >= 0),
    CHECK (output_tokens IS NULL OR output_tokens >= 0),
    CHECK (cache_write_tokens IS NULL OR cache_write_tokens >= 0)
);
CREATE INDEX idx_model_usage_task_time ON model_usage_request(task_id, started_ms);
CREATE INDEX idx_model_usage_price_match ON model_usage_request(provider, model, started_ms);

CREATE TABLE model_price_snapshot (
    snapshot_id VARCHAR(128) PRIMARY KEY,
    provider VARCHAR(64) NOT NULL,
    source_url VARCHAR(2048) NOT NULL,
    content_sha256 VARCHAR(64) NOT NULL,
    snapshot_digest VARCHAR(64) NOT NULL,
    parser_version VARCHAR(128) NOT NULL,
    observed_ms BIGINT NOT NULL,
    published_ms BIGINT NOT NULL
);
CREATE TABLE model_price_head (
    provider VARCHAR(64) PRIMARY KEY,
    snapshot_id VARCHAR(128) NOT NULL REFERENCES model_price_snapshot(snapshot_id)
);
CREATE TABLE model_price_rule (
    snapshot_id VARCHAR(128) NOT NULL REFERENCES model_price_snapshot(snapshot_id),
    rule_id VARCHAR(128) NOT NULL,
    provider VARCHAR(64) NOT NULL,
    model VARCHAR(200) NOT NULL,
    currency VARCHAR(16) NOT NULL,
    service_tier VARCHAR(64) NOT NULL,
    valid_from_ms BIGINT NOT NULL,
    valid_until_ms BIGINT,
    zone_id VARCHAR(128) NOT NULL,
    days_json VARCHAR(128) NOT NULL,
    start_time VARCHAR(32),
    end_time VARCHAR(32),
    only_dates_json TEXT NOT NULL,
    excluded_dates_json TEXT NOT NULL,
    calendar_from VARCHAR(10),
    calendar_until VARCHAR(10),
    input_per_million BIGINT NOT NULL,
    cached_per_million BIGINT NOT NULL,
    output_per_million BIGINT NOT NULL,
    cache_write_included BOOLEAN NOT NULL,
    cache_write_per_million BIGINT,
    min_context BIGINT,
    max_context BIGINT,
    PRIMARY KEY (snapshot_id, rule_id),
    CHECK (valid_until_ms IS NULL OR valid_until_ms > valid_from_ms),
    CHECK (input_per_million >= 0 AND cached_per_million >= 0 AND output_per_million >= 0),
    CHECK (cache_write_per_million IS NULL OR cache_write_per_million >= 0)
);
CREATE INDEX idx_model_price_match ON model_price_rule(provider, model, valid_from_ms, valid_until_ms);

-- Estimates are versioned separately from actual reconciled charges.
CREATE TABLE model_usage_estimate (
    attempt_id VARCHAR(128) NOT NULL,
    request_id VARCHAR(128) NOT NULL,
    estimate_revision BIGINT NOT NULL,
    snapshot_id VARCHAR(128) NOT NULL,
    rule_id VARCHAR(128) NOT NULL,
    currency VARCHAR(16) NOT NULL,
    cost_micros BIGINT NOT NULL,
    calculated_ms BIGINT NOT NULL,
    PRIMARY KEY (attempt_id, request_id, estimate_revision),
    FOREIGN KEY (attempt_id, request_id) REFERENCES model_usage_request(attempt_id, request_id),
    FOREIGN KEY (snapshot_id, rule_id) REFERENCES model_price_rule(snapshot_id, rule_id),
    CHECK (estimate_revision > 0 AND cost_micros >= 0)
);
