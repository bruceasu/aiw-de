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
    @Autowired private me.asu.aidev.workflow.application.PlanLocations locations;
    @Autowired private me.asu.aidev.execution.application.TaskHandoffs handoffs;
    @Autowired private me.asu.aidev.execution.pricing.ModelPriceRepairResults priceRepairResults;
    private me.asu.aidev.execution.skills.SkillFiles skillFiles;
    @Autowired public void setSkillFiles(me.asu.aidev.execution.skills.SkillFiles value) { skillFiles=value; }
    private me.asu.aidev.execution.config.ExecutionConfigurations configurations;
    @Autowired public void setExecutionConfigurations(me.asu.aidev.execution.config.ExecutionConfigurations configurations) { this.configurations=configurations; }
    private me.asu.aidev.authority.AuthorityTaskPolicy delegationPolicy;
    @Autowired
    public void setDelegationPolicy(me.asu.aidev.authority.AuthorityTaskPolicy policy) { this.delegationPolicy=policy; }
    private me.asu.aidev.goal.application.GoalTaskPolicy goalPolicy;
    @Autowired
    public void setGoalPolicy(me.asu.aidev.goal.application.GoalTaskPolicy policy) { this.goalPolicy = policy; }
    private final WorkerService workers;
    @Autowired private me.asu.aidev.authority.AuthorityRepository authorityRepository;
    @Autowired private org.springframework.beans.factory.ObjectProvider<me.asu.aidev.questions.NativeQuestions> nativeQuestions;
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
        var worker=workers.get(workerId);
        if(worker.capabilities().contains(me.asu.aidev.execution.run.NativePlanOwners.CAPABILITY)) {
            String expected=me.asu.aidev.authority.AuthorityRepository.hash(authorityRepository.encode(new java.util.TreeSet<>(worker.capabilities())));
            if(!expected.equals(request.capabilitiesDigest()))throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,"Worker capability snapshot changed; register a fresh compatible Worker");
        }
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

    public record WorkspaceBaselineRequest(String workerId,String leaseToken,String baseRevision) {}
    // Baseline pinning is Worker-local; no public endpoint.
    public me.asu.aidev.execution.domain.WorkspaceAssignment pinBaseline(@PathVariable String taskId,@RequestBody WorkspaceBaselineRequest request) {
        return executions.pinWorkspaceBaseline(new TaskId(taskId),new WorkerId(required(request.workerId(),"workerId")),
                new LeaseToken(required(request.leaseToken(),"leaseToken")),request.baseRevision());
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

    @PostMapping("/tasks/{taskId}/handoff")
    public void handoff(@PathVariable String taskId,@RequestBody HandoffRequest request) {
        handoffs.record(taskId,required(request.workerId(),"workerId"),required(request.leaseToken(),"leaseToken"),request.handoff());
    }

    @org.springframework.transaction.annotation.Transactional
    @PostMapping("/tasks/{taskId}/complete")
    public CompletionResponse complete(@PathVariable String taskId, @RequestBody CompleteTaskRequest request) {
        WorkspaceResult workspaceResult = request.gitResult() == null ? null : new WorkspaceResult(
                request.gitResult().resultRevision(), request.gitResult().changedFiles(),
                request.gitResult().publicationRef());
        CompletionReport report = request.report() == null ? null : new CompletionReport(
                CompletionVerdict.valueOf(required(request.report().verdict(), "report.verdict")),
                required(request.report().summary(), "report.summary"));
        if(request.report()==null) throw new IllegalArgumentException("Completion report required");
        if(executions.replayedGuardedCompletion(new TaskId(taskId),new WorkerId(required(request.workerId(),"workerId")),new LeaseToken(required(request.leaseToken(),"leaseToken")),workspaceResult,report))
        { handoffs.verifyReplay(request.leaseToken(),request.handoff()); return new CompletionResponse(taskId,List.of(),List.of()); }
        if (report.verdict() == CompletionVerdict.PASS && priceRepairResults != null)
            priceRepairResults.requireSubmittedIfRepair(taskId, request.workerId(), request.leaseToken());
        handoffs.record(taskId,request.workerId(),request.leaseToken(),request.handoff());
        TaskCompletionResult result = executions.complete(
                new TaskId(taskId), new WorkerId(required(request.workerId(), "workerId")),
                new LeaseToken(required(request.leaseToken(), "leaseToken")),
                request.externalSessionId(), workspaceResult, report);

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

    }

    private ClaimedTaskResponse toResponse(ClaimedTask claimed,String runtimeType) {
        var task=claimed.task();var lease=claimed.lease();
        return new ClaimedTaskResponse(task.id().value(),task.type().name(),task.subjectType(),task.subjectId(),
                task.requiredCapabilities(),task.skillRef(),task.instruction(),task.instructionRevision(),task.priority().value(),
                claimed.attemptId().value(),claimed.attemptNo(),lease.token().value(),lease.acquiredAt(),lease.expiresAt(),
                task.subjectId(),me.asu.aidev.execution.pricing.ModelPriceRepairDispatch.SUBJECT.equals(task.subjectType()) ? null : locations.forTask(task),"aiw.handoff.v1",
                claimed.inputs().stream().map(input->new TaskInputResponse(input.taskId().value(),input.taskType().name(),
                    input.completionVerdict()==null?null:input.completionVerdict().name(),input.completionSummary(),input.attemptId(),handoffs.input(input.attemptId()))).toList(),
                configurations!=null && configurations.managed(task.id().value())?configurations.binding(task.id().value()).guidance()
                    :goalPolicy!=null && goalPolicy.managed(task)?goalPolicy.guidance(task,runtimeType)
                    :recommendationPolicy!=null && recommendationPolicy.managed(task)?recommendationPolicy.guidance(task,runtimeType)
                    :delegatedGuidance(task,runtimeType,claimed.attemptNo()),
                configurations!=null && configurations.managed(task.id().value())?null:workflowAiPreferences==null?null:workflowAiPreferences.forTask(task),
                configurations==null?null:configurations.payload(task.id().value(),claimed.attemptId().value()),
                skillFiles==null?null:skillFiles.payload(task.id().value(),claimed.attemptId().value()),
                goalPolicy!=null&&goalPolicy.managed(task)?goalPolicy.frozen(task).images():List.of());
    }

    private AiProfileCatalog.Guidance delegatedGuidance(me.asu.aidev.work.domain.WorkTask task,String runtime,int attempt) {
        var normal=aiProfiles.guidance(task.type(),runtime,attempts.findPreviousAiAttempt(task.id(),attempt).orElse(null));
        var selected=delegationPolicy==null?normal:delegationPolicy.guidance(task,runtime,normal);
        return nativeQuestions==null?selected:nativeQuestions.getObject().pinGuidance(task,runtime,selected);
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
    public record ClaimRequest(String workerId,String capabilitiesDigest) {
        public ClaimRequest(String workerId) { this(workerId,null); }
    }
    public record HeartbeatResponse(boolean stopRequested) {}
    public record StopAcknowledgementRequest(String workerId, String leaseToken) {}
    public record HandoffRequest(String workerId,String leaseToken,me.asu.aidev.execution.application.TaskHandoffs.Reference handoff) {}
    public record LeaseActionRequest(String workerId, String leaseToken, String externalSessionId) {}
    public record StartTaskRequest(String workerId, String leaseToken, String externalSessionId,
                                   String profileId, String profileVersion, String selectionReason) {}
    public record FailTaskRequest(String workerId, String leaseToken, String externalSessionId,
                                  String failureReason, String failureCategory) {
        public FailTaskRequest(String workerId, String leaseToken, String externalSessionId, String failureReason) {
            this(workerId, leaseToken, externalSessionId, failureReason, null);
        }
    }
    public record CompleteTaskRequest(String workerId,String leaseToken,String externalSessionId,
            GitResultRequest gitResult,CompletionReportRequest report,
            me.asu.aidev.execution.application.TaskHandoffs.Reference handoff) {}
    public record CompletionReportRequest(String verdict,String summary) {}
    public record GitResultRequest(String resultRevision,List<String> changedFiles,String publicationRef) {}
    public record TaskInputResponse(String taskId,String taskType,String completionVerdict,String completionSummary,
            String attemptId,me.asu.aidev.execution.application.TaskHandoffs.Input handoff) {}
    public record CompletionResponse(String taskId,List<String> newlyReadyTaskIds,List<String> generatedTaskIds) {}
    public record ClaimedTaskResponse(String taskId,String taskType,String subjectType,String subjectId,
            Set<String> requiredCapabilities,String skillRef,String instruction,int instructionRevision,int priority,
            String attemptId,int attemptNo,String leaseToken,Instant leaseAcquiredAt,Instant leaseExpiresAt,
            String planId,String location,String handoffProtocol,List<TaskInputResponse> inputs,
            AiProfileCatalog.Guidance aiGuidance,
            me.asu.aidev.workflow.application.WorkflowAiPreferences.Preferences aiPreferences,
            me.asu.aidev.execution.config.ConfigurationContract.Payload executionConfiguration,
            me.asu.aidev.execution.skills.SkillFileContract.Payload skillFiles,
            @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_EMPTY)
            List<me.asu.aidev.goal.application.GoalImages.Image> modelImages) {}
}
