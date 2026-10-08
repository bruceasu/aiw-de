package me.asu.aidev.webconsole.jdbc;

import me.asu.aidev.webconsole.domain.WebConsoleMissionDetail.Attempt;
import me.asu.aidev.webconsole.domain.WebConsoleMissionDetail.Requirement;
import me.asu.aidev.webconsole.domain.WebConsoleMissionDetail.RunSignal;
import me.asu.aidev.webconsole.domain.WebConsoleTaskSummary;
import me.asu.aidev.work.domain.CapabilitySetCodec;
import me.asu.aidev.work.domain.TaskId;
import me.asu.aidev.work.domain.TaskInstructionRevision;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** All filters precede LIMIT. Row mappers never issue nested JDBC queries. */
@Repository
public class JdbcWebConsoleMissionReadRepository {
    public static final int TASK_LIMIT = 200;
    public static final int DEPENDENCY_LIMIT = 1200;
    public static final int ATTEMPT_LIMIT = 300;
    public static final int SIGNAL_LIMIT = 20;
    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate named;

    public JdbcWebConsoleMissionReadRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.named = new NamedParameterJdbcTemplate(jdbc);
    }

    public Requirement requirement(String runId) {
        return jdbc.queryForObject("""
                SELECT ar.requirement_task_id, t.instruction
                  FROM autonomous_run ar
                  LEFT JOIN work_task t ON t.id=ar.requirement_task_id
                   AND t.subject_type='AUTONOMOUS_REQUIREMENT' AND t.subject_id=ar.id
                 WHERE ar.id=?
                """, (rs, n) -> new Requirement(rs.getString("requirement_task_id"),
                rs.getString("instruction"), rs.getString("instruction") != null), runId);
    }

    public List<WebConsoleTaskSummary> tasks(String runId) {
        List<WebConsoleTaskSummary> rows = jdbc.query("""
                SELECT wt.id, wt.task_type, wt.state, wt.required_capabilities, wt.current_worker_id, wt.attempt_no,
                       wt.repository_ref, wt.base_revision, wt.instruction, wt.created_at_epoch_ms, wt.updated_at_epoch_ms,
                       wt.version, wt.instruction_revision,
                       CASE WHEN ea.state IN ('CLAIMED','RUNNING') THEN ea.instruction_revision END AS executing_instruction_revision,
                       ea.stop_requested_at_epoch_ms, ea.stop_acknowledged_at_epoch_ms
                  FROM work_task wt
                  LEFT JOIN execution_attempt ea ON ea.task_id=wt.id AND ea.attempt_no=wt.attempt_no
                 WHERE wt.subject_type='AUTONOMOUS_REQUIREMENT' AND wt.subject_id=?
                 ORDER BY wt.created_at_epoch_ms DESC, wt.id DESC LIMIT ?
                """, (rs, n) -> new WebConsoleTaskSummary(rs.getString("id"), rs.getString("task_type"),
                rs.getString("state"), CapabilitySetCodec.decode(rs.getString("required_capabilities")),
                rs.getString("current_worker_id"), rs.getInt("attempt_no"), rs.getString("repository_ref"),
                rs.getString("base_revision"), rs.getString("instruction"),
                instant(rs, "created_at_epoch_ms"), instant(rs, "updated_at_epoch_ms"), rs.getLong("version"),
                rs.getInt("instruction_revision"), integer(rs, "executing_instruction_revision"),
                instant(rs, "stop_requested_at_epoch_ms"), instant(rs, "stop_acknowledged_at_epoch_ms"),
                List.of(), List.of()), runId, TASK_LIMIT);
        List<String> ids = rows.stream().map(WebConsoleTaskSummary::taskId).toList();
        if (ids.isEmpty()) return rows;
        Map<String, List<TaskInstructionRevision>> revisions = instructionRevisions(ids);
        Map<String, List<String>> corrections = corrections(ids);
        return rows.stream().map(task -> new WebConsoleTaskSummary(task.taskId(), task.taskType(), task.state(),
                task.requiredCapabilities(), task.currentWorkerId(), task.attemptNo(), task.repositoryRef(),
                task.baseRevision(), task.instruction(), task.createdAt(), task.updatedAt(), task.version(),
                task.instructionRevision(), task.executingInstructionRevision(), task.stopRequestedAt(),
                task.stopAcknowledgedAt(), revisions.getOrDefault(task.taskId(), List.of()),
                corrections.getOrDefault(task.taskId(), List.of()))).toList();
    }

    private Map<String, List<TaskInstructionRevision>> instructionRevisions(List<String> taskIds) {
        Map<String, List<TaskInstructionRevision>> byTask = new LinkedHashMap<>();
        taskIds.forEach(id -> byTask.put(id, new ArrayList<>()));
        named.query("""
                SELECT task_id, revision, instruction, actor, reason, predecessor_revision, created_at_epoch_ms
                  FROM work_task_instruction_revision WHERE task_id IN (:ids)
                 ORDER BY task_id, revision
                """, Map.of("ids", taskIds), rs -> {
            String taskId = rs.getString("task_id");
            byTask.computeIfAbsent(taskId, ignored -> new ArrayList<>())
                    .add(new TaskInstructionRevision(new TaskId(taskId), rs.getInt("revision"),
                    rs.getString("instruction"), rs.getString("actor"), rs.getString("reason"),
                    integer(rs, "predecessor_revision"), instant(rs, "created_at_epoch_ms")));
            return null;
        });
        return byTask;
    }

    private Map<String, List<String>> corrections(List<String> taskIds) {
        Map<String, List<String>> byTask = new LinkedHashMap<>();
        taskIds.forEach(id -> byTask.put(id, new ArrayList<>()));
        named.query("""
                SELECT completed_task_id, correction_task_id FROM work_task_correction
                 WHERE completed_task_id IN (:ids) ORDER BY created_at_epoch_ms, correction_task_id
                """, Map.of("ids", taskIds), rs -> {
            String completedTaskId = rs.getString("completed_task_id");
            byTask.computeIfAbsent(completedTaskId, ignored -> new ArrayList<>())
                    .add(rs.getString("correction_task_id"));
            return null;
        });
        return byTask;
    }

    public long dependencyCount(String runId) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM work_task_dependency d JOIN work_task t ON t.id=d.task_id
                 WHERE t.subject_type='AUTONOMOUS_REQUIREMENT' AND t.subject_id=?
                """, Long.class, runId);
    }

    public long displayedTaskDependencyCount(List<String> taskIds) {
        if (taskIds.isEmpty()) return 0;
        return named.queryForObject("SELECT COUNT(*) FROM work_task_dependency WHERE task_id IN (:ids)",
                Map.of("ids", taskIds), Long.class);
    }

    public List<Dependency> dependencies(List<String> taskIds) {
        if (taskIds.isEmpty()) return List.of();
        return named.query("""
                SELECT d.task_id, d.depends_on_task_id, p.subject_type, p.subject_id
                  FROM work_task_dependency d LEFT JOIN work_task p ON p.id=d.depends_on_task_id
                 WHERE d.task_id IN (:ids)
                 ORDER BY d.task_id, d.depends_on_task_id LIMIT :limit
                """, Map.of("ids", taskIds, "limit", DEPENDENCY_LIMIT),
                (rs, n) -> new Dependency(rs.getString("task_id"), rs.getString("depends_on_task_id"),
                        rs.getString("subject_type"), rs.getString("subject_id")));
    }

    public List<Attempt> attempts(String runId) {
        return jdbc.query("""
                SELECT a.id, a.task_id, a.attempt_no, a.worker_id, a.state, a.created_at_epoch_ms,
                       a.started_at_epoch_ms, a.finished_at_epoch_ms, a.termination_reason,
                       a.failure_category, a.completion_verdict, a.completion_summary
                  FROM execution_attempt a JOIN work_task t ON t.id=a.task_id
                 WHERE t.subject_type='AUTONOMOUS_REQUIREMENT' AND t.subject_id=?
                 ORDER BY a.created_at_epoch_ms DESC, a.id DESC LIMIT ?
                """, (rs, n) -> new Attempt(rs.getString("id"), rs.getString("task_id"),
                rs.getInt("attempt_no"), rs.getString("worker_id"), rs.getString("state"),
                instant(rs, "created_at_epoch_ms"), instant(rs, "started_at_epoch_ms"),
                instant(rs, "finished_at_epoch_ms"), rs.getString("termination_reason"),
                rs.getString("failure_category"), rs.getString("completion_verdict"),
                rs.getString("completion_summary"), null, null), runId, ATTEMPT_LIMIT);
    }

    public long signalCount(String runId) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM autonomous_run_event
                 WHERE run_id=? AND event_type IN ('FAILED','RECOVERED')
                """, Long.class, runId);
    }

    public List<RunSignal> signals(String runId) {
        return jdbc.query("""
                SELECT sequence_no, event_type, related_task_id, actor, detail, occurred_at_epoch_ms
                  FROM autonomous_run_event
                 WHERE run_id=? AND event_type IN ('FAILED','RECOVERED')
                 ORDER BY occurred_at_epoch_ms DESC, sequence_no DESC LIMIT ?
                """, (rs, n) -> signal(rs), runId, SIGNAL_LIMIT);
    }

    public RunSignal latestFailure(String runId) {
        var rows = jdbc.query("""
                SELECT sequence_no, event_type, related_task_id, actor, detail, occurred_at_epoch_ms
                  FROM autonomous_run_event WHERE run_id=? AND event_type='FAILED'
                 ORDER BY occurred_at_epoch_ms DESC, sequence_no DESC LIMIT 1
                """, (rs, n) -> signal(rs), runId);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private static RunSignal signal(ResultSet rs) throws SQLException {
        return new RunSignal(rs.getLong("sequence_no"), rs.getString("event_type"),
                rs.getString("related_task_id"), rs.getString("actor"), rs.getString("detail"),
                instant(rs, "occurred_at_epoch_ms"), null);
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Number value = (Number) rs.getObject(column);
        return value == null ? null : Instant.ofEpochMilli(value.longValue());
    }

    private static Integer integer(ResultSet rs, String column) throws SQLException {
        Number value = (Number) rs.getObject(column);
        return value == null ? null : value.intValue();
    }

    public record Dependency(String taskId, String prerequisiteId, String subjectType, String subjectId) {}
}
