package me.asu.aidev.work.domain;

public enum TaskState {
    BLOCKED,
    READY,
    CLAIMED,
    RUNNING,
    YIELDING,
    WAITING_INPUT,
    CONTINUED,
    COMPLETED,
    FAILED,
    CANCELLED
}
