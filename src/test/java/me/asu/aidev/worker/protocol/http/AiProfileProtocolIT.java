package me.asu.aidev.worker.protocol.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import me.asu.aidev.testsupport.DatabaseTestCleaner;
import me.asu.aidev.work.application.CreateWorkTaskCommand;
import me.asu.aidev.work.application.WorkTaskService;
import me.asu.aidev.work.domain.TaskId;
import me.asu.aidev.work.domain.TaskPriority;
import me.asu.aidev.work.domain.TaskState;
import me.asu.aidev.work.domain.TaskType;
import me.asu.aidev.work.port.WorkTaskRepository;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
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
class AiProfileProtocolIT {
    private static final Path DB;
    static {
        try {
            DB = Files.createTempFile("ai-profile-protocol-", ".db");
            Files.deleteIfExists(DB);
        } catch (Exception error) { throw new ExceptionInInitializerError(error); }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + DB.toAbsolutePath());
        registry.add("aidev.worker-protocol.token", () -> "test-worker-token");
        registry.add("aidev.ai.version", () -> "reviewed-v1");
        registry.add("aidev.ai.profiles.fast.provider", () -> "openai");
        registry.add("aidev.ai.profiles.fast.runtime", () -> "codex-sdk");
        registry.add("aidev.ai.profiles.fast.model", () -> "gpt-6-luna");
        registry.add("aidev.ai.profiles.fast.effort", () -> "medium");
        registry.add("aidev.ai.profiles.fast.fast", () -> "false");
        registry.add("aidev.ai.profiles.fast.level", () -> "1");
        registry.add("aidev.ai.profiles.normal.provider", () -> "openai");
        registry.add("aidev.ai.profiles.normal.runtime", () -> "codex-sdk");
        registry.add("aidev.ai.profiles.normal.model", () -> "gpt-6-sol");
        registry.add("aidev.ai.profiles.normal.effort", () -> "medium");
        registry.add("aidev.ai.profiles.normal.fast", () -> "false");
        registry.add("aidev.ai.profiles.normal.level", () -> "2");
        registry.add("aidev.ai.policies[0].task-type", () -> "IMPLEMENTATION");
        registry.add("aidev.ai.policies[0].profiles[0]", () -> "fast");
        registry.add("aidev.ai.policies[0].profiles[1]", () -> "normal");
    }

    @LocalServerPort int port;
    @Autowired WorkTaskService work;
    @Autowired WorkTaskRepository tasks;
    @Autowired JdbcTemplate jdbc;
    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();

    @BeforeEach void clearDatabase() { DatabaseTestCleaner.clear(jdbc); }

    @Test
    void workerSelectsApprovedProfileBeforeExecutionAndRetryReceivesHistory() throws Exception {
        TaskId id = new TaskId("AI-PROFILE-1");
        work.create(new CreateWorkTaskCommand(id, TaskType.IMPLEMENTATION, "TEST", "AI-1",
                Set.of("coding"), "coding", "Implement task", TaskPriority.NORMAL, Set.of()));
        assertThat(post("/workers/register", """
                {"workerId":"non-ai","runtimeType":"demo:worker","capabilities":["coding"],"maxConcurrency":1}
                """).statusCode()).isEqualTo(200);
        assertThat(post("/tasks/claim", "{\"workerId\":\"non-ai\"}").statusCode()).isEqualTo(204);
        assertThat(post("/workers/register", """
                {"workerId":"ai-coder","runtimeType":"codex-sdk:coder","capabilities":["coding"],"maxConcurrency":1}
                """).statusCode()).isEqualTo(200);

        JsonNode first = json.readTree(post("/tasks/claim", "{\"workerId\":\"ai-coder\"}").body());
        assertThat(first.path("aiGuidance").path("suggestedProfile").asText()).isEqualTo("fast");
        assertThat(first.path("aiGuidance").path("allowedProfiles")).hasSize(2);
        String lease = first.path("leaseToken").asText();
        assertThat(post("/tasks/" + id.value() + "/start", start(lease, "unknown")).statusCode()).isNotEqualTo(200);
        assertThat(work.get(id).state()).isEqualTo(TaskState.CLAIMED);
        assertThat(post("/tasks/" + id.value() + "/start", start(lease, "fast")).statusCode()).isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT ai_model FROM execution_attempt WHERE task_id=? AND attempt_no=1",
                String.class, id.value())).isEqualTo("gpt-6-luna");
        assertThat(post("/tasks/" + id.value() + "/fail", """
                {"workerId":"ai-coder","leaseToken":"%s","failureReason":"model failed","failureCategory":"MODEL_ERROR"}
                """.formatted(lease)).statusCode()).isEqualTo(200);

        assertThat(tasks.retryFailed(id, Instant.now())).isTrue();
        JsonNode second = json.readTree(post("/tasks/claim", "{\"workerId\":\"ai-coder\"}").body());
        assertThat(second.path("aiGuidance").path("previous").path("profileId").asText()).isEqualTo("fast");
        assertThat(second.path("aiGuidance").path("previous").path("failureCategory").asText()).isEqualTo("MODEL_ERROR");
        assertThat(second.path("aiGuidance").path("suggestedProfile").asText()).isEqualTo("normal");
        assertThat(post("/tasks/" + id.value() + "/start", start(second.path("leaseToken").asText(), "normal")).statusCode()).isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT ai_model FROM execution_attempt WHERE task_id=? AND attempt_no=2",
                String.class, id.value())).isEqualTo("gpt-6-sol");
    }

    private String start(String lease, String profile) {
        return """
                {"workerId":"ai-coder","leaseToken":"%s","profileId":"%s", "profileVersion":"reviewed-v1", "selectionReason":"Worker selected approved profile"}
                """.formatted(lease, profile);
    }

    private HttpResponse<String> post(String endpoint, String body) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/r1/worker-protocol/v1" + endpoint))
                .header("Authorization", "Bearer test-worker-token")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }
}
