CREATE TABLE fleet_admission_budget (
    id VARCHAR(32) PRIMARY KEY,
    max_parallelism INTEGER NOT NULL DEFAULT 0 CHECK (max_parallelism >= 0 AND max_parallelism <= 65535),
    updated_at_epoch_ms BIGINT NOT NULL
);

INSERT INTO fleet_admission_budget(id, max_parallelism, updated_at_epoch_ms)
VALUES ('GLOBAL', 0, 0);
