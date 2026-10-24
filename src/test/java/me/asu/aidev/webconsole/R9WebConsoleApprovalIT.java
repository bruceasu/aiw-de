package me.asu.aidev.webconsole;

import me.asu.aidev.approval.application.ApprovalService;
import me.asu.aidev.approval.application.RequestApprovalCommand;
import me.asu.aidev.approval.domain.ApprovalChannel;
import me.asu.aidev.approval.domain.ApprovalStatus;
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
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("sqlite")
class R9WebConsoleApprovalIT {
    private static final String BASE = "4444444444444444444444444444444444444444";
    private static final String RESULT = "5555555555555555555555555555555555555555";
    private static final Path DB;
    static {
        try {
            DB = Files.createTempFile("r9-web-console-approval-", ".db");
            Files.deleteIfExists(DB);
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + DB.toAbsolutePath());
        registry.add("aidev.approval.policy.mode", () -> "MANUAL");
        registry.add("aidev.web-console.auth.enabled", () -> "false");
        registry.add("aidev.web-console.auth.username", () -> "console-admin");
        registry.add("aidev.web-console.auth.password", () -> "test-console-secret");
        registry.add("aidev.web-console.auth.role", () -> "ADMIN");
        registry.add("spring.thymeleaf.cache", () -> "false");
    }

    @LocalServerPort int port;
    @Autowired WorkTaskService taskService;
    @Autowired WorkerService workerService;
    @Autowired TaskClaimService claimService;
    @Autowired TaskExecutionService executionService;
    @Autowired ApprovalService approvals;

    private final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
    private final HttpClient http = HttpClient.newBuilder().cookieHandler(cookies).connectTimeout(Duration.ofSeconds(3))
            .followRedirects(HttpClient.Redirect.NORMAL).build();

    @BeforeEach
    void signIn() throws Exception {
        var response = post("/ui/login", form(Map.of("username", "console-admin", "password", "test-console-secret")));
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.uri().getPath()).isEqualTo("/ui/plans");
    }

    @Test
    void pendingApprovalCanBeDecidedFromWebConsoleUsingExistingApprovalAuthority() throws Exception {
        TaskId implementationId = new TaskId("R9-WEB-APPROVAL-IMPL");
        taskService.create(new CreateWorkTaskCommand(
                implementationId, TaskType.IMPLEMENTATION, "WORK_ITEM", "WI-R9-WEB-APPROVAL",
                Set.of("coding", "java", "maven"), "coding", "Implement Web approval integration test.",
                "repo-main", BASE, TaskPriority.HIGH, Set.of()));

        WorkerId coder = register("r9-web-coder", Set.of("coding", "java", "maven", "repo:repo-main"));
        WorkerId reviewer = register("r9-web-reviewer", Set.of("code-review", "java", "maven", "repo:repo-main"));
        WorkerId verifier = register("r9-web-verifier", Set.of("verification", "java", "maven", "repo:repo-main"));

        var implementation = claimService.claimNext(coder).orElseThrow();
        executionService.start(implementationId, coder, implementation.lease().token());
        var completed = executionService.complete(
                implementationId, coder, implementation.lease().token(), "thread-r9-web-impl",
                new WorkspaceResult(RESULT, List.of("src/main/java/App.java"),
                        "git:origin:" + implementation.workspace().branchName()));

        TaskId reviewId = completed.generatedTaskIds().stream()
                .filter(id -> taskService.get(id).type() == TaskType.CODE_REVIEW).findFirst().orElseThrow();
        TaskId verificationId = completed.generatedTaskIds().stream()
                .filter(id -> taskService.get(id).type() == TaskType.VERIFICATION).findFirst().orElseThrow();

        var review = claimService.claimNext(reviewer).orElseThrow();
        executionService.start(reviewId, reviewer, review.lease().token());
        executionService.complete(reviewId, reviewer, review.lease().token(), "thread-r9-web-review",
                new WorkspaceResult(RESULT, List.of()),
                new CompletionReport(CompletionVerdict.PASS, "Review passed.\nAIW_VERDICT: PASS"));

        var verification = claimService.claimNext(verifier).orElseThrow();
        executionService.start(verificationId, verifier, verification.lease().token());
        executionService.complete(verificationId, verifier, verification.lease().token(), "thread-r9-web-verify",
                new WorkspaceResult(RESULT, List.of()),
                new CompletionReport(CompletionVerdict.PASS, "Verification passed.\nAIW_VERDICT: PASS"));

        var pending = approvals.request(new RequestApprovalCommand(
                implementationId, "test:web-console", ApprovalChannel.WEB, "web-console"));
        assertThat(pending.status()).isEqualTo(ApprovalStatus.PENDING);

        HttpResponse<String> page = get("/ui/approvals");
        assertThat(page.body()).contains(pending.id().value(), "Approve", "Reject");
        String csrf = csrf(page.body());

        HttpResponse<String> approved = post("/ui/approvals/" + pending.id().value() + "/approve", form(Map.of(
                "_csrf", csrf,
                "note", "Approved from Web Console")));
        assertThat(approved.statusCode()).isEqualTo(200);
        assertThat(approved.body()).contains("APPROVED");
        var authoritative = approvals.find(pending.id()).orElseThrow();
        assertThat(authoritative.status()).isEqualTo(ApprovalStatus.APPROVED);
        assertThat(authoritative.decidedBy()).isEqualTo("web:console-admin");
        assertThat(authoritative.decisionNote()).isEqualTo("Approved from Web Console");
    }

    private WorkerId register(String id, Set<String> capabilities) {
        WorkerId workerId = new WorkerId(id);
        workerService.register(new RegisterWorkerCommand(workerId, "test", capabilities, 1));
        return workerId;
    }

    private HttpResponse<String> get(String path) throws Exception {
        return http.send(HttpRequest.newBuilder(uri(path)).timeout(Duration.ofSeconds(5)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, String body) throws Exception {
        return http.send(HttpRequest.newBuilder(uri(path)).timeout(Duration.ofSeconds(5))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) { return URI.create("http://127.0.0.1:" + port + path); }

    private static String csrf(String html) {
        var matcher = Pattern.compile("name=\\\"_csrf\\\"[^>]*value=\\\"([^\\\"]+)\\\"").matcher(html);
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }

    private static String form(Map<String, String> values) {
        return values.entrySet().stream().map(e -> encode(e.getKey()) + "=" + encode(e.getValue()))
                .reduce((a, b) -> a + "&" + b).orElse("");
    }

    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
}
