package me.asu.aidev.workflow.jdbc;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Browser read model for factory work. Never changes draft approval or execution history. */
@Repository
public class JdbcConfiguredPlanHistoryRepository {
    public record Preparation(String id, long version, long generation, String state, String errorCode,
                              String taskId, String repository, String role, String instruction,
                              Instant time, List<String> missing) {}
    public record Attempt(String id, int number, String worker, String state, Instant startedAt,
                          Instant finishedAt, String failureCode, String verdict, String report,
                          String baseRevision, String resultRevision, String changedFiles,
                          String branch, String workspace) {}
    public record Task(String id, String state, String repository, String instruction,
                       Instant createdAt, List<Attempt> attempts) {}
    public record Operation(String id, String taskId, String attemptId, String tool, String resource,
                            String state, Instant startedAt, Instant finishedAt, String evidenceRef) {}
    public record History(String state, List<Preparation> preparations, List<Task> tasks,
                          List<Operation> operations, boolean operationsTruncated) {
        public boolean present() { return !preparations.isEmpty() || !tasks.isEmpty(); }
    }

    private final JdbcTemplate jdbc;
    private final ObjectMapper json = new ObjectMapper();

    public JdbcConfiguredPlanHistoryRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional(readOnly = true)
    public History read(String planId) {
        return read(planId, true);
    }

    @Transactional(readOnly = true)
    public String stateForList(String planId) {
        return read(planId, false).state();
    }

    /** Public display state; the saved graph's approval state remains separate. */
    public static String displayState(String state) {
        if (state == null) return null;
        return switch (state) {
            case "DELEGATED_COMPLETED" -> "COMPLETED";
            case "DELEGATED_RUNNING" -> "IN_PROGRESS";
            case "DELEGATED_WAITING" -> "WAITING_FOR_WORKER";
            case "DELEGATED_NEEDS_ATTENTION" -> "NEEDS_ATTENTION";
            default -> state;
        };
    }

    private History read(String planId, boolean includeOperations) {
        var preparations = jdbc.query("""
                SELECT p.*, (SELECT MAX(a.created_ms) FROM authority_audit a
                   WHERE a.target_key=? AND a.detail=p.intent_id
                     AND a.event='PROFESSIONAL_TASK_PREPARED') AS prepared_ms
                  FROM execution_preparation p
                 WHERE EXISTS (SELECT 1 FROM authority_audit a WHERE a.target_key=?
                       AND a.detail=p.intent_id AND a.event='PROFESSIONAL_TASK_PREPARED')
                    OR EXISTS (SELECT 1 FROM work_task t WHERE t.id=p.task_id
                       AND t.subject_type='configured-plan' AND t.subject_id=?)
                 ORDER BY prepared_ms DESC, p.intent_id, p.intent_version DESC
                """, (rs, n) -> {
            try {
                var intent = json.readTree(rs.getString("intent_json"));
                var details = json.readTree(rs.getString("details_json"));
                var missing = new ArrayList<String>();
                if (details != null) details.path("missing").forEach(value -> missing.add(value.asText()));
                return new Preparation(rs.getString("intent_id"), rs.getLong("intent_version"),
                        rs.getLong("generation"), rs.getString("state"), rs.getString("error_code"),
                        rs.getString("task_id"), intent.path("repositoryRef").asText(""),
                        intent.path("defaultRole").asText(""), intent.path("instruction").asText(""),
                        instant((Number) rs.getObject("prepared_ms")), List.copyOf(missing));
            } catch (java.io.IOException failure) {
                throw new IllegalStateException("Cannot read recorded factory preparation", failure);
            }
        }, "PLAN:" + planId, "PLAN:" + planId, planId);
        var tasks = jdbc.query("""
                SELECT id,state,repository_ref,instruction,created_at_epoch_ms FROM work_task
                 WHERE subject_type='configured-plan' AND subject_id=?
                 ORDER BY created_at_epoch_ms DESC,id DESC
                """, (rs, n) -> new Task(rs.getString("id"), rs.getString("state"),
                rs.getString("repository_ref"), rs.getString("instruction"),
                instant((Number) rs.getObject("created_at_epoch_ms")), attempts(rs.getString("id"))), planId);
        List<Operation> operations = includeOperations ? jdbc.query("""
                SELECT o.operation_id,o.task_id,o.attempt_id,o.tool_id,o.resource,o.state,
                       o.started_ms,o.finished_ms,o.evidence_ref FROM execution_operation o
                 JOIN work_task t ON t.id=o.task_id
                 WHERE t.subject_type='configured-plan' AND t.subject_id=?
                 ORDER BY o.started_ms DESC,o.operation_id DESC LIMIT 501
                """, (rs, n) -> new Operation(rs.getString("operation_id"), rs.getString("task_id"),
                rs.getString("attempt_id"), rs.getString("tool_id"), rs.getString("resource"),
                rs.getString("state"), instant((Number) rs.getObject("started_ms")),
                instant((Number) rs.getObject("finished_ms")), rs.getString("evidence_ref")), planId) : List.of();
        return new History(state(preparations, tasks), preparations, tasks,
                List.copyOf(operations.subList(0, Math.min(500, operations.size()))), operations.size() > 500);
    }

    private List<Attempt> attempts(String taskId) {
        return jdbc.query("""
                SELECT id,attempt_no,worker_id,state,started_at_epoch_ms,finished_at_epoch_ms,
                       termination_reason,completion_verdict,completion_summary,base_revision,
                       result_revision,changed_files,branch_name,workspace_key
                  FROM execution_attempt WHERE task_id=? ORDER BY attempt_no DESC,id DESC
                """, (rs, n) -> new Attempt(rs.getString("id"), rs.getInt("attempt_no"),
                rs.getString("worker_id"), rs.getString("state"),
                instant((Number) rs.getObject("started_at_epoch_ms")),
                instant((Number) rs.getObject("finished_at_epoch_ms")), safeCode(rs.getString("termination_reason")),
                rs.getString("completion_verdict"), rs.getString("completion_summary"),
                rs.getString("base_revision"), rs.getString("result_revision"), rs.getString("changed_files"),
                rs.getString("branch_name"), rs.getString("workspace_key")), taskId);
    }

    private String state(List<Preparation> preparations, List<Task> tasks) {
        if (tasks.stream().anyMatch(t -> Set.of("CLAIMED", "RUNNING").contains(t.state()))) return "DELEGATED_RUNNING";
        if (tasks.stream().anyMatch(t -> Set.of("READY", "BLOCKED").contains(t.state()))) return "DELEGATED_WAITING";
        // Earlier failed submissions remain history; use the latest submission for each role/repository.
        var latest = new LinkedHashMap<String, Preparation>();
        preparations.stream().sorted(Comparator.comparing(Preparation::time,
                Comparator.nullsLast(Comparator.reverseOrder())).thenComparing(Preparation::version, Comparator.reverseOrder()))
                .forEach(p -> latest.putIfAbsent(p.repository() + "\n" + p.role(), p));
        if (latest.values().stream().anyMatch(p -> !"READY".equals(p.state()))) return "DELEGATED_NEEDS_ATTENTION";
        Set<String> ids = new HashSet<>();
        latest.values().forEach(p -> { if (p.taskId() != null) ids.add(p.taskId()); });
        var current = tasks.stream().filter(t -> ids.isEmpty() || ids.contains(t.id())).toList();
        if (current.isEmpty()) return preparations.isEmpty() ? null : "DELEGATED_WAITING";
        return current.stream().allMatch(this::successful) ? "DELEGATED_COMPLETED" : "DELEGATED_NEEDS_ATTENTION";
    }

    public boolean successful(Task task) {
        if (!"COMPLETED".equals(task.state()) || task.attempts().isEmpty()) return false;
        Attempt attempt = task.attempts().getFirst();
        if (!"PASS".equals(attempt.verdict())) return false;
        try {
            var report = json.readTree(attempt.report() == null ? "{}" : attempt.report());
            String outcome = report == null ? "" : report.path("outcome").asText("");
            if (outcome.startsWith("BLOCKED") || outcome.startsWith("FAILED") || outcome.startsWith("INCOMPLETE")
                    || report != null && report.has("completed") && !report.path("completed").asBoolean()) return false;
        } catch (java.io.IOException plainTextReport) { /* Existing plain text reports are valid. */ }
        return true;
    }

    private static String safeCode(String value) {
        return value != null && value.matches("[A-Z][A-Z0-9_]{0,119}") ? value : null;
    }
    private static Instant instant(Number value) { return value == null ? null : Instant.ofEpochMilli(value.longValue()); }
}
