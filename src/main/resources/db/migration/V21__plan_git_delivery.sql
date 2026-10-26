CREATE TABLE plan_git_delivery (
    plan_id VARCHAR(128) NOT NULL REFERENCES flexible_workflow_plan(id) ON DELETE CASCADE,
    repository_ref VARCHAR(256) NOT NULL,
    repository_path TEXT NOT NULL,
    result_revision VARCHAR(64) NOT NULL,
    request_id VARCHAR(64) NOT NULL UNIQUE,
    state VARCHAR(16) NOT NULL CHECK (state IN ('READY','BLOCKED','MERGING','DELIVERED','UNKNOWN')),
    actor VARCHAR(256) NOT NULL,
    target_branch VARCHAR(512) NOT NULL,
    before_revision VARCHAR(64) NOT NULL,
    after_revision VARCHAR(64),
    message TEXT NOT NULL,
    created_ms BIGINT NOT NULL,
    updated_ms BIGINT NOT NULL,
    PRIMARY KEY (plan_id, repository_ref, result_revision)
);
CREATE INDEX plan_git_delivery_ready ON plan_git_delivery(state,created_ms);
CREATE TABLE plan_git_delivery_target (
    plan_id VARCHAR(128) NOT NULL REFERENCES flexible_workflow_plan(id) ON DELETE CASCADE,
    repository_ref VARCHAR(256) NOT NULL,
    repository_path TEXT NOT NULL,
    target_branch VARCHAR(512) NOT NULL,
    captured_revision VARCHAR(64) NOT NULL,
    PRIMARY KEY(plan_id,repository_ref)
);
