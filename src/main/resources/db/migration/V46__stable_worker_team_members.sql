-- A member outlives execution processes. Old tasks remain unassigned.
CREATE TABLE worker_team_member (
 id VARCHAR(80) PRIMARY KEY, actor VARCHAR(128) NOT NULL,
 name VARCHAR(80) NOT NULL, purpose TEXT NOT NULL, skills_json TEXT NOT NULL, notes TEXT NOT NULL,
 revision BIGINT NOT NULL, state VARCHAR(24) NOT NULL,
 created_ms BIGINT NOT NULL, updated_ms BIGINT NOT NULL,
 CHECK(state IN ('ACTIVE','DRAINING','ARCHIVED'))
);
CREATE INDEX worker_team_actor ON worker_team_member(actor,state,updated_ms);
CREATE TABLE worker_team_assignment (
 run_id VARCHAR(80) NOT NULL REFERENCES workflow_execution(id), node_key VARCHAR(32) NOT NULL,
 member_id VARCHAR(80) NOT NULL REFERENCES worker_team_member(id), profile_json TEXT NOT NULL,
 PRIMARY KEY(run_id,node_key)
);
CREATE INDEX worker_team_assigned_member ON worker_team_assignment(member_id,run_id);
CREATE TABLE worker_team_task (
 task_id VARCHAR(128) PRIMARY KEY REFERENCES work_task(id),
 run_id VARCHAR(80) NOT NULL, node_key VARCHAR(32) NOT NULL,
 FOREIGN KEY(run_id,node_key) REFERENCES worker_team_assignment(run_id,node_key)
);
