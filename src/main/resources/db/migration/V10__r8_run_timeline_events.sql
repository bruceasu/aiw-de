-- R8-S6: retain only run-level history that cannot be reconstructed from the existing
-- authoritative snapshots. Task/attempt/gate/approval timeline entries remain projections
-- of work_task, execution_attempt, approval_request, and autonomous_run.
-- event_sequence gives every run a strict audit order even when multiple actions share a millisecond.
ALTER TABLE autonomous_run ADD COLUMN event_sequence BIGINT NOT NULL DEFAULT 0;

CREATE TABLE autonomous_run_event (
    run_id VARCHAR(128) NOT NULL,
    sequence_no BIGINT NOT NULL,
    event_type VARCHAR(32) NOT NULL,
    actor VARCHAR(256),
    related_task_id VARCHAR(128),
    detail VARCHAR(4096),
    occurred_at_epoch_ms BIGINT NOT NULL,
    PRIMARY KEY (run_id, sequence_no),
    FOREIGN KEY (run_id) REFERENCES autonomous_run(id) ON DELETE CASCADE,
    FOREIGN KEY (related_task_id) REFERENCES work_task(id),
    CHECK (sequence_no >= 1),
    CHECK (event_type IN ('FAILED','RECOVERED','PAUSED','RESUMED','CANCELLED'))
);

CREATE INDEX idx_autonomous_run_event_run_time
    ON autonomous_run_event(run_id, occurred_at_epoch_ms, sequence_no);
