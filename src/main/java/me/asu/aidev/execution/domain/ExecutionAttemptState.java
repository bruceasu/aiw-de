package me.asu.aidev.execution.domain;

public enum ExecutionAttemptState {
    CLAIMED,
    RUNNING,
    YIELDED,
    COMPLETED,
    FAILED,
    EXPIRED
}
