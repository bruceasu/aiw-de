package me.asu.aidev.workflow.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Durable manager decisions and the set of steps intentionally omitted from execution. */
@Repository
public class JdbcWorkflowControlRepository {
    private final JdbcTemplate jdbc;

    public JdbcWorkflowControlRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public record Control(String id, String planId, String action, int fromStep, Integer targetStep,
                          String taskId, String leaseWorker, String leaseToken, String status,
                          String actor, String reason, Instant requestedAt, Instant appliedAt) {}

    public void insert(Control control) {
        jdbc.update("""
                INSERT INTO flexible_workflow_control
                (id,plan_id,action,from_step_index,target_step_index,task_id,lease_worker_id,lease_token,
                 status,actor,reason,requested_at_epoch_ms,applied_at_epoch_ms)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, control.id(), control.planId(), control.action(), control.fromStep(), control.targetStep(),
                control.taskId(), control.leaseWorker(), control.leaseToken(), control.status(), control.actor(),
                control.reason(), control.requestedAt().toEpochMilli(),
                control.appliedAt() == null ? null : control.appliedAt().toEpochMilli());
    }

    public Optional<Control> pending(String planId) {
        return jdbc.query("SELECT * FROM flexible_workflow_control WHERE plan_id=? AND status='PENDING_STOP'",
                this::map, planId).stream().findFirst();
    }

    public List<Control> history(String planId) {
        return jdbc.query("SELECT * FROM flexible_workflow_control WHERE plan_id=? ORDER BY requested_at_epoch_ms,id",
                this::map, planId);
    }

    public void finish(String id, String status, Instant at) {
        if (jdbc.update("""
                UPDATE flexible_workflow_control SET status=?,applied_at_epoch_ms=?
                 WHERE id=? AND status='PENDING_STOP'
                """, status, at.toEpochMilli(), id) != 1) {
            throw new IllegalStateException("workflow control changed before completion: " + id);
        }
    }

    public void skip(String planId, int step, String controlId) {
        jdbc.update("INSERT INTO flexible_workflow_skipped_step(plan_id,step_index,control_id) VALUES (?,?,?)",
                planId, step, controlId);
    }

    public boolean skipped(String planId, int step) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM flexible_workflow_skipped_step WHERE plan_id=? AND step_index=?
                """, Integer.class, planId, step);
        return count != null && count > 0;
    }

    private Control map(ResultSet rs, int row) throws SQLException {
        Number target = (Number) rs.getObject("target_step_index");
        Number applied = (Number) rs.getObject("applied_at_epoch_ms");
        return new Control(rs.getString("id"), rs.getString("plan_id"), rs.getString("action"),
                rs.getInt("from_step_index"), target == null ? null : target.intValue(),
                rs.getString("task_id"), rs.getString("lease_worker_id"), rs.getString("lease_token"),
                rs.getString("status"), rs.getString("actor"), rs.getString("reason"),
                Instant.ofEpochMilli(rs.getLong("requested_at_epoch_ms")),
                applied == null ? null : Instant.ofEpochMilli(applied.longValue()));
    }
}
