-- Additive, single-machine secretary records. Historical ownership is not migrated.
CREATE TABLE secretary_lock (id INTEGER PRIMARY KEY, version BIGINT NOT NULL);
INSERT INTO secretary_lock VALUES (1,0);
CREATE TABLE secretary_work (
 id VARCHAR(64) PRIMARY KEY, actor VARCHAR(256) NOT NULL,
 revision BIGINT NOT NULL, generation BIGINT NOT NULL, state VARCHAR(32) NOT NULL,
 plan_id VARCHAR(128) UNIQUE, due_ms BIGINT, payload TEXT NOT NULL, updated_ms BIGINT NOT NULL
);
CREATE INDEX idx_secretary_due ON secretary_work(state,due_ms);
CREATE INDEX idx_secretary_actor ON secretary_work(actor,updated_ms);
CREATE TABLE secretary_request (
 id VARCHAR(128) PRIMARY KEY, work_id VARCHAR(64) NOT NULL, generation BIGINT NOT NULL,
 phase VARCHAR(128) NOT NULL, plan_version BIGINT NOT NULL, source_attempt VARCHAR(128),
 consumed BOOLEAN NOT NULL, FOREIGN KEY(work_id) REFERENCES secretary_work(id),
 UNIQUE(work_id,generation,phase)
);
CREATE TABLE secretary_participant (
 work_id VARCHAR(64) NOT NULL, worker_id VARCHAR(128) NOT NULL, role VARCHAR(16) NOT NULL,
 PRIMARY KEY(work_id,worker_id), FOREIGN KEY(work_id) REFERENCES secretary_work(id)
);
CREATE TABLE secretary_admitted_task (
 task_id VARCHAR(128) PRIMARY KEY, work_id VARCHAR(64) NOT NULL, generation BIGINT NOT NULL,
 FOREIGN KEY(work_id) REFERENCES secretary_work(id)
);
CREATE TABLE secretary_message (
 id VARCHAR(128) PRIMARY KEY, work_id VARCHAR(64) NOT NULL, actor VARCHAR(256) NOT NULL,
 input_hash VARCHAR(64) NOT NULL, user_text TEXT NOT NULL, reply TEXT, created_ms BIGINT NOT NULL, source_revision BIGINT NOT NULL,
 FOREIGN KEY(work_id) REFERENCES secretary_work(id)
);
