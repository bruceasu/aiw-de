package me.asu.aidev.execution.pricing;

import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import me.asu.aidev.authority.AuthorityBoundary;
import me.asu.aidev.authority.AuthorityRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Presents the exact repair source and page for a Plane-owner decision. */
@Service
public class ModelPriceRepairReview {
    private static final int MAX_MODULE = 1_000_000, MAX_PAGE = 2_000_000;
    private static final String ELIGIBLE = """
        SELECT m.id AS material_id,m.provider,m.source_url AS material_source_url,m.changed_page_sha256,
               r.module_sha256,r.module_base64,r.checks_sha256,r.checks_base64,r.summary,r.source_url AS candidate_source_url,
               f.source_url AS failure_source_url,f.content_sha256,f.sample_base64,f.last_seen_ms,
               v.state AS activation_state,v.failure_code AS activation_failure_code,v.lease_token,
               v.execution_started_ms,v.approved_candidate_sha256,v.approved_page_sha256,
               v.reviewed_candidate_sha256,v.reviewed_page_sha256
          FROM model_price_repair_material m
          JOIN model_price_collection_state s ON s.provider=m.provider
          JOIN model_price_collection_failure f ON f.provider=m.provider
          JOIN work_task t ON t.subject_type='price-source-repair' AND t.subject_id=m.id AND t.state='COMPLETED'
          JOIN model_price_repair_result r ON r.material_id=m.id AND r.task_id=t.id
          JOIN execution_attempt a ON a.id=r.attempt_id AND a.task_id=t.id AND a.state='COMPLETED'
          LEFT JOIN model_price_repair_activation v ON v.material_id=m.id
         WHERE m.id=? AND m.status='PREPARED' AND f.active=TRUE AND f.failure_count>=2
           AND f.fingerprint=m.failure_fingerprint AND f.collector_sha256=m.collector_sha256
           AND f.content_sha256=m.changed_page_sha256 AND f.source_url=m.source_url
           AND s.collector_sha256=m.collector_sha256 AND s.content_sha256=m.last_success_sha256
           AND a.stop_requested_at_epoch_ms IS NULL
           AND r.attempt_id=(SELECT r2.attempt_id FROM model_price_repair_result r2
               WHERE r2.material_id=m.id ORDER BY r2.received_ms DESC,r2.attempt_id DESC LIMIT 1)
           AND r.source_url IS NOT NULL AND (v.material_id IS NULL OR v.state='FAILED')
        """;

    private final AuthorityRepository repo;
    private final AuthorityBoundary boundary;
    private final ModelPriceCollectorProcess collector;
    private final TransactionTemplate transactions;

    public ModelPriceRepairReview(AuthorityRepository repo, AuthorityBoundary boundary,
            ModelPriceCollectorProcess collector, PlatformTransactionManager transactionManager) {
        this.repo = repo;
        this.boundary = boundary;
        this.collector = collector;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    public record Candidate(String materialId, String provider, String summary,
                            String candidateSha256, String candidateSource,
                            String checksSha256, String checksSource,
                            String sourceUrl, String pageSha256, long pageObservedMs,
                            String pageContent, String previousFailure) {}
    public record Review(String protocol, List<Candidate> candidates) {}
    public record Decision(String materialId, String candidateSha256, String pageSha256, String decision) {}

    public Review read(String owner) {
        requireOwner(owner);
        List<Map<String, Object>> rows = repo.jdbc().queryForList("""
            SELECT m.id AS material_id
              FROM model_price_repair_material m
              JOIN model_price_collection_state s ON s.provider=m.provider
              JOIN model_price_collection_failure f ON f.provider=m.provider
              JOIN work_task t ON t.subject_type='price-source-repair' AND t.subject_id=m.id AND t.state='COMPLETED'
              JOIN model_price_repair_result r ON r.material_id=m.id AND r.task_id=t.id
              JOIN execution_attempt a ON a.id=r.attempt_id AND a.task_id=t.id AND a.state='COMPLETED'
              LEFT JOIN model_price_repair_activation v ON v.material_id=m.id
             WHERE m.status='PREPARED' AND f.active=TRUE AND f.failure_count>=2
               AND f.fingerprint=m.failure_fingerprint AND f.collector_sha256=m.collector_sha256
               AND f.content_sha256=m.changed_page_sha256 AND f.source_url=m.source_url
               AND s.collector_sha256=m.collector_sha256 AND s.content_sha256=m.last_success_sha256
               AND a.stop_requested_at_epoch_ms IS NULL
               AND r.attempt_id=(SELECT r2.attempt_id FROM model_price_repair_result r2
                   WHERE r2.material_id=m.id ORDER BY r2.received_ms DESC,r2.attempt_id DESC LIMIT 1)
               AND r.source_url IS NOT NULL AND (v.material_id IS NULL OR v.state='FAILED')
             ORDER BY m.provider LIMIT 1
            """);
        var candidates = new java.util.ArrayList<Candidate>();
        var seen = new HashSet<String>();
        for (Map<String, Object> id : rows) {
            Map<String, Object> row = eligible(String.valueOf(id.get("material_id")));
            if (row == null || !seen.add(String.valueOf(row.get("provider")))) continue;
            var page = collector.fetchLatestPage(text(row.get("provider")), text(row.get("candidate_source_url")));
            if ("ADMIN_REJECTED".equals(row.get("activation_failure_code"))
                    && text(row.get("module_sha256")).equals(row.get("reviewed_candidate_sha256"))
                    && page.contentSha256().equals(row.get("reviewed_page_sha256"))) continue;
            candidates.add(toCandidate(row, page));
        }
        return new Review("aiw.model-price-repair-review.v1", List.copyOf(candidates));
    }

    public Map<String, String> decide(String owner, Decision input) {
        requireOwner(owner);
        if (input == null || input.materialId() == null || !input.materialId().matches("price-repair-[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}")
                || !digest(input.candidateSha256()) || !digest(input.pageSha256()) || input.decision() == null
                || !List.of("APPROVE", "REJECT").contains(input.decision()))
            throw new IllegalArgumentException("PRICE_REPAIR_DECISION_INVALID");
        Map<String, Object> preview = eligible(input.materialId());
        if (preview == null || !input.candidateSha256().equals(preview.get("module_sha256"))
                || preview.get("candidate_source_url") == null)
            throw new IllegalStateException("PRICE_REPAIR_REVIEW_STALE");
        var page = collector.fetchLatestPage(text(preview.get("provider")), text(preview.get("candidate_source_url")));
        if (!input.pageSha256().equals(page.contentSha256())) throw new IllegalStateException("PRICE_REPAIR_REVIEW_STALE");
        toCandidate(preview, page);
        return transactions.execute(ignored -> {
            boundary.lock();
            Map<String, Object> row = eligible(input.materialId());
            if (row == null || !input.candidateSha256().equals(row.get("module_sha256"))
                    || !text(preview.get("candidate_source_url")).equals(text(row.get("candidate_source_url"))))
                throw new IllegalStateException("PRICE_REPAIR_REVIEW_STALE");
            String state = text(row.get("activation_state"));
            if ("INSTALLING".equals(state) || "INSTALLED".equals(state)
                    || "VERIFYING".equals(state) && (row.get("execution_started_ms") != null || row.get("lease_token") != null))
                throw new IllegalStateException("PRICE_REPAIR_DECISION_ALREADY_CONSUMED");
            long now = System.currentTimeMillis();
            if ("APPROVE".equals(input.decision())) {
                if ("VERIFYING".equals(state) && input.candidateSha256().equals(row.get("approved_candidate_sha256"))
                        && input.pageSha256().equals(row.get("approved_page_sha256")))
                    return Map.of("state", "APPROVED", "materialId", input.materialId());
                repo.jdbc().update("""
                INSERT INTO model_price_repair_activation(material_id,provider,state,lease_token,lease_until_ms,
                  candidate_sha256,verified_page_sha256,failure_code,updated_ms,decision_by,decision_ms,
                  reviewed_candidate_sha256,reviewed_page_sha256,approved_by,
                  approved_ms,approved_candidate_sha256,approved_page_sha256,approved_page_base64,approved_source_url,
                  approved_page_observed_ms,execution_started_ms,collection_lease_token)
                VALUES(?,?,'VERIFYING',NULL,NULL,NULL,NULL,NULL,?,?,?,?,?,?,?,?,?,?,?,?,NULL,NULL)
                ON CONFLICT(material_id) DO UPDATE SET state='VERIFYING',lease_token=NULL,lease_until_ms=NULL,
                  candidate_sha256=NULL,verified_page_sha256=NULL,failure_code=NULL,updated_ms=excluded.updated_ms,
                  decision_by=excluded.decision_by,decision_ms=excluded.decision_ms,approved_by=excluded.approved_by,
                  reviewed_candidate_sha256=excluded.reviewed_candidate_sha256,
                  reviewed_page_sha256=excluded.reviewed_page_sha256,
                  approved_ms=excluded.approved_ms,approved_candidate_sha256=excluded.approved_candidate_sha256,
                  approved_page_sha256=excluded.approved_page_sha256,approved_page_base64=excluded.approved_page_base64,
                  approved_source_url=excluded.approved_source_url,approved_page_observed_ms=excluded.approved_page_observed_ms,
                  execution_started_ms=NULL,collection_lease_token=NULL
                """, input.materialId(), row.get("provider"), now, owner, now, input.candidateSha256(), input.pageSha256(),
                    owner, now, input.candidateSha256(), input.pageSha256(), page.contentBase64(), page.sourceUrl(), page.observedMs());
                repo.audit("PRICE_REPAIR:" + input.materialId(), null, input.materialId(), repo.actor(owner),
                        "PRICE_REPAIR_CANDIDATE_APPROVED", input.candidateSha256() + ":" + input.pageSha256());
                return Map.of("state", "APPROVED", "materialId", input.materialId());
            }
            repo.jdbc().update("""
            INSERT INTO model_price_repair_activation(material_id,provider,state,lease_token,lease_until_ms,
              candidate_sha256,verified_page_sha256,failure_code,updated_ms,decision_by,decision_ms,
              reviewed_candidate_sha256,reviewed_page_sha256,approved_by,
              approved_ms,approved_candidate_sha256,approved_page_sha256,approved_page_base64,approved_source_url,
              approved_page_observed_ms,execution_started_ms,collection_lease_token)
            VALUES(?,?,'FAILED',NULL,NULL,NULL,NULL,'ADMIN_REJECTED',?,?,?,?,?,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL)
            ON CONFLICT(material_id) DO UPDATE SET state='FAILED',lease_token=NULL,lease_until_ms=NULL,
              candidate_sha256=NULL,verified_page_sha256=NULL,failure_code='ADMIN_REJECTED',updated_ms=excluded.updated_ms,
              decision_by=excluded.decision_by,decision_ms=excluded.decision_ms,approved_by=NULL,approved_ms=NULL,
              reviewed_candidate_sha256=excluded.reviewed_candidate_sha256,reviewed_page_sha256=excluded.reviewed_page_sha256,
              approved_candidate_sha256=NULL,approved_page_sha256=NULL,approved_page_base64=NULL,
              approved_source_url=NULL,approved_page_observed_ms=NULL,execution_started_ms=NULL,
              collection_lease_token=NULL
                """, input.materialId(), row.get("provider"), now, owner, now, input.candidateSha256(), input.pageSha256());
            repo.audit("PRICE_REPAIR:" + input.materialId(), null, input.materialId(), repo.actor(owner),
                    "PRICE_REPAIR_CANDIDATE_REJECTED", input.candidateSha256() + ":" + input.pageSha256());
            return Map.of("state", "REJECTED", "materialId", input.materialId());
        });
    }

    private Map<String, Object> eligible(String materialId) {
        List<Map<String, Object>> rows = repo.jdbc().queryForList(ELIGIBLE, materialId);
        if (rows.isEmpty()) return null;
        if (rows.size() != 1) throw new IllegalStateException("PRICE_REPAIR_REVIEW_AMBIGUOUS");
        return rows.getFirst();
    }

    private static Candidate toCandidate(Map<String, Object> row, ModelPriceCollectorProcess.PageSnapshot page) {
        String module = checkedText(text(row.get("module_base64")), text(row.get("module_sha256")), MAX_MODULE);
        String checks = checkedText(text(row.get("checks_base64")), text(row.get("checks_sha256")), MAX_MODULE);
        String pageContent = checkedText(page.contentBase64(), page.contentSha256(), MAX_PAGE);
        return new Candidate(text(row.get("material_id")), text(row.get("provider")), text(row.get("summary")),
                text(row.get("module_sha256")), module, text(row.get("checks_sha256")), checks,
                page.sourceUrl(), page.contentSha256(), page.observedMs(), pageContent,
                text(row.get("activation_failure_code")));
    }

    private static String checkedText(String encoded, String expected, int maximum) {
        if (encoded == null || encoded.length() > (long) maximum * 4 / 3 + 8 || !digest(expected))
            throw new IllegalStateException("PRICE_REPAIR_REVIEW_CONTENT_INVALID");
        try {
            byte[] bytes = Base64.getDecoder().decode(encoded);
            if (bytes.length == 0 || bytes.length > maximum || !hash(bytes).equals(expected))
                throw new IllegalStateException("PRICE_REPAIR_REVIEW_CONTENT_INVALID");
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        } catch (IllegalArgumentException | java.nio.charset.CharacterCodingException failure) {
            throw new IllegalStateException("PRICE_REPAIR_REVIEW_CONTENT_INVALID");
        }
    }

    private static long number(Object value) {
        if (!(value instanceof Number number)) throw new IllegalStateException("PRICE_REPAIR_REVIEW_CONTENT_INVALID");
        return number.longValue();
    }
    private static String text(Object value) { return value == null ? null : String.valueOf(value); }
    private static boolean digest(String value) { return value != null && value.matches("[a-f0-9]{64}"); }
    private static void requireOwner(String owner) {
        if (owner == null || owner.isBlank() || owner.length() > 64) throw new SecurityException("OWNER_REQUIRED");
    }
    private static String hash(byte[] value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value)); }
        catch (java.security.NoSuchAlgorithmException failure) { throw new IllegalStateException(failure); }
    }
}
