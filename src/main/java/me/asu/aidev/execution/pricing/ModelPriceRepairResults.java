package me.asu.aidev.execution.pricing;

import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import me.asu.aidev.authority.AuthorityBoundary;
import me.asu.aidev.authority.AuthorityRepository;
import me.asu.aidev.execution.guard.GuardContract.Lease;
import me.asu.aidev.work.domain.TaskId;
import me.asu.aidev.work.port.WorkTaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Stores an immutable, untrusted proposal; validation and installation are separate. */
@Service
public class ModelPriceRepairResults {
    private static final int MAX_FILE = 1_000_000;
    public record Submission(Lease lease, String repairId, String moduleSha256, String moduleBase64,
                             String checksSha256, String checksBase64, String sourceUrl, String summary) {}
    private final AuthorityRepository repo;
    private final AuthorityBoundary boundary;
    private final ModelPriceRepairMaterials materials;
    private final WorkTaskRepository tasks;
    private final ModelPriceCollectorProcess collector;
    public ModelPriceRepairResults(AuthorityRepository repo, AuthorityBoundary boundary,
                                   ModelPriceRepairMaterials materials, WorkTaskRepository tasks,
                                   ModelPriceCollectorProcess collector) {
        this.repo=repo; this.boundary=boundary; this.materials=materials; this.tasks=tasks; this.collector=collector;
    }

    @Transactional
    public Map<String,String> submit(String task, Submission input) {
        if (input == null || input.lease() == null || task == null || task.isBlank() || task.length() > 128
                || input.repairId() == null || input.sourceUrl() == null || input.sourceUrl().isBlank() || input.sourceUrl().length() > 2048
                || input.summary() == null || input.summary().isBlank() || input.summary().length() > 8192)
            throw new IllegalArgumentException("PRICE_REPAIR_RESULT_INVALID");
        validateFile(input.moduleBase64(), input.moduleSha256());
        validateFile(input.checksBase64(), input.checksSha256());
        boundary.lock();
        String digest = AuthorityRepository.hash(repo.encode(input));
        var existing = repo.jdbc().queryForList("""
            SELECT r.payload_digest FROM model_price_repair_result r JOIN execution_attempt a ON a.id=r.attempt_id
             WHERE r.attempt_id=? AND r.task_id=? AND r.worker_id=? AND a.worker_id=? AND a.lease_token=?
            """, String.class, input.lease().attemptId(), task, input.lease().workerId(), input.lease().workerId(), input.lease().leaseToken());
        if (!existing.isEmpty()) {
            if (!digest.equals(existing.getFirst())) throw new IllegalStateException("PRICE_REPAIR_RESULT_CONFLICT");
            return Map.of("state", "SUBMITTED", "attemptId", input.lease().attemptId());
        }
        if (!tasks.lock(new TaskId(task))) throw new SecurityException("PRICE_REPAIR_LEASE_REQUIRED");
        var material = materials.read(task, input.lease());
        if (!material.repairId().equals(input.repairId())) throw new SecurityException("PRICE_REPAIR_TARGET_MISMATCH");
        if (!collector.isOfficialSourceUrl(input.sourceUrl(), material.provider()))
            throw new IllegalArgumentException("PRICE_REPAIR_SOURCE_INVALID");
        repo.jdbc().update("""
                INSERT INTO model_price_repair_result(attempt_id,task_id,material_id,worker_id,module_sha256,module_base64,
                  checks_sha256,checks_base64,summary,payload_digest,received_ms,source_url)
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?)
                """,
                input.lease().attemptId(), task, input.repairId(), input.lease().workerId(),
                input.moduleSha256(), input.moduleBase64(), input.checksSha256(), input.checksBase64(),
                input.summary(), digest, System.currentTimeMillis(), input.sourceUrl());
        return Map.of("state", "SUBMITTED", "attemptId", input.lease().attemptId());
    }

    /** A PASS for repair work is valid only after this lease has submitted its proposal. */
    @Transactional
    public void requireSubmittedIfRepair(String task, String worker, String token) {
        boundary.lock();
        var subjects = repo.jdbc().queryForList("SELECT subject_type FROM work_task WHERE id=?", String.class, task);
        if (subjects.size() != 1 || !ModelPriceRepairDispatch.SUBJECT.equals(subjects.getFirst())) return;
        if (!tasks.lock(new TaskId(task))) throw new SecurityException("PRICE_REPAIR_LEASE_REQUIRED");
        var ids = repo.jdbc().queryForList("""
            SELECT r.attempt_id FROM model_price_repair_result r
              JOIN execution_attempt a ON a.id=r.attempt_id
             WHERE r.task_id=? AND r.worker_id=? AND a.worker_id=? AND a.lease_token=? AND a.state='RUNNING'
            """, String.class, task, worker, worker, token);
        if (ids.size() != 1) throw new IllegalStateException("PRICE_REPAIR_RESULT_REQUIRED");
        materials.read(task, new Lease(worker, ids.getFirst(), token));
    }

    private static void validateFile(String encoded, String expected) {
        if (encoded == null || encoded.length() > MAX_FILE * 4L / 3 + 8 || expected == null || !expected.matches("[a-f0-9]{64}"))
            throw new IllegalArgumentException("PRICE_REPAIR_RESULT_INVALID");
        try {
            byte[] bytes = Base64.getDecoder().decode(encoded);
            if (bytes.length == 0 || bytes.length > MAX_FILE || !hash(bytes).equals(expected))
                throw new IllegalArgumentException("PRICE_REPAIR_RESULT_INVALID");
            String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
            if (text.indexOf('\0') >= 0) throw new IllegalArgumentException("PRICE_REPAIR_RESULT_INVALID");
        } catch (IllegalArgumentException | java.nio.charset.CharacterCodingException failure) {
            throw new IllegalArgumentException("PRICE_REPAIR_RESULT_INVALID");
        }
    }
    private static String hash(byte[] value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value)); }
        catch (java.security.NoSuchAlgorithmException failure) { throw new IllegalStateException(failure); }
    }
}
