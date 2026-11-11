package me.asu.aidev.execution.pricing;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import me.asu.aidev.authority.AuthorityBoundary;
import me.asu.aidev.authority.AuthorityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.core.io.ClassPathResource;

class ModelPriceCollectionStateTest {
    @TempDir Path temp;
    private JdbcTemplate db;
    private ModelPriceCollectionState state;

    @BeforeEach void setUp() {
        var datasource = new DriverManagerDataSource("jdbc:sqlite:" + temp.resolve("collection.db"));
        db = new JdbcTemplate(datasource);
        db.execute("CREATE TABLE authority_lock(id INTEGER PRIMARY KEY,version INTEGER)");
        db.execute("CREATE TABLE work_task(id TEXT PRIMARY KEY,subject_type TEXT,subject_id TEXT,state TEXT)");
        db.execute("CREATE TABLE execution_attempt(id TEXT PRIMARY KEY,task_id TEXT,state TEXT,stop_requested_at_epoch_ms BIGINT)");
        db.update("INSERT INTO authority_lock VALUES(1,1)");
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V37__price_collection_state.sql"),
                new ClassPathResource("db/migration/V38__price_collection_failures.sql"),
                new ClassPathResource("db/migration/V39__price_collector_identity.sql"),
                new ClassPathResource("db/migration/V40__price_repair_material.sql"),
                new ClassPathResource("db/migration/V41__price_repair_result.sql")).execute(datasource);
        var repo = new AuthorityRepository(db);
        state = new ModelPriceCollectionState(repo, new AuthorityBoundary(repo));
    }

    private ModelPriceCollectorProcess.Observation observation(long time) {
        return new ModelPriceCollectorProcess.Observation("aiw.price-observation.v1", "deepseek",
                "https://api-docs.deepseek.com/pricing", "a".repeat(64), "test", time, "USD",
                "micro-currency-per-million-tokens", "OBSERVED_ONLY", false,
                List.of("TRUSTED_EFFECTIVE_INTERVAL", "CHINESE_PUBLIC_HOLIDAY_CALENDAR"), Map.of(), List.of());
    }

    @Test void failureRetainsPreviousObservationAndPreventsImmediateDuplicate() {
        String first = state.claimDeepSeek(1_000_000);
        assertNotNull(first);
        assertNull(state.claimDeepSeek(1_000_001));
        state.observed(first, observation(1_000_002));
        String second = state.claimDeepSeek(1_400_000);
        state.failed(second, "SOURCE_UNAVAILABLE");
        var row = db.queryForMap("SELECT * FROM model_price_collection_state WHERE provider='deepseek'");
        assertEquals("FAILED", row.get("status"));
        assertEquals("SOURCE_UNAVAILABLE", row.get("failure_code"));
        assertEquals(1_000_002L, ((Number)row.get("last_success_ms")).longValue());
        assertNotNull(row.get("observation_json"));
    }

    @Test void staleCollectorCannotReplaceANewerLease() {
        String old = state.claimDeepSeek(1_000_000);
        String current = state.claimDeepSeek(1_400_000);
        state.observed(old, observation(1_000_002));
        assertEquals("RUNNING", state.status().getFirst().get("status"));
        state.observed(current, observation(1_400_002));
        assertEquals("OBSERVED_INCOMPLETE", state.status().getFirst().get("status"));
    }

    @Test void onlyPreviouslyWorkingSourceWithRepeatedStructureFailureNeedsRepair() {
        String first = state.claimDeepSeek(1_000_000);
        state.observed(first, observation(1_000_002));
        var change = new ModelPriceCollectorProcess.SourceChange("https://api-docs.deepseek.com/pricing", 1_400_001,
                "v1", "PRICE_ROWS_CHANGED", "f".repeat(64), "a".repeat(64), "cHVibGlj");
        String second = state.claimDeepSeek(1_400_000);
        state.structureChanged(second, change);
        assertEquals(0, ((Number)state.status().getFirst().get("repair_needed")).intValue());
        String third = state.claimDeepSeek(1_800_000);
        state.structureChanged(third, change);
        assertEquals(1, ((Number)state.status().getFirst().get("repair_needed")).intValue());
        assertEquals(2, ((Number)state.status().getFirst().get("failure_count")).intValue());
        String fourth = state.claimDeepSeek(2_200_000);
        state.observed(fourth, observation(2_200_001));
        assertEquals(0, ((Number)state.status().getFirst().get("repair_needed")).intValue());
    }

    @Test void firstInstallAndNetworkFailuresDoNotRequestRepair() {
        var change = new ModelPriceCollectorProcess.SourceChange("https://api-docs.deepseek.com/pricing", 1_000_001,
                "v1", "PRICE_ROWS_CHANGED", "f".repeat(64), "a".repeat(64), "cHVibGlj");
        state.structureChanged(state.claimDeepSeek(1_000_000), change);
        state.structureChanged(state.claimDeepSeek(1_400_000), change);
        state.failed(state.claimDeepSeek(1_800_000), "SOURCE_UNAVAILABLE");
        var row = state.status().getFirst();
        assertEquals(0, ((Number)row.get("repair_needed")).intValue());
        assertEquals(2, ((Number)row.get("failure_count")).intValue());
    }

    @Test void providersKeepIndependentLeasesAndFailureStatus() {
        String deepseek = state.claim("deepseek", 1_000_000);
        String openai = state.claim("openai", 1_000_000);
        assertNotNull(deepseek);
        assertNotNull(openai);
        state.observed(deepseek, observation(1_000_001));
        state.failed("openai", openai, "SOURCE_UNAVAILABLE");
        var rows = state.status();
        assertEquals("deepseek", rows.get(0).get("provider"));
        assertEquals("OBSERVED_INCOMPLETE", rows.get(0).get("status"));
        assertEquals("openai", rows.get(1).get("provider"));
        assertEquals("FAILED", rows.get(1).get("status"));
    }
}
