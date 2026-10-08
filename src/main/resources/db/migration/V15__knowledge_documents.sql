CREATE TABLE knowledge_document (
    id VARCHAR(128) PRIMARY KEY,
    run_id VARCHAR(128) NOT NULL,
    document_type VARCHAR(32) NOT NULL,
    repository_ref VARCHAR(120),
    base_revision VARCHAR(128),
    result_revision VARCHAR(128),
    attempt_no INTEGER NOT NULL,
    content_hash VARCHAR(64) NOT NULL,
    markdown TEXT NOT NULL,
    created_at_epoch_ms BIGINT NOT NULL,
    FOREIGN KEY (id) REFERENCES work_task(id),
    CHECK (document_type IN ('REQUIREMENT_ANALYSIS','SOLUTION_ARCHITECTURE','IMPLEMENTATION_PLANNING')),
    CHECK (attempt_no >= 1)
);

CREATE INDEX idx_knowledge_document_repository ON knowledge_document(repository_ref, created_at_epoch_ms);
CREATE INDEX idx_knowledge_document_run ON knowledge_document(run_id, document_type);
