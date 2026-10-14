package me.asu.aidev.workflow.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Read-only execution history for a plan task, excluding lease credentials. */
@Repository
public class JdbcWorkflowTaskReadRepository {
    public record Attempt(String id, int attemptNo, int instructionRevision, String workerId,
                          String state, Instant createdAt, Instant startedAt, Instant finishedAt,
                          String failureCategory, String failureCode, String verdict,
                          String report, String resultRevision) {}
    public record RetryEvent(String actor, String reason, Instant requestedAt) {}

    private final JdbcTemplate jdbc;

    public JdbcWorkflowTaskReadRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public List<Attempt> attempts(String taskId) {
        return jdbc.query("""
                SELECT id, attempt_no, instruction_revision, worker_id, state,
                       created_at_epoch_ms, started_at_epoch_ms, finished_at_epoch_ms,
                       failure_category, termination_reason, completion_verdict,
                       completion_summary, result_revision
                  FROM execution_attempt
                 WHERE task_id=?
                 ORDER BY attempt_no DESC, created_at_epoch_ms DESC, id
                """, (rs, n) -> new Attempt(rs.getString("id"), rs.getInt("attempt_no"),
                rs.getInt("instruction_revision"), rs.getString("worker_id"), rs.getString("state"),
                instant(rs, "created_at_epoch_ms"), instant(rs, "started_at_epoch_ms"),
                instant(rs, "finished_at_epoch_ms"), rs.getString("failure_category"),
                code(rs.getString("termination_reason")), rs.getString("completion_verdict"),
                rs.getString("completion_summary"), rs.getString("result_revision")), taskId);
    }

    @Transactional(readOnly = true)
    public List<RetryEvent> retries(String taskId) {
        return jdbc.query("""
                SELECT actor, reason, created_at_epoch_ms
                  FROM work_task_control_event
                 WHERE task_id=? AND action='RETRY_FAILED'
                 ORDER BY created_at_epoch_ms DESC, id DESC
                 LIMIT 50
                """, (rs, n) -> new RetryEvent(rs.getString("actor"), rs.getString("reason"),
                instant(rs, "created_at_epoch_ms")), taskId);
    }

    private static String code(String value) {
        return value != null && value.matches("[A-Z][A-Z0-9_]{0,119}") ? value : null;
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Number value = (Number) rs.getObject(column);
        return value == null ? null : Instant.ofEpochMilli(value.longValue());
    }
}
