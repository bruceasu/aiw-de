package me.asu.aidev.webconsole;

import me.asu.aidev.autonomous.application.AutonomousRequirementService;
import me.asu.aidev.autonomous.application.StartAutonomousRequirementCommand;
import me.asu.aidev.work.domain.TaskPriority;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
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
class R9S1WebConsoleIT {
    private static final String BASE = "9999999999999999999999999999999999999999";
    private static final Path DB;
    static {
        try {
            DB = Files.createTempFile("r9-s1-web-console-", ".db");
            Files.deleteIfExists(DB);
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + DB.toAbsolutePath());
        registry.add("aidev.approval.policy.mode", () -> "MANUAL");
        registry.add("spring.thymeleaf.cache", () -> "false");
    }

    @LocalServerPort int port;
    @Autowired AutonomousRequirementService autonomous;

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).followRedirects(HttpClient.Redirect.NORMAL).build();

    @Test
    void consoleRendersDashboardRunsRunDetailWorkersApprovalsAndSystem() throws Exception {
        var run = autonomous.start(new StartAutonomousRequirementCommand(
                "r9s1-console",
                "Render the first read-only Web Console from existing Control Plane projections.",
                "repo-main", BASE, Set.of("java", "maven"), TaskPriority.HIGH));

        assertHtml("/ui", "AIW:DE", "Dashboard");
        assertHtml("/ui/runs", "Autonomous Runs", run.runId());
        assertHtml("/ui/runs/" + run.runId(), "Autonomous run authority", "REQUIREMENT_ANALYSIS");
        assertHtml("/ui/workers", "Worker Fleet", "Registered runtimes");
        assertHtml("/ui/approvals", "Approvals", "Human approval inbox");
        assertHtml("/ui/system", "System", "Control Plane, scheduling authority");

        HttpResponse<String> root = get("/");
        assertThat(root.statusCode()).isEqualTo(200);
        assertThat(root.uri().getPath()).isEqualTo("/ui");
    }

    private void assertHtml(String path, String... fragments) throws Exception {
        HttpResponse<String> response = get(path);
        assertThat(response.statusCode()).as(path).isEqualTo(200);
        assertThat(response.headers().firstValue("content-type").orElse(""))
                .contains("text/html");
        for (String fragment : fragments) {
            assertThat(response.body()).as(path).contains(fragment);
        }
    }

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(5))
                .GET().build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
