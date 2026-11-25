CREATE TABLE shared_tool (
    id VARCHAR(64) PRIMARY KEY,
    revision BIGINT NOT NULL CHECK (revision > 0),
    kind VARCHAR(8) NOT NULL CHECK (kind IN ('MCP', 'HTTP')),
    title VARCHAR(160) NOT NULL,
    description VARCHAR(2000) NOT NULL,
    endpoint VARCHAR(2048) NOT NULL,
    operation VARCHAR(120) NOT NULL,
    http_method VARCHAR(8),
    input_schema TEXT NOT NULL,
    enabled BOOLEAN NOT NULL,
    updated_ms BIGINT NOT NULL
);
