CREATE TABLE fleet_admission_budget_event (
    budget_id VARCHAR(32) NOT NULL,
    sequence_no BIGINT NOT NULL,
    event_type VARCHAR(64) NOT NULL CHECK (event_type IN ('MAX_PARALLELISM_CHANGED')),
    actor VARCHAR(256) NOT NULL,
    previous_max_parallelism INTEGER NOT NULL CHECK (previous_max_parallelism >= 0 AND previous_max_parallelism <= 65535),
    max_parallelism INTEGER NOT NULL CHECK (max_parallelism >= 0 AND max_parallelism <= 65535),
    active_leases INTEGER NOT NULL CHECK (active_leases >= 0),
    reason VARCHAR(4096),
    occurred_at_epoch_ms BIGINT NOT NULL,
    PRIMARY KEY (budget_id, sequence_no),
    FOREIGN KEY (budget_id) REFERENCES fleet_admission_budget(id)
);

CREATE INDEX idx_fleet_admission_budget_event_time
    ON fleet_admission_budget_event(budget_id, occurred_at_epoch_ms);
