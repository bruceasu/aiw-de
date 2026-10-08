package me.asu.aidev.worker.runtime;

public enum WorkerRuntimeState {
    NEW,
    RUNNING,
    DRAINING,
    STOPPED,
    CRASHED
}
