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
    private TaskWorkspaceReplacementService workspaceReplacements;
    private WorkflowPlanService workflowPlans;

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
        if (!tasks.lock(taskId)) throw new IllegalStateException("task not found: " + taskId.value());
        var task = tasks.find(taskId).orElseThrow(() -> new IllegalStateException("task not found: " + taskId.value()));
        assertExecutionAllowed(task);
        if (task.state() == TaskState.CLAIMED && attempts.stopRequested(leaseToken, workerId)) {
            throw new IllegalStateException("Worker stop requested before task start; acknowledge stop instead");
        }
        if (aiProfiles.hasPolicy(task.type())) {
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
        for (TaskFailureListener listener : failureListeners) {
            listener.onFailed(failedTask, failureReason);
        }
    }

    private void assertExecutionAllowed(me.asu.aidev.work.domain.WorkTask task) {
        for (TaskExecutionPolicy policy : executionPolicies) policy.assertExecutionAllowed(task);
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
            releaseConcurrency(recoveredTask, now);
            if (!workers.releaseCapacity(lease.workerId(), now)) {
                throw new IllegalStateException("worker capacity accounting underflow: " + lease.workerId().value());
            }
            recovered++;
        }
        return recovered;
    }
}
