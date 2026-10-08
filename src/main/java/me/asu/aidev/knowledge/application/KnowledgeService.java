package me.asu.aidev.knowledge.application;

import me.asu.aidev.execution.domain.CompletionReport;
import me.asu.aidev.execution.domain.WorkspaceResult;
import me.asu.aidev.knowledge.domain.KnowledgeDocument;
import me.asu.aidev.work.domain.TaskType;
import me.asu.aidev.work.domain.WorkTask;
import me.asu.aidev.work.port.WorkTaskRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class KnowledgeService {
    private final JdbcTemplate jdbc;
    private final WorkTaskRepository tasks;

    public KnowledgeService(JdbcTemplate jdbc, WorkTaskRepository tasks) {
        this.jdbc = jdbc;
        this.tasks = tasks;
    }

    /** Called inside the authoritative task-completion transaction. */
    public void record(WorkTask task, CompletionReport report, WorkspaceResult workspaceResult, Instant completedAt) {
        if (!isDocumentType(task.type())) return;
        if (report == null) throw new IllegalStateException("knowledge document requires a completion report");
        jdbc.update("""
                INSERT INTO knowledge_document(
                    id, run_id, document_type, repository_ref, base_revision, result_revision,
                    attempt_no, content_hash, markdown, created_at_epoch_ms
                ) VALUES(?,?,?,?,?,?,?,?,?,?)
                """, task.id().value(), task.subjectId(), task.type().name(), task.repositoryRef(),
                task.baseRevision(), workspaceResult == null ? null : workspaceResult.resultRevision(),
                tasks.attemptNo(task.id()), sha256(report.summary()), report.summary(), completedAt.toEpochMilli());
    }

    @Transactional(readOnly = true)
    public Optional<KnowledgeDocument> find(String id) {
        var rows = jdbc.query("SELECT * FROM knowledge_document WHERE id=?", (rs, row) -> new KnowledgeDocument(
                rs.getString("id"), rs.getString("run_id"), TaskType.valueOf(rs.getString("document_type")),
                rs.getString("repository_ref"), rs.getString("base_revision"), rs.getString("result_revision"),
                rs.getInt("attempt_no"), rs.getString("content_hash"), rs.getString("markdown"),
                Instant.ofEpochMilli(rs.getLong("created_at_epoch_ms")), upstreamIds(rs.getString("id"))), id);
        return rows.stream().findFirst();
    }

    @Transactional(readOnly = true)
    public List<KnowledgeDocument> search(String repositoryRef, String runId, String query, int limit) {
        if (limit < 1 || limit > 100) throw new IllegalArgumentException("limit must be between 1 and 100");
        return jdbc.query("""
                SELECT id FROM knowledge_document
                 WHERE (? IS NULL OR repository_ref = ?)
                   AND (? IS NULL OR run_id = ?)
                   AND (? IS NULL OR LOWER(markdown) LIKE ? ESCAPE '!')
                 ORDER BY created_at_epoch_ms DESC, id DESC
                 LIMIT ?
                """, (rs, row) -> find(rs.getString("id")).orElseThrow(),
                repositoryRef, repositoryRef, runId, runId,
                query, query == null ? null : "%" + query.toLowerCase(java.util.Locale.ROOT)
                        .replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%", limit);
    }

    private List<String> upstreamIds(String id) {
        return jdbc.query("""
                SELECT kd.id FROM work_task_dependency dep
                  JOIN knowledge_document kd ON kd.id = dep.depends_on_task_id
                 WHERE dep.task_id=? ORDER BY kd.id
                """, (rs, row) -> rs.getString(1), id);
    }

    private static boolean isDocumentType(TaskType type) {
        return type == TaskType.REQUIREMENT_ANALYSIS || type == TaskType.SOLUTION_ARCHITECTURE
                || type == TaskType.IMPLEMENTATION_PLANNING;
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 unavailable", error);
        }
    }
}
