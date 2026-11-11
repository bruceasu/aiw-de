package me.asu.aidev.execution.pricing;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import me.asu.aidev.authority.AuthorityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class ModelUsageViewTest {
    @TempDir Path temp;
    private JdbcTemplate db;
    private ModelUsageView view;

    @BeforeEach void setUp() {
        db = new JdbcTemplate(new DriverManagerDataSource("jdbc:sqlite:" + temp.resolve("usage.db")));
        db.execute("CREATE TABLE authority_owner(id TEXT PRIMARY KEY,actor TEXT NOT NULL)");
        db.execute("CREATE TABLE work_task(id TEXT PRIMARY KEY,subject_type TEXT,subject_id TEXT)");
        db.execute("CREATE TABLE execution_context(task_id TEXT,plan_id TEXT)");
        db.execute("CREATE TABLE execution_policy(plan_id TEXT,owner_id TEXT)");
        db.execute("CREATE TABLE flexible_workflow_plan(id TEXT,created_by TEXT)");
        db.execute("""
            CREATE TABLE model_usage_request(attempt_id TEXT,request_id TEXT,task_id TEXT,worker_id TEXT,
              provider TEXT,model TEXT,protocol TEXT,purpose TEXT,connection_kind TEXT,price_scope TEXT,
              service_tier TEXT,started_ms BIGINT,finished_ms BIGINT,state TEXT,input_tokens BIGINT,
              cached_tokens BIGINT,output_tokens BIGINT,cache_write_tokens BIGINT)
            """);
        db.execute("""
            CREATE TABLE model_usage_estimate(attempt_id TEXT,request_id TEXT,estimate_revision BIGINT,
              currency TEXT,cost_micros BIGINT,snapshot_id TEXT,rule_id TEXT,calculated_ms BIGINT)
            """);
        db.update("INSERT INTO authority_owner VALUES (?,?),(?,?)", "owner-a", "alice", "owner-b", "bob");
        db.update("INSERT INTO flexible_workflow_plan VALUES (?,?),(?,?)", "plan-a", "alice", "plan-b", "bob");
        db.update("INSERT INTO work_task VALUES (?,?,?),(?,?,?)", "task-a", "flexible-plan", "plan-a", "task-b", "flexible-plan", "plan-b");
        usage("attempt-a", "request-a", "task-a", 100L, 20L, 10L, "API_KEY");
        usage("attempt-a", "request-b", "task-a", 50L, null, 2L, "CHATGPT");
        usage("attempt-b", "request-c", "task-b", 999L, 0L, 7L, "API_KEY");
        db.update("INSERT INTO model_usage_estimate VALUES (?,?,?,?,?,?,?,?)", "attempt-a", "request-a", 1, "USD", 2000, "snapshot", "rule", 200);
        view = new ModelUsageView(new AuthorityRepository(db));
    }

    private void usage(String attempt, String request, String task, Long input, Long cached, Long output, String connection) {
        db.update("INSERT INTO model_usage_request VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                attempt, request, task, "worker", "provider", "model", "chat", "task", connection, "TEXT_ONLY", "default",
                100, 200, "COMPLETED", input, cached, output, null);
    }

    private long count(Object value) { return ((Number)value).longValue(); }

    @Test void totalsPreserveUnknownsAndExcludeOtherOwners() {
        var snapshot = view.read("owner-a", new ModelUsageView.Query("plan-a", null));
        assertEquals(2, count(snapshot.totals().get("calls")));
        assertEquals(150, count(snapshot.totals().get("known_input")));
        assertEquals(1, count(snapshot.totals().get("missing_cached")));
        assertEquals(1, count(snapshot.totals().get("without_estimate")));
        assertEquals(2000, count(snapshot.currencies().getFirst().get("known_cost_micros")));
        assertEquals(2, snapshot.calls().size());
    }

    @Test void taskFilterCannotReadAnotherOwnersPlan() {
        var snapshot = view.read("owner-a", new ModelUsageView.Query("plan-b", "task-b"));
        assertEquals(0, count(snapshot.totals().get("calls")));
        assertTrue(snapshot.calls().isEmpty());
        assertNull(snapshot.totals().get("known_input"));
        assertTrue(snapshot.currencies().isEmpty());
    }
}
