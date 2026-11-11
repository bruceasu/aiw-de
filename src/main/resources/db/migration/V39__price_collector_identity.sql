-- The parser version is descriptive; the digest binds results to exact executed bytes.
ALTER TABLE model_price_collection_state ADD COLUMN collector_sha256 VARCHAR(64);
ALTER TABLE model_price_collection_state ADD COLUMN last_success_sample_base64 TEXT;
ALTER TABLE model_price_collection_failure ADD COLUMN collector_sha256 VARCHAR(64);
