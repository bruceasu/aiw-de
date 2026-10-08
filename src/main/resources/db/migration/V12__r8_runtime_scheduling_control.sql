-- R8-S11: runtime maxParallelism changes are durable scheduling authority and require audit retention.
-- Rebuild the run-event table portably so the event_type CHECK admits the new event without
-- introducing a new business table. Existing run-event history and sequence numbers are preserved.
CREATE TABLE autonomous_run_event_v12 (
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
    CHECK (event_type IN ('FAILED','RECOVERED','PAUSED','RESUMED','CANCELLED','MAX_PARALLELISM_CHANGED'))
);

INSERT INTO autonomous_run_event_v12(
    run_id,sequence_no,event_type,actor,related_task_id,detail,occurred_at_epoch_ms
)
SELECT run_id,sequence_no,event_type,actor,related_task_id,detail,occurred_at_epoch_ms
  FROM autonomous_run_event;

DROP TABLE autonomous_run_event;
ALTER TABLE autonomous_run_event_v12 RENAME TO autonomous_run_event;

CREATE INDEX idx_autonomous_run_event_run_time
    ON autonomous_run_event(run_id, occurred_at_epoch_ms, sequence_no);
