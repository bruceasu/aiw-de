package me.asu.aidev.autonomous.domain;

import me.asu.aidev.approval.domain.ApprovalRequestId;
import me.asu.aidev.work.domain.TaskId;
import java.time.Instant;
import java.util.Objects;

/** Current authoritative state for one autonomous requirement run. */
public record AutonomousRun(
        String id,
        TaskId requirementTaskId,
        AutonomousRunStatus status,
        AutonomousRunControlState controlState,
        TaskId terminalSourceTaskId,
        String resultRevision,
        ApprovalRequestId approvalRequestId,
        TaskId lastFailedTaskId,
        String lastFailureReason,
        int failureCount,
        int recoveryCount,
        String lastRecoveredBy,
        Instant lastRecoveredAt,
        int pauseCount,
        int resumeCount,
        String lastControlAction,
        String lastControlledBy,
        String lastControlReason,
        Instant lastControlledAt,
        Instant cancelledAt,
        int maxParallelism,
        int activeSlots,
        Instant startedAt,
        Instant statusUpdatedAt,
        Instant completedAt) {

    public AutonomousRun {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("run id is blank");
        Objects.requireNonNull(requirementTaskId, "requirementTaskId");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(controlState, "controlState");
        if (failureCount < 0) throw new IllegalArgumentException("failureCount < 0");
        if (recoveryCount < 0) throw new IllegalArgumentException("recoveryCount < 0");
        if (pauseCount < 0) throw new IllegalArgumentException("pauseCount < 0");
        if (resumeCount < 0) throw new IllegalArgumentException("resumeCount < 0");
        if (maxParallelism < 0 || maxParallelism > 1024) {
            throw new IllegalArgumentException("maxParallelism must be between 0 and 1024");
        }
        if (activeSlots < 0) throw new IllegalArgumentException("activeSlots < 0");
        // activeSlots may temporarily exceed maxParallelism after a runtime contraction.
        // Existing leases are allowed to drain naturally; admission blocks all new claims until
        // activeSlots falls below the configured limit.
        Objects.requireNonNull(startedAt, "startedAt");
        Objects.requireNonNull(statusUpdatedAt, "statusUpdatedAt");
        id = id.strip();
        resultRevision = normalize(resultRevision);
        lastFailureReason = normalize(lastFailureReason);
        lastRecoveredBy = normalize(lastRecoveredBy);
        lastControlAction = normalize(lastControlAction);
        lastControlledBy = normalize(lastControlledBy);
        lastControlReason = normalize(lastControlReason);
    }

    public boolean terminal() {
        return controlState == AutonomousRunControlState.CANCELLED
                || status == AutonomousRunStatus.SUCCEEDED
                || status == AutonomousRunStatus.REJECTED;
    }

    public boolean executionAllowed() {
        return controlState == AutonomousRunControlState.ACTIVE && status == AutonomousRunStatus.RUNNING;
    }

    public boolean concurrencyAvailable() {
        return maxParallelism == 0 || activeSlots < maxParallelism;
    }

    public int availableSlots() {
        return maxParallelism == 0 ? Integer.MAX_VALUE : Math.max(0, maxParallelism - activeSlots);
    }

    public int overcommittedSlots() {
        return maxParallelism == 0 ? 0 : Math.max(0, activeSlots - maxParallelism);
    }

    private static String normalize(String value) {
        if (value == null) return null;
        String v = value.strip();
        return v.isEmpty() ? null : v;
    }
}
