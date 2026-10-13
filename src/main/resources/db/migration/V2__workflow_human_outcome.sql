CREATE TABLE flexible_workflow_human_outcome (
    plan_id VARCHAR(128) PRIMARY KEY,
    outcome VARCHAR(16) NOT NULL CHECK (outcome IN ('COMPLETE','CANCEL')),
    status VARCHAR(16) NOT NULL CHECK (status IN ('PENDING','APPLIED')),
    actor VARCHAR(256) NOT NULL,
    reason VARCHAR(2000) NOT NULL,
    decided_at_epoch_ms BIGINT NOT NULL,
    FOREIGN KEY (plan_id) REFERENCES flexible_workflow_plan(id)
);
