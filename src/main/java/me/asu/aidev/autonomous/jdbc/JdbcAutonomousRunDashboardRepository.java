package me.asu.aidev.autonomous.jdbc;

import me.asu.aidev.autonomous.domain.AutonomousRunControlState;
import me.asu.aidev.autonomous.domain.AutonomousRunDashboard;
import me.asu.aidev.autonomous.domain.AutonomousRunPhase;
import me.asu.aidev.autonomous.domain.AutonomousRunStatus;
import me.asu.aidev.autonomous.port.AutonomousRunDashboardRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcAutonomousRunDashboardRepository implements AutonomousRunDashboardRepository {
    private static final String SUBJECT_TYPE = "AUTONOMOUS_REQUIREMENT";
    private final JdbcTemplate jdbc;

    public JdbcAutonomousRunDashboardRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<AutonomousRunDashboard> project(String runId) {
        List<RunRow> runs = jdbc.query("SELECT * FROM autonomous_run WHERE id=?", (rs, n) -> runRow(rs), runId);
        if (runs.isEmpty()) return Optional.empty();
        RunRow run = runs.getFirst();

        TaskSnapshot tasks = taskSnapshot(runId);
        ExecutionSnapshot execution = executionSnapshot(runId);
        AutonomousRunDashboard.ApprovalProgress approval = approval(run.approvalRequestId());
        AutonomousRunPhase phase = phase(run, tasks.byTypeState());
        String effectiveStatus = switch (run.controlState()) {
            case PAUSED -> "PAUSED";
            case CANCELLED -> "CANCELLED";
            case ACTIVE -> run.status().name();
        };
        boolean terminal = run.controlState() == AutonomousRunControlState.CANCELLED
                || run.status() == AutonomousRunStatus.SUCCEEDED
                || run.status() == AutonomousRunStatus.REJECTED;
        Instant end = run.completedAt() == null ? Instant.now() : run.completedAt();
        long elapsed = Math.max(0L, end.toEpochMilli() - run.startedAt().toEpochMilli());

        return Optional.of(new AutonomousRunDashboard(
                run.id(), run.status().name(), run.controlState().name(), effectiveStatus, phase, terminal,
                run.startedAt(), run.statusUpdatedAt(), run.completedAt(), elapsed,
                tasks.progress(), execution.progress(), approval,
                new AutonomousRunDashboard.SchedulingProgress(
                        run.maxParallelism(), run.activeSlots(),
                        run.maxParallelism() == 0 ? null : Math.max(0, run.maxParallelism() - run.activeSlots()),
                        run.maxParallelism() == 0 ? 0 : Math.max(0, run.activeSlots() - run.maxParallelism()),
                        run.activeSlots() - execution.progress().active().size()),
                run.failureCount(), run.recoveryCount(), run.pauseCount(), run.resumeCount(), run.lastFailureReason()));
    }

    private TaskSnapshot taskSnapshot(String runId) {
        List<TaskRow> rows = jdbc.query("""
                SELECT id,task_type,state,current_worker_id,attempt_no,lease_expires_at_epoch_ms
                  FROM work_task
                 WHERE subject_type=? AND subject_id=?
                """, (rs, n) -> new TaskRow(
                rs.getString("id"), rs.getString("task_type"), rs.getString("state"),
                rs.getString("current_worker_id"), rs.getInt("attempt_no"), number(rs, "lease_expires_at_epoch_ms")),
                SUBJECT_TYPE, runId);

        int blocked = 0, ready = 0, claimed = 0, running = 0, completed = 0, failed = 0, cancelled = 0;
        Map<String, Integer> byType = new LinkedHashMap<>();
        Map<String, Map<String, Integer>> byTypeState = new LinkedHashMap<>();
        for (TaskRow row : rows) {
            byType.merge(row.type(), 1, Integer::sum);
            byTypeState.computeIfAbsent(row.type(), k -> new LinkedHashMap<>()).merge(row.state(), 1, Integer::sum);
            switch (row.state()) {
                case "BLOCKED" -> blocked++;
                case "READY" -> ready++;
                case "CLAIMED" -> claimed++;
                case "RUNNING" -> running++;
                case "COMPLETED" -> completed++;
                case "FAILED" -> failed++;
                case "CANCELLED" -> cancelled++;
                default -> throw new IllegalStateException("unknown task state: " + row.state());
            }
        }
        var progress = new AutonomousRunDashboard.TaskProgress(
                rows.size(), blocked, ready, claimed, running, completed, failed, cancelled, Map.copyOf(byType));
        return new TaskSnapshot(progress, byTypeState);
    }

    private ExecutionSnapshot executionSnapshot(String runId) {
        List<AttemptRow> attempts = jdbc.query("""
                SELECT ea.task_id,wt.task_type,ea.attempt_no,ea.worker_id,ea.state,
                       ea.created_at_epoch_ms,ea.started_at_epoch_ms,ea.finished_at_epoch_ms
                  FROM execution_attempt ea
                  JOIN work_task wt ON wt.id=ea.task_id
                 WHERE wt.subject_type=? AND wt.subject_id=?
                """, (rs, n) -> new AttemptRow(
                rs.getString("task_id"), rs.getString("task_type"), rs.getInt("attempt_no"),
                rs.getString("worker_id"), rs.getString("state"),
                Instant.ofEpochMilli(rs.getLong("created_at_epoch_ms")), instant(rs, "started_at_epoch_ms"), instant(rs, "finished_at_epoch_ms")),
                SUBJECT_TYPE, runId);

        int failedAttempts = 0;
        int expiredAttempts = 0;
        Map<String, Integer> maxAttemptByTask = new LinkedHashMap<>();
        for (AttemptRow a : attempts) {
            maxAttemptByTask.merge(a.taskId(), a.attemptNo(), Math::max);
            if ("FAILED".equals(a.state())) failedAttempts++;
            if ("EXPIRED".equals(a.state())) expiredAttempts++;
        }
        int retries = maxAttemptByTask.values().stream().mapToInt(n -> Math.max(0, n - 1)).sum();

        List<AutonomousRunDashboard.ActiveExecution> active = jdbc.query("""
                SELECT wt.id,wt.task_type,wt.state,wt.current_worker_id,wt.attempt_no,
                       wt.lease_expires_at_epoch_ms,ea.started_at_epoch_ms
                  FROM work_task wt
                  LEFT JOIN execution_attempt ea
                    ON ea.task_id=wt.id AND ea.attempt_no=wt.attempt_no
                 WHERE wt.subject_type=? AND wt.subject_id=? AND wt.state IN ('CLAIMED','RUNNING')
                 ORDER BY wt.created_at_epoch_ms,wt.id
                """, (rs, n) -> new AutonomousRunDashboard.ActiveExecution(
                rs.getString("id"), rs.getString("task_type"), rs.getString("state"), rs.getString("current_worker_id"),
                rs.getInt("attempt_no"), instant(rs, "started_at_epoch_ms"),
                instant(rs, "lease_expires_at_epoch_ms")), SUBJECT_TYPE, runId);

        int currentParallelism = (int) active.stream().filter(a -> "RUNNING".equals(a.state())).count();
        int peak = peakParallelism(attempts);
        var progress = new AutonomousRunDashboard.ExecutionProgress(
                attempts.size(), retries, failedAttempts, expiredAttempts, currentParallelism, peak, List.copyOf(active));
        return new ExecutionSnapshot(progress);
    }


    private AutonomousRunDashboard.ApprovalProgress approval(String approvalRequestId) {
        if (approvalRequestId == null) return null;
        List<AutonomousRunDashboard.ApprovalProgress> rows = jdbc.query("""
                SELECT id,status,requested_by,decided_by,requested_at_epoch_ms,decided_at_epoch_ms
                  FROM approval_request WHERE id=?
                """, (rs, n) -> new AutonomousRunDashboard.ApprovalProgress(
                rs.getString("id"), rs.getString("status"), rs.getString("requested_by"), rs.getString("decided_by"),
                Instant.ofEpochMilli(rs.getLong("requested_at_epoch_ms")), instant(rs, "decided_at_epoch_ms")), approvalRequestId);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private static int peakParallelism(List<AttemptRow> attempts) {
        record Edge(Instant at, int delta) {}
        List<Edge> edges = new ArrayList<>();
        for (AttemptRow attempt : attempts) {
            Instant start = attempt.startedAt();
            if (start == null) continue;
            Instant end = attempt.finishedAt() == null ? Instant.now() : attempt.finishedAt();
            edges.add(new Edge(start, +1));
            edges.add(new Edge(end, -1));
        }
        edges.sort(Comparator.comparing(Edge::at).thenComparingInt(Edge::delta)); // -1 before +1 at equal timestamp
        int current = 0;
        int peak = 0;
        for (Edge edge : edges) {
            current += edge.delta();
            peak = Math.max(peak, current);
        }
        return peak;
    }

    private static AutonomousRunPhase phase(RunRow run, Map<String, Map<String, Integer>> byTypeState) {
        if (run.controlState() == AutonomousRunControlState.CANCELLED) return AutonomousRunPhase.CANCELLED;
        if (run.controlState() == AutonomousRunControlState.PAUSED) return AutonomousRunPhase.PAUSED;
        if (run.status() == AutonomousRunStatus.FAILED) return AutonomousRunPhase.FAILED;
        if (run.status() == AutonomousRunStatus.WAITING_APPROVAL) return AutonomousRunPhase.APPROVAL;
        if (run.status() == AutonomousRunStatus.ENGINEERING_PASSED) return AutonomousRunPhase.ENGINEERING_GATE;
        if (run.status() == AutonomousRunStatus.SUCCEEDED || run.status() == AutonomousRunStatus.REJECTED) return AutonomousRunPhase.COMPLETED;

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

    private static RunRow runRow(ResultSet rs) throws SQLException {
        return new RunRow(
                rs.getString("id"), AutonomousRunStatus.valueOf(rs.getString("status")),
                AutonomousRunControlState.valueOf(rs.getString("control_state")), rs.getString("approval_request_id"),
                rs.getInt("failure_count"), rs.getInt("recovery_count"), rs.getInt("pause_count"), rs.getInt("resume_count"),
                rs.getString("last_failure_reason"), rs.getInt("max_parallelism"), rs.getInt("active_slots"),
                Instant.ofEpochMilli(rs.getLong("started_at_epoch_ms")),
                Instant.ofEpochMilli(rs.getLong("status_updated_at_epoch_ms")), instant(rs, "completed_at_epoch_ms"));
    }

    private static Number number(ResultSet rs, String column) throws SQLException {
        return (Number) rs.getObject(column);
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Number value = number(rs, column);
        return value == null ? null : Instant.ofEpochMilli(value.longValue());
    }

    private record RunRow(String id, AutonomousRunStatus status, AutonomousRunControlState controlState,
                          String approvalRequestId, int failureCount, int recoveryCount, int pauseCount, int resumeCount,
                          String lastFailureReason, int maxParallelism, int activeSlots,
                          Instant startedAt, Instant statusUpdatedAt, Instant completedAt) {}
    private record TaskRow(String id, String type, String state, String currentWorkerId, int attemptNo, Number leaseExpiresAt) {}
    private record TaskSnapshot(AutonomousRunDashboard.TaskProgress progress, Map<String, Map<String, Integer>> byTypeState) {}
    private record AttemptRow(String taskId, String taskType, int attemptNo, String workerId, String state,
                              Instant createdAt, Instant startedAt, Instant finishedAt) {}
    private record ExecutionSnapshot(AutonomousRunDashboard.ExecutionProgress progress) {}
}
