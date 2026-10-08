package me.asu.aidev.execution.domain;

public enum ExecutionAttemptState {
    CLAIMED,
    RUNNING,
    COMPLETED,
    FAILED,
    EXPIRED
}
