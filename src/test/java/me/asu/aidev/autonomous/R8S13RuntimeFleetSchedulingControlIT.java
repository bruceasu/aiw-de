package me.asu.aidev.autonomous;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import me.asu.aidev.autonomous.application.AutonomousRequirementService;
import me.asu.aidev.autonomous.application.AutonomousRunService;
import me.asu.aidev.autonomous.application.StartAutonomousRequirementCommand;
import me.asu.aidev.execution.application.TaskClaimService;
import me.asu.aidev.fleet.application.FleetAdmissionBudgetService;
import me.asu.aidev.work.application.WorkTaskService;
import me.asu.aidev.work.domain.TaskPriority;
import me.asu.aidev.work.domain.TaskState;
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
class R8S13RuntimeFleetSchedulingControlIT {
    private static final String BASE = "8888888888888888888888888888888888888888";
    private static final Path DB;
    static {
        try {
            DB = Files.createTempFile("r8-s13-runtime-fleet-scheduling-", ".db");
            Files.deleteIfExists(DB);
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + DB.toAbsolutePath());
        registry.add("aidev.approval.policy.mode", () -> "MANUAL");
        registry.add("aidev.autonomous.default-max-parallelism", () -> "0");
        registry.add("aidev.fleet.max-parallelism", () -> "2");
    }

    @LocalServerPort int port;
    @Autowired AutonomousRequirementService autonomous;
    @Autowired WorkerService workers;
    @Autowired TaskClaimService claims;
    @Autowired AutonomousRunService runs;
    @Autowired WorkTaskService tasks;
    @Autowired FleetAdmissionBudgetService fleetBudget;

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void runtimeFleetLimitContractsWithoutRevokingLeasesThenExpandsPersistsAndAudits() throws Exception {
        for (int i = 1; i <= 6; i++) start("r8s13-" + i);

        WorkerId worker = new WorkerId("r8s13-worker");
        workers.register(new RegisterWorkerCommand(
                worker, "test", Set.of("requirement-analysis", "fleet-runtime", "repo:repo-fleet-runtime"), 6));

        var first = claims.claimNext(worker).orElseThrow();
        var second = claims.claimNext(worker).orElseThrow();
        assertThat(claims.claimNext(worker)).isEmpty();
        assertThat(fleetBudget.snapshot().activeLeases()).isEqualTo(2);

        JsonNode contracted = postScheduling(1, "operator:r8s13", "reduce global pressure", 200);
        assertThat(contracted.path("maxParallelism").asInt()).isEqualTo(1);
        assertThat(contracted.path("activeLeases").asInt()).isEqualTo(2);
        assertThat(contracted.path("availableSlots").asInt()).isZero();
        assertThat(contracted.path("overcommittedSlots").asInt()).isEqualTo(1);
        assertThat(contracted.path("saturated").asBoolean()).isTrue();
        assertThat(tasks.get(first.task().id()).state()).isEqualTo(TaskState.CLAIMED);
        assertThat(tasks.get(second.task().id()).state()).isEqualTo(TaskState.CLAIMED);
        assertThat(claims.claimNext(worker)).isEmpty();

        JsonNode dashboard = get("/api/r8/fleet/dashboard?detailLimit=20");
        assertThat(dashboard.path("admission").path("maxParallelism").asInt()).isEqualTo(1);
        assertThat(dashboard.path("admission").path("activeLeases").asInt()).isEqualTo(2);
        assertThat(dashboard.path("admission").path("overcommittedSlots").asInt()).isEqualTo(1);
        assertThat(dashboard.path("backlog").path("fleetAdmissionBlockedReady").asInt()).isGreaterThanOrEqualTo(1);

        // Contraction never revokes existing leases. Explicit operator control can release them.
        runs.pause(first.task().subjectId(), "operator:r8s13", "release first lease for test");
        assertThat(fleetBudget.snapshot().activeLeases()).isEqualTo(1);
        assertThat(claims.claimNext(worker)).isEmpty(); // 1/1 is still saturated.

        runs.pause(second.task().subjectId(), "operator:r8s13", "release second lease for test");
        assertThat(fleetBudget.snapshot().activeLeases()).isZero();
        assertThat(claims.claimNext(worker)).isPresent();
        assertThat(fleetBudget.snapshot().activeLeases()).isEqualTo(1);

        JsonNode expanded = postScheduling(3, "operator:r8s13", "restore fleet capacity", 200);
        assertThat(expanded.path("maxParallelism").asInt()).isEqualTo(3);
        assertThat(expanded.path("activeLeases").asInt()).isEqualTo(1);
        assertThat(expanded.path("availableSlots").asInt()).isEqualTo(2);
        assertThat(expanded.path("overcommittedSlots").asInt()).isZero();
        assertThat(claims.claimNext(worker)).isPresent();
        assertThat(claims.claimNext(worker)).isPresent();
        assertThat(fleetBudget.snapshot().activeLeases()).isEqualTo(3);
        assertThat(claims.claimNext(worker)).isEmpty();

        JsonNode eventsBeforeReplay = get("/api/r8/fleet/scheduling/events?limit=20");
        assertThat(eventsBeforeReplay.size()).isEqualTo(2);
        assertThat(eventsBeforeReplay.get(0).path("previousMaxParallelism").asInt()).isEqualTo(1);
        assertThat(eventsBeforeReplay.get(0).path("maxParallelism").asInt()).isEqualTo(3);
        assertThat(eventsBeforeReplay.get(1).path("previousMaxParallelism").asInt()).isEqualTo(2);
        assertThat(eventsBeforeReplay.get(1).path("maxParallelism").asInt()).isEqualTo(1);
        assertThat(eventsBeforeReplay.get(1).path("activeLeases").asInt()).isEqualTo(2);
        assertThat(eventsBeforeReplay.get(1).path("controlledBy").asText()).isEqualTo("operator:r8s13");

        // Same value is idempotent and does not append audit noise.
        postScheduling(3, "operator:r8s13", "same value replay", 200);
        assertThat(get("/api/r8/fleet/scheduling/events?limit=20").size()).isEqualTo(2);

        // Startup configuration is bootstrap-only after V13 initialization; runtime authority survives re-application.
        assertThat(fleetBudget.configureFromDeployment().maxParallelism()).isEqualTo(3);

        postScheduling(65536, "operator:r8s13", "invalid", 400);
        postScheduling(2, "  ", "invalid actor", 400);
    }

    private void start(String requestId) {
        autonomous.start(new StartAutonomousRequirementCommand(
                requestId, "Exercise runtime fleet scheduling " + requestId,
                "repo-fleet-runtime", BASE, Set.of("fleet-runtime"), TaskPriority.HIGH));
    }

    private JsonNode postScheduling(int maxParallelism, String controlledBy, String reason, int expectedStatus)
            throws Exception {
        String body = json.createObjectNode()
                .put("maxParallelism", maxParallelism)
                .put("controlledBy", controlledBy)
                .put("reason", reason)
                .toString();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + "/api/r8/fleet/scheduling"))
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
}
