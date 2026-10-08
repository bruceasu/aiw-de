-- R8-S10: per-autonomous-run concurrency budget and atomic admission accounting.
-- max_parallelism=0 means unlimited. active_slots is a capacity ledger analogous to worker.active_leases.
ALTER TABLE autonomous_run ADD COLUMN max_parallelism INTEGER NOT NULL DEFAULT 0 CHECK (max_parallelism >= 0);
ALTER TABLE autonomous_run ADD COLUMN active_slots INTEGER NOT NULL DEFAULT 0 CHECK (active_slots >= 0);

-- Preserve lease/accounting authority when upgrading a database that was stopped with claimed/running work.
UPDATE autonomous_run
   SET active_slots=(
       SELECT COUNT(*)
         FROM work_task wt
        WHERE wt.subject_type='AUTONOMOUS_REQUIREMENT'
          AND wt.subject_id=autonomous_run.id
          AND wt.state IN ('CLAIMED','RUNNING')
   );

CREATE INDEX idx_autonomous_run_admission
    ON autonomous_run(control_state, status, max_parallelism, active_slots);
