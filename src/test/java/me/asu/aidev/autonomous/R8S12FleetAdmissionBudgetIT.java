package me.asu.aidev.autonomous;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import me.asu.aidev.autonomous.application.AutonomousRequirementService;
import me.asu.aidev.autonomous.application.StartAutonomousRequirementCommand;
import me.asu.aidev.execution.application.TaskClaimService;
import me.asu.aidev.execution.application.TaskExecutionService;
import me.asu.aidev.fleet.application.FleetAdmissionBudgetService;
import me.asu.aidev.work.domain.TaskPriority;
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
class R8S12FleetAdmissionBudgetIT {
    private static final String BASE = "7777777777777777777777777777777777777777";
    private static final Path DB;
    static {
        try {
            DB = Files.createTempFile("r8-s12-fleet-budget-", ".db");
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
    @Autowired TaskExecutionService executions;
    @Autowired FleetAdmissionBudgetService fleetBudget;

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void globalBudgetHardCapsClaimsAcrossRunsAndUsesLiveLeaseStateWithoutASecondCounter() throws Exception {
        start("r8s12-a");
        start("r8s12-b");
        start("r8s12-c");

        WorkerId worker = new WorkerId("r8s12-worker");
        workers.register(new RegisterWorkerCommand(
                worker, "test", Set.of("requirement-analysis", "fleet-limit", "repo:repo-fleet"), 4));

        var first = claims.claimNext(worker).orElseThrow();
        var second = claims.claimNext(worker).orElseThrow();
        assertThat(first.task().subjectId()).isNotEqualTo(second.task().subjectId());
        assertThat(claims.claimNext(worker)).isEmpty();

        var atLimit = fleetBudget.snapshot();
        assertThat(atLimit.maxParallelism()).isEqualTo(2);
        assertThat(atLimit.activeLeases()).isEqualTo(2);
        assertThat(atLimit.availableSlots()).isZero();
        assertThat(atLimit.saturated()).isTrue();

        JsonNode dashboard = dashboard();
        assertThat(dashboard.path("admission").path("maxParallelism").asInt()).isEqualTo(2);
        assertThat(dashboard.path("admission").path("activeLeases").asInt()).isEqualTo(2);
        assertThat(dashboard.path("admission").path("availableSlots").asInt()).isZero();
        assertThat(dashboard.path("admission").path("saturated").asBoolean()).isTrue();
        assertThat(dashboard.path("backlog").path("fleetAdmissionBlockedReady").asInt()).isEqualTo(1);
        assertThat(dashboard.path("backlog").path("admissionBlockedReady").asInt()).isZero();
        assertThat(dashboard.path("backlog").path("claimableReady").asInt()).isZero();
        assertThat(dashboard.path("backlog").path("readyTasks").get(0).path("fleetAdmissionAllowed").asBoolean()).isFalse();

        executions.start(first.task().id(), worker, first.lease().token());
        executions.fail(first.task().id(), worker, first.lease().token(), null, "release one fleet slot");

        var afterFailure = fleetBudget.snapshot();
        assertThat(afterFailure.activeLeases()).isEqualTo(1);
        assertThat(afterFailure.availableSlots()).isEqualTo(1);
        assertThat(afterFailure.saturated()).isFalse();

        var third = claims.claimNext(worker).orElseThrow();
        assertThat(third.task().subjectId()).isNotEqualTo(first.task().subjectId());
        assertThat(fleetBudget.snapshot().activeLeases()).isEqualTo(2);
        assertThat(claims.claimNext(worker)).isEmpty();
    }

    private void start(String requestId) {
        autonomous.start(new StartAutonomousRequirementCommand(
                requestId, "Exercise fleet admission budget " + requestId, "repo-fleet", BASE,
                Set.of("fleet-limit"), TaskPriority.HIGH));
    }

    private JsonNode dashboard() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + "/api/r1/fleet/dashboard?detailLimit=20"))
                .timeout(Duration.ofSeconds(5))
                .GET().build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        return json.readTree(response.body());
    }
}
