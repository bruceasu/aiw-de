-- R5 keeps workspace authority in existing Task/ExecutionAttempt aggregates.
-- No new table is introduced. repository_ref is a logical name, never a machine-local path.
ALTER TABLE work_task ADD COLUMN repository_ref VARCHAR(256);
ALTER TABLE work_task ADD COLUMN base_revision VARCHAR(128);

ALTER TABLE execution_attempt ADD COLUMN workspace_key VARCHAR(256);
ALTER TABLE execution_attempt ADD COLUMN branch_name VARCHAR(512);
ALTER TABLE execution_attempt ADD COLUMN base_revision VARCHAR(128);
ALTER TABLE execution_attempt ADD COLUMN result_revision VARCHAR(128);
ALTER TABLE execution_attempt ADD COLUMN changed_files TEXT;

CREATE INDEX idx_work_task_repository_ready
    ON work_task(repository_ref, state, priority DESC, created_at_epoch_ms);
