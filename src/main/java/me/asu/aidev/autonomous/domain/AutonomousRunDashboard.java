package me.asu.aidev.autonomous.domain;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Read-only operational projection for one autonomous requirement run. */
public record AutonomousRunDashboard(
        String runId,
        String lifecycleStatus,
        String controlState,
        String effectiveStatus,
        AutonomousRunPhase phase,
        boolean terminal,
        Instant startedAt,
        Instant statusUpdatedAt,
        Instant completedAt,
        long elapsedMillis,
        TaskProgress tasks,
        ExecutionProgress execution,
        ApprovalProgress approval,
        SchedulingProgress scheduling,
        int failureCount,
        int recoveryCount,
        int pauseCount,
        int resumeCount,
        String lastFailureReason) {

    public record TaskProgress(
            int total,
            int blocked,
            int ready,
            int claimed,
            int running,
            int completed,
            int failed,
            int cancelled,
            Map<String, Integer> byType) {}

    public record ExecutionProgress(
            int attempts,
            int retries,
            int failedAttempts,
            int expiredAttempts,
            int currentParallelism,
            int peakParallelism,
            List<ActiveExecution> active) {}

    public record ActiveExecution(
            String taskId,
            String taskType,
            String state,
            String workerId,
            int attemptNo,
            Instant startedAt,
            Instant leaseExpiresAt) {}

    public record SchedulingProgress(
            int maxParallelism,
            int activeSlots,
            Integer availableSlots,
            int overcommittedSlots,
            int accountingDelta) {}

    public record ApprovalProgress(
            String requestId,
            String status,
            String requestedBy,
            String decidedBy,
            Instant requestedAt,
            Instant decidedAt) {}
}
