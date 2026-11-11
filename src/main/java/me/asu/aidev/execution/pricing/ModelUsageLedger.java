package me.asu.aidev.execution.pricing;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import me.asu.aidev.authority.AuthorityBoundary;
import me.asu.aidev.authority.AuthorityRepository;
import me.asu.aidev.execution.guard.GuardContract.Lease;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Records model request facts without changing guarded budget settlement. */
@Service
public class ModelUsageLedger {
    public record Start(Lease lease, String requestId, String provider, String model,
                        String protocol, String purpose, String connectionKind, String priceScope, String serviceTier, long startedMs) {}
    public record Terminal(Lease lease, String requestId, String state, long finishedMs,
                           Long inputTokens, Long cachedTokens, Long outputTokens, Long cacheWriteTokens,
                           String effectiveServiceTier) {}

    private final AuthorityRepository repo;
    private final AuthorityBoundary boundary;
    public ModelUsageLedger(AuthorityRepository repo, AuthorityBoundary boundary) {
        this.repo = repo;
        this.boundary = boundary;
    }

    @Transactional
    public Map<String,String> start(String task, Start input) {
        Objects.requireNonNull(input); Objects.requireNonNull(input.lease());
        text(task, 128); text(input.requestId(), 128); text(input.provider(), 64); text(input.model(), 200);
        text(input.protocol(), 64); text(input.serviceTier(), 64);
        if (!SetValues.CONNECTIONS.contains(input.connectionKind()) || !SetValues.PRICE_SCOPES.contains(input.priceScope())
                || !SetValues.PURPOSES.contains(input.purpose()) || input.startedMs() < 1
                || "openai".equals(input.provider()) && !"unknown".equals(input.serviceTier()))
            throw new IllegalArgumentException("MODEL_USAGE_START_INVALID");
        boundary.lock();
        String digest = AuthorityRepository.hash(repo.encode(input));
        var existing = rows(task, input.lease(), input.requestId());
        if (!existing.isEmpty()) {
            if (!digest.equals(existing.getFirst().get("request_digest"))) throw new IllegalStateException("MODEL_USAGE_START_CONFLICT");
            return Map.of("state", String.valueOf(existing.getFirst().get("state")));
        }
        // The local encrypted outbox may deliver after cancellation or expiry.
        // This accepts observation only and never grants a live task operation.
        if (repo.jdbc().queryForObject("""
                SELECT COUNT(*) FROM execution_attempt WHERE id=? AND task_id=? AND worker_id=?
                  AND lease_token=? AND started_at_epoch_ms IS NOT NULL
                """, Long.class, input.lease().attemptId(), task, input.lease().workerId(), input.lease().leaseToken()) != 1)
            throw new SecurityException("MODEL_USAGE_ORIGINAL_LEASE_REQUIRED");
        repo.jdbc().update("""
            INSERT INTO model_usage_request(attempt_id,request_id,task_id,worker_id,provider,model,protocol,purpose,
                    connection_kind,price_scope,service_tier,started_ms,state,request_digest)
            VALUES(?,?,?,?,?,?,?,?,?,?,?,?,'STARTED',?)
            """, input.lease().attemptId(), input.requestId(), task, input.lease().workerId(), input.provider(),
                input.model(), input.protocol(), input.purpose(), input.connectionKind(), input.priceScope(), input.serviceTier(), input.startedMs(), digest);
        return Map.of("state", "STARTED");
    }

    @Transactional
    public Map<String,String> finish(String task, Terminal input) {
        Objects.requireNonNull(input); Objects.requireNonNull(input.lease());
        text(task, 128); text(input.requestId(), 128);
        if (!SetValues.TERMINALS.contains(input.state()) || input.finishedMs() < 1)
            throw new IllegalArgumentException("MODEL_USAGE_TERMINAL_INVALID");
        new PriceSchedule.Usage(input.inputTokens(), input.cachedTokens(), input.outputTokens(), input.cacheWriteTokens());
        boundary.lock();
        var existing = rows(task, input.lease(), input.requestId());
        if (existing.size() != 1) throw new IllegalStateException("MODEL_USAGE_START_REQUIRED");
        var row = existing.getFirst();
        if (input.effectiveServiceTier() != null && (!"openai".equals(row.get("provider"))
                || !SetValues.EFFECTIVE_TIERS.contains(input.effectiveServiceTier())))
            throw new IllegalArgumentException("MODEL_USAGE_TIER_INVALID");
        if (input.finishedMs() < ((Number)row.get("started_ms")).longValue())
            throw new IllegalArgumentException("MODEL_USAGE_TIME_INVALID");
        String digest = AuthorityRepository.hash(repo.encode(input));
        if (!"STARTED".equals(row.get("state"))) {
            if (!digest.equals(row.get("terminal_digest"))) throw new IllegalStateException("MODEL_USAGE_TERMINAL_CONFLICT");
            return Map.of("state", String.valueOf(row.get("state")));
        }
        // The exact original worker and lease may report the terminal after cancellation
        // or expiry; this does not revive the task or authorize any operation.
        int changed = repo.jdbc().update("""
            UPDATE model_usage_request SET state=?,finished_ms=?,input_tokens=?,cached_tokens=?,output_tokens=?,
                    cache_write_tokens=?,terminal_digest=?,service_tier=COALESCE(?,service_tier)
                    WHERE attempt_id=? AND request_id=? AND state='STARTED'
            """, input.state(), input.finishedMs(), input.inputTokens(), input.cachedTokens(), input.outputTokens(),
                input.cacheWriteTokens(), digest, input.effectiveServiceTier(), input.lease().attemptId(), input.requestId());
        if (changed != 1) throw new IllegalStateException("MODEL_USAGE_TERMINAL_RACE");
        return Map.of("state", input.state());
    }

    private List<Map<String,Object>> rows(String task, Lease lease, String requestId) {
        return repo.jdbc().queryForList("""
            SELECT u.* FROM model_usage_request u JOIN execution_attempt a ON a.id=u.attempt_id AND a.task_id=u.task_id
             WHERE u.task_id=? AND u.attempt_id=? AND u.request_id=? AND u.worker_id=?
               AND a.worker_id=? AND a.lease_token=?
            """, task, lease.attemptId(), requestId, lease.workerId(), lease.workerId(), lease.leaseToken());
    }

    private static void text(String value, int limit) {
        if (value == null || value.isBlank() || value.length() > limit) throw new IllegalArgumentException("MODEL_USAGE_ID_INVALID");
    }
    private static final class SetValues {
        private static final java.util.Set<String> CONNECTIONS = java.util.Set.of("API_KEY", "CHATGPT", "UNKNOWN");
        private static final java.util.Set<String> PRICE_SCOPES = java.util.Set.of("TEXT_ONLY", "UNSUPPORTED");
        private static final java.util.Set<String> PURPOSES = java.util.Set.of("task", "history-summary");
        private static final java.util.Set<String> TERMINALS = java.util.Set.of("COMPLETED", "UNKNOWN");
        private static final java.util.Set<String> EFFECTIVE_TIERS = java.util.Set.of("default", "flex", "priority", "fast", "ultrafast");
    }
}
