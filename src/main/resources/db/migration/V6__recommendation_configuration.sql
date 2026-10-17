CREATE TABLE workflow_recommendation_settings (
    id INTEGER PRIMARY KEY,
    enabled BOOLEAN NOT NULL,
    planner VARCHAR(64), model VARCHAR(120), effort VARCHAR(120),
    revision BIGINT NOT NULL,
    updated_by VARCHAR(256) NOT NULL,
    updated_at_epoch_ms BIGINT NOT NULL
);
INSERT INTO workflow_recommendation_settings VALUES (1, TRUE, NULL, NULL, NULL, 0, 'system', 0);
CREATE TABLE workflow_recommendation_audit (
    id VARCHAR(128) PRIMARY KEY, actor VARCHAR(256) NOT NULL,
    occurred_at_epoch_ms BIGINT NOT NULL, previous_json TEXT NOT NULL, current_json TEXT NOT NULL
);
CREATE TABLE workflow_recommendation_context (
    id VARCHAR(128) PRIMARY KEY, actor VARCHAR(256) NOT NULL,
    goal TEXT NOT NULL, input_version BIGINT NOT NULL, revision BIGINT NOT NULL,
    plan_id VARCHAR(128), updated_at_epoch_ms BIGINT NOT NULL,
    FOREIGN KEY (plan_id) REFERENCES flexible_workflow_plan(id) ON DELETE SET NULL
);
CREATE INDEX idx_recommendation_context_actor ON workflow_recommendation_context(actor, updated_at_epoch_ms);
CREATE TABLE workflow_recommendation_request (
    task_id VARCHAR(256) PRIMARY KEY, request_key VARCHAR(64) NOT NULL UNIQUE,
    context_id VARCHAR(128) NOT NULL, actor VARCHAR(256) NOT NULL,
    goal TEXT NOT NULL, input_version BIGINT NOT NULL,
    settings_revision BIGINT NOT NULL, frozen_json TEXT NOT NULL, context_revision BIGINT NOT NULL,
    created_at_epoch_ms BIGINT NOT NULL,
    FOREIGN KEY (context_id) REFERENCES workflow_recommendation_context(id),
    FOREIGN KEY (task_id) REFERENCES work_task(id)
);
CREATE INDEX idx_recommendation_request_context ON workflow_recommendation_request(context_id, created_at_epoch_ms);
CREATE TABLE workflow_recommendation_plan_context (
    plan_id VARCHAR(128) PRIMARY KEY, context_id VARCHAR(128) NOT NULL,
    FOREIGN KEY (plan_id) REFERENCES flexible_workflow_plan(id) ON DELETE CASCADE,
    FOREIGN KEY (context_id) REFERENCES workflow_recommendation_context(id)
);
