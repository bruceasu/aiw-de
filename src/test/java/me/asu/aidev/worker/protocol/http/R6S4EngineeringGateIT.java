package me.asu.aidev.worker.protocol.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import me.asu.aidev.development.application.EngineeringGateService;
import me.asu.aidev.development.domain.EngineeringGateStatus;
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
class R6S4EngineeringGateIT {
    private static final String BASE = "1111111111111111111111111111111111111111";
    private static final String IMPLEMENTED = "2222222222222222222222222222222222222222";
    private static final String REPAIRED = "3333333333333333333333333333333333333333";
    private static final Path DB;
    static {
        try {
            DB = Files.createTempFile("r6-s4-engineering-gate-", ".db");
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
    @Autowired EngineeringGateService gateService;

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void repairRequiredAndPassedResultsProduceImmutableQueryableGates() throws Exception {
        TaskId implementationId = new TaskId("R6-S4-IMPL-1");
        taskService.create(new CreateWorkTaskCommand(
                implementationId, TaskType.IMPLEMENTATION, "WORK_ITEM", "WI-R6-S4-1",
                Set.of("coding", "java", "maven"), "coding", "Implement the requested change.",
                "repo-main", BASE, TaskPriority.HIGH, Set.of()));

        WorkerId coder = register("r6-s4-coder", Set.of("coding", "java", "maven", "repo:repo-main"));
        var implementation = claimService.claimNext(coder).orElseThrow();
        executionService.start(implementationId, coder, implementation.lease().token());
        var implementationCompletion = executionService.complete(
                implementationId, coder, implementation.lease().token(), "thread-r6-s4-impl",
                new WorkspaceResult(IMPLEMENTED, List.of("src/main/java/App.java"),
                        "git:origin:" + implementation.workspace().branchName()));

        TaskId reviewId = implementationCompletion.generatedTaskIds().stream()
                .filter(id -> taskService.get(id).type() == TaskType.CODE_REVIEW).findFirst().orElseThrow();
        TaskId verificationId = implementationCompletion.generatedTaskIds().stream()
                .filter(id -> taskService.get(id).type() == TaskType.VERIFICATION).findFirst().orElseThrow();

        WorkerId reviewer = register("r6-s4-reviewer", Set.of("code-review", "java", "maven", "repo:repo-main"));
        WorkerId verifier = register("r6-s4-verifier", Set.of("verification", "java", "maven", "repo:repo-main"));

        var review = claimService.claimNext(reviewer).orElseThrow();
        executionService.start(reviewId, reviewer, review.lease().token());
        executionService.complete(
                reviewId, reviewer, review.lease().token(), "thread-r6-s4-review",
                new WorkspaceResult(IMPLEMENTED, List.of()),
                new CompletionReport(CompletionVerdict.REPAIR_REQUIRED,
                        "Null handling is missing.\nAIW_VERDICT: REPAIR_REQUIRED"));

        var verification = claimService.claimNext(verifier).orElseThrow();
        executionService.start(verificationId, verifier, verification.lease().token());
        var verificationCompletion = executionService.complete(
                verificationId, verifier, verification.lease().token(), "thread-r6-s4-verify",
                new WorkspaceResult(IMPLEMENTED, List.of()),
                new CompletionReport(CompletionVerdict.PASS,
                        "mvn verify passed.\nAIW_VERDICT: PASS"));

        var firstGate = gateService.find(implementationId).orElseThrow();
        assertThat(firstGate.status()).isEqualTo(EngineeringGateStatus.REPAIR_REQUIRED);
        assertThat(firstGate.resultRevision()).isEqualTo(IMPLEMENTED);
        assertThat(firstGate.reviewTaskId()).isEqualTo(reviewId);
        assertThat(firstGate.reviewReport().verdict()).isEqualTo(CompletionVerdict.REPAIR_REQUIRED);
        assertThat(firstGate.verificationTaskId()).isEqualTo(verificationId);
        assertThat(firstGate.verificationReport().verdict()).isEqualTo(CompletionVerdict.PASS);
        assertThat(gateService.findLatestPassed("WORK_ITEM", "WI-R6-S4-1")).isEmpty();

        TaskId repairId = verificationCompletion.generatedTaskIds().getFirst();
        WorkerId repairer = register("r6-s4-repairer", Set.of("repair", "java", "maven", "repo:repo-main"));
        var repair = claimService.claimNext(repairer).orElseThrow();
        executionService.start(repairId, repairer, repair.lease().token());
        var repairCompletion = executionService.complete(
                repairId, repairer, repair.lease().token(), "thread-r6-s4-repair",
                new WorkspaceResult(REPAIRED, List.of("src/main/java/App.java"),
                        "git:origin:" + repair.workspace().branchName()));

        TaskId secondReviewId = repairCompletion.generatedTaskIds().stream()
                .filter(id -> taskService.get(id).type() == TaskType.CODE_REVIEW).findFirst().orElseThrow();
        TaskId secondVerificationId = repairCompletion.generatedTaskIds().stream()
                .filter(id -> taskService.get(id).type() == TaskType.VERIFICATION).findFirst().orElseThrow();

        var secondReview = claimService.claimNext(reviewer).orElseThrow();
        executionService.start(secondReviewId, reviewer, secondReview.lease().token());
        executionService.complete(
                secondReviewId, reviewer, secondReview.lease().token(), "thread-r6-s4-review-2",
                new WorkspaceResult(REPAIRED, List.of()),
                new CompletionReport(CompletionVerdict.PASS, "Review passed.\nAIW_VERDICT: PASS"));

        var secondVerification = claimService.claimNext(verifier).orElseThrow();
        executionService.start(secondVerificationId, verifier, secondVerification.lease().token());
        executionService.complete(
                secondVerificationId, verifier, secondVerification.lease().token(), "thread-r6-s4-verify-2",
                new WorkspaceResult(REPAIRED, List.of()),
                new CompletionReport(CompletionVerdict.PASS, "Verification passed.\nAIW_VERDICT: PASS"));

        var passedGate = gateService.find(repairId).orElseThrow();
        assertThat(passedGate.status()).isEqualTo(EngineeringGateStatus.PASSED);
        assertThat(passedGate.resultRevision()).isEqualTo(REPAIRED);
        assertThat(passedGate.changedFiles()).containsExactly("src/main/java/App.java");
        assertThat(passedGate.publicationRef()).isEqualTo("git:origin:" + repair.workspace().branchName());
        assertThat(gateService.findLatestPassed("WORK_ITEM", "WI-R6-S4-1").orElseThrow().sourceTaskId())
                .isEqualTo(repairId);

        HttpResponse<String> byTask = get("/api/r6/engineering-gates/" + repairId.value());
        assertThat(byTask.statusCode()).isEqualTo(200);
        JsonNode taskJson = json.readTree(byTask.body());
        assertThat(taskJson.path("status").asText()).isEqualTo("PASSED");
        assertThat(taskJson.path("resultRevision").asText()).isEqualTo(REPAIRED);
        assertThat(taskJson.path("review").path("verdict").asText()).isEqualTo("PASS");
        assertThat(taskJson.path("verification").path("verdict").asText()).isEqualTo("PASS");

        HttpResponse<String> latest = get("/api/r6/engineering-gates/latest-passed?subjectType=WORK_ITEM&subjectId=WI-R6-S4-1");
        assertThat(latest.statusCode()).isEqualTo(200);
        assertThat(json.readTree(latest.body()).path("sourceTaskId").asText()).isEqualTo(repairId.value());
    }

    private WorkerId register(String id, Set<String> capabilities) {
        WorkerId workerId = new WorkerId(id);
        workerService.register(new RegisterWorkerCommand(workerId, "test", capabilities, 1));
        return workerId;
    }

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + path))
                .GET()
                .build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
