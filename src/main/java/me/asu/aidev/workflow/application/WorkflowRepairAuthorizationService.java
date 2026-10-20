package me.asu.aidev.workflow.application;

import java.time.Instant;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import me.asu.aidev.execution.application.TaskCompletionContext;
import me.asu.aidev.execution.domain.CompletionVerdict;
import me.asu.aidev.work.application.CreateWorkTaskCommand;
import me.asu.aidev.work.application.WorkTaskService;
import me.asu.aidev.work.domain.TaskId;
import me.asu.aidev.work.domain.TaskPriority;
import me.asu.aidev.work.domain.TaskState;
import me.asu.aidev.work.domain.TaskType;
import me.asu.aidev.work.port.WorkTaskRepository;
import me.asu.aidev.worker.domain.WorkerId;
import me.asu.aidev.worker.domain.WorkerLiveness;
import me.asu.aidev.worker.domain.WorkerStatus;
import me.asu.aidev.worker.port.WorkerRepository;
import me.asu.aidev.workflow.domain.WorkflowTaskPosition;
import me.asu.aidev.workflow.jdbc.JdbcWorkflowRepairAuthorizationRepository;
import me.asu.aidev.workflow.jdbc.JdbcWorkflowRepairAuthorizationRepository.Authorization;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Human authority fixes the scope; a designated independent Worker may only consume one grant. */
@Service
public class WorkflowRepairAuthorizationService {
    private me.asu.aidev.secretary.application.DelegationBoundary delegation;
    @org.springframework.beans.factory.annotation.Autowired
    public void setDelegationBoundary(me.asu.aidev.secretary.application.DelegationBoundary boundary) { this.delegation = boundary; }
    private WorkflowQuestionService questionDecisions;
    @org.springframework.beans.factory.annotation.Autowired
    public void setQuestionDecisions(WorkflowQuestionService questionDecisions) { this.questionDecisions = questionDecisions; }

    public static final String SUBJECT = "workflow-repair-authorization";
    public static final String CAPABILITY = "workflow-recovery-decision";
    private final WorkflowPlanService plans;
    private final JdbcWorkflowRepairAuthorizationRepository authorizations;
    private final WorkTaskService taskService;
    private final WorkTaskRepository tasks;
    private final WorkerRepository workers;

    public WorkflowRepairAuthorizationService(WorkflowPlanService plans, JdbcWorkflowRepairAuthorizationRepository authorizations,
            WorkTaskService taskService, WorkTaskRepository tasks, WorkerRepository workers) {
        this.plans = plans; this.authorizations = authorizations; this.taskService = taskService;
        this.tasks = tasks; this.workers = workers;
    }

    public List<Authorization> list(String planId) { return authorizations.list(planId); }
    public java.util.Map<String, String> decisionReports(String planId) { return authorizations.decisionReports(planId); }

    public List<String> decisionWorkers(String planId) {
        return workers.findAvailable(Instant.now().minus(WorkerLiveness.HEARTBEAT_WINDOW)).stream()
                .filter(worker -> worker.capabilities().contains(CAPABILITY)).map(worker -> worker.id().value()).toList();
    }

    @Transactional
    public Authorization authorize(String planId, String sourceReview, long planVersion, long taskVersion,
            String scope, String reason, String delegateWorkerId, boolean confirmed, String actor) {
        if (delegation != null) {
            delegation.changing("PLAN:" + planId);
            if (delegateWorkerId != null && !delegateWorkerId.isBlank() && delegation.managedPlan(planId))
                throw new IllegalStateException("This target uses versioned delegation; the legacy proxy grant cannot bypass its policy");
        }
        if (!confirmed) throw new IllegalArgumentException("Confirm the one-round repair authorization.");
        if (scope == null || scope.isBlank() || scope.length() > 4000
                || reason == null || reason.isBlank() || reason.length() > 2000) {
            throw new IllegalArgumentException("Repair scope and reason are required (4000 and 2000 characters maximum).");
        }
        Instant now = Instant.now();
        for (var grant : authorizations.list(planId)) {
            if (!grant.status().equals("PENDING")) continue;
            if (grant.expiresAt().isAfter(now) && authorizations.planUnchanged(grant)) throw new IllegalStateException("A repair authorization is already pending.");
            cancelInactiveDecision(grant);
            authorizations.finish(grant.id(), grant.expiresAt().isAfter(now) ? "STALE" : "EXPIRED", null, now);
        }
        var plan = plans.get(planId);
        var position = WorkflowTaskPosition.parse(plan, new TaskId(sourceReview)).orElseThrow();
        String delegate = delegateWorkerId == null || delegateWorkerId.isBlank() ? null : delegateWorkerId.strip();
        if (delegate != null) {
            var worker = workers.find(new WorkerId(delegate)).orElseThrow(() -> new IllegalArgumentException("Decision Worker not found."));
            if (worker.status() != WorkerStatus.ACTIVE || !worker.capabilities().contains(CAPABILITY)
                    || worker.lastHeartbeatAt().isBefore(now.minus(WorkerLiveness.HEARTBEAT_WINDOW))) {
                throw new IllegalArgumentException("Decision Worker must be active with workflow-recovery-decision capability.");
            }
            String executor = authorizations.completedWorker(WorkflowPlanService.executionId(plan, position.step(), position.round()).value()).orElseThrow();
            String reviewer = authorizations.completedWorker(sourceReview).orElseThrow();
            if (delegate.equals(executor) || delegate.equals(reviewer)) throw new IllegalArgumentException("Decision Worker must be independent of the executor and reviewer.");
        }
        var snapshot = plans.reserveRepair(planId, sourceReview, planVersion, taskVersion, actor, reason.strip());
        String grantId = "RA-" + UUID.randomUUID();
        String decisionId = delegate == null ? null : grantId + "~decision";
        var grant = new Authorization(grantId, planId, snapshot.planVersion(), sourceReview, taskVersion,
                scope.strip(), reason.strip(), actor, delegate, decisionId, null, "PENDING", now, now.plus(Duration.ofHours(1)));
        authorizations.insert(grant);
        if (delegate == null) consume(grant, actor);
        else {
            var inputs = new java.util.LinkedHashSet<TaskId>(plans.acceptedInputs(plan, position.step() - 1));
            inputs.add(new TaskId(sourceReview));
            inputs.add(WorkflowPlanService.executionId(plan, position.step(), position.round()));
            var step = plan.spec().steps().get(position.step());
            taskService.createIfAbsent(new CreateWorkTaskCommand(new TaskId(decisionId), TaskType.EXTERNAL_WORK, SUBJECT,
                    grantId, Set.of(CAPABILITY, "task-outputs-v1"), "workflow-recovery-decider",
                    "Decide whether to use this human-approved one-round repair authorization. Do not modify files, change requirements, expand scope, renew the grant or approve your own work. "
                    + "Return PASS only if the supplied review findings can be addressed within the exact scope below. Otherwise return REPAIR_REQUIRED with the reason. "
                    + "Grant: " + grantId + "\nSource review: " + sourceReview + "\nHuman authority: " + actor
                    + "\nFixed scope:\n" + scope.strip() + "\nExpires: " + grant.expiresAt()
                    + (questionDecisions == null ? "" : questionDecisions.withContext("", planId))
                    + "\nEnd with AIW_VERDICT: PASS or AIW_VERDICT: REPAIR_REQUIRED.",
                    step.repositoryRef(), plans.completedRevision(WorkflowPlanService.executionId(plan, position.step(), position.round()),
                            step.baseRevision()), TaskPriority.NORMAL, Set.copyOf(inputs)));
        }
        return authorizations.find(grantId).orElseThrow();
    }

    private List<TaskId> consume(Authorization grant, String actor) {
        if (delegation != null && grant.delegateWorkerId() != null && delegation.managedPlan(grant.planId())) {
            authorizations.finish(grant.id(), "STALE", null, Instant.now());
            return List.of();
        }
        if (!grant.expiresAt().isAfter(Instant.now()) || !authorizations.planUnchanged(grant)) {
            authorizations.finish(grant.id(), grant.expiresAt().isAfter(Instant.now()) ? "STALE" : "EXPIRED", null, Instant.now());
            return List.of();
        }
        java.util.function.Supplier<java.util.Optional<TaskId>> continuation = () -> plans.continueAuthorizedRepair(
                grant.planId(), grant.sourceReviewId(), grant.planVersion(), grant.sourceTaskVersion(), actor, grant.reason(), grant.scope());
        var task = delegation == null ? continuation.get() : delegation.legacyCompletion(continuation);
        if (!authorizations.finish(grant.id(), task.isPresent() ? "CONSUMED" : "STALE",
                task.map(TaskId::value).orElse(null), Instant.now())) {
            throw new IllegalStateException("Repair authorization was already consumed or revoked.");
        }
        return task.map(List::of).orElseGet(List::of);
    }

    @Transactional
    public List<TaskId> decide(TaskCompletionContext context) {
        var grant = authorizations.decision(context.completedTask().id().value()).orElseThrow();
        if (!grant.status().equals("PENDING")) return List.of();
        String worker = authorizations.completedWorker(context.completedTask().id().value()).orElseThrow();
        if (!worker.equals(grant.delegateWorkerId()) || context.completionReport() == null
                || context.completionReport().verdict() != CompletionVerdict.PASS) {
            authorizations.finish(grant.id(), "DENIED", null, Instant.now());
            return List.of();
        }
        return consume(grant, "worker:" + worker);
    }

    @Transactional
    public void revoke(String planId, String grantId, String actor) {
        var grant = authorizations.find(grantId).orElseThrow();
        if (!grant.planId().equals(planId)) throw new IllegalArgumentException("Authorization does not belong to this plan.");
        cancelInactiveDecision(grant);
        if (!authorizations.finish(grantId, "REVOKED", null, Instant.now())) throw new IllegalStateException("Authorization is no longer pending.");
        plans.get(planId);
        if (grant.decisionTaskId() != null) tasks.recordControlEvent(UUID.randomUUID().toString(), new TaskId(grant.decisionTaskId()),
                null, "AUTHORIZATION_REVOKED", actor, "Human revoked repair authorization", true, Instant.now());
    }

    private void cancelInactiveDecision(Authorization grant) {
        if (grant.decisionTaskId() == null) return;
        tasks.find(new TaskId(grant.decisionTaskId())).filter(task -> task.state() == TaskState.READY
                || task.state() == TaskState.BLOCKED || task.state() == TaskState.FAILED)
                .ifPresent(task -> tasks.cancelInactive(task.id(), task.state(), Instant.now()));
    }
}
