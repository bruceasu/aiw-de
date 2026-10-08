package me.asu.aidev.execution.domain;

import me.asu.aidev.work.domain.TaskId;
import me.asu.aidev.worker.domain.WorkerId;
import java.time.Instant;
import java.util.Objects;

public record ExecutionAttempt(
        ExecutionAttemptId id,
        TaskId taskId,
        int attemptNo,
        int instructionRevision,
        WorkerId workerId,
        LeaseToken leaseToken,
        ExecutionAttemptState state,
        String externalSessionId,
        Instant createdAt,
        Instant startedAt,
        Instant finishedAt) {

    public ExecutionAttempt {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(taskId, "taskId");
        Objects.requireNonNull(workerId, "workerId");
        Objects.requireNonNull(leaseToken, "leaseToken");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(createdAt, "createdAt");
        if (attemptNo < 1) throw new IllegalArgumentException("attemptNo must be >= 1");
        if (instructionRevision < 1) throw new IllegalArgumentException("instructionRevision must be >= 1");
    }

    public ExecutionAttempt(ExecutionAttemptId id, TaskId taskId, int attemptNo, WorkerId workerId,
                            LeaseToken leaseToken, ExecutionAttemptState state, String externalSessionId,
                            Instant createdAt, Instant startedAt, Instant finishedAt) {
        this(id, taskId, attemptNo, 1, workerId, leaseToken, state, externalSessionId,
                createdAt, startedAt, finishedAt);
    }
}
