package me.asu.aidev.execution.pricing;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import me.asu.aidev.authority.AuthorityBoundary;
import me.asu.aidev.authority.AuthorityRepository;
import me.asu.aidev.execution.guard.GuardContract.Lease;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

class ModelUsageLedgerTest {
    @TempDir Path temp;
    private JdbcTemplate db;
    private ModelUsageLedger ledger;
    private final Lease lease = new Lease("worker", "attempt", "lease");

    @BeforeEach void setUp() {
        var datasource = new DriverManagerDataSource("jdbc:sqlite:" + temp.resolve("ledger.db"));
        db = new JdbcTemplate(datasource);
        db.execute("CREATE TABLE authority_lock(id INTEGER PRIMARY KEY,version INTEGER)");
        db.execute("CREATE TABLE work_task(id TEXT PRIMARY KEY)");
        db.execute("CREATE TABLE execution_attempt(id TEXT PRIMARY KEY,task_id TEXT,worker_id TEXT,lease_token TEXT,started_at_epoch_ms BIGINT)");
        db.update("INSERT INTO authority_lock VALUES(1,1)");
        db.update("INSERT INTO work_task VALUES('task')");
        db.update("INSERT INTO execution_attempt VALUES('attempt','task','worker','lease',100)");
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V36__model_usage_pricing.sql")).execute(datasource);
        var repo = new AuthorityRepository(db);
        ledger = new ModelUsageLedger(repo, new AuthorityBoundary(repo));
    }

    private ModelUsageLedger.Start start(String request, String tier) {
        return new ModelUsageLedger.Start(lease, request, "openai", "model", "responses", "task", "API_KEY", "TEXT_ONLY", tier, 100);
    }
    private ModelUsageLedger.Terminal terminal(String request, String tier) {
        return new ModelUsageLedger.Terminal(lease, request, "COMPLETED", 200, 10L, 0L, 2L, null, tier);
    }

    @Test void recordsOnlyProviderReportedEffectiveTier() {
        assertThrows(IllegalArgumentException.class, () -> ledger.start("task", start("bad", "default")));
        ledger.start("task", start("known", "unknown"));
        ledger.finish("task", terminal("known", "priority"));
        ledger.finish("task", terminal("known", "priority"));
        ledger.start("task", start("known", "unknown"));
        assertEquals("priority", db.queryForObject("SELECT service_tier FROM model_usage_request WHERE request_id='known'", String.class));
    }

    @Test void missingOrAutomaticEffectiveTierRemainsUnknown() {
        ledger.start("task", start("missing", "unknown"));
        assertThrows(IllegalArgumentException.class, () -> ledger.finish("task", terminal("missing", "auto")));
        ledger.finish("task", terminal("missing", null));
        assertEquals("unknown", db.queryForObject("SELECT service_tier FROM model_usage_request WHERE request_id='missing'", String.class));
    }
}
