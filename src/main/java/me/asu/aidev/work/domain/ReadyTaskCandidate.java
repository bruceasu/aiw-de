package me.asu.aidev.work.domain;

import java.util.Objects;

/** READY task plus the current active lease count for its scheduling subject. */
public record ReadyTaskCandidate(WorkTask task, int activeSubjectLeases) {
    public ReadyTaskCandidate {
        Objects.requireNonNull(task, "task");
        if (task.state() != TaskState.READY) throw new IllegalArgumentException("candidate task must be READY");
        if (activeSubjectLeases < 0) throw new IllegalArgumentException("activeSubjectLeases must be >= 0");
    }
}
