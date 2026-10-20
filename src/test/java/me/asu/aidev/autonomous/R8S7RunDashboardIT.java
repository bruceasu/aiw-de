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
class R8S7RunDashboardIT {
    private static final String BASE = "2222222222222222222222222222222222222222";
    private static final Path DB;
    static {
        try {
            DB = Files.createTempFile("r8-s7-dashboard-", ".db");
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
    void dashboardProjectsPhaseProgressActiveWorkerRetriesAndOperatorControlWithoutNewAuthority() throws Exception {
        var started = autonomous.start(new StartAutonomousRequirementCommand(
                "r8s7-dashboard",
                "Exercise operational dashboard projection without adding dashboard persistence.",
                "repo-main", BASE, Set.of("java", "maven"), TaskPriority.HIGH));

        WorkerId analyst = new WorkerId("r8s7-analyst");
        workers.register(new RegisterWorkerCommand(
                analyst, "test", Set.of("requirement-analysis", "java", "maven", "repo:repo-main"), 1));

        var attempt1 = claims.claimNext(analyst).orElseThrow();
        executions.start(attempt1.task().id(), analyst, attempt1.lease().token());

        JsonNode running = dashboard(started.runId());
        assertThat(running.path("phase").asText()).isEqualTo("REQUIREMENT_ANALYSIS");
        assertThat(running.path("effectiveStatus").asText()).isEqualTo("RUNNING");
        assertThat(running.path("tasks").path("total").asInt()).isEqualTo(1);
        assertThat(running.path("tasks").path("running").asInt()).isEqualTo(1);
        assertThat(running.path("execution").path("attempts").asInt()).isEqualTo(1);
        assertThat(running.path("execution").path("retries").asInt()).isZero();
        assertThat(running.path("execution").path("currentParallelism").asInt()).isEqualTo(1);
        assertThat(running.path("execution").path("peakParallelism").asInt()).isEqualTo(1);
        assertThat(running.path("execution").path("active").get(0).path("workerId").asText())
                .isEqualTo(analyst.value());
        assertThat(running.path("elapsedMillis").asLong()).isGreaterThanOrEqualTo(0L);

        executions.fail(attempt1.task().id(), analyst, attempt1.lease().token(),
                "thread-r8s7-failed", "transient analysis failure");

        JsonNode failed = dashboard(started.runId());
        assertThat(failed.path("phase").asText()).isEqualTo("FAILED");
        assertThat(failed.path("failureCount").asInt()).isEqualTo(1);
        assertThat(failed.path("execution").path("failedAttempts").asInt()).isEqualTo(1);
        assertThat(failed.path("execution").path("currentParallelism").asInt()).isZero();

        runs.recover(started.runId(), "operator:dashboard");
        var attempt2 = claims.claimNext(analyst).orElseThrow();
        executions.start(attempt2.task().id(), analyst, attempt2.lease().token());

        JsonNode retried = dashboard(started.runId());
        assertThat(retried.path("phase").asText()).isEqualTo("REQUIREMENT_ANALYSIS");
        assertThat(retried.path("execution").path("attempts").asInt()).isEqualTo(2);
        assertThat(retried.path("execution").path("retries").asInt()).isEqualTo(1);
        assertThat(retried.path("recoveryCount").asInt()).isEqualTo(1);

        runs.pause(started.runId(), "operator:dashboard", "inspect dashboard pause state");
        JsonNode paused = dashboard(started.runId());
        assertThat(paused.path("phase").asText()).isEqualTo("PAUSED");
        assertThat(paused.path("effectiveStatus").asText()).isEqualTo("PAUSED");
        assertThat(paused.path("execution").path("currentParallelism").asInt()).isZero();
        assertThat(paused.path("execution").path("expiredAttempts").asInt()).isEqualTo(1);
        assertThat(paused.path("pauseCount").asInt()).isEqualTo(1);
    }

    private JsonNode dashboard(String runId) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + "/api/r1/runs/" + runId + "/dashboard"))
                .timeout(Duration.ofSeconds(5))
                .GET().build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        return json.readTree(response.body());
    }
}
