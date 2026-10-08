package me.asu.aidev.autonomous.domain;

/** Authoritative lifecycle of one autonomous requirement execution. */
public enum AutonomousRunStatus {
    /** Work is still progressing or a failed task has explicitly been recovered for another attempt. */
    RUNNING,
    /** Engineering gate passed, but no human approval request has been materialized yet. */
    ENGINEERING_PASSED,
    /** A durable human approval request exists and is still pending. */
    WAITING_APPROVAL,
    /** Human approval accepted the exact gated result. */
    SUCCEEDED,
    /** A worker explicitly failed one task; the run requires explicit recovery. */
    FAILED,
    /** Human approval rejected the exact gated result. */
    REJECTED
}
