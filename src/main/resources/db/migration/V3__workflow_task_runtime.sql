CREATE TABLE workflow_task_runtime (
    task_id VARCHAR(128) PRIMARY KEY REFERENCES work_task(id) ON DELETE CASCADE,
    profile_json TEXT NOT NULL,
    canonical_location TEXT
);
