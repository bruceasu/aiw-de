package me.asu.aidev.development.jdbc;

import me.asu.aidev.development.domain.EngineeringGate;
import me.asu.aidev.development.domain.EngineeringGateStatus;
import me.asu.aidev.development.port.EngineeringGateRepository;
import me.asu.aidev.execution.domain.CompletionReport;
import me.asu.aidev.execution.domain.CompletionVerdict;
import me.asu.aidev.work.domain.TaskId;
import me.asu.aidev.work.domain.TaskType;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcEngineeringGateRepository implements EngineeringGateRepository {
    private static final String GATE_SELECT = """
            SELECT s.id AS source_task_id,
                   s.task_type AS source_task_type,
                   s.subject_type,
                   s.subject_id,
                   s.repository_ref,
                   sea.base_revision,
                   sea.result_revision,
                   sea.publication_ref,
                   sea.changed_files,
                   sea.engineering_gate_status,
                   sea.engineering_gate_review_task_id,
                   sea.engineering_gate_verification_task_id,
                   sea.engineering_gate_closed_at_epoch_ms,
                   rea.completion_verdict AS review_verdict,
                   rea.completion_summary AS review_summary,
                   vea.completion_verdict AS verification_verdict,
                   vea.completion_summary AS verification_summary
              FROM execution_attempt sea
              JOIN work_task s ON s.id=sea.task_id
              JOIN execution_attempt rea
                ON rea.task_id=sea.engineering_gate_review_task_id
               AND rea.state='COMPLETED'
               AND rea.attempt_no=(
                    SELECT MAX(r2.attempt_no) FROM execution_attempt r2
                     WHERE r2.task_id=sea.engineering_gate_review_task_id AND r2.state='COMPLETED')
              JOIN execution_attempt vea
                ON vea.task_id=sea.engineering_gate_verification_task_id
               AND vea.state='COMPLETED'
               AND vea.attempt_no=(
                    SELECT MAX(v2.attempt_no) FROM execution_attempt v2
                     WHERE v2.task_id=sea.engineering_gate_verification_task_id AND v2.state='COMPLETED')
            """;

    private final JdbcTemplate jdbc;

    public JdbcEngineeringGateRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean finalizeGate(TaskId sourceTaskId,
                                TaskId reviewTaskId,
                                TaskId verificationTaskId,
                                EngineeringGateStatus status,
                                Instant closedAt) {
        int changed = jdbc.update("""
                UPDATE execution_attempt
                   SET engineering_gate_status=?,
                       engineering_gate_review_task_id=?,
                       engineering_gate_verification_task_id=?,
                       engineering_gate_closed_at_epoch_ms=?
                 WHERE task_id=? AND state='COMPLETED'
                   AND attempt_no=(
                        SELECT MAX(e2.attempt_no) FROM execution_attempt e2
                         WHERE e2.task_id=? AND e2.state='COMPLETED')
                   AND engineering_gate_status IS NULL
                """, status.name(), reviewTaskId.value(), verificationTaskId.value(), closedAt.toEpochMilli(),
                sourceTaskId.value(), sourceTaskId.value());
        if (changed == 1) return true;

        return findBySourceTask(sourceTaskId)
                .map(existing -> existing.status() == status
                        && existing.reviewTaskId().equals(reviewTaskId)
                        && existing.verificationTaskId().equals(verificationTaskId))
                .orElse(false);
    }

    @Override
    public Optional<EngineeringGate> findBySourceTask(TaskId sourceTaskId) {
        List<EngineeringGate> rows = jdbc.query(GATE_SELECT + """
                 WHERE sea.task_id=? AND sea.state='COMPLETED' AND sea.engineering_gate_status IS NOT NULL
                 ORDER BY sea.attempt_no DESC
                 LIMIT 1
                """, (rs, rowNum) -> mapGate(rs), sourceTaskId.value());
        return rows.stream().findFirst();
    }

    @Override
    public Optional<EngineeringGate> findLatestPassed(String subjectType, String subjectId) {
        List<EngineeringGate> rows = jdbc.query(GATE_SELECT + """
                 WHERE s.subject_type=? AND s.subject_id=?
                   AND sea.state='COMPLETED' AND sea.engineering_gate_status='PASSED'
                 ORDER BY sea.engineering_gate_closed_at_epoch_ms DESC, sea.finished_at_epoch_ms DESC, s.id DESC
                 LIMIT 1
                """, (rs, rowNum) -> mapGate(rs), subjectType, subjectId);
        return rows.stream().findFirst();
    }

    private static EngineeringGate mapGate(ResultSet rs) throws SQLException {
        return new EngineeringGate(
                new TaskId(rs.getString("source_task_id")),
                TaskType.valueOf(rs.getString("source_task_type")),
                rs.getString("subject_type"),
                rs.getString("subject_id"),
                EngineeringGateStatus.valueOf(rs.getString("engineering_gate_status")),
                rs.getString("repository_ref"),
                rs.getString("base_revision"),
                rs.getString("result_revision"),
                rs.getString("publication_ref"),
                decodeChangedFiles(rs.getString("changed_files")),
                new TaskId(rs.getString("engineering_gate_review_task_id")),
                new CompletionReport(
                        CompletionVerdict.valueOf(rs.getString("review_verdict")),
                        rs.getString("review_summary")),
                new TaskId(rs.getString("engineering_gate_verification_task_id")),
                new CompletionReport(
                        CompletionVerdict.valueOf(rs.getString("verification_verdict")),
                        rs.getString("verification_summary")),
                Instant.ofEpochMilli(rs.getLong("engineering_gate_closed_at_epoch_ms")));
    }

    private static List<String> decodeChangedFiles(String value) {
        if (value == null || value.isBlank()) return List.of();
        return Arrays.stream(value.split("\\n"))
                .filter(s -> !s.isBlank())
                .toList();
    }
}
