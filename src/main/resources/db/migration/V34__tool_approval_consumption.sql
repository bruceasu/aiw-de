ALTER TABLE execution_tool_approval ADD COLUMN consumption_json TEXT;
ALTER TABLE execution_tool_approval ADD COLUMN start_digest VARCHAR(64);
ALTER TABLE execution_tool_approval ADD COLUMN consumed_task_id VARCHAR(128) REFERENCES execution_context(task_id);
ALTER TABLE execution_tool_approval ADD COLUMN consumed_ms BIGINT;
