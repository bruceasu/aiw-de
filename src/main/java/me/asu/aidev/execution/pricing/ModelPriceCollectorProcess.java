package me.asu.aidev.execution.pricing;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.net.URI;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import me.asu.aidev.authority.AuthorityRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Runs the installed collector; observations are never published as price rules here. */
@Component
public class ModelPriceCollectorProcess {
    static final int MAX_CONTENT = 2_000_000;
    private static final int MAX_OUTPUT = 3_800_000;
    public record Observation(String protocol, String provider, String sourceUrl, String contentSha256,
                              String collectorVersion, long observedMs, String currency, String unit,
                              String validity, boolean accountingReady, List<String> missing,
                              Map<String,Object> conditions, List<Map<String,Object>> models, String collectorSha256) {
        public Observation(String protocol, String provider, String sourceUrl, String contentSha256,
                           String collectorVersion, long observedMs, String currency, String unit,
                           String validity, boolean accountingReady, List<String> missing,
                           Map<String,Object> conditions, List<Map<String,Object>> models) {
            this(protocol, provider, sourceUrl, contentSha256, collectorVersion, observedMs, currency, unit,
                    validity, accountingReady, missing, conditions, models, "0".repeat(64));
        }
    }
    public record Response(String protocol, String state, Observation observation, String contentBase64,
                           String sourceUrl, Long observedMs, String collectorVersion, String collectorSha256, String reason) {}
    public record Collected(Observation observation, String sampleBase64) {}
    public record PageSnapshot(String sourceUrl, long observedMs, String contentSha256, String contentBase64) {}
    public record SourceChange(String provider, String sourceUrl, long observedMs, String collectorVersion, String collectorSha256, String reason,
                               String fingerprint, String contentSha256, String contentBase64) {
        public SourceChange(String sourceUrl, long observedMs, String collectorVersion, String reason,
                            String fingerprint, String contentSha256, String contentBase64) {
            this("deepseek", sourceUrl, observedMs, collectorVersion, "0".repeat(64), reason, fingerprint, contentSha256, contentBase64);
        }
    }
    public static final class StructureChange extends RuntimeException {
        private final SourceChange change;
        StructureChange(SourceChange change) { super("PRICE_SOURCE_STRUCTURE_CHANGED"); this.change = change; }
        public SourceChange change() { return change; }
    }
    private record Result(int exit, byte[] output) {}
    private final AuthorityRepository repo;
    private final String python;
    private final Path runner;
    private final String deepseekHost;
    private final String openaiHost;

    @org.springframework.beans.factory.annotation.Autowired
    public ModelPriceCollectorProcess(AuthorityRepository repo,
            @Value("${aidev.model-pricing.python:python}") String python,
            @Value("${aidev.model-pricing.collector-root:workers/price-collectors}") String root,
            @Value("${aidev.model-pricing.deepseek-host:api-docs.deepseek.com}") String deepseekHost,
            @Value("${aidev.model-pricing.openai-host:developers.openai.com}") String openaiHost) {
        this.repo = repo;
        this.python = python;
        this.runner = Path.of(root).toAbsolutePath().normalize().resolve("runner.py");
        this.deepseekHost = deepseekHost;
        this.openaiHost = openaiHost;
    }

    public ModelPriceCollectorProcess(AuthorityRepository repo, String python, String root) {
        this(repo, python, root, "api-docs.deepseek.com", "developers.openai.com");
    }

    /** The caller supplies public bytes and is responsible for authenticating the source. */
    public Observation parseDeepSeek(byte[] content, long observedMs) {
        if (content == null || content.length == 0 || content.length > MAX_CONTENT || observedMs <= 0)
            throw new IllegalArgumentException("PRICE_COLLECTOR_INPUT_INVALID");
        Response response = invoke(Map.of("protocol", "aiw.price-parser-request.v1", "provider", "deepseek",
                "observedMs", observedMs, "contentBase64", Base64.getEncoder().encodeToString(content)));
        return validate(response, content, observedMs, "deepseek");
    }

    /** Fetching and source-specific parsing live in the installed Python collector. */
    public Observation collectDeepSeek() {
        return collectDetails("deepseek").observation();
    }

    public Observation collectOpenAI() {
        return collectDetails("openai").observation();
    }

    public Collected collectDetails(String provider) {
        if (!List.of("deepseek", "openai").contains(provider)) throw new IllegalArgumentException("PRICE_PROVIDER_INVALID");
        long started = System.currentTimeMillis();
        Response response = invoke(Map.of("protocol", "aiw.price-collector-request.v1", "provider", provider));
        byte[] content;
        try { content = Base64.getDecoder().decode(response.contentBase64()); }
        catch (IllegalArgumentException | NullPointerException failure) { throw new IllegalStateException("PRICE_COLLECTOR_RESPONSE_INVALID"); }
        if (content.length == 0 || content.length > MAX_CONTENT)
            throw new IllegalStateException("PRICE_COLLECTOR_RESPONSE_INVALID");
        if ("STRUCTURE_CHANGED".equals(response.state())) {
            Long observed = response.observedMs();
            if (observed == null || observed < started - 30_000 || observed > System.currentTimeMillis() + 30_000
                    || !official(response.sourceUrl(), provider) || response.reason() == null || !response.reason().matches("[A-Z0-9_]{1,80}")
                    || response.collectorVersion() == null || !response.collectorVersion().matches("[A-Za-z0-9._-]{1,128}")
                    || response.collectorSha256() == null || !response.collectorSha256().matches("[a-f0-9]{64}"))
                throw new IllegalStateException("PRICE_COLLECTOR_RESPONSE_INVALID");
            String fingerprint = AuthorityRepository.hash(provider + ":" + response.collectorSha256() + ":" + response.reason());
            throw new StructureChange(new SourceChange(provider, response.sourceUrl(), observed, response.collectorVersion(), response.collectorSha256(),
                    response.reason(), fingerprint, hash(content), response.contentBase64()));
        }
        if (response.observation() == null) throw new IllegalStateException("PRICE_COLLECTOR_RESPONSE_INVALID");
        long observed = response.observation().observedMs();
        if (observed < started - 30_000 || observed > System.currentTimeMillis() + 30_000)
            throw new IllegalStateException("PRICE_COLLECTOR_RESPONSE_INVALID");
        return new Collected(validate(response, content, observed, provider), response.contentBase64());
    }

    /** Fetches an official page URL named by the repair worker without running its candidate code. */
    public PageSnapshot fetchLatestPage(String provider, String sourceUrl) {
        if (!List.of("deepseek", "openai").contains(provider) || !official(sourceUrl, provider))
            throw new IllegalArgumentException("PRICE_SOURCE_URL_INVALID");
        long started = System.currentTimeMillis();
        Response response = invoke(Map.of("protocol", "aiw.price-page-fetch-request.v1",
                "provider", provider, "sourceUrl", sourceUrl));
        if (!"PAGE".equals(response.state())) throw new IllegalStateException("PRICE_PAGE_RESPONSE_INVALID");
        byte[] content;
        try { content = Base64.getDecoder().decode(response.contentBase64()); }
        catch (IllegalArgumentException | NullPointerException failure) { throw new IllegalStateException("PRICE_PAGE_RESPONSE_INVALID"); }
        Long observed = response.observedMs();
        if (content.length == 0 || content.length > MAX_CONTENT || observed == null
                || observed < started - 30_000 || observed > System.currentTimeMillis() + 30_000
                || !sourceUrl.equals(response.sourceUrl()) || !official(response.sourceUrl(), provider))
            throw new IllegalStateException("PRICE_PAGE_RESPONSE_INVALID");
        return new PageSnapshot(sourceUrl, observed, hash(content), response.contentBase64());
    }

    /** Runs an owner-approved candidate once against the exact public page snapshot the owner reviewed. */
    public Collected parseApprovedCandidate(String provider, byte[] module, byte[] content,
                                            long observedMs, String sourceUrl) {
        if (!List.of("openai", "deepseek").contains(provider) || module == null || module.length == 0 || module.length > 1_000_000
                || content == null || content.length == 0 || content.length > MAX_CONTENT || observedMs <= 0
                || !official(sourceUrl, provider)) throw new IllegalArgumentException("PRICE_CANDIDATE_INVALID");
        Path temporary;
        try {
            temporary = java.nio.file.Files.createTempDirectory("aiw-price-candidate-");
            java.nio.file.Files.copy(runner, temporary.resolve("runner.py"));
            java.nio.file.Files.write(temporary.resolve(provider + ".py"), module,
                    java.nio.file.StandardOpenOption.CREATE_NEW, java.nio.file.StandardOpenOption.WRITE);
        } catch (java.io.IOException failure) {
            throw new IllegalStateException("PRICE_CANDIDATE_UNAVAILABLE");
        }
        try {
            var isolated = new ModelPriceCollectorProcess(repo, python, temporary.toString(), deepseekHost, openaiHost);
            Response response = isolated.invoke(Map.of("protocol", "aiw.price-parser-request.v1", "provider", provider,
                    "observedMs", observedMs, "contentBase64", Base64.getEncoder().encodeToString(content), "sourceUrl", sourceUrl));
            Observation observation = isolated.validate(response, content, observedMs, provider);
            if (!sourceUrl.equals(observation.sourceUrl()) || !hash(module).equals(observation.collectorSha256()))
                throw new IllegalStateException("PRICE_CANDIDATE_RESPONSE_INVALID");
            return new Collected(observation, Base64.getEncoder().encodeToString(content));
        } finally {
            try {
                java.nio.file.Files.deleteIfExists(temporary.resolve(provider + ".py"));
                java.nio.file.Files.deleteIfExists(temporary.resolve("runner.py"));
                java.nio.file.Files.deleteIfExists(temporary);
            } catch (java.io.IOException cleanupFailure) {
                // The directory has a generated name and contains only the submitted module and runner.
            }
        }
    }

    private Response invoke(Map<String,Object> request) {
        String input = repo.encode(request);
        Process process;
        try {
            var builder = new ProcessBuilder(python, "-I", runner.toString());
            builder.redirectError(ProcessBuilder.Redirect.DISCARD);
            // Do not expose API keys, proxy credentials, user profile or task data to the parser.
            var environment = builder.environment();
            String systemRoot = environment.get("SystemRoot"), windir = environment.get("WINDIR");
            environment.clear();
            if (systemRoot != null) environment.put("SystemRoot", systemRoot);
            if (windir != null) environment.put("WINDIR", windir);
            process = builder.start();
        } catch (java.io.IOException failure) {
            throw new IllegalStateException("PRICE_COLLECTOR_UNAVAILABLE");
        }
        var executor = Executors.newVirtualThreadPerTaskExecutor();
        try {
            var task = executor.submit(() -> {
                try (var stdin = process.getOutputStream()) { stdin.write(input.getBytes(StandardCharsets.UTF_8)); }
                byte[] output;
                try (var stdout = process.getInputStream()) { output = stdout.readNBytes(MAX_OUTPUT + 1); }
                int exit = process.waitFor();
                if (output.length > MAX_OUTPUT) throw new IllegalStateException("PRICE_COLLECTOR_REJECTED");
                return new Result(exit, output);
            });
            Result result = task.get(25, TimeUnit.SECONDS);
            var response = repo.decode(new String(result.output(), StandardCharsets.UTF_8), Response.class);
            if (response == null || !"aiw.price-parser-response.v1".equals(response.protocol()))
                throw new IllegalStateException("PRICE_COLLECTOR_RESPONSE_INVALID");
            if ("FETCH_FAILED".equals(response.state())) throw new IllegalStateException("PRICE_SOURCE_UNAVAILABLE");
            if ("STRUCTURE_CHANGED".equals(response.state()) && result.exit() != 0) return response;
            boolean pageFetch = "aiw.price-page-fetch-request.v1".equals(request.get("protocol"))
                    && "PAGE".equals(response.state());
            if (result.exit() != 0 || !pageFetch && !"OBSERVED".equals(response.state()))
                throw new IllegalStateException("PRICE_COLLECTOR_REJECTED");
            return response;
        } catch (TimeoutException failure) {
            throw new IllegalStateException("PRICE_COLLECTOR_TIMEOUT");
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("PRICE_COLLECTOR_INTERRUPTED");
        } catch (ExecutionException failure) {
            throw new IllegalStateException("PRICE_COLLECTOR_REJECTED");
        } finally {
            process.destroyForcibly();
            executor.shutdownNow();
        }
    }

    private Observation validate(Response response, byte[] content, long observedMs, String provider) {
        Observation observation = response.observation();
        if (observation == null || !"aiw.price-observation.v1".equals(observation.protocol())
                || !provider.equals(observation.provider()) || !official(observation.sourceUrl(), provider)
                || !hash(content).equals(observation.contentSha256()) || observation.observedMs() != observedMs
                || observation.collectorSha256() == null || !observation.collectorSha256().matches("[a-f0-9]{64}")
                || observation.collectorVersion() == null || !observation.collectorVersion().matches("[A-Za-z0-9._-]{1,128}")
                || !"USD".equals(observation.currency()) || !"micro-currency-per-million-tokens".equals(observation.unit())
                || observation.conditions() == null || observation.conditions().isEmpty()
                || observation.models() == null || observation.models().isEmpty() || observation.models().size() > 100
                || observation.models().stream().anyMatch(model -> model == null || model.isEmpty())
                || observation.accountingReady() || !"OBSERVED_ONLY".equals(observation.validity())
                || observation.missing() == null || !observation.missing().containsAll(List.of("TRUSTED_EFFECTIVE_INTERVAL",
                        "openai".equals(provider) ? "SERVICE_TIER_AND_PRICING_CONDITIONS" : "CHINESE_PUBLIC_HOLIDAY_CALENDAR")))
            throw new IllegalStateException("PRICE_COLLECTOR_RESPONSE_INVALID");
        return observation;
    }

    private boolean official(String value, String provider) {
        try {
            URI source = URI.create(value);
            String host = "openai".equals(provider) ? openaiHost : deepseekHost;
            return value.length() <= 2048 && "https".equalsIgnoreCase(source.getScheme()) && host.equalsIgnoreCase(source.getHost())
                    && (source.getPort() == -1 || source.getPort() == 443)
                    && source.getUserInfo() == null && source.getFragment() == null;
        } catch (IllegalArgumentException | NullPointerException failure) { return false; }
    }

    public boolean isOfficialSourceUrl(String value, String provider) {
        return List.of("deepseek", "openai").contains(provider) && official(value, provider);
    }

    private static String hash(byte[] content) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content)); }
        catch (java.security.NoSuchAlgorithmException failure) { throw new IllegalStateException(failure); }
    }
}
