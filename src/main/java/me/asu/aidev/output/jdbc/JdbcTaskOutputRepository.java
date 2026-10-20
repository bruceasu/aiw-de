package me.asu.aidev.output.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import me.asu.aidev.output.domain.TaskOutputBundle;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import com.fasterxml.jackson.databind.ObjectMapper;

@Repository
public class JdbcTaskOutputRepository {
    private static final String SELECT_BUNDLE = """
            SELECT b.manifest_json,COALESCE(a.state,b.execution_state),
                EXISTS(SELECT 1 FROM task_output_review r WHERE r.source_attempt_id=b.attempt_id)
            FROM task_output_bundle b
            LEFT JOIN execution_attempt a ON a.id=b.attempt_id
            """;
    private final JdbcTemplate jdbc;
    private final ObjectMapper json = new ObjectMapper();

    public JdbcTaskOutputRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(TaskOutputBundle bundle, List<String> contents) {
        jdbc.update("""
                INSERT INTO task_output_bundle(attempt_id,task_id,repository_ref,base_revision,result_revision,
                    directory,created_at_epoch_ms,manifest_json) VALUES(?,?,?,?,?,?,?,?)
                """, bundle.attemptId(), bundle.taskId(), bundle.repositoryRef(), bundle.baseRevision(), bundle.resultRevision(),
                bundle.directory(), bundle.createdAtEpochMs(), encode(bundle));
        for (int i = 0; i < bundle.files().size(); i++) {
            var file = bundle.files().get(i);
            jdbc.update("""
                    INSERT INTO task_output_file(id,attempt_id,relative_path,kind,content_hash,byte_count,
                        content_base64) VALUES(?,?,?,?,?,?,?)
                    """, file.id(), bundle.attemptId(), file.path(), file.kind(), file.sha256(), file.bytes(), contents.get(i));
        }
    }

    public Optional<TaskOutputBundle> find(String attemptId) {
        return jdbc.query(SELECT_BUNDLE + "WHERE b.attempt_id=?", this::mapBundle, attemptId).stream().findFirst();
    }

    public List<TaskOutputBundle> forTask(String taskId) {
        // A mapper must not borrow another connection while this query holds the SQLite pool's sole connection.
        return jdbc.query(SELECT_BUNDLE + "WHERE b.task_id=? ORDER BY b.created_at_epoch_ms DESC,b.attempt_id DESC LIMIT 100",
                this::mapBundle, taskId);
    }

    private TaskOutputBundle mapBundle(ResultSet rs, int row) throws SQLException {
        TaskOutputBundle b;
        try { b = json.readValue(rs.getString(1), TaskOutputBundle.class); }
        catch (com.fasterxml.jackson.core.JsonProcessingException error) { throw new IllegalStateException("invalid output manifest", error); }
        String state = rs.getString(2);
        String review = rs.getBoolean(3) ? "PASS" : "NOT_REQUIRED".equals(b.reviewState()) ? "NOT_REQUIRED"
                : "COMPLETED".equals(state) ? "PENDING" : "UNFINISHED";
        return new TaskOutputBundle(b.attemptId(), b.taskId(), b.repositoryRef(), b.baseRevision(), b.resultRevision(),
                b.directory(), b.createdAtEpochMs(), state, review, b.files());
    }

    public Optional<TaskOutputBundle> completed(String taskId) {
        return forTask(taskId).stream().filter(b -> "COMPLETED".equals(b.executionState())).findFirst();
    }

    public void pin(String consumerAttemptId, String sourceAttemptId) {
        jdbc.update("INSERT INTO task_output_input VALUES(?,?) ON CONFLICT DO NOTHING", consumerAttemptId, sourceAttemptId);
    }

    public boolean pinned(String consumerAttemptId, String sourceAttemptId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM task_output_input WHERE consumer_attempt_id=? AND source_attempt_id=?",
                Integer.class, consumerAttemptId, sourceAttemptId) == 1;
    }

    public void observe(String attemptId) {
        jdbc.update("""
                UPDATE task_output_bundle SET execution_state=COALESCE(
                    (SELECT state FROM execution_attempt WHERE id=?),execution_state) WHERE attempt_id=?
                """, attemptId, attemptId);
    }

    public void snapshotPlan(String planId) {
        var ids = jdbc.query("""
                SELECT a.id FROM execution_attempt a JOIN work_task t ON t.id=a.task_id
                WHERE t.subject_type='flexible-plan' AND t.subject_id=?
                """, (rs, row) -> rs.getString(1), planId);
        for (String id : ids) { observe(id); recordReviews(id); }
    }

    public record StoredFile(String attemptId, TaskOutputBundle.File file, String contentBase64) {}

    public StoredFile file(String id) {
        return jdbc.query("SELECT * FROM task_output_file WHERE id=?", (rs, row) -> new StoredFile(
                rs.getString("attempt_id"), new TaskOutputBundle.File(rs.getString("id"), rs.getString("relative_path"),
                rs.getString("kind"), rs.getString("content_hash"), rs.getLong("byte_count")),
                rs.getString("content_base64")), id).stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("output file not found"));
    }

    public boolean reviewed(String sourceTaskId, String sourceAttemptId, String reviewTaskId) {
        if (jdbc.queryForObject("SELECT COUNT(*) FROM task_output_review WHERE source_attempt_id=? AND review_task_id=?",
                Integer.class, sourceAttemptId, reviewTaskId) > 0) return true;
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM work_task_dependency d JOIN work_task t ON t.id=d.task_id
                WHERE d.task_id=? AND d.depends_on_task_id=? AND t.state='COMPLETED'
                AND (t.skill_ref IN ('workflow-stage-reviewer','software-code-reviewer','software-test-verifier')
                    OR t.task_type IN ('CODE_REVIEW','VERIFICATION','INTEGRATION_VERIFICATION'))
                AND EXISTS (SELECT 1 FROM execution_attempt a WHERE a.task_id=t.id
                    AND a.state='COMPLETED' AND a.completion_verdict='PASS'
                    AND a.worker_id<>(SELECT worker_id FROM execution_attempt WHERE id=?))
                """, Integer.class, reviewTaskId, sourceTaskId, sourceAttemptId) > 0;
    }

    public void recordReviews(String attemptId) {
        jdbc.update("""
                INSERT INTO task_output_review(source_attempt_id,review_task_id,review_attempt_id,
                    review_worker_id,source_worker_id,reviewed_at_epoch_ms)
                SELECT b.attempt_id,a.task_id,a.id,a.worker_id,s.worker_id,a.finished_at_epoch_ms
                FROM execution_attempt a JOIN work_task t ON t.id=a.task_id
                JOIN task_output_input i ON i.consumer_attempt_id=a.id
                JOIN task_output_bundle b ON b.attempt_id=i.source_attempt_id
                JOIN execution_attempt s ON s.id=b.attempt_id
                JOIN work_task_dependency d ON d.task_id=a.task_id AND d.depends_on_task_id=b.task_id
                WHERE a.id=? AND a.state='COMPLETED' AND a.completion_verdict='PASS'
                AND s.state='COMPLETED' AND a.worker_id<>s.worker_id
                AND (t.skill_ref IN ('workflow-stage-reviewer','software-code-reviewer','software-test-verifier')
                    OR t.task_type IN ('CODE_REVIEW','VERIFICATION','INTEGRATION_VERIFICATION'))
                ON CONFLICT DO NOTHING
                """, attemptId);
    }

    public record Publication(String fileId, String reviewTaskId, String targetPath) {}

    public Optional<Publication> publication(String planId) {
        return jdbc.query("SELECT file_id,review_task_id,target_path FROM task_output_publication WHERE plan_id=?",
                (rs, row) -> new Publication(rs.getString(1), rs.getString(2), rs.getString(3)), planId).stream().findFirst();
    }
    public String publicationReason(String planId) {
        return jdbc.queryForObject("SELECT reason FROM task_output_publication WHERE plan_id=?",String.class,planId);
    }

    public void publication(String planId, String fileId, String reviewTaskId, String targetPath, String actor, String reason, long createdAt) {
        jdbc.update("INSERT INTO task_output_publication VALUES(?,?,?,?,?,?,?)",
                planId, fileId, reviewTaskId, targetPath, actor, reason, createdAt);
    }

    private String encode(Object value) {
        try { return json.writeValueAsString(value); }
        catch (com.fasterxml.jackson.core.JsonProcessingException error) { throw new IllegalStateException("output manifest serialization failed", error); }
    }
}
