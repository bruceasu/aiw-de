-- Immutable task-attempt deliverables. Existing task/lease state remains authoritative.
CREATE TABLE task_output_bundle (
    attempt_id VARCHAR(128) PRIMARY KEY,
    task_id VARCHAR(128) NOT NULL,
    repository_ref VARCHAR(256) NOT NULL,
    base_revision VARCHAR(64),
    result_revision VARCHAR(64),
    directory TEXT NOT NULL,
    created_at_epoch_ms BIGINT NOT NULL,
    manifest_json TEXT NOT NULL,
    execution_state VARCHAR(32) NOT NULL DEFAULT 'REGISTERED'
);

CREATE INDEX idx_task_output_task ON task_output_bundle(task_id, created_at_epoch_ms);

CREATE TABLE task_output_file (
    id VARCHAR(128) PRIMARY KEY,
    attempt_id VARCHAR(128) NOT NULL,
    relative_path VARCHAR(512) NOT NULL,
    kind VARCHAR(32) NOT NULL,
    content_hash VARCHAR(64) NOT NULL,
    byte_count BIGINT NOT NULL,
    content_base64 TEXT NOT NULL,
    FOREIGN KEY (attempt_id) REFERENCES task_output_bundle(attempt_id),
    UNIQUE (attempt_id, relative_path)
);

CREATE TABLE task_output_publication (
    plan_id VARCHAR(128) PRIMARY KEY,
    file_id VARCHAR(128) NOT NULL,
    review_task_id VARCHAR(128) NOT NULL,
    target_path VARCHAR(512) NOT NULL,
    actor VARCHAR(256) NOT NULL,
    reason VARCHAR(2000) NOT NULL,
    created_at_epoch_ms BIGINT NOT NULL,
    FOREIGN KEY (file_id) REFERENCES task_output_file(id)
);

CREATE TABLE task_output_input (
    consumer_attempt_id VARCHAR(128) NOT NULL,
    source_attempt_id VARCHAR(128) NOT NULL,
    PRIMARY KEY (consumer_attempt_id, source_attempt_id)
);

CREATE TABLE task_output_review (
    source_attempt_id VARCHAR(128) NOT NULL,
    review_task_id VARCHAR(128) NOT NULL,
    review_attempt_id VARCHAR(128) NOT NULL,
    review_worker_id VARCHAR(128) NOT NULL,
    source_worker_id VARCHAR(128) NOT NULL,
    reviewed_at_epoch_ms BIGINT NOT NULL,
    PRIMARY KEY (source_attempt_id, review_task_id)
);
