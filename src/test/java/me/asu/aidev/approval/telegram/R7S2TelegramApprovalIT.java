package me.asu.aidev.approval.telegram;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import me.asu.aidev.approval.application.ApprovalService;
import me.asu.aidev.approval.domain.ApprovalRequestId;
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
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
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
class R7S2TelegramApprovalIT {
    private static final String BASE = "1111111111111111111111111111111111111111";
    private static final String RESULT = "7777777777777777777777777777777777777777";
    private static final Path DB;
    private static HttpServer telegram;
    private static int telegramPort;
    private static final AtomicReference<String> NEXT_UPDATE = new AtomicReference<>();
    private static final AtomicReference<Map<String, String>> LAST_MESSAGE = new AtomicReference<>();
    private static volatile CountDownLatch messageSent = new CountDownLatch(1);
    private static volatile CountDownLatch callbackAnswered = new CountDownLatch(1);

    static {
        try {
            DB = Files.createTempFile("r7-s2-telegram-", ".db");
            Files.deleteIfExists(DB);
            telegram = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            telegram.createContext("/", R7S2TelegramApprovalIT::handleTelegram);
            telegram.start();
            telegramPort = telegram.getAddress().getPort();
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @AfterAll
    static void stopTelegram() {
        if (telegram != null) telegram.stop(0);
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + DB.toAbsolutePath());
        registry.add("aidev.approval.telegram.enabled", () -> "true");
        registry.add("aidev.approval.telegram.token", () -> "TEST-TOKEN");
        registry.add("aidev.approval.telegram.api-base", () -> "http://127.0.0.1:" + telegramPort);
        registry.add("aidev.approval.telegram.default-chat-id", () -> "123");
        registry.add("aidev.approval.telegram.allowed-user-ids", () -> "42");
        registry.add("aidev.approval.telegram.poll-timeout-seconds", () -> "1");
        registry.add("aidev.approval.telegram.backoff-ms", () -> "50");
    }

    @LocalServerPort int port;
    @Autowired WorkTaskService taskService;
    @Autowired WorkerService workerService;
    @Autowired TaskClaimService claimService;
    @Autowired TaskExecutionService executionService;
    @Autowired ApprovalService approvalService;

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void telegramInlineButtonIsAuthorizedAndBecomesImmutableApprovalAuthority() throws Exception {
        TaskId implementationId = preparePassedGate();

        HttpResponse<String> requested = post("/api/r1/approvals", """
                {"sourceTaskId":"R7-S2-IMPL-1","requestedBy":"control-plane","channel":"TELEGRAM","channelRef":"chat:123"}
                """);
        assertThat(requested.statusCode()).isEqualTo(200);
        String approvalId = json.readTree(requested.body()).path("approvalRequestId").asText();
        assertThat(messageSent.await(3, TimeUnit.SECONDS)).isTrue();
        Map<String, String> notification = LAST_MESSAGE.get();
        assertThat(notification.get("chat_id")).isEqualTo("123");
        assertThat(notification.get("text")).contains(approvalId).contains(implementationId.value()).contains(RESULT);
        assertThat(notification.get("reply_markup")).contains("aiw:a:" + approvalId).contains("aiw:r:" + approvalId);

        // A user outside the allow-list can press the button, but cannot create authority.
        callbackAnswered = new CountDownLatch(1);
        NEXT_UPDATE.set(callback(100, "cb-unauthorized", 99, 123, 7, "aiw:a:" + approvalId));
        assertThat(callbackAnswered.await(3, TimeUnit.SECONDS)).isTrue();
        assertThat(approvalService.find(new ApprovalRequestId(approvalId)).orElseThrow().status())
                .isEqualTo(ApprovalStatus.PENDING);

        // The authorized human expresses the decision; ApprovalService commits the authority.
        callbackAnswered = new CountDownLatch(1);
        NEXT_UPDATE.set(callback(101, "cb-approved", 42, 123, 7, "aiw:a:" + approvalId));
        await(Duration.ofSeconds(5), () -> approvalService.find(new ApprovalRequestId(approvalId))
                .map(a -> a.status() == ApprovalStatus.APPROVED).orElse(false));
        assertThat(callbackAnswered.await(1, TimeUnit.SECONDS)).isTrue();

        var approved = approvalService.find(new ApprovalRequestId(approvalId)).orElseThrow();
        assertThat(approved.status()).isEqualTo(ApprovalStatus.APPROVED);
        assertThat(approved.decidedBy()).isEqualTo("telegram:user:42");
        assertThat(approved.decisionNote()).isEqualTo("Approved from Telegram inline button");
        assertThat(approved.resultRevision()).isEqualTo(RESULT);
    }

    private TaskId preparePassedGate() {
        TaskId implementationId = new TaskId("R7-S2-IMPL-1");
        taskService.create(new CreateWorkTaskCommand(
                implementationId, TaskType.IMPLEMENTATION, "WORK_ITEM", "WI-R7-S2-1",
                Set.of("coding", "java", "maven"), "coding", "Implement Telegram approval test change.",
                "repo-main", BASE, TaskPriority.HIGH, Set.of()));

        WorkerId coder = register("r7-s2-coder", Set.of("coding", "java", "maven", "repo:repo-main"));
        WorkerId reviewer = register("r7-s2-reviewer", Set.of("code-review", "java", "maven", "repo:repo-main"));
        WorkerId verifier = register("r7-s2-verifier", Set.of("verification", "java", "maven", "repo:repo-main"));

        var implementation = claimService.claimNext(coder).orElseThrow();
        executionService.start(implementationId, coder, implementation.lease().token());
        var completed = executionService.complete(
                implementationId, coder, implementation.lease().token(), "thread-r7-s2-impl",
                new WorkspaceResult(RESULT, List.of("src/main/java/App.java"),
                        "git:origin:" + implementation.workspace().branchName()));

        TaskId reviewId = completed.generatedTaskIds().stream()
                .filter(id -> taskService.get(id).type() == TaskType.CODE_REVIEW).findFirst().orElseThrow();
        TaskId verificationId = completed.generatedTaskIds().stream()
                .filter(id -> taskService.get(id).type() == TaskType.VERIFICATION).findFirst().orElseThrow();

        var review = claimService.claimNext(reviewer).orElseThrow();
        executionService.start(reviewId, reviewer, review.lease().token());
        executionService.complete(reviewId, reviewer, review.lease().token(), "thread-r7-s2-review",
                new WorkspaceResult(RESULT, List.of()),
                new CompletionReport(CompletionVerdict.PASS, "Review passed.\nAIW_VERDICT: PASS"));

        var verification = claimService.claimNext(verifier).orElseThrow();
        executionService.start(verificationId, verifier, verification.lease().token());
        executionService.complete(verificationId, verifier, verification.lease().token(), "thread-r7-s2-verify",
                new WorkspaceResult(RESULT, List.of()),
                new CompletionReport(CompletionVerdict.PASS, "Verification passed.\nAIW_VERDICT: PASS"));
        return implementationId;
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

    private static void handleTelegram(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String response;
        if (path.endsWith("/sendMessage")) {
            LAST_MESSAGE.set(parseForm(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
            messageSent.countDown();
            response = "{\"ok\":true,\"result\":{\"message_id\":7}}";
        } else if (path.endsWith("/getUpdates")) {
            String update = NEXT_UPDATE.getAndSet(null);
            if (update == null) {
                try { Thread.sleep(25); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                response = "{\"ok\":true,\"result\":[]}";
            } else {
                response = "{\"ok\":true,\"result\":[" + update + "]}";
            }
        } else if (path.endsWith("/answerCallbackQuery")) {
            callbackAnswered.countDown();
            response = "{\"ok\":true,\"result\":true}";
        } else if (path.endsWith("/editMessageReplyMarkup")) {
            response = "{\"ok\":true,\"result\":true}";
        } else {
            response = "{\"ok\":false}";
        }
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private static String callback(long updateId, String callbackId, long userId, long chatId, long messageId, String data) {
        return "{\"update_id\":" + updateId + ",\"callback_query\":{"
                + "\"id\":\"" + callbackId + "\","
                + "\"from\":{\"id\":" + userId + "},"
                + "\"message\":{\"message_id\":" + messageId + ",\"chat\":{\"id\":" + chatId + "}},"
                + "\"data\":\"" + data + "\"}}";
    }

    private static Map<String, String> parseForm(String body) {
        Map<String, String> out = new HashMap<>();
        for (String pair : body.split("&")) {
            int eq = pair.indexOf('=');
            String key = eq < 0 ? pair : pair.substring(0, eq);
            String value = eq < 0 ? "" : pair.substring(eq + 1);
            out.put(URLDecoder.decode(key, StandardCharsets.UTF_8),
                    URLDecoder.decode(value, StandardCharsets.UTF_8));
        }
        return out;
    }

    private static void await(Duration timeout, java.util.function.BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) return;
            Thread.sleep(25);
        }
        throw new AssertionError("condition not satisfied within " + timeout);
    }
}
