package me.asu.aidev.autonomous;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import me.asu.aidev.autonomous.application.AutonomousRequirementService;
import me.asu.aidev.autonomous.application.AutonomousRunService;
import me.asu.aidev.autonomous.application.StartAutonomousRequirementCommand;
import me.asu.aidev.execution.application.TaskClaimService;
import me.asu.aidev.execution.application.TaskExecutionService;
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
class R8S8FleetDashboardIT {
    private static final String BASE = "3333333333333333333333333333333333333333";
    private static final Path DB;
    static {
        try {
            DB = Files.createTempFile("r8-s8-fleet-", ".db");
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
    @Autowired WorkerService workers;
    @Autowired TaskClaimService claims;
    @Autowired TaskExecutionService executions;

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void fleetDashboardProjectsCapacityBacklogPolicyBlockingAndActiveLeasesWithoutNewAuthority() throws Exception {
        var runningRun = autonomous.start(new StartAutonomousRequirementCommand(
                "r8s8-running", "Fleet dashboard running task.", "repo-main", BASE,
                Set.of("java", "maven"), TaskPriority.HIGH));
        autonomous.start(new StartAutonomousRequirementCommand(
                "r8s8-unmatched", "Fleet dashboard unmatched task.", "repo-python", BASE,
                Set.of("python"), TaskPriority.HIGH));
        var pausedRun = autonomous.start(new StartAutonomousRequirementCommand(
                "r8s8-paused", "Fleet dashboard policy blocked task.", "repo-main", BASE,
                Set.of("java", "maven"), TaskPriority.HIGH));
        runs.pause(pausedRun.runId(), "operator:fleet", "exercise policy-blocked backlog");

        WorkerId active = new WorkerId("r8s8-active");
        workers.register(new RegisterWorkerCommand(
                active, "java", Set.of("requirement-analysis", "java", "maven", "repo:repo-main"), 2));
        WorkerId draining = new WorkerId("r8s8-draining");
        workers.register(new RegisterWorkerCommand(
                draining, "go", Set.of("requirement-analysis", "java", "maven", "repo:repo-main"), 1));
        workers.drain(draining);

        var lease = claims.claimNext(active).orElseThrow();
        assertThat(lease.task().subjectId()).isEqualTo(runningRun.runId());
        executions.start(lease.task().id(), active, lease.lease().token());

        JsonNode fleet = dashboard(10);
        assertThat(fleet.path("workers").path("total").asInt()).isEqualTo(2);
        assertThat(fleet.path("workers").path("active").asInt()).isEqualTo(1);
        assertThat(fleet.path("workers").path("draining").asInt()).isEqualTo(1);
        assertThat(fleet.path("workers").path("registeredCapacity").asInt()).isEqualTo(3);
        assertThat(fleet.path("workers").path("claimableCapacity").asInt()).isEqualTo(2);
        assertThat(fleet.path("workers").path("activeLeases").asInt()).isEqualTo(1);
        assertThat(fleet.path("workers").path("availableClaimCapacity").asInt()).isEqualTo(1);

        JsonNode backlog = fleet.path("backlog");
        assertThat(backlog.path("unfinished").asInt()).isEqualTo(3);
        assertThat(backlog.path("ready").asInt()).isEqualTo(2);
        assertThat(backlog.path("running").asInt()).isEqualTo(1);
        assertThat(backlog.path("claimableReady").asInt()).isZero();
        assertThat(backlog.path("unmatchedReady").asInt()).isEqualTo(1);
        assertThat(backlog.path("capacityBlockedReady").asInt()).isZero();
        assertThat(backlog.path("policyBlockedReady").asInt()).isEqualTo(1);
        assertThat(backlog.path("readyByType").path("REQUIREMENT_ANALYSIS").asInt()).isEqualTo(2);
        assertThat(backlog.path("readyCapabilityDemand").path("requirement-analysis").asInt()).isEqualTo(2);

        JsonNode execution = fleet.path("execution");
        assertThat(execution.path("leasedTasks").asInt()).isEqualTo(1);
        assertThat(execution.path("running").asInt()).isEqualTo(1);
        assertThat(execution.path("currentParallelism").asInt()).isEqualTo(1);
        assertThat(execution.path("workerLeaseAccountingDelta").asInt()).isZero();
        assertThat(execution.path("activeLeases").get(0).path("workerId").asText()).isEqualTo(active.value());

        JsonNode runFleet = fleet.path("runs");
        assertThat(runFleet.path("total").asInt()).isEqualTo(3);
        assertThat(runFleet.path("lifecycleRunning").asInt()).isEqualTo(3);
        assertThat(runFleet.path("paused").asInt()).isEqualTo(1);
        assertThat(runFleet.path("byPhase").path("REQUIREMENT_ANALYSIS").asInt()).isEqualTo(2);
        assertThat(runFleet.path("byPhase").path("PAUSED").asInt()).isEqualTo(1);
        assertThat(runFleet.path("activeRuns").size()).isEqualTo(3);
        assertThat(runFleet.path("attentionRuns").size()).isEqualTo(1);
        assertThat(runFleet.path("attentionRuns").get(0).path("runId").asText()).isEqualTo(pausedRun.runId());

        JsonNode limited = dashboard(1);
        assertThat(limited.path("workers").path("workers").size()).isEqualTo(1);
        assertThat(limited.path("backlog").path("readyTasks").size()).isEqualTo(1);
        assertThat(limited.path("runs").path("activeRuns").size()).isEqualTo(1);
        assertThat(limited.path("workers").path("total").asInt()).isEqualTo(2);
        assertThat(limited.path("backlog").path("ready").asInt()).isEqualTo(2);
    }

    private JsonNode dashboard(int detailLimit) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + "/api/r8/fleet/dashboard?detailLimit=" + detailLimit))
                .timeout(Duration.ofSeconds(5))
                .GET().build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        return json.readTree(response.body());
    }
}
