package me.asu.aidev.autonomous;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import me.asu.aidev.autonomous.application.AutonomousRequirementService;
import me.asu.aidev.autonomous.application.AutonomousRunService;
import me.asu.aidev.autonomous.application.StartAutonomousRequirementCommand;
import me.asu.aidev.autonomous.domain.AutonomousRunControlState;
import me.asu.aidev.autonomous.domain.AutonomousRunStatus;
import me.asu.aidev.execution.application.TaskClaimService;
import me.asu.aidev.execution.application.TaskExecutionService;
import me.asu.aidev.execution.domain.CompletionReport;
import me.asu.aidev.execution.domain.CompletionVerdict;
import me.asu.aidev.execution.domain.WorkspaceResult;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("sqlite")
class R8S5OperatorControlIT {
    private static final String BASE = "1111111111111111111111111111111111111111";
    private static final Path DB;
    static {
        try {
            DB = Files.createTempFile("r8-s5-operator-control-", ".db");
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
    @Autowired WorkTaskService tasks;
    @Autowired WorkerService workers;
    @Autowired TaskClaimService claims;
    @Autowired TaskExecutionService executions;
    @Autowired JdbcTemplate jdbc;

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void pauseRevokesActiveLeaseResumeCreatesNewAttemptAndCancelIsTerminal() throws Exception {
        var started = autonomous.start(new StartAutonomousRequirementCommand(
                "r8s5-control",
                "Demonstrate operator pause, resume, and cancellation authority.",
                "repo-main", BASE, Set.of("java", "maven"), TaskPriority.HIGH));

        WorkerId analyst = register("r8s5-analyst", Set.of("requirement-analysis", "java", "maven", "repo:repo-main"));
        WorkerId architect = register("r8s5-architect", Set.of("solution-architecture", "java", "maven", "repo:repo-main"));

        var firstAttempt = claims.claimNext(analyst).orElseThrow();
        executions.start(firstAttempt.task().id(), analyst, firstAttempt.lease().token());

        HttpResponse<String> pausedResponse = post("/api/r1/runs/" + started.runId() + "/pause",
                "{\"controlledBy\":\"operator:test\",\"reason\":\"inspect repository state\"}");
        assertThat(pausedResponse.statusCode()).isEqualTo(200);
        JsonNode pausedJson = json.readTree(pausedResponse.body());
        assertThat(pausedJson.path("status").asText()).isEqualTo("RUNNING");
        assertThat(pausedJson.path("controlState").asText()).isEqualTo("PAUSED");
        assertThat(pausedJson.path("effectiveStatus").asText()).isEqualTo("PAUSED");
        assertThat(pausedJson.path("pauseCount").asInt()).isEqualTo(1);
        assertThat(pausedJson.path("terminal").asBoolean()).isFalse();
        assertThat(tasks.get(firstAttempt.task().id()).state()).isEqualTo(TaskState.READY);
        assertThat(claims.claimNext(analyst)).isEmpty();

        assertThatThrownBy(() -> executions.heartbeat(firstAttempt.task().id(), analyst, firstAttempt.lease().token()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("does not allow worker execution");
        assertThat(jdbc.queryForObject(
                "SELECT state FROM execution_attempt WHERE lease_token=?", String.class, firstAttempt.lease().token().value()))
                .isEqualTo("EXPIRED");
        assertThat(jdbc.queryForObject(
                "SELECT termination_reason FROM execution_attempt WHERE lease_token=?", String.class, firstAttempt.lease().token().value()))
                .isEqualTo("OPERATOR_PAUSED");

        HttpResponse<String> resumedResponse = post("/api/r1/runs/" + started.runId() + "/resume",
                "{\"controlledBy\":\"operator:test\",\"reason\":\"inspection complete\"}");
        assertThat(resumedResponse.statusCode()).isEqualTo(200);
        JsonNode resumedJson = json.readTree(resumedResponse.body());
        assertThat(resumedJson.path("controlState").asText()).isEqualTo("ACTIVE");
        assertThat(resumedJson.path("resumeCount").asInt()).isEqualTo(1);

        var secondAttempt = claims.claimNext(analyst).orElseThrow();
        assertThat(secondAttempt.task().id()).isEqualTo(firstAttempt.task().id());
        assertThat(secondAttempt.attemptNo()).isEqualTo(2);
        executions.start(secondAttempt.task().id(), analyst, secondAttempt.lease().token());
        executions.complete(secondAttempt.task().id(), analyst, secondAttempt.lease().token(), "thread-r8s5-resumed",
                new WorkspaceResult(BASE, List.of()),
                new CompletionReport(CompletionVerdict.PASS, "Requirement analysis after resume."));

        var architecture = claims.claimNext(architect).orElseThrow();
        // Return the lease to an active state so cancellation proves active ownership revocation too.
        executions.start(architecture.task().id(), architect, architecture.lease().token());

        HttpResponse<String> cancelledResponse = post("/api/r1/runs/" + started.runId() + "/cancel",
                "{\"controlledBy\":\"operator:test\",\"reason\":\"requirement withdrawn\"}");
        assertThat(cancelledResponse.statusCode()).isEqualTo(200);
        JsonNode cancelledJson = json.readTree(cancelledResponse.body());
        assertThat(cancelledJson.path("controlState").asText()).isEqualTo("CANCELLED");
        assertThat(cancelledJson.path("effectiveStatus").asText()).isEqualTo("CANCELLED");
        assertThat(cancelledJson.path("terminal").asBoolean()).isTrue();
        assertThat(cancelledJson.path("lastControlAction").asText()).isEqualTo("CANCEL");
        assertThat(cancelledJson.path("lastControlReason").asText()).isEqualTo("requirement withdrawn");
        assertThat(cancelledJson.path("cancelledAt").isTextual()).isTrue();

        assertThat(tasks.get(firstAttempt.task().id()).state()).isEqualTo(TaskState.COMPLETED);
        assertThat(tasks.get(architecture.task().id()).state()).isEqualTo(TaskState.CANCELLED);
        assertThat(jdbc.queryForObject(
                "SELECT termination_reason FROM execution_attempt WHERE lease_token=?", String.class, architecture.lease().token().value()))
                .isEqualTo("OPERATOR_CANCELLED");
        assertThat(claims.claimNext(architect)).isEmpty();

        var cancelled = runs.find(started.runId()).orElseThrow();
        assertThat(cancelled.status()).isEqualTo(AutonomousRunStatus.RUNNING);
        assertThat(cancelled.controlState()).isEqualTo(AutonomousRunControlState.CANCELLED);
        assertThat(cancelled.terminal()).isTrue();

        HttpResponse<String> resumeCancelled = post("/api/r1/runs/" + started.runId() + "/resume",
                "{\"controlledBy\":\"operator:test\",\"reason\":\"should not resume\"}");
        assertThat(resumeCancelled.statusCode()).isEqualTo(409);
    }

    private WorkerId register(String id, Set<String> capabilities) {
        WorkerId workerId = new WorkerId(id);
        workers.register(new RegisterWorkerCommand(workerId, "test", capabilities, 1));
        return workerId;
    }

    private HttpResponse<String> post(String path, String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
