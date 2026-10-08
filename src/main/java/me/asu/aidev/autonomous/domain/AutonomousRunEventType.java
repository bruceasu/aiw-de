package me.asu.aidev.autonomous.domain;

/** Run-level history that cannot be reconstructed from current-state authority rows. */
public enum AutonomousRunEventType {
    FAILED,
    RECOVERED,
    PAUSED,
    RESUMED,
    CANCELLED,
    MAX_PARALLELISM_CHANGED
}
