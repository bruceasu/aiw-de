CREATE TABLE platform_skill_version (
    actor VARCHAR(256) NOT NULL,
    skill_ref VARCHAR(96) NOT NULL,
    version INTEGER NOT NULL CHECK (version > 0),
    enabled BOOLEAN NOT NULL,
    title VARCHAR(160) NOT NULL,
    content TEXT NOT NULL,
    digest VARCHAR(64) NOT NULL,
    created_ms BIGINT NOT NULL,
    PRIMARY KEY (actor, skill_ref, version)
);
