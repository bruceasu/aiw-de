package me.asu.aidev.worker.protocol.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import me.asu.aidev.work.application.CreateWorkTaskCommand;
import me.asu.aidev.work.application.WorkTaskService;
import me.asu.aidev.work.domain.TaskId;
import me.asu.aidev.work.domain.TaskPriority;
import me.asu.aidev.work.domain.TaskState;
import me.asu.aidev.work.domain.TaskType;
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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("sqlite")
class R5WorkspaceProtocolIT {
    private static final String TOKEN = "r5-test-token";
    private static final String BASE = "1111111111111111111111111111111111111111";
    private static final String RESULT = "2222222222222222222222222222222222222222";
    private static final Path DB;
    static {
        try {
            DB = Files.createTempFile("r5-workspace-protocol-", ".db");
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

    @Test
    void repositoryTaskIsClaimedOnlyByRepositoryWorkerAndPersistsGitResult() throws Exception {
        TaskId taskId = new TaskId("R5-WORKSPACE-1");
        taskService.create(new CreateWorkTaskCommand(
                taskId, TaskType.IMPLEMENTATION, "WORK_ITEM", "WI-R5-1",
                Set.of("coding", "java"), "coding",
                "Modify the implementation in an isolated worktree.",
                "repo-main", BASE, TaskPriority.HIGH, Set.of()));

        post("/workers/register", """
                {"workerId":"wrong-repo","runtimeType":"test","capabilities":["coding","java"],"maxConcurrency":1}
                """);
        assertThat(post("/tasks/claim", "{\"workerId\":\"wrong-repo\"}").statusCode()).isEqualTo(204);

        post("/workers/register", """
                {"workerId":"r5-java","runtimeType":"codex-java-cli","capabilities":["coding","java","repo:repo-main"],"maxConcurrency":1}
                """);
        HttpResponse<String> claim = post("/tasks/claim", "{\"workerId\":\"r5-java\"}");
        assertThat(claim.statusCode()).isEqualTo(200);
        JsonNode claimed = json.readTree(claim.body());
        assertThat(claimed.path("requiredCapabilities").toString()).contains("repo:repo-main");
        assertThat(claimed.path("workspace").path("repositoryRef").asText()).isEqualTo("repo-main");
        assertThat(claimed.path("workspace").path("baseRevision").asText()).isEqualTo(BASE);
        assertThat(claimed.path("workspace").path("workspaceKey").asText()).contains("R5-WORKSPACE-1").endsWith("-a1");
        assertThat(claimed.path("workspace").path("branchName").asText()).startsWith("aidev/").endsWith("/a1");

        String lease = claimed.path("leaseToken").asText();
        String start = json.writeValueAsString(new WorkerProtocolV1Controller.LeaseActionRequest("r5-java", lease, null));
        assertThat(post("/tasks/" + taskId.value() + "/start", start).statusCode()).isEqualTo(200);

        String missingResult = json.writeValueAsString(new WorkerProtocolV1Controller.CompleteTaskRequest(
                "r5-java", lease, "thread-r5", null));
        assertThat(post("/tasks/" + taskId.value() + "/complete", missingResult).statusCode()).isEqualTo(409);
        assertThat(taskService.get(taskId).state()).isEqualTo(TaskState.RUNNING);

        String complete = json.writeValueAsString(new WorkerProtocolV1Controller.CompleteTaskRequest(
                "r5-java", lease, "thread-r5",
                new WorkerProtocolV1Controller.WorkspaceResultRequest(RESULT, List.of("src/main/java/App.java", "pom.xml"))));
        assertThat(post("/tasks/" + taskId.value() + "/complete", complete).statusCode()).isEqualTo(200);
        assertThat(taskService.get(taskId).state()).isEqualTo(TaskState.COMPLETED);

        var row = jdbc.queryForMap("""
                SELECT workspace_key,branch_name,base_revision,result_revision,changed_files
                  FROM execution_attempt WHERE task_id=?
                """, taskId.value());
        assertThat(row.get("workspace_key").toString()).isEqualTo(claimed.path("workspace").path("workspaceKey").asText());
        assertThat(row.get("branch_name").toString()).isEqualTo(claimed.path("workspace").path("branchName").asText());
        assertThat(row.get("base_revision").toString()).isEqualTo(BASE);
        assertThat(row.get("result_revision").toString()).isEqualTo(RESULT);
        assertThat(row.get("changed_files").toString()).isEqualTo("pom.xml\nsrc/main/java/App.java");
    }

    private HttpResponse<String> post(String path, String body) throws Exception {
        return http.send(HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + "/api/r1/worker-protocol/v1" + path))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + TOKEN)
                .POST(HttpRequest.BodyPublishers.ofString(body == null ? "" : body))
                .build(), HttpResponse.BodyHandlers.ofString());
    }
}
