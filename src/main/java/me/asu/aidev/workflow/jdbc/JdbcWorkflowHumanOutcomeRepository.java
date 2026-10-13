package me.asu.aidev.workflow.jdbc;

import java.time.Instant;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcWorkflowHumanOutcomeRepository {
    public record Outcome(String planId, String outcome, String status, String actor, String reason, Instant decidedAt) {}

    private final JdbcTemplate jdbc;

    public JdbcWorkflowHumanOutcomeRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Optional<Outcome> find(String planId) {
        return jdbc.query("SELECT * FROM flexible_workflow_human_outcome WHERE plan_id=?",
                (rs, row) -> new Outcome(rs.getString("plan_id"), rs.getString("outcome"),
                        rs.getString("status"), rs.getString("actor"), rs.getString("reason"),
                        Instant.ofEpochMilli(rs.getLong("decided_at_epoch_ms"))), planId).stream().findFirst();
    }

    public void insert(String planId, String outcome, String status, String actor, String reason, Instant at) {
        jdbc.update("""
                INSERT INTO flexible_workflow_human_outcome
                (plan_id,outcome,status,actor,reason,decided_at_epoch_ms) VALUES (?,?,?,?,?,?)
                """, planId, outcome, status, actor, reason, at.toEpochMilli());
    }

    public void apply(String planId) {
        if (jdbc.update("""
                UPDATE flexible_workflow_human_outcome SET status='APPLIED'
                 WHERE plan_id=? AND outcome='COMPLETE' AND status='PENDING'
                """, planId) != 1) throw new IllegalStateException("human completion decision is missing");
    }

    public void clearPending(String planId) {
        jdbc.update("DELETE FROM flexible_workflow_human_outcome WHERE plan_id=? AND status='PENDING'", planId);
    }
}
