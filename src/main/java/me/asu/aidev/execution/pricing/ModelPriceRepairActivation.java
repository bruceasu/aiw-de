package me.asu.aidev.execution.pricing;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import me.asu.aidev.authority.AuthorityBoundary;
import me.asu.aidev.authority.AuthorityRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Executes a Plane-owner-approved candidate once. A failed run needs a new approval. */
@Component
public class ModelPriceRepairActivation {
    private static final Logger LOG = LoggerFactory.getLogger(ModelPriceRepairActivation.class);
    private static final long CLAIM_MS = 120_000;
    private final AuthorityRepository repo;
    private final AuthorityBoundary boundary;
    private final ModelPriceCollectionState state;
    private final ModelPriceCollectorProcess collector;
    private final ModelPriceObservationPublisher publisher;
    private final Path root;
    private final TransactionTemplate transactions;
    private final boolean enabled;
    private final AtomicBoolean started = new AtomicBoolean();
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(task -> {
        var thread = new Thread(task, "model-price-repair-activation"); thread.setDaemon(true); return thread;
    });

    public ModelPriceRepairActivation(AuthorityRepository repo, AuthorityBoundary boundary,
            ModelPriceCollectionState state, ModelPriceCollectorProcess collector,
            ModelPriceObservationPublisher publisher, PlatformTransactionManager transactionManager,
            @Value("${aidev.model-pricing.collector-root:workers/price-collectors}") String root,
            @Value("${aidev.model-pricing.enabled:true}") boolean enabled) {
        this.repo = repo; this.boundary = boundary; this.state = state; this.collector = collector;
        this.publisher = publisher; this.root = Path.of(root).toAbsolutePath().normalize();
        this.transactions = new TransactionTemplate(transactionManager);
        this.enabled = enabled;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        if (enabled && started.compareAndSet(false, true)) executor.scheduleWithFixedDelay(this::run, 15, 30, TimeUnit.SECONDS);
    }

    private void run() {
        try {
            for (int count = 0; count < 2; count++) {
                Candidate candidate = claim();
                if (candidate == null) break;
                activate(candidate);
            }
        } catch (RuntimeException failure) {
            LOG.warn("Price repair activation pass failed ({})", failure.getClass().getSimpleName());
        }
    }

    private Candidate claim() {
        return transactions.execute(ignored -> {
            boundary.lock();
            long now = System.currentTimeMillis();
            // An interrupted execution is not retried; a Plane owner must approve it again.
            repo.jdbc().update("""
                UPDATE model_price_repair_activation SET state='FAILED',lease_token=NULL,lease_until_ms=NULL,
                  collection_lease_token=NULL,failure_code='APPROVAL_EXECUTION_INTERRUPTED',updated_ms=?
                 WHERE state IN ('VERIFYING','INSTALLING') AND execution_started_ms IS NOT NULL
                   AND lease_until_ms IS NOT NULL AND lease_until_ms<=?
                """, now, now);
            var rows = repo.jdbc().queryForList("""
                SELECT m.id,m.provider,m.collector_sha256,m.original_module_base64,
                       r.module_sha256,r.module_base64,v.state AS activation_state,
                       v.approved_page_sha256,v.approved_page_base64,v.approved_source_url,
                       v.approved_page_observed_ms
                  FROM model_price_repair_material m
                  JOIN model_price_collection_state s ON s.provider=m.provider
                  JOIN model_price_collection_failure f ON f.provider=m.provider
                  JOIN work_task t ON t.subject_type='price-source-repair' AND t.subject_id=m.id AND t.state='COMPLETED'
                  JOIN model_price_repair_result r ON r.material_id=m.id AND r.task_id=t.id
                  JOIN execution_attempt a ON a.id=r.attempt_id AND a.task_id=t.id AND a.state='COMPLETED'
                  JOIN model_price_repair_activation v ON v.material_id=m.id
                 WHERE m.status='PREPARED' AND f.active=TRUE AND f.failure_count>=2
                   AND f.fingerprint=m.failure_fingerprint AND f.collector_sha256=m.collector_sha256
                   AND f.content_sha256=m.changed_page_sha256 AND f.source_url=m.source_url
                   AND s.collector_sha256=m.collector_sha256 AND s.content_sha256=m.last_success_sha256
                   AND v.approved_by IS NOT NULL AND v.approved_candidate_sha256=r.module_sha256
                   AND v.approved_page_sha256 IS NOT NULL AND v.approved_source_url=r.source_url
                   AND v.state='VERIFYING' AND v.execution_started_ms IS NULL
                   AND a.stop_requested_at_epoch_ms IS NULL
                   AND r.attempt_id=(SELECT r2.attempt_id FROM model_price_repair_result r2
                       WHERE r2.material_id=m.id ORDER BY r2.received_ms DESC,r2.attempt_id DESC LIMIT 1)
                 ORDER BY r.received_ms DESC LIMIT 1
                """);
            if (rows.isEmpty()) return null;
            Map<String,Object> row = rows.getFirst();
            String materialId = (String)row.get("id");
            String collectionLease = state.claimRepair(materialId, now);
            if (collectionLease == null) return null;
            String token = UUID.randomUUID().toString();
            int claimed = repo.jdbc().update("""
                UPDATE model_price_repair_activation SET lease_token=?,lease_until_ms=?,execution_started_ms=?,
                  collection_lease_token=?,failure_code=NULL,updated_ms=?
                 WHERE material_id=? AND state='VERIFYING' AND execution_started_ms IS NULL
                   AND approved_candidate_sha256=? AND approved_page_sha256=?
                """, token, now + CLAIM_MS, now, collectionLease, now, materialId,
                    row.get("module_sha256"), row.get("approved_page_sha256"));
            if (claimed != 1) throw new IllegalStateException("PRICE_ACTIVATION_LEASE_LOST");
            return new Candidate(materialId, (String)row.get("provider"),
                    (String)row.get("collector_sha256"), (String)row.get("original_module_base64"),
                    (String)row.get("module_sha256"), (String)row.get("module_base64"), token,
                    (String)row.get("approved_page_sha256"), (String)row.get("approved_page_base64"),
                    (String)row.get("approved_source_url"), ((Number)row.get("approved_page_observed_ms")).longValue(),
                    collectionLease);
        });
    }

    private void activate(Candidate candidate) {
        String collectionLease = candidate.collectionLeaseToken();
        try {
            byte[] original = decode(candidate.originalBase64(), candidate.baseSha256());
            byte[] module = decode(candidate.moduleBase64(), candidate.moduleSha256());
            byte[] page = decodePage(candidate.approvedPageBase64(), candidate.approvedPageSha256());
            if (MessageDigest.isEqual(original, module)) throw new IllegalStateException("PRICE_CANDIDATE_UNCHANGED");
            var verified = collector.parseApprovedCandidate(candidate.provider(), module, page,
                    candidate.approvedPageObservedMs(), candidate.approvedSourceUrl());
            if (!publisher.canPublish(verified.observation())) throw new IllegalStateException("PRICE_CANDIDATE_HAS_NO_PUBLISHABLE_PRICES");
            markInstalling(candidate, collectionLease, verified);
            install(candidate, module);
            String lease = collectionLease;
            transactions.executeWithoutResult(ignored -> {
                boundary.lock();
                if (!publisher.publish(verified.observation())) throw new IllegalStateException("PRICE_CANDIDATE_NOT_PUBLISHABLE");
                state.observed(lease, verified);
                int updated = repo.jdbc().update("""
                    UPDATE model_price_repair_activation SET state='INSTALLED',lease_token=NULL,lease_until_ms=NULL,
                      collection_lease_token=NULL,candidate_sha256=?,verified_page_sha256=?,failure_code=NULL,updated_ms=?
                     WHERE material_id=? AND lease_token=? AND state='INSTALLING'
                    """, candidate.moduleSha256(), verified.observation().contentSha256(), System.currentTimeMillis(),
                        candidate.materialId(), candidate.activationToken());
                if (updated != 1) throw new IllegalStateException("PRICE_ACTIVATION_LEASE_LOST");
            });
        } catch (RuntimeException failure) {
            String code = classify(failure);
            if (collectionLease != null && !"PRICE_ACTIVATION_LEASE_LOST".equals(failure.getMessage())) {
                try { state.failed(candidate.provider(), collectionLease, "COLLECTOR_REJECTED"); }
                catch (RuntimeException ignored) { }
            }
            reject(candidate, code);
            LOG.warn("Price collector candidate was not activated ({})", code);
        }
    }

    private void markInstalling(Candidate candidate, String collectionLease, ModelPriceCollectorProcess.Collected verified) {
        transactions.executeWithoutResult(ignored -> {
            boundary.lock();
            Long eligible = repo.jdbc().queryForObject("""
                SELECT COUNT(*) FROM model_price_repair_material m
                  JOIN model_price_collection_state s ON s.provider=m.provider
                  JOIN model_price_collection_failure f ON f.provider=m.provider
                  JOIN work_task t ON t.subject_type='price-source-repair' AND t.subject_id=m.id AND t.state='COMPLETED'
                  JOIN model_price_repair_result r ON r.material_id=m.id AND r.task_id=t.id
                  JOIN execution_attempt a ON a.id=r.attempt_id AND a.task_id=t.id AND a.state='COMPLETED'
                  JOIN model_price_repair_activation v ON v.material_id=m.id
                 WHERE m.id=? AND m.status='PREPARED' AND s.status='RUNNING' AND s.lease_token=?
                   AND f.active=TRUE AND f.fingerprint=m.failure_fingerprint AND f.collector_sha256=m.collector_sha256
                   AND f.content_sha256=m.changed_page_sha256 AND f.source_url=m.source_url
                   AND s.collector_sha256=m.collector_sha256 AND s.content_sha256=m.last_success_sha256
                   AND a.stop_requested_at_epoch_ms IS NULL
                   AND v.lease_token=? AND v.state='VERIFYING' AND v.execution_started_ms IS NOT NULL
                   AND v.approved_by IS NOT NULL AND v.approved_candidate_sha256=?
                   AND v.approved_page_sha256 IS NOT NULL AND v.approved_source_url=r.source_url
                """, Long.class, candidate.materialId(), collectionLease, candidate.activationToken(), candidate.moduleSha256());
            if (eligible == null || eligible != 1) throw new IllegalStateException("PRICE_REPAIR_MATERIAL_STALE");
            int updated = repo.jdbc().update("""
                UPDATE model_price_repair_activation SET state='INSTALLING',lease_until_ms=?,candidate_sha256=?,
                  verified_page_sha256=?,updated_ms=?
                 WHERE material_id=? AND lease_token=? AND state='VERIFYING' AND execution_started_ms IS NOT NULL
                   AND approved_candidate_sha256=? AND approved_page_sha256=?
                """, System.currentTimeMillis() + CLAIM_MS, candidate.moduleSha256(),
                    verified.observation().contentSha256(), System.currentTimeMillis(),
                    candidate.materialId(), candidate.activationToken(), candidate.moduleSha256(), candidate.approvedPageSha256());
            if (updated != 1) throw new IllegalStateException("PRICE_ACTIVATION_LEASE_LOST");
        });
    }

    private void install(Candidate candidate, byte[] module) {
        Path stage = null;
        try {
            Path directory = root.toRealPath();
            Path target = directory.resolve(candidate.provider() + ".py");
            Path backup = directory.resolve(candidate.provider() + "-" + candidate.materialId() + ".bak");
            if (Files.isSymbolicLink(target)) throw new IllegalStateException("PRICE_REPAIR_TARGET_INVALID");
            if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                if (!Files.isRegularFile(backup, LinkOption.NOFOLLOW_LINKS) || !hash(readBounded(backup)).equals(candidate.baseSha256()))
                    throw new IllegalStateException("PRICE_REPAIR_BASE_CHANGED");
                Files.move(backup, target, StandardCopyOption.ATOMIC_MOVE);
            }
            if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) throw new IllegalStateException("PRICE_REPAIR_TARGET_INVALID");
            if (hash(readBounded(target)).equals(candidate.moduleSha256())) return;
            if (!hash(readBounded(target)).equals(candidate.baseSha256())) throw new IllegalStateException("PRICE_REPAIR_BASE_CHANGED");
            if (Files.exists(backup, LinkOption.NOFOLLOW_LINKS)) {
                if (!Files.isRegularFile(backup, LinkOption.NOFOLLOW_LINKS) || !hash(readBounded(backup)).equals(candidate.baseSha256()))
                    throw new IllegalStateException("PRICE_REPAIR_BACKUP_CONFLICT");
                Files.delete(backup);
            }
            stage = Files.createTempFile(directory, candidate.provider() + "-", ".candidate");
            try (FileChannel output = FileChannel.open(stage, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
                output.write(java.nio.ByteBuffer.wrap(module)); output.force(true);
            }
            Files.move(target, backup, StandardCopyOption.ATOMIC_MOVE);
            if (!hash(readBounded(backup)).equals(candidate.baseSha256())) {
                if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) Files.move(backup, target, StandardCopyOption.ATOMIC_MOVE);
                throw new IllegalStateException("PRICE_REPAIR_BASE_CHANGED");
            }
            try {
                Files.move(stage, target, StandardCopyOption.ATOMIC_MOVE);
                stage = null;
            } catch (IOException failure) {
                if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS) && Files.exists(backup, LinkOption.NOFOLLOW_LINKS))
                    Files.move(backup, target, StandardCopyOption.ATOMIC_MOVE);
                throw failure;
            }
            if (!hash(readBounded(target)).equals(candidate.moduleSha256())) {
                Files.delete(target);
                Files.move(backup, target, StandardCopyOption.ATOMIC_MOVE);
                throw new IllegalStateException("PRICE_REPAIR_INSTALL_DIGEST_MISMATCH");
            }
        } catch (IOException failure) {
            throw new IllegalStateException("PRICE_REPAIR_INSTALL_FAILED");
        } finally {
            if (stage != null) try { Files.deleteIfExists(stage); } catch (IOException ignored) { }
        }
    }

    private void reject(Candidate candidate, String code) {
        activationState(candidate, "FAILED", code);
    }

    private void activationState(Candidate candidate, String status, String code) {
        try {
            transactions.executeWithoutResult(ignored -> {
                boundary.lock();
                repo.jdbc().update("""
                    UPDATE model_price_repair_activation SET state=?,lease_token=NULL,lease_until_ms=?,
                      collection_lease_token=NULL,failure_code=?,updated_ms=?
                     WHERE material_id=? AND lease_token=?
                    """, status, null, code,
                        System.currentTimeMillis(), candidate.materialId(), candidate.activationToken());
            });
        } catch (RuntimeException failure) {
            LOG.warn("Price repair activation status could not be recorded ({})", failure.getClass().getSimpleName());
        }
    }

    private static byte[] decode(String encoded, String expected) {
        try {
            byte[] value = Base64.getDecoder().decode(encoded);
            if (value.length == 0 || value.length > 1_000_000 || !hash(value).equals(expected))
                throw new IllegalArgumentException("PRICE_REPAIR_RESULT_INVALID");
            return value;
        } catch (IllegalArgumentException failure) { throw new IllegalArgumentException("PRICE_REPAIR_RESULT_INVALID"); }
    }

    private static byte[] decodePage(String encoded, String expected) {
        try {
            if (encoded == null || expected == null) throw new IllegalArgumentException("PRICE_REPAIR_PAGE_INVALID");
            byte[] value = Base64.getDecoder().decode(encoded);
            if (value.length == 0 || value.length > ModelPriceCollectorProcess.MAX_CONTENT || !hash(value).equals(expected))
                throw new IllegalArgumentException("PRICE_REPAIR_PAGE_INVALID");
            return value;
        } catch (IllegalArgumentException failure) { throw new IllegalArgumentException("PRICE_REPAIR_PAGE_INVALID"); }
    }

    private static String classify(RuntimeException failure) {
        String message = failure.getMessage();
        return message != null && message.matches("PRICE_[A-Z0-9_]{1,70}") ? message : "PRICE_CANDIDATE_REJECTED";
    }
    private static String hash(byte[] value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value)); }
        catch (java.security.NoSuchAlgorithmException failure) { throw new IllegalStateException(failure); }
    }

    private static byte[] readBounded(Path path) throws IOException {
        long size = Files.size(path);
        if (size < 1 || size > 1_000_000) throw new IllegalStateException("PRICE_REPAIR_MODULE_INVALID");
        return Files.readAllBytes(path);
    }

    @PreDestroy public void close() { executor.shutdownNow(); }

    private record Candidate(String materialId, String provider, String baseSha256, String originalBase64,
                             String moduleSha256, String moduleBase64, String activationToken,
                             String approvedPageSha256, String approvedPageBase64, String approvedSourceUrl,
                             long approvedPageObservedMs, String collectionLeaseToken) { }
}
