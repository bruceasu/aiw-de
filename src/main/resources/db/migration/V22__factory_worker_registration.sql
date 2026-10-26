-- Retiring a registration must not remove Worker IDs referenced by execution history.
CREATE TABLE factory_worker_registration (
    worker_id VARCHAR(128) PRIMARY KEY,
    compatibility_key VARCHAR(64) NOT NULL,
    retired_at_epoch_ms BIGINT
);
