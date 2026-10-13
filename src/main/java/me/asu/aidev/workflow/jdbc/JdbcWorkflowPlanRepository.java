package me.asu.aidev.workflow.jdbc;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import me.asu.aidev.work.domain.CapabilitySetCodec;
import me.asu.aidev.work.domain.TaskId;
import me.asu.aidev.workflow.domain.WorkflowPlan;
import me.asu.aidev.workflow.domain.WorkflowPlanSpec;
import me.asu.aidev.workflow.port.WorkflowPlanRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcWorkflowPlanRepository implements WorkflowPlanRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json = new ObjectMapper();

    public JdbcWorkflowPlanRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public void insert(WorkflowPlan plan) {
        String specification;
        try { specification = json.writeValueAsString(plan.spec()); }
        catch (Exception e) { throw new IllegalStateException("cannot encode workflow plan", e); }
        jdbc.update("""
                INSERT INTO flexible_workflow_plan
                (id,state,version,spec_json,created_by,updated_by,reason,created_at_epoch_ms,updated_at_epoch_ms)
                VALUES (?,?,?,?,?,?,?,?,?)
                """, plan.id(), plan.state().name(), plan.version(), specification, plan.createdBy(),
                plan.updatedBy(), plan.reason(), plan.createdAt().toEpochMilli(), plan.updatedAt().toEpochMilli());
    }

    @Override
    public Optional<WorkflowPlan> find(String id) {
        return jdbc.query("SELECT * FROM flexible_workflow_plan WHERE id=?", this::map, id).stream().findFirst();
    }

    @Override
    public List<WorkflowPlan> list() {
        return list(false);
    }

    @Override
    public List<WorkflowPlan> list(boolean archived) {
        return jdbc.query("""
                SELECT * FROM flexible_workflow_plan p
                 WHERE (EXISTS (SELECT 1 FROM web_console_plan_archive a WHERE a.plan_id=p.id)) = ?
                 ORDER BY p.created_at_epoch_ms DESC,p.id LIMIT 100
                """, this::map, archived);
    }

    @Override
    public boolean transition(String id, long version, WorkflowPlan.State from, WorkflowPlan.State to,
                              String actor, String reason, Instant now) {
        return jdbc.update("""
                UPDATE flexible_workflow_plan SET state=?,version=version+1,updated_by=?,reason=?,updated_at_epoch_ms=?
                WHERE id=? AND version=? AND state=?
                """, to.name(), actor, reason, now.toEpochMilli(), id, version, from.name()) == 1;
    }

    @Override
    public void savePreferences(TaskId task, Set<String> skills) {
        jdbc.update("""
                INSERT INTO work_task_skill_preference(task_id,preferred_skills) VALUES (?,?)
                ON CONFLICT(task_id) DO UPDATE SET preferred_skills=excluded.preferred_skills
                """,
                task.value(), CapabilitySetCodec.encode(skills));
    }

    @Override
    public Map<TaskId, Set<String>> preferences() {
        Map<TaskId, Set<String>> result = new LinkedHashMap<>();
        jdbc.query("""
                SELECT p.task_id,p.preferred_skills FROM work_task_skill_preference p
                JOIN work_task t ON t.id=p.task_id WHERE t.state='READY'
                """, (org.springframework.jdbc.core.RowCallbackHandler) rs -> result.put(new TaskId(rs.getString(1)),
                CapabilitySetCodec.decode(rs.getString(2))));
        return Map.copyOf(result);
    }

    private WorkflowPlan map(ResultSet rs, int row) throws SQLException {
        WorkflowPlanSpec spec;
        try { spec = json.readValue(rs.getString("spec_json"), WorkflowPlanSpec.class); }
        catch (Exception e) { throw new IllegalStateException("cannot decode workflow plan", e); }
        return new WorkflowPlan(rs.getString("id"), WorkflowPlan.State.valueOf(rs.getString("state")),
                rs.getLong("version"), spec, rs.getString("created_by"), rs.getString("updated_by"), rs.getString("reason"),
                Instant.ofEpochMilli(rs.getLong("created_at_epoch_ms")), Instant.ofEpochMilli(rs.getLong("updated_at_epoch_ms")));
    }
}
