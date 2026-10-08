ALTER TABLE work_task ADD COLUMN instruction_revision INTEGER NOT NULL DEFAULT 1;

CREATE TABLE work_task_instruction_revision (
    task_id VARCHAR(128) NOT NULL,
    revision INTEGER NOT NULL,
    instruction TEXT NOT NULL,
    actor VARCHAR(256) NOT NULL,
    reason VARCHAR(2000) NOT NULL,
    predecessor_revision INTEGER,
    created_at_epoch_ms BIGINT NOT NULL,
    PRIMARY KEY (task_id, revision),
    FOREIGN KEY (task_id) REFERENCES work_task(id) ON DELETE CASCADE,
    CHECK (revision >= 1),
    CHECK (predecessor_revision IS NULL OR predecessor_revision = revision - 1)
);

INSERT INTO work_task_instruction_revision(
    task_id, revision, instruction, actor, reason, predecessor_revision, created_at_epoch_ms
)
SELECT id, 1, instruction, 'system:migration', 'Initial task instruction (migrated)', NULL, created_at_epoch_ms
  FROM work_task;

ALTER TABLE execution_attempt ADD COLUMN instruction_revision INTEGER NOT NULL DEFAULT 1;
ALTER TABLE execution_attempt ADD COLUMN stop_requested_at_epoch_ms BIGINT;
ALTER TABLE execution_attempt ADD COLUMN stop_acknowledged_at_epoch_ms BIGINT;

CREATE TABLE work_task_control_event (
    id VARCHAR(128) PRIMARY KEY,
    task_id VARCHAR(128) NOT NULL,
    attempt_id VARCHAR(128),
    action VARCHAR(32) NOT NULL,
    actor VARCHAR(256) NOT NULL,
    reason VARCHAR(2000) NOT NULL,
    warning_confirmed INTEGER NOT NULL DEFAULT 0,
    created_at_epoch_ms BIGINT NOT NULL,
    FOREIGN KEY (task_id) REFERENCES work_task(id),
    FOREIGN KEY (attempt_id) REFERENCES execution_attempt(id),
    CHECK (warning_confirmed IN (0,1))
);

CREATE TABLE work_task_correction (
    completed_task_id VARCHAR(128) NOT NULL,
    correction_task_id VARCHAR(128) NOT NULL,
    created_by VARCHAR(256) NOT NULL,
    reason VARCHAR(2000) NOT NULL,
    created_at_epoch_ms BIGINT NOT NULL,
    PRIMARY KEY (completed_task_id, correction_task_id),
    UNIQUE (correction_task_id),
    FOREIGN KEY (completed_task_id) REFERENCES work_task(id),
    FOREIGN KEY (correction_task_id) REFERENCES work_task(id)
);

CREATE INDEX idx_task_instruction_revision_history
    ON work_task_instruction_revision(task_id, revision);
CREATE INDEX idx_task_control_event_history
    ON work_task_control_event(task_id, created_at_epoch_ms, id);
CREATE INDEX idx_task_correction_parent
    ON work_task_correction(completed_task_id, created_at_epoch_ms);
