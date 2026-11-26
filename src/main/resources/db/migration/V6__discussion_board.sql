CREATE TABLE discussion_thread (
    id VARCHAR(36) PRIMARY KEY,
    actor VARCHAR(256) NOT NULL,
    wish_id VARCHAR(80),
    title VARCHAR(160) NOT NULL,
    closed BOOLEAN NOT NULL DEFAULT FALSE,
    secretary_id VARCHAR(128),
    updated_ms BIGINT NOT NULL,
    UNIQUE (actor, wish_id)
);
CREATE INDEX discussion_thread_actor ON discussion_thread(actor, updated_ms);

CREATE TABLE discussion_post (
    thread_id VARCHAR(36) NOT NULL REFERENCES discussion_thread(id),
    id VARCHAR(100) NOT NULL,
    author_role VARCHAR(16) NOT NULL CHECK (author_role IN ('USER','MANAGER','EMPLOYEE','SECRETARY')),
    body TEXT NOT NULL,
    created_ms BIGINT NOT NULL,
    PRIMARY KEY (thread_id, id)
);
CREATE INDEX discussion_post_thread ON discussion_post(thread_id, created_ms, id);
