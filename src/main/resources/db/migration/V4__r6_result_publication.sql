-- R6-S1: result publication metadata stays on execution_attempt.
-- No new aggregate/table is introduced.
ALTER TABLE execution_attempt ADD COLUMN publication_ref VARCHAR(2048);
