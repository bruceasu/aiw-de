package me.asu.aidev.webconsole.jdbc;

import me.asu.aidev.webconsole.domain.WebConsoleApprovalDetail;
import me.asu.aidev.webconsole.domain.WebConsoleApprovalDetail.Evidence;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Private Web Console projection. A gate is evidence only when it matches the saved approval. */
@Repository
public class JdbcWebConsoleApprovalReadRepository {
    private static final String SELECT = """
            SELECT a.id, a.subject_id, a.source_task_id, a.status, a.result_revision,
                   a.engineering_gate_closed_at_epoch_ms, a.requested_by, a.channel,
                   a.requested_at_epoch_ms, a.decided_by, a.decision_note, a.decided_at_epoch_ms,
                   st.subject_type AS source_subject_type, st.subject_id AS source_subject_id,
                   (SELECT COUNT(*) FROM execution_attempt candidate
                     WHERE candidate.task_id=a.source_task_id AND candidate.state='COMPLETED'
                       AND candidate.result_revision=a.result_revision
                       AND candidate.engineering_gate_closed_at_epoch_ms=a.engineering_gate_closed_at_epoch_ms
                       AND candidate.engineering_gate_status IS NOT NULL) AS source_matches,
                   sea.id AS source_attempt_id, sea.finished_at_epoch_ms AS source_finished_at,
                   sea.completion_summary AS source_summary, sea.engineering_gate_status AS gate_status,
                   sea.publication_ref, sea.engineering_gate_review_task_id AS review_task_id,
                   sea.engineering_gate_verification_task_id AS verification_task_id,
                   rt.task_type AS review_type, rt.subject_id AS review_subject_id,
                   rt.base_revision AS review_base_revision,
                   vt.task_type AS verification_type, vt.subject_id AS verification_subject_id,
                   vt.base_revision AS verification_base_revision,
                   EXISTS (SELECT 1 FROM work_task_dependency d
                            WHERE d.task_id=sea.engineering_gate_review_task_id
                              AND d.depends_on_task_id=a.source_task_id) AS review_depends_on_source,
                   EXISTS (SELECT 1 FROM work_task_dependency d
                            WHERE d.task_id=sea.engineering_gate_verification_task_id
                              AND d.depends_on_task_id=a.source_task_id) AS verification_depends_on_source,
                   rea.id AS review_attempt_id, rea.completion_verdict AS review_verdict,
                   rea.completion_summary AS review_summary, rea.finished_at_epoch_ms AS review_finished_at,
                   vea.id AS verification_attempt_id, vea.completion_verdict AS verification_verdict,
                   vea.completion_summary AS verification_summary, vea.finished_at_epoch_ms AS verification_finished_at
              FROM approval_request a
              LEFT JOIN work_task st ON st.id=a.source_task_id
              LEFT JOIN execution_attempt sea ON sea.id=(
                   SELECT candidate.id FROM execution_attempt candidate
                    WHERE candidate.task_id=a.source_task_id AND candidate.state='COMPLETED'
                      AND candidate.result_revision=a.result_revision
                      AND candidate.engineering_gate_closed_at_epoch_ms=a.engineering_gate_closed_at_epoch_ms
                      AND candidate.engineering_gate_status IS NOT NULL
                    ORDER BY candidate.attempt_no DESC LIMIT 1)
              LEFT JOIN work_task rt ON rt.id=sea.engineering_gate_review_task_id
              LEFT JOIN work_task vt ON vt.id=sea.engineering_gate_verification_task_id
              LEFT JOIN execution_attempt rea ON rea.id=(
                   SELECT candidate.id FROM execution_attempt candidate
                    WHERE candidate.task_id=sea.engineering_gate_review_task_id
                      AND candidate.state='COMPLETED' AND candidate.finished_at_epoch_ms IS NOT NULL
                      AND candidate.finished_at_epoch_ms<=a.engineering_gate_closed_at_epoch_ms
                    ORDER BY candidate.attempt_no DESC LIMIT 1)
              LEFT JOIN execution_attempt vea ON vea.id=(
                   SELECT candidate.id FROM execution_attempt candidate
                    WHERE candidate.task_id=sea.engineering_gate_verification_task_id
                      AND candidate.state='COMPLETED' AND candidate.finished_at_epoch_ms IS NOT NULL
                      AND candidate.finished_at_epoch_ms<=a.engineering_gate_closed_at_epoch_ms
                    ORDER BY candidate.attempt_no DESC LIMIT 1)
            """;

    private final JdbcTemplate jdbc;

    public JdbcWebConsoleApprovalReadRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional(readOnly = true)
    public List<WebConsoleApprovalDetail> pending(int limit) {
        return jdbc.query(SELECT + """
                 WHERE a.status='PENDING'
                 ORDER BY a.requested_at_epoch_ms DESC, a.id DESC LIMIT ?
                """, (rs, n) -> map(rs), limit);
    }

    @Transactional(readOnly = true)
    public List<WebConsoleApprovalDetail> decided(int limit) {
        return jdbc.query(SELECT + """
                 WHERE a.status IN ('APPROVED','REJECTED')
                 ORDER BY a.decided_at_epoch_ms DESC, a.id DESC LIMIT ?
                """, (rs, n) -> map(rs), limit);
    }

    @Transactional(readOnly = true)
    public Optional<WebConsoleApprovalDetail> find(String approvalRequestId) {
        return jdbc.query(SELECT + " WHERE a.id=?", (rs, n) -> map(rs), approvalRequestId)
                .stream().findFirst();
    }

    private static WebConsoleApprovalDetail map(ResultSet rs) throws SQLException {
        String sourceProblem = null;
        if (!"AUTONOMOUS_REQUIREMENT".equals(rs.getString("source_subject_type"))
                || !rs.getString("subject_id").equals(rs.getString("source_subject_id"))) {
            sourceProblem = "Source task is missing or belongs to another Run";
        } else if (rs.getLong("source_matches") != 1) {
            sourceProblem = rs.getLong("source_matches") == 0
                    ? "Saved result revision or gate close time has no matching completed attempt"
                    : "More than one source attempt matches this approval";
        } else if (!"PASSED".equals(rs.getString("gate_status"))) {
            sourceProblem = "Matching engineering gate is not PASSED";
        } else if (instant(rs, "source_finished_at") == null
                || instant(rs, "source_finished_at").isAfter(instant(rs, "engineering_gate_closed_at_epoch_ms"))) {
            sourceProblem = "Source completion time is missing or later than the gate";
        }
        Evidence review = evidence(rs, "review", "CODE_REVIEW", sourceProblem);
        String verificationType = "INTEGRATION_VERIFICATION".equals(rs.getString("verification_type"))
                ? "INTEGRATION_VERIFICATION" : "VERIFICATION";
        Evidence verification = evidence(rs, "verification", verificationType, sourceProblem);
        String problem = sourceProblem != null ? sourceProblem : !review.available() ? review.problem()
                : !verification.available() ? verification.problem() : null;
        return new WebConsoleApprovalDetail(rs.getString("id"), rs.getString("subject_id"), rs.getString("status"),
                rs.getString("result_revision"), rs.getString("source_task_id"), rs.getString("source_attempt_id"),
                instant(rs, "source_finished_at"), rs.getString("source_summary"), rs.getString("gate_status"),
                instant(rs, "engineering_gate_closed_at_epoch_ms"), rs.getString("publication_ref"),
                review, verification, problem, rs.getString("requested_by"), rs.getString("channel"),
                instant(rs, "requested_at_epoch_ms"), rs.getString("decided_by"), rs.getString("decision_note"),
                instant(rs, "decided_at_epoch_ms"));
    }

    private static Evidence evidence(ResultSet rs, String prefix, String expectedType, String sourceProblem)
            throws SQLException {
        String taskId = rs.getString(prefix + "_task_id");
        String attemptId = rs.getString(prefix + "_attempt_id");
        String problem = sourceProblem;
        if (problem == null && (taskId == null || !expectedType.equals(rs.getString(prefix + "_type")))) {
            problem = prefix + " task is missing or has the wrong type";
        } else if (problem == null && (!rs.getString("subject_id").equals(rs.getString(prefix + "_subject_id"))
                || !rs.getString("result_revision").equals(rs.getString(prefix + "_base_revision"))
                || !rs.getBoolean(prefix + "_depends_on_source"))) {
            problem = prefix + " task does not belong to this result";
        } else if (problem == null && (attemptId == null || rs.getString(prefix + "_verdict") == null
                || rs.getString(prefix + "_summary") == null)) {
            problem = prefix + " completion evidence is missing";
        } else if (problem == null && !"PASS".equals(rs.getString(prefix + "_verdict"))) {
            problem = prefix + " verdict conflicts with the PASSED gate";
        }
        return new Evidence(taskId, attemptId, rs.getString(prefix + "_verdict"),
                rs.getString(prefix + "_summary"), instant(rs, prefix + "_finished_at"), problem);
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Number value = (Number) rs.getObject(column);
        return value == null ? null : Instant.ofEpochMilli(value.longValue());
    }
}
