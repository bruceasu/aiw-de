package me.asu.aidev.work.domain;

public enum TaskState {
    BLOCKED,
    READY,
    CLAIMED,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED
}
