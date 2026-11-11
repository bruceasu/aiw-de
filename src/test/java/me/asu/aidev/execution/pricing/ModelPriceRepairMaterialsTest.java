package me.asu.aidev.execution.pricing;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import me.asu.aidev.authority.AuthorityBoundary;
import me.asu.aidev.authority.AuthorityRepository;
import me.asu.aidev.execution.guard.GuardContract.Lease;
import me.asu.aidev.work.domain.TaskId;
import me.asu.aidev.work.port.WorkTaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

class ModelPriceRepairMaterialsTest {
    @TempDir Path temp;
    private JdbcTemplate db;
    private ModelPriceCollectionState state;
    private ModelPriceRepairMaterials repairs;
    private ModelPriceRepairResults results;
    private byte[] module, oldPage, changedPage;

    @BeforeEach void setUp() throws Exception {
        var datasource = new DriverManagerDataSource("jdbc:sqlite:" + temp.resolve("repair.db"));
        db = new JdbcTemplate(datasource);
        db.execute("CREATE TABLE authority_lock(id INTEGER PRIMARY KEY,version INTEGER)");
        db.execute("CREATE TABLE work_task(id TEXT PRIMARY KEY,subject_type TEXT,subject_id TEXT,state TEXT,current_worker_id TEXT,lease_token TEXT,lease_expires_at_epoch_ms BIGINT)");
        db.execute("CREATE TABLE execution_attempt(id TEXT PRIMARY KEY,task_id TEXT,worker_id TEXT,lease_token TEXT,state TEXT,stop_requested_at_epoch_ms BIGINT)");
        db.update("INSERT INTO authority_lock VALUES(1,1)");
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V37__price_collection_state.sql"),
                new ClassPathResource("db/migration/V38__price_collection_failures.sql"),
                new ClassPathResource("db/migration/V39__price_collector_identity.sql"),
                new ClassPathResource("db/migration/V40__price_repair_material.sql"),
                new ClassPathResource("db/migration/V41__price_repair_result.sql"),
                new ClassPathResource("db/migration/V42__price_repair_activation.sql"),
                new ClassPathResource("db/migration/V43__price_repair_admin_approval.sql")).execute(datasource);
        var repo = new AuthorityRepository(db);
        var boundary = new AuthorityBoundary(repo);
        state = new ModelPriceCollectionState(repo, boundary);
        repairs = new ModelPriceRepairMaterials(repo, boundary, temp.toString());
        var tasks = mock(WorkTaskRepository.class);
        when(tasks.lock(any(TaskId.class))).thenReturn(true);
        var collector = new ModelPriceCollectorProcess(repo, "python", temp.toString());
        results = new ModelPriceRepairResults(repo, boundary, repairs, tasks, collector);
        module = "# fixture collector".getBytes(StandardCharsets.UTF_8);
        oldPage = "public old".getBytes(StandardCharsets.UTF_8);
        changedPage = "public new".getBytes(StandardCharsets.UTF_8);
        Files.write(temp.resolve("deepseek.py"), module);
    }

    private static String hash(byte[] value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    }
    private static String b64(byte[] value) { return Base64.getEncoder().encodeToString(value); }

    private void makeCandidate() throws Exception {
        var observation = new ModelPriceCollectorProcess.Observation("aiw.price-observation.v1", "deepseek",
                "https://api-docs.deepseek.com/pricing", hash(oldPage), "v1", 1_000_001, "USD",
                "micro-currency-per-million-tokens", "OBSERVED_ONLY", false,
                List.of("TRUSTED_EFFECTIVE_INTERVAL", "CHINESE_PUBLIC_HOLIDAY_CALENDAR"), Map.of("source", "fixture"),
                List.of(Map.of("model", "fixture")), hash(module));
        state.observed(state.claim("deepseek", 1_000_000), new ModelPriceCollectorProcess.Collected(observation, b64(oldPage)));
        var change = new ModelPriceCollectorProcess.SourceChange("deepseek", "https://api-docs.deepseek.com/pricing",
                1_400_001, "v1", hash(module), "PRICE_ROWS_CHANGED", "f".repeat(64), hash(changedPage), b64(changedPage));
        state.structureChanged(state.claim("deepseek", 1_400_000), change);
        state.structureChanged(state.claim("deepseek", 1_800_000), change);
    }

    @Test void freezesInputsOnceWithoutCreatingAWorkerTask() throws Exception {
        makeCandidate();
        String id = repairs.prepare("deepseek");
        assertNotNull(id);
        assertEquals(id, repairs.prepare("deepseek"));
        var row = db.queryForMap("SELECT * FROM model_price_repair_material WHERE id=?", id);
        assertEquals("PREPARED", row.get("status"));
        assertArrayEquals(module, Base64.getDecoder().decode((String)row.get("original_module_base64")));
        assertArrayEquals(oldPage, Base64.getDecoder().decode((String)row.get("last_success_sample_base64")));
        assertArrayEquals(changedPage, Base64.getDecoder().decode((String)row.get("changed_page_base64")));
        assertEquals(1L, db.queryForObject("SELECT COUNT(*) FROM model_price_repair_material", Long.class));
    }

    @Test void refusesInstalledModuleChangedAfterTheFailure() throws Exception {
        makeCandidate();
        Files.writeString(temp.resolve("deepseek.py"), "# changed module");
        assertNull(repairs.prepare("deepseek"));
        assertEquals(0L, db.queryForObject("SELECT COUNT(*) FROM model_price_repair_material", Long.class));
    }

    @Test void materialReadRequiresLiveExactLeaseAndFailsAfterStopOrRecovery() throws Exception {
        makeCandidate();
        String id = repairs.prepare("deepseek");
        db.update("INSERT INTO work_task VALUES(?,?,?,?,?,?,?)", "task", "price-source-repair", id, "RUNNING", "worker", "lease", System.currentTimeMillis() + 60_000);
        db.update("INSERT INTO execution_attempt VALUES(?,?,?,?,?,?)", "attempt", "task", "worker", "lease", "RUNNING", null);
        var lease = new Lease("worker", "attempt", "lease");
        var payload = repairs.read("task", lease);
        assertEquals(id, payload.repairId());
        assertEquals(3, payload.files().size());
        assertThrows(SecurityException.class, () -> repairs.read("task", new Lease("other", "attempt", "lease")));
        db.update("UPDATE execution_attempt SET stop_requested_at_epoch_ms=1 WHERE id='attempt'");
        assertThrows(SecurityException.class, () -> repairs.read("task", lease));
        db.update("UPDATE execution_attempt SET stop_requested_at_epoch_ms=NULL WHERE id='attempt'");
        state.observed(state.claim("deepseek", 2_200_000), new ModelPriceCollectorProcess.Collected(
                new ModelPriceCollectorProcess.Observation("aiw.price-observation.v1", "deepseek",
                        "https://api-docs.deepseek.com/pricing", hash(oldPage), "v1", 2_200_001, "USD",
                        "micro-currency-per-million-tokens", "OBSERVED_ONLY", false,
                        List.of("TRUSTED_EFFECTIVE_INTERVAL", "CHINESE_PUBLIC_HOLIDAY_CALENDAR"), Map.of("source", "fixture"),
                        List.of(Map.of("model", "fixture")), hash(module)), b64(oldPage)));
        assertEquals("SUPERSEDED", db.queryForObject("SELECT status FROM model_price_repair_material WHERE id=?", String.class, id));
        assertThrows(SecurityException.class, () -> repairs.read("task", lease));
    }

    @Test void rejectsTamperedStoredMaterial() throws Exception {
        makeCandidate();
        String id = repairs.prepare("deepseek");
        db.update("INSERT INTO work_task VALUES(?,?,?,?,?,?,?)", "task", "price-source-repair", id, "RUNNING", "worker", "lease", System.currentTimeMillis() + 60_000);
        db.update("INSERT INTO execution_attempt VALUES(?,?,?,?,?,?)", "attempt", "task", "worker", "lease", "RUNNING", null);
        db.update("UPDATE model_price_repair_material SET original_module_base64=? WHERE id=?", b64("tampered".getBytes(StandardCharsets.UTF_8)), id);
        assertThrows(IllegalStateException.class, () -> repairs.read("task", new Lease("worker", "attempt", "lease")));
    }

    @Test void aLaterFailureEpisodeGetsNewMaterialAfterRecovery() throws Exception {
        makeCandidate();
        String first = repairs.prepare("deepseek");
        var success = new ModelPriceCollectorProcess.Observation("aiw.price-observation.v1", "deepseek",
                "https://api-docs.deepseek.com/pricing", hash(oldPage), "v1", 2_200_001, "USD",
                "micro-currency-per-million-tokens", "OBSERVED_ONLY", false,
                List.of("TRUSTED_EFFECTIVE_INTERVAL", "CHINESE_PUBLIC_HOLIDAY_CALENDAR"), Map.of("source", "fixture"),
                List.of(Map.of("model", "fixture")), hash(module));
        state.observed(state.claim("deepseek", 2_200_000), new ModelPriceCollectorProcess.Collected(success, b64(oldPage)));
        var change = new ModelPriceCollectorProcess.SourceChange("deepseek", "https://api-docs.deepseek.com/pricing",
                2_600_001, "v1", hash(module), "PRICE_ROWS_CHANGED", "f".repeat(64), hash(changedPage), b64(changedPage));
        state.structureChanged(state.claim("deepseek", 2_600_000), change);
        state.structureChanged(state.claim("deepseek", 3_000_000), change);
        String second = repairs.prepare("deepseek");
        assertNotNull(second);
        assertNotEquals(first, second);
        assertEquals(2L, db.queryForObject("SELECT COUNT(*) FROM model_price_repair_material", Long.class));
    }

    @Test void differentFailureReplacesStalePreparedMaterial() throws Exception {
        makeCandidate();
        String first = repairs.prepare("deepseek");
        var change = new ModelPriceCollectorProcess.SourceChange("deepseek", "https://api-docs.deepseek.com/pricing",
                2_200_001, "v1", hash(module), "OTHER_STRUCTURE_CHANGED", "e".repeat(64), hash(changedPage), b64(changedPage));
        state.structureChanged(state.claim("deepseek", 2_200_000), change);
        state.structureChanged(state.claim("deepseek", 2_600_000), change);
        String second = repairs.prepare("deepseek");
        assertNotNull(second);
        assertNotEquals(first, second);
        assertEquals("SUPERSEDED", db.queryForObject("SELECT status FROM model_price_repair_material WHERE id=?", String.class, first));
    }

    @Test void submittedProposalIsImmutableAndCanReplayAfterStop() throws Exception {
        makeCandidate();
        String id = repairs.prepare("deepseek");
        db.update("INSERT INTO work_task VALUES(?,?,?,?,?,?,?)", "task", "price-source-repair", id, "RUNNING", "worker", "lease", System.currentTimeMillis() + 60_000);
        db.update("INSERT INTO execution_attempt VALUES(?,?,?,?,?,?)", "attempt", "task", "worker", "lease", "RUNNING", null);
        byte[] proposal = "# fixed collector".getBytes(StandardCharsets.UTF_8), checks = "assert True".getBytes(StandardCharsets.UTF_8);
        var lease = new Lease("worker", "attempt", "lease");
        var input = new ModelPriceRepairResults.Submission(lease, id, hash(proposal), b64(proposal), hash(checks), b64(checks), "https://api-docs.deepseek.com/quick_start/pricing/", "fixed");
        assertEquals("SUBMITTED", results.submit("task", input).get("state"));
        db.update("UPDATE execution_attempt SET stop_requested_at_epoch_ms=1,state='EXPIRED' WHERE id='attempt'");
        assertEquals("SUBMITTED", results.submit("task", input).get("state"));
        var changed = new ModelPriceRepairResults.Submission(lease, id, hash(proposal), b64(proposal), hash(checks), b64(checks), "https://api-docs.deepseek.com/quick_start/pricing/", "different summary");
        assertThrows(IllegalStateException.class, () -> results.submit("task", changed));
        assertArrayEquals(module, Files.readAllBytes(temp.resolve("deepseek.py")));
        assertEquals(1L, db.queryForObject("SELECT COUNT(*) FROM model_price_repair_result", Long.class));
    }

    @Test void newProposalCannotBeSubmittedAfterStop() throws Exception {
        makeCandidate();
        String id = repairs.prepare("deepseek");
        db.update("INSERT INTO work_task VALUES(?,?,?,?,?,?,?)", "task", "price-source-repair", id, "RUNNING", "worker", "lease", System.currentTimeMillis() + 60_000);
        db.update("INSERT INTO execution_attempt VALUES(?,?,?,?,?,?)", "attempt", "task", "worker", "lease", "RUNNING", 1);
        byte[] proposal = "# fixed".getBytes(StandardCharsets.UTF_8), checks = "assert True".getBytes(StandardCharsets.UTF_8);
        var input = new ModelPriceRepairResults.Submission(new Lease("worker", "attempt", "lease"), id, hash(proposal), b64(proposal), hash(checks), b64(checks), "https://api-docs.deepseek.com/quick_start/pricing/", "fixed");
        assertThrows(SecurityException.class, () -> results.submit("task", input));
        assertEquals(0L, db.queryForObject("SELECT COUNT(*) FROM model_price_repair_result", Long.class));
    }

    @Test void passingRepairRequiresProposalFromCurrentLiveLease() throws Exception {
        makeCandidate();
        String id = repairs.prepare("deepseek");
        db.update("INSERT INTO work_task VALUES(?,?,?,?,?,?,?)", "task", "price-source-repair", id, "RUNNING", "worker", "lease", System.currentTimeMillis() + 60_000);
        db.update("INSERT INTO execution_attempt VALUES(?,?,?,?,?,?)", "attempt", "task", "worker", "lease", "RUNNING", null);
        assertThrows(IllegalStateException.class, () -> results.requireSubmittedIfRepair("task", "worker", "lease"));
        byte[] proposal = "# fixed".getBytes(StandardCharsets.UTF_8), checks = "assert True".getBytes(StandardCharsets.UTF_8);
        results.submit("task", new ModelPriceRepairResults.Submission(new Lease("worker", "attempt", "lease"), id,
                hash(proposal), b64(proposal), hash(checks), b64(checks), "https://api-docs.deepseek.com/quick_start/pricing/", "fixed"));
        results.requireSubmittedIfRepair("task", "worker", "lease");
        assertThrows(IllegalStateException.class, () -> results.requireSubmittedIfRepair("task", "other", "lease"));
        db.update("UPDATE execution_attempt SET stop_requested_at_epoch_ms=1 WHERE id='attempt'");
        assertThrows(SecurityException.class, () -> results.requireSubmittedIfRepair("task", "worker", "lease"));
    }

    @Test void statusDistinguishesSubmittedCandidateFromCompletedVerification() throws Exception {
        makeCandidate();
        String id = repairs.prepare("deepseek");
        String task = "price-repair-task-" + id.substring("price-repair-".length());
        db.update("INSERT INTO work_task VALUES(?,?,?,?,?,?,?)", task, "price-source-repair", id, "READY", null, null, null);
        assertEquals("WAITING_WORKER", state.status().getFirst().get("repair_stage"));
        db.update("UPDATE work_task SET state='RUNNING',current_worker_id='worker',lease_token='lease',lease_expires_at_epoch_ms=? WHERE id=?", System.currentTimeMillis()+60_000, task);
        db.update("INSERT INTO execution_attempt VALUES(?,?,?,?,?,?)", "attempt", task, "worker", "lease", "RUNNING", null);
        assertEquals("REPAIRING", state.status().getFirst().get("repair_stage"));
        byte[] proposal = "# fixed".getBytes(StandardCharsets.UTF_8), checks = "assert True".getBytes(StandardCharsets.UTF_8);
        results.submit(task, new ModelPriceRepairResults.Submission(new Lease("worker", "attempt", "lease"), id,
                hash(proposal), b64(proposal), hash(checks), b64(checks), "https://api-docs.deepseek.com/quick_start/pricing/", "fixed"));
        db.update("UPDATE work_task SET state='COMPLETED' WHERE id=?", task);
        db.update("UPDATE execution_attempt SET state='COMPLETED' WHERE id='attempt'");
        assertEquals("AWAITING_VERIFICATION", state.status().getFirst().get("repair_stage"));
        db.update("UPDATE execution_attempt SET stop_requested_at_epoch_ms=1 WHERE id='attempt'");
        assertEquals("STOPPED", state.status().getFirst().get("repair_stage"));
    }

    @Test void repairClaimRejectsStoppedOrRepeatedAttempts() throws Exception {
        makeCandidate();
        String id = repairs.prepare("deepseek");
        var now = java.time.Instant.now();
        var task = new me.asu.aidev.work.domain.WorkTask(new TaskId("task"), me.asu.aidev.work.domain.TaskType.EXTERNAL_WORK,
                me.asu.aidev.work.domain.TaskState.READY, "price-source-repair", id, java.util.Set.of(), "skill", "",
                null, null, me.asu.aidev.work.domain.TaskPriority.NORMAL, 0, now, now);
        var policy = new ModelPriceRepairClaimPolicy(new AuthorityRepository(db));
        assertTrue(policy.canClaim(task));
        db.update("INSERT INTO execution_attempt VALUES(?,?,?,?,?,?)", "a1", "task", "worker", "lease", "EXPIRED", 1);
        assertFalse(policy.canClaim(task));
        db.update("UPDATE execution_attempt SET stop_requested_at_epoch_ms=NULL WHERE id='a1'");
        db.update("INSERT INTO execution_attempt VALUES(?,?,?,?,?,?)", "a2", "task", "worker", "lease2", "EXPIRED", null);
        db.update("INSERT INTO execution_attempt VALUES(?,?,?,?,?,?)", "a3", "task", "worker", "lease3", "EXPIRED", null);
        assertFalse(policy.canClaim(task));
    }
}
