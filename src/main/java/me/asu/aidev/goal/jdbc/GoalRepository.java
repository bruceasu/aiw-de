package me.asu.aidev.goal.jdbc;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import me.asu.aidev.goal.domain.GoalData.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class GoalRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json = new ObjectMapper();
    public GoalRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    // Methods let Spring repository proxies delegate access to the initialized target.
    public JdbcTemplate jdbc() { return jdbc; }
    public ObjectMapper json() { return json; }
    public String encode(Object value) {
        try { return json.writeValueAsString(value); }
        catch (Exception e) { throw new IllegalStateException("Cannot encode goal data", e); }
    }
    public <T> T decode(String value, Class<T> type) {
        try { return json.readValue(value, type); }
        catch (Exception e) { throw new IllegalStateException("Invalid goal data", e); }
    }
    public Draft get(String id, String actor) {
        return jdbc.query("SELECT * FROM goal_draft WHERE id=? AND actor=?", (rs,n) ->
            new Draft(rs.getString("id"), rs.getString("actor"), rs.getString("title"), rs.getLong("revision"),
                rs.getLong("epoch"), rs.getBoolean("deleted"), decode(rs.getString("payload"), Content.class)), id, actor)
            .stream().findFirst().orElseThrow(() -> new SecurityException("Goal is not owned by this operator"));
    }
    public List<Draft> list(String actor) {
        List<String> ids = jdbc.query("SELECT id FROM goal_draft WHERE actor=? ORDER BY updated_at DESC,id LIMIT 100",
            (rs,n) -> rs.getString(1), actor);
        // Finish the outer query before loading details from the single-connection SQLite pool.
        return ids.stream().map(id -> get(id, actor)).toList();
    }
    public Draft create(String actor) {
        String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO goal_draft VALUES (?,?,?,0,0,FALSE,?,?)", id, actor, "New goal",
            encode(new Content("", "Goal, scope and acceptance need discussion", "openai", List.of(), List.of())), now());
        return get(id, actor);
    }
    public Draft save(Draft before, String title, Content content, boolean deleted, boolean changeEpoch) {
        if (jdbc.update("UPDATE goal_draft SET title=?,payload=?,revision=revision+1,epoch=?,deleted=?,updated_at=? WHERE id=? AND actor=? AND revision=?",
                title, encode(content), before.epoch() + (changeEpoch ? 1 : 0), deleted, now(), before.id(), before.actor(), before.revision()) != 1)
            throw new IllegalStateException("Goal changed; reload before saving");
        return get(before.id(), before.actor());
    }
    public void lock(Draft draft) {
        if (jdbc.update("UPDATE goal_draft SET revision=revision WHERE id=? AND actor=? AND revision=?",
            draft.id(), draft.actor(), draft.revision()) != 1) throw new IllegalStateException("Goal changed; reload");
    }
    public Version version(String id, String actor) {
        return jdbc.query("SELECT v.* FROM goal_version v JOIN goal_draft g ON g.id=v.goal_id WHERE v.id=? AND g.actor=? AND g.deleted=FALSE", (rs,n) ->
            new Version(rs.getString("id"), rs.getString("goal_id"), rs.getLong("source_revision"),
                decode(rs.getString("snapshot"), com.fasterxml.jackson.databind.JsonNode.class),
                decode(rs.getString("editor_json"), com.fasterxml.jackson.databind.JsonNode.class), rs.getLong("editor_revision"),
                rs.getString("recommendation_id"), rs.getString("plan_id")), id, actor).stream().findFirst()
            .orElseThrow(() -> new SecurityException("Version is not owned by this operator or goal is deleted"));
    }
    public List<Version> versions(String goal, String actor) {
        get(goal, actor);
        List<String> ids = jdbc.query("SELECT id FROM goal_version WHERE goal_id=? ORDER BY source_revision DESC",
            (rs,n) -> rs.getString(1), goal);
        return ids.stream().map(id -> version(id, actor)).toList();
    }
    public List<Attachment> attachments(String goal, String actor) {
        get(goal, actor);
        return jdbc.query("SELECT id,filename,sha256,status,extracted,detail FROM goal_attachment WHERE goal_id=? ORDER BY id", (rs,n) ->
            new Attachment(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6)), goal);
    }
    public static long now() { return Instant.now().toEpochMilli(); }
}
