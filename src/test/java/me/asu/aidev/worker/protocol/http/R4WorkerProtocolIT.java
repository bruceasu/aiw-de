package me.asu.aidev.worker.protocol.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import me.asu.aidev.work.application.CreateWorkTaskCommand;
import me.asu.aidev.work.application.WorkTaskService;
import me.asu.aidev.work.domain.TaskId;
import me.asu.aidev.work.domain.TaskPriority;
import me.asu.aidev.work.domain.TaskState;
import me.asu.aidev.work.domain.TaskType;
import me.asu.aidev.testsupport.DatabaseTestCleaner;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
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
class R4WorkerProtocolIT {
    private static final String TOKEN = "r4-test-token";
    private static final Path DB;
    static {
        try {
            DB = Files.createTempFile("r4-worker-protocol-", ".db");
            Files.deleteIfExists(DB);
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + DB.toAbsolutePath());
        registry.add("aidev.worker-protocol.token", () -> TOKEN);
    }

    @LocalServerPort int port;
    @Autowired WorkTaskService taskService;
    @Autowired JdbcTemplate jdbc;
    private final ObjectMapper json = new ObjectMapper();

    private final HttpClient http = HttpClient.newHttpClient();

    @BeforeEach
    void clearDatabase() {
        DatabaseTestCleaner.clear(jdbc);
    }

    @Test
    void externalWorkerCanClaimExecuteAndPersistCodexSession() throws Exception {
        TaskId taskId = new TaskId("R4-PROTOCOL-1");
        taskService.create(new CreateWorkTaskCommand(
                taskId,
                TaskType.IMPLEMENTATION,
                "WORK_ITEM",
                "WI-R4-1",
                Set.of("coding", "java"),
                "coding",
                "Create the requested Java implementation and run the relevant tests.",
                TaskPriority.HIGH,
                Set.of()));

        assertThat(post("/workers/register", """
                {"workerId":"r4-codex-1","runtimeType":"codex-python-sdk","capabilities":["coding","java","maven"],"maxConcurrency":1}
                """, false).statusCode()).isEqualTo(401);

        assertThat(post("/workers/register", """
                {"workerId":"r4-codex-1","runtimeType":"codex-python-sdk","capabilities":["coding","java","maven"],"maxConcurrency":1}
                """, true).statusCode()).isEqualTo(200);

        HttpResponse<String> claim = post("/tasks/claim", "{\"workerId\":\"r4-codex-1\"}", true);
        assertThat(claim.statusCode()).isEqualTo(200);
        JsonNode claimed = json.readTree(claim.body());
        assertThat(claimed.path("taskId").asText()).isEqualTo(taskId.value());
        assertThat(claimed.path("instruction").asText()).contains("Java implementation");
        assertThat(claimed.path("attemptNo").asInt()).isEqualTo(1);

        String lease = claimed.path("leaseToken").asText();
        String action = json.writeValueAsString(new WorkerProtocolV1Controller.LeaseActionRequest(
                "r4-codex-1", lease, null));
        assertThat(post("/tasks/" + taskId.value() + "/start", action, true).statusCode()).isEqualTo(200);

        String sessionAction = json.writeValueAsString(new WorkerProtocolV1Controller.LeaseActionRequest(
                "r4-codex-1", lease, "thr_r4_test_123"));
        assertThat(post("/tasks/" + taskId.value() + "/heartbeat", sessionAction, true).statusCode()).isEqualTo(200);

        HttpResponse<String> complete = post("/tasks/" + taskId.value() + "/complete", sessionAction, true);
        assertThat(complete.statusCode()).isEqualTo(200);
        assertThat(taskService.get(taskId).state()).isEqualTo(TaskState.COMPLETED);
        assertThat(jdbc.queryForObject(
                "SELECT external_session_id FROM execution_attempt WHERE task_id=?", String.class, taskId.value()))
                .isEqualTo("thr_r4_test_123");
        assertThat(jdbc.queryForObject(
                "SELECT state FROM execution_attempt WHERE task_id=?", String.class, taskId.value()))
                .isEqualTo("COMPLETED");
    }

    @Test
    void claimReturnsNoContentWhenNoCompatibleTaskExists() throws Exception {
        assertThat(post("/workers/register", """
                {"workerId":"r4-python-only","runtimeType":"test","capabilities":["python"],"maxConcurrency":1}
                """, true).statusCode()).isEqualTo(200);

        TaskId taskId = new TaskId("R4-NO-MATCH-1");
        taskService.create(new CreateWorkTaskCommand(
                taskId, TaskType.IMPLEMENTATION, "WORK_ITEM", "WI-R4-NO-MATCH",
                Set.of("java"), "coding", "Implement the Java task.", TaskPriority.NORMAL, Set.of()));

        HttpResponse<String> claim = post("/tasks/claim", "{\"workerId\":\"r4-python-only\"}", true);
        assertThat(claim.statusCode()).isEqualTo(204);
        assertThat(taskService.get(taskId).state()).isEqualTo(TaskState.READY);
    }

    private HttpResponse<String> post(String path, String body, boolean authenticated) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + "/api/worker-protocol/v1" + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body == null ? "" : body));
        if (authenticated) builder.header("Authorization", "Bearer " + TOKEN);
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
}
