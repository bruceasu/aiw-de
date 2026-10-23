CREATE TABLE workflow_graph (
 id VARCHAR(96) PRIMARY KEY, owner_id VARCHAR(128) NOT NULL, edit_version BIGINT NOT NULL,
 state VARCHAR(32) NOT NULL, runtime_revision BIGINT NOT NULL DEFAULT 0, runtime_json TEXT NOT NULL
);
CREATE TABLE workflow_graph_version (
 plan_id VARCHAR(96) NOT NULL, version BIGINT NOT NULL, spec_json TEXT NOT NULL,
 digest VARCHAR(64) NOT NULL, source VARCHAR(128) NOT NULL, reason TEXT NOT NULL,
 PRIMARY KEY(plan_id,version)
);
CREATE TABLE workflow_graph_request (
 owner_id VARCHAR(128) NOT NULL, request_id VARCHAR(128) NOT NULL, plan_id VARCHAR(96) NOT NULL,
 digest VARCHAR(64) NOT NULL, receipt_json TEXT NOT NULL, PRIMARY KEY(owner_id,request_id)
);
CREATE TABLE workflow_graph_event (
 plan_id VARCHAR(96) NOT NULL, event_id VARCHAR(128) NOT NULL, execution_id VARCHAR(128),
 kind VARCHAR(64) NOT NULL, payload_json TEXT NOT NULL, created_ms BIGINT NOT NULL,
 PRIMARY KEY(plan_id,event_id)
);
CREATE TABLE workflow_graph_task (
 task_id VARCHAR(128) PRIMARY KEY, plan_id VARCHAR(96) NOT NULL, execution_id VARCHAR(128) NOT NULL
);
CREATE INDEX workflow_graph_task_execution ON workflow_graph_task(plan_id,execution_id);
CREATE TABLE workflow_graph_recommendation (
 request_id VARCHAR(128) PRIMARY KEY, plan_id VARCHAR(96) NOT NULL, plan_version BIGINT NOT NULL,
 task_id VARCHAR(128), intent_json TEXT NOT NULL, state VARCHAR(32) NOT NULL,
 candidate_json TEXT, diagnostic TEXT
);
