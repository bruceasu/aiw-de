package me.asu.aidev.workflow.jdbc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcWorkflowPlanArchiveRepository {
    private me.asu.aidev.output.jdbc.JdbcTaskOutputRepository taskOutputs;

    @org.springframework.beans.factory.annotation.Autowired
    public void setTaskOutputs(me.asu.aidev.output.jdbc.JdbcTaskOutputRepository taskOutputs) {
        this.taskOutputs = taskOutputs;
    }
    private static final String TASKS = "SELECT id FROM work_task WHERE subject_type='flexible-plan' AND subject_id=?";
    private final JdbcTemplate jdbc;

    public JdbcWorkflowPlanArchiveRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public boolean archived(String id) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM web_console_plan_archive WHERE plan_id=?", Integer.class, id);
        return count != null && count > 0;
    }

    public Optional<Instant> deleteAfter(String id) {
        var rows = jdbc.query("SELECT delete_after_epoch_ms FROM web_console_plan_archive WHERE plan_id=?",
                (rs, row) -> {
                    Number value = (Number) rs.getObject(1);
                    return value == null ? null : Instant.ofEpochMilli(value.longValue());
                }, id);
        return Optional.ofNullable(rows.isEmpty() ? null : rows.getFirst());
    }

    public boolean archive(String id, String actor, Instant at) {
        return jdbc.update("""
                INSERT INTO web_console_plan_archive(plan_id,archived_by,archived_at_epoch_ms)
                SELECT id,?,? FROM flexible_workflow_plan WHERE id=?
                  AND NOT EXISTS (SELECT 1 FROM web_console_plan_archive WHERE plan_id=?)
                """, actor, at.toEpochMilli(), id, id) == 1;
    }

    public boolean scheduleDeletion(String id, String actor, Instant now, Instant due) {
        return jdbc.update("""
                UPDATE web_console_plan_archive
                   SET delete_requested_by=?,delete_requested_at_epoch_ms=?,delete_after_epoch_ms=?
                 WHERE plan_id=? AND delete_after_epoch_ms IS NULL
                """, actor, now.toEpochMilli(), due.toEpochMilli(), id) == 1;
    }

    public boolean undoDeletion(String id, Instant now) {
        return jdbc.update("""
                UPDATE web_console_plan_archive
                   SET delete_requested_by=NULL,delete_requested_at_epoch_ms=NULL,delete_after_epoch_ms=NULL
                 WHERE plan_id=? AND delete_after_epoch_ms>?
                """, id, now.toEpochMilli()) == 1;
    }

    public List<String> dueIds(Instant now) {
        return jdbc.query("""
                SELECT plan_id FROM web_console_plan_archive WHERE delete_after_epoch_ms<=?
                 ORDER BY delete_after_epoch_ms,plan_id
                """, (rs, row) -> rs.getString(1), now.toEpochMilli());
    }

    @Transactional
    public boolean purge(String id, Instant now) {
        if (jdbc.update("""
                UPDATE web_console_plan_archive SET delete_after_epoch_ms=delete_after_epoch_ms
                 WHERE plan_id=? AND delete_after_epoch_ms<=?
                """, id, now.toEpochMilli()) != 1) return false;
        Integer active = jdbc.queryForObject("""
                SELECT COUNT(*) FROM work_task WHERE subject_type='flexible-plan' AND subject_id=?
                  AND state NOT IN ('COMPLETED','FAILED','CANCELLED')
                """, Integer.class, id);
        if (active == null || active != 0) throw new IllegalStateException("plan still has unfinished tasks: " + id);
        Integer external = jdbc.queryForObject("""
                SELECT COUNT(*) FROM execution_attempt e JOIN work_task owner ON owner.id=e.task_id
                 WHERE (owner.subject_type<>'flexible-plan' OR owner.subject_id<>?)
                   AND (e.engineering_gate_review_task_id IN (%s)
                     OR e.engineering_gate_verification_task_id IN (%s))
                """.formatted(TASKS, TASKS), Integer.class, id, id, id);
        if (external == null || external != 0) {
            throw new IllegalStateException("another attempt refers to plan tasks: " + id);
        }
        jdbc.update("DELETE FROM web_console_plan_archive WHERE plan_id=?", id);
        jdbc.update("DELETE FROM flexible_workflow_skipped_step WHERE plan_id=?", id);
        jdbc.update("DELETE FROM flexible_workflow_control WHERE plan_id=?", id);
        jdbc.update("DELETE FROM workflow_question_decision WHERE plan_id=?", id);
        jdbc.update("DELETE FROM flexible_workflow_human_outcome WHERE plan_id=?", id);
        jdbc.update("DELETE FROM approval_request WHERE source_task_id IN (" + TASKS + ")", id);
        jdbc.update("DELETE FROM knowledge_document WHERE id IN (" + TASKS + ")", id);
        jdbc.update("DELETE FROM work_task_control_event WHERE task_id IN (" + TASKS + ")", id);
        jdbc.update("DELETE FROM work_task_correction WHERE completed_task_id IN (" + TASKS + ") AND correction_task_id IN (" + TASKS + ")", id, id);
        jdbc.update("DELETE FROM work_task_workspace_replacement WHERE source_task_id IN (" + TASKS + ") AND (replacement_task_id IS NULL OR replacement_task_id IN (" + TASKS + "))", id, id);
        jdbc.update("DELETE FROM work_task_skill_preference WHERE task_id IN (" + TASKS + ")", id);
        jdbc.update("DELETE FROM work_task_instruction_revision WHERE task_id IN (" + TASKS + ")", id);
        // Keep immutable deliverables even when their source plan/task history is removed.
        if (taskOutputs != null) taskOutputs.snapshotPlan(id);
        jdbc.update("DELETE FROM execution_attempt WHERE task_id IN (" + TASKS + ")", id);
        jdbc.update("DELETE FROM work_task_dependency WHERE task_id IN (" + TASKS + ")", id);
        jdbc.update("DELETE FROM work_task WHERE subject_type='flexible-plan' AND subject_id=?", id);
        if (jdbc.update("DELETE FROM flexible_workflow_plan WHERE id=?", id) != 1) {
            throw new IllegalStateException("plan disappeared during purge: " + id);
        }
        return true;
    }
}
