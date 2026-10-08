package me.asu.aidev.autonomous;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import me.asu.aidev.approval.application.ApprovalService;
import me.asu.aidev.approval.application.DecideApprovalCommand;
import me.asu.aidev.approval.domain.ApprovalDecision;
import me.asu.aidev.autonomous.application.AutonomousRequirementService;
import me.asu.aidev.autonomous.application.AutonomousRunService;
import me.asu.aidev.autonomous.application.StartAutonomousRequirementCommand;
import me.asu.aidev.autonomous.domain.AutonomousRunStatus;
import me.asu.aidev.execution.application.TaskClaimService;
import me.asu.aidev.execution.application.TaskExecutionService;
import me.asu.aidev.execution.domain.CompletionReport;
import me.asu.aidev.execution.domain.CompletionVerdict;
import me.asu.aidev.execution.domain.WorkspaceResult;
import me.asu.aidev.work.application.WorkTaskService;
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
import java.util.List;
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
class R8S4AutonomousRunAuthorityIT {
    private static final String BASE = "1111111111111111111111111111111111111111";
    private static final String RESULT = "4444444444444444444444444444444444444444";
    private static final Path DB;
    static {
        try {
            DB = Files.createTempFile("r8-s4-run-authority-", ".db");
            Files.deleteIfExists(DB);
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + DB.toAbsolutePath());
        registry.add("aidev.approval.policy.mode", () -> "REQUIRED");
        registry.add("aidev.approval.policy.channel", () -> "API");
    }

    @LocalServerPort int port;
    @Autowired AutonomousRequirementService autonomous;
    @Autowired AutonomousRunService runs;
    @Autowired WorkTaskService tasks;
    @Autowired WorkerService workers;
    @Autowired TaskClaimService claims;
    @Autowired TaskExecutionService executions;
    @Autowired ApprovalService approvals;

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void explicitTaskFailurePausesRunRecoveryCreatesNewAttemptAndApprovalCompletesExactResult() throws Exception {
        var started = autonomous.start(new StartAutonomousRequirementCommand(
                "r8s4-authority",
                "Implement one change, recover cleanly from an explicit worker failure, and require approval.",
                "repo-main", BASE, Set.of("java", "maven"), TaskPriority.HIGH));

        assertThat(runs.find(started.runId()).orElseThrow().status()).isEqualTo(AutonomousRunStatus.RUNNING);

        WorkerId analyst = register("r8s4-analyst", Set.of("requirement-analysis", "java", "maven", "repo:repo-main"));
        WorkerId architect = register("r8s4-architect", Set.of("solution-architecture", "java", "maven", "repo:repo-main"));
        WorkerId planner = register("r8s4-planner", Set.of("implementation-planning", "java", "maven", "repo:repo-main"));
        WorkerId coder = register("r8s4-coder", Set.of("coding", "java", "maven", "repo:repo-main"));
        WorkerId reviewer = register("r8s4-reviewer", Set.of("code-review", "java", "maven", "repo:repo-main"));
        WorkerId verifier = register("r8s4-verifier", Set.of("verification", "java", "maven", "repo:repo-main"));

        var requirement = claims.claimNext(analyst).orElseThrow();
        completePlanningStage(requirement.task().id(), analyst, requirement.lease().token(), "Requirement ready.");
        var architecture = claims.claimNext(architect).orElseThrow();
        completePlanningStage(architecture.task().id(), architect, architecture.lease().token(), "Architecture ready.");
        var planning = claims.claimNext(planner).orElseThrow();
        completePlanningStage(planning.task().id(), planner, planning.lease().token(), "One implementation work item.");

        var firstAttempt = claims.claimNext(coder).orElseThrow();
        assertThat(firstAttempt.task().type()).isEqualTo(TaskType.IMPLEMENTATION);
        assertThat(firstAttempt.attemptNo()).isEqualTo(1);
        executions.start(firstAttempt.task().id(), coder, firstAttempt.lease().token());
        executions.fail(firstAttempt.task().id(), coder, firstAttempt.lease().token(),
                "thread-r8s4-failed", "mvn verify failed in worker execution");

        var failed = runs.find(started.runId()).orElseThrow();
        assertThat(failed.status()).isEqualTo(AutonomousRunStatus.FAILED);
        assertThat(failed.lastFailedTaskId()).isEqualTo(firstAttempt.task().id());
        assertThat(failed.lastFailureReason()).contains("mvn verify failed");
        assertThat(failed.failureCount()).isEqualTo(1);
        assertThat(tasks.get(firstAttempt.task().id()).state()).isEqualTo(TaskState.FAILED);

        HttpResponse<String> failedQuery = get("/api/r8/runs/" + started.runId());
        assertThat(failedQuery.statusCode()).isEqualTo(200);
        JsonNode failedJson = json.readTree(failedQuery.body());
        assertThat(failedJson.path("status").asText()).isEqualTo("FAILED");
        assertThat(failedJson.path("lastFailedTaskId").asText()).isEqualTo(firstAttempt.task().id().value());

        HttpResponse<String> recovered = post("/api/r8/runs/" + started.runId() + "/recover",
                "{\"recoveredBy\":\"operator:test\"}");
        assertThat(recovered.statusCode()).isEqualTo(200);
        JsonNode recoveredJson = json.readTree(recovered.body());
        assertThat(recoveredJson.path("status").asText()).isEqualTo("RUNNING");
        assertThat(recoveredJson.path("recoveryCount").asInt()).isEqualTo(1);
        assertThat(recoveredJson.path("lastRecoveredBy").asText()).isEqualTo("operator:test");
        assertThat(tasks.get(firstAttempt.task().id()).state()).isEqualTo(TaskState.READY);

        var secondAttempt = claims.claimNext(coder).orElseThrow();
        assertThat(secondAttempt.task().id()).isEqualTo(firstAttempt.task().id());
        assertThat(secondAttempt.attemptNo()).isEqualTo(2);
        executions.start(secondAttempt.task().id(), coder, secondAttempt.lease().token());
        var implementationCompletion = executions.complete(
                secondAttempt.task().id(), coder, secondAttempt.lease().token(), "thread-r8s4-retry",
                new WorkspaceResult(RESULT, List.of("src/main/java/Recovered.java"),
                        "git:origin:" + secondAttempt.workspace().branchName()));
        assertThat(implementationCompletion.generatedTaskIds()).hasSize(2);

        var review = claims.claimNext(reviewer).orElseThrow();
        executions.start(review.task().id(), reviewer, review.lease().token());
        executions.complete(review.task().id(), reviewer, review.lease().token(), "thread-r8s4-review",
                new WorkspaceResult(RESULT, List.of()),
                new CompletionReport(CompletionVerdict.PASS, "Review passed.\nAIW_VERDICT: PASS"));

        var verification = claims.claimNext(verifier).orElseThrow();
        executions.start(verification.task().id(), verifier, verification.lease().token());
        executions.complete(verification.task().id(), verifier, verification.lease().token(), "thread-r8s4-verify",
                new WorkspaceResult(RESULT, List.of()),
                new CompletionReport(CompletionVerdict.PASS, "Verification passed.\nAIW_VERDICT: PASS"));

        var waiting = runs.find(started.runId()).orElseThrow();
        assertThat(waiting.status()).isEqualTo(AutonomousRunStatus.WAITING_APPROVAL);
        assertThat(waiting.terminalSourceTaskId()).isEqualTo(secondAttempt.task().id());
        assertThat(waiting.resultRevision()).isEqualTo(RESULT);
        assertThat(waiting.approvalRequestId()).isNotNull();

        approvals.decide(new DecideApprovalCommand(
                waiting.approvalRequestId(), ApprovalDecision.APPROVE, "human:test", "approved exact result"));

        var succeeded = runs.find(started.runId()).orElseThrow();
        assertThat(succeeded.status()).isEqualTo(AutonomousRunStatus.SUCCEEDED);
        assertThat(succeeded.resultRevision()).isEqualTo(RESULT);
        assertThat(succeeded.completedAt()).isNotNull();
        assertThat(succeeded.failureCount()).isEqualTo(1);
        assertThat(succeeded.recoveryCount()).isEqualTo(1);

        JsonNode finalJson = json.readTree(get("/api/r8/runs/" + started.runId()).body());
        assertThat(finalJson.path("status").asText()).isEqualTo("SUCCEEDED");
        assertThat(finalJson.path("resultRevision").asText()).isEqualTo(RESULT);
    }

    private void completePlanningStage(me.asu.aidev.work.domain.TaskId taskId, WorkerId workerId,
                                       me.asu.aidev.execution.domain.LeaseToken token, String summary) {
        executions.start(taskId, workerId, token);
        executions.complete(taskId, workerId, token, "thread-" + taskId.value(),
                new WorkspaceResult(BASE, List.of()), new CompletionReport(CompletionVerdict.PASS, summary));
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

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + path)).GET().build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
