CREATE TABLE workspace_registry (
    id VARCHAR(80) PRIMARY KEY,
    display_name VARCHAR(160) NOT NULL,
    root_path VARCHAR(2048) NOT NULL,
    target_mode VARCHAR(16) NOT NULL,
    enabled INTEGER NOT NULL DEFAULT 1,
    created_at_epoch_ms BIGINT NOT NULL,
    CHECK (target_mode IN ('ROOT','CHILD')),
    CHECK (enabled IN (0,1))
);

CREATE TABLE workspace_repo_registry (
    repository_ref VARCHAR(120) PRIMARY KEY,
    workspace_id VARCHAR(80) NOT NULL,
    repo_id VARCHAR(80) NOT NULL,
    relative_path VARCHAR(1024) NOT NULL,
    git_warning VARCHAR(2048),
    enabled INTEGER NOT NULL DEFAULT 1,
    FOREIGN KEY (workspace_id) REFERENCES workspace_registry(id),
    UNIQUE (workspace_id, repo_id),
    CHECK (enabled IN (0,1))
);

CREATE INDEX idx_workspace_repo_workspace ON workspace_repo_registry(workspace_id);
