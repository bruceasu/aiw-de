-- R8-S5: operator control is orthogonal to autonomous lifecycle status.
-- ACTIVE/PAUSED/CANCELLED remains on the existing autonomous_run authority row;
-- no separate operator-control table is required for current-state authority.
ALTER TABLE autonomous_run ADD COLUMN control_state VARCHAR(32) NOT NULL DEFAULT 'ACTIVE';
ALTER TABLE autonomous_run ADD COLUMN pause_count INTEGER NOT NULL DEFAULT 0;
ALTER TABLE autonomous_run ADD COLUMN resume_count INTEGER NOT NULL DEFAULT 0;
ALTER TABLE autonomous_run ADD COLUMN last_control_action VARCHAR(32);
ALTER TABLE autonomous_run ADD COLUMN last_controlled_by VARCHAR(256);
ALTER TABLE autonomous_run ADD COLUMN last_control_reason VARCHAR(4096);
ALTER TABLE autonomous_run ADD COLUMN last_controlled_at_epoch_ms BIGINT;
ALTER TABLE autonomous_run ADD COLUMN cancelled_at_epoch_ms BIGINT;

-- Active attempts revoked by pause/cancel use the existing terminal EXPIRED state.
-- termination_reason distinguishes natural lease expiry from explicit operator revocation.
ALTER TABLE execution_attempt ADD COLUMN termination_reason VARCHAR(64);

CREATE INDEX idx_autonomous_run_control
    ON autonomous_run(control_state, status, status_updated_at_epoch_ms);
