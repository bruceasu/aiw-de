package me.asu.aidev.webconsole.domain;

import me.asu.aidev.fleet.domain.FleetDashboard;
import java.time.Instant;
import java.util.List;

/** Private browser projection: aggregate counts and bounded details are distinct. */
public record WebConsoleOverview(
        FleetDashboard fleet,
        int activeRunCount,
        List<RunCard> activeRuns,
        List<RunCard> attentionRuns,
        int pendingApprovalCount,
        List<WebConsoleApprovalSummary> pendingApprovals,
        List<FleetDashboard.WorkerSummary> workers,
        List<WaitingGroup> waitingGroups,
        List<Activity> activities) {

    public record RunCard(FleetDashboard.RunSummary run, String requirement,
                          int completedTasks, String failureReason) {}
    public record WaitingGroup(String label, int total, List<FleetDashboard.ReadyTaskSummary> tasks) {}
    public record Activity(Instant occurredAt, String runId, String eventType,
                           String taskId, Integer attemptNo, String workerId,
                           String detail, String sourceKey) {}
}
