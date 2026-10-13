package me.asu.aidev.workflow.application;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import me.asu.aidev.work.application.CreateWorkTaskCommand;
import me.asu.aidev.work.application.WorkTaskService;
import me.asu.aidev.work.domain.TaskId;
import me.asu.aidev.work.domain.TaskPriority;
import me.asu.aidev.work.domain.TaskState;
import me.asu.aidev.work.domain.TaskType;
import me.asu.aidev.work.domain.WorkTask;
import me.asu.aidev.work.port.WorkTaskRepository;
import me.asu.aidev.execution.port.ExecutionAttemptRepository;
import me.asu.aidev.execution.domain.CompletionVerdict;
import me.asu.aidev.workflow.domain.WorkflowPlan;
import me.asu.aidev.workflow.domain.WorkflowPlanSpec;
import me.asu.aidev.workflow.port.WorkflowPlanRepository;
import me.asu.aidev.workflow.jdbc.JdbcWorkflowControlRepository;
import me.asu.aidev.workflow.jdbc.JdbcWorkflowControlRepository.Control;
import me.asu.aidev.workflow.jdbc.JdbcWorkflowHumanOutcomeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkflowPlanService {
    public static final String SUBJECT = "flexible-plan";
    private final WorkflowPlanRepository plans;
    private final WorkTaskService taskService;
    private final WorkTaskRepository tasks;
    private final ExecutionAttemptRepository attempts;
    private final JdbcWorkflowControlRepository controls;
    private final JdbcWorkflowHumanOutcomeRepository outcomes;

    public WorkflowPlanService(WorkflowPlanRepository plans, WorkTaskService taskService, WorkTaskRepository tasks,
                               ExecutionAttemptRepository attempts, JdbcWorkflowControlRepository controls,
                               JdbcWorkflowHumanOutcomeRepository outcomes) {
        this.plans = plans;
        this.taskService = taskService;
        this.tasks = tasks;
        this.attempts = attempts;
        this.controls = controls;
        this.outcomes = outcomes;
    }

    @Transactional
    public WorkflowPlan draft(WorkflowPlanSpec spec, String actor) {
        if (spec == null) throw new IllegalArgumentException("plan specification is required");
        WorkflowEditorService.validateSpec(spec, false);
        actor = actor(actor);
        Instant now = Instant.now();
        WorkflowPlan plan = new WorkflowPlan("FP-" + UUID.randomUUID(), WorkflowPlan.State.DRAFT, 0,
                spec, actor, actor, "", now, now);
        plans.insert(plan);
        return plan;
    }

    @Transactional
    public WorkflowPlan copy(String id, String actor) { return draft(get(id).spec(), actor); }

    @Transactional(readOnly = true)
    public WorkflowPlan get(String id) {
        return plans.find(id).orElseThrow(() -> new PlanNotFound(id));
    }

    @Transactional(readOnly = true)
    public List<WorkflowPlan> list() { return plans.list(); }

    @Transactional(readOnly = true)
    public List<WorkflowPlan> list(boolean archived) { return plans.list(archived); }

    @Transactional(readOnly = true)
    public List<WorkTask> steps(String id) {
        get(id);
        return tasks.findBySubject(SUBJECT, id);
    }

    @Transactional(readOnly = true)
    public List<Control> controlHistory(String id) {
        get(id);
        return controls.history(id);
    }

    @Transactional
    public WorkflowPlan jump(String id, long version, int targetStep, String actor, String reason) {
        return control(id, version, targetStep, actor, reason, false);
    }

    @Transactional
    public WorkflowPlan stop(String id, long version, String actor, String reason) {
        return control(id, version, null, actor, reason, false);
    }

    @Transactional
    public WorkflowPlan complete(String id, long version, String actor, String reason) {
        return control(id, version, null, actor, reason, true);
    }

    private WorkflowPlan control(String id, long version, Integer targetStep, String actor, String reason,
                                 boolean complete) {
        actor = actor(actor);
        if (reason == null || reason.isBlank() || reason.length() > 2000) {
            throw new IllegalArgumentException("control reason must contain 1 to 2000 characters");
        }
        WorkflowPlan plan = get(id);
        if (plan.state() != WorkflowPlan.State.CONFIRMED || plan.version() != version) {
            throw new IllegalStateException("plan state or version changed; reload before controlling it");
        }
        int finalStep = plan.spec().steps().size() - 1;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS_PER_STEP; attempt++) {
            if (attempts.findCompletionReport(reviewId(plan, finalStep, attempt))
                    .map(report -> report.verdict() == CompletionVerdict.PASS).orElse(false)) {
                throw new IllegalStateException("plan has already completed its final accepted review");
            }
        }
        var pending = controls.pending(id);
        if (pending.isPresent()) {
            throw new IllegalStateException("a Worker stop acknowledgement or conflict resolution is still pending");
        }
        List<WorkTask> current = lockedTasks(id);
        int fromStep = current.stream().mapToInt(task -> stepIndex(plan, task.id())).max().orElseThrow();
        if (targetStep != null && (targetStep <= fromStep || targetStep >= plan.spec().steps().size())) {
            throw new IllegalArgumentException("target step must be later than the current step and inside the plan");
        }
        List<WorkTask> active = current.stream().filter(task -> task.state() == TaskState.CLAIMED
                || task.state() == TaskState.RUNNING).toList();
        if (active.size() > 1) throw new IllegalStateException("plan has more than one active task");
        Instant now = Instant.now();
        if (!plans.transition(id, version, WorkflowPlan.State.CONFIRMED, WorkflowPlan.State.CONFIRMED,
                actor, reason.strip(), now)) throw new IllegalStateException("plan changed concurrently");
        if (complete) outcomes.insert(id, "COMPLETE", "PENDING", actor, reason.strip(), now);
        WorkTask running = active.isEmpty() ? null : active.getFirst();
        var lease = running == null ? null : tasks.findLease(running.id()).orElseThrow();
        Control decision = new Control(UUID.randomUUID().toString(), id, targetStep == null ? "STOP" : "JUMP",
                fromStep, targetStep, running == null ? null : running.id().value(),
                lease == null ? null : lease.workerId().value(), lease == null ? null : lease.token().value(),
                "PENDING_STOP", actor, reason.strip(), now, null);
        controls.insert(decision);
        if (running != null) {
            if (!attempts.requestStop(lease.token(), lease.workerId(), now)) {
                throw new IllegalStateException("active Worker attempt cannot be asked to stop");
            }
            tasks.recordControlEvent(UUID.randomUUID().toString(), running.id(),
                    attempts.findAttemptId(lease.token()).orElseThrow().value(),
                    "STOP_REQUESTED", actor, reason.strip(), false, now);
        } else applyControl(decision, plan);
        return get(id);
    }

    /** Runs in the Worker acknowledgement transaction after the lease has been fenced. */
    public void afterStopAcknowledged(TaskId taskId, String workerId, String leaseToken) {
        WorkTask task = tasks.find(taskId).orElseThrow();
        if (!SUBJECT.equals(task.subjectType())) return;
        Control decision = controls.pending(task.subjectId()).orElse(null);
        if (decision == null) return;
        if (!taskId.value().equals(decision.taskId()) || !workerId.equals(decision.leaseWorker())
                || !leaseToken.equals(decision.leaseToken())) {
            throw new IllegalStateException("workflow stop acknowledgement belongs to another lease");
        }
        if (task.state() != TaskState.READY || tasks.findLease(taskId).isPresent()) {
            throw new IllegalStateException("workflow task was not released after stop acknowledgement");
        }
        applyControl(decision, get(task.subjectId()));
    }

    public void conflictPending(TaskId taskId) {
        WorkTask task = tasks.find(taskId).orElse(null);
        if (task == null || !SUBJECT.equals(task.subjectType())) return;
        controls.pending(task.subjectId()).filter(p -> taskId.value().equals(p.taskId()))
                .ifPresent(p -> {
                    controls.finish(p.id(), "CONFLICT", Instant.now());
                    outcomes.clearPending(task.subjectId());
                });
    }

    @Transactional
    public WorkflowPlan clearStaleControl(String id, long version, String actor) {
        actor = actor(actor);
        WorkflowPlan plan = get(id);
        if (plan.state() != WorkflowPlan.State.CONFIRMED || plan.version() != version) {
            throw new IllegalStateException("plan state or version changed; reload before resolving control");
        }
        Control decision = controls.pending(id).orElseThrow(() -> new IllegalStateException("no pending control"));
        WorkTask source = tasks.find(new TaskId(decision.taskId())).orElseThrow();
        if (!tasks.lock(source.id())) throw new IllegalStateException("source task disappeared");
        source = tasks.find(source.id()).orElseThrow();
        if (source.state() == TaskState.CLAIMED || source.state() == TaskState.RUNNING) {
            throw new IllegalStateException("Worker lease is still active; wait for acknowledgement");
        }
        Instant now = Instant.now();
        if (!plans.transition(id, version, WorkflowPlan.State.CONFIRMED, WorkflowPlan.State.CONFIRMED,
                actor, "Pending stop ended without Worker acknowledgement", now)) {
            throw new IllegalStateException("plan changed concurrently");
        }
        controls.finish(decision.id(), "CONFLICT", now);
        outcomes.clearPending(id);
        return get(id);
    }

    private void applyControl(Control decision, WorkflowPlan plan) {
        if (plan.state() != WorkflowPlan.State.CONFIRMED) throw new IllegalStateException("plan is no longer active");
        Instant now = Instant.now();
        for (WorkTask task : lockedTasks(plan.id())) {
            WorkTask latest = tasks.find(task.id()).orElseThrow();
            if (latest.state() == TaskState.CLAIMED || latest.state() == TaskState.RUNNING) {
                throw new IllegalStateException("Worker stop acknowledgement is still required");
            }
            if (latest.state() == TaskState.COMPLETED || latest.state() == TaskState.CANCELLED) continue;
            if (!tasks.cancelInactive(latest.id(), latest.state(), now)) {
                throw new IllegalStateException("plan task changed while applying control");
            }
            tasks.recordControlEvent(UUID.randomUUID().toString(), latest.id(), null,
                    decision.action().equals("STOP") ? "PLAN_STOPPED" : "PLAN_STEP_SKIPPED",
                    decision.actor(), decision.reason(), false, now);
        }
        if (decision.targetStep() == null) {
            WorkflowPlan latest = get(plan.id());
            if (!plans.transition(plan.id(), latest.version(), WorkflowPlan.State.CONFIRMED,
                    WorkflowPlan.State.STOPPED, decision.actor(), decision.reason(), now)) {
                throw new IllegalStateException("plan changed before stop was applied");
            }
            if (outcomes.find(plan.id()).filter(outcome -> outcome.outcome().equals("COMPLETE")
                    && outcome.status().equals("PENDING")).isPresent()) outcomes.apply(plan.id());
        } else {
            for (int step = decision.fromStep(); step < decision.targetStep(); step++) {
                if (!controls.skipped(plan.id(), step)) controls.skip(plan.id(), step, decision.id());
            }
            createExecution(plan, decision.targetStep(), 1, acceptedInputs(plan, decision.targetStep() - 1));
        }
        controls.finish(decision.id(), "APPLIED", now);
    }

    private List<WorkTask> lockedTasks(String id) {
        List<TaskId> ids = tasks.findBySubject(SUBJECT, id).stream().map(WorkTask::id)
                .sorted(Comparator.comparing(TaskId::value)).toList();
        for (TaskId task : ids) if (!tasks.lock(task)) throw new IllegalStateException("plan task disappeared");
        return ids.stream().map(taskId -> tasks.find(taskId).orElseThrow()).toList();
    }

    private static int stepIndex(WorkflowPlan plan, TaskId taskId) {
        for (int step = 0; step < plan.spec().steps().size(); step++) {
            if (taskId.value().startsWith(plan.id() + "~" + plan.spec().steps().get(step).key() + "~")) return step;
        }
        throw new IllegalStateException("task does not match any plan step: " + taskId.value());
    }

    @Transactional
    public WorkflowPlan confirm(String id, long version, String actor) {
        actor = actor(actor);
        WorkflowPlan plan = get(id);
        WorkflowEditorService.validateSpec(plan.spec(), false);
        // An immutable confirmed plan can acknowledge a retry of its original confirmation.
        if (plan.state() == WorkflowPlan.State.CONFIRMED && version >= 0
                && (plan.version() == version || plan.version() - 1 == version)) return plan;
        if (plan.state() != WorkflowPlan.State.DRAFT || plan.version() != version) {
            throw new IllegalStateException("plan state or version changed; reload before confirming");
        }
        if (!plans.transition(id, version, WorkflowPlan.State.DRAFT, WorkflowPlan.State.CONFIRMED,
                actor, "Manager confirmed selected steps", Instant.now())) {
            throw new IllegalStateException("plan changed concurrently; reload before confirming");
        }
        createExecution(plan, 0, 1, Set.of());
        return get(id);
    }

    static final int MAX_ATTEMPTS_PER_STEP = 3;

    static TaskId executionId(WorkflowPlan plan, int step, int attempt) {
        return new TaskId(plan.id() + "~" + plan.spec().steps().get(step).key() + "~work-" + attempt);
    }

    static TaskId reviewId(WorkflowPlan plan, int step, int attempt) {
        return new TaskId(plan.id() + "~" + plan.spec().steps().get(step).key() + "~review-" + attempt);
    }

    void createExecution(WorkflowPlan plan, int stepIndex, int attempt, Set<TaskId> dependencies) {
        var step = plan.spec().steps().get(stepIndex);
        String revision = revisionFor(plan, stepIndex, attempt);
        TaskId id = executionId(plan, stepIndex, attempt);
        String feedback = "";
        if (attempt > 1) {
            var review = attempts.findCompletionReport(reviewId(plan, stepIndex, attempt - 1))
                    .orElseThrow(() -> new IllegalStateException("prior review is missing"));
            if (review.verdict() != CompletionVerdict.REPAIR_REQUIRED) {
                throw new IllegalStateException("prior review did not request repair");
            }
            feedback = "\nPrevious independent review (attempt " + (attempt - 1) + "):\n"
                    + review.summary() + "\nAddress these findings and explain the changes.";
        }
        taskService.createIfAbsent(new CreateWorkTaskCommand(id, TaskType.EXTERNAL_WORK, SUBJECT, plan.id(),
                step.requiredSkills(), step.skillRef(),
                "Goal: " + plan.spec().goal() + "\nStep: " + step.key()
                        + "\nExecute this step and address its acceptance criteria:\n" + step.instruction() + feedback,
                step.repositoryRef(), revision, TaskPriority.NORMAL, dependencies));
        plans.savePreferences(id, step.preferredSkills());
    }

    private String revisionFor(WorkflowPlan plan, int stepIndex, int attempt) {
        var step = plan.spec().steps().get(stepIndex);
        if (!"coding-work-package-executor".equals(step.skillRef())) return step.baseRevision();
        if (attempt > 1) {
            var prior = attempts.findCompletedInput(executionId(plan, stepIndex, attempt - 1));
            if (prior.isPresent() && prior.orElseThrow().resultRevision() != null) {
                return prior.orElseThrow().resultRevision();
            }
        }
        for (int priorStep = stepIndex - 1; priorStep >= 0; priorStep--) {
            for (int priorAttempt = MAX_ATTEMPTS_PER_STEP; priorAttempt >= 1; priorAttempt--) {
                TaskId priorReview = reviewId(plan, priorStep, priorAttempt);
                if (!attempts.findCompletionReport(priorReview)
                        .map(report -> report.verdict() == CompletionVerdict.PASS).orElse(false)) continue;
                var input = attempts.findCompletedInput(executionId(plan, priorStep, priorAttempt));
                if (input.isPresent() && step.repositoryRef() != null
                        && step.repositoryRef().equals(input.orElseThrow().repositoryRef())
                        && input.orElseThrow().resultRevision() != null) return input.orElseThrow().resultRevision();
            }
        }
        return step.baseRevision();
    }

    void createReview(WorkflowPlan plan, int stepIndex, int attempt) {
        var step = plan.spec().steps().get(stepIndex);
        TaskId execution = executionId(plan, stepIndex, attempt);
        var output = attempts.findCompletedInput(execution)
                .orElseThrow(() -> new IllegalStateException("completed workflow output is missing: " + execution.value()));
        TaskId id = reviewId(plan, stepIndex, attempt);
        var reviewSkills = new java.util.LinkedHashSet<String>();
        reviewSkills.add("workflow-review");
        if ("coding-work-package-executor".equals(step.skillRef())) {
            step.requiredSkills().stream().filter(skill -> !Set.of("coding", "integration", "repair").contains(skill))
                    .forEach(reviewSkills::add);
        }
        taskService.createIfAbsent(new CreateWorkTaskCommand(id, TaskType.EXTERNAL_WORK, SUBJECT, plan.id(),
                Set.copyOf(reviewSkills), "workflow-stage-reviewer",
                "Independently review the authoritative output of step '" + step.key() + "' against this goal and step instruction. "
                        + "Goal: " + plan.spec().goal() + "\nStep instruction and acceptance criteria: " + step.instruction()
                        + "\nReturn concrete findings when correction is needed. End with exactly one line: "
                        + "AIW_VERDICT: PASS or AIW_VERDICT: REPAIR_REQUIRED.",
                step.repositoryRef(), output.resultRevision() == null ? step.baseRevision() : output.resultRevision(),
                TaskPriority.NORMAL, Set.of(execution)));
    }

    Set<TaskId> acceptedInputs(WorkflowPlan plan, int throughStep) {
        var result = new java.util.LinkedHashSet<TaskId>();
        for (int step = 0; step <= throughStep; step++) {
            boolean accepted = false;
            for (int attempt = 1; attempt <= MAX_ATTEMPTS_PER_STEP; attempt++) {
                TaskId review = reviewId(plan, step, attempt);
                if (attempts.findCompletionReport(review).map(report -> report.verdict() == CompletionVerdict.PASS)
                        .orElse(false)) {
                    result.add(executionId(plan, step, attempt));
                    result.add(review);
                    accepted = true;
                    break;
                }
            }
            if (!accepted && !controls.skipped(plan.id(), step)) {
                throw new IllegalStateException("preceding workflow step has no accepted review: " + step);
            }
        }
        return Set.copyOf(result);
    }

    @Transactional
    public WorkflowPlan withdraw(String id, long version, String actor, String reason) {
        return withdraw(id, version, actor, reason, false);
    }

    WorkflowPlan withdrawAfterRollback(String id, long version, String actor, String reason) {
        return withdraw(id, version, actor, reason, true);
    }

    private WorkflowPlan withdraw(String id, long version, String actor, String reason, boolean rollbackComplete) {
        actor = actor(actor);
        if (reason == null || reason.isBlank() || reason.length() > 2000) {
            throw new IllegalArgumentException("withdrawal reason must contain 1 to 2000 characters");
        }
        WorkflowPlan plan = get(id);
        if (!rollbackComplete && tasks.findBySubject(SUBJECT, id).stream().anyMatch(WorkTask::requiresWorkspace)) {
            throw new IllegalStateException("Workspace plan must use cancellation with Git reversal");
        }
        if (controls.pending(id).isPresent()) throw new IllegalStateException("Worker stop acknowledgement is pending");
        if (plan.state() != WorkflowPlan.State.CONFIRMED || plan.version() != version
                || !plans.transition(id, version, WorkflowPlan.State.CONFIRMED, WorkflowPlan.State.WITHDRAWN,
                actor, reason.strip(), Instant.now())) {
            throw new IllegalStateException("plan state or version changed; reload before withdrawing");
        }
        List<TaskId> ids = tasks.findBySubject(SUBJECT, id).stream().map(WorkTask::id)
                .sorted(Comparator.comparing(TaskId::value)).toList();
        for (TaskId task : ids) {
            if (!tasks.lock(task)) throw new IllegalStateException("plan task disappeared");
        }
        List<WorkTask> current = ids.stream().map(taskService::get).toList();
        if (current.stream().anyMatch(t -> t.state() == TaskState.CLAIMED || t.state() == TaskState.RUNNING)) {
            throw new IllegalStateException("active work cannot be withdrawn; wait until workers finish");
        }
        Instant now = Instant.now();
        for (WorkTask task : current) {
            if (task.state() != TaskState.COMPLETED && task.state() != TaskState.CANCELLED) {
                if (!tasks.cancelInactive(task.id(), task.state(), now)) {
                    throw new IllegalStateException("task changed during withdrawal");
                }
                tasks.recordControlEvent(UUID.randomUUID().toString(), task.id(), null,
                        "PLAN_WITHDRAWN", actor, reason.strip(), false, now);
            }
        }
        return get(id);
    }

    private static String actor(String value) {
        if (value == null || value.isBlank() || value.length() > 256) {
            throw new IllegalArgumentException("actor must contain 1 to 256 characters");
        }
        return value.strip();
    }

    public static class PlanNotFound extends RuntimeException {
        public PlanNotFound(String id) { super("workflow plan not found: " + id); }
    }
}
