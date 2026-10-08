package me.asu.aidev.execution.domain;

import me.asu.aidev.work.domain.TaskId;
import me.asu.aidev.worker.domain.WorkerId;
import java.util.Objects;

/**
 * Minimal ownership identity for a lease selected by the recovery path.
 *
 * <p>This deliberately does not carry active-lease timing invariants. Recovery only needs
 * the task, worker, and token to perform compare-and-set requeue and attempt expiration.
 */
public record ExpiredLease(
        TaskId taskId,
        WorkerId workerId,
        LeaseToken token) {

    public ExpiredLease {
        Objects.requireNonNull(taskId, "taskId");
        Objects.requireNonNull(workerId, "workerId");
        Objects.requireNonNull(token, "token");
    }
}
