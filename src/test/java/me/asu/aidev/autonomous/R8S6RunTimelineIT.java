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
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("sqlite")
class R8S6RunTimelineIT {
    private static final String BASE = "1111111111111111111111111111111111111111";
    private static final Path DB;
    static {
        try {
            DB = Files.createTempFile("r8-s6-timeline-", ".db");
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
    @Autowired JdbcTemplate jdbc;

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void timelineCombinesProjectedExecutionHistoryWithStrictRunEventAuditOrder() throws Exception {
        var started = autonomous.start(new StartAutonomousRequirementCommand(
                "r8s6-timeline",
                "Exercise failure recovery and repeated operator controls for timeline audit.",
                "repo-main", BASE, Set.of("java", "maven"), TaskPriority.HIGH));

        WorkerId analyst = new WorkerId("r8s6-analyst");
        workers.register(new RegisterWorkerCommand(
                analyst, "test", Set.of("requirement-analysis", "java", "maven", "repo:repo-main"), 1));

        var attempt1 = claims.claimNext(analyst).orElseThrow();
        executions.start(attempt1.task().id(), analyst, attempt1.lease().token());
        executions.fail(attempt1.task().id(), analyst, attempt1.lease().token(),
                "thread-r8s6-failed", "analysis failed before producing requirements");

        runs.recover(started.runId(), "operator:recover");
        runs.pause(started.runId(), "operator:one", "inspect after recovery");
        runs.resume(started.runId(), "operator:one", "inspection complete");

        var attempt2 = claims.claimNext(analyst).orElseThrow();
        executions.start(attempt2.task().id(), analyst, attempt2.lease().token());
        runs.pause(started.runId(), "operator:two", "pause active retry");
        runs.resume(started.runId(), "operator:two", "continue retry later");
        runs.cancel(started.runId(), "operator:cancel", "requirement withdrawn");

        JsonNode response = json.readTree(get("/api/r8/runs/" + started.runId() + "/timeline").body());
        assertThat(response.path("runId").asText()).isEqualTo(started.runId());
        assertThat(response.path("effectiveStatus").asText()).isEqualTo("CANCELLED");
        assertThat(response.path("truncated").asBoolean()).isFalse();

        List<String> auditTypes = new ArrayList<>();
        List<Long> auditSequences = new ArrayList<>();
        boolean sawFailedAttempt = false;
        boolean sawFailureReason = false;
        boolean sawPausedExpiration = false;
        for (JsonNode entry : response.path("entries")) {
            if (!entry.path("runEventSequence").isNull() && !entry.path("runEventSequence").isMissingNode()) {
                auditSequences.add(entry.path("runEventSequence").asLong());
                auditTypes.add(entry.path("eventType").asText());
            }
            if (entry.path("eventType").asText().equals("ATTEMPT_FAILED")) sawFailedAttempt = true;
            if (entry.path("eventType").asText().equals("RUN_FAILED")
                    && entry.path("detail").asText().contains("analysis failed")) sawFailureReason = true;
            if (entry.path("eventType").asText().equals("ATTEMPT_EXPIRED")
                    && entry.path("detail").asText().contains("OPERATOR_PAUSED")) sawPausedExpiration = true;
        }

        assertThat(auditSequences).containsExactly(1L, 2L, 3L, 4L, 5L, 6L, 7L);
        assertThat(auditTypes).containsExactly(
                "RUN_FAILED", "RUN_RECOVERED", "RUN_PAUSED", "RUN_RESUMED",
                "RUN_PAUSED", "RUN_RESUMED", "RUN_CANCELLED");
        assertThat(sawFailedAttempt).isTrue();
        assertThat(sawFailureReason).isTrue();
        assertThat(sawPausedExpiration).isTrue();

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM autonomous_run_event WHERE run_id=?", Integer.class, started.runId()))
                .isEqualTo(7);

        JsonNode limited = json.readTree(get("/api/r8/runs/" + started.runId() + "/timeline?limit=3").body());
        assertThat(limited.path("returnedEntries").asInt()).isEqualTo(3);
        assertThat(limited.path("truncated").asBoolean()).isTrue();
        assertThat(limited.path("totalEntries").asInt()).isGreaterThan(3);
    }

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + path)).GET().build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
