package me.asu.aidev.workflow.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import me.asu.aidev.output.domain.TaskOutputBundle;
import me.asu.aidev.output.jdbc.JdbcTaskOutputRepository;
import me.asu.aidev.work.domain.TaskState;
import me.asu.aidev.work.domain.WorkTask;
import me.asu.aidev.work.port.WorkTaskRepository;
import me.asu.aidev.workflow.domain.WorkflowPlan;
import me.asu.aidev.workflow.domain.WorkflowTaskPosition;
import me.asu.aidev.workflow.jdbc.JdbcWorkflowControlRepository;
import me.asu.aidev.workflow.jdbc.JdbcWorkflowHumanOutcomeRepository;
import me.asu.aidev.workflow.jdbc.JdbcWorkflowPlanArchiveRepository;
import me.asu.aidev.workflow.jdbc.JdbcWorkflowQuestionRepository;
import me.asu.aidev.workflow.jdbc.JdbcWorkflowQuestionRepository.Answer;
import me.asu.aidev.workflow.jdbc.JdbcWorkflowRepairAuthorizationRepository;
import me.asu.aidev.workflow.port.WorkflowPlanRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Source artifacts propose questions; only a separately confirmed manager answer is authority. */
@Service
public class WorkflowQuestionService {
    private me.asu.aidev.secretary.application.SecretaryPlanGate secretaryGate;
    @org.springframework.beans.factory.annotation.Autowired
    public void setSecretaryGate(me.asu.aidev.secretary.application.SecretaryPlanGate gate) { this.secretaryGate=gate; }
    public record Document(String taskId, String attemptId, String reviewState, TaskOutputBundle.File file) {}
    public record Question(String key, String id, String question, String impact, String sourceState,
            List<String> affects, String context, Document source, Answer decision) {}
    public record Board(long planVersion, String blockedReason, List<Question> questions,
            List<Document> documents, List<Answer> history, List<String> warnings) {}
    public record Preview(String path, String content, boolean truncated) {}
    private static final String START = "\n[AIW_CONFIRMED_DECISIONS]\n";
    private static final String END = "\n[/AIW_CONFIRMED_DECISIONS]\n";
    private final WorkflowPlanRepository plans;
    private final WorkTaskRepository tasks;
    private final JdbcTaskOutputRepository outputs;
    private final JdbcWorkflowQuestionRepository decisions;
    private final JdbcWorkflowPlanArchiveRepository archives;
    private final JdbcWorkflowHumanOutcomeRepository outcomes;
    private final JdbcWorkflowControlRepository controls;
    private final JdbcWorkflowRepairAuthorizationRepository repairs;
    private final ObjectMapper json = new ObjectMapper();

    public WorkflowQuestionService(WorkflowPlanRepository plans, WorkTaskRepository tasks, JdbcTaskOutputRepository outputs,
            JdbcWorkflowQuestionRepository decisions, JdbcWorkflowPlanArchiveRepository archives,
            JdbcWorkflowHumanOutcomeRepository outcomes, JdbcWorkflowControlRepository controls,
            JdbcWorkflowRepairAuthorizationRepository repairs) {
        this.plans = plans; this.tasks = tasks; this.outputs = outputs; this.decisions = decisions;
        this.archives = archives; this.outcomes = outcomes; this.controls = controls; this.repairs = repairs;
    }

    @Transactional(readOnly = true)
    public Board board(String id) {
        var plan = plan(id);
        var current = tasks.findBySubject(WorkflowPlanService.SUBJECT, id);
        var history = decisions.history(id);
        var latest = new LinkedHashMap<String, WorkTask>();
        for (var task : current) {
            if (task.state() != TaskState.COMPLETED) continue;
            var position = WorkflowTaskPosition.parse(plan, task.id());
            if (position.isEmpty()) continue;
            var p = position.orElseThrow();
            String slot = p.step() + ":" + p.review();
            var previous = latest.get(slot);
            if (previous == null || rank(plan, task) > rank(plan, previous)) latest.put(slot, task);
        }
        var documents = new ArrayList<Document>();
        var questions = new LinkedHashMap<String, Question>();
        var warnings = new ArrayList<String>();
        for (var task : latest.values()) {
            outputs.completed(task.id().value()).ifPresent(bundle -> {
                for (var file : bundle.files()) {
                    var document = new Document(task.id().value(), bundle.attemptId(), bundle.reviewState(), file);
                    documents.add(document);
                    if (WorkflowTaskPosition.parse(plan, task.id()).orElseThrow().review() || !file.path().endsWith(".json")) continue;
                    if (file.bytes() > 1_048_576) { warnings.add("Question extraction skipped large file: " + file.path()); continue; }
                    try {
                        var root = json.readTree(Base64.getDecoder().decode(outputs.file(file.id()).contentBase64()));
                        if (root == null || !root.isObject()) continue;
                        var payload = root.has("payload") ? root.path("payload") : root;
                        var pending = payload.path("openQuestions");
                        if (!pending.isArray()) continue;
                        for (var item : pending) {
                            String questionId = text(item, "id"), question = text(item, "question");
                            if (questionId.isBlank() || questionId.length() > 160 || question.isBlank() || question.length() > 4000) continue;
                            if (questions.size() >= 256) { warnings.add("Question limit reached; inspect the source files for more questions."); break; }
                            String key = hash(task.id().value() + "\n" + questionId);
                            var answer = history.stream().filter(a -> a.questionKey().equals(key)).findFirst()
                                    .filter(a -> a.sourceFileId().equals(file.id()) && a.sourceHash().equals(file.sha256())).orElse(null);
                            questions.putIfAbsent(key, new Question(key, questionId, question, text(item, "impact"), text(item, "state"),
                                    strings(item.path("affects")), text(item, "temporaryAssumption") + "\n" + text(item, "implementationGuardrail"), document, answer));
                        }
                    } catch (java.io.IOException | IllegalArgumentException error) {
                        warnings.add("Cannot extract questions from: " + file.path());
                    }
                }
            });
        }
        return new Board(plan.version(), blocked(plan, current), List.copyOf(questions.values()),
                List.copyOf(documents), history, List.copyOf(warnings));
    }

    @Transactional
    public Answer answer(String id, long version, String key, String fileId, String sourceHash,
            String answer, boolean confirmed, String actor) {
        if(secretaryGate!=null) secretaryGate.serializePlan(id);
        if (!confirmed) throw new IllegalArgumentException("Confirm this answer before saving.");
        if (answer == null || answer.isBlank() || answer.length() > 2000 || answer.contains("[AIW_CONFIRMED_DECISIONS]") || answer.contains("[/AIW_CONFIRMED_DECISIONS]")) throw new IllegalArgumentException("Answer must contain 1 to 2000 characters.");
        if (actor == null || actor.isBlank() || actor.length() > 256) throw new IllegalArgumentException("Decision actor is required.");
        // Lock tasks in the same order as plan controls, then CAS the plan version.
        var current = tasks.findBySubject(WorkflowPlanService.SUBJECT, id).stream().sorted(Comparator.comparing(t -> t.id().value())).toList();
        for (var task : current) if (!tasks.lock(task.id())) throw new IllegalStateException("Plan tasks changed. Refresh before answering.");
        var board = board(id);
        if (board.planVersion() != version) throw new IllegalStateException("Plan changed. Refresh before answering.");
        if (board.blockedReason() != null) throw new IllegalStateException(board.blockedReason());
        var question = board.questions().stream().filter(q -> q.key().equals(key)
                && q.source().file().id().equals(fileId) && q.source().file().sha256().equals(sourceHash)).findFirst()
                .orElseThrow(() -> new IllegalStateException("Question source changed. Refresh before answering."));
        var now = Instant.now();
        var decision = new Answer("QD-" + UUID.randomUUID(), id, key, question.source().taskId(), fileId,
                sourceHash, question.id(), question.question(), answer.strip(), actor, version + 1, now);
        var revised = board.questions().stream().map(q -> q.key().equals(key)
                ? new Question(q.key(), q.id(), q.question(), q.impact(), q.sourceState(), q.affects(), q.context(), q.source(), decision) : q).toList();
        renderContext(revised); // Refuse oversized authority instead of silently dropping answers from task input.
        if (!plans.transition(id, version, WorkflowPlan.State.CONFIRMED, WorkflowPlan.State.CONFIRMED,
                actor, "Question decision: " + question.id(), now)) throw new IllegalStateException("Plan changed. Refresh before answering.");
        decisions.insert(decision);
        return decision;
    }

    @Transactional(readOnly = true)
    public Preview preview(String id, String fileId) {
        var document = board(id).documents().stream().filter(d -> d.file().id().equals(fileId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("File is not a current output of this plan."));
        String content = new String(Base64.getDecoder().decode(outputs.file(fileId).contentBase64()), StandardCharsets.UTF_8);
        boolean truncated = content.length() > 65_536;
        return new Preview(document.file().path(), truncated ? content.substring(0, 65_536) : content, truncated);
    }

    /** Embed an immutable textual snapshot when a new task or instruction revision is created. */
    public String withContext(String instruction, String planId) {
        String context = renderContext(board(planId).questions());
        int start = instruction.indexOf(START);
        if (start >= 0) {
            int end = instruction.indexOf(END, start);
            if (end >= 0) return instruction.substring(0, start) + context + instruction.substring(end + END.length());
        }
        return instruction + context;
    }

    private String renderContext(List<Question> questions) {
        var text = new StringBuilder();
        for (var question : questions) {
            var decision = question.decision();
            if (decision == null) continue;
            text.append("\nDecision ").append(decision.id()).append("; question ").append(question.id())
                .append("; source task ").append(decision.sourceTaskId()).append("; source file ").append(decision.sourceFileId())
                .append("; SHA-256 ").append(decision.sourceHash()).append("; confirmed by ").append(decision.actor())
                .append(" at ").append(decision.decidedAt()).append("\nQuestion: ").append(question.question())
                .append("\nConfirmed answer: ").append(decision.answer()).append('\n');
        }
        if (text.isEmpty()) return "";
        if (text.length() > 8000) throw new IllegalStateException("Confirmed decisions exceed the task context budget. Split this plan before adding more answers.");
        return START + "Use these confirmed manager answers for the listed questions instead of UNKNOWN states or temporary assumptions in older source artifacts. "
                + "Other acceptance criteria remain unchanged. These answers do not grant execution, test, network or deployment permissions.\n" + text + END;
    }

    private String blocked(WorkflowPlan plan, List<WorkTask> current) {
        if (plan.state() != WorkflowPlan.State.CONFIRMED || archives.archived(plan.id()) || outcomes.find(plan.id()).isPresent()) return "This plan is not open for decisions.";
        if (controls.pending(plan.id()).isPresent()) return "Resolve the pending plan control before answering.";
        if (current.stream().anyMatch(t -> !Set.of(TaskState.COMPLETED, TaskState.CANCELLED, TaskState.FAILED).contains(t.state()))) return "Wait for pending or running tasks to finish before confirming answers.";
        if (repairs.list(plan.id()).stream().anyMatch(a -> a.status().equals("PENDING") && a.planVersion() == plan.version()
                && a.expiresAt().isAfter(Instant.now()))) return "Revoke or finish the pending repair authorization before changing answers.";
        return null;
    }
    private WorkflowPlan plan(String id) { return plans.find(id).orElseThrow(() -> new WorkflowPlanService.PlanNotFound(id)); }
    private long rank(WorkflowPlan plan, WorkTask task) { var p = WorkflowTaskPosition.parse(plan, task.id()).orElseThrow(); return p.round() * 1_000_000_000L + p.resume(); }
    private static String text(JsonNode node, String key) { return node.path(key).isTextual() ? node.path(key).textValue() : ""; }
    private static List<String> strings(JsonNode node) { var values = new ArrayList<String>(); if (node.isArray()) node.forEach(value -> { if (value.isTextual()) values.add(value.textValue()); }); return List.copyOf(values); }
    private static String hash(String value) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); } catch (java.security.NoSuchAlgorithmException error) { throw new IllegalStateException(error); } }
}
