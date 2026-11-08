ALTER TABLE execution_tool_approval ADD COLUMN successor_task_id VARCHAR(128) REFERENCES work_task(id);
CREATE UNIQUE INDEX idx_tool_approval_successor ON execution_tool_approval(successor_task_id) WHERE successor_task_id IS NOT NULL;
