package me.asu.aidev.approval.protocol.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import me.asu.aidev.execution.application.TaskClaimService;
import me.asu.aidev.execution.application.TaskExecutionService;
import me.asu.aidev.execution.domain.CompletionReport;
import me.asu.aidev.execution.domain.CompletionVerdict;
import me.asu.aidev.execution.domain.WorkspaceResult;
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
class R7S1HumanApprovalIT {
    private static final String BASE = "1111111111111111111111111111111111111111";
    private static final String RESULT = "7777777777777777777777777777777777777777";
    private static final Path DB;
    static {
        try {
            DB = Files.createTempFile("r7-s1-human-approval-", ".db");
            Files.deleteIfExists(DB);
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + DB.toAbsolutePath());
    }

    @LocalServerPort int port;
    @Autowired WorkTaskService taskService;
    @Autowired WorkerService workerService;
    @Autowired TaskClaimService claimService;
    @Autowired TaskExecutionService executionService;

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void passedEngineeringGateCanBeApprovedExactlyOnceAndDecisionIsImmutable() throws Exception {
        TaskId implementationId = new TaskId("R7-S1-IMPL-1");
        taskService.create(new CreateWorkTaskCommand(
                implementationId, TaskType.IMPLEMENTATION, "WORK_ITEM", "WI-R7-S1-1",
                Set.of("coding", "java", "maven"), "coding", "Implement approval test change.",
                "repo-main", BASE, TaskPriority.HIGH, Set.of()));

        WorkerId coder = register("r7-coder", Set.of("coding", "java", "maven", "repo:repo-main"));
        WorkerId reviewer = register("r7-reviewer", Set.of("code-review", "java", "maven", "repo:repo-main"));
        WorkerId verifier = register("r7-verifier", Set.of("verification", "java", "maven", "repo:repo-main"));

        var implementation = claimService.claimNext(coder).orElseThrow();
        executionService.start(implementationId, coder, implementation.lease().token());
        var completed = executionService.complete(
                implementationId, coder, implementation.lease().token(), "thread-r7-impl",
                new WorkspaceResult(RESULT, List.of("src/main/java/App.java"),
                        "git:origin:" + implementation.workspace().branchName()));

        TaskId reviewId = completed.generatedTaskIds().stream()
                .filter(id -> taskService.get(id).type() == TaskType.CODE_REVIEW).findFirst().orElseThrow();
        TaskId verificationId = completed.generatedTaskIds().stream()
                .filter(id -> taskService.get(id).type() == TaskType.VERIFICATION).findFirst().orElseThrow();

        var review = claimService.claimNext(reviewer).orElseThrow();
        executionService.start(reviewId, reviewer, review.lease().token());
        executionService.complete(reviewId, reviewer, review.lease().token(), "thread-r7-review",
                new WorkspaceResult(RESULT, List.of()),
                new CompletionReport(CompletionVerdict.PASS, "Review passed.\nAIW_VERDICT: PASS"));

        var verification = claimService.claimNext(verifier).orElseThrow();
        executionService.start(verificationId, verifier, verification.lease().token());
        executionService.complete(verificationId, verifier, verification.lease().token(), "thread-r7-verify",
                new WorkspaceResult(RESULT, List.of()),
                new CompletionReport(CompletionVerdict.PASS, "Verification passed.\nAIW_VERDICT: PASS"));

        String requestBody = """
                {"sourceTaskId":"R7-S1-IMPL-1","requestedBy":"control-plane","channel":"TELEGRAM","channelRef":"chat:123"}
                """;
        HttpResponse<String> requested = post("/api/r7/approvals", requestBody);
        assertThat(requested.statusCode()).isEqualTo(200);
        JsonNode pending = json.readTree(requested.body());
        String approvalId = pending.path("approvalRequestId").asText();
        assertThat(approvalId).startsWith("APR-");
        assertThat(pending.path("status").asText()).isEqualTo("PENDING");
        assertThat(pending.path("sourceTaskId").asText()).isEqualTo(implementationId.value());
        assertThat(pending.path("resultRevision").asText()).isEqualTo(RESULT);
        assertThat(pending.path("channel").asText()).isEqualTo("TELEGRAM");
        assertThat(pending.path("channelRef").asText()).isEqualTo("chat:123");

        HttpResponse<String> duplicate = post("/api/r7/approvals", requestBody);
        assertThat(duplicate.statusCode()).isEqualTo(200);
        assertThat(json.readTree(duplicate.body()).path("approvalRequestId").asText()).isEqualTo(approvalId);

        String decision = """
                {"decision":"APPROVE","decidedBy":"human:s","note":"Approved after review"}
                """;
        HttpResponse<String> approved = post("/api/r7/approvals/" + approvalId + "/decision", decision);
        assertThat(approved.statusCode()).isEqualTo(200);
        JsonNode approvedJson = json.readTree(approved.body());
        assertThat(approvedJson.path("status").asText()).isEqualTo("APPROVED");
        assertThat(approvedJson.path("decidedBy").asText()).isEqualTo("human:s");

        HttpResponse<String> replay = post("/api/r7/approvals/" + approvalId + "/decision", decision);
        assertThat(replay.statusCode()).isEqualTo(200);
        assertThat(json.readTree(replay.body()).path("status").asText()).isEqualTo("APPROVED");

        HttpResponse<String> conflicting = post("/api/r7/approvals/" + approvalId + "/decision",
                "{\"decision\":\"REJECT\",\"decidedBy\":\"human:s\",\"note\":\"changed mind\"}");
        assertThat(conflicting.statusCode()).isEqualTo(409);

        HttpResponse<String> bySource = get("/api/r7/approvals/by-source/" + implementationId.value());
        assertThat(bySource.statusCode()).isEqualTo(200);
        assertThat(json.readTree(bySource.body()).path("approvalRequestId").asText()).isEqualTo(approvalId);
    }

    private WorkerId register(String id, Set<String> capabilities) {
        WorkerId workerId = new WorkerId(id);
        workerService.register(new RegisterWorkerCommand(workerId, "test", capabilities, 1));
        return workerId;
    }

    private HttpResponse<String> post(String path, String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + path))
                .GET().build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
