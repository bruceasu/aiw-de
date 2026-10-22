package me.asu.aidev.worker.protocol.http;

import me.asu.aidev.ai.application.AiProfileCatalog;
import me.asu.aidev.autonomous.application.WorkspaceRegistry;
import me.asu.aidev.execution.application.TaskClaimService;
import me.asu.aidev.execution.application.TaskExecutionService;
import me.asu.aidev.execution.domain.ClaimedTask;
import me.asu.aidev.execution.domain.CompletionReport;
import me.asu.aidev.execution.domain.CompletionVerdict;
import me.asu.aidev.execution.domain.LeaseToken;
import me.asu.aidev.execution.domain.WorkspaceResult;
import me.asu.aidev.execution.port.ExecutionAttemptRepository;
import me.asu.aidev.work.application.TaskCompletionResult;
import me.asu.aidev.work.domain.TaskId;
import me.asu.aidev.worker.application.RegisterWorkerCommand;
import me.asu.aidev.worker.application.WorkerService;
import me.asu.aidev.worker.domain.WorkerId;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Stable transport adapter for the R3/R4 worker protocol semantics.
 *
 * The controller owns no lifecycle rules. All authority remains in the application services.
 */
@RestController
@RequestMapping("/api/r1/worker-protocol/v1")
public class WorkerProtocolV1Controller {
    private me.asu.aidev.authority.AuthorityTaskPolicy delegationPolicy;
    @Autowired
    public void setDelegationPolicy(me.asu.aidev.authority.AuthorityTaskPolicy policy) { this.delegationPolicy=policy; }
    private me.asu.aidev.goal.application.GoalTaskPolicy goalPolicy;
    @Autowired
    public void setGoalPolicy(me.asu.aidev.goal.application.GoalTaskPolicy policy) { this.goalPolicy = policy; }
    private final WorkerService workers;
    private final TaskClaimService claims;
    private final TaskExecutionService executions;
    private final ExecutionAttemptRepository attempts;
    private final AiProfileCatalog aiProfiles;
    private me.asu.aidev.workflow.application.RecommendationTaskPolicy recommendationPolicy;
    @Autowired
    public void setRecommendationPolicy(me.asu.aidev.workflow.application.RecommendationTaskPolicy policy) { this.recommendationPolicy = policy; }
    private me.asu.aidev.workflow.application.WorkflowAiPreferences workflowAiPreferences;

    @Autowired
    public void setWorkflowAiPreferences(me.asu.aidev.workflow.application.WorkflowAiPreferences preferences) {
        this.workflowAiPreferences = preferences;
    }

    private WorkspaceRegistry workspaceRegistry;
    private me.asu.aidev.output.application.TaskOutputService outputs;

    @Autowired
    public void setTaskOutputService(me.asu.aidev.output.application.TaskOutputService outputs) {
        this.outputs = outputs;
    }

    @Autowired
    public void setWorkspaceRegistry(WorkspaceRegistry workspaceRegistry) {
        this.workspaceRegistry = workspaceRegistry;
    }

    public WorkerProtocolV1Controller(WorkerService workers,
                                      TaskClaimService claims,
                                      TaskExecutionService executions,
                                      ExecutionAttemptRepository attempts,
                                      AiProfileCatalog aiProfiles) {
        this.workers = workers;
        this.claims = claims;
        this.executions = executions;
        this.attempts = attempts;
        this.aiProfiles = aiProfiles;
    }

    @PostMapping("/workers/register")
    public WorkerResponse register(@RequestBody RegisterRequest request) {
        var worker = workers.register(new RegisterWorkerCommand(
                new WorkerId(required(request.workerId(), "workerId")),
                required(request.runtimeType(), "runtimeType"),
                request.capabilities() == null ? Set.of() : request.capabilities(),
                request.maxConcurrency()));
        return new WorkerResponse(worker.id().value(), worker.status().name(), worker.activeLeases(), worker.maxConcurrency());
    }

    @PostMapping("/workers/{workerId}/heartbeat")
    public void workerHeartbeat(@PathVariable String workerId) {
        workers.heartbeat(new WorkerId(workerId));
    }

    @PostMapping("/workers/{workerId}/drain")
    public void drain(@PathVariable String workerId) {
        workers.drain(new WorkerId(workerId));
    }

    @PostMapping("/workers/{workerId}/offline")
    public void offline(@PathVariable String workerId) {
        workers.offline(new WorkerId(workerId));
    }

    @PostMapping("/tasks/claim")
    public ResponseEntity<ClaimedTaskResponse> claim(@RequestBody ClaimRequest request) {
        var workerId = new WorkerId(required(request.workerId(), "workerId"));
        var claimed = claims.claimNext(workerId);
        return claimed.map(value -> ResponseEntity.ok(toResponse(value, workers.get(workerId).runtimeType())))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/tasks/{taskId}/start")
    public void start(@PathVariable String taskId, @RequestBody StartTaskRequest request) {
        executions.start(new TaskId(taskId), new WorkerId(required(request.workerId(), "workerId")),
                new LeaseToken(required(request.leaseToken(), "leaseToken")),
                request.profileId(), request.profileVersion(), request.selectionReason());
    }

    @PostMapping("/tasks/{taskId}/heartbeat")
    public HeartbeatResponse taskHeartbeat(@PathVariable String taskId, @RequestBody LeaseActionRequest request) {
        return new HeartbeatResponse(executions.heartbeat(
                new TaskId(taskId), workerId(request), leaseToken(request), request.externalSessionId()));
    }

    @PostMapping("/tasks/{taskId}/stop-acknowledgement")
    public void acknowledgeStop(@PathVariable String taskId, @RequestBody StopAcknowledgementRequest request) {
        executions.acknowledgeStop(new TaskId(taskId),
                new WorkerId(required(request.workerId(), "workerId")),
                new LeaseToken(required(request.leaseToken(), "leaseToken")));
    }

    @PostMapping("/tasks/{taskId}/complete")
    public CompletionResponse complete(@PathVariable String taskId, @RequestBody CompleteTaskRequest request) {
        WorkspaceResult workspaceResult = request.workspaceResult() == null ? null : new WorkspaceResult(
                request.workspaceResult().resultRevision(), request.workspaceResult().changedFiles(),
                request.workspaceResult().publicationRef());
        CompletionReport report = request.report() == null ? null : new CompletionReport(
                CompletionVerdict.valueOf(required(request.report().verdict(), "report.verdict")),
                required(request.report().summary(), "report.summary"));
        if(executions.replayedGuardedCompletion(new TaskId(taskId),new WorkerId(required(request.workerId(),"workerId")),new LeaseToken(required(request.leaseToken(),"leaseToken")),workspaceResult,report))
            return new CompletionResponse(taskId,List.of(),List.of());
        if (outputs != null) outputs.ensureReport(taskId, request.workerId(), request.leaseToken(), report, workspaceResult);
        TaskCompletionResult result = executions.complete(
                new TaskId(taskId), new WorkerId(required(request.workerId(), "workerId")),
                new LeaseToken(required(request.leaseToken(), "leaseToken")),
                request.externalSessionId(), workspaceResult, report);
        if (outputs != null) outputs.observe(request.leaseToken());
        return new CompletionResponse(
                result.completedTaskId().value(),
                result.newlyReadyTaskIds().stream().map(TaskId::value).toList(),
                result.generatedTaskIds().stream().map(TaskId::value).toList());
    }

    @PostMapping("/tasks/{taskId}/fail")
    public void fail(@PathVariable String taskId, @RequestBody FailTaskRequest request) {
        executions.fail(new TaskId(taskId), new WorkerId(required(request.workerId(), "workerId")),
                new LeaseToken(required(request.leaseToken(), "leaseToken")),
                request.externalSessionId(), request.failureReason(), request.failureCategory());
        if (outputs != null) outputs.observe(request.leaseToken());
    }

    private ClaimedTaskResponse toResponse(ClaimedTask claimed, String runtimeType) {
        var task = claimed.task();
        var lease = claimed.lease();
        return new ClaimedTaskResponse(
                task.id().value(),
                task.type().name(),
                task.subjectType(),
                task.subjectId(),
                task.requiredCapabilities(),
                task.skillRef(),
                task.instruction(),
                task.instructionRevision(),
                task.priority().value(),
                claimed.attemptId().value(),
                claimed.attemptNo(),
                lease.token().value(),
                lease.acquiredAt(),
                lease.expiresAt(),
                workspaceResponse(claimed),
                claimed.inputs().stream().map(input -> new TaskInputResponse(
                        input.taskId().value(), input.taskType().name(), input.repositoryRef(), input.baseRevision(),
                        input.resultRevision(), input.publicationRef(), input.changedFiles(),
                        input.completionVerdict() == null ? null : input.completionVerdict().name(),
                        input.completionSummary(), input.attemptId())).toList(),
                goalPolicy != null && goalPolicy.managed(task) ? goalPolicy.guidance(task, runtimeType)
                    : recommendationPolicy != null && recommendationPolicy.managed(task) ? recommendationPolicy.guidance(task, runtimeType)
                        : delegatedGuidance(task,runtimeType,claimed.attemptNo()),
                outputs == null ? null : outputs.store(task, claimed.attemptId().value(), claimed.inputs()),
                workflowAiPreferences == null ? null : workflowAiPreferences.forTask(task));
    }

    private AiProfileCatalog.Guidance delegatedGuidance(me.asu.aidev.work.domain.WorkTask task,String runtime,int attempt) {
        var normal=aiProfiles.guidance(task.type(),runtime,attempts.findPreviousAiAttempt(task.id(),attempt).orElse(null));
        return delegationPolicy==null?normal:delegationPolicy.guidance(task,runtime,normal);
    }

    private WorkspaceResponse workspaceResponse(ClaimedTask claimed) {
        var assignment = claimed.workspace();
        if (assignment == null) return null;
        var location = workspaceRegistry == null ? null : workspaceRegistry.location(assignment.repositoryRef());
        return new WorkspaceResponse(assignment.repositoryRef(), assignment.baseRevision(),
                assignment.workspaceKey(), assignment.branchName(),
                location == null ? null : location.workspaceId(),
                location == null ? null : location.workspaceName(),
                location == null ? null : location.workspaceRoot().toString(),
                location == null ? null : location.repositoryPath().toString(),
                assignment.baseRevision() == null ? "DIRECT" : "GIT_WORKTREE");
    }

    private static WorkerId workerId(LeaseActionRequest request) {
        return new WorkerId(required(request.workerId(), "workerId"));
    }

    private static LeaseToken leaseToken(LeaseActionRequest request) {
        return new LeaseToken(required(request.leaseToken(), "leaseToken"));
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is blank");
        return value.strip();
    }

    public record RegisterRequest(String workerId, String runtimeType, Set<String> capabilities, int maxConcurrency) {}
    public record WorkerResponse(String workerId, String status, int activeLeases, int maxConcurrency) {}
    public record ClaimRequest(String workerId) {}
    public record HeartbeatResponse(boolean stopRequested) {}
    public record StopAcknowledgementRequest(String workerId, String leaseToken) {}
    public record LeaseActionRequest(String workerId, String leaseToken, String externalSessionId) {}
    public record StartTaskRequest(String workerId, String leaseToken, String externalSessionId,
                                   String profileId, String profileVersion, String selectionReason) {}
    public record FailTaskRequest(String workerId, String leaseToken, String externalSessionId,
                                  String failureReason, String failureCategory) {
        public FailTaskRequest(String workerId, String leaseToken, String externalSessionId, String failureReason) {
            this(workerId, leaseToken, externalSessionId, failureReason, null);
        }
    }
    public record CompleteTaskRequest(
            String workerId, String leaseToken, String externalSessionId,
            WorkspaceResultRequest workspaceResult, CompletionReportRequest report) {
        public CompleteTaskRequest(String workerId, String leaseToken, String externalSessionId,
                                   WorkspaceResultRequest workspaceResult) {
            this(workerId, leaseToken, externalSessionId, workspaceResult, null);
        }
    }
    public record CompletionReportRequest(String verdict, String summary) {}
    public record WorkspaceResultRequest(String resultRevision, List<String> changedFiles, String publicationRef) {
        public WorkspaceResultRequest(String resultRevision, List<String> changedFiles) {
            this(resultRevision, changedFiles, null);
        }
    }
    public record WorkspaceResponse(String repositoryRef, String baseRevision, String workspaceKey, String branchName,
                                    String workspaceId, String workspaceName, String workspaceRoot,
                                    String repositoryPath, String mode) {
        public WorkspaceResponse(String repositoryRef, String baseRevision, String workspaceKey, String branchName) {
            this(repositoryRef, baseRevision, workspaceKey, branchName, null, null, null, null,
                    baseRevision == null ? "DIRECT" : "GIT_WORKTREE");
        }
    }
    public record TaskInputResponse(String taskId, String taskType, String repositoryRef, String baseRevision,
                                    String resultRevision, String publicationRef, List<String> changedFiles,
                                    String completionVerdict, String completionSummary, String attemptId) {
        public TaskInputResponse(String taskId, String taskType, String repositoryRef, String baseRevision,
                String resultRevision, String publicationRef, List<String> changedFiles,
                String completionVerdict, String completionSummary) {
            this(taskId, taskType, repositoryRef, baseRevision, resultRevision, publicationRef,
                    changedFiles, completionVerdict, completionSummary, null);
        }
    }
    public record CompletionResponse(String taskId, List<String> newlyReadyTaskIds, List<String> generatedTaskIds) {}
    public record ClaimedTaskResponse(
            String taskId,
            String taskType,
            String subjectType,
            String subjectId,
            Set<String> requiredCapabilities,
            String skillRef,
            String instruction,
            int instructionRevision,
            int priority,
            String attemptId,
            int attemptNo,
            String leaseToken,
            Instant leaseAcquiredAt,
            Instant leaseExpiresAt,
            WorkspaceResponse workspace,
            List<TaskInputResponse> inputs,
            AiProfileCatalog.Guidance aiGuidance,
            me.asu.aidev.output.application.TaskOutputService.Store outputStore,
            me.asu.aidev.workflow.application.WorkflowAiPreferences.Preferences aiPreferences) {
        public ClaimedTaskResponse(String taskId, String taskType, String subjectType, String subjectId,
                Set<String> requiredCapabilities, String skillRef, String instruction, int instructionRevision,
                int priority, String attemptId, int attemptNo, String leaseToken, Instant leaseAcquiredAt,
                Instant leaseExpiresAt, WorkspaceResponse workspace, List<TaskInputResponse> inputs,
                AiProfileCatalog.Guidance aiGuidance, me.asu.aidev.output.application.TaskOutputService.Store outputStore) {
            this(taskId, taskType, subjectType, subjectId, requiredCapabilities, skillRef, instruction,
                    instructionRevision, priority, attemptId, attemptNo, leaseToken, leaseAcquiredAt,
                    leaseExpiresAt, workspace, inputs, aiGuidance, outputStore, null);
        }
        public ClaimedTaskResponse(String taskId, String taskType, String subjectType, String subjectId,
                Set<String> requiredCapabilities, String skillRef, String instruction, int instructionRevision,
                int priority, String attemptId, int attemptNo, String leaseToken, Instant leaseAcquiredAt,
                Instant leaseExpiresAt, WorkspaceResponse workspace, List<TaskInputResponse> inputs,
                AiProfileCatalog.Guidance aiGuidance) {
            this(taskId, taskType, subjectType, subjectId, requiredCapabilities, skillRef, instruction,
                    instructionRevision, priority, attemptId, attemptNo, leaseToken, leaseAcquiredAt,
                    leaseExpiresAt, workspace, inputs, aiGuidance, null);
        }
    }
}
