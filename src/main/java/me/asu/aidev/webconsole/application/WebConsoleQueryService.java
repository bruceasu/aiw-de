package me.asu.aidev.webconsole.application;

import me.asu.aidev.autonomous.application.AutonomousRunDashboardService;
import me.asu.aidev.autonomous.application.AutonomousRunTimelineService;
import me.asu.aidev.autonomous.domain.AutonomousRunDashboard;
import me.asu.aidev.fleet.application.FleetAdmissionBudgetService;
import me.asu.aidev.fleet.application.FleetDashboardService;
import me.asu.aidev.fleet.domain.FleetAdmissionBudgetEvent;
import me.asu.aidev.fleet.domain.FleetDashboard;
import me.asu.aidev.webconsole.domain.WebConsoleApprovalSummary;
import me.asu.aidev.webconsole.domain.WebConsoleApprovalDetail;
import me.asu.aidev.webconsole.domain.WebConsoleRunSummary;
import me.asu.aidev.webconsole.domain.WebConsoleTaskSummary;
import me.asu.aidev.webconsole.domain.WebConsoleOverview;
import me.asu.aidev.webconsole.domain.WebConsoleFleetDetail;
import me.asu.aidev.webconsole.domain.WebConsoleOverview.RunCard;
import me.asu.aidev.webconsole.domain.WebConsoleOverview.WaitingGroup;
import me.asu.aidev.webconsole.jdbc.JdbcWebConsoleOverviewReadRepository;
import me.asu.aidev.webconsole.jdbc.JdbcWebConsoleReadRepository;
import me.asu.aidev.webconsole.jdbc.JdbcWebConsoleApprovalReadRepository;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.NoSuchElementException;
import java.util.Comparator;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;

/** Read-only facade for the R9 Web Console. */
@Service
public class WebConsoleQueryService {
    private final FleetDashboardService fleet;
    private final FleetAdmissionBudgetService fleetScheduling;
    private final AutonomousRunDashboardService runDashboards;
    private final AutonomousRunTimelineService timelines;
    private final JdbcWebConsoleReadRepository reads;
    private final JdbcWebConsoleOverviewReadRepository overviewReads;
    private final JdbcWebConsoleApprovalReadRepository approvalReads;

    public WebConsoleQueryService(FleetDashboardService fleet,
                                  FleetAdmissionBudgetService fleetScheduling,
                                  AutonomousRunDashboardService runDashboards,
                                  AutonomousRunTimelineService timelines,
                                  JdbcWebConsoleReadRepository reads,
                                  JdbcWebConsoleOverviewReadRepository overviewReads,
                                  JdbcWebConsoleApprovalReadRepository approvalReads) {
        this.fleet = fleet;
        this.fleetScheduling = fleetScheduling;
        this.runDashboards = runDashboards;
        this.timelines = timelines;
        this.reads = reads;
        this.overviewReads = overviewReads;
        this.approvalReads = approvalReads;
    }

    public FleetDashboard fleet() { return fleet.dashboard(100); }
    @Transactional(readOnly = true)
    public WebConsoleFleetDetail fleetDetail(FleetDashboard snapshot) {
        var runIds = Stream.concat(
                snapshot.backlog().readyTasks().stream().map(t -> new String[] {t.subjectType(), t.subjectId()}),
                snapshot.execution().activeLeases().stream().map(l -> new String[] {l.subjectType(), l.subjectId()}))
                .filter(subject -> "AUTONOMOUS_REQUIREMENT".equals(subject[0]) && subject[1] != null)
                .map(subject -> subject[1]).distinct().collect(Collectors.toList());
        return WebConsoleFleetDetail.from(snapshot, overviewReads.existingRunIds(runIds));
    }
    public List<FleetAdmissionBudgetEvent> fleetSchedulingEvents(int limit) { return fleetScheduling.recentEvents(limit); }
    public List<WebConsoleRunSummary> runs() { return reads.recentRuns(200); }
    public static final int APPROVAL_WINDOW = 100;
    public record ApprovalInbox(int pendingTotal, List<WebConsoleApprovalDetail> pending,
                                List<WebConsoleApprovalDetail> decided, int limit) {}
    @Transactional(readOnly = true)
    public ApprovalInbox approvalInbox() {
        return new ApprovalInbox(overviewReads.pendingApprovalCount(), approvalReads.pending(APPROVAL_WINDOW),
                approvalReads.decided(APPROVAL_WINDOW), APPROVAL_WINDOW);
    }
    public Optional<WebConsoleApprovalDetail> approvalDetail(String approvalRequestId) {
        return approvalReads.find(approvalRequestId);
    }
    public List<WebConsoleApprovalSummary> pendingApprovals() {
        return overviewReads.pendingApprovals(100);
    }
    @Transactional(readOnly = true)
    public WebConsoleOverview overview() {
        var snapshot = fleet();
        var active = snapshot.runs().activeRuns().stream().limit(12).toList();
        var attention = snapshot.runs().attentionRuns().stream().limit(8).toList();
        var ids = Stream.concat(active.stream(), attention.stream()).map(FleetDashboard.RunSummary::runId).distinct().toList();
        var contexts = overviewReads.runContexts(ids);
        java.util.function.Function<FleetDashboard.RunSummary, RunCard> card = run -> {
            var context = contexts.get(run.runId());
            return new RunCard(run, context == null || context.requirement().isBlank() ? "Requirement unavailable" : context.requirement(),
                    context == null ? 0 : context.completedTasks(), context == null ? null : context.failureReason());
        };
        var backlog = snapshot.backlog();
        var waiting = List.of(
                waiting("No matching capability", backlog.unmatchedReady(), backlog.readyTasks(), t -> t.policyAllowed() && t.admissionAllowed() && t.compatibleActiveWorkers() == 0),
                waiting("Worker capacity", backlog.capacityBlockedReady(), backlog.readyTasks(), t -> t.policyAllowed() && t.admissionAllowed() && t.compatibleActiveWorkers() > 0 && t.claimableWorkers() == 0),
                waiting("Run admission", backlog.admissionBlockedReady(), backlog.readyTasks(), t -> t.policyAllowed() && !t.admissionAllowed()),
                waiting("Fleet admission", backlog.fleetAdmissionBlockedReady(), backlog.readyTasks(), t -> t.policyAllowed() && t.admissionAllowed() && t.compatibleActiveWorkers() > 0 && t.claimableWorkers() > 0 && !t.fleetAdmissionAllowed()),
                waiting("Policy", backlog.policyBlockedReady(), backlog.readyTasks(), t -> !t.policyAllowed()));
        var workers = snapshot.workers().workers().stream().sorted(Comparator
                .comparingInt((FleetDashboard.WorkerSummary w) -> w.activeLeases() > 0 ? 0 : "ACTIVE".equals(w.status()) ? 1 : 2)
                .thenComparing(FleetDashboard.WorkerSummary::workerId)).limit(8).toList();
        return new WebConsoleOverview(snapshot, overviewReads.activeRunCount(), active.stream().map(card).toList(),
                attention.stream().map(card).toList(), overviewReads.pendingApprovalCount(),
                overviewReads.pendingApprovals(8), workers, waiting, overviewReads.recentActivity(20));
    }
    private static WaitingGroup waiting(String label, int total, List<FleetDashboard.ReadyTaskSummary> tasks,
                                        Predicate<FleetDashboard.ReadyTaskSummary> condition) {
        return new WaitingGroup(label, total, tasks.stream().filter(condition).limit(5).toList());
    }
    public List<WebConsoleTaskSummary> tasks(String runId) { return reads.tasksForRun(runId); }
    public AutonomousRunDashboard runDashboard(String runId) {
        return runDashboards.find(runId).orElseThrow(() -> new NoSuchElementException("autonomous run not found: " + runId));
    }
    public AutonomousRunTimelineService.TimelineResult timeline(String runId, int limit) {
        return timelines.get(runId, limit);
    }
}
