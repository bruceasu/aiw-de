package me.asu.aidev.execution.application;

import me.asu.aidev.ai.application.AiProfileCatalog;
import me.asu.aidev.execution.domain.AiSelection;
import me.asu.aidev.execution.domain.CompletionReport;
import me.asu.aidev.execution.domain.ExecutionAttemptState;
import me.asu.aidev.execution.domain.LeaseToken;
import me.asu.aidev.execution.domain.WorkspaceAssignment;
import me.asu.aidev.execution.domain.WorkspaceResult;
import me.asu.aidev.execution.port.ExecutionAttemptRepository;
import me.asu.aidev.execution.port.TaskFailureListener;
import me.asu.aidev.execution.port.TaskExecutionPolicy;
import me.asu.aidev.execution.port.TaskConcurrencyBudget;
import me.asu.aidev.knowledge.application.KnowledgeService;
import me.asu.aidev.work.application.DependencyResolver;
import me.asu.aidev.work.application.TaskWorkspaceReplacementService;
import me.asu.aidev.workflow.application.WorkflowPlanService;
import me.asu.aidev.work.application.TaskCompletionResult;
import me.asu.aidev.work.domain.TaskId;
import me.asu.aidev.work.domain.TaskState;
import me.asu.aidev.work.port.WorkTaskRepository;
import me.asu.aidev.worker.domain.WorkerId;
import me.asu.aidev.worker.port.WorkerRepository;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskExecutionService {
    private me.asu.aidev.execution.config.ExecutionConfigurations configurations;
    @Autowired public void setExecutionConfigurations(me.asu.aidev.execution.config.ExecutionConfigurations configurations) { this.configurations=configurations; }
    private me.asu.aidev.execution.guard.ExecutionGuard guard;
    private me.asu.aidev.execution.guard.ExecutionYieldService guardedYield;
    @org.springframework.beans.factory.annotation.Autowired
    public void setExecutionGuard(me.asu.aidev.execution.guard.ExecutionGuard guard,me.asu.aidev.execution.guard.ExecutionYieldService yields) { this.guard=guard;this.guardedYield=yields; }
    private me.asu.aidev.execution.guard.GuardContract.Lease guardLease(WorkerId worker,LeaseToken token) {
        return new me.asu.aidev.execution.guard.GuardContract.Lease(worker.value(),attempts.findAttemptId(token).orElseThrow().value(),token.value());
    }
    private me.asu.aidev.authority.AuthorityTaskPolicy authorityPolicy;
    @org.springframework.beans.factory.annotation.Autowired
    public void setAuthorityPolicy(me.asu.aidev.authority.AuthorityTaskPolicy policy) { this.authorityPolicy=policy; }
    private me.asu.aidev.goal.application.GoalTaskPolicy goalPolicy;
    @Autowired
    public void setGoalPolicy(me.asu.aidev.goal.application.GoalTaskPolicy policy) { this.goalPolicy = policy; }
    private final WorkTaskRepository tasks;
    private final WorkerRepository workers;
    private final ExecutionAttemptRepository attempts;
    private final DependencyResolver dependencyResolver;
    private final TaskCompletionDispatcher completionDispatcher;
    private final KnowledgeService knowledge;
    private final List<TaskFailureListener> failureListeners;
    private final List<TaskExecutionPolicy> executionPolicies;
    private final List<TaskConcurrencyBudget> concurrencyBudgets;
    private final AiProfileCatalog aiProfiles;
    private final Clock clock;
    private final Duration leaseDuration;
    private me.asu.aidev.authority.ManagedPlanAdmission managedPlans;
    @Autowired
    public void setManagedPlanAdmission(me.asu.aidev.authority.ManagedPlanAdmission gate) { this.managedPlans=gate; }
    private void managedMutation(TaskId taskId) {
        if(managedPlans!=null) tasks.find(taskId).ifPresent(managedPlans::checkTask);
    }
    private void managedSerialization(TaskId taskId) {
        if(managedPlans!=null) tasks.find(taskId).ifPresent(managedPlans::serializeTask);
    }
    private TaskWorkspaceReplacementService workspaceReplacements;
    private WorkflowPlanService workflowPlans;
    private me.asu.aidev.workflow.application.RecommendationTaskPolicy recommendationPolicy;
    @Autowired
    public void setRecommendationPolicy(me.asu.aidev.workflow.application.RecommendationTaskPolicy policy) { this.recommendationPolicy = policy; }

    @Autowired
    public void setWorkflowPlans(WorkflowPlanService workflowPlans) {
        this.workflowPlans = workflowPlans;
    }

    @Autowired
    public void setWorkspaceReplacements(TaskWorkspaceReplacementService workspaceReplacements) {
        this.workspaceReplacements = workspaceReplacements;
    }

    @Autowired
    public TaskExecutionService(WorkTaskRepository tasks,
                                WorkerRepository workers,
                                ExecutionAttemptRepository attempts,
                                DependencyResolver dependencyResolver,
                                TaskCompletionDispatcher completionDispatcher,
                                KnowledgeService knowledge,
                                List<TaskFailureListener> failureListeners,
                                List<TaskExecutionPolicy> executionPolicies,
                                List<TaskConcurrencyBudget> concurrencyBudgets,
                                AiProfileCatalog aiProfiles) {
        this(tasks, workers, attempts, dependencyResolver, completionDispatcher, knowledge, failureListeners, executionPolicies, concurrencyBudgets, aiProfiles,
                Clock.systemUTC(), Duration.ofSeconds(60));
    }

    TaskExecutionService(WorkTaskRepository tasks,
                         WorkerRepository workers,
                         ExecutionAttemptRepository attempts,
                         DependencyResolver dependencyResolver,
                         TaskCompletionDispatcher completionDispatcher,
                         KnowledgeService knowledge,
                         List<TaskFailureListener> failureListeners,
                         List<TaskExecutionPolicy> executionPolicies,
                         List<TaskConcurrencyBudget> concurrencyBudgets,
                         AiProfileCatalog aiProfiles,
                         Clock clock,
                         Duration leaseDuration) {
        this.tasks = tasks;
        this.workers = workers;
        this.attempts = attempts;
        this.dependencyResolver = dependencyResolver;
        this.completionDispatcher = completionDispatcher;
        this.knowledge = knowledge;
        this.failureListeners = List.copyOf(failureListeners);
        this.executionPolicies = List.copyOf(executionPolicies);
        this.concurrencyBudgets = List.copyOf(concurrencyBudgets);
        this.aiProfiles = aiProfiles;
        this.clock = clock;
        this.leaseDuration = leaseDuration;
    }

    @Transactional
    public void start(TaskId taskId, WorkerId workerId, LeaseToken leaseToken) {
        start(taskId, workerId, leaseToken, null, null, null);
    }

    @Transactional
    public void start(TaskId taskId, WorkerId workerId, LeaseToken leaseToken,
                      String profileId, String profileVersion, String selectionReason) {
        var task = tasks.find(taskId).orElseThrow(() -> new IllegalStateException("task not found: " + taskId.value()));
        if(authorityPolicy!=null) authorityPolicy.beforeStart(task);
        if(guard!=null) { guard.serialize(taskId.value());guard.starting(taskId.value(),guardLease(workerId,leaseToken)); }
        assertExecutionAllowed(task);
        if (!tasks.lock(taskId)) throw new IllegalStateException("task not found: " + taskId.value());
        task = tasks.find(taskId).orElseThrow();
        if(authorityPolicy!=null) authorityPolicy.starting(task,workerId.value());
        if(authorityPolicy!=null) authorityPolicy.selection(task,profileId,profileVersion);
        if (task.state() == TaskState.CLAIMED && attempts.stopRequested(leaseToken, workerId)) {
            throw new IllegalStateException("Worker stop requested before task start; acknowledge stop instead");
        }
        if(configurations!=null && configurations.managed(taskId.value())) {
            AiSelection selection=configurations.select(taskId.value(),workerId.value(),profileId,profileVersion,selectionReason,attempts.findAttemptId(leaseToken).orElseThrow().value());
            if(!attempts.attachAiSelection(leaseToken,workerId,selection)) throw new IllegalStateException("Cannot attach prepared AI selection");
        } else if (goalPolicy != null && goalPolicy.managed(task)) {
            var worker = workers.find(workerId).orElseThrow(() -> new IllegalStateException("worker not found"));
            AiSelection selection = goalPolicy.select(task, worker.runtimeType(), profileId, profileVersion, selectionReason);
            if (selection != null && !attempts.attachAiSelection(leaseToken, workerId, selection))
                throw new IllegalStateException("cannot attach frozen goal selection");
        } else if (recommendationPolicy != null && recommendationPolicy.managed(task)) {
            var worker = workers.find(workerId).orElseThrow(() -> new IllegalStateException("worker not found"));
            AiSelection selection = recommendationPolicy.select(task, worker.runtimeType(), profileId, profileVersion, selectionReason);
            if (selection != null && !attempts.attachAiSelection(leaseToken, workerId, selection))
                throw new IllegalStateException("cannot attach frozen recommendation selection");
        } else if (aiProfiles.hasPolicy(task.type())) {
            var worker = workers.find(workerId).orElseThrow(() -> new IllegalStateException("worker not found"));
            AiSelection selection = aiProfiles.select(task.type(), worker.runtimeType(),
                    profileId, profileVersion, selectionReason);
            if (!attempts.attachAiSelection(leaseToken, workerId, selection)) {
                throw new IllegalStateException("cannot select AI profile; CLAIMED lease is missing or already selected");
            }
        } else if (profileId != null || profileVersion != null || selectionReason != null) {
            throw new IllegalArgumentException("AI profile is not configured for this task type");
        }
        var now = clock.instant();
        if (!tasks.start(taskId, workerId, leaseToken, now)) {
            throw new IllegalStateException("cannot start task; lease is missing, expired, or not owned by worker");
        }
        if (!attempts.transition(leaseToken, ExecutionAttemptState.CLAIMED, ExecutionAttemptState.RUNNING, now, null)) {
            throw new IllegalStateException("execution attempt is not CLAIMED");
        }
        if(authorityPolicy!=null) authorityPolicy.started(task);
    }

    @Transactional
    public WorkspaceAssignment pinWorkspaceBaseline(TaskId taskId,WorkerId workerId,LeaseToken leaseToken,String revision) {
        if(revision==null || !revision.matches("[0-9a-f]{40}|[0-9a-f]{64}")) throw new IllegalArgumentException("Immutable Git revision required");
        if(!tasks.lock(taskId)) throw new IllegalStateException("Task not found");
        var task=tasks.find(taskId).orElseThrow();
        assertExecutionAllowed(task);
        if(!"flexible-plan".equals(task.subjectType()) || !"coding-work-package-executor".equals(task.skillRef())
                || task.repositoryRef()==null || task.requiredCapabilities().contains("execution-guard-v1")
                || configurations!=null && configurations.managed(taskId.value()))
            throw new IllegalStateException("Automatic baseline is available only for native coding tasks");
        boolean first=task.baseRevision()==null;
        if(!tasks.pinBaselineOwned(taskId,workerId,leaseToken,revision,clock.instant()))
            throw new IllegalStateException("Baseline conflicts or the running lease is no longer owned");
        if(first) tasks.recordControlEvent(UUID.randomUUID().toString(),taskId,attempts.findAttemptId(leaseToken).orElseThrow().value(),
                "PIN_BASELINE","worker:"+workerId.value(),"Pinned current HEAD: "+revision,false,clock.instant());
        return WorkspaceAssignment.forTask(tasks.find(taskId).orElseThrow(),tasks.attemptNo(taskId));
    }

    @Transactional
    public boolean heartbeat(TaskId taskId, WorkerId workerId, LeaseToken leaseToken) {
        return heartbeat(taskId, workerId, leaseToken, null);
    }

    @Transactional
    public boolean heartbeat(TaskId taskId, WorkerId workerId, LeaseToken leaseToken, String externalSessionId) {
        var task = tasks.find(taskId).orElseThrow(() -> new IllegalStateException("task not found: " + taskId.value()));
        assertExecutionAllowed(task);
        var now = clock.instant();
        if (!tasks.heartbeatLease(taskId, workerId, leaseToken, now, now.plus(leaseDuration))) {
            throw new IllegalStateException("cannot heartbeat task lease; lease is missing, expired, or not owned by worker");
        }
        attachExternalSession(workerId, leaseToken, externalSessionId);
        workers.heartbeat(workerId, now);
        return attempts.stopRequested(leaseToken, workerId);
    }

    @Transactional(readOnly = true)
    public boolean stopRequested(WorkerId workerId, LeaseToken leaseToken) {
        return attempts.stopRequested(leaseToken, workerId);
    }

    @Transactional
    public void reviseInstruction(TaskId taskId, long expectedTaskVersion, int expectedInstructionRevision,
                                  String instruction, String actor, String reason) {
        if(guard!=null && guard.managed(taskId.value())) throw new IllegalStateException("Fixed inputs require a new authorized task, never in-place revision");
        managedMutation(taskId);
        requireAudit(actor, reason);
        if (!tasks.lock(taskId)) throw new IllegalStateException("task not found: " + taskId.value());
        var task = tasks.find(taskId).orElseThrow();
        if (task.version() != expectedTaskVersion || task.instructionRevision() != expectedInstructionRevision) {
            throw new IllegalStateException("task changed since it was displayed; reload and retry");
        }
        if (task.state() != TaskState.BLOCKED && task.state() != TaskState.READY
                && task.state() != TaskState.CLAIMED && task.state() != TaskState.RUNNING) {
            throw new IllegalStateException("completed or inactive task instructions cannot be revised");
        }
        var now = clock.instant();
        if (!tasks.reviseInstruction(taskId, task.state(), expectedTaskVersion, expectedInstructionRevision,
                instruction, actor, reason, now)) {
            throw new IllegalStateException("task revision lost a concurrent update; reload and retry");
        }
        if (task.state() == TaskState.CLAIMED) {
            revokeAndRequeue(task, TaskState.CLAIMED, now, actor, reason, "REQUEUED_AFTER_REVISION", false);
        } else if (task.state() == TaskState.RUNNING) {
            var lease = tasks.findLease(taskId).orElseThrow(() -> new IllegalStateException("running task has no lease"));
            if (!attempts.requestStop(lease.token(), lease.workerId(), now)) {
                throw new IllegalStateException("cannot request stop; active execution attempt is missing");
            }
            record(taskId, lease.token(), "STOP_REQUESTED", actor, reason, false, now);
        }
    }

    @Transactional
    public void requestStop(TaskId taskId, String actor, String reason) {
        managedSerialization(taskId);
        requireAudit(actor, reason);
        if (!tasks.lock(taskId)) throw new IllegalStateException("task not found: " + taskId.value());
        var task = tasks.find(taskId).orElseThrow();
        if (task.state() != TaskState.RUNNING) throw new IllegalStateException("only a RUNNING task can be asked to stop");
        var lease = tasks.findLease(taskId).orElseThrow(() -> new IllegalStateException("running task has no lease"));
        var now = clock.instant();
        if (!attempts.requestStop(lease.token(), lease.workerId(), now)) {
            throw new IllegalStateException("cannot request stop; active execution attempt is missing");
        }
        record(taskId, lease.token(), "STOP_REQUESTED", actor, reason, false, now);
    }

    @Transactional
    public void acknowledgeStop(TaskId taskId, WorkerId workerId, LeaseToken leaseToken) {
        if(guard!=null && guard.managed(taskId.value())) { guardedYield.stopped(taskId.value(),guardLease(workerId,leaseToken));return; }
        if(guard!=null) { guard.serialize(taskId.value());guard.finishing(taskId.value(),guardLease(workerId,leaseToken),false); }
        managedSerialization(taskId);
        if (!tasks.lock(taskId)) throw new IllegalStateException("task not found: " + taskId.value());
        var task = tasks.find(taskId).orElseThrow();
        if (attempts.stopAcknowledged(taskId, workerId, leaseToken)) return;
        if (task.state() != TaskState.CLAIMED && task.state() != TaskState.RUNNING) {
            throw new IllegalStateException("task is no longer active");
        }
        var now = clock.instant();
        var attemptId = attempts.findAttemptId(leaseToken).orElseThrow(() -> new IllegalStateException("execution attempt not found"));
        if (!attempts.acknowledgeStop(leaseToken, workerId, now)) {
            throw new IllegalStateException("stop was not requested for this Worker attempt");
        }
        if (!tasks.requeueRevoked(taskId, task.state(), workerId, leaseToken, now)) {
            throw new IllegalStateException("task lease changed before stop acknowledgement");
        }
        releaseOwnedResources(task, workerId, now);
        tasks.recordControlEvent(UUID.randomUUID().toString(), taskId, attemptId.value(), "STOP_ACKNOWLEDGED",
                "worker:" + workerId.value(), "Worker acknowledged stop request", false, now);
        if (workspaceReplacements != null) workspaceReplacements.afterStopAcknowledged(taskId, workerId, leaseToken);
        if (workflowPlans != null) workflowPlans.afterStopAcknowledged(taskId, workerId.value(), leaseToken.value());
    }

    @Transactional
    public void forceRequeue(TaskId taskId, String actor, String reason, boolean warningConfirmed) {
        if(guard!=null && guard.managed(taskId.value())) throw new IllegalStateException("Guarded attempts require actual quiescence and explicit continuation; no force replay");
        managedMutation(taskId);
        requireAudit(actor, reason);
        if (!tasks.lock(taskId)) throw new IllegalStateException("task not found: " + taskId.value());
        var task = tasks.find(taskId).orElseThrow();
        if (task.state() != TaskState.CLAIMED && task.state() != TaskState.RUNNING) {
            throw new IllegalStateException("only an active leased task can be force-requeued");
        }
        if (task.state() == TaskState.RUNNING && !warningConfirmed) {
            throw new IllegalArgumentException("ADMIN must confirm possible duplicate external side effects");
        }
        var lease = tasks.findLease(taskId).orElseThrow(() -> new IllegalStateException("active task has no lease"));
        if (task.state() == TaskState.RUNNING && !attempts.stopRequested(lease.token(), lease.workerId())) {
            throw new IllegalStateException("request cooperative stop before force re-dispatch");
        }
        var now = clock.instant();
        revokeAndRequeue(task, task.state(), now, actor, reason,
                task.state() == TaskState.RUNNING ? "FORCED_REQUEUE" : "REQUEUED", warningConfirmed);
    }

    private void revokeAndRequeue(me.asu.aidev.work.domain.WorkTask task, TaskState expectedState,
                                  java.time.Instant now, String actor, String reason,
                                  String action, boolean warningConfirmed) {
        var lease = tasks.findLease(task.id()).orElseThrow(() -> new IllegalStateException("active task has no lease"));
        var attemptId = attempts.findAttemptId(lease.token()).orElseThrow(() -> new IllegalStateException("execution attempt not found"));
        if (!attempts.terminateActiveLease(lease.token(), now, reason)) {
            throw new IllegalStateException("cannot revoke active attempt");
        }
        if (!tasks.requeueRevoked(task.id(), expectedState, lease.workerId(), lease.token(), now)) {
            throw new IllegalStateException("task lease changed before requeue");
        }
        releaseOwnedResources(task, lease.workerId(), now);
        tasks.recordControlEvent(UUID.randomUUID().toString(), task.id(), attemptId.value(), action,
                actor, reason, warningConfirmed, now);
        if (workspaceReplacements != null) workspaceReplacements.conflictIfPending(task.id(), "lease was requeued before stop acknowledgement");
    }

    private void releaseOwnedResources(me.asu.aidev.work.domain.WorkTask task, WorkerId workerId,
                                       java.time.Instant now) {
        if (!workers.releaseCapacity(workerId, now)) {
            throw new IllegalStateException("worker capacity accounting underflow: " + workerId.value());
        }
        releaseConcurrency(task, now);
    }

    private void record(TaskId taskId, LeaseToken leaseToken, String action, String actor,
                        String reason, boolean warningConfirmed, java.time.Instant now) {
        var attemptId = attempts.findAttemptId(leaseToken).orElseThrow(() -> new IllegalStateException("execution attempt not found"));
        tasks.recordControlEvent(UUID.randomUUID().toString(), taskId, attemptId.value(), action,
                actor, reason, warningConfirmed, now);
    }

    private static void requireAudit(String actor, String reason) {
        if (actor == null || actor.isBlank()) throw new IllegalArgumentException("actor is required");
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("reason is required");
    }

    @Transactional
    public TaskCompletionResult complete(TaskId taskId, WorkerId workerId, LeaseToken leaseToken) {
        return complete(taskId, workerId, leaseToken, null);
    }

    @Transactional
    public TaskCompletionResult complete(TaskId taskId, WorkerId workerId, LeaseToken leaseToken, String externalSessionId) {
        return complete(taskId, workerId, leaseToken, externalSessionId, null);
    }

    @Transactional
    public TaskCompletionResult complete(TaskId taskId, WorkerId workerId, LeaseToken leaseToken,
                                         String externalSessionId, WorkspaceResult workspaceResult) {
        return complete(taskId, workerId, leaseToken, externalSessionId, workspaceResult, null);
    }

    @Transactional
    public TaskCompletionResult complete(TaskId taskId, WorkerId workerId, LeaseToken leaseToken,
                                         String externalSessionId, WorkspaceResult workspaceResult,
                                         CompletionReport completionReport) {
        if(replayedGuardedCompletion(taskId,workerId,leaseToken,workspaceResult,completionReport)) return new TaskCompletionResult(taskId,List.of(),List.of());
        if(guard!=null) { guard.serialize(taskId.value());guard.finishing(taskId.value(),guardLease(workerId,leaseToken),true); }
        if(guard!=null) guard.validateResult(taskId.value(),guardLease(workerId,leaseToken),workspaceResult);
        var now = clock.instant();
        var taskBeforeCompletion = tasks.find(taskId)
                .orElseThrow(() -> new IllegalStateException("task not found: " + taskId.value()));
        assertExecutionAllowed(taskBeforeCompletion);
        var taskType = taskBeforeCompletion.type();
        if ((taskType == me.asu.aidev.work.domain.TaskType.CODE_REVIEW
                || taskType == me.asu.aidev.work.domain.TaskType.VERIFICATION
                || taskType == me.asu.aidev.work.domain.TaskType.INTEGRATION_VERIFICATION)
                && completionReport == null) {
            throw new IllegalStateException("completion report required for review/verification task");
        }
        if (taskType == me.asu.aidev.work.domain.TaskType.REQUIREMENT_ANALYSIS
                || taskType == me.asu.aidev.work.domain.TaskType.SOLUTION_ARCHITECTURE
                || taskType == me.asu.aidev.work.domain.TaskType.IMPLEMENTATION_PLANNING) {
            if (completionReport == null || completionReport.verdict() != me.asu.aidev.execution.domain.CompletionVerdict.PASS) {
                throw new IllegalStateException("PASS completion report required for autonomous planning task: " + taskType);
            }
        }
        attachExternalSession(workerId, leaseToken, externalSessionId);
        attachWorkspaceResult(taskId, workerId, leaseToken, workspaceResult);
        if (completionReport != null && !attempts.attachCompletionReport(leaseToken, workerId, completionReport)) {
            throw new IllegalStateException("cannot attach completion report; execution attempt is not RUNNING or not owned by worker");
        }
        if (!tasks.completeOwned(taskId, workerId, leaseToken, now)) {
            throw new IllegalStateException("cannot complete task; RUNNING lease is missing, expired, or not owned by worker");
        }
        if (!attempts.transition(leaseToken, ExecutionAttemptState.RUNNING, ExecutionAttemptState.COMPLETED, null, now)) {
            throw new IllegalStateException("execution attempt is not RUNNING");
        }
        if (workspaceReplacements != null) workspaceReplacements.conflictIfPending(taskId, "Worker completed before stop acknowledgement");
        if (workflowPlans != null) workflowPlans.conflictPending(taskId);
        if (!workers.releaseCapacity(workerId, now)) {
            throw new IllegalStateException("worker capacity accounting underflow: " + workerId.value());
        }
        releaseConcurrency(taskBeforeCompletion, now);
        var completedTask = tasks.find(taskId)
                .orElseThrow(() -> new IllegalStateException("completed task not found: " + taskId.value()));
        knowledge.record(completedTask, completionReport, workspaceResult, now);
        if(guard!=null && guard.managed(taskId.value())) {
            guard.deferCompletion(taskId.value(),guardLease(workerId,leaseToken).attemptId(),workspaceResult,completionReport);
            requestGuardedAdvance(guard.context(taskId.value()).planId());
            return new TaskCompletionResult(taskId,List.of(),List.of());
        }
        List<TaskId> generated = completionDispatcher.dispatch(
                new TaskCompletionContext(completedTask, workspaceResult, completionReport));
        List<TaskId> newlyReady = dependencyResolver.refreshDependents(taskId);
        return new TaskCompletionResult(taskId, newlyReady, generated);
    }

    @Transactional
    public void fail(TaskId taskId, WorkerId workerId, LeaseToken leaseToken) {
        fail(taskId, workerId, leaseToken, null, null);
    }

    @Transactional
    public void fail(TaskId taskId, WorkerId workerId, LeaseToken leaseToken, String externalSessionId) {
        fail(taskId, workerId, leaseToken, externalSessionId, null);
    }

    @Transactional
    public void fail(TaskId taskId, WorkerId workerId, LeaseToken leaseToken,
                     String externalSessionId, String failureReason) {
        fail(taskId, workerId, leaseToken, externalSessionId, failureReason, "UNKNOWN");
    }

    @Transactional
    public void fail(TaskId taskId, WorkerId workerId, LeaseToken leaseToken,
                     String externalSessionId, String failureReason, String failureCategory) {
        if(guard!=null) { guard.serialize(taskId.value());guard.finishing(taskId.value(),guardLease(workerId,leaseToken),false); }
        var task = tasks.find(taskId).orElseThrow(() -> new IllegalStateException("task not found: " + taskId.value()));
        assertExecutionAllowed(task);
        String category = failureCategory == null ? "UNKNOWN" : failureCategory;
        if (!Set.of("UNKNOWN", "RATE_LIMIT", "MODEL_ERROR", "OUTPUT_INVALID", "TOOL_ERROR", "CONFIGURATION")
                .contains(category)) throw new IllegalArgumentException("invalid failure category");
        var now = clock.instant();
        attachExternalSession(workerId, leaseToken, externalSessionId);
        String code = failureReason != null && failureReason.matches("[A-Z][A-Z0-9_]{0,119}")
                ? failureReason : null;
        if (!attempts.attachFailureDetails(leaseToken, workerId, category, code)) {
            throw new IllegalStateException("cannot record failure category; RUNNING lease is missing");
        }
        if (!tasks.failOwned(taskId, workerId, leaseToken, now)) {
            throw new IllegalStateException("cannot fail task; RUNNING lease is missing, expired, or not owned by worker");
        }
        if (!attempts.transition(leaseToken, ExecutionAttemptState.RUNNING, ExecutionAttemptState.FAILED, null, now)) {
            throw new IllegalStateException("execution attempt is not RUNNING");
        }
        if (workspaceReplacements != null) workspaceReplacements.conflictIfPending(taskId, "Worker failed before stop acknowledgement");
        if (workflowPlans != null) workflowPlans.conflictPending(taskId);
        if (!workers.releaseCapacity(workerId, now)) {
            throw new IllegalStateException("worker capacity accounting underflow: " + workerId.value());
        }
        releaseConcurrency(task, now);
        var failedTask = tasks.find(taskId)
                .orElseThrow(() -> new IllegalStateException("failed task not found: " + taskId.value()));
        if(guard!=null && guard.managed(taskId.value())) {
            guard.deferFailure(taskId.value(),guardLease(workerId,leaseToken).attemptId(),failureReason);
            requestGuardedAdvance(guard.context(taskId.value()).planId());return;
        }
        for (TaskFailureListener listener : failureListeners) {
            listener.onFailed(failedTask, failureReason);
        }
    }

    private void assertExecutionAllowed(me.asu.aidev.work.domain.WorkTask task) {
        for (TaskExecutionPolicy policy : executionPolicies) policy.assertExecutionAllowed(task);
    }
    @Transactional
    public boolean replayedGuardedCompletion(TaskId task,WorkerId worker,LeaseToken lease,WorkspaceResult result,CompletionReport report) {
        boolean recorded=guard!=null && guard.managed(task.value()) && guard.completed(task.value(),guardLease(worker,lease),result,report);
        if(recorded) requestGuardedAdvance(guard.context(task.value()).planId());
        return recorded;
    }
    public void requestGuardedAdvance(String plan) {
        Runnable advance=()->{
            try { guard.advanceAfterCommit(plan); }
            catch(RuntimeException error) { System.getLogger(TaskExecutionService.class.getName()).log(System.Logger.Level.WARNING,"Guarded execution facts retained; advancement remains pending"); }
        };
        if(org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization() {
                @Override public void afterCommit() { advance.run(); }
            });
        } else advance.run();
    }
    @Transactional(propagation=org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void drainGuardedCompletions(String plan) {
        if(guard==null) return;
        guard.serializePlan(plan);
        for(var pending:guard.completions(plan)) {
            var id=new TaskId(String.valueOf(pending.get("task_id")));guard.serialize(id.value());
            if(!guard.mayAdvance(id.value())) return;
            var task=tasks.find(id).orElseThrow();
            String attempt=String.valueOf(pending.get("attempt_id"));
            guard.completionApplied(attempt);
            if(!guard.latestAttempt(id.value(),attempt)) continue;
            if("FAILED".equals(pending.get("event_type"))) {
                if(task.state()!=TaskState.FAILED) throw new IllegalStateException("Deferred failure is not a failed task");
                for(TaskFailureListener listener:failureListeners) listener.onFailed(task,(String)pending.get("failure_reason"));
            } else {
                if(task.state()!=TaskState.COMPLETED) throw new IllegalStateException("Deferred completion is not a completed task");
                completionDispatcher.dispatch(new TaskCompletionContext(task,
                    guard.decode((String)pending.get("workspace_json"),WorkspaceResult.class),guard.decode((String)pending.get("report_json"),CompletionReport.class)));
                dependencyResolver.refreshDependents(id);
            }
        }
    }

    private void attachWorkspaceResult(TaskId taskId, WorkerId workerId, LeaseToken leaseToken,
                                       WorkspaceResult workspaceResult) {
        var task = tasks.find(taskId).orElseThrow(() -> new IllegalStateException("task not found: " + taskId.value()));
        WorkspaceAssignment assignment = WorkspaceAssignment.forTask(task, tasks.attemptNo(taskId));
        if (assignment == null) {
            if (workspaceResult != null) {
                throw new IllegalStateException("workspace result provided for task without repository assignment");
            }
            return;
        }
        if (assignment.baseRevision() == null) {
            if (workspaceResult != null) {
                throw new IllegalStateException("direct directory task must not report a Git workspace result");
            }
            return;
        }
        if (workspaceResult == null) {
            throw new IllegalStateException("workspace result required for repository task");
        }
        if (!attempts.attachWorkspaceResult(leaseToken, workerId, assignment, workspaceResult)) {
            throw new IllegalStateException("cannot attach workspace result; execution attempt is not RUNNING or not owned by worker");
        }
    }

    private void attachExternalSession(WorkerId workerId, LeaseToken leaseToken, String externalSessionId) {
        if (externalSessionId == null || externalSessionId.isBlank()) return;
        if (!attempts.attachExternalSession(leaseToken, workerId, externalSessionId)) {
            throw new IllegalStateException("cannot attach external session; execution attempt is not active or not owned by worker");
        }
    }

    private void releaseConcurrency(me.asu.aidev.work.domain.WorkTask task, java.time.Instant at) {
        for (int i = concurrencyBudgets.size() - 1; i >= 0; i--) concurrencyBudgets.get(i).release(task, at);
    }

    @Transactional
    public int recoverExpiredLeases(int limit) {
        var now = clock.instant();
        int recovered = 0;
        for (var lease : tasks.findExpiredLeases(now, limit)) {
            if(guard!=null && guard.retainExpired(lease.taskId().value(),attempts.findAttemptId(lease.token()).orElseThrow().value())) continue;
            if (!tasks.requeueExpired(lease.taskId(), lease.workerId(), lease.token(), now)) continue;
            boolean attemptExpired = attempts.transition(
                    lease.token(), ExecutionAttemptState.CLAIMED, ExecutionAttemptState.EXPIRED, null, now)
                    || attempts.transition(
                    lease.token(), ExecutionAttemptState.RUNNING, ExecutionAttemptState.EXPIRED, null, now);
            if (!attemptExpired) {
                throw new IllegalStateException("execution attempt not found for expired lease: " + lease.token().value());
            }
            var recoveredTask = tasks.find(lease.taskId())
                    .orElseThrow(() -> new IllegalStateException("recovered task not found: " + lease.taskId().value()));
            if ("workflow-recommendation".equals(recoveredTask.subjectType())) {
                tasks.recordControlEvent(UUID.randomUUID().toString(), lease.taskId(),
                        attempts.findAttemptId(lease.token()).map(id -> id.value()).orElse(null),
                        "RECOMMENDATION_LEASE_EXPIRED", "system", "Analysis lease expired; manual retry required; the original model call may still incur cost",
                        false, now);
            }
            releaseConcurrency(recoveredTask, now);
            if (!workers.releaseCapacity(lease.workerId(), now)) {
                throw new IllegalStateException("worker capacity accounting underflow: " + lease.workerId().value());
            }
            recovered++;
        }
        return recovered;
    }
}
