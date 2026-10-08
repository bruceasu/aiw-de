package me.asu.aidev.execution.domain;

import me.asu.aidev.work.domain.TaskId;
import me.asu.aidev.worker.domain.WorkerId;
import java.time.Instant;
import java.util.Objects;

public record TaskLease(
        TaskId taskId,
        WorkerId workerId,
        LeaseToken token,
        Instant acquiredAt,
        Instant expiresAt,
        Instant lastHeartbeatAt) {

    public TaskLease {
        Objects.requireNonNull(taskId, "taskId");
        Objects.requireNonNull(workerId, "workerId");
        Objects.requireNonNull(token, "token");
        Objects.requireNonNull(acquiredAt, "acquiredAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
        Objects.requireNonNull(lastHeartbeatAt, "lastHeartbeatAt");
        if (!expiresAt.isAfter(acquiredAt)) throw new IllegalArgumentException("lease expiry must be after acquisition");
    }
}
