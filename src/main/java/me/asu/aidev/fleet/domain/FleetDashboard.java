package me.asu.aidev.fleet.domain;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Read-only fleet/backlog projection for the whole AI workforce. */
public record FleetDashboard(
        Instant capturedAt,
        WorkerFleet workers,
        AdmissionBudget admission,
        Backlog backlog,
        RunFleet runs,
        ExecutionFleet execution) {


    public record AdmissionBudget(
            int maxParallelism,
            int activeLeases,
            Integer availableSlots,
            int overcommittedSlots,
            boolean saturated,
            Instant updatedAt) {}

    public record WorkerFleet(
            int total,
            int active,
            int draining,
            int offline,
            int registeredCapacity,
            int claimableCapacity,
            int activeLeases,
            int availableClaimCapacity,
            Map<String, Integer> byRuntimeType,
            List<WorkerSummary> workers) {}

    public record WorkerSummary(
            String workerId,
            String runtimeType,
            String status,
            Set<String> capabilities,
            int maxConcurrency,
            int activeLeases,
            int availableCapacity,
            Instant lastHeartbeatAt) {}

    public record Backlog(
            int unfinished,
            int blocked,
            int ready,
            int claimed,
            int running,
            int failed,
            int claimableReady,
            int unmatchedReady,
            int capacityBlockedReady,
            int admissionBlockedReady,
            int fleetAdmissionBlockedReady,
            int policyBlockedReady,
            Map<String, Integer> readyByType,
            Map<String, Integer> readyCapabilityDemand,
            List<ReadyTaskSummary> readyTasks) {}

    public record ReadyTaskSummary(
            String taskId,
            String taskType,
            String subjectType,
            String subjectId,
            Set<String> requiredCapabilities,
            int priority,
            int compatibleActiveWorkers,
            int claimableWorkers,
            boolean admissionAllowed,
            boolean fleetAdmissionAllowed,
            boolean policyAllowed,
            Instant createdAt) {}

    public record RunFleet(
            int total,
            int lifecycleRunning,
            int engineeringPassed,
            int waitingApproval,
            int succeeded,
            int failed,
            int rejected,
            int paused,
            int cancelled,
            Map<String, Integer> byPhase,
            List<RunSummary> activeRuns,
            List<RunSummary> attentionRuns) {}

    public record RunSummary(
            String runId,
            String lifecycleStatus,
            String controlState,
            String effectiveStatus,
            String phase,
            int totalTasks,
            int readyTasks,
            int activeTasks,
            int failedTasks,
            int maxParallelism,
            int activeSlots,
            Integer availableSlots,
            int overcommittedSlots,
            Instant startedAt,
            Instant statusUpdatedAt) {}

    public record ExecutionFleet(
            int leasedTasks,
            int claimed,
            int running,
            int currentParallelism,
            int workerLeaseAccountingDelta,
            int runSlotAccountingDelta,
            List<ActiveLeaseSummary> activeLeases) {}

    public record ActiveLeaseSummary(
            String taskId,
            String taskType,
            String subjectType,
            String subjectId,
            String workerId,
            String state,
            int attemptNo,
            Instant leaseExpiresAt) {}
}
