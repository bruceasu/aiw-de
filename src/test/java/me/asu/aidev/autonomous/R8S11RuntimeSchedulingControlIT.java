package me.asu.aidev.autonomous;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import me.asu.aidev.autonomous.application.AutonomousRequirementService;
import me.asu.aidev.autonomous.application.AutonomousRunService;
import me.asu.aidev.autonomous.application.StartAutonomousRequirementCommand;
import me.asu.aidev.execution.application.TaskClaimService;
import me.asu.aidev.work.application.CreateWorkTaskCommand;
import me.asu.aidev.work.application.WorkTaskService;
import me.asu.aidev.work.domain.TaskId;
import me.asu.aidev.work.domain.TaskPriority;
import me.asu.aidev.work.domain.TaskState;
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
class R8S11RuntimeSchedulingControlIT {
    private static final String BASE = "6666666666666666666666666666666666666666";
    private static final Path DB;
    static {
        try {
            DB = Files.createTempFile("r8-s11-runtime-scheduling-", ".db");
            Files.deleteIfExists(DB);
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + DB.toAbsolutePath());
        registry.add("aidev.approval.policy.mode", () -> "MANUAL");
    }

    @LocalServerPort int port;
    @Autowired AutonomousRequirementService autonomous;
    @Autowired AutonomousRunService runs;
    @Autowired WorkTaskService taskService;
    @Autowired WorkerService workers;
    @Autowired TaskClaimService claims;

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void runtimeLimitContractsWithoutRevokingLeasesThenExpandsAndAuditsTheChange() throws Exception {
        var started = autonomous.start(new StartAutonomousRequirementCommand(
                "r8s11-runtime", "Exercise runtime scheduling control.", "repo-runtime", BASE,
                Set.of("sched-runtime"), TaskPriority.HIGH, 2));

        for (int i = 1; i <= 3; i++) {
            taskService.create(new CreateWorkTaskCommand(
                    new TaskId(started.runId() + "~extra-" + i), TaskType.REQUIREMENT_ANALYSIS,
                    AutonomousRequirementService.SUBJECT_TYPE, started.runId(),
                    Set.of("requirement-analysis", "sched-runtime"), "r8s11-runtime", "Independent work " + i,
                    "repo-runtime", BASE, TaskPriority.HIGH, Set.of()));
        }

        WorkerId worker = new WorkerId("r8s11-worker");
        workers.register(new RegisterWorkerCommand(
                worker, "test", Set.of("requirement-analysis", "sched-runtime", "repo:repo-runtime"), 5));

        var first = claims.claimNext(worker).orElseThrow();
        var second = claims.claimNext(worker).orElseThrow();
        assertThat(claims.claimNext(worker)).isEmpty();
        assertThat(runs.find(started.runId()).orElseThrow().activeSlots()).isEqualTo(2);

        JsonNode contracted = postScheduling(started.runId(), 1, "operator:r8s11", "reduce fleet pressure", 200);
        assertThat(contracted.path("maxParallelism").asInt()).isEqualTo(1);
        assertThat(contracted.path("activeSlots").asInt()).isEqualTo(2);
        assertThat(contracted.path("availableSlots").asInt()).isZero();
        assertThat(contracted.path("overcommittedSlots").asInt()).isEqualTo(1);
        assertThat(taskService.get(first.task().id()).state()).isEqualTo(TaskState.CLAIMED);
        assertThat(taskService.get(second.task().id()).state()).isEqualTo(TaskState.CLAIMED);
        assertThat(claims.claimNext(worker)).isEmpty();

        JsonNode dashboard = get("/api/r1/runs/" + started.runId() + "/dashboard");
        assertThat(dashboard.path("scheduling").path("maxParallelism").asInt()).isEqualTo(1);
        assertThat(dashboard.path("scheduling").path("activeSlots").asInt()).isEqualTo(2);
        assertThat(dashboard.path("scheduling").path("overcommittedSlots").asInt()).isEqualTo(1);
        assertThat(dashboard.path("scheduling").path("accountingDelta").asInt()).isZero();

        JsonNode fleet = get("/api/r1/fleet/dashboard?detailLimit=20");
        JsonNode runSummary = findRun(fleet.path("runs").path("activeRuns"), started.runId());
        assertThat(runSummary.path("maxParallelism").asInt()).isEqualTo(1);
        assertThat(runSummary.path("activeSlots").asInt()).isEqualTo(2);
        assertThat(runSummary.path("overcommittedSlots").asInt()).isEqualTo(1);
        assertThat(fleet.path("backlog").path("admissionBlockedReady").asInt()).isGreaterThanOrEqualTo(1);

        // Idempotent replay of the same value must not create another audit event.
        JsonNode beforeReplay = get("/api/r1/runs/" + started.runId() + "/timeline");
        long schedulingEventsBefore = countSchedulingEvents(beforeReplay);
        postScheduling(started.runId(), 1, "operator:r8s11", "same value replay", 200);
        JsonNode afterReplay = get("/api/r1/runs/" + started.runId() + "/timeline");
        assertThat(countSchedulingEvents(afterReplay)).isEqualTo(schedulingEventsBefore);

        // Pause is the explicit way to revoke leases. Changing the scheduling limit itself never does it.
        runs.pause(started.runId(), "operator:r8s11", "reconfigure while paused");
        assertThat(runs.find(started.runId()).orElseThrow().activeSlots()).isZero();

        JsonNode expanded = postScheduling(started.runId(), 3, "operator:r8s11", "restore parallel capacity", 200);
        assertThat(expanded.path("controlState").asText()).isEqualTo("PAUSED");
        assertThat(expanded.path("maxParallelism").asInt()).isEqualTo(3);
        assertThat(expanded.path("overcommittedSlots").asInt()).isZero();

        runs.resume(started.runId(), "operator:r8s11", "continue");
        assertThat(claims.claimNext(worker)).isPresent();
        assertThat(claims.claimNext(worker)).isPresent();
        assertThat(claims.claimNext(worker)).isPresent();
        assertThat(claims.claimNext(worker)).isEmpty();
        assertThat(runs.find(started.runId()).orElseThrow().activeSlots()).isEqualTo(3);

        // 0 means unlimited and takes effect immediately without touching existing attempts.
        JsonNode unlimited = postScheduling(started.runId(), 0, "operator:r8s11", "temporary unlimited", 200);
        assertThat(unlimited.path("maxParallelism").asInt()).isZero();
        assertThat(unlimited.path("availableSlots").isNull()).isTrue();
        assertThat(claims.claimNext(worker)).isPresent();

        JsonNode timeline = get("/api/r1/runs/" + started.runId() + "/timeline");
        assertThat(countSchedulingEvents(timeline)).isEqualTo(3);
        assertThat(timeline.toString()).contains("maxParallelism=2->1");
        assertThat(timeline.toString()).contains("maxParallelism=1->3");
        assertThat(timeline.toString()).contains("maxParallelism=3->0");
        assertThat(timeline.toString()).contains("SCHEDULING");

        runs.cancel(started.runId(), "operator:r8s11", "finish test");
        postScheduling(started.runId(), 2, "operator:r8s11", "must be rejected after cancel", 409);
    }

    private JsonNode postScheduling(String runId, int maxParallelism, String controlledBy,
                                    String reason, int expectedStatus) throws Exception {
        String body = json.createObjectNode()
                .put("maxParallelism", maxParallelism)
                .put("controlledBy", controlledBy)
                .put("reason", reason)
                .toString();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + "/api/r1/runs/" + runId + "/scheduling"))
                .timeout(Duration.ofSeconds(5))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(expectedStatus);
        return json.readTree(response.body());
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

    private static JsonNode findRun(JsonNode runs, String runId) {
        for (JsonNode run : runs) {
            if (runId.equals(run.path("runId").asText())) return run;
        }
        throw new AssertionError("run not found in fleet dashboard: " + runId);
    }

    private static long countSchedulingEvents(JsonNode timeline) {
        long count = 0;
        for (JsonNode entry : timeline.path("entries")) {
            if ("RUN_MAX_PARALLELISM_CHANGED".equals(entry.path("eventType").asText())) count++;
        }
        return count;
    }
}
