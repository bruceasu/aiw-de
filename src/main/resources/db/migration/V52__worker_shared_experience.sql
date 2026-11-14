CREATE TABLE worker_team_experience (
    task_id VARCHAR(128) PRIMARY KEY REFERENCES work_task(id),
    actor VARCHAR(128) NOT NULL,
    member_id VARCHAR(80) NOT NULL REFERENCES worker_team_member(id),
    run_id VARCHAR(80) NOT NULL,
    experience TEXT NOT NULL,
    created_ms BIGINT NOT NULL
);
CREATE INDEX worker_team_experience_owner ON worker_team_experience(actor, created_ms DESC, task_id);
