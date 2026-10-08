-- R6-S4: durable final engineering gate attached to the exact completed code-change attempt.
-- No new table is introduced. A later REPAIR has its own attempt and therefore its own gate.
ALTER TABLE execution_attempt ADD COLUMN engineering_gate_status VARCHAR(32);
ALTER TABLE execution_attempt ADD COLUMN engineering_gate_review_task_id VARCHAR(128);
ALTER TABLE execution_attempt ADD COLUMN engineering_gate_verification_task_id VARCHAR(128);
ALTER TABLE execution_attempt ADD COLUMN engineering_gate_closed_at_epoch_ms BIGINT;
