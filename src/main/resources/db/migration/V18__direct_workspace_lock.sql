-- Stable row mutex for direct-directory claims, including implicit child directories.
CREATE TABLE direct_workspace_lock (
    repository_ref VARCHAR(120) PRIMARY KEY
);
