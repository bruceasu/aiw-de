-- Fixed operation consent only. No grant is created and no execution is started by a decision.
CREATE TABLE execution_tool_approval (
 id VARCHAR(128) PRIMARY KEY, task_id VARCHAR(128) NOT NULL REFERENCES execution_context(task_id),
 plan_id VARCHAR(128) NOT NULL REFERENCES execution_policy(plan_id), owner_id VARCHAR(128) NOT NULL,
 attempt_id VARCHAR(128) NOT NULL REFERENCES execution_attempt(id), operation_id VARCHAR(128) NOT NULL UNIQUE,
 binding_json TEXT NOT NULL, binding_digest VARCHAR(64) NOT NULL, request_digest VARCHAR(64) NOT NULL,
 policy_digest VARCHAR(64) NOT NULL, generation BIGINT NOT NULL,
 state VARCHAR(24) NOT NULL, revision BIGINT NOT NULL DEFAULT 1, expires_ms BIGINT NOT NULL,
 decision_json TEXT, decided_by VARCHAR(256), created_ms BIGINT NOT NULL, decided_ms BIGINT,
 CHECK(state IN ('PENDING','APPROVED','REJECTED','EXPIRED','INVALIDATED','CONSUMED'))
);
CREATE INDEX idx_tool_approval_plan ON execution_tool_approval(plan_id,created_ms);
