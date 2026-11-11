package me.asu.aidev.execution.pricing;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import me.asu.aidev.authority.AuthorityBoundary;
import me.asu.aidev.authority.AuthorityRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Coordinates source observations across Plane instances without publishing prices. */
@Service
public class ModelPriceCollectionState {
    private static final long LEASE_MS = 120_000, DEDUP_MS = 300_000;
    private final AuthorityRepository repo;
    private final AuthorityBoundary boundary;
    public ModelPriceCollectionState(AuthorityRepository repo, AuthorityBoundary boundary) {
        this.repo = repo; this.boundary = boundary;
    }

    @Transactional
    public String claimDeepSeek(long now) {
        return claim("deepseek", now);
    }

    @Transactional
    public String claim(String provider, long now) {
        provider(provider);
        boundary.lock();
        repo.jdbc().update("INSERT INTO model_price_collection_state(provider,status) VALUES(?,'PENDING') ON CONFLICT(provider) DO NOTHING", provider);
        var row = repo.jdbc().queryForMap("SELECT lease_until_ms,last_attempt_ms FROM model_price_collection_state WHERE provider=?", provider);
        Long lease = number(row.get("lease_until_ms")), previous = number(row.get("last_attempt_ms"));
        if (lease != null && lease > now || previous != null && previous > now - DEDUP_MS) return null;
        String token = UUID.randomUUID().toString();
        repo.jdbc().update("""
            UPDATE model_price_collection_state SET lease_token=?,lease_until_ms=?,last_attempt_ms=?,
              status='RUNNING',failure_code=NULL WHERE provider=?
            """, token, Math.addExact(now, LEASE_MS), now, provider);
        return token;
    }

    /** Claims an eligible repair observation without the normal refresh deduplication delay. */
    @Transactional
    public String claimRepair(String materialId, long now) {
        if (materialId == null || !materialId.matches("price-repair-[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}"))
            throw new IllegalArgumentException("PRICE_REPAIR_MATERIAL_INVALID");
        boundary.lock();
        var rows = repo.jdbc().queryForList("""
            SELECT s.provider,s.lease_until_ms FROM model_price_repair_material m
              JOIN model_price_collection_state s ON s.provider=m.provider
              JOIN model_price_collection_failure f ON f.provider=m.provider
             WHERE m.id=? AND m.status='PREPARED' AND f.active=TRUE
               AND (s.status='FAILED' OR (s.status='RUNNING' AND s.lease_until_ms<=?))
               AND f.fingerprint=m.failure_fingerprint AND f.collector_sha256=m.collector_sha256
               AND s.collector_sha256=m.collector_sha256 AND s.content_sha256=m.last_success_sha256
            """, materialId, now);
        if (rows.size() != 1) return null;
        var row = rows.getFirst();
        Long lease = number(row.get("lease_until_ms"));
        if (lease != null && lease > now) return null;
        String token = UUID.randomUUID().toString();
        repo.jdbc().update("""
            UPDATE model_price_collection_state SET lease_token=?,lease_until_ms=?,last_attempt_ms=?,
              status='RUNNING',failure_code=NULL WHERE provider=?
            """, token, Math.addExact(now, LEASE_MS), now, row.get("provider"));
        return token;
    }

    @Transactional
    public void observed(String token, ModelPriceCollectorProcess.Observation observation) {
        saveObservation(token, observation, null);
    }

    @Transactional
    public void observed(String token, ModelPriceCollectorProcess.Collected collected) {
        if (collected == null || collected.observation() == null || collected.sampleBase64() == null
                || collected.sampleBase64().length() > 2_700_000)
            throw new IllegalArgumentException("PRICE_OBSERVATION_INVALID");
        byte[] sample;
        try { sample = java.util.Base64.getDecoder().decode(collected.sampleBase64()); }
        catch (IllegalArgumentException failure) { throw new IllegalArgumentException("PRICE_OBSERVATION_INVALID"); }
        if (sample.length == 0 || sample.length > 2_000_000 || !hash(sample).equals(collected.observation().contentSha256()))
            throw new IllegalArgumentException("PRICE_OBSERVATION_INVALID");
        saveObservation(token, collected.observation(), collected.sampleBase64());
    }

    private void saveObservation(String token, ModelPriceCollectorProcess.Observation observation, String sample) {
        if (token == null || observation == null || observation.accountingReady()) throw new IllegalArgumentException("PRICE_OBSERVATION_INVALID");
        provider(observation.provider());
        String json = repo.encode(observation);
        if (json.length() > 1_000_000) throw new IllegalArgumentException("PRICE_OBSERVATION_INVALID");
        boundary.lock();
        int changed = repo.jdbc().update("""
            UPDATE model_price_collection_state SET status='OBSERVED_INCOMPLETE',failure_code=NULL,
              last_success_ms=?,source_url=?,content_sha256=?,collector_version=?,collector_sha256=?,observation_json=?,last_success_sample_base64=?,
              lease_token=NULL,lease_until_ms=NULL WHERE provider=? AND lease_token=?
            """, observation.observedMs(), observation.sourceUrl(), observation.contentSha256(), observation.collectorVersion(), observation.collectorSha256(), json, sample, observation.provider(), token);
        if (changed == 1) {
            repo.jdbc().update("UPDATE model_price_collection_failure SET active=FALSE,failure_count=0 WHERE provider=?", observation.provider());
            repo.jdbc().update("UPDATE model_price_repair_material SET status='SUPERSEDED' WHERE provider=? AND status='PREPARED'", observation.provider());
        }
    }

    @Transactional
    public void structureChanged(String token, ModelPriceCollectorProcess.SourceChange change) {
        if (token == null || change == null || change.contentBase64() == null || change.contentBase64().length() > 2_700_000)
            throw new IllegalArgumentException("PRICE_SOURCE_CHANGE_INVALID");
        provider(change.provider());
        boundary.lock();
        int changed = repo.jdbc().update("""
            UPDATE model_price_collection_state SET status='FAILED',failure_code='STRUCTURE_CHANGED',
              lease_token=NULL,lease_until_ms=NULL WHERE provider=? AND lease_token=?
            """, change.provider(), token);
        if (changed != 1) return;
        repo.jdbc().update("""
            INSERT INTO model_price_collection_failure(provider,fingerprint,collector_version,collector_sha256,reason,source_url,
              content_sha256,sample_base64,last_seen_ms,failure_count,active)
            VALUES(?,?,?,?,?,?,?,?,?,1,TRUE)
            ON CONFLICT(provider) DO UPDATE SET
              failure_count=CASE WHEN model_price_collection_failure.active=TRUE
                AND model_price_collection_failure.fingerprint=excluded.fingerprint
                THEN model_price_collection_failure.failure_count+1 ELSE 1 END,
              fingerprint=excluded.fingerprint,collector_version=excluded.collector_version,collector_sha256=excluded.collector_sha256,reason=excluded.reason,
              source_url=excluded.source_url,content_sha256=excluded.content_sha256,sample_base64=excluded.sample_base64,
              last_seen_ms=excluded.last_seen_ms,active=TRUE
            """, change.provider(), change.fingerprint(), change.collectorVersion(), change.collectorSha256(), change.reason(), change.sourceUrl(),
                change.contentSha256(), change.contentBase64(), change.observedMs());
    }

    @Transactional
    public void failed(String token, String reason) {
        failed("deepseek", token, reason);
    }

    @Transactional
    public void failed(String provider, String token, String reason) {
        provider(provider);
        if (token == null || !List.of("SOURCE_UNAVAILABLE", "COLLECTOR_REJECTED", "COLLECTOR_UNAVAILABLE").contains(reason))
            throw new IllegalArgumentException("PRICE_COLLECTION_FAILURE_INVALID");
        boundary.lock();
        repo.jdbc().update("""
            UPDATE model_price_collection_state SET status='FAILED',failure_code=?,lease_token=NULL,lease_until_ms=NULL
             WHERE provider=? AND lease_token=?
            """, reason, provider, token);
    }

    public List<Map<String,Object>> status() {
        return repo.jdbc().queryForList("""
            SELECT s.provider,s.status,s.failure_code,s.last_attempt_ms,s.last_success_ms,s.source_url,s.content_sha256,s.collector_version,s.collector_sha256,
                   f.reason AS structure_reason,f.failure_count,
                   CASE WHEN s.last_success_ms IS NOT NULL AND f.active=TRUE AND f.failure_count>=2 THEN TRUE ELSE FALSE END AS repair_needed,
                   (SELECT COUNT(*) FROM model_price_rule r JOIN model_price_head h ON h.snapshot_id=r.snapshot_id
                      WHERE h.provider=s.provider AND r.provider=s.provider) AS published_rule_count,
                   (SELECT snapshot_id FROM model_price_head WHERE provider=s.provider) AS published_snapshot_id,
                   CASE WHEN m.id IS NULL THEN NULL
                        WHEN v.state='INSTALLED' THEN 'INSTALLED'
                        WHEN NOT (m.failure_fingerprint=f.fingerprint AND m.collector_sha256=f.collector_sha256
                            AND m.collector_sha256=s.collector_sha256 AND m.last_success_sha256=s.content_sha256
                            AND m.changed_page_sha256=f.content_sha256 AND m.source_url=f.source_url) THEN 'STALE'
                        WHEN v.state='FAILED' AND v.failure_code='ADMIN_REJECTED'
                          AND v.reviewed_candidate_sha256=(SELECT r3.module_sha256 FROM model_price_repair_result r3
                              WHERE r3.material_id=m.id ORDER BY r3.received_ms DESC,r3.attempt_id DESC LIMIT 1)
                          AND v.reviewed_page_sha256=f.content_sha256 THEN 'REJECTED_BY_ADMIN'
                        WHEN v.state='FAILED' AND v.failure_code='ADMIN_REJECTED' THEN 'AWAITING_ADMIN'
                        WHEN v.state='FAILED' THEN 'REVIEW_AGAIN'
                        WHEN v.state='INSTALLING' THEN 'INSTALLING'
                        WHEN v.state='VERIFYING' AND v.approved_by IS NOT NULL AND v.execution_started_ms IS NULL THEN 'APPROVED_BY_ADMIN'
                        WHEN v.state='VERIFYING' THEN 'VERIFYING'
                        WHEN t.id IS NULL THEN 'WAITING_DISPATCH'
                        WHEN t.state='CANCELLED' OR EXISTS (SELECT 1 FROM execution_attempt a WHERE a.task_id=t.id AND a.stop_requested_at_epoch_ms IS NOT NULL) THEN 'STOPPED'
                        WHEN t.state='COMPLETED' AND EXISTS (SELECT 1 FROM model_price_repair_result r
                            JOIN execution_attempt a ON a.id=r.attempt_id WHERE r.task_id=t.id AND a.state='COMPLETED') THEN 'AWAITING_ADMIN'
                        WHEN t.state IN ('FAILED','COMPLETED') THEN 'INCOMPLETE'
                        WHEN t.state='READY' AND (SELECT COUNT(*) FROM execution_attempt a WHERE a.task_id=t.id)>=3 THEN 'ATTEMPT_LIMIT'
                        WHEN t.state='READY' THEN 'WAITING_WORKER'
                        WHEN t.state IN ('CLAIMED','RUNNING') THEN 'REPAIRING'
                        ELSE 'WAITING_DISPATCH' END AS repair_stage,
                   v.failure_code AS repair_failure_code
              FROM model_price_collection_state s LEFT JOIN model_price_collection_failure f ON f.provider=s.provider
              LEFT JOIN model_price_repair_material m ON m.id=(
                   SELECT m2.id FROM model_price_repair_material m2 WHERE m2.provider=s.provider
                    ORDER BY m2.created_ms DESC,m2.id DESC LIMIT 1)
              LEFT JOIN model_price_repair_activation v ON v.material_id=m.id
              LEFT JOIN work_task t ON t.id='price-repair-task-' || substr(m.id,14) AND t.subject_type='price-source-repair' AND t.subject_id=m.id
             ORDER BY s.provider
            """);
    }

    private static Long number(Object value) { return value == null ? null : ((Number)value).longValue(); }
    private static String hash(byte[] value) {
        try { return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(value)); }
        catch (java.security.NoSuchAlgorithmException failure) { throw new IllegalStateException(failure); }
    }
    private static void provider(String value) {
        if (!List.of("deepseek", "openai").contains(value)) throw new IllegalArgumentException("PRICE_PROVIDER_INVALID");
    }
}
