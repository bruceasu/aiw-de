package me.asu.aidev.autonomous.domain;

/** Operator authority orthogonal to the autonomous engineering lifecycle. */
public enum AutonomousRunControlState {
    /** Normal worker claim/execution is allowed while lifecycle status also permits it. */
    ACTIVE,
    /** Operator-revoked execution; active leases are requeued and no new claims are allowed. */
    PAUSED,
    /** Irreversible operator cancellation; unfinished work is terminally cancelled. */
    CANCELLED
}
