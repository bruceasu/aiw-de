package me.asu.aidev.webconsole.jdbc;

import me.asu.aidev.webconsole.domain.WebConsoleApprovalSummary;
import me.asu.aidev.webconsole.domain.WebConsoleOverview.Activity;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Bounded, parameterized browser reads over existing authority tables. */
@Repository
@Transactional(readOnly = true)
public class JdbcWebConsoleOverviewReadRepository {
    private final JdbcTemplate jdbc;

    public JdbcWebConsoleOverviewReadRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /** Resolve only IDs already present in the bounded Fleet detail snapshot. */
    public Set<String> existingRunIds(Collection<String> ids) {
        if (ids.isEmpty()) return Set.of();
        String placeholders = String.join(",", java.util.Collections.nCopies(ids.size(), "?"));
        return new HashSet<>(jdbc.query("SELECT id FROM autonomous_run WHERE id IN (%s)".formatted(placeholders),
                (rs, row) -> rs.getString("id"), ids.toArray()));
    }

    public record RunContext(String requirement, int completedTasks, String failureReason) {}

    public Map<String, RunContext> runContexts(Collection<String> ids) {
        if (ids.isEmpty()) return Map.of();
        String placeholders = String.join(",", java.util.Collections.nCopies(ids.size(), "?"));
        Map<String, RunContext> result = new HashMap<>();
        jdbc.query("""
                SELECT ar.id, ar.last_failure_reason,
                       COALESCE((SELECT wt.instruction FROM work_task wt
                         WHERE wt.subject_type='AUTONOMOUS_REQUIREMENT' AND wt.subject_id=ar.id
                           AND wt.task_type='REQUIREMENT_ANALYSIS'
                         ORDER BY wt.created_at_epoch_ms, wt.id LIMIT 1), '') AS requirement,
                       (SELECT COUNT(*) FROM work_task wt
                         WHERE wt.subject_type='AUTONOMOUS_REQUIREMENT' AND wt.subject_id=ar.id
                           AND wt.state='COMPLETED') AS completed_tasks
                  FROM autonomous_run ar WHERE ar.id IN (%s)
                """.formatted(placeholders), rs -> {
            while (rs.next()) result.put(rs.getString("id"), new RunContext(
                    rs.getString("requirement"), rs.getInt("completed_tasks"), rs.getString("last_failure_reason")));
            return null;
        }, ids.toArray());
        return Map.copyOf(result);
    }

    public int activeRunCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM autonomous_run WHERE status NOT IN ('SUCCEEDED','REJECTED') AND control_state<>'CANCELLED'", Integer.class);
    }

    public int pendingApprovalCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM approval_request WHERE status='PENDING'", Integer.class);
    }

    public List<WebConsoleApprovalSummary> pendingApprovals(int limit) {
        return jdbc.query("""
                SELECT id, subject_id, status, result_revision, requested_by, channel,
                       decided_by, requested_at_epoch_ms, decided_at_epoch_ms
                  FROM approval_request WHERE status='PENDING'
                 ORDER BY requested_at_epoch_ms DESC, id LIMIT ?
                """, (rs, n) -> new WebConsoleApprovalSummary(rs.getString("id"), rs.getString("subject_id"),
                rs.getString("status"), rs.getString("result_revision"), rs.getString("requested_by"),
                rs.getString("channel"), rs.getString("decided_by"),
                Instant.ofEpochMilli(rs.getLong("requested_at_epoch_ms")),
                rs.getObject("decided_at_epoch_ms") == null ? null : Instant.ofEpochMilli(rs.getLong("decided_at_epoch_ms"))), limit);
    }

    /** Each branch contributes at most limit rows; no per-Run timeline traversal. */
    public List<Activity> recentActivity(int limit) {
        List<String> branches = new ArrayList<>();
        branches.add("""
                SELECT ar.started_at_epoch_ms AS happened, ar.id AS run_id, 'RUN_STARTED' AS event_type,
                       ar.requirement_task_id AS task_id, NULL AS attempt_no, NULL AS worker_id,
                       NULL AS detail, 'run:start:' || ar.id AS source_key FROM autonomous_run ar
                """);
        branches.add("""
                SELECT ar.completed_at_epoch_ms AS happened, ar.id AS run_id,
                       CASE WHEN ar.status='SUCCEEDED' THEN 'RUN_SUCCEEDED' ELSE 'RUN_REJECTED' END AS event_type,
                       ar.terminal_source_task_id AS task_id, NULL AS attempt_no, NULL AS worker_id,
                       NULL AS detail, 'run:finish:' || ar.id AS source_key FROM autonomous_run ar
                 WHERE ar.completed_at_epoch_ms IS NOT NULL AND ar.status IN ('SUCCEEDED','REJECTED')
                """);
        branches.add("""
                SELECT wt.created_at_epoch_ms AS happened, wt.subject_id AS run_id, 'TASK_CREATED' AS event_type,
                       wt.id AS task_id, NULL AS attempt_no, NULL AS worker_id, wt.task_type AS detail,
                       'task:' || wt.id AS source_key FROM work_task wt
                  JOIN autonomous_run ar ON ar.id=wt.subject_id WHERE wt.subject_type='AUTONOMOUS_REQUIREMENT'
                """);
        for (String phase : List.of("created", "started", "finished", "engineering_gate_closed")) {
            String event = switch (phase) {
                case "created" -> "'ATTEMPT_CLAIMED'";
                case "started" -> "'ATTEMPT_STARTED'";
                case "finished" -> "CASE ea.state WHEN 'COMPLETED' THEN 'ATTEMPT_COMPLETED' WHEN 'FAILED' THEN 'ATTEMPT_FAILED' ELSE 'ATTEMPT_EXPIRED' END";
                default -> "CASE ea.engineering_gate_status WHEN 'PASSED' THEN 'ENGINEERING_GATE_PASSED' ELSE 'ENGINEERING_GATE_REPAIR_REQUIRED' END";
            };
            String condition = switch (phase) {
                case "finished" -> " AND ea.state IN ('COMPLETED','FAILED','EXPIRED')";
                case "engineering_gate_closed" -> " AND ea.engineering_gate_status IS NOT NULL";
                default -> "";
            };
            String detail = switch (phase) {
                case "finished" -> "CASE WHEN ea.state='EXPIRED' AND ea.termination_reason IS NOT NULL THEN 'terminationReason=' || ea.termination_reason WHEN ea.state='COMPLETED' AND ea.completion_verdict IS NOT NULL THEN 'verdict=' || ea.completion_verdict ELSE NULL END";
                case "engineering_gate_closed" -> "'review=' || COALESCE(ea.engineering_gate_review_task_id,'unavailable') || ', verification=' || COALESCE(ea.engineering_gate_verification_task_id,'unavailable')";
                default -> "NULL";
            };
            branches.add("""
                    SELECT ea.%s_at_epoch_ms AS happened, wt.subject_id AS run_id, %s AS event_type,
                           ea.task_id, ea.attempt_no, ea.worker_id, %s AS detail,
                           'attempt:%s:' || ea.task_id || ':' || ea.attempt_no AS source_key
                      FROM execution_attempt ea JOIN work_task wt ON wt.id=ea.task_id
                      JOIN autonomous_run ar ON ar.id=wt.subject_id
                     WHERE wt.subject_type='AUTONOMOUS_REQUIREMENT' AND ea.%s_at_epoch_ms IS NOT NULL%s
                    """.formatted(phase, event, detail, phase, phase, condition));
        }
        for (String phase : List.of("requested", "decided")) {
            String event = phase.equals("requested") ? "'APPROVAL_REQUESTED'"
                    : "CASE ap.status WHEN 'APPROVED' THEN 'APPROVAL_APPROVED' ELSE 'APPROVAL_REJECTED' END";
            branches.add("""
                    SELECT ap.%s_at_epoch_ms AS happened, ap.subject_id AS run_id, %s AS event_type,
                           ap.source_task_id AS task_id, NULL AS attempt_no, NULL AS worker_id,
                           NULL AS detail, 'approval:%s:' || ap.id AS source_key
                      FROM approval_request ap JOIN autonomous_run ar ON ar.id=ap.subject_id
                     WHERE ap.subject_type='AUTONOMOUS_REQUIREMENT' AND ap.%s_at_epoch_ms IS NOT NULL
                    """.formatted(phase, event, phase, phase));
        }
        branches.add("""
                SELECT re.occurred_at_epoch_ms AS happened, re.run_id, 'RUN_' || re.event_type AS event_type,
                       re.related_task_id AS task_id, NULL AS attempt_no, NULL AS worker_id,
                       re.detail, 'event:' || re.run_id || ':' || re.sequence_no AS source_key
                  FROM autonomous_run_event re JOIN autonomous_run ar ON ar.id=re.run_id
                """);
        String query = branches.stream().map(sql -> "SELECT * FROM (" + sql + " ORDER BY happened DESC, source_key DESC LIMIT ?)")
                .collect(java.util.stream.Collectors.joining(" UNION ALL "));
        Object[] parameters = java.util.Collections.nCopies(branches.size() + 1, limit).toArray();
        return jdbc.query("SELECT * FROM (" + query + ") ORDER BY happened DESC, source_key DESC LIMIT ?",
                (rs, n) -> new Activity(Instant.ofEpochMilli(rs.getLong("happened")), rs.getString("run_id"),
                        rs.getString("event_type"), rs.getString("task_id"),
                        rs.getObject("attempt_no") == null ? null : rs.getInt("attempt_no"),
                        rs.getString("worker_id"), rs.getString("detail"), rs.getString("source_key")), parameters);
    }
}
