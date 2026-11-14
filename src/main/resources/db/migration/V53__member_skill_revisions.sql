CREATE TABLE worker_team_skill_revision (
    member_id VARCHAR(80) NOT NULL REFERENCES worker_team_member(id),
    skill_ref VARCHAR(96) NOT NULL,
    version INTEGER NOT NULL CHECK(version >= 0),
    content TEXT NOT NULL,
    content_hash VARCHAR(64) NOT NULL,
    reason VARCHAR(1000) NOT NULL,
    source_task_id VARCHAR(128) REFERENCES work_task(id),
    source_run_id VARCHAR(80),
    created_ms BIGINT NOT NULL,
    PRIMARY KEY(member_id,skill_ref,version),
    UNIQUE(source_task_id,skill_ref)
);
