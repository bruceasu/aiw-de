package me.asu.aidev.workflow.application;

import java.sql.DriverManager;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import me.asu.aidev.ai.application.AiProfileCatalog;
import me.asu.aidev.execution.application.TaskExecutionService;
import me.asu.aidev.execution.domain.LeaseToken;
import me.asu.aidev.execution.jdbc.JdbcExecutionAttemptRepository;
import me.asu.aidev.execution.port.TaskConcurrencyBudget;
import me.asu.aidev.work.application.*;
import me.asu.aidev.work.domain.*;
import me.asu.aidev.work.jdbc.*;
import me.asu.aidev.work.port.TaskDependencyRepository;
import me.asu.aidev.worker.domain.*;
import me.asu.aidev.worker.jdbc.JdbcWorkerRepository;
import me.asu.aidev.workflow.jdbc.JdbcRecommendationRepository;
import org.junit.jupiter.api.*;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class RecommendationAcceptanceTest {
    SingleConnectionDataSource source;
    JdbcTemplate jdbc;
    TransactionTemplate transaction;
    JdbcRecommendationRepository repository;
    JdbcWorkTaskRepository tasks;
    JdbcExecutionAttemptRepository attempts;
    JdbcWorkerRepository workers;
    WorkTaskService taskService;
    AiProfileCatalog profiles;
    WorkflowRecommendationService service;
    WorkerId worker = new WorkerId("acceptance-worker");

    @BeforeEach void database() throws Exception {
        source = new SingleConnectionDataSource(DriverManager.getConnection("jdbc:sqlite::memory:"), true);
        jdbc = new JdbcTemplate(source);
        jdbc.execute("PRAGMA foreign_keys=ON");
        var migrations = new ResourceDatabasePopulator();
        for (String file : List.of("V1__initial_schema.sql", "V2__workflow_human_outcome.sql",
                "V3__task_outputs.sql", "V4__workflow_repair_authorization.sql",
                "V5__workflow_question_decision.sql", "V6__recommendation_configuration.sql"))
            migrations.addScript(new ClassPathResource("db/migration/" + file));
        migrations.execute(source);
        transaction = new TransactionTemplate(new DataSourceTransactionManager(source));
        repository = new JdbcRecommendationRepository(jdbc);
        tasks = new JdbcWorkTaskRepository(jdbc, source, new SqliteWorkDatabaseDialect());
        attempts = new JdbcExecutionAttemptRepository(jdbc);
        workers = new JdbcWorkerRepository(jdbc);
        taskService = new WorkTaskService(tasks, mock(TaskDependencyRepository.class), mock(DependencyResolver.class));
        profiles = new AiProfileCatalog(new StandardEnvironment());
        service = newService();
    }
    @AfterEach void close() { source.destroy(); }
    WorkflowRecommendationService newService() {
        return new WorkflowRecommendationService(repository, profiles, taskService, tasks, attempts, mock(WorkflowEditorService.class));
    }
    <T> T tx(Supplier<T> action) { return transaction.execute(status -> action.get()); }
    void onlineBusy() {
        Instant now = Instant.now();
        workers.register(new Worker(worker, "codex-cli:acceptance", Set.of("implementation-planning"),
                2, 2, WorkerStatus.ACTIVE, now, now, now));
    }
    WorkflowRecommendationService.Request request(String context, long revision, String goal) {
        return new WorkflowRecommendationService.Request(UUID.randomUUID().toString(), 7, goal, context, revision);
    }
    WorkflowRecommendationService.Result submit(String goal) {
        var context = tx(() -> service.createContext("alice"));
        return tx(() -> service.request(request(context.id(), 0, goal), "alice"));
    }
    int count(String table) { return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class); }

    @Test void A01_defaultsPersistAuditAndRejectStaleRevision() {
        assertThat(service.settings().enabled()).isTrue();
        tx(() -> service.configure(new WorkflowRecommendationService.Configuration(false, null, null, null, 0), "admin"));
        service = newService();
        assertThat(service.settings().enabled()).isFalse();
        assertThat(service.settings().revision()).isEqualTo(1);
        assertThat(service.audit()).hasSize(1);
        var audit = service.audit().getFirst();
        assertThat(audit.actor()).isEqualTo("admin");
        assertThat(audit.occurredAt()).isAfter(Instant.EPOCH);
        assertThat(audit.previous()).contains("\"enabled\":true");
        assertThat(audit.current()).contains("\"enabled\":false");
        assertThatThrownBy(() -> tx(() -> service.configure(
                new WorkflowRecommendationService.Configuration(true, null, null, null, 0), "admin")))
                .isInstanceOf(IllegalStateException.class);
        assertThat(count("workflow_recommendation_audit")).isEqualTo(1);
        assertThat(service.settings().enabled()).isFalse();
    }
    @Test void A03_offlineRejectsWithoutWritesButBusyOnlineQueues() {
        var context = tx(() -> service.createContext("alice"));
        var input = request(context.id(), 0, "Build an audit view");
        assertThat(service.availability().code()).isEqualTo("NO_ONLINE_WORKER");
        assertThatThrownBy(() -> tx(() -> service.request(input, "alice"))).isInstanceOf(IllegalStateException.class);
        assertThat(count("work_task")).isZero();
        onlineBusy();
        assertThat(service.availability().available()).isTrue();
        assertThat(tx(() -> service.request(input, "alice")).state()).isEqualTo("READY");
        assertThat(count("workflow_recommendation_request")).isEqualTo(1);
    }
    @Test void A04_disablingCancelsOnlyQueuedAndAllowsRecoveryOfClaimed() {
        onlineBusy();
        var queued = submit("Queued analysis");
        var claimed = submit("Claimed analysis");
        Instant now = Instant.now();
        assertThat(tasks.claim(new TaskId(claimed.id()), worker, new LeaseToken("claim-token"), now, now.plusSeconds(60), now)).isPresent();
        tx(() -> service.configure(new WorkflowRecommendationService.Configuration(false, null, null, null, 0), "admin"));
        assertThat(service.get(queued.id(), "alice").state()).isEqualTo("CANCELLED");
        assertThat(service.get(claimed.id(), "alice").state()).isEqualTo("CLAIMED");
        assertThatThrownBy(() -> tx(() -> service.cancelQueued(claimed.id(), "alice"))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> submit("New analysis")).isInstanceOf(IllegalStateException.class);
        assertThat(count("work_task")).isEqualTo(2);
    }
    @Test void A05_contextRecoveryIdempotencyAndOwnership() {
        onlineBusy();
        var context = tx(() -> service.createContext("alice"));
        var input = request(context.id(), 0, "Add reporting");
        var first = tx(() -> service.request(input, "alice"));
        assertThat(tx(() -> service.request(input, "alice")).id()).isEqualTo(first.id());
        assertThat(repository.context(context.id(), "alice", false).revision()).isEqualTo(1);
        assertThatThrownBy(() -> tx(() -> service.request(request(context.id(), 0, "New goal"), "alice")))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> tx(() -> service.request(new WorkflowRecommendationService.Request(
                input.requestKey(), 7, "Different goal", context.id(), 0L), "alice")))
                .isInstanceOf(IllegalArgumentException.class);
        service = newService();
        var recovered = service.recover(context.id(), "alice");
        assertThat(recovered.context().goal()).isEqualTo("Add reporting");
        assertThat(recovered.context().inputVersion()).isEqualTo(7);
        assertThat(recovered.result().id()).isEqualTo(first.id());
        assertThat(recovered.result().state()).isEqualTo("READY");
        assertThatThrownBy(() -> service.recover(context.id(), "bob")).isInstanceOf(SecurityException.class);
        assertThat(service.contexts("bob")).isEmpty();
        assertThat(count("work_task")).isEqualTo(1);
    }
    @Test void A02_frozenGuidanceAndSelectionDoNotFollowLaterConfiguration() {
        onlineBusy();
        // The catalog is outside this rule: approved examples are fixed independently.
        profiles = mock(AiProfileCatalog.class);
        var approved = new AiProfileCatalog.NamedProfile("planner-a",
                new AiProfileCatalog.Profile("openai", "codex-cli", "model-a", "high", false, 1));
        when(profiles.approved(TaskType.EXTERNAL_WORK)).thenReturn(List.of(approved));
        when(profiles.version()).thenReturn("catalog-1");
        service = newService();
        tx(() -> service.configure(new WorkflowRecommendationService.Configuration(true, "planner-a", "model-a", "high", 0), "admin"));
        var result = submit("Frozen recommendation");
        when(profiles.approved(TaskType.EXTERNAL_WORK)).thenReturn(List.of());
        when(profiles.version()).thenReturn("catalog-2");
        var task = tasks.find(new TaskId(result.id())).orElseThrow();
        var policy = new RecommendationTaskPolicy(repository);
        var guidance = policy.guidance(task, "codex-cli:acceptance");
        assertThat(guidance.version()).isEqualTo("catalog-1");
        assertThat(guidance.allowedProfiles()).containsExactly(approved);
        assertThat(policy.select(task, "codex-cli:acceptance", "planner-a", "catalog-1", "approved").model()).isEqualTo("model-a");
        assertThatThrownBy(() -> policy.select(task, "codex-cli:acceptance", "planner-b", "catalog-1", "approved"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> policy.select(task, "codex-cli:acceptance", "planner-a", "catalog-2", "approved"))
                .isInstanceOf(IllegalArgumentException.class);
    }
    @Test void A06_expiryFailsRecommendationRequeuesOrdinaryAndRejectsOldLease() {
        onlineBusy();
        var recommendation = submit("Expiry analysis");
        var ordinary = new TaskId("ordinary-task");
        taskService.create(new CreateWorkTaskCommand(ordinary, TaskType.EXTERNAL_WORK, "ordinary", "ordinary",
                Set.of("implementation-planning"), "implementation-planner", "Analyze only", null, null, TaskPriority.NORMAL, Set.of()));
        Instant before = Instant.now().minusSeconds(120);
        for (String id : List.of(recommendation.id(), ordinary.value())) {
            var token = new LeaseToken("expired-" + id);
            assertThat(tasks.claim(new TaskId(id), worker, token, before, before.plusSeconds(60), before)).isPresent();
            jdbc.update("INSERT INTO execution_attempt(id,task_id,attempt_no,instruction_revision,worker_id,lease_token,state,created_at_epoch_ms,stop_requested_at_epoch_ms) VALUES (?,?,1,1,?,?,'CLAIMED',?,?)",
                    "attempt-" + id, id, worker.value(), token.value(), before.toEpochMilli(), id.equals(recommendation.id()) ? before.toEpochMilli() : null);
        }
        var budget = mock(TaskConcurrencyBudget.class);
        var execution = new TaskExecutionService(tasks, workers, attempts, mock(DependencyResolver.class), null, null,
                List.of(), List.of(), List.of(budget), profiles);
        assertThat(tx(() -> execution.recoverExpiredLeases(10))).isEqualTo(2);
        var id = new TaskId(recommendation.id());
        var token = new LeaseToken("expired-" + recommendation.id());
        assertThat(tasks.find(id).orElseThrow().state()).isEqualTo(TaskState.FAILED);
        assertThat(tasks.find(ordinary).orElseThrow().state()).isEqualTo(TaskState.READY);
        assertThat(tasks.findLease(id)).isEmpty();
        assertThat(jdbc.queryForObject("SELECT state FROM execution_attempt WHERE task_id=?", String.class, id.value())).isEqualTo("EXPIRED");
        assertThat(workers.find(worker).orElseThrow().activeLeases()).isZero();
        assertThat(tasks.completeOwned(id, worker, token, Instant.now())).isFalse();
        assertThat(tasks.heartbeatLease(id, worker, token, Instant.now(), Instant.now().plusSeconds(60))).isFalse();
        assertThat(tx(() -> execution.recoverExpiredLeases(10))).isZero();
        verify(budget, times(2)).release(any(), any());
    }
    @Test void A07_httpRejectsOperatorSettingsWrite() throws Exception {
        var security = new me.asu.aidev.webconsole.security.WebConsoleSecurityService(true, "operator", "fixture", "OPERATOR");
        var session = new org.springframework.mock.web.MockHttpSession();
        security.login(session);
        var controller = new me.asu.aidev.workflow.protocol.http.RecommendationConsoleController(service, security);
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(controller).build();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/ui/system/recommendations")
                .session(session).param("enabled", "false").param("revision", "0"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
        assertThat(service.settings().enabled()).isTrue();
        assertThat(count("workflow_recommendation_audit")).isZero();
    }
    @Test void A08_deletingPlanPreservesRecoveryContextWithoutDanglingPlan() {
        var context = tx(() -> service.createContext("alice"));
        jdbc.update("INSERT INTO flexible_workflow_plan(id,state,version,spec_json,created_by,updated_by,reason,created_at_epoch_ms,updated_at_epoch_ms) VALUES (?,'DRAFT',0,'{}','alice','alice','fixture',0,0)", "deletable-plan");
        tx(() -> { repository.bind(context, "deletable-plan"); return null; });
        assertThat(jdbc.update("DELETE FROM flexible_workflow_plan WHERE id=?", "deletable-plan")).isEqualTo(1);
        assertThat(service.recover(context.id(), "alice").context().planId()).isNull();
        assertThat(count("workflow_recommendation_context")).isEqualTo(1);
        assertThat(count("workflow_recommendation_plan_context")).isZero();
    }
    @Test void A09_explicitFastAppServerCannotFallBackButNonFastRemainsSupported() {
        var fast = new AiProfileCatalog.NamedProfile("app-fast",
                new AiProfileCatalog.Profile("openai", "codex-app-server", "model-a", "high", true, 1));
        var normal = new AiProfileCatalog.NamedProfile("app-normal",
                new AiProfileCatalog.Profile("openai", "codex-app-server", "model-a", "high", false, 1));
        var fallback = new AiProfileCatalog.NamedProfile("cli-normal",
                new AiProfileCatalog.Profile("openai", "codex-cli", "model-a", "high", false, 1));
        var approved = List.of(fast, normal, fallback);
        assertThatThrownBy(() -> RecommendationProfiles.filter(approved, "app-fast", null, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("fast=false");
        assertThat(RecommendationProfiles.filter(approved, "app-normal", null, null)).containsExactly(normal);
    }
}
