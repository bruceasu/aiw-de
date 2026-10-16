-- One human-approved repair round; history survives plan/task retention cleanup.
CREATE TABLE workflow_repair_authorization (
    id VARCHAR(128) PRIMARY KEY,
    plan_id VARCHAR(128) NOT NULL,
    plan_version BIGINT NOT NULL,
    source_review_id VARCHAR(128) NOT NULL,
    source_task_version BIGINT NOT NULL,
    scope TEXT NOT NULL,
    reason VARCHAR(2000) NOT NULL,
    granted_by VARCHAR(256) NOT NULL,
    delegate_worker_id VARCHAR(128),
    decision_task_id VARCHAR(128) UNIQUE,
    repair_task_id VARCHAR(128),
    round_budget INTEGER NOT NULL CHECK (round_budget = 1),
    status VARCHAR(16) NOT NULL CHECK (status IN ('PENDING','CONSUMED','DENIED','REVOKED','EXPIRED','STALE')),
    created_at_epoch_ms BIGINT NOT NULL,
    expires_at_epoch_ms BIGINT NOT NULL,
    decided_at_epoch_ms BIGINT
);
CREATE UNIQUE INDEX idx_workflow_repair_pending ON workflow_repair_authorization(plan_id) WHERE status='PENDING';
CREATE INDEX idx_workflow_repair_history ON workflow_repair_authorization(plan_id,created_at_epoch_ms);
