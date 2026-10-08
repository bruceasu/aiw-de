package me.asu.aidev.webconsole.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import me.asu.aidev.fleet.domain.FleetDashboard;

/** Bounded browser view over a Fleet snapshot; the dashboard remains the authority for totals. */
public record WebConsoleFleetDetail(
        List<ReadyItem> readyTasks,
        List<LeaseItem> activeLeases) {
    private static final String RUN_SUBJECT = "AUTONOMOUS_REQUIREMENT";

    public static WebConsoleFleetDetail from(FleetDashboard fleet, Set<String> existingRunIds) {
        List<ReadyItem> ready = fleet.backlog().readyTasks().stream().map(task -> {
            String runId = runId(task.subjectType(), task.subjectId(), existingRunIds);
            List<String> blockers = new ArrayList<>();
            if (RUN_SUBJECT.equals(task.subjectType()) && runId == null) {
                blockers.add("Run unavailable");
            } else if (!task.policyAllowed()) {
                blockers.add("Run policy");
            }
            if (!task.admissionAllowed()) blockers.add("Run admission");
            if (task.compatibleActiveWorkers() == 0) blockers.add("No compatible active Worker");
            else if (task.claimableWorkers() == 0) blockers.add("Worker capacity");
            if (!task.fleetAdmissionAllowed()) blockers.add("Fleet admission");
            if (blockers.isEmpty()) blockers.add("Claimable in this snapshot");
            return new ReadyItem(task, runId, List.copyOf(blockers));
        }).toList();
        List<LeaseItem> leases = fleet.execution().activeLeases().stream()
                .map(lease -> new LeaseItem(lease, runId(lease.subjectType(), lease.subjectId(), existingRunIds)))
                .toList();
        return new WebConsoleFleetDetail(ready, leases);
    }

    private static String runId(String type, String id, Set<String> existingRunIds) {
        return RUN_SUBJECT.equals(type) && existingRunIds.contains(id) ? id : null;
    }

    public record ReadyItem(FleetDashboard.ReadyTaskSummary task, String runId, List<String> blockers) {}
    public record LeaseItem(FleetDashboard.ActiveLeaseSummary lease, String runId) {}
}
