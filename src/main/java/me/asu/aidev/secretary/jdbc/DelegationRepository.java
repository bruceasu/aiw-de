package me.asu.aidev.secretary.jdbc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import me.asu.aidev.secretary.domain.DelegationRule;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class DelegationRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json = new ObjectMapper().enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    public DelegationRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public record Grant(String id, String owner, String workerId, long revision, String state,
                        String originalText, DelegationRule rule, String authority, String templateId,
                        Long templateRevision, long effectiveMs, long expiresMs) {}
    public String encode(Object value) {
        try { return json.writeValueAsString(value); }
        catch (Exception error) { throw new IllegalArgumentException("Cannot encode structured delegation data", error); }
    }
    public <T> T decode(String value, Class<T> type) {
        try { return json.readValue(value, type); }
        catch (Exception error) { throw new IllegalArgumentException("Invalid structured delegation data", error); }
    }
    public List<Grant> grants() {
        return jdbc.query("SELECT * FROM delegation_grant ORDER BY effective_ms,id", (rs,n) -> new Grant(
                rs.getString("id"), rs.getString("owner"), rs.getString("worker_id"), rs.getLong("revision"),
                rs.getString("state"), rs.getString("original_text"), decode(rs.getString("rule_json"), DelegationRule.class),
                rs.getString("authority"), rs.getString("template_id"), rs.getObject("template_revision") == null ? null : rs.getLong("template_revision"),
                rs.getLong("effective_ms"), rs.getLong("expires_ms")));
    }
    public Grant get(String id) { return grants().stream().filter(g -> g.id().equals(id)).findFirst().orElseThrow(() -> new IllegalArgumentException("Grant not found")); }
    public void audit(String target, String grant, String request, String actor, String event, String detail) {
        jdbc.update("INSERT INTO delegation_audit VALUES(?,?,?,?,?,?,?,?)", UUID.randomUUID().toString(), target, grant,
                request, actor, event, detail, Instant.now().toEpochMilli());
    }
    public void history(Grant grant, String actor) {
        jdbc.update("INSERT INTO delegation_history VALUES(?,?,?,?,?)", grant.id(), grant.revision(), encode(grant), actor, Instant.now().toEpochMilli());
    }
}
