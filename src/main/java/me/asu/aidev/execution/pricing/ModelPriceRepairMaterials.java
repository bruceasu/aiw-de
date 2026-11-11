package me.asu.aidev.execution.pricing;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import me.asu.aidev.authority.AuthorityBoundary;
import me.asu.aidev.authority.AuthorityRepository;
import me.asu.aidev.execution.guard.GuardContract.Lease;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Freezes public repair inputs; validation and installation are separate. */
@Service
public class ModelPriceRepairMaterials {
    private static final int MAX_MODULE = 1_000_000, MAX_PAGE = 2_000_000;
    private final AuthorityRepository repo;
    private final AuthorityBoundary boundary;
    private final Path root;
    public record File(String name, String sha256, String contentBase64) {}
    public record Payload(String protocol, String repairId, String provider, String sourceUrl, String reason,
                          String collectorVersion, String failureFingerprint, List<File> files) {}

    public ModelPriceRepairMaterials(AuthorityRepository repo, AuthorityBoundary boundary,
            @Value("${aidev.model-pricing.collector-root:workers/price-collectors}") String root) {
        this.repo = repo; this.boundary = boundary; this.root = Path.of(root).toAbsolutePath().normalize();
    }

    @Transactional
    public String prepare(String provider) {
        if (!List.of("deepseek", "openai").contains(provider)) throw new IllegalArgumentException("PRICE_PROVIDER_INVALID");
        boundary.lock();
        var rows = repo.jdbc().queryForList("""
            SELECT s.last_success_ms,s.content_sha256 AS success_sha,s.last_success_sample_base64,
                   s.collector_sha256 AS success_collector_sha,f.fingerprint,f.collector_sha256,
                   f.collector_version,f.reason,f.source_url,f.content_sha256 AS failure_sha,f.sample_base64
              FROM model_price_collection_state s JOIN model_price_collection_failure f ON f.provider=s.provider
             WHERE s.provider=? AND s.status='FAILED' AND s.last_success_ms IS NOT NULL
               AND f.active=TRUE AND f.failure_count>=2 AND s.last_success_sample_base64 IS NOT NULL
            """, provider);
        if (rows.size() != 1) return null;
        Map<String,Object> row = rows.getFirst();
        String codeSha = string(row, "collector_sha256"), successCodeSha = string(row, "success_collector_sha");
        if (codeSha == null || !codeSha.matches("[a-f0-9]{64}") || !codeSha.equals(successCodeSha)) return null;
        String fingerprint = string(row, "fingerprint");
        if (fingerprint == null || !fingerprint.matches("[a-f0-9]{64}")) return null;
        var active = repo.jdbc().queryForList("""
            SELECT m.id,m.failure_fingerprint,m.collector_sha256,m.last_success_sha256,m.changed_page_sha256,
                   f.content_sha256 AS current_failure_sha,v.state AS activation_state,v.verified_page_sha256
              FROM model_price_repair_material m
              LEFT JOIN model_price_collection_failure f ON f.provider=m.provider
              LEFT JOIN model_price_repair_activation v ON v.material_id=m.id
             WHERE m.provider=? AND m.status='PREPARED'
            """, provider);
        if (!active.isEmpty()) {
            var previous = active.getFirst();
            if (fingerprint.equals(previous.get("failure_fingerprint")) && codeSha.equals(previous.get("collector_sha256"))
                    && java.util.Objects.equals(row.get("success_sha"), previous.get("last_success_sha256"))) {
                if (!"FAILED".equals(previous.get("activation_state"))) return string(previous, "id");
                Object verifiedPage = previous.get("verified_page_sha256");
                Object blockedPage = verifiedPage == null ? previous.get("changed_page_sha256") : verifiedPage;
                if (java.util.Objects.equals(row.get("failure_sha"), blockedPage)) return null;
            }
            repo.jdbc().update("UPDATE model_price_repair_material SET status='SUPERSEDED' WHERE id=? AND status='PREPARED'", previous.get("id"));
        }
        String id = "price-repair-" + java.util.UUID.randomUUID();
        byte[] module = readInstalled(provider);
        if (!hash(module).equals(codeSha)) return null;
        String oldPage = string(row, "last_success_sample_base64"), newPage = string(row, "sample_base64");
        if (!verifiedPage(oldPage, string(row, "success_sha")) || !verifiedPage(newPage, string(row, "failure_sha")))
            throw new IllegalStateException("PRICE_REPAIR_SAMPLE_INVALID");
        repo.jdbc().update("""
            INSERT INTO model_price_repair_material(id,provider,failure_fingerprint,collector_sha256,collector_version,
              source_url,reason,original_module_base64,last_success_sha256,last_success_sample_base64,
              changed_page_sha256,changed_page_base64,created_ms,status)
            VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,'PREPARED')
            """, id, provider, fingerprint, codeSha, row.get("collector_version"), row.get("source_url"), row.get("reason"),
                Base64.getEncoder().encodeToString(module), row.get("success_sha"), oldPage, row.get("failure_sha"), newPage,
                System.currentTimeMillis());
        return id;
    }

    /** Only the running, unexpired task lease can read its own immutable public inputs. */
    public Payload read(String task, Lease lease) {
        if (task == null || task.isBlank() || task.length() > 128 || lease == null)
            throw new IllegalArgumentException("PRICE_REPAIR_LEASE_INVALID");
        var rows = repo.jdbc().queryForList("""
            SELECT m.* FROM model_price_repair_material m
              JOIN work_task t ON t.subject_type='price-source-repair' AND t.subject_id=m.id
              JOIN execution_attempt a ON a.task_id=t.id AND a.id=?
              JOIN model_price_collection_state s ON s.provider=m.provider
              JOIN model_price_collection_failure f ON f.provider=m.provider
             WHERE t.id=? AND t.state='RUNNING' AND t.current_worker_id=? AND t.lease_token=?
               AND t.lease_expires_at_epoch_ms>? AND a.state='RUNNING' AND a.worker_id=? AND a.lease_token=?
               AND a.stop_requested_at_epoch_ms IS NULL AND m.status='PREPARED' AND s.status='FAILED'
               AND f.active=TRUE AND f.failure_count>=2 AND f.fingerprint=m.failure_fingerprint
               AND f.collector_sha256=m.collector_sha256 AND s.collector_sha256=m.collector_sha256
               AND s.content_sha256=m.last_success_sha256
            """, lease.attemptId(), task, lease.workerId(), lease.leaseToken(), System.currentTimeMillis(),
                lease.workerId(), lease.leaseToken());
        if (rows.size() != 1) throw new SecurityException("PRICE_REPAIR_LEASE_REQUIRED");
        var row = rows.getFirst();
        File module = file("collector.py", string(row, "collector_sha256"), string(row, "original_module_base64"), MAX_MODULE);
        File oldPage = file("last-success.page", string(row, "last_success_sha256"), string(row, "last_success_sample_base64"), MAX_PAGE);
        File newPage = file("changed.page", string(row, "changed_page_sha256"), string(row, "changed_page_base64"), MAX_PAGE);
        return new Payload("aiw.price-repair-material.v1", string(row, "id"), string(row, "provider"),
                string(row, "source_url"), string(row, "reason"), string(row, "collector_version"),
                string(row, "failure_fingerprint"), List.of(module, oldPage, newPage));
    }

    private static File file(String name, String expected, String encoded, int limit) {
        if (encoded == null || encoded.length() > (limit * 4L / 3) + 8 || expected == null || !expected.matches("[a-f0-9]{64}"))
            throw new IllegalStateException("PRICE_REPAIR_MATERIAL_CORRUPT");
        try {
            byte[] bytes = Base64.getDecoder().decode(encoded);
            if (bytes.length == 0 || bytes.length > limit || !hash(bytes).equals(expected))
                throw new IllegalStateException("PRICE_REPAIR_MATERIAL_CORRUPT");
            return new File(name, expected, encoded);
        } catch (IllegalArgumentException failure) { throw new IllegalStateException("PRICE_REPAIR_MATERIAL_CORRUPT"); }
    }

    private byte[] readInstalled(String provider) {
        try {
            Path realRoot = root.toRealPath();
            Path file = realRoot.resolve(provider + ".py");
            if (Files.isSymbolicLink(file) || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
                    || Files.size(file) > MAX_MODULE) throw new IllegalStateException("PRICE_REPAIR_MODULE_INVALID");
            try (var input = Files.newInputStream(file, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
                byte[] bytes = input.readNBytes(MAX_MODULE + 1);
                if (bytes.length == 0 || bytes.length > MAX_MODULE) throw new IllegalStateException("PRICE_REPAIR_MODULE_INVALID");
                return bytes;
            }
        } catch (java.io.IOException failure) { throw new IllegalStateException("PRICE_REPAIR_MODULE_UNAVAILABLE"); }
    }

    private static boolean verifiedPage(String encoded, String expected) {
        if (encoded == null || encoded.length() > 2_700_000 || expected == null || !expected.matches("[a-f0-9]{64}")) return false;
        try {
            byte[] content = Base64.getDecoder().decode(encoded);
            return content.length > 0 && content.length <= MAX_PAGE && hash(content).equals(expected);
        } catch (IllegalArgumentException failure) { return false; }
    }
    private static String string(Map<String,Object> row, String key) { return (String)row.get(key); }
    private static String hash(byte[] value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value)); }
        catch (java.security.NoSuchAlgorithmException failure) { throw new IllegalStateException(failure); }
    }
}
