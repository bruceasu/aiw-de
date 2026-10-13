package me.asu.aidev.webconsole.application;

import me.asu.aidev.approval.application.ApprovalService;
import me.asu.aidev.approval.application.DecideApprovalCommand;
import me.asu.aidev.approval.application.RequestApprovalCommand;
import me.asu.aidev.approval.domain.ApprovalChannel;
import me.asu.aidev.approval.domain.ApprovalDecision;
import me.asu.aidev.approval.domain.ApprovalRequest;
import me.asu.aidev.approval.domain.ApprovalRequestId;
import me.asu.aidev.autonomous.application.AutonomousRunService;
import me.asu.aidev.execution.application.TaskExecutionService;
import me.asu.aidev.fleet.application.FleetAdmissionBudgetService;
import me.asu.aidev.fleet.domain.FleetAdmissionBudget;
import me.asu.aidev.work.domain.TaskPriority;
import me.asu.aidev.work.domain.TaskId;
import me.asu.aidev.work.domain.WorkTask;
import me.asu.aidev.work.application.WorkTaskService;
import me.asu.aidev.work.application.TaskWorkspaceReplacementService;
import me.asu.aidev.worker.application.WorkerService;
import me.asu.aidev.worker.domain.WorkerId;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

/** Browser adapter facade. All mutations delegate to existing application-service authority. */
@Service
public class WebConsoleActionService {
    private final AutonomousRunService runs;
    private final ApprovalService approvals;
    private final FleetAdmissionBudgetService fleetScheduling;
    private final WorkerService workers;
    private final TaskExecutionService taskExecution;
    private final WorkTaskService tasks;
    private TaskWorkspaceReplacementService workspaceReplacements;

    @Autowired
    public void setWorkspaceReplacements(TaskWorkspaceReplacementService workspaceReplacements) {
        this.workspaceReplacements = workspaceReplacements;
    }

    public WebConsoleActionService(AutonomousRunService runs,
                                   ApprovalService approvals,
                                   FleetAdmissionBudgetService fleetScheduling,
                                   WorkerService workers,
                                   TaskExecutionService taskExecution,
                                   WorkTaskService tasks) {
        this.runs = runs;
        this.approvals = approvals;
        this.fleetScheduling = fleetScheduling;
        this.workers = workers;
        this.taskExecution = taskExecution;
        this.tasks = tasks;
    }

    public void pause(String runId, String actor, String reason) { runs.pause(runId, actor, reason); }
    public void resume(String runId, String actor, String reason) { runs.resume(runId, actor, reason); }
    public void recover(String runId, String actor) { runs.recover(runId, actor); }
    public void cancel(String runId, String actor, String reason) { runs.cancel(runId, actor, reason); }
    public void updateRunScheduling(String runId, int maxParallelism, String actor, String reason) {
        runs.updateMaxParallelism(runId, maxParallelism, actor, reason);
    }

    public FleetAdmissionBudget updateFleetScheduling(int maxParallelism, String actor, String reason) {
        return fleetScheduling.updateMaxParallelism(maxParallelism, actor, reason);
    }

    public ApprovalRequest requestApproval(String runId, String actor) {
        var run = runs.find(runId).orElseThrow(() -> new NoSuchElementException("autonomous run not found: " + runId));
        if (run.terminalSourceTaskId() == null) {
            throw new IllegalStateException("run has no terminal engineering result available for approval");
        }
        return approvals.request(new RequestApprovalCommand(
                run.terminalSourceTaskId(), actor, ApprovalChannel.WEB, "web-console"));
    }

    public ApprovalRequest approve(String approvalRequestId, String actor, String note) {
        return approvals.decide(new DecideApprovalCommand(
                new ApprovalRequestId(required(approvalRequestId, "approvalRequestId")),
                ApprovalDecision.APPROVE, actor, blankToNull(note)));
    }

    public ApprovalRequest reject(String approvalRequestId, String actor, String note) {
        return approvals.decide(new DecideApprovalCommand(
                new ApprovalRequestId(required(approvalRequestId, "approvalRequestId")),
                ApprovalDecision.REJECT, actor, blankToNull(note)));
    }

    public void drainWorker(String workerId) { workers.drain(new WorkerId(required(workerId, "workerId"))); }
    public void offlineWorker(String workerId) { workers.offline(new WorkerId(required(workerId, "workerId"))); }

    public void reviseTaskInstruction(String runId, String taskId, long expectedVersion, int expectedRevision,
                                       String instruction, String actor, String reason) {
        WorkTask task = runTask(runId, taskId);
        taskExecution.reviseInstruction(task.id(), expectedVersion, expectedRevision, instruction, actor, reason);
    }

    public void stopTask(String runId, String taskId, String actor, String reason) {
        taskExecution.requestStop(runTask(runId, taskId).id(), actor, reason);
    }

    public void forceRequeueTask(String runId, String taskId, String actor, String reason, boolean warningConfirmed) {
        taskExecution.forceRequeue(runTask(runId, taskId).id(), actor, reason, warningConfirmed);
    }

    public WorkTask createCorrectionTask(String runId, String taskId, String instruction, String actor, String reason) {
        return tasks.createCorrection(runTask(runId, taskId).id(), instruction, actor, reason);
    }

    public TaskWorkspaceReplacementService.Replacement replaceTaskWorkspace(String runId, String taskId,
            long expectedVersion, String repositoryRef, String mode, String revision, String actor, String reason) {
        return workspaceReplacements.request(runId, runTask(runId, taskId).id(), expectedVersion,
                repositoryRef, mode, revision, actor, reason);
    }

    public void recordRejectedWorkspaceReplacement(String runId, String taskId, String repositoryRef,
            String revision, String actor, String reason, String failure) {
        workspaceReplacements.recordRejected(runId, new TaskId(taskId), repositoryRef, revision, actor, reason, failure);
    }

    private WorkTask runTask(String runId, String taskId) {
        WorkTask task = tasks.get(new TaskId(required(taskId, "taskId")));
        if (!"AUTONOMOUS_REQUIREMENT".equals(task.subjectType()) || !runId.equals(task.subjectId())) {
            throw new IllegalStateException("task does not belong to this Run");
        }
        return task;
    }

    private static Set<String> parseCapabilities(String capabilities) {
        if (capabilities == null || capabilities.isBlank()) return Set.of();
        LinkedHashSet<String> parsed = new LinkedHashSet<>();
        Arrays.stream(capabilities.split("[,\\s]+"))
                .map(String::strip)
                .filter(value -> !value.isEmpty())
                .forEach(parsed::add);
        return Set.copyOf(parsed);
    }

    private static TaskPriority parsePriority(String value) {
        if (value == null || value.isBlank()) return TaskPriority.NORMAL;
        String normalized = value.strip().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "LOW" -> TaskPriority.LOW;
            case "NORMAL" -> TaskPriority.NORMAL;
            case "HIGH" -> TaskPriority.HIGH;
            case "CRITICAL" -> TaskPriority.CRITICAL;
            default -> new TaskPriority(Integer.parseInt(normalized));
        };
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is blank");
        return value.strip();
    }

    private static String blankToNull(String value) {
        if (value == null) return null;
        String normalized = value.strip();
        return normalized.isEmpty() ? null : normalized;
    }
}
