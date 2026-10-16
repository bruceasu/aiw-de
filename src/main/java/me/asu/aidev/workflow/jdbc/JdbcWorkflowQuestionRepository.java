package me.asu.aidev.workflow.jdbc;

import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcWorkflowQuestionRepository {
    public record Answer(String id, String planId, String questionKey, String sourceTaskId,
            String sourceFileId, String sourceHash, String questionId, String questionText,
            String answer, String actor, long planVersion, Instant decidedAt) {}
    private final JdbcTemplate jdbc;
    public JdbcWorkflowQuestionRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<Answer> history(String planId) {
        return jdbc.query("SELECT * FROM workflow_question_decision WHERE plan_id=? ORDER BY plan_version DESC", (rs, row) ->
                new Answer(rs.getString("id"), rs.getString("plan_id"), rs.getString("question_key"),
                    rs.getString("source_task_id"), rs.getString("source_file_id"), rs.getString("source_hash"),
                    rs.getString("question_id"), rs.getString("question_text"), rs.getString("answer"),
                    rs.getString("actor"), rs.getLong("plan_version"), Instant.ofEpochMilli(rs.getLong("decided_at_epoch_ms"))), planId);
    }

    public void insert(Answer answer) {
        jdbc.update("""
                INSERT INTO workflow_question_decision
                (id,plan_id,question_key,source_task_id,source_file_id,source_hash,question_id,question_text,
                 answer,actor,plan_version,decided_at_epoch_ms) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)
                """, answer.id(), answer.planId(), answer.questionKey(), answer.sourceTaskId(), answer.sourceFileId(),
                answer.sourceHash(), answer.questionId(), answer.questionText(), answer.answer(), answer.actor(),
                answer.planVersion(), answer.decidedAt().toEpochMilli());
    }
}
