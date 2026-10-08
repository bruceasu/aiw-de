package me.asu.aidev.autonomous.domain;

/** Human-oriented progress phase projected from authoritative task/run state. */
public enum AutonomousRunPhase {
    REQUIREMENT_ANALYSIS,
    SOLUTION_ARCHITECTURE,
    IMPLEMENTATION_PLANNING,
    IMPLEMENTATION,
    INTEGRATION,
    REVIEW_VERIFICATION,
    REPAIR,
    ENGINEERING_GATE,
    APPROVAL,
    FAILED,
    PAUSED,
    CANCELLED,
    COMPLETED
}
