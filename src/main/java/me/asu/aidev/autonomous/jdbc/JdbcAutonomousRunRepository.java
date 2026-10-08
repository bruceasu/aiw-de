package me.asu.aidev.autonomous.jdbc;

import me.asu.aidev.approval.domain.ApprovalRequestId;
import me.asu.aidev.autonomous.domain.AutonomousRun;
import me.asu.aidev.autonomous.domain.AutonomousRunControlState;
import me.asu.aidev.autonomous.domain.AutonomousRunStatus;
import me.asu.aidev.autonomous.port.AutonomousRunRepository;
import me.asu.aidev.work.domain.TaskId;
import me.asu.aidev.work.jdbc.WorkDatabaseDialect;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcAutonomousRunRepository implements AutonomousRunRepository {
    private final JdbcTemplate jdbc;
    private final DataSource dataSource;
    private final WorkDatabaseDialect dialect;

    public JdbcAutonomousRunRepository(JdbcTemplate jdbc, DataSource dataSource, WorkDatabaseDialect dialect) {
        this.jdbc = jdbc;
        this.dataSource = dataSource;
        this.dialect = dialect;
    }

    @Override
    public boolean insertIfAbsent(AutonomousRun run) {
        return jdbc.update("""
                INSERT INTO autonomous_run(
                    id,requirement_task_id,status,terminal_source_task_id,result_revision,approval_request_id,
                    last_failed_task_id,last_failure_reason,failure_count,recovery_count,
                    last_recovered_by,last_recovered_at_epoch_ms,started_at_epoch_ms,status_updated_at_epoch_ms,completed_at_epoch_ms,
                    control_state,pause_count,resume_count,last_control_action,last_controlled_by,last_control_reason,
                    last_controlled_at_epoch_ms,cancelled_at_epoch_ms,max_parallelism,active_slots
                ) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                ON CONFLICT(id) DO NOTHING
                """,
                run.id(), run.requirementTaskId().value(), run.status().name(),
                value(run.terminalSourceTaskId()), run.resultRevision(), value(run.approvalRequestId()),
                value(run.lastFailedTaskId()), run.lastFailureReason(), run.failureCount(), run.recoveryCount(),
                run.lastRecoveredBy(), epoch(run.lastRecoveredAt()), run.startedAt().toEpochMilli(),
                run.statusUpdatedAt().toEpochMilli(), epoch(run.completedAt()),
                run.controlState().name(), run.pauseCount(), run.resumeCount(), run.lastControlAction(),
                run.lastControlledBy(), run.lastControlReason(), epoch(run.lastControlledAt()), epoch(run.cancelledAt()),
                run.maxParallelism(), run.activeSlots()) == 1;
    }

    @Override
    public Optional<AutonomousRun> find(String runId) {
        List<AutonomousRun> rows = jdbc.query("SELECT * FROM autonomous_run WHERE id=?",
                (rs, rowNum) -> map(rs), runId);
        return rows.stream().findFirst();
    }

    @Override
    public boolean lock(String runId) {
        var connection = DataSourceUtils.getConnection(dataSource);
        try {
            return dialect.lockAutonomousRun(connection, runId);
        } catch (java.sql.SQLException e) {
            throw new IllegalStateException("cannot lock autonomous run " + runId, e);
        }
    }

    @Override
    public boolean reserveExecutionSlot(String runId) {
        return jdbc.update("""
                UPDATE autonomous_run
                   SET active_slots=active_slots+1
                 WHERE id=? AND control_state='ACTIVE' AND status='RUNNING'
                   AND (max_parallelism=0 OR active_slots < max_parallelism)
                """, runId) == 1;
    }

    @Override
    public boolean releaseExecutionSlot(String runId) {
        return jdbc.update("""
                UPDATE autonomous_run
                   SET active_slots=active_slots-1
                 WHERE id=? AND active_slots > 0
                """, runId) == 1;
    }

    @Override
    public boolean updateMaxParallelism(String runId, int maxParallelism, Instant updatedAt) {
        return jdbc.update("""
                UPDATE autonomous_run
                   SET max_parallelism=?, status_updated_at_epoch_ms=?
                 WHERE id=?
                   AND control_state IN ('ACTIVE','PAUSED')
                   AND status IN ('RUNNING','FAILED')
                """, maxParallelism, updatedAt.toEpochMilli(), runId) == 1;
    }

    @Override
    public boolean recordEngineeringPassed(String runId, TaskId sourceTaskId, String resultRevision, Instant updatedAt) {
        return jdbc.update("""
                UPDATE autonomous_run
                   SET status=CASE WHEN status='RUNNING' THEN 'ENGINEERING_PASSED' ELSE status END,
                       terminal_source_task_id=?, result_revision=?, status_updated_at_epoch_ms=?
                 WHERE id=? AND control_state='ACTIVE'
                   AND status IN ('RUNNING','ENGINEERING_PASSED','WAITING_APPROVAL')
                   AND (terminal_source_task_id IS NULL OR terminal_source_task_id=?)
                   AND (result_revision IS NULL OR result_revision=?)
                   AND active_slots=0
                """, sourceTaskId.value(), resultRevision, updatedAt.toEpochMilli(), runId,
                sourceTaskId.value(), resultRevision) == 1;
    }

    @Override
    public boolean markApprovalPending(String runId, ApprovalRequestId approvalRequestId,
                                       TaskId sourceTaskId, String resultRevision, Instant updatedAt) {
        return jdbc.update("""
                UPDATE autonomous_run
                   SET status='WAITING_APPROVAL', approval_request_id=?,
                       terminal_source_task_id=?, result_revision=?, status_updated_at_epoch_ms=?
                 WHERE id=? AND control_state='ACTIVE'
                   AND status IN ('RUNNING','ENGINEERING_PASSED','WAITING_APPROVAL')
                   AND (approval_request_id IS NULL OR approval_request_id=?)
                   AND (terminal_source_task_id IS NULL OR terminal_source_task_id=?)
                   AND (result_revision IS NULL OR result_revision=?)
                   AND active_slots=0
                """, approvalRequestId.value(), sourceTaskId.value(), resultRevision, updatedAt.toEpochMilli(), runId,
                approvalRequestId.value(), sourceTaskId.value(), resultRevision) == 1;
    }

    @Override public boolean markSucceeded(String runId, ApprovalRequestId id, Instant at) {
        return markApprovalTerminal(runId, id, AutonomousRunStatus.SUCCEEDED, at);
    }
    @Override public boolean markRejected(String runId, ApprovalRequestId id, Instant at) {
        return markApprovalTerminal(runId, id, AutonomousRunStatus.REJECTED, at);
    }

    private boolean markApprovalTerminal(String runId, ApprovalRequestId approvalRequestId,
                                         AutonomousRunStatus target, Instant completedAt) {
        return jdbc.update("""
                UPDATE autonomous_run
                   SET status=?, status_updated_at_epoch_ms=?, completed_at_epoch_ms=?
                 WHERE id=? AND control_state='ACTIVE' AND status='WAITING_APPROVAL' AND approval_request_id=?
                """, target.name(), completedAt.toEpochMilli(), completedAt.toEpochMilli(),
                runId, approvalRequestId.value()) == 1;
    }

    @Override
    public boolean markFailed(String runId, TaskId failedTaskId, String reason, Instant failedAt) {
        return jdbc.update("""
                UPDATE autonomous_run
                   SET status='FAILED', last_failed_task_id=?, last_failure_reason=?,
                       failure_count=failure_count+1, status_updated_at_epoch_ms=?, completed_at_epoch_ms=NULL
                 WHERE id=? AND control_state='ACTIVE' AND status IN ('RUNNING','FAILED')
                """, failedTaskId.value(), normalize(reason), failedAt.toEpochMilli(), runId) == 1;
    }

    @Override
    public boolean markRecovered(String runId, String recoveredBy, Instant recoveredAt) {
        return jdbc.update("""
                UPDATE autonomous_run
                   SET status='RUNNING', recovery_count=recovery_count+1,
                       last_recovered_by=?, last_recovered_at_epoch_ms=?, status_updated_at_epoch_ms=?, completed_at_epoch_ms=NULL
                 WHERE id=? AND control_state='ACTIVE' AND status='FAILED'
                """, recoveredBy, recoveredAt.toEpochMilli(), recoveredAt.toEpochMilli(), runId) == 1;
    }

    @Override
    public boolean markPaused(String runId, String actor, String reason, Instant at) {
        return jdbc.update("""
                UPDATE autonomous_run
                   SET control_state='PAUSED', pause_count=pause_count+1,
                       last_control_action='PAUSE', last_controlled_by=?, last_control_reason=?,
                       last_controlled_at_epoch_ms=?, status_updated_at_epoch_ms=?
                 WHERE id=? AND control_state='ACTIVE' AND status='RUNNING' AND active_slots=0
                """, actor, normalize(reason), at.toEpochMilli(), at.toEpochMilli(), runId) == 1;
    }

    @Override
    public boolean markResumed(String runId, String actor, String reason, Instant at) {
        return jdbc.update("""
                UPDATE autonomous_run
                   SET control_state='ACTIVE', resume_count=resume_count+1,
                       last_control_action='RESUME', last_controlled_by=?, last_control_reason=?,
                       last_controlled_at_epoch_ms=?, status_updated_at_epoch_ms=?
                 WHERE id=? AND control_state='PAUSED' AND status='RUNNING'
                """, actor, normalize(reason), at.toEpochMilli(), at.toEpochMilli(), runId) == 1;
    }

    @Override
    public boolean markCancelled(String runId, String actor, String reason, Instant at) {
        return jdbc.update("""
                UPDATE autonomous_run
                   SET control_state='CANCELLED', last_control_action='CANCEL',
                       last_controlled_by=?, last_control_reason=?, last_controlled_at_epoch_ms=?,
                       cancelled_at_epoch_ms=?, status_updated_at_epoch_ms=?
                 WHERE id=? AND control_state IN ('ACTIVE','PAUSED') AND status IN ('RUNNING','FAILED') AND active_slots=0
                """, actor, normalize(reason), at.toEpochMilli(), at.toEpochMilli(), at.toEpochMilli(), runId) == 1;
    }

    private static AutonomousRun map(ResultSet rs) throws SQLException {
        return new AutonomousRun(
                rs.getString("id"), new TaskId(rs.getString("requirement_task_id")),
                AutonomousRunStatus.valueOf(rs.getString("status")),
                AutonomousRunControlState.valueOf(rs.getString("control_state")),
                taskId(rs.getString("terminal_source_task_id")), rs.getString("result_revision"),
                approvalId(rs.getString("approval_request_id")), taskId(rs.getString("last_failed_task_id")),
                rs.getString("last_failure_reason"), rs.getInt("failure_count"), rs.getInt("recovery_count"),
                rs.getString("last_recovered_by"), instant(rs, "last_recovered_at_epoch_ms"),
                rs.getInt("pause_count"), rs.getInt("resume_count"), rs.getString("last_control_action"),
                rs.getString("last_controlled_by"), rs.getString("last_control_reason"),
                instant(rs, "last_controlled_at_epoch_ms"), instant(rs, "cancelled_at_epoch_ms"),
                rs.getInt("max_parallelism"), rs.getInt("active_slots"),
                Instant.ofEpochMilli(rs.getLong("started_at_epoch_ms")),
                Instant.ofEpochMilli(rs.getLong("status_updated_at_epoch_ms")), instant(rs, "completed_at_epoch_ms"));
    }

    private static TaskId taskId(String value) { return value == null ? null : new TaskId(value); }
    private static ApprovalRequestId approvalId(String value) { return value == null ? null : new ApprovalRequestId(value); }
    private static String value(TaskId id) { return id == null ? null : id.value(); }
    private static String value(ApprovalRequestId id) { return id == null ? null : id.value(); }
    private static Long epoch(Instant value) { return value == null ? null : value.toEpochMilli(); }
    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Number value = (Number) rs.getObject(column);
        return value == null ? null : Instant.ofEpochMilli(value.longValue());
    }
    private static String normalize(String value) {
        if (value == null) return null;
        String v = value.strip();
        if (v.isEmpty()) return null;
        return v.length() <= 4096 ? v : v.substring(0, 4096);
    }
}
