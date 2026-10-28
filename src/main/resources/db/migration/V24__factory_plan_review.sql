CREATE TABLE factory_plan_review (
    plan_id VARCHAR(128) PRIMARY KEY REFERENCES workflow_factory_plan(plan_id) ON DELETE CASCADE,
    source_task_id VARCHAR(128) REFERENCES work_task(id) ON DELETE SET NULL,
    review_task_id VARCHAR(128) REFERENCES work_task(id) ON DELETE SET NULL,
    state VARCHAR(32) NOT NULL,
    updated_at_epoch_ms BIGINT NOT NULL
);
CREATE UNIQUE INDEX idx_factory_plan_review_task ON factory_plan_review(review_task_id);
