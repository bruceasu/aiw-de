package me.asu.aidev.workflow.jdbc;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import me.asu.aidev.ai.application.AiProfileCatalog.NamedProfile;
import me.asu.aidev.work.domain.CapabilitySetCodec;
import me.asu.aidev.work.domain.TaskId;
import me.asu.aidev.worker.domain.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Durable settings, immutable request parameters and actor-owned recovery handles. */
@Repository
public class JdbcRecommendationRepository {
    public record Settings(boolean enabled, String planner, String model, String effort,
                           long revision, String updatedBy, Instant updatedAt) {}
    public record Frozen(String version, List<NamedProfile> profiles) {}
    public record Context(String id, String actor, String goal, long inputVersion, long revision, String planId) {}
    public record RequestRow(String taskId, String requestKey, String contextId, String actor, String goal,
                             long inputVersion, long settingsRevision, Frozen frozen) {}
    public record Audit(String actor, Instant occurredAt, String previous, String current) {}
    private final JdbcTemplate jdbc;
    private final ObjectMapper json = new ObjectMapper();
    public JdbcRecommendationRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Settings settings() {
        return jdbc.queryForObject("SELECT * FROM workflow_recommendation_settings WHERE id=1", (rs, n) ->
                new Settings(rs.getBoolean("enabled"), rs.getString("planner"), rs.getString("model"),
                        rs.getString("effort"), rs.getLong("revision"), rs.getString("updated_by"),
                        Instant.ofEpochMilli(rs.getLong("updated_at_epoch_ms"))));
    }
    public void lockSettings() {
        if (jdbc.update("UPDATE workflow_recommendation_settings SET revision=revision WHERE id=1") != 1)
            throw new IllegalStateException("recommendation settings row is missing; check database migration");
    }
    public void save(Settings before, Settings after, String actor) {
        if (jdbc.update("""
                UPDATE workflow_recommendation_settings SET enabled=?,planner=?,model=?,effort=?,revision=revision+1,
                    updated_by=?,updated_at_epoch_ms=? WHERE id=1 AND revision=?
                """, after.enabled(), after.planner(), after.model(), after.effort(), actor,
                after.updatedAt().toEpochMilli(), before.revision()) != 1)
            throw new IllegalStateException("recommendation settings changed; reload");
        jdbc.update("INSERT INTO workflow_recommendation_audit VALUES (?,?,?,?,?)", UUID.randomUUID().toString(),
                actor, after.updatedAt().toEpochMilli(), encode(before), encode(after));
    }
    public List<Audit> audit() {
        return jdbc.query("SELECT * FROM workflow_recommendation_audit ORDER BY occurred_at_epoch_ms DESC,id DESC LIMIT 50",
                (rs,n) -> new Audit(rs.getString("actor"), Instant.ofEpochMilli(rs.getLong("occurred_at_epoch_ms")),
                        rs.getString("previous_json"), rs.getString("current_json")));
    }
    public Context createContext(String actor) {
        String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO workflow_recommendation_context VALUES (?,?,?,0,0,NULL,?)", id, actor, "", Instant.now().toEpochMilli());
        return new Context(id, actor, "", 0, 0, null);
    }
    public Context context(String id, String actor, boolean lock) {
        if (lock && jdbc.update("UPDATE workflow_recommendation_context SET revision=revision WHERE id=? AND actor=?", id, actor) != 1)
            throw new SecurityException("recommendation context is not owned by this operator");
        return jdbc.query("SELECT * FROM workflow_recommendation_context WHERE id=? AND actor=?", (rs,n) ->
                new Context(rs.getString("id"), rs.getString("actor"), rs.getString("goal"), rs.getLong("input_version"),
                        rs.getLong("revision"), rs.getString("plan_id")), id, actor).stream().findFirst()
                .orElseThrow(() -> new SecurityException("recommendation context is not owned by this operator"));
    }
    public List<Context> contexts(String actor) {
        return jdbc.query("SELECT * FROM workflow_recommendation_context WHERE actor=? ORDER BY updated_at_epoch_ms DESC,id DESC LIMIT 30",
                (rs,n) -> new Context(rs.getString("id"), rs.getString("actor"), rs.getString("goal"), rs.getLong("input_version"),
                        rs.getLong("revision"), rs.getString("plan_id")), actor);
    }
    public void advance(Context context, long expected, String goal, long version) {
        if (jdbc.update("""
                UPDATE workflow_recommendation_context SET goal=?,input_version=?,revision=revision+1,updated_at_epoch_ms=?
                WHERE id=? AND revision=? AND plan_id IS NULL
                """, goal, version, Instant.now().toEpochMilli(), context.id(), expected) != 1)
            throw new IllegalStateException("editor context changed or was saved as a Plan; reload before requesting again");
    }
    public void bind(Context context, String planId) {
        if (context.planId() == null && jdbc.update("UPDATE workflow_recommendation_context SET plan_id=?,revision=revision+1 WHERE id=? AND plan_id IS NULL AND revision=?",
                planId, context.id(), context.revision()) != 1) throw new IllegalStateException("context already saved or changed; reload");
        jdbc.update("INSERT INTO workflow_recommendation_plan_context VALUES (?,?)", planId, context.id());
    }
    public Optional<String> contextForPlan(String planId, String actor) {
        return jdbc.query("""
                SELECT c.id FROM workflow_recommendation_plan_context p
                JOIN workflow_recommendation_context c ON c.id=p.context_id WHERE p.plan_id=? AND c.actor=?
                """, (rs,n) -> rs.getString(1), planId, actor).stream().findFirst();
    }
    public void insert(RequestRow request) {
        long revision = context(request.contextId(), request.actor(), false).revision();
        jdbc.update("INSERT INTO workflow_recommendation_request VALUES (?,?,?,?,?,?,?,?,?,?)", request.taskId(), request.requestKey(),
                request.contextId(), request.actor(), request.goal(), request.inputVersion(), request.settingsRevision(),
                encode(request.frozen()), revision, Instant.now().toEpochMilli());
    }
    public Optional<RequestRow> request(String taskId) {
        return jdbc.query("SELECT * FROM workflow_recommendation_request WHERE task_id=?", (rs,n) ->
                new RequestRow(rs.getString("task_id"), rs.getString("request_key"), rs.getString("context_id"),
                        rs.getString("actor"), rs.getString("goal"), rs.getLong("input_version"), rs.getLong("settings_revision"),
                        decode(rs.getString("frozen_json"), Frozen.class)), taskId).stream().findFirst();
    }
    public Optional<String> latest(String contextId) {
        return jdbc.query("SELECT task_id FROM workflow_recommendation_request WHERE context_id=? ORDER BY context_revision DESC LIMIT 1",
                (rs,n) -> rs.getString(1), contextId).stream().findFirst();
    }
    public List<TaskId> queued() {
        return jdbc.query("SELECT id FROM work_task WHERE subject_type='workflow-recommendation' AND state IN ('READY','BLOCKED') ORDER BY id",
                (rs,n) -> new TaskId(rs.getString(1)));
    }
    public List<Worker> online(Instant since) {
        return jdbc.query("SELECT * FROM worker WHERE status='ACTIVE' AND last_heartbeat_at_epoch_ms>=?", (rs,n) ->
                new Worker(new WorkerId(rs.getString("id")), rs.getString("runtime_type"), CapabilitySetCodec.decode(rs.getString("capabilities")),
                        rs.getInt("max_concurrency"), rs.getInt("active_leases"), WorkerStatus.ACTIVE,
                        Instant.ofEpochMilli(rs.getLong("registered_at_epoch_ms")), Instant.ofEpochMilli(rs.getLong("last_heartbeat_at_epoch_ms")),
                        Instant.ofEpochMilli(rs.getLong("updated_at_epoch_ms"))), since.toEpochMilli());
    }
    private String encode(Object value) {
        if (value instanceof Settings s) value = java.util.Map.of("enabled", s.enabled(), "planner", s.planner() == null ? "" : s.planner(),
                "model", s.model() == null ? "" : s.model(), "effort", s.effort() == null ? "" : s.effort(),
                "revision", s.revision(), "updatedBy", s.updatedBy(), "updatedAt", s.updatedAt().toString());
        try { return json.writeValueAsString(value); }
        catch (Exception e) { throw new IllegalStateException("cannot encode recommendation data", e); }
    }
    private <T> T decode(String value, Class<T> type) {
        try { return json.readValue(value, type); }
        catch (Exception e) { throw new IllegalStateException("invalid frozen recommendation data", e); }
    }
}
