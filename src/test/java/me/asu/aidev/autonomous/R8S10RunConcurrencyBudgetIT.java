package me.asu.aidev.autonomous;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import me.asu.aidev.autonomous.application.AutonomousRequirementService;
import me.asu.aidev.autonomous.application.AutonomousRunService;
import me.asu.aidev.autonomous.application.StartAutonomousRequirementCommand;
import me.asu.aidev.execution.application.TaskClaimService;
import me.asu.aidev.execution.application.TaskExecutionService;
import me.asu.aidev.work.application.CreateWorkTaskCommand;
import me.asu.aidev.work.application.WorkTaskService;
import me.asu.aidev.work.domain.TaskId;
import me.asu.aidev.work.domain.TaskPriority;
import me.asu.aidev.work.domain.TaskType;
import me.asu.aidev.worker.application.RegisterWorkerCommand;
import me.asu.aidev.worker.application.WorkerService;
import me.asu.aidev.worker.domain.WorkerId;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("sqlite")
class R8S10RunConcurrencyBudgetIT {
    private static final String BASE = "5555555555555555555555555555555555555555";
    private static final Path DB;
    static {
        try {
            DB = Files.createTempFile("r8-s10-concurrency-", ".db");
            Files.deleteIfExists(DB);
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + DB.toAbsolutePath());
        registry.add("aidev.approval.policy.mode", () -> "MANUAL");
        registry.add("aidev.autonomous.default-max-parallelism", () -> "2");
    }

    @LocalServerPort int port;
    @Autowired AutonomousRequirementService autonomous;
    @Autowired AutonomousRunService runs;
    @Autowired WorkTaskService taskService;
    @Autowired WorkerService workers;
    @Autowired TaskClaimService claims;
    @Autowired TaskExecutionService executions;

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void perRunBudgetAtomicallyBlocksAdditionalClaimsAndReleasesAcrossFailurePauseAndCancel() throws Exception {
        var started = autonomous.start(new StartAutonomousRequirementCommand(
                "r8s10-limit-one", "Exercise per-run admission control.", "repo-limit", BASE,
                Set.of("sched-limit"), TaskPriority.HIGH, 1));

        taskService.create(new CreateWorkTaskCommand(
                new TaskId(started.runId() + "~extra"), TaskType.REQUIREMENT_ANALYSIS,
                AutonomousRequirementService.SUBJECT_TYPE, started.runId(),
                Set.of("requirement-analysis", "sched-limit"), "r8s10-limit", "Independent extra work",
                "repo-limit", BASE, TaskPriority.HIGH, Set.of()));

        WorkerId worker = new WorkerId("r8s10-worker");
        workers.register(new RegisterWorkerCommand(
                worker, "test", Set.of("requirement-analysis", "sched-limit", "repo:repo-limit"), 3));

        var first = claims.claimNext(worker).orElseThrow();
        assertThat(first.task().subjectId()).isEqualTo(started.runId());
        assertThat(claims.claimNext(worker)).isEmpty();

        var runAtLimit = runs.find(started.runId()).orElseThrow();
        assertThat(runAtLimit.maxParallelism()).isEqualTo(1);
        assertThat(runAtLimit.activeSlots()).isEqualTo(1);
        assertThat(runAtLimit.availableSlots()).isZero();

        JsonNode runDashboard = runDashboard(started.runId());
        assertThat(runDashboard.path("scheduling").path("maxParallelism").asInt()).isEqualTo(1);
        assertThat(runDashboard.path("scheduling").path("activeSlots").asInt()).isEqualTo(1);
        assertThat(runDashboard.path("scheduling").path("availableSlots").asInt()).isZero();
        assertThat(runDashboard.path("scheduling").path("accountingDelta").asInt()).isZero();

        JsonNode fleetAtLimit = fleetDashboard();
        assertThat(fleetAtLimit.path("backlog").path("admissionBlockedReady").asInt()).isEqualTo(1);
        assertThat(fleetAtLimit.path("backlog").path("claimableReady").asInt()).isZero();
        assertThat(fleetAtLimit.path("execution").path("runSlotAccountingDelta").asInt()).isZero();

        executions.start(first.task().id(), worker, first.lease().token());
        executions.fail(first.task().id(), worker, first.lease().token(), null, "exercise release on failure");
        assertThat(runs.find(started.runId()).orElseThrow().activeSlots()).isZero();

        runs.recover(started.runId(), "operator:r8s10");
        var second = claims.claimNext(worker).orElseThrow();
        assertThat(runs.find(started.runId()).orElseThrow().activeSlots()).isEqualTo(1);

        runs.pause(started.runId(), "operator:r8s10", "exercise release on pause");
        assertThat(runs.find(started.runId()).orElseThrow().activeSlots()).isZero();
        assertThat(claims.claimNext(worker)).isEmpty();

        runs.resume(started.runId(), "operator:r8s10", "continue after inspection");
        var third = claims.claimNext(worker).orElseThrow();
        assertThat(third.task().subjectId()).isEqualTo(started.runId());
        assertThat(runs.find(started.runId()).orElseThrow().activeSlots()).isEqualTo(1);

        runs.cancel(started.runId(), "operator:r8s10", "exercise release on cancel");
        var cancelled = runs.find(started.runId()).orElseThrow();
        assertThat(cancelled.activeSlots()).isZero();
        assertThat(cancelled.controlState().name()).isEqualTo("CANCELLED");
    }

    @Test
    void configuredDefaultBudgetIsPersistedWhenRequestDoesNotOverrideIt() {
        var started = autonomous.start(new StartAutonomousRequirementCommand(
                "r8s10-default", "Use configured default concurrency.", "repo-default", BASE,
                Set.of("sched-default"), TaskPriority.NORMAL));
        var run = runs.find(started.runId()).orElseThrow();
        assertThat(run.maxParallelism()).isEqualTo(2);
        assertThat(run.activeSlots()).isZero();
    }

    private JsonNode runDashboard(String runId) throws Exception {
        return get("/api/r1/runs/" + runId + "/dashboard");
    }

    private JsonNode fleetDashboard() throws Exception {
        return get("/api/r1/fleet/dashboard?detailLimit=20");
    }

    private JsonNode get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(5))
                .GET().build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        return json.readTree(response.body());
    }
}
