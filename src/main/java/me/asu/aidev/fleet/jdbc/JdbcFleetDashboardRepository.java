package me.asu.aidev.fleet.jdbc;

import me.asu.aidev.autonomous.domain.AutonomousRunControlState;
import me.asu.aidev.autonomous.domain.AutonomousRunPhase;
import me.asu.aidev.autonomous.domain.AutonomousRunStatus;
import me.asu.aidev.fleet.domain.FleetAdmissionBudget;
import me.asu.aidev.fleet.domain.FleetDashboard;
import me.asu.aidev.fleet.port.FleetAdmissionBudgetRepository;
import me.asu.aidev.fleet.port.FleetDashboardRepository;
import me.asu.aidev.work.domain.CapabilitySetCodec;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcFleetDashboardRepository implements FleetDashboardRepository {
    private static final String AUTONOMOUS_SUBJECT = "AUTONOMOUS_REQUIREMENT";
    private final JdbcTemplate jdbc;
    private final FleetAdmissionBudgetRepository admissionBudgets;

    public JdbcFleetDashboardRepository(JdbcTemplate jdbc, FleetAdmissionBudgetRepository admissionBudgets) {
        this.jdbc = jdbc;
        this.admissionBudgets = admissionBudgets;
    }

    @Override
    public FleetDashboard project(int detailLimit) {
        Instant capturedAt = Instant.now();
        List<WorkerRow> workerRows = workers();
        List<TaskRow> unfinishedTasks = unfinishedTasks();
        FleetAdmissionBudget admissionBudget = admissionBudgets.snapshot();
        RunCounts runCounts = runCounts();
        List<RunRow> liveRuns = liveRuns();
        Map<String, RunTaskStats> runTaskStats = runTaskStats();

        FleetDashboard.WorkerFleet workers = workerFleet(workerRows, detailLimit);
        FleetDashboard.AdmissionBudget admission = new FleetDashboard.AdmissionBudget(
                admissionBudget.maxParallelism(), admissionBudget.activeLeases(), admissionBudget.availableSlots(),
                admissionBudget.overcommittedSlots(), admissionBudget.saturated(), admissionBudget.updatedAt());
        FleetDashboard.Backlog backlog = backlog(unfinishedTasks, workerRows, admissionBudget, detailLimit);
        FleetDashboard.RunFleet runs = runFleet(runCounts, liveRuns, runTaskStats, detailLimit);
        FleetDashboard.ExecutionFleet execution = executionFleet(unfinishedTasks, workers.activeLeases(), runCounts.activeSlots(), detailLimit);
        return new FleetDashboard(capturedAt, workers, admission, backlog, runs, execution);
    }

    private List<WorkerRow> workers() {
        return jdbc.query("""
                SELECT id,runtime_type,capabilities,max_concurrency,active_leases,status,
                       last_heartbeat_at_epoch_ms
                  FROM worker
                 ORDER BY id
                """, (rs, n) -> new WorkerRow(
                rs.getString("id"), rs.getString("runtime_type"), CapabilitySetCodec.decode(rs.getString("capabilities")),
                rs.getInt("max_concurrency"), rs.getInt("active_leases"), rs.getString("status"),
                Instant.ofEpochMilli(rs.getLong("last_heartbeat_at_epoch_ms"))));
    }

    private List<TaskRow> unfinishedTasks() {
        return jdbc.query("""
                SELECT wt.id,wt.task_type,wt.state,wt.subject_type,wt.subject_id,
                       wt.required_capabilities,wt.priority,wt.current_worker_id,wt.attempt_no,
                       wt.lease_expires_at_epoch_ms,wt.created_at_epoch_ms,
                       ar.status AS run_status,ar.control_state AS run_control_state,
                       ar.max_parallelism AS run_max_parallelism,ar.active_slots AS run_active_slots
                  FROM work_task wt
                  LEFT JOIN autonomous_run ar
                    ON wt.subject_type='AUTONOMOUS_REQUIREMENT' AND ar.id=wt.subject_id
                 WHERE wt.state NOT IN ('COMPLETED','CANCELLED')
                 ORDER BY wt.priority DESC,wt.created_at_epoch_ms,wt.id
                """, (rs, n) -> new TaskRow(
                rs.getString("id"), rs.getString("task_type"), rs.getString("state"),
                rs.getString("subject_type"), rs.getString("subject_id"),
                CapabilitySetCodec.decode(rs.getString("required_capabilities")), rs.getInt("priority"),
                rs.getString("current_worker_id"), rs.getInt("attempt_no"), instant(rs, "lease_expires_at_epoch_ms"),
                Instant.ofEpochMilli(rs.getLong("created_at_epoch_ms")), rs.getString("run_status"),
                rs.getString("run_control_state"), rs.getInt("run_max_parallelism"), rs.getInt("run_active_slots")));
    }

    private RunCounts runCounts() {
        Map<String, Integer> lifecycle = new LinkedHashMap<>();
        Map<String, Integer> control = new LinkedHashMap<>();
        int total = 0;
        int activeSlots = 0;
        for (CountRow row : jdbc.query("""
                SELECT status,control_state,COUNT(*) AS n,COALESCE(SUM(active_slots),0) AS active_slots
                  FROM autonomous_run
                 GROUP BY status,control_state
                """, (rs, n) -> new CountRow(rs.getString("status"), rs.getString("control_state"), rs.getInt("n"), rs.getInt("active_slots")))) {
            total += row.count();
            lifecycle.merge(row.status(), row.count(), Integer::sum);
            control.merge(row.controlState(), row.count(), Integer::sum);
            activeSlots += row.activeSlots();
        }
        return new RunCounts(total, lifecycle, control, activeSlots);
    }

    private List<RunRow> liveRuns() {
        return jdbc.query("""
                SELECT id,status,control_state,max_parallelism,active_slots,started_at_epoch_ms,status_updated_at_epoch_ms
                  FROM autonomous_run
                 WHERE control_state <> 'CANCELLED' AND status NOT IN ('SUCCEEDED','REJECTED')
                 ORDER BY status_updated_at_epoch_ms DESC,id
                """, (rs, n) -> new RunRow(
                rs.getString("id"), AutonomousRunStatus.valueOf(rs.getString("status")),
                AutonomousRunControlState.valueOf(rs.getString("control_state")),
                rs.getInt("max_parallelism"), rs.getInt("active_slots"),
                Instant.ofEpochMilli(rs.getLong("started_at_epoch_ms")),
                Instant.ofEpochMilli(rs.getLong("status_updated_at_epoch_ms"))));
    }

    private Map<String, RunTaskStats> runTaskStats() {
        Map<String, RunTaskStats> result = new LinkedHashMap<>();
        List<RunTaskCountRow> rows = jdbc.query("""
                SELECT wt.subject_id,wt.task_type,wt.state,COUNT(*) AS n
                  FROM work_task wt
                  JOIN autonomous_run ar ON ar.id=wt.subject_id
                 WHERE wt.subject_type='AUTONOMOUS_REQUIREMENT'
                   AND ar.control_state <> 'CANCELLED'
                   AND ar.status NOT IN ('SUCCEEDED','REJECTED')
                 GROUP BY wt.subject_id,wt.task_type,wt.state
                """, (rs, n) -> new RunTaskCountRow(
                rs.getString("subject_id"), rs.getString("task_type"), rs.getString("state"), rs.getInt("n")));
        for (RunTaskCountRow row : rows) {
            result.computeIfAbsent(row.runId(), ignored -> new RunTaskStats())
                    .add(row.taskType(), row.state(), row.count());
        }
        return result;
    }

    private static FleetDashboard.WorkerFleet workerFleet(List<WorkerRow> rows, int detailLimit) {
        int active = 0, draining = 0, offline = 0;
        int registeredCapacity = 0, claimableCapacity = 0, activeLeases = 0, available = 0;
        Map<String, Integer> byRuntime = new LinkedHashMap<>();
        List<FleetDashboard.WorkerSummary> summaries = new ArrayList<>();
        for (WorkerRow row : rows) {
            registeredCapacity += row.maxConcurrency();
            activeLeases += row.activeLeases();
            byRuntime.merge(row.runtimeType(), 1, Integer::sum);
            switch (row.status()) {
                case "ACTIVE" -> {
                    active++;
                    claimableCapacity += row.maxConcurrency();
                    available += Math.max(0, row.maxConcurrency() - row.activeLeases());
                }
                case "DRAINING" -> draining++;
                case "OFFLINE" -> offline++;
                default -> throw new IllegalStateException("unknown worker status: " + row.status());
            }
            summaries.add(new FleetDashboard.WorkerSummary(
                    row.id(), row.runtimeType(), row.status(), row.capabilities(), row.maxConcurrency(), row.activeLeases(),
                    "ACTIVE".equals(row.status()) ? Math.max(0, row.maxConcurrency() - row.activeLeases()) : 0,
                    row.lastHeartbeatAt()));
        }
        summaries.sort(Comparator.comparingInt((FleetDashboard.WorkerSummary w) -> workerStatusRank(w.status()))
                .thenComparing(FleetDashboard.WorkerSummary::workerId));
        return new FleetDashboard.WorkerFleet(
                rows.size(), active, draining, offline, registeredCapacity, claimableCapacity, activeLeases, available,
                Map.copyOf(byRuntime), limited(summaries, detailLimit));
    }

    private static FleetDashboard.Backlog backlog(List<TaskRow> tasks, List<WorkerRow> workers,
                                                    FleetAdmissionBudget fleetAdmission, int detailLimit) {
        int blocked = 0, ready = 0, claimed = 0, running = 0, failed = 0;
        int claimableReady = 0, unmatchedReady = 0, capacityBlockedReady = 0, admissionBlockedReady = 0;
        int fleetAdmissionBlockedReady = 0, policyBlockedReady = 0;
        Map<String, Integer> readyByType = new LinkedHashMap<>();
        Map<String, Integer> capabilityDemand = new LinkedHashMap<>();
        List<FleetDashboard.ReadyTaskSummary> readyTasks = new ArrayList<>();

        List<WorkerRow> activeWorkers = workers.stream().filter(w -> "ACTIVE".equals(w.status())).toList();
        for (TaskRow task : tasks) {
            switch (task.state()) {
                case "BLOCKED" -> blocked++;
                case "READY" -> ready++;
                case "CLAIMED" -> claimed++;
                case "RUNNING" -> running++;
                case "FAILED" -> failed++;
                default -> throw new IllegalStateException("unexpected unfinished task state: " + task.state());
            }
            if (!"READY".equals(task.state())) continue;

            readyByType.merge(task.taskType(), 1, Integer::sum);
            task.requiredCapabilities().forEach(capability -> capabilityDemand.merge(capability, 1, Integer::sum));

            boolean policyAllowed = policyAllowed(task);
            boolean admissionAllowed = admissionAllowed(task);
            boolean fleetAdmissionAllowed = !fleetAdmission.saturated();
            int compatibleWorkers = 0;
            int claimableWorkers = 0;
            for (WorkerRow worker : activeWorkers) {
                if (!worker.supports(task.requiredCapabilities())) continue;
                compatibleWorkers++;
                if (worker.activeLeases() < worker.maxConcurrency()) claimableWorkers++;
            }

            if (!policyAllowed) policyBlockedReady++;
            else if (!admissionAllowed) admissionBlockedReady++;
            else if (compatibleWorkers == 0) unmatchedReady++;
            else if (claimableWorkers == 0) capacityBlockedReady++;
            else if (!fleetAdmissionAllowed) fleetAdmissionBlockedReady++;
            else claimableReady++;

            readyTasks.add(new FleetDashboard.ReadyTaskSummary(
                    task.id(), task.taskType(), task.subjectType(), task.subjectId(), task.requiredCapabilities(), task.priority(),
                    compatibleWorkers, claimableWorkers, admissionAllowed, fleetAdmissionAllowed, policyAllowed, task.createdAt()));
        }

        readyTasks.sort(Comparator.comparingInt(FleetDashboard.ReadyTaskSummary::priority).reversed()
                .thenComparing(FleetDashboard.ReadyTaskSummary::createdAt)
                .thenComparing(FleetDashboard.ReadyTaskSummary::taskId));
        return new FleetDashboard.Backlog(
                tasks.size(), blocked, ready, claimed, running, failed,
                claimableReady, unmatchedReady, capacityBlockedReady, admissionBlockedReady, fleetAdmissionBlockedReady, policyBlockedReady,
                Map.copyOf(readyByType), Map.copyOf(capabilityDemand), limited(readyTasks, detailLimit));
    }

    private static FleetDashboard.RunFleet runFleet(
            RunCounts counts, List<RunRow> liveRuns, Map<String, RunTaskStats> taskStats, int detailLimit) {
        Map<String, Integer> byPhase = new LinkedHashMap<>();
        int cancelled = counts.control().getOrDefault("CANCELLED", 0);
        int completed = counts.lifecycle().getOrDefault("SUCCEEDED", 0) + counts.lifecycle().getOrDefault("REJECTED", 0);
        if (cancelled > 0) byPhase.put(AutonomousRunPhase.CANCELLED.name(), cancelled);
        if (completed > 0) byPhase.put(AutonomousRunPhase.COMPLETED.name(), completed);

        List<FleetDashboard.RunSummary> activeRuns = new ArrayList<>();
        List<FleetDashboard.RunSummary> attentionRuns = new ArrayList<>();
        for (RunRow run : liveRuns) {
            RunTaskStats stats = taskStats.getOrDefault(run.id(), new RunTaskStats());
            AutonomousRunPhase phase = phase(run, stats.byTypeState());
            byPhase.merge(phase.name(), 1, Integer::sum);
            String effectiveStatus = run.controlState() == AutonomousRunControlState.PAUSED
                    ? "PAUSED" : run.status().name();
            FleetDashboard.RunSummary summary = new FleetDashboard.RunSummary(
                    run.id(), run.status().name(), run.controlState().name(), effectiveStatus, phase.name(),
                    stats.total(), stats.stateCount("READY"), stats.stateCount("CLAIMED") + stats.stateCount("RUNNING"),
                    stats.stateCount("FAILED"), run.maxParallelism(), run.activeSlots(),
                    run.maxParallelism() == 0 ? null : Math.max(0, run.maxParallelism() - run.activeSlots()),
                    run.maxParallelism() == 0 ? 0 : Math.max(0, run.activeSlots() - run.maxParallelism()),
                    run.startedAt(), run.statusUpdatedAt());
            activeRuns.add(summary);
            if (run.controlState() == AutonomousRunControlState.PAUSED
                    || run.status() == AutonomousRunStatus.FAILED
                    || run.status() == AutonomousRunStatus.WAITING_APPROVAL
                    || run.status() == AutonomousRunStatus.ENGINEERING_PASSED) {
                attentionRuns.add(summary);
            }
        }
        Comparator<FleetDashboard.RunSummary> recentFirst = Comparator
                .comparing(FleetDashboard.RunSummary::statusUpdatedAt).reversed()
                .thenComparing(FleetDashboard.RunSummary::runId);
        activeRuns.sort(recentFirst);
        attentionRuns.sort(recentFirst);

        return new FleetDashboard.RunFleet(
                counts.total(),
                counts.lifecycle().getOrDefault("RUNNING", 0),
                counts.lifecycle().getOrDefault("ENGINEERING_PASSED", 0),
                counts.lifecycle().getOrDefault("WAITING_APPROVAL", 0),
                counts.lifecycle().getOrDefault("SUCCEEDED", 0),
                counts.lifecycle().getOrDefault("FAILED", 0),
                counts.lifecycle().getOrDefault("REJECTED", 0),
                counts.control().getOrDefault("PAUSED", 0),
                cancelled,
                Map.copyOf(byPhase), limited(activeRuns, detailLimit), limited(attentionRuns, detailLimit));
    }

    private static FleetDashboard.ExecutionFleet executionFleet(
            List<TaskRow> tasks, int workerActiveLeaseCount, int runActiveSlotCount, int detailLimit) {
        int claimed = 0, running = 0;
        List<FleetDashboard.ActiveLeaseSummary> leases = new ArrayList<>();
        for (TaskRow task : tasks) {
            if (!"CLAIMED".equals(task.state()) && !"RUNNING".equals(task.state())) continue;
            if ("CLAIMED".equals(task.state())) claimed++;
            else running++;
            leases.add(new FleetDashboard.ActiveLeaseSummary(
                    task.id(), task.taskType(), task.subjectType(), task.subjectId(), task.currentWorkerId(), task.state(),
                    task.attemptNo(), task.leaseExpiresAt()));
        }
        leases.sort(Comparator.comparing((FleetDashboard.ActiveLeaseSummary l) -> l.leaseExpiresAt(),
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(FleetDashboard.ActiveLeaseSummary::taskId));
        int leasedTasks = claimed + running;
        int autonomousLeasedTasks = (int) tasks.stream()
                .filter(t -> AUTONOMOUS_SUBJECT.equals(t.subjectType()))
                .filter(t -> "CLAIMED".equals(t.state()) || "RUNNING".equals(t.state()))
                .count();
        return new FleetDashboard.ExecutionFleet(
                leasedTasks, claimed, running, running, workerActiveLeaseCount - leasedTasks,
                runActiveSlotCount - autonomousLeasedTasks, limited(leases, detailLimit));
    }

    private static boolean admissionAllowed(TaskRow task) {
        if (!AUTONOMOUS_SUBJECT.equals(task.subjectType())) return true;
        return task.runMaxParallelism() == 0 || task.runActiveSlots() < task.runMaxParallelism();
    }

    private static boolean policyAllowed(TaskRow task) {
        if (!AUTONOMOUS_SUBJECT.equals(task.subjectType())) return true;
        return "RUNNING".equals(task.runStatus()) && "ACTIVE".equals(task.runControlState());
    }

    private static AutonomousRunPhase phase(RunRow run, Map<String, Map<String, Integer>> byTypeState) {
        if (run.controlState() == AutonomousRunControlState.PAUSED) return AutonomousRunPhase.PAUSED;
        if (run.status() == AutonomousRunStatus.FAILED) return AutonomousRunPhase.FAILED;
        if (run.status() == AutonomousRunStatus.WAITING_APPROVAL) return AutonomousRunPhase.APPROVAL;
        if (run.status() == AutonomousRunStatus.ENGINEERING_PASSED) return AutonomousRunPhase.ENGINEERING_GATE;
        if (unfinished(byTypeState, "REQUIREMENT_ANALYSIS")) return AutonomousRunPhase.REQUIREMENT_ANALYSIS;
        if (unfinished(byTypeState, "SOLUTION_ARCHITECTURE")) return AutonomousRunPhase.SOLUTION_ARCHITECTURE;
        if (unfinished(byTypeState, "IMPLEMENTATION_PLANNING")) return AutonomousRunPhase.IMPLEMENTATION_PLANNING;
        if (unfinished(byTypeState, "IMPLEMENTATION")) return AutonomousRunPhase.IMPLEMENTATION;
        if (unfinished(byTypeState, "INTEGRATION")) return AutonomousRunPhase.INTEGRATION;
        if (unfinished(byTypeState, "REPAIR")) return AutonomousRunPhase.REPAIR;
        if (unfinished(byTypeState, "CODE_REVIEW") || unfinished(byTypeState, "VERIFICATION")
                || unfinished(byTypeState, "INTEGRATION_VERIFICATION")) return AutonomousRunPhase.REVIEW_VERIFICATION;
        return AutonomousRunPhase.ENGINEERING_GATE;
    }

    private static boolean unfinished(Map<String, Map<String, Integer>> byTypeState, String type) {
        Map<String, Integer> states = byTypeState.get(type);
        if (states == null) return false;
        int total = states.values().stream().mapToInt(Integer::intValue).sum();
        int terminal = states.getOrDefault("COMPLETED", 0) + states.getOrDefault("CANCELLED", 0);
        return total > terminal;
    }

    private static int workerStatusRank(String status) {
        return switch (status) {
            case "ACTIVE" -> 0;
            case "DRAINING" -> 1;
            case "OFFLINE" -> 2;
            default -> 3;
        };
    }

    private static <T> List<T> limited(List<T> source, int limit) {
        return List.copyOf(source.subList(0, Math.min(source.size(), limit)));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Number value = (Number) rs.getObject(column);
        return value == null ? null : Instant.ofEpochMilli(value.longValue());
    }

    private record WorkerRow(String id, String runtimeType, Set<String> capabilities, int maxConcurrency,
                             int activeLeases, String status, Instant lastHeartbeatAt) {
        boolean supports(Set<String> required) {
            return capabilities.contains("*") || capabilities.containsAll(required);
        }
    }

    private record TaskRow(String id, String taskType, String state, String subjectType, String subjectId,
                           Set<String> requiredCapabilities, int priority, String currentWorkerId, int attemptNo,
                           Instant leaseExpiresAt, Instant createdAt, String runStatus, String runControlState,
                           int runMaxParallelism, int runActiveSlots) {}

    private record CountRow(String status, String controlState, int count, int activeSlots) {}
    private record RunCounts(int total, Map<String, Integer> lifecycle, Map<String, Integer> control, int activeSlots) {}
    private record RunRow(String id, AutonomousRunStatus status, AutonomousRunControlState controlState,
                          int maxParallelism, int activeSlots, Instant startedAt, Instant statusUpdatedAt) {}
    private record RunTaskCountRow(String runId, String taskType, String state, int count) {}

    private static final class RunTaskStats {
        private int total;
        private final Map<String, Integer> byState = new LinkedHashMap<>();
        private final Map<String, Map<String, Integer>> byTypeState = new LinkedHashMap<>();

        void add(String taskType, String state, int count) {
            total += count;
            byState.merge(state, count, Integer::sum);
            byTypeState.computeIfAbsent(taskType, ignored -> new LinkedHashMap<>()).merge(state, count, Integer::sum);
        }

        int total() { return total; }
        int stateCount(String state) { return byState.getOrDefault(state, 0); }
        Map<String, Map<String, Integer>> byTypeState() { return byTypeState; }
    }
}
