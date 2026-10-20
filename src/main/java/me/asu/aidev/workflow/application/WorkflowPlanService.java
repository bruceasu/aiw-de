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
import me.asu.aidev.workflow.domain.WorkflowTaskPosition;
import me.asu.aidev.workflow.port.WorkflowPlanRepository;
import me.asu.aidev.workflow.jdbc.JdbcWorkflowControlRepository;
import me.asu.aidev.workflow.jdbc.JdbcWorkflowControlRepository.Control;
import me.asu.aidev.workflow.jdbc.JdbcWorkflowHumanOutcomeRepository;
import me.asu.aidev.workflow.jdbc.JdbcWorkflowPlanArchiveRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkflowPlanService {
    private me.asu.aidev.secretary.application.DelegationBoundary delegation;
    @org.springframework.beans.factory.annotation.Autowired
    public void setDelegationBoundary(me.asu.aidev.secretary.application.DelegationBoundary boundary) { this.delegation=boundary; }
    private void humanChange(String id) { if(delegation!=null) delegation.changing("PLAN:"+id); }
    private me.asu.aidev.secretary.application.SecretaryPlanGate secretaryGate;
    @org.springframework.beans.factory.annotation.Autowired
    public void setSecretaryGate(me.asu.aidev.secretary.application.SecretaryPlanGate gate) { this.secretaryGate=gate; }
    private void secretaryAdmission(String id) { if(secretaryGate!=null) secretaryGate.check(id); }
    private void secretarySerialization(String id) { if(secretaryGate!=null) secretaryGate.serializePlan(id); }
    private WorkflowQuestionService questionDecisions;

    @org.springframework.beans.factory.annotation.Autowired
    public void setQuestionDecisions(WorkflowQuestionService questionDecisions) {
        this.questionDecisions = questionDecisions;
    }

    private String questionContext(String instruction, String planId) {
        return questionDecisions == null ? instruction : questionDecisions.withContext(instruction, planId);
    }
    private me.asu.aidev.output.jdbc.JdbcTaskOutputRepository taskOutputs;

    @org.springframework.beans.factory.annotation.Autowired
    public void setTaskOutputs(me.asu.aidev.output.jdbc.JdbcTaskOutputRepository taskOutputs) {
        this.taskOutputs = taskOutputs;
    }
    public static final String SUBJECT = "flexible-plan";
    private final WorkflowPlanRepository plans;
    private final WorkTaskService taskService;
    private final WorkTaskRepository tasks;
    private final ExecutionAttemptRepository attempts;
    private final JdbcWorkflowControlRepository controls;
    private final JdbcWorkflowHumanOutcomeRepository outcomes;
    private final JdbcWorkflowPlanArchiveRepository archives;

    public record RetryEligibility(long planVersion, boolean allowed, String blockedReason) {}
    public record ReviewRecovery(long planVersion, String taskId, long taskVersion, boolean allowed, String blockedReason) {}

    public WorkflowPlanService(WorkflowPlanRepository plans, WorkTaskService taskService, WorkTaskRepository tasks,
                               ExecutionAttemptRepository attempts, JdbcWorkflowControlRepository controls,
                               JdbcWorkflowHumanOutcomeRepository outcomes,
                               JdbcWorkflowPlanArchiveRepository archives) {
        this.plans = plans;
        this.taskService = taskService;
        this.tasks = tasks;
        this.attempts = attempts;
        this.controls = controls;
        this.outcomes = outcomes;
        this.archives = archives;
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

    @Transactional(readOnly = true)
    public RetryEligibility retryEligibility(String id, String taskId) {
        WorkflowPlan plan = get(id);
        List<WorkTask> current = steps(id);
        WorkTask task = current.stream().filter(row -> row.id().value().equals(taskId)).findFirst()
                .orElseThrow(() -> new PlanNotFound(taskId));
        String blocked = retryBlockedReason(plan, task, current);
        return new RetryEligibility(plan.version(), blocked == null, blocked);
    }

    @Transactional(readOnly = true)
    public ReviewRecovery reviewRecovery(String id) {
        WorkflowPlan plan = get(id);
        List<WorkTask> current = steps(id);
        WorkTask latest = current.stream().max(Comparator
                .comparingLong((WorkTask task) -> taskSequence(plan, task.id()))
                .thenComparingInt(task -> reviewRecoveryNumber(task.id()))).orElse(null);
        String blocked = null;
        if (plan.state() != WorkflowPlan.State.CONFIRMED || archives.archived(id) || outcomes.find(id).isPresent()
                || controls.pending(id).isPresent()) blocked = "This plan cannot be resumed in its current state.";
        else if (current.stream().anyMatch(task -> taskSequence(plan, task.id()) < 0
                || task.state() != TaskState.COMPLETED && task.state() != TaskState.CANCELLED)) {
            blocked = "Finish or retry the current task before requesting a new review.";
        } else if (latest == null || latest.state() != TaskState.COMPLETED
                || !WorkflowTaskPosition.parse(plan, latest.id()).map(position -> position.review()
                        && position.round() >= MAX_ATTEMPTS_PER_STEP).orElse(false)
                || controls.skipped(id, stepIndex(plan, latest.id()))
                || !attempts.findCompletionReport(latest.id())
                        .map(report -> report.verdict() == CompletionVerdict.REPAIR_REQUIRED).orElse(false)) {
            blocked = "Only an exhausted step awaiting corrections can be reviewed again.";
        }
        return new ReviewRecovery(plan.version(), latest == null ? null : latest.id().value(),
                latest == null ? -1 : latest.version(), blocked == null, blocked);
    }

    /** A new review preserves all prior reports and does not rerun accepted execution work. */
    @Transactional
    public WorkflowPlan resumeReview(String id, String taskId, long planVersion, long taskVersion,
                                     String actor, String reason, boolean warningConfirmed) {
        humanChange(id);
        secretaryAdmission(id);
        actor = actor(actor);
        if (reason == null || reason.isBlank() || reason.length() > 2000) {
            throw new IllegalArgumentException("Recovery reason must contain 1 to 2000 characters.");
        }
        if (!warningConfirmed) throw new IllegalArgumentException("Confirm the recovery warning before continuing.");
        get(id);
        lockedTasks(id);
        ReviewRecovery recovery = reviewRecovery(id);
        if (!recovery.allowed()) throw new IllegalStateException(recovery.blockedReason());
        if (recovery.planVersion() != planVersion || recovery.taskVersion() != taskVersion
                || !java.util.Objects.equals(recovery.taskId(), taskId)) {
            throw new IllegalStateException("Plan or task changed. Reload before requesting a new review.");
        }
        WorkflowPlan plan = get(id);
        TaskId previous = new TaskId(taskId);
        int step = stepIndex(plan, previous);
        int round = WorkflowTaskPosition.parse(plan, previous).orElseThrow().round();
        TaskId next = new TaskId(reviewId(plan, step, round).value()
                + "-resume-" + (reviewRecoveryNumber(previous) + 1));
        Instant now = Instant.now();
        String audit = "Review recovery: " + reason.strip();
        if (!plans.transition(id, planVersion, WorkflowPlan.State.CONFIRMED, WorkflowPlan.State.CONFIRMED,
                actor, audit.substring(0, Math.min(2000, audit.length())), now)) {
            throw new IllegalStateException("Plan changed. Reload before requesting a new review.");
        }
        createReview(plan, step, round, next);
        tasks.linkCorrection(previous, next, actor, reason.strip(), now);
        tasks.recordControlEvent(UUID.randomUUID().toString(), next, null, "RESUME_REVIEW", actor, reason.strip(), true, now);
        return get(id);
    }

    @Transactional
    public ReviewRecovery reserveRepair(String id, String taskId, long planVersion, long taskVersion,
                                         String actor, String reason) {
        humanChange(id);
        secretaryAdmission(id);
        actor = actor(actor);
        get(id);
        lockedTasks(id);
        var recovery = reviewRecovery(id);
        if (!recovery.allowed() || recovery.planVersion() != planVersion || recovery.taskVersion() != taskVersion
                || !java.util.Objects.equals(taskId, recovery.taskId())) {
            throw new IllegalStateException("Plan or review changed; reload before authorizing repair.");
        }
        String audit = "One-round repair authorization: " + reason;
        if (!plans.transition(id, planVersion, WorkflowPlan.State.CONFIRMED, WorkflowPlan.State.CONFIRMED,
                actor, audit.substring(0, Math.min(2000, audit.length())), Instant.now())) {
            throw new IllegalStateException("Plan changed while authorizing repair.");
        }
        return reviewRecovery(id);
    }

    @Transactional
    public java.util.Optional<TaskId> continueAuthorizedRepair(String id, String sourceReview, long planVersion,
            long taskVersion, String actor, String reason, String scope) {
        humanChange(id);
        secretaryAdmission(id);
        get(id);
        lockedTasks(id);
        var recovery = reviewRecovery(id);
        if (!recovery.allowed() || recovery.planVersion() != planVersion || recovery.taskVersion() != taskVersion
                || !java.util.Objects.equals(sourceReview, recovery.taskId())) return java.util.Optional.empty();
        WorkflowPlan plan = get(id);
        TaskId previous = new TaskId(sourceReview);
        var position = WorkflowTaskPosition.parse(plan, previous).orElseThrow();
        if (position.round() >= 999_999_999) throw new IllegalStateException("workflow round limit reached");
        var inputs = new java.util.LinkedHashSet<TaskId>(acceptedInputs(plan, position.step() - 1));
        inputs.add(executionId(plan, position.step(), position.round()));
        inputs.add(previous);
        Instant now = Instant.now();
        String audit = "Authorized repair: " + reason;
        if (!plans.transition(id, planVersion, WorkflowPlan.State.CONFIRMED, WorkflowPlan.State.CONFIRMED,
                actor(actor), audit.substring(0, Math.min(2000, audit.length())), now)) return java.util.Optional.empty();
        TaskId next = executionId(plan, position.step(), position.round() + 1);
        createExecution(plan, position.step(), position.round() + 1, Set.copyOf(inputs),
                "\nManager-authorized correction scope:\n" + scope
                + "\nAuthorization permits this one execution and its independent review only. Do not extend the scope or start another round.");
        tasks.linkCorrection(previous, next, actor, reason, now);
        tasks.recordControlEvent(UUID.randomUUID().toString(), next, null, "CONTINUE_REPAIR", actor, reason, true, now);
        return java.util.Optional.of(next);
    }

    @Transactional
    public WorkflowPlan retryFailedTask(String id, String taskId, long planVersion, long taskVersion,
                                        String actor, String reason, boolean warningConfirmed) {
        humanChange(id);
        secretaryAdmission(id);
        actor = actor(actor);
        if (reason == null || reason.isBlank() || reason.length() > 2000) {
            throw new IllegalArgumentException("Retry reason must contain 1 to 2000 characters.");
        }
        if (!warningConfirmed) throw new IllegalArgumentException("Confirm the retry warning before continuing.");
        get(id);
        List<WorkTask> current = lockedTasks(id);
        WorkflowPlan plan = get(id);
        WorkTask task = current.stream().filter(row -> row.id().value().equals(taskId)).findFirst()
                .orElseThrow(() -> new PlanNotFound(taskId));
        if (plan.version() != planVersion || task.version() != taskVersion) {
            throw new IllegalStateException("Plan or task changed. Reload before retrying.");
        }
        String blocked = retryBlockedReason(plan, task, current);
        if (blocked != null) throw new IllegalStateException(blocked);
        int step = stepIndex(plan, task.id());
        acceptedInputs(plan, step - 1);
        Instant now = Instant.now();
        String auditReason = "Retry " + taskId + ": " + reason.strip();
        if (!plans.transition(id, planVersion, WorkflowPlan.State.CONFIRMED, WorkflowPlan.State.CONFIRMED,
                actor, auditReason.substring(0, Math.min(2000, auditReason.length())), now)) {
            throw new IllegalStateException("Plan or task changed. Reload before retrying.");
        }
        String instruction = questionContext(task.instruction(), id);
        if (!instruction.equals(task.instruction()) && !tasks.reviseInstruction(task.id(), TaskState.FAILED,
                task.version(), task.instructionRevision(), instruction, actor, "Apply confirmed question decisions", now)) {
            throw new IllegalStateException("Task instruction changed. Reload before retrying.");
        }
        if (!tasks.retryFailed(task.id(), now)) {
            throw new IllegalStateException("Plan or task changed. Reload before retrying.");
        }
        if(delegation!=null) delegation.allowHumanTask(task.id().value());
        tasks.recordControlEvent(UUID.randomUUID().toString(), task.id(), null,
                "RETRY_FAILED", actor, reason.strip(), true, now);
        return get(id);
    }

    private String retryBlockedReason(WorkflowPlan plan, WorkTask task, List<WorkTask> current) {
        if (plan.state() != WorkflowPlan.State.CONFIRMED || archives.archived(plan.id())
                || outcomes.find(plan.id()).isPresent()) {
            return "This plan has ended or is archived. Its tasks cannot be retried.";
        }
        if (controls.pending(plan.id()).isPresent()) return "Resolve the pending stop request before retrying.";
        if (task.state() != TaskState.FAILED) return "Only a failed task can be retried.";
        long sequence = taskSequence(plan, task.id());
        if (sequence < 0 || controls.skipped(plan.id(), stepIndex(plan, task.id()))
                || current.stream().anyMatch(row -> taskSequence(plan, row.id()) < 0
                        || taskSequence(plan, row.id()) > sequence
                        || taskSequence(plan, row.id()) == sequence
                        && reviewRecoveryNumber(row.id()) > reviewRecoveryNumber(task.id()))) {
            return "Only the current failed task can be retried. This plan has already advanced.";
        }
        if (current.stream().anyMatch(row -> !row.id().equals(task.id())
                && (row.state() == TaskState.READY || row.state() == TaskState.BLOCKED
                || row.state() == TaskState.CLAIMED || row.state() == TaskState.RUNNING))) {
            return "Another task is pending or running. Wait before retrying.";
        }
        int last = plan.spec().steps().size() - 1;
        for (int attempt : rounds(plan, last)) {
            if (attempts.findCompletionReport(effectiveReviewId(plan, last, attempt))
                    .map(report -> report.verdict() == CompletionVerdict.PASS).orElse(false)) {
                return "This plan has already passed its final review.";
            }
        }
        return null;
    }

    private static long taskSequence(WorkflowPlan plan, TaskId id) {
        return WorkflowTaskPosition.parse(plan, id).map(WorkflowTaskPosition::sequence).orElse(-1L);
    }

    List<Integer> rounds(WorkflowPlan plan, int step) {
        return tasks.findBySubject(SUBJECT, plan.id()).stream()
                .map(task -> WorkflowTaskPosition.parse(plan, task.id()))
                .flatMap(java.util.Optional::stream).filter(position -> position.step() == step)
                .map(WorkflowTaskPosition::round).distinct().sorted().toList();
    }

    boolean passed(WorkflowPlan plan, int step) {
        return rounds(plan, step).stream().anyMatch(round -> attempts.findCompletionReport(effectiveReviewId(plan, step, round))
                .map(report -> report.verdict() == CompletionVerdict.PASS).orElse(false));
    }

    String completedRevision(TaskId task, String fallback) {
        var input = attempts.findCompletedInput(task).orElseThrow(() -> new IllegalStateException("Completed source output is missing."));
        return input.resultRevision() == null ? fallback : input.resultRevision();
    }

    @Transactional
    public WorkflowPlan jump(String id, long version, int targetStep, String actor, String reason) {
        humanChange(id);
        secretaryAdmission(id);
        return control(id, version, targetStep, actor, reason, false);
    }

    @Transactional
    public WorkflowPlan stop(String id, long version, String actor, String reason) {
        humanChange(id);
        return control(id, version, null, actor, reason, false);
    }

    @Transactional
    public WorkflowPlan complete(String id, long version, String actor, String reason) {
        humanChange(id);
        return control(id, version, null, actor, reason, true);
    }

    private WorkflowPlan control(String id, long version, Integer targetStep, String actor, String reason,
                                 boolean complete) {
        secretarySerialization(id);
        actor = actor(actor);
        if (reason == null || reason.isBlank() || reason.length() > 2000) {
            throw new IllegalArgumentException("control reason must contain 1 to 2000 characters");
        }
        WorkflowPlan plan = get(id);
        if (plan.state() != WorkflowPlan.State.CONFIRMED || plan.version() != version) {
            throw new IllegalStateException("plan state or version changed; reload before controlling it");
        }
        int finalStep = plan.spec().steps().size() - 1;
        for (int attempt : rounds(plan, finalStep)) {
            if (attempts.findCompletionReport(effectiveReviewId(plan, finalStep, attempt))
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
        // TaskExecutionService acquired the same lock before fencing the task lease.
        secretarySerialization(task.subjectId());
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
        humanChange(id);
        secretarySerialization(id);
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
        humanChange(id);
        if(secretaryGate!=null) secretaryGate.initialConfirmation(id);
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

    private static boolean isReviewRound(TaskId id, TaskId original) {
        return id.equals(original) || id.value().startsWith(original.value() + "-resume-")
                && id.value().substring(original.value().length() + 8).matches("[1-9][0-9]{0,8}");
    }

    private static int reviewRecoveryNumber(TaskId id) {
        var match = java.util.regex.Pattern.compile("~review-[1-9][0-9]{0,8}-resume-([1-9][0-9]{0,8})$").matcher(id.value());
        return match.find() ? Integer.parseInt(match.group(1)) : 0;
    }

    TaskId effectiveReviewId(WorkflowPlan plan, int step, int attempt) {
        TaskId original = reviewId(plan, step, attempt);
        return tasks.findBySubject(SUBJECT, plan.id()).stream().map(WorkTask::id)
                .filter(id -> isReviewRound(id, original)).max(Comparator.comparingInt(WorkflowPlanService::reviewRecoveryNumber))
                .orElse(original);
    }

    void createExecution(WorkflowPlan plan, int stepIndex, int attempt, Set<TaskId> dependencies) {
        createExecution(plan, stepIndex, attempt, dependencies, "");
    }

    private void createExecution(WorkflowPlan plan, int stepIndex, int attempt, Set<TaskId> dependencies, String authorization) {
        secretaryAdmission(plan.id());
        var step = plan.spec().steps().get(stepIndex);
        String revision = revisionFor(plan, stepIndex, attempt);
        TaskId id = executionId(plan, stepIndex, attempt);
        String feedback = "";
        if (attempt > 1) {
            var review = attempts.findCompletionReport(effectiveReviewId(plan, stepIndex, attempt - 1))
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
                        + "\nExecute this step and address its acceptance criteria:\n" + questionContext(step.instruction(), plan.id()) + feedback + authorization,
                step.repositoryRef(), revision, TaskPriority.NORMAL, dependencies));
        plans.savePreferences(id, step.preferredSkills());
        if(delegation!=null) delegation.allowHumanTask(id.value());
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
            for (int priorAttempt : rounds(plan, priorStep).reversed()) {
                TaskId priorReview = effectiveReviewId(plan, priorStep, priorAttempt);
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
        createReview(plan, stepIndex, attempt, reviewId(plan, stepIndex, attempt));
    }

    private void createReview(WorkflowPlan plan, int stepIndex, int attempt, TaskId id) {
        secretaryAdmission(plan.id());
        var step = plan.spec().steps().get(stepIndex);
        TaskId execution = executionId(plan, stepIndex, attempt);
        var output = attempts.findCompletedInput(execution)
                .orElseThrow(() -> new IllegalStateException("completed workflow output is missing: " + execution.value()));
        var inputs = new java.util.LinkedHashSet<TaskId>(acceptedInputs(plan, stepIndex - 1));
        inputs.add(execution);
        var reviewSkills = new java.util.LinkedHashSet<String>();
        reviewSkills.add("workflow-review");
        if (taskOutputs != null && inputs.stream().anyMatch(input -> taskOutputs.completed(input.value())
                .map(bundle -> bundle.files().stream().anyMatch(file -> Set.of("DOCUMENT", "ATTACHMENT").contains(file.kind())))
                .orElse(false))) reviewSkills.add("task-outputs-v1");
        if ("coding-work-package-executor".equals(step.skillRef())) {
            step.requiredSkills().stream().filter(skill -> !Set.of("coding", "integration", "repair").contains(skill))
                    .forEach(reviewSkills::add);
        }
        String authorization = tasks.find(execution).map(WorkTask::instruction)
                .filter(instruction -> instruction.contains("\nManager-authorized correction scope:\n"))
                .map(instruction -> instruction.substring(instruction.indexOf("\nManager-authorized correction scope:\n")))
                .orElse("");
        taskService.createIfAbsent(new CreateWorkTaskCommand(id, TaskType.EXTERNAL_WORK, SUBJECT, plan.id(),
                Set.copyOf(reviewSkills), "workflow-stage-reviewer",
                "Independently review the authoritative output of step '" + step.key() + "' against this goal and step instruction. "
                        + "Goal: " + plan.spec().goal() + "\nStep instruction and acceptance criteria: " + questionContext(step.instruction(), plan.id()) + authorization
                        + "\nReturn concrete findings when correction is needed. End with exactly one line: "
                        + "AIW_VERDICT: PASS or AIW_VERDICT: REPAIR_REQUIRED.",
                step.repositoryRef(), output.resultRevision() == null ? step.baseRevision() : output.resultRevision(),
                TaskPriority.NORMAL, Set.copyOf(inputs)));
    }

    Set<TaskId> acceptedInputs(WorkflowPlan plan, int throughStep) {
        var result = new java.util.LinkedHashSet<TaskId>();
        for (int step = 0; step <= throughStep; step++) {
            boolean accepted = false;
            for (int attempt : rounds(plan, step)) {
                TaskId review = effectiveReviewId(plan, step, attempt);
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
        humanChange(id);
        return withdraw(id, version, actor, reason, false);
    }

    WorkflowPlan withdrawAfterRollback(String id, long version, String actor, String reason) {
        return withdraw(id, version, actor, reason, true);
    }

    private WorkflowPlan withdraw(String id, long version, String actor, String reason, boolean rollbackComplete) {
        // WorkflowCancellationService also serializes before taking any task locks.
        secretarySerialization(id);
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
