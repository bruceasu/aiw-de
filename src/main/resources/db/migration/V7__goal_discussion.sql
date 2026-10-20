CREATE TABLE goal_draft (
 id VARCHAR(64) PRIMARY KEY, actor VARCHAR(256) NOT NULL, title VARCHAR(160) NOT NULL,
 revision BIGINT NOT NULL, epoch BIGINT NOT NULL, deleted BOOLEAN NOT NULL,
 payload TEXT NOT NULL, updated_at BIGINT NOT NULL
);
CREATE INDEX idx_goal_actor ON goal_draft(actor, updated_at);
CREATE TABLE goal_attachment (
 id VARCHAR(64) PRIMARY KEY, goal_id VARCHAR(64) NOT NULL,
 filename VARCHAR(256) NOT NULL, content_base64 TEXT NOT NULL, sha256 VARCHAR(64) NOT NULL,
 status VARCHAR(64) NOT NULL, extracted TEXT NOT NULL, detail TEXT NOT NULL,
 FOREIGN KEY(goal_id) REFERENCES goal_draft(id)
);
CREATE TABLE goal_request (
 id VARCHAR(64) PRIMARY KEY, goal_id VARCHAR(64) NOT NULL, actor VARCHAR(256) NOT NULL,
 source_revision BIGINT NOT NULL, epoch BIGINT NOT NULL, input_hash VARCHAR(64) NOT NULL,
 frozen_json TEXT NOT NULL, consumed BOOLEAN NOT NULL,
 FOREIGN KEY(goal_id) REFERENCES goal_draft(id), FOREIGN KEY(id) REFERENCES work_task(id)
);
CREATE TABLE goal_version (
 id VARCHAR(64) PRIMARY KEY, goal_id VARCHAR(64) NOT NULL, source_revision BIGINT NOT NULL,
 snapshot TEXT NOT NULL, editor_json TEXT NOT NULL, editor_revision BIGINT NOT NULL,
 recommendation_id VARCHAR(128), plan_id VARCHAR(128),
 UNIQUE(goal_id, source_revision), FOREIGN KEY(goal_id) REFERENCES goal_draft(id),
 FOREIGN KEY(plan_id) REFERENCES flexible_workflow_plan(id) ON DELETE SET NULL
);
