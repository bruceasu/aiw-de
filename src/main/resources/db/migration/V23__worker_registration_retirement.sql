CREATE TABLE worker_registration_retirement (
    worker_id VARCHAR(128) PRIMARY KEY REFERENCES worker(id),
    actor VARCHAR(256) NOT NULL,
    reason TEXT NOT NULL,
    retired_at_epoch_ms BIGINT NOT NULL
);
