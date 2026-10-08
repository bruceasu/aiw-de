package me.asu.aidev.webconsole;

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
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("sqlite")
class R9WebConsoleSecurityIT {
    private static final Path DB;
    static {
        try {
            DB = Files.createTempFile("r9-web-console-security-", ".db");
            Files.deleteIfExists(DB);
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + DB.toAbsolutePath());
        registry.add("aidev.web-console.auth.enabled", () -> "true");
        registry.add("aidev.web-console.auth.username", () -> "viewer");
        registry.add("aidev.web-console.auth.password", () -> "test-secret");
        registry.add("aidev.web-console.auth.role", () -> "VIEWER");
        registry.add("spring.thymeleaf.cache", () -> "false");
    }

    @LocalServerPort int port;
    private final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
    private final HttpClient http = HttpClient.newBuilder().cookieHandler(cookies).connectTimeout(Duration.ofSeconds(3))
            .followRedirects(HttpClient.Redirect.NEVER).build();

    @Test
    void sessionAuthenticationCsrfAndRoleAuthorizationProtectBrowserMutations() throws Exception {
        HttpResponse<String> unauthenticated = get("/ui");
        assertThat(unauthenticated.statusCode()).isEqualTo(302);
        assertRedirect(unauthenticated, "/ui/login", "next=%2Fui");

        HttpResponse<String> login = post("/ui/login", form(Map.of(
                "username", "viewer", "password", "test-secret", "next", "/ui")));
        assertThat(login.statusCode()).isEqualTo(302);
        assertRedirect(login, "/ui", null);

        HttpResponse<String> dashboard = get("/ui");
        assertThat(dashboard.statusCode()).isEqualTo(200);
        assertThat(dashboard.body()).contains("VIEWER");
        assertThat(dashboard.body()).doesNotContain("Start Autonomous Run");
        String csrf = csrf(dashboard.body());

        HttpResponse<String> noCsrf = post("/ui/system/fleet-scheduling", "maxParallelism=1");
        assertThat(noCsrf.statusCode()).isEqualTo(403);

        HttpResponse<String> forbidden = post("/ui/system/fleet-scheduling", form(Map.of(
                "_csrf", csrf, "maxParallelism", "1", "reason", "should be denied")));
        assertThat(forbidden.statusCode()).isEqualTo(403);
        assertThat(forbidden.body()).contains("Access denied");
    }

    private static void assertRedirect(HttpResponse<?> response, String expectedPath, String expectedRawQuery) {
        String location = response.headers().firstValue("location").orElseThrow();
        URI redirect = URI.create(location);
        assertThat(redirect.getPath()).isEqualTo(expectedPath);
        assertThat(redirect.getRawQuery()).isEqualTo(expectedRawQuery);
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
