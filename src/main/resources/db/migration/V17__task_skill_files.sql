-- Fresh-schema additive storage. No destructive cutover or data migration.
CREATE TABLE execution_task_skills (
    task_id TEXT PRIMARY KEY REFERENCES work_task(id),
    binding_json TEXT NOT NULL,
    digest TEXT NOT NULL,
    configured INTEGER NOT NULL CHECK(configured IN (0,1))
);
