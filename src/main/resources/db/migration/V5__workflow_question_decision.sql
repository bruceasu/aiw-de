-- Append-only answers pinned to immutable source questions; old reports stay unchanged.
CREATE TABLE workflow_question_decision (
    id VARCHAR(128) PRIMARY KEY,
    plan_id VARCHAR(128) NOT NULL,
    question_key VARCHAR(64) NOT NULL,
    source_task_id VARCHAR(256) NOT NULL,
    source_file_id VARCHAR(128) NOT NULL,
    source_hash VARCHAR(64) NOT NULL,
    question_id VARCHAR(160) NOT NULL,
    question_text TEXT NOT NULL,
    answer TEXT NOT NULL,
    actor VARCHAR(256) NOT NULL,
    plan_version BIGINT NOT NULL,
    decided_at_epoch_ms BIGINT NOT NULL,
    FOREIGN KEY (plan_id) REFERENCES flexible_workflow_plan(id) ON DELETE CASCADE,
    UNIQUE (plan_id, plan_version)
);
CREATE INDEX idx_workflow_question_decision ON workflow_question_decision(plan_id, question_key, plan_version);
