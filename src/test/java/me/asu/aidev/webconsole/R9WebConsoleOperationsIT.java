package me.asu.aidev.webconsole;

import me.asu.aidev.autonomous.application.AutonomousRunService;
import me.asu.aidev.worker.application.RegisterWorkerCommand;
import me.asu.aidev.worker.application.WorkerService;
import me.asu.aidev.worker.domain.WorkerId;
import me.asu.aidev.worker.domain.WorkerStatus;
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
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
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
class R9WebConsoleOperationsIT {
    private static final String BASE = "8888888888888888888888888888888888888888";
    private static final Path DB;
    static {
        try {
            DB = Files.createTempFile("r9-web-console-ops-", ".db");
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
        registry.add("spring.thymeleaf.cache", () -> "false");
    }

    @LocalServerPort int port;
    @Autowired AutonomousRunService runs;
    @Autowired WorkerService workers;

    private final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
    private final HttpClient http = HttpClient.newBuilder()
            .cookieHandler(cookies)
            .connectTimeout(Duration.ofSeconds(3))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    @Test
    void browserConsoleCreatesRunControlsSchedulingAndManagesWorkerWithoutBypassingAuthority() throws Exception {
        HttpResponse<String> createPage = get("/ui/runs/new");
        String csrf = csrf(createPage.body());
        HttpResponse<String> created = post("/ui/runs", form(Map.of(
                "_csrf", csrf,
                "requestId", "r9-console-ops",
                "requirement", "Create a small endpoint and verify it.",
                "repositoryRef", "repo-main",
                "baseRevision", BASE,
                "capabilities", "java,maven",
                "priority", "HIGH",
                "maxParallelism", "2")));
        assertThat(created.statusCode()).isEqualTo(200);
        assertThat(created.uri().getPath()).isEqualTo("/ui/runs/AR-r9-console-ops");
        assertThat(runs.find("AR-r9-console-ops").orElseThrow().maxParallelism()).isEqualTo(2);

        csrf = csrf(created.body());
        HttpResponse<String> paused = post("/ui/runs/AR-r9-console-ops/pause", form(Map.of(
                "_csrf", csrf, "reason", "operator inspection")));
        assertThat(paused.body()).contains("PAUSED");

        csrf = csrf(paused.body());
        HttpResponse<String> resumed = post("/ui/runs/AR-r9-console-ops/resume", form(Map.of(
                "_csrf", csrf, "reason", "inspection complete")));
        assertThat(resumed.body()).contains("RUNNING");

        csrf = csrf(resumed.body());
        post("/ui/runs/AR-r9-console-ops/scheduling", form(Map.of(
                "_csrf", csrf, "maxParallelism", "1", "reason", "limit test")));
        assertThat(runs.find("AR-r9-console-ops").orElseThrow().maxParallelism()).isEqualTo(1);

        HttpResponse<String> system = get("/ui/system");
        csrf = csrf(system.body());
        HttpResponse<String> fleetUpdated = post("/ui/system/fleet-scheduling", form(Map.of(
                "_csrf", csrf, "maxParallelism", "2", "reason", "web console integration test")));
        assertThat(fleetUpdated.statusCode()).isEqualTo(200);
        assertThat(fleetUpdated.body()).contains("Fleet Scheduling Audit");

        workers.register(new RegisterWorkerCommand(new WorkerId("r9-ui-worker"), "java", Set.of("java", "maven"), 1));
        HttpResponse<String> workerPage = get("/ui/workers");
        csrf = csrf(workerPage.body());
        post("/ui/workers/r9-ui-worker/drain", form(Map.of("_csrf", csrf)));
        assertThat(workers.get(new WorkerId("r9-ui-worker")).status()).isEqualTo(WorkerStatus.DRAINING);

        workerPage = get("/ui/workers");
        csrf = csrf(workerPage.body());
        post("/ui/workers/r9-ui-worker/offline", form(Map.of("_csrf", csrf)));
        assertThat(workers.get(new WorkerId("r9-ui-worker")).status()).isEqualTo(WorkerStatus.OFFLINE);
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
        assertThat(matcher.find()).as("CSRF token in rendered Web Console page").isTrue();
        return matcher.group(1);
    }

    private static String form(Map<String, String> values) {
        return values.entrySet().stream().map(e -> encode(e.getKey()) + "=" + encode(e.getValue()))
                .reduce((a, b) -> a + "&" + b).orElse("");
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
