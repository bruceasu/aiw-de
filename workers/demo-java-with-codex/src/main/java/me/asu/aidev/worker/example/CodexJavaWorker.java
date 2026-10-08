package me.asu.aidev.worker.example;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Java 21 reference implementation of Worker Protocol v1.
 *
 * No third-party Java dependencies are required. The AI runtime is the installed Codex CLI,
 * invoked as `codex exec --json`. The Java process never touches the Control Plane database.
 */
public final class CodexJavaWorker {
    private final Config cfg;
    private final ControlPlaneClient client;
    private final AtomicBoolean stopping = new AtomicBoolean();
    private final Set<Process> activeProcesses = ConcurrentHashMap.newKeySet();

    private CodexJavaWorker(Config cfg) {
        this.cfg = cfg;
        this.client = new ControlPlaneClient(cfg);
    }

    public static void main(String[] args) throws Exception {
        new CodexJavaWorker(Config.fromEnv()).run();
    }

    private void run() throws Exception {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            stopping.set(true);
            activeProcesses.forEach(Process::destroy);
            try { client.drain(); } catch (Exception ignored) {}
            try { client.offline(); } catch (Exception ignored) {}
        }, "shutdown"));
        client.register();
        System.out.printf("registered %s (%d slots)%n", cfg.workerId(), cfg.maxConcurrency());

        ScheduledExecutorService workerHeartbeat = Executors.newSingleThreadScheduledExecutor();
        workerHeartbeat.scheduleWithFixedDelay(() -> {
            if (!stopping.get()) {
                try { client.workerHeartbeat(); }
                catch (Exception e) { System.err.println("worker heartbeat failed: " + e); }
            }
        }, cfg.heartbeatInterval().toMillis(), cfg.heartbeatInterval().toMillis(), TimeUnit.MILLISECONDS);

        try (ExecutorService slots = Executors.newFixedThreadPool(cfg.maxConcurrency())) {
            for (int i = 1; i <= cfg.maxConcurrency(); i++) {
                int slotNo = i;
                slots.submit(() -> slotLoop(slotNo));
            }
            while (!stopping.get()) Thread.sleep(250);
            slots.shutdownNow();
            slots.awaitTermination(5, TimeUnit.SECONDS);
        } finally {
            workerHeartbeat.shutdownNow();
            try { client.drain(); } catch (Exception ignored) {}
            try { client.offline(); } catch (Exception ignored) {}
        }
    }

    private void slotLoop(int slot) {
        while (!stopping.get() && !Thread.currentThread().isInterrupted()) {
            try {
                ClaimedTask task = client.claim();
                if (task == null) {
                    sleep(cfg.pollInterval());
                    continue;
                }
                executeTask(slot, task);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                System.err.printf("slot %d: %s%n", slot, e);
                try { sleep(cfg.pollInterval()); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); return; }
            }
        }
    }

    private void executeTask(int slot, ClaimedTask task) throws Exception {
        client.start(task);
        AtomicReference<String> sessionId = new AtomicReference<>();
        AtomicReference<Process> processRef = new AtomicReference<>();
        AtomicBoolean taskDone = new AtomicBoolean();
        AtomicBoolean ownershipLost = new AtomicBoolean();

        Thread leaseHeartbeat = Thread.ofVirtual().name("lease-heartbeat-" + task.taskId()).start(() -> {
            while (!taskDone.get() && !stopping.get()) {
                try {
                    Thread.sleep(cfg.heartbeatInterval());
                    if (taskDone.get()) return;
                    client.taskHeartbeat(task, sessionId.get());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                } catch (Exception e) {
                    ownershipLost.set(true);
                    System.err.printf("task %s lease lost: %s%n", task.taskId(), e);
                    Process p = processRef.get();
                    if (p != null && p.isAlive()) {
                        p.destroy();
                        try {
                            if (!p.waitFor(1, TimeUnit.SECONDS)) p.destroyForcibly();
                        } catch (InterruptedException ex) {
                            Thread.currentThread().interrupt();
                        }
                    }
                    return;
                }
            }
        });

        GitWorkspace workspace = null;
        try {
            workspace = GitWorkspace.materialize(cfg, task);
            Path executionDir = workspace.cwd();
            String effectiveInstruction = task.instruction();
            if (!task.inputs().isEmpty()) effectiveInstruction += "\n\nUpstream task inputs (authoritative):\n" + Json.stringify(task.inputs().stream().map(TaskInput::toJson).toList());
            CodexResult result = runCodex(executionDir, effectiveInstruction, processRef, id -> {
                sessionId.set(id);
                try { client.taskHeartbeat(task, id); }
                catch (Exception e) {
                    ownershipLost.set(true);
                    Process p = processRef.get();
                    if (p != null) p.destroy();
                    throw new RuntimeException(e);
                }
            });
            if (!ownershipLost.get()) {
                WorkspaceResult workspaceResult = workspace.finalizeResult(task);
                CompletionReport completionReport = completionReport(task, result.finalMessage());
                client.complete(task, sessionId.get(), workspaceResult, completionReport);
                System.out.printf("slot %d completed %s session=%s revision=%s%n", slot, task.taskId(),
                        nullToDash(sessionId.get()), workspaceResult == null ? "-" : workspaceResult.resultRevision());
                if (!result.finalMessage().isBlank()) System.out.printf("slot %d: %s%n", slot, result.finalMessage());
            }
        } catch (Exception e) {
            if (!ownershipLost.get()) {
                try { client.fail(task, sessionId.get(), e.toString()); }
                catch (Exception reportError) { System.err.println("fail reporting failed: " + reportError); }
                System.err.printf("slot %d task %s failed: %s%n", slot, task.taskId(), e);
            }
        } finally {
            taskDone.set(true);
            leaseHeartbeat.interrupt();
            leaseHeartbeat.join(Duration.ofSeconds(2));
            if (workspace != null) workspace.cleanup();
        }
    }

    private static CompletionReport completionReport(ClaimedTask task, String finalMessage) {
        if (Set.of("REQUIREMENT_ANALYSIS", "SOLUTION_ARCHITECTURE", "IMPLEMENTATION_PLANNING").contains(task.taskType())) {
            String summary = finalMessage == null ? "" : finalMessage.strip();
            if (summary.isBlank()) throw new IllegalStateException(task.taskType() + " must return a non-empty final response");
            return new CompletionReport("PASS", summary);
        }
        if (!"CODE_REVIEW".equals(task.taskType()) && !"VERIFICATION".equals(task.taskType()) && !"INTEGRATION_VERIFICATION".equals(task.taskType())) return null;
        String verdict = null;
        String[] lines = defaultString(finalMessage).split("\\R");
        for (int i = lines.length - 1; i >= 0; i--) {
            String line = lines[i].strip();
            if (line.startsWith("AIW_VERDICT:")) {
                verdict = line.substring("AIW_VERDICT:".length()).strip();
                break;
            }
        }
        if (!"PASS".equals(verdict) && !"REPAIR_REQUIRED".equals(verdict)) {
            throw new IllegalStateException("review/verification final response must end with AIW_VERDICT: PASS or AIW_VERDICT: REPAIR_REQUIRED");
        }
        String summary = defaultString(finalMessage).strip();
        if (summary.isBlank()) summary = "AIW_VERDICT: " + verdict;
        return new CompletionReport(verdict, summary);
    }

    private CodexResult runCodex(Path workspace, String instruction,
                                 AtomicReference<Process> processRef,
                                 java.util.function.Consumer<String> onSession) throws Exception {
        List<String> command = new ArrayList<>();
        command.add(cfg.codexPath());
        command.addAll(List.of("exec", "--json", "--sandbox", cfg.sandbox(), "--cd", workspace.toString()));
        command.addAll(List.of("--config", "approval_policy=\"" + cfg.approvalPolicy() + "\""));
        command.addAll(List.of("--config", "model_reasoning_effort=\"" + cfg.reasoningEffort() + "\""));
        command.addAll(List.of("--config", "sandbox_workspace_write.network_access=" + cfg.networkAccess()));
        if (cfg.model() != null && !cfg.model().isBlank()) command.addAll(List.of("--model", cfg.model()));
        command.add("-");

        Process process = new ProcessBuilder(command)
                .directory(workspace.toFile())
                .redirectError(ProcessBuilder.Redirect.INHERIT)
                .start();
        processRef.set(process);
        activeProcesses.add(process);
        try (Writer stdin = new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8)) {
            stdin.write(instruction);
            stdin.write(System.lineSeparator());
        }

        String session = null;
        String finalMessage = "";
        String terminalFailure = null;
        try (BufferedReader reader = process.inputReader(StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                Object parsed;
                try { parsed = Json.parse(line); }
                catch (RuntimeException e) {
                    System.err.println("codex non-json stdout: " + line);
                    continue;
                }
                if (!(parsed instanceof Map<?, ?> event)) continue;
                String type = string(event.get("type"));
                if ("thread.started".equals(type)) {
                    session = string(event.get("thread_id"));
                    if (session != null) onSession.accept(session);
                } else if ("item.completed".equals(type) && event.get("item") instanceof Map<?, ?> item) {
                    if ("agent_message".equals(string(item.get("type")))) finalMessage = defaultString(string(item.get("text")));
                } else if ("turn.failed".equals(type) || "error".equals(type)) {
                    terminalFailure = line;
                }
            }
        }

        int exit = process.waitFor();
        activeProcesses.remove(process);
        if (terminalFailure != null) throw new IllegalStateException(terminalFailure);
        if (exit != 0) throw new IllegalStateException("codex exec exited " + exit);
        return new CodexResult(session, finalMessage);
    }

    private record CodexResult(String sessionId, String finalMessage) {}

    private record WorkspaceAssignment(String repositoryRef, String baseRevision, String workspaceKey, String branchName,
                                       String workspaceId, String repositoryPath) {
        static WorkspaceAssignment from(Object value) {
            if (value == null) return null;
            if (!(value instanceof Map<?, ?> map)) throw new IllegalArgumentException("workspace is not an object");
            return new WorkspaceAssignment(requiredString(map, "repositoryRef"), string(map.get("baseRevision")),
                    requiredString(map, "workspaceKey"), string(map.get("branchName")),
                    string(map.get("workspaceId")), string(map.get("repositoryPath")));
        }
    }

    private record WorkspaceResult(String resultRevision, List<String> changedFiles, String publicationRef) {
        Map<String,Object> toJson() {
            Map<String,Object> out = new LinkedHashMap<>();
            out.put("resultRevision", resultRevision); out.put("changedFiles", changedFiles); out.put("publicationRef", publicationRef);
            return out;
        }
    }

    private record CompletionReport(String verdict, String summary) {
        Map<String,Object> toJson() { return Map.of("verdict", verdict, "summary", summary); }
    }

    private record TaskInput(String taskId, String taskType, String repositoryRef, String baseRevision,
                             String resultRevision, String publicationRef, List<String> changedFiles,
                             String completionVerdict, String completionSummary) {
        static TaskInput from(Object value) {
            if (!(value instanceof Map<?, ?> map)) throw new IllegalArgumentException("task input is not an object");
            return new TaskInput(string(map.get("taskId")), string(map.get("taskType")), string(map.get("repositoryRef")),
                    string(map.get("baseRevision")), string(map.get("resultRevision")), string(map.get("publicationRef")), stringList(map.get("changedFiles")),
                    string(map.get("completionVerdict")), string(map.get("completionSummary")));
        }
        Map<String,Object> toJson() {
            Map<String,Object> out = new LinkedHashMap<>();
            out.put("taskId", taskId); out.put("taskType", taskType); out.put("repositoryRef", repositoryRef);
            out.put("baseRevision", baseRevision); out.put("resultRevision", resultRevision); out.put("publicationRef", publicationRef); out.put("changedFiles", changedFiles);
            out.put("completionVerdict", completionVerdict); out.put("completionSummary", completionSummary);
            return out;
        }
    }

    private record ClaimedTask(String taskId, String taskType, String instruction, String leaseToken, int attemptNo, WorkspaceAssignment workspace, List<TaskInput> inputs) {
        static ClaimedTask from(Object value) {
            if (!(value instanceof Map<?, ?> map)) throw new IllegalArgumentException("claim response is not an object");
            Number attempt = map.get("attemptNo") instanceof Number n ? n : null;
            if (attempt == null) throw new IllegalArgumentException("attemptNo missing");
            List<TaskInput> inputs = new ArrayList<>();
            if (map.get("inputs") instanceof List<?> list) for (Object item : list) inputs.add(TaskInput.from(item));
            return new ClaimedTask(requiredString(map, "taskId"), requiredString(map, "taskType"), requiredString(map, "instruction"),
                    requiredString(map, "leaseToken"), attempt.intValue(), WorkspaceAssignment.from(map.get("workspace")), List.copyOf(inputs));
        }
    }

    private static final class GitWorkspace {
        private final Config cfg;
        private final Path cwd;
        private final WorkspaceAssignment assignment;
        private final Path repositoryRoot;

        private GitWorkspace(Config cfg, Path cwd, WorkspaceAssignment assignment, Path repositoryRoot) {
            this.cfg = cfg; this.cwd = cwd; this.assignment = assignment; this.repositoryRoot = repositoryRoot;
        }
        Path cwd() { return cwd; }

        static GitWorkspace materialize(Config cfg, ClaimedTask task) throws Exception {
            if (task.workspace() == null) return new GitWorkspace(cfg, cfg.fallbackWorkspace(), null, cfg.repositoryRoot());
            Path repositoryRoot = task.workspace().repositoryPath() == null ? cfg.repositoryRoot()
                    : Path.of(task.workspace().repositoryPath()).toAbsolutePath().normalize();
            if (repositoryRoot == null)
                throw new IllegalStateException("repository task claimed without AIW_REPOSITORY_REF/AIW_REPOSITORY_ROOT");
            if (task.workspace().repositoryPath() == null && !task.workspace().repositoryRef().equals(cfg.repositoryRef()))
                throw new IllegalStateException("repository mismatch: " + task.workspace().repositoryRef() + " != " + cfg.repositoryRef());
            if (task.workspace().baseRevision() == null) {
                if (!Files.isDirectory(repositoryRoot)) throw new IllegalStateException("workspace directory missing: " + repositoryRoot);
                return new GitWorkspace(cfg, repositoryRoot, task.workspace(), repositoryRoot);
            }
            fetchPublishedInputs(repositoryRoot, task);
            String resolved = gitText(repositoryRoot, "rev-parse", "--verify", task.workspace().baseRevision() + "^{commit}").strip().toLowerCase();
            if (!resolved.equals(task.workspace().baseRevision().toLowerCase()))
                throw new IllegalStateException("base revision mismatch: expected " + task.workspace().baseRevision() + " resolved " + resolved);
            Files.createDirectories(cfg.worktreeRoot());
            Path cwd = cfg.worktreeRoot().resolve(task.workspace().workspaceKey()).toAbsolutePath().normalize();
            gitText(repositoryRoot, "worktree", "add", "-b", task.workspace().branchName(), cwd.toString(), task.workspace().baseRevision());
            return new GitWorkspace(cfg, cwd, task.workspace(), repositoryRoot);
        }

        WorkspaceResult finalizeResult(ClaimedTask task) throws Exception {
            if (assignment == null || assignment.baseRevision() == null) return null;
            gitText(cwd, "add", "-A");
            byte[] staged = gitBytes(cwd, "diff", "--cached", "--name-only", "-z");
            if (staged.length > 0) {
                gitText(cwd, "-c", "user.name=AI Dev Worker", "-c", "user.email=aidev-worker@localhost",
                        "commit", "--no-gpg-sign", "-m", "aidev: " + task.taskId() + " attempt " + task.attemptNo());
            }
            String revision = gitText(cwd, "rev-parse", "HEAD").strip().toLowerCase();
            List<String> changed = splitZero(gitBytes(cwd, "diff", "--name-only", "-z", assignment.baseRevision(), revision));
            String publicationRef = null;
            if (!cfg.gitPublishRemote().isBlank()) {
                gitText(cwd, "push", cfg.gitPublishRemote(), revision + ":refs/heads/" + assignment.branchName());
                publicationRef = "git:" + cfg.gitPublishRemote() + ":" + assignment.branchName();
            }
            return new WorkspaceResult(revision, List.copyOf(changed), publicationRef);
        }

        private static void fetchPublishedInputs(Path repositoryRoot, ClaimedTask task) throws Exception {
            for (TaskInput input : task.inputs()) {
                String ref = input.publicationRef();
                if (ref == null || !ref.startsWith("git:")) continue;
                String[] parts = ref.split(":", 3);
                if (parts.length != 3) continue;
                gitText(repositoryRoot, "fetch", parts[1], parts[2]);
            }
        }

        void cleanup() {
            if (assignment == null || assignment.baseRevision() == null) return;
            try { gitText(repositoryRoot, "worktree", "remove", "--force", cwd.toString()); }
            catch (Exception e) { System.err.println("worktree cleanup failed " + cwd + ": " + e); }
        }
    }

    private static String gitText(Path cwd, String... args) throws Exception {
        return new String(gitBytes(cwd, args), StandardCharsets.UTF_8);
    }

    private static byte[] gitBytes(Path cwd, String... args) throws Exception {
        List<String> command = new ArrayList<>(); command.add("git"); command.add("-C"); command.add(cwd.toString()); command.addAll(List.of(args));
        Process p = new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.INHERIT).start();
        byte[] out = p.getInputStream().readAllBytes();
        int exit = p.waitFor();
        if (exit != 0) throw new IllegalStateException("git exited " + exit + ": " + String.join(" ", command));
        return out;
    }

    private static List<String> splitZero(byte[] raw) {
        List<String> files = new ArrayList<>(); int start = 0;
        for (int i = 0; i <= raw.length; i++) {
            if (i == raw.length || raw[i] == 0) {
                if (i > start) files.add(new String(raw, start, i - start, StandardCharsets.UTF_8));
                start = i + 1;
            }
        }
        files.sort(String::compareTo); return files;
    }

    private static final class ControlPlaneClient {
        private final Config cfg;
        private final HttpClient http;
        private final URI base;

        ControlPlaneClient(Config cfg) {
            this.cfg = cfg;
            this.http = HttpClient.newBuilder().connectTimeout(cfg.httpTimeout()).build();
            this.base = URI.create(cfg.baseUrl() + "/api/worker-protocol/v1");
        }

        void register() throws Exception {
            post("/workers/register", Map.of(
                    "workerId", cfg.workerId(),
                    "runtimeType", "codex-java-cli",
                    "capabilities", cfg.capabilities(),
                    "maxConcurrency", cfg.maxConcurrency()), false);
        }

        ClaimedTask claim() throws Exception {
            Response response = post("/tasks/claim", Map.of("workerId", cfg.workerId()), true);
            return response.status() == 204 ? null : ClaimedTask.from(Json.parse(response.body()));
        }

        void workerHeartbeat() throws Exception { post("/workers/" + path(cfg.workerId()) + "/heartbeat", null, false); }
        void drain() throws Exception { post("/workers/" + path(cfg.workerId()) + "/drain", null, false); }
        void offline() throws Exception { post("/workers/" + path(cfg.workerId()) + "/offline", null, false); }
        void start(ClaimedTask task) throws Exception { action(task, "start", null); }
        void taskHeartbeat(ClaimedTask task, String session) throws Exception { action(task, "heartbeat", session); }
        void complete(ClaimedTask task, String session, WorkspaceResult workspaceResult, CompletionReport report) throws Exception {
            Map<String,Object> body = new LinkedHashMap<>();
            body.put("workerId", cfg.workerId()); body.put("leaseToken", task.leaseToken()); body.put("externalSessionId", session);
            body.put("workspaceResult", workspaceResult == null ? null : workspaceResult.toJson());
            body.put("report", report == null ? null : report.toJson());
            post("/tasks/" + path(task.taskId()) + "/complete", body, false);
        }
        void fail(ClaimedTask task, String session, String failureReason) throws Exception {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("workerId", cfg.workerId());
            body.put("leaseToken", task.leaseToken());
            body.put("externalSessionId", session);
            body.put("failureReason", failureReason);
            post("/tasks/" + path(task.taskId()) + "/fail", body, false);
        }

        private void action(ClaimedTask task, String action, String session) throws Exception {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("workerId", cfg.workerId());
            body.put("leaseToken", task.leaseToken());
            body.put("externalSessionId", session);
            post("/tasks/" + path(task.taskId()) + "/" + action, body, false);
        }

        private Response post(String path, Object payload, boolean allowNoContent) throws Exception {
            HttpRequest.Builder builder = HttpRequest.newBuilder(base.resolve(base.getPath() + path))
                    .timeout(cfg.httpTimeout())
                    .header("Accept", "application/json");
            if (!cfg.token().isBlank()) builder.header("Authorization", "Bearer " + cfg.token());
            if (payload == null) {
                builder.POST(HttpRequest.BodyPublishers.noBody());
            } else {
                builder.header("Content-Type", "application/json")
                       .POST(HttpRequest.BodyPublishers.ofString(Json.stringify(payload), StandardCharsets.UTF_8));
            }
            HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() == 204 && allowNoContent) return new Response(204, "");
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ProtocolException(response.statusCode(), response.body());
            }
            return new Response(response.statusCode(), response.body());
        }
    }

    private record Response(int status, String body) {}
    private static final class ProtocolException extends IOException {
        ProtocolException(int status, String body) { super("worker protocol HTTP " + status + ": " + body); }
    }

    private record Config(String baseUrl, String workerId, String token, Set<String> capabilities,
                          int maxConcurrency, Path fallbackWorkspace, String repositoryRef, Path repositoryRoot, Path worktreeRoot, String gitPublishRemote,
                          Duration pollInterval, Duration heartbeatInterval, Duration httpTimeout, String codexPath, String model,
                          String sandbox, String approvalPolicy, String reasoningEffort, boolean networkAccess) {
        static Config fromEnv() {
            String repositoryRef = env("AIW_REPOSITORY_REF", "").strip();
            String repositoryRootValue = env("AIW_REPOSITORY_ROOT", "").strip();
            if (repositoryRef.isBlank() != repositoryRootValue.isBlank())
                throw new IllegalArgumentException("AIW_REPOSITORY_REF and AIW_REPOSITORY_ROOT must be configured together");
            Path repositoryRoot = repositoryRootValue.isBlank() ? null : Path.of(repositoryRootValue).toAbsolutePath().normalize();
            Path fallback = Path.of(env("AIW_WORKSPACE", repositoryRoot == null ? "." : repositoryRoot.toString())).toAbsolutePath().normalize();
            Path worktreeRoot = Path.of(env("AIW_WORKTREE_ROOT",
                    (repositoryRoot == null ? fallback : repositoryRoot).resolveSibling(".aidev-worktrees").toString())).toAbsolutePath().normalize();
            Set<String> capabilities = new LinkedHashSet<>(parseCapabilities(env("AIW_WORKER_CAPABILITIES", "coding")));
            if (!repositoryRef.isBlank()) capabilities.add("repo:" + repositoryRef);
            return new Config(
                    stripTrailingSlash(env("AIW_CONTROL_PLANE_URL", "http://127.0.0.1:8080")),
                    env("AIW_WORKER_ID", "codex-java-1"), env("AIW_WORKER_TOKEN", ""), Set.copyOf(capabilities),
                    positiveInt("AIW_WORKER_MAX_CONCURRENCY", 1), fallback, repositoryRef, repositoryRoot, worktreeRoot, env("AIW_GIT_PUBLISH_REMOTE", ""),
                    Duration.ofMillis(positiveInt("AIW_WORKER_POLL_MS", 1000)), Duration.ofMillis(positiveInt("AIW_WORKER_HEARTBEAT_MS", 20000)),
                    Duration.ofMillis(positiveInt("AIW_WORKER_HTTP_TIMEOUT_MS", 15000)), env("AIW_CODEX_PATH", "codex"), System.getenv("AIW_CODEX_MODEL"),
                    env("AIW_CODEX_SANDBOX", "workspace-write"), env("AIW_CODEX_APPROVAL_POLICY", "never"),
                    env("AIW_CODEX_REASONING_EFFORT", "high"), boolEnv("AIW_CODEX_NETWORK_ACCESS", false));
        }
    }

    /** Minimal JSON codec sufficient for the stable Worker Protocol v1 payloads. */
    private static final class Json {
        static Object parse(String text) { return new Parser(new java.io.StringReader(text)).parse(); }
        static String stringify(Object value) {
            StringBuilder out = new StringBuilder(); write(out, value); return out.toString();
        }
        private static void write(StringBuilder out, Object v) {
            if (v == null) out.append("null");
            else if (v instanceof String s) quote(out, s);
            else if (v instanceof Number || v instanceof Boolean) out.append(v);
            else if (v instanceof Map<?, ?> m) {
                out.append('{'); boolean first = true;
                for (var e : m.entrySet()) { if (!first) out.append(','); first = false; quote(out, String.valueOf(e.getKey())); out.append(':'); write(out, e.getValue()); }
                out.append('}');
            } else if (v instanceof Iterable<?> it) {
                out.append('['); boolean first = true;
                for (Object e : it) { if (!first) out.append(','); first = false; write(out, e); }
                out.append(']');
            } else throw new IllegalArgumentException("unsupported JSON type: " + v.getClass());
        }
        private static void quote(StringBuilder out, String s) {
            out.append('"');
            for (char c : s.toCharArray()) switch (c) {
                case '"' -> out.append("\\\""); case '\\' -> out.append("\\\\"); case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f"); case '\n' -> out.append("\\n"); case '\r' -> out.append("\\r"); case '\t' -> out.append("\\t");
                default -> { if (c < 0x20) out.append(String.format("\\u%04x", (int)c)); else out.append(c); }
            }
            out.append('"');
        }

        private static final class Parser {
            private final Reader in; private int ch = -2;
            Parser(Reader in) { this.in = in; }
            Object parse() { Object v = value(); ws(); if (peek() != -1) error("trailing input"); return v; }
            private Object value() {
                ws(); int c = peek();
                return switch (c) {
                    case '"' -> string(); case '{' -> object(); case '[' -> array();
                    case 't' -> literal("true", true); case 'f' -> literal("false", false); case 'n' -> literal("null", null);
                    default -> { if (c == '-' || Character.isDigit(c)) yield number(); error("expected value"); yield null; }
                };
            }
            private Map<String,Object> object() {
                expect('{'); Map<String,Object> m = new LinkedHashMap<>(); ws(); if (peek()=='}') { next(); return m; }
                while (true) { ws(); String k=string(); ws(); expect(':'); m.put(k,value()); ws(); int c=next(); if(c=='}') return m; if(c!=',') error("expected , or }"); }
            }
            private List<Object> array() {
                expect('['); List<Object> a=new ArrayList<>(); ws(); if(peek()==']'){next();return a;}
                while(true){a.add(value());ws();int c=next();if(c==']')return a;if(c!=',')error("expected , or ]");}
            }
            private String string() {
                expect('"'); StringBuilder b=new StringBuilder();
                while(true){int c=next();if(c==-1)error("unterminated string");if(c=='"')return b.toString();if(c=='\\'){int e=next();switch(e){case '"','\\','/'->b.append((char)e);case'b'->b.append('\b');case'f'->b.append('\f');case'n'->b.append('\n');case'r'->b.append('\r');case't'->b.append('\t');case'u'->{int n=0;for(int i=0;i<4;i++){int h=Character.digit(next(),16);if(h<0)error("bad unicode");n=n*16+h;}b.append((char)n);}default->error("bad escape");}}else b.append((char)c);}
            }
            private Number number(){StringBuilder b=new StringBuilder();if(peek()=='-')b.append((char)next());while(Character.isDigit(peek()))b.append((char)next());if(peek()=='.'){b.append((char)next());while(Character.isDigit(peek()))b.append((char)next());return Double.parseDouble(b.toString());}return Long.parseLong(b.toString());}
            private Object literal(String word,Object v){for(char c:word.toCharArray())expect(c);return v;}
            private void ws(){while(Character.isWhitespace(peek()))next();}
            private int peek(){if(ch!=-2)return ch;try{return ch=in.read();}catch(IOException e){throw new IllegalStateException(e);}}
            private int next(){int c=peek();ch=-2;return c;}
            private void expect(int c){if(next()!=c)error("expected "+(char)c);}
            private void error(String m){throw new IllegalArgumentException("invalid JSON: "+m);}
        }
    }

    private static String requiredString(Map<?, ?> map, String key) {
        String value = string(map.get(key));
        if (value == null || value.isBlank()) throw new IllegalArgumentException(key + " is blank");
        return value;
    }
    private static String string(Object v) { return v instanceof String s ? s : null; }
    private static List<String> stringList(Object value) {
        if (!(value instanceof List<?> list)) return List.of();
        return list.stream().filter(x -> x != null).map(Object::toString).toList();
    }
    private static String defaultString(String v) { return v == null ? "" : v; }
    private static String nullToDash(String v) { return v == null ? "-" : v; }
    private static String path(String s) { return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20"); }
    private static String env(String name, String fallback) { String v=System.getenv(name); return v==null||v.isBlank()?fallback:v; }
    private static int positiveInt(String name, int fallback) { int v=Integer.parseInt(env(name,String.valueOf(fallback))); if(v<1)throw new IllegalArgumentException(name+" must be >= 1"); return v; }
    private static boolean boolEnv(String name, boolean fallback) { return switch(env(name,String.valueOf(fallback)).toLowerCase()){case"1","true","yes"->true;default->false;}; }
    private static Set<String> parseCapabilities(String csv) { Set<String>s=new LinkedHashSet<>(); for(String p:csv.split(",")){p=p.strip();if(!p.isEmpty())s.add(p);} return Set.copyOf(s); }
    private static String stripTrailingSlash(String s){return s.endsWith("/")?s.substring(0,s.length()-1):s;}
    private static void sleep(Duration d) throws InterruptedException { Thread.sleep(d); }
}
