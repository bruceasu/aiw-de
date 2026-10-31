package me.asu.aidev.authority;

import java.security.SecureRandom;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Durable state-change notifications. Payloads are references, never executable work. */
@Service
public class AgentMessageService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int MAX_ATTEMPTS = 12;
    private static final long MESSAGE_TTL_MS = 7L * 24 * 60 * 60 * 1000;
    private static final long LEASE_MS = 60_000;
    private final AuthorityRepository repo;
    private final AuthorityBoundary boundary;

    public record Delivery(String id, String eventType, String resourceId, long resourceVersion,
                          int deliveryAttempt, long expiresAt, String deliveryToken) {}
    public record DeadMessage(String id, String eventType, String resourceId, long resourceVersion,
                              int attempts, long createdAt, long expiresAt, String lastError) {}

    public AgentMessageService(AuthorityRepository repo, AuthorityBoundary boundary) {
        this.repo = repo;
        this.boundary = boundary;
    }

    @Transactional
    public void stateChanged(String agentId, String eventType, String resourceId, long version) {
        if (!Set.of("GRANT_CHANGED", "PERMISSION_CHANGED").contains(eventType)
                || agentId == null || resourceId == null || resourceId.isBlank() || resourceId.length() > 256 || version < 1)
            throw new IllegalArgumentException("Invalid state-change reference");
        if (repo.jdbc().queryForObject("SELECT COUNT(*) FROM authority_identity WHERE worker_id=? AND identity_kind='AGENT'", Long.class, agentId) != 1)
            return;
        String dedupe = AuthorityRepository.hash(eventType + "\n" + resourceId + "\n" + version);
        long now = System.currentTimeMillis();
        repo.jdbc().update("""
                INSERT INTO agent_message_outbox(id,agent_id,event_type,resource_id,resource_version,dedupe_key,state,attempts,created_ms,available_ms,expires_ms)
                VALUES(?,?,?,?,?,?,'READY',0,?,?,?) ON CONFLICT(agent_id,dedupe_key) DO NOTHING
                """, UUID.randomUUID().toString(), agentId, eventType, resourceId, version, dedupe, now, now, now + MESSAGE_TTL_MS);
    }

    @Transactional
    public List<Delivery> claim(AuthorityBoundary.Principal principal, int requestedLimit) {
        if (requestedLimit < 1 || requestedLimit > 50) throw new IllegalArgumentException("Message batch must be between 1 and 50");
        boundary.lock();
        long now = System.currentTimeMillis();
        String agent = principal.worker();
        repo.jdbc().update("UPDATE agent_message_outbox SET state='DEAD',last_error='EXPIRED',lease_until_ms=NULL WHERE agent_id=? AND state<>'ACKNOWLEDGED' AND expires_ms<=?", agent, now);
        var rows = repo.jdbc().queryForList("""
                SELECT id,event_type,resource_id,resource_version,attempts,expires_ms,state FROM agent_message_outbox
                WHERE agent_id=? AND expires_ms>? AND ((state IN ('READY','RETRY') AND available_ms<=?) OR (state='LEASED' AND lease_until_ms<=?))
                ORDER BY created_ms,id LIMIT ?
                """, agent, now, now, now, requestedLimit);
        List<Delivery> result = new ArrayList<>();
        for (var row : rows) {
            int attempts = ((Number) row.get("attempts")).intValue() + 1;
            String id = String.valueOf(row.get("id"));
            if (attempts > MAX_ATTEMPTS) {
                repo.jdbc().update("UPDATE agent_message_outbox SET state='DEAD',last_error='MAX_DELIVERIES',lease_until_ms=NULL WHERE id=?", id);
                continue;
            }
            String token = token();
            long leaseUntil = now + LEASE_MS;
            repo.jdbc().update("UPDATE agent_message_outbox SET state='LEASED',attempts=?,lease_until_ms=?,lease_hash=?,last_error=NULL WHERE id=?",
                    attempts, leaseUntil, AuthorityRepository.hash(token), id);
            result.add(new Delivery(id, String.valueOf(row.get("event_type")), String.valueOf(row.get("resource_id")),
                    ((Number) row.get("resource_version")).longValue(), attempts,
                    ((Number) row.get("expires_ms")).longValue(), token));
        }
        return List.copyOf(result);
    }

    @Transactional
    public Map<String, Object> acknowledge(AuthorityBoundary.Principal principal, String id, String token) {
        if (token == null || token.length() < 32 || token.length() > 128) throw new SecurityException("Delivery token required");
        boundary.lock();
        var rows = repo.jdbc().queryForList("SELECT agent_id,state,lease_hash,lease_until_ms,expires_ms FROM agent_message_outbox WHERE id=?", id);
        if (rows.size() != 1 || !principal.worker().equals(rows.getFirst().get("agent_id"))) throw new SecurityException("Message is not addressed to this Agent");
        var row = rows.getFirst();
        String tokenHash = AuthorityRepository.hash(token);
        if (!MessageDigestSupport.equal(tokenHash, String.valueOf(row.get("lease_hash")))) throw new SecurityException("Current delivery lease required");
        if ("ACKNOWLEDGED".equals(row.get("state"))) return Map.of("state", "ACKNOWLEDGED");
        if (!"LEASED".equals(row.get("state"))) throw new IllegalStateException("Message lease expired or changed");
        long now = System.currentTimeMillis();
        if (((Number) row.get("lease_until_ms")).longValue() <= now || ((Number) row.get("expires_ms")).longValue() <= now)
            throw new IllegalStateException("Message lease expired or changed");
        if (repo.jdbc().update("UPDATE agent_message_outbox SET state='ACKNOWLEDGED',acked_ms=?,lease_until_ms=NULL WHERE id=? AND state='LEASED' AND lease_hash=? AND lease_until_ms>? AND expires_ms>?",
                now, id, tokenHash, now, now) != 1) throw new IllegalStateException("Message lease changed");
        return Map.of("state", "ACKNOWLEDGED");
    }

    public List<DeadMessage> dead(String agentId, String actor) {
        String owner = repo.jdbc().queryForObject("SELECT owner_id FROM authority_identity WHERE worker_id=? AND identity_kind='AGENT'", String.class, agentId);
        if (!Objects.equals(repo.actor(owner), actor)) throw new SecurityException("Current Owner must select the Agent");
        return repo.jdbc().query("SELECT id,event_type,resource_id,resource_version,attempts,created_ms,expires_ms,last_error FROM agent_message_outbox WHERE agent_id=? AND state='DEAD' ORDER BY created_ms DESC",
                (rs, n) -> new DeadMessage(rs.getString("id"), rs.getString("event_type"), rs.getString("resource_id"),
                        rs.getLong("resource_version"), rs.getInt("attempts"), rs.getLong("created_ms"), rs.getLong("expires_ms"), rs.getString("last_error")), agentId);
    }

    @Transactional
    public void retryDead(String agentId, String messageId, String actor) {
        String owner = repo.jdbc().queryForObject("SELECT owner_id FROM authority_identity WHERE worker_id=? AND identity_kind='AGENT'", String.class, agentId);
        if (!Objects.equals(repo.actor(owner), actor)) throw new SecurityException("Current Owner must select the Agent");
        boundary.lock();
        long now = System.currentTimeMillis();
        if (repo.jdbc().update("UPDATE agent_message_outbox SET state='READY',attempts=0,available_ms=?,expires_ms=?,lease_until_ms=NULL,lease_hash=NULL,last_error=NULL WHERE id=? AND agent_id=? AND state='DEAD'",
                now, now + MESSAGE_TTL_MS, messageId, agentId) != 1) throw new IllegalStateException("Dead message changed; refresh before retrying");
        repo.audit("AGENT:" + agentId, null, messageId, actor, "AGENT_MESSAGE_REQUEUED", "State-change reference retried; no business action replayed");
    }

    private static String token() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static final class MessageDigestSupport {
        static boolean equal(String left, String right) {
            return java.security.MessageDigest.isEqual(left.getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                    right.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        }
    }
}
