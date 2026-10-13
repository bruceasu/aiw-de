package me.asu.aidev.webconsole.jdbc;

import me.asu.aidev.webconsole.domain.WebConsoleApprovalSummary;
import me.asu.aidev.webconsole.domain.WebConsoleRunSummary;
import me.asu.aidev.webconsole.domain.WebConsoleTaskSummary;
import me.asu.aidev.work.domain.CapabilitySetCodec;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcWebConsoleReadRepository {
    private static final String SUBJECT_TYPE = "AUTONOMOUS_REQUIREMENT";
    private final JdbcTemplate jdbc;

    public JdbcWebConsoleReadRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public List<WebConsoleRunSummary> recentRuns(int limit, boolean archived) {
        return jdbc.query("""
                SELECT ar.id, ar.status, ar.control_state, ar.max_parallelism, ar.active_slots,
                       ar.started_at_epoch_ms, ar.status_updated_at_epoch_ms, a.delete_after_epoch_ms,
                       COALESCE((SELECT wt.instruction
                                   FROM work_task wt
                                  WHERE wt.subject_type='AUTONOMOUS_REQUIREMENT'
                                    AND wt.subject_id=ar.id
                                    AND wt.task_type='REQUIREMENT_ANALYSIS'
                                  ORDER BY wt.created_at_epoch_ms, wt.id
                                  LIMIT 1), '') AS requirement,
                       (SELECT COUNT(*) FROM work_task wt
                         WHERE wt.subject_type='AUTONOMOUS_REQUIREMENT' AND wt.subject_id=ar.id) AS total_tasks,
                       (SELECT COUNT(*) FROM work_task wt
                         WHERE wt.subject_type='AUTONOMOUS_REQUIREMENT' AND wt.subject_id=ar.id
                           AND wt.state='COMPLETED') AS completed_tasks,
                       (SELECT COUNT(*) FROM work_task wt
                         WHERE wt.subject_type='AUTONOMOUS_REQUIREMENT' AND wt.subject_id=ar.id
                           AND wt.state IN ('CLAIMED','RUNNING')) AS active_tasks,
                       (SELECT COUNT(*) FROM work_task wt
                         WHERE wt.subject_type='AUTONOMOUS_REQUIREMENT' AND wt.subject_id=ar.id
                           AND wt.state='FAILED') AS failed_tasks,
                       CASE
                         WHEN ar.control_state='CANCELLED' THEN 'CANCELLED'
                         WHEN ar.control_state='PAUSED' THEN 'PAUSED'
                         WHEN ar.status='FAILED' THEN 'FAILED'
                         WHEN ar.status='WAITING_APPROVAL' THEN 'APPROVAL'
                         WHEN ar.status='ENGINEERING_PASSED' THEN 'ENGINEERING_GATE'
                         WHEN ar.status IN ('SUCCEEDED','REJECTED') THEN 'COMPLETED'
                         WHEN EXISTS (SELECT 1 FROM work_task wt WHERE wt.subject_type='AUTONOMOUS_REQUIREMENT' AND wt.subject_id=ar.id AND wt.task_type='REQUIREMENT_ANALYSIS' AND wt.state NOT IN ('COMPLETED','CANCELLED')) THEN 'REQUIREMENT_ANALYSIS'
                         WHEN EXISTS (SELECT 1 FROM work_task wt WHERE wt.subject_type='AUTONOMOUS_REQUIREMENT' AND wt.subject_id=ar.id AND wt.task_type='SOLUTION_ARCHITECTURE' AND wt.state NOT IN ('COMPLETED','CANCELLED')) THEN 'SOLUTION_ARCHITECTURE'
                         WHEN EXISTS (SELECT 1 FROM work_task wt WHERE wt.subject_type='AUTONOMOUS_REQUIREMENT' AND wt.subject_id=ar.id AND wt.task_type='IMPLEMENTATION_PLANNING' AND wt.state NOT IN ('COMPLETED','CANCELLED')) THEN 'IMPLEMENTATION_PLANNING'
                         WHEN EXISTS (SELECT 1 FROM work_task wt WHERE wt.subject_type='AUTONOMOUS_REQUIREMENT' AND wt.subject_id=ar.id AND wt.task_type='IMPLEMENTATION' AND wt.state NOT IN ('COMPLETED','CANCELLED')) THEN 'IMPLEMENTATION'
                         WHEN EXISTS (SELECT 1 FROM work_task wt WHERE wt.subject_type='AUTONOMOUS_REQUIREMENT' AND wt.subject_id=ar.id AND wt.task_type='INTEGRATION' AND wt.state NOT IN ('COMPLETED','CANCELLED')) THEN 'INTEGRATION'
                         WHEN EXISTS (SELECT 1 FROM work_task wt WHERE wt.subject_type='AUTONOMOUS_REQUIREMENT' AND wt.subject_id=ar.id AND wt.task_type='REPAIR' AND wt.state NOT IN ('COMPLETED','CANCELLED')) THEN 'REPAIR'
                         WHEN EXISTS (SELECT 1 FROM work_task wt WHERE wt.subject_type='AUTONOMOUS_REQUIREMENT' AND wt.subject_id=ar.id AND wt.task_type IN ('CODE_REVIEW','VERIFICATION','INTEGRATION_VERIFICATION') AND wt.state NOT IN ('COMPLETED','CANCELLED')) THEN 'REVIEW_VERIFICATION'
                         ELSE 'ENGINEERING_GATE'
                       END AS phase
                  FROM autonomous_run ar
                  LEFT JOIN web_console_run_archive a ON a.run_id=ar.id
                 WHERE (a.run_id IS NOT NULL) = ?
                 ORDER BY ar.started_at_epoch_ms DESC, ar.id
                 LIMIT ?
                """, (rs, n) -> runSummary(rs), archived, limit);
    }

    @Transactional(readOnly = true)
    public List<WebConsoleApprovalSummary> recentApprovals(int limit) {
        return jdbc.query("""
                SELECT id, subject_id, status, result_revision, requested_by, channel,
                       decided_by, requested_at_epoch_ms, decided_at_epoch_ms
                  FROM approval_request
                 ORDER BY requested_at_epoch_ms DESC, id
                 LIMIT ?
                """, (rs, n) -> new WebConsoleApprovalSummary(
                rs.getString("id"), rs.getString("subject_id"), rs.getString("status"),
                rs.getString("result_revision"), rs.getString("requested_by"), rs.getString("channel"),
                rs.getString("decided_by"), instant(rs, "requested_at_epoch_ms"),
                instant(rs, "decided_at_epoch_ms")), limit);
    }

    @Transactional(readOnly = true)
    public List<WebConsoleTaskSummary> tasksForRun(String runId) {
        return jdbc.query("""
                SELECT id, task_type, state, required_capabilities, current_worker_id, attempt_no,
                       repository_ref, base_revision, instruction, created_at_epoch_ms, updated_at_epoch_ms
                  FROM work_task
                 WHERE subject_type=? AND subject_id=?
                 ORDER BY created_at_epoch_ms, id
                """, (rs, n) -> new WebConsoleTaskSummary(
                rs.getString("id"), rs.getString("task_type"), rs.getString("state"),
                CapabilitySetCodec.decode(rs.getString("required_capabilities")),
                rs.getString("current_worker_id"), rs.getInt("attempt_no"), rs.getString("repository_ref"),
                rs.getString("base_revision"), rs.getString("instruction"),
                instant(rs, "created_at_epoch_ms"), instant(rs, "updated_at_epoch_ms")),
                SUBJECT_TYPE, runId);
    }

    private static WebConsoleRunSummary runSummary(ResultSet rs) throws SQLException {
        String status = rs.getString("status");
        String control = rs.getString("control_state");
        String effective = switch (control) {
            case "PAUSED" -> "PAUSED";
            case "CANCELLED" -> "CANCELLED";
            default -> status;
        };
        return new WebConsoleRunSummary(
                rs.getString("id"), rs.getString("requirement"), status, control, effective,
                rs.getString("phase"), rs.getInt("total_tasks"), rs.getInt("completed_tasks"),
                rs.getInt("active_tasks"), rs.getInt("failed_tasks"), rs.getInt("max_parallelism"),
                rs.getInt("active_slots"), instant(rs, "started_at_epoch_ms"),
                instant(rs, "status_updated_at_epoch_ms"), instant(rs, "delete_after_epoch_ms"));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Number value = (Number) rs.getObject(column);
        return value == null ? null : Instant.ofEpochMilli(value.longValue());
    }
}
