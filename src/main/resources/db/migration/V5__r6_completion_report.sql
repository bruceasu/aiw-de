-- R6-S3: semantic review/verification result remains part of the execution attempt.
-- No new table is introduced.
ALTER TABLE execution_attempt ADD COLUMN completion_verdict VARCHAR(32);
ALTER TABLE execution_attempt ADD COLUMN completion_summary TEXT;
