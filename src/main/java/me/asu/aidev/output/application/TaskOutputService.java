package me.asu.aidev.output.application;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import me.asu.aidev.autonomous.application.WorkspaceRegistry;
import me.asu.aidev.execution.domain.LeaseToken;
import me.asu.aidev.execution.port.ExecutionAttemptRepository;
import me.asu.aidev.output.domain.TaskOutputBundle;
import me.asu.aidev.output.jdbc.JdbcTaskOutputRepository;
import me.asu.aidev.work.domain.TaskId;
import me.asu.aidev.work.domain.TaskState;
import me.asu.aidev.work.domain.WorkTask;
import me.asu.aidev.work.port.WorkTaskRepository;
import me.asu.aidev.workflow.application.WorkflowPlanService;
import me.asu.aidev.workflow.domain.WorkflowPlan;
import me.asu.aidev.workflow.domain.WorkflowPlanSpec;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Immutable deliverables at a persistent workspace; never a second owner of task state. */
@Service
public class TaskOutputService {
    private static final int MAX_FILE = 4 * 1024 * 1024;
    private static final int MAX_BUNDLE = 8 * 1024 * 1024;
    private final WorkTaskRepository tasks;
    private final ExecutionAttemptRepository attempts;
    private final JdbcTaskOutputRepository outputs;
    private final WorkspaceRegistry workspaces;
    private final WorkflowPlanService plans;
    private final ObjectMapper json = new ObjectMapper();
    private final java.time.Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public TaskOutputService(WorkTaskRepository tasks, ExecutionAttemptRepository attempts,
                             JdbcTaskOutputRepository outputs, WorkspaceRegistry workspaces,
                             WorkflowPlanService plans) {
        this(tasks, attempts, outputs, workspaces, plans, java.time.Clock.systemUTC());
    }

    public TaskOutputService(WorkTaskRepository tasks, ExecutionAttemptRepository attempts,
                             JdbcTaskOutputRepository outputs, WorkspaceRegistry workspaces,
                             WorkflowPlanService plans, java.time.Clock clock) {
        this.tasks = tasks; this.attempts = attempts; this.outputs = outputs;
        this.workspaces = workspaces; this.plans = plans; this.clock = clock;
    }

    public record Upload(String path, String kind, String contentBase64) {}
    public record Registration(String workerId, String leaseToken, String attemptId,
                               String verdict, String summary, String resultRevision, List<Upload> files) {}
    public record Ref(String attemptId, String taskId, List<TaskOutputBundle.File> files) {}
    public record Store(List<Ref> inputs, JdbcTaskOutputRepository.Publication publication) {}
    private record Report(String taskId, String attemptId, String verdict, String summary,
                          JdbcTaskOutputRepository.Publication publication) {}

    @Transactional
    public Store store(WorkTask task, String consumerAttemptId, List<me.asu.aidev.execution.domain.TaskInput> upstream) {
        if (task.repositoryRef() == null) return null;
        workspaces.location(task.repositoryRef());
        var refs = new ArrayList<Ref>();
        for (var input : upstream) {
            if (input.attemptId() != null) outputs.find(input.attemptId())
                    .filter(b -> b.taskId().equals(input.taskId().value()) && "COMPLETED".equals(b.executionState()))
                    .ifPresent(b -> refs.add(ref(b)));
        }
        var publication = outputs.publication(task.subjectId()).orElse(null);
        if (publication != null) {
            var b = outputs.find(outputs.file(publication.fileId()).attemptId()).orElseThrow();
            if (refs.stream().noneMatch(r -> r.attemptId().equals(b.attemptId()))) refs.add(ref(b));
        }
        for (var ref : refs) outputs.pin(consumerAttemptId, ref.attemptId());
        return new Store(List.copyOf(refs), publication);
    }

    private static Ref ref(TaskOutputBundle b) {
        return new Ref(b.attemptId(), b.taskId(), b.files().stream().filter(file -> !"PLAN".equals(file.kind())).toList());
    }

    private WorkTask lease(String taskId, String workerId, String token, String attemptId) {
        var id = new TaskId(taskId);
        if (!tasks.lock(id)) throw new IllegalArgumentException("task not found");
        var task = tasks.find(id).orElseThrow();
        var lease = tasks.findLease(id).orElseThrow(() -> new IllegalStateException("active lease required"));
        if (task.state() != TaskState.RUNNING || !lease.workerId().value().equals(workerId)
                || !lease.token().value().equals(token) || !lease.expiresAt().isAfter(clock.instant())
                || !attempts.findAttemptId(new LeaseToken(token)).orElseThrow().value().equals(attemptId)
                || attempts.stopRequested(lease.token(), lease.workerId())) {
            throw new IllegalStateException("output access requires the current running lease");
        }
        return task;
    }

    @Transactional
    public TaskOutputBundle register(String taskId, Registration request) {
        var task = lease(taskId, request.workerId(), request.leaseToken(), request.attemptId());
        if (task.repositoryRef() == null) throw new IllegalArgumentException("workspace repository required");
        if (request.summary() == null || request.summary().isBlank()
                || request.summary().length() > MAX_FILE || request.verdict() == null
                || !Set.of("PASS", "REPAIR_REQUIRED", "FAILED", "NO_REPORT").contains(request.verdict())) {
            throw new IllegalArgumentException("invalid output report");
        }
        if (request.resultRevision() != null && !request.resultRevision().matches("(?i)[a-f0-9]{40}|[a-f0-9]{64}")) {
            throw new IllegalArgumentException("invalid output revision");
        }
        var uploads = new ArrayList<Upload>();
        uploads.add(text("report.md", "REPORT", request.summary()));
        uploads.add(text("report.json", "REPORT", encode(new Report(taskId, request.attemptId(), request.verdict(), request.summary(),
                outputs.publication(task.subjectId()).orElse(null)))));
        if ("flexible-plan".equals(task.subjectType())) {
            var spec = plans.get(task.subjectId()).spec();
            uploads.add(text("plan.json", "PLAN", encode(spec)));
            StringBuilder markdown = new StringBuilder("# Plan\n\n").append(spec.goal()).append("\n");
            for (var step : spec.steps()) markdown.append("\n## ").append(step.key()).append("\n\n").append(step.instruction()).append("\n");
            uploads.add(text("plan.md", "PLAN", markdown.toString()));
        }
        if (request.files() != null) {
            if (request.files().size() > 16) throw new IllegalArgumentException("at most 16 output files");
            for (Upload file : request.files()) {
                if (file == null || file.kind() == null || !Set.of("DOCUMENT", "ATTACHMENT").contains(file.kind())) throw new IllegalArgumentException("invalid output kind");
                validatePath(file.path());
                uploads.add(new Upload("artifacts/" + file.path(), file.kind(), file.contentBase64()));
            }
        }
        var metadata = new ArrayList<TaskOutputBundle.File>();
        var contents = new ArrayList<String>();
        var bytes = new ArrayList<byte[]>();
        var seen = new java.util.HashSet<String>();
        long total = 0;
        for (Upload upload : uploads) {
            if (!seen.add(java.text.Normalizer.normalize(upload.path(), java.text.Normalizer.Form.NFC).toLowerCase(java.util.Locale.ROOT))
                    || upload.contentBase64() == null || upload.contentBase64().length() > MAX_FILE * 4L / 3 + 8) {
                throw new IllegalArgumentException("invalid or duplicate output file");
            }
            byte[] content = Base64.getDecoder().decode(upload.contentBase64());
            total += content.length;
            if (content.length > MAX_FILE || total > MAX_BUNDLE) throw new IllegalArgumentException("output capacity exceeded");
            metadata.add(new TaskOutputBundle.File(hash(request.attemptId() + ":" + upload.path()),
                    upload.path(), upload.kind(), hash(content), content.length));
            contents.add(Base64.getEncoder().encodeToString(content));
            bytes.add(content);
        }
        var existing = outputs.find(request.attemptId());
        if (existing.isPresent()) {
            var old = existing.orElseThrow();
            if (!old.files().equals(metadata) || !java.util.Objects.equals(old.resultRevision(), request.resultRevision())) {
                throw new IllegalStateException("attempt output is immutable");
            }
            return old;
        }
        Instant now = clock.instant();
        try {
            Path root = workspaces.location(task.repositoryRef()).workspaceRoot().toRealPath();
            Path parent = root.resolve(".aiw/plans").resolve(segment(task.subjectId()))
                    .resolve("tasks").resolve(segment(taskId));
            directory(root, parent);
            String datetime = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH-mm-ss.SSS'Z'")
                    .withZone(java.time.ZoneOffset.UTC).format(now);
            String name = datetime + "_" + segment(request.attemptId());
            Path target = parent.resolve(name);
            Path staging = Files.createTempDirectory(parent, ".staging-");
            privateDirectory(staging);
            var bundle = new TaskOutputBundle(request.attemptId(), taskId, task.repositoryRef(), task.baseRevision(), request.resultRevision(),
                    target.toString(), now.toEpochMilli(), "REGISTERED",
                    (task.skillRef() != null && Set.of("workflow-stage-reviewer", "software-code-reviewer", "software-test-verifier").contains(task.skillRef()))
                            || Set.of("CODE_REVIEW", "VERIFICATION", "INTEGRATION_VERIFICATION").contains(task.type().name())
                            ? "NOT_REQUIRED" : "PENDING", List.copyOf(metadata));
            for (int i = 0; i < metadata.size(); i++) {
                Path file = staging.resolve(metadata.get(i).path());
                directory(staging, file.getParent());
                Files.write(file, bytes.get(i));
                privateFile(file);
            }
            Files.writeString(staging.resolve("manifest.json"), encode(bundle));
            privateFile(staging.resolve("manifest.json"));
            Files.move(staging, target, StandardCopyOption.ATOMIC_MOVE);
            outputs.insert(bundle, contents);
            return bundle;
        } catch (IOException error) { throw new IllegalStateException("output archive write failed", error); }
    }

    /** Older Workers still get report archiving without adding fields to their complete request. */
    @Transactional
    public void ensureReport(String taskId, String workerId, String token,
                             me.asu.aidev.execution.domain.CompletionReport report,
                             me.asu.aidev.execution.domain.WorkspaceResult result) {
        var task = tasks.find(new TaskId(taskId)).orElseThrow();
        if (task.repositoryRef() == null) return;
        String attemptId = attempts.findAttemptId(new LeaseToken(token)).orElseThrow().value();
        var existing = outputs.find(attemptId);
        if (existing.isPresent()) {
            var bundle = existing.orElseThrow();
            var saved = bundle.files().stream().filter(f -> f.path().equals("report.md")).findFirst().orElseThrow();
            if (!saved.sha256().equals(hash(report == null ? "Completion metadata only. The Worker supplied no report." : report.summary()))
                    || !java.util.Objects.equals(bundle.resultRevision(), result == null ? null : result.resultRevision())) {
                throw new IllegalStateException("completion report differs from registered output");
            }
            return;
        }
        register(taskId, new Registration(workerId, token, attemptId, report == null ? "NO_REPORT" : report.verdict().name(),
                report == null ? "Completion metadata only. The Worker supplied no report." : report.summary(),
                result == null ? null : result.resultRevision(), List.of()));
    }

    @Transactional
    public List<JdbcTaskOutputRepository.StoredFile> read(String taskId, String workerId, String token,
                                                          String attemptId, String bundleId) {
        lease(taskId, workerId, token, attemptId);
        if (!outputs.pinned(attemptId, bundleId)) {
            throw new IllegalStateException("output bundle is not an authoritative task input");
        }
        var bundle = outputs.find(bundleId).orElseThrow();
        if (!"COMPLETED".equals(bundle.executionState())) throw new IllegalStateException("input attempt is not completed");
        return ref(bundle).files().stream().map(file -> outputs.file(file.id())).toList();
    }

    public List<TaskOutputBundle> list(String taskId) { return outputs.forTask(taskId); }
    public JdbcTaskOutputRepository.StoredFile file(String fileId) { return outputs.file(fileId); }

    @Transactional
    public void observe(String leaseToken) {
        attempts.findAttemptId(new LeaseToken(leaseToken)).ifPresent(id -> {
            outputs.observe(id.value()); outputs.recordReviews(id.value());
        });
    }

    @Transactional
    public WorkflowPlan publication(String fileId, String reviewTaskId, String targetPath, String reason, String actor) {
        validatePath(targetPath);
        if (targetPath.startsWith(".aiw/") || !targetPath.endsWith(".md")) throw new IllegalArgumentException("publication target must be a Markdown project document");
        if (reason == null || reason.isBlank() || reason.length() > 2000) throw new IllegalArgumentException("publication reason required");
        var source = outputs.file(fileId);
        var bundle = outputs.find(source.attemptId()).orElseThrow();
        if (!"COMPLETED".equals(bundle.executionState()) || !Set.of("REPORT", "DOCUMENT").contains(source.file().kind())
                || !source.file().path().endsWith(".md") || !outputs.reviewed(bundle.taskId(), bundle.attemptId(), reviewTaskId)) {
            throw new IllegalStateException("document requires a completed source and a dependent PASS review");
        }
        var instruction = "Publish the reviewed document from the authoritative output input. Source file ID: " + fileId
                + ". SHA-256: " + source.file().sha256() + ". Target path: " + targetPath
                + ". Preserve the document content exactly. Verify its hash before writing. Do not modify other files. "
                + "Report the source ID, hash, target path and local result revision when a Git baseline is assigned. "
                + "Do not push. Reason: " + reason;
        var step = new WorkflowPlanSpec.Step("publish-document", Set.of("coding", "task-outputs-v1"), Set.of(),
                "coding-work-package-executor", instruction, bundle.repositoryRef(),
                bundle.resultRevision() == null ? bundle.baseRevision() : bundle.resultRevision(), Set.of());
        var plan = plans.draft(new WorkflowPlanSpec("Publish reviewed document to " + targetPath,
                WorkflowPlanSpec.Source.MANAGER, List.of(step)), actor);
        outputs.publication(plan.id(), fileId, reviewTaskId, targetPath, actor, reason, clock.millis());
        return plan;
    }

    private static Upload text(String path, String kind, String text) {
        return new Upload(path, kind, Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8)));
    }

    private String encode(Object value) {
        try { return json.writeValueAsString(value); }
        catch (com.fasterxml.jackson.core.JsonProcessingException error) { throw new IllegalStateException("output serialization failed", error); }
    }

    public static void validatePath(String value) {
        if (value == null || value.length() > 480 || !value.matches("[\\p{L}\\p{N}\\p{M} ._/-]+") || value.startsWith("/")) throw new IllegalArgumentException("invalid output path");
        for (String part : value.split("/", -1)) {
            if (part.isEmpty() || !part.equals(part.strip()) || part.endsWith(".")
                    || part.getBytes(StandardCharsets.UTF_8).length > 255
                    || Set.of(".", "..", ".git", ".aiw").contains(part.toLowerCase(java.util.Locale.ROOT))) throw new IllegalArgumentException("unsafe output path");
        }
    }

    private static String segment(String value) {
        if (value == null || value.isBlank()) value = "standalone";
        String readable = value.replaceAll("[^A-Za-z0-9_~-]", "-");
        return readable.substring(0, Math.min(readable.length(), 100)) + "_" + hash(value).substring(0, 12);
    }

    private static void directory(Path root, Path target) throws IOException {
        Path cursor = root;
        for (Path part : root.relativize(target)) {
            cursor = cursor.resolve(part);
            if (!Files.exists(cursor, LinkOption.NOFOLLOW_LINKS)) {
                try { Files.createDirectory(cursor); privateDirectory(cursor); }
                catch (java.nio.file.FileAlreadyExistsException raced) { /* Validate below. */ }
            }
            if (!Files.isDirectory(cursor, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(cursor)) throw new IOException("unsafe archive directory");
        }
    }
    private static void privateDirectory(Path path) throws IOException {
        if (Files.getFileStore(path).supportsFileAttributeView("posix")) Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rwx------"));
    }
    private static void privateFile(Path path) throws IOException {
        if (Files.getFileStore(path).supportsFileAttributeView("posix")) Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------"));
    }
    private static String hash(String value) { return hash(value.getBytes(StandardCharsets.UTF_8)); }
    private static String hash(byte[] value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value)); }
        catch (java.security.NoSuchAlgorithmException error) { throw new IllegalStateException(error); }
    }
}
