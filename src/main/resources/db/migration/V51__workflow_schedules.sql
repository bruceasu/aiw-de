CREATE TABLE workflow_schedule (
 id VARCHAR(36) PRIMARY KEY,
 actor VARCHAR(128) NOT NULL,
 owner_id VARCHAR(64) NOT NULL REFERENCES authority_owner(id),
 request_digest VARCHAR(64) NOT NULL,
 spec_json TEXT NOT NULL,
 repeat_kind VARCHAR(16) NOT NULL,
 zone_id VARCHAR(80) NOT NULL,
 local_time VARCHAR(8) NOT NULL,
 weekday INTEGER NOT NULL,
 next_ms BIGINT NOT NULL,
 state VARCHAR(32) NOT NULL,
 last_execution VARCHAR(128),
 failure_checked BOOLEAN NOT NULL DEFAULT FALSE,
 message VARCHAR(1000) NOT NULL,
 version BIGINT NOT NULL,
 updated_ms BIGINT NOT NULL
);
CREATE INDEX workflow_schedule_due ON workflow_schedule(state,next_ms);
