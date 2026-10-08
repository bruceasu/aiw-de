-- R4 adds executable task instructions without introducing a new aggregate/table.
ALTER TABLE work_task ADD COLUMN instruction TEXT NOT NULL DEFAULT '';
