package me.asu.aidev.workflow.jdbc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcWorkflowRepairAuthorizationRepository {
    public record Authorization(String id, String planId, long planVersion, String sourceReviewId,
            long sourceTaskVersion, String scope, String reason, String grantedBy, String delegateWorkerId,
            String decisionTaskId, String repairTaskId, String status, Instant createdAt, Instant expiresAt) {}

    private final JdbcTemplate jdbc;
    public JdbcWorkflowRepairAuthorizationRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void insert(Authorization grant) {
        jdbc.update("""
                INSERT INTO workflow_repair_authorization
                (id,plan_id,plan_version,source_review_id,source_task_version,scope,reason,granted_by,
                 delegate_worker_id,decision_task_id,repair_task_id,round_budget,status,created_at_epoch_ms,expires_at_epoch_ms)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,1,?,?,?)
                """, grant.id(), grant.planId(), grant.planVersion(), grant.sourceReviewId(), grant.sourceTaskVersion(),
                grant.scope(), grant.reason(), grant.grantedBy(), grant.delegateWorkerId(), grant.decisionTaskId(),
                grant.repairTaskId(), grant.status(), grant.createdAt().toEpochMilli(), grant.expiresAt().toEpochMilli());
    }

    public List<Authorization> list(String planId) {
        return jdbc.query("SELECT * FROM workflow_repair_authorization WHERE plan_id=? ORDER BY created_at_epoch_ms DESC,id DESC LIMIT 100",
                this::map, planId);
    }

    private Authorization map(ResultSet rs, int n) throws SQLException {
        return new Authorization(rs.getString("id"), rs.getString("plan_id"), rs.getLong("plan_version"),
                        rs.getString("source_review_id"), rs.getLong("source_task_version"), rs.getString("scope"),
                        rs.getString("reason"), rs.getString("granted_by"), rs.getString("delegate_worker_id"),
                        rs.getString("decision_task_id"), rs.getString("repair_task_id"), rs.getString("status"),
                        Instant.ofEpochMilli(rs.getLong("created_at_epoch_ms")), Instant.ofEpochMilli(rs.getLong("expires_at_epoch_ms")));
    }

    public Optional<Authorization> find(String id) {
        return jdbc.query("SELECT * FROM workflow_repair_authorization WHERE id=?", this::map, id).stream().findFirst();
    }

    public Optional<Authorization> decision(String taskId) {
        return jdbc.query("SELECT * FROM workflow_repair_authorization WHERE decision_task_id=?", this::map, taskId).stream().findFirst();
    }

    public java.util.Map<String, String> decisionReports(String planId) {
        java.util.Map<String, String> result = new java.util.LinkedHashMap<>();
        jdbc.query("""
                SELECT g.id,a.completion_summary,a.termination_reason FROM workflow_repair_authorization g
                JOIN execution_attempt a ON a.task_id=g.decision_task_id
                WHERE g.plan_id=? AND a.attempt_no=(SELECT MAX(b.attempt_no) FROM execution_attempt b WHERE b.task_id=a.task_id)
                """, (org.springframework.jdbc.core.RowCallbackHandler) rs -> {
            String report = rs.getString("completion_summary");
            if (report == null) report = rs.getString("termination_reason");
            if (report != null) result.put(rs.getString("id"), report);
        }, planId);
        return java.util.Map.copyOf(result);
    }

    public boolean finish(String id, String status, String repairTaskId, Instant now) {
        return jdbc.update("""
                UPDATE workflow_repair_authorization SET status=?,repair_task_id=?,decided_at_epoch_ms=?
                WHERE id=? AND status='PENDING'
                """, status, repairTaskId, now.toEpochMilli(), id) == 1;
    }

    public Optional<String> completedWorker(String taskId) {
        return jdbc.queryForList("""
                SELECT worker_id FROM execution_attempt WHERE task_id=? AND state='COMPLETED'
                ORDER BY attempt_no DESC LIMIT 1
                """, String.class, taskId).stream().findFirst();
    }

    public boolean planUnchanged(Authorization grant) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM flexible_workflow_plan WHERE id=? AND state='CONFIRMED' AND version=?",
                Integer.class, grant.planId(), grant.planVersion());
        return count != null && count == 1;
    }
}
