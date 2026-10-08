package me.asu.aidev.development.application;

import me.asu.aidev.development.domain.EngineeringGateStatus;
import me.asu.aidev.development.port.EngineeringGateFinalizedListener;
import me.asu.aidev.development.port.EngineeringGateRepository;
import me.asu.aidev.execution.application.TaskCompletionContext;
import me.asu.aidev.execution.application.TaskCompletionHandler;
import me.asu.aidev.execution.domain.CompletionVerdict;
import me.asu.aidev.execution.port.ExecutionAttemptRepository;
import me.asu.aidev.work.application.CreateWorkTaskCommand;
import me.asu.aidev.work.application.WorkTaskService;
import me.asu.aidev.work.domain.TaskId;
import me.asu.aidev.work.domain.TaskState;
import me.asu.aidev.work.domain.TaskType;
import me.asu.aidev.work.port.TaskDependencyRepository;
import me.asu.aidev.work.port.WorkTaskRepository;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Joins the parallel review + verification branches. If either requests repair, one durable REPAIR
 * task is created. The common source task row is locked so concurrent branch completions cannot
 * lose the join notification or create duplicate repair work.
 */
@Component
public class ReviewVerificationCompletedHandler implements TaskCompletionHandler {
    private final WorkTaskService taskService;
    private final WorkTaskRepository tasks;
    private final TaskDependencyRepository dependencies;
    private final ExecutionAttemptRepository attempts;
    private final EngineeringGateRepository gates;
    private final List<EngineeringGateFinalizedListener> gateListeners;

    public ReviewVerificationCompletedHandler(WorkTaskService taskService,
                                              WorkTaskRepository tasks,
                                              TaskDependencyRepository dependencies,
                                              ExecutionAttemptRepository attempts,
                                              EngineeringGateRepository gates,
                                              List<EngineeringGateFinalizedListener> gateListeners) {
        this.taskService = taskService;
        this.tasks = tasks;
        this.dependencies = dependencies;
        this.attempts = attempts;
        this.gates = gates;
        this.gateListeners = List.copyOf(gateListeners);
    }

    @Override
    public Set<TaskType> supportedTypes() {
        return Set.of(TaskType.CODE_REVIEW, TaskType.VERIFICATION, TaskType.INTEGRATION_VERIFICATION);
    }

    @Override
    public List<TaskId> onCompleted(TaskCompletionContext context) {
        List<TaskId> sourceDependencies = dependencies.findDependencies(context.completedTask().id());
        if (sourceDependencies.size() != 1) {
            throw new IllegalStateException("review/verification task must have exactly one code-change dependency: "
                    + context.completedTask().id().value());
        }
        TaskId sourceId = sourceDependencies.getFirst();
        if (!tasks.lock(sourceId)) {
            throw new IllegalStateException("code-change source task not found: " + sourceId.value());
        }

        TaskId reviewId = DevelopmentTaskIds.review(sourceId);
        TaskId verificationId = DevelopmentTaskIds.verification(sourceId);
        var review = tasks.find(reviewId).orElse(null);
        var verification = tasks.find(verificationId).orElse(null);
        if (review == null || verification == null
                || review.state() != TaskState.COMPLETED
                || verification.state() != TaskState.COMPLETED) {
            return List.of();
        }

        var reviewReport = attempts.findCompletionReport(reviewId)
                .orElseThrow(() -> new IllegalStateException("completed review has no completion report: " + reviewId.value()));
        var verificationReport = attempts.findCompletionReport(verificationId)
                .orElseThrow(() -> new IllegalStateException("completed verification has no completion report: " + verificationId.value()));
        EngineeringGateStatus gateStatus = reviewReport.verdict() == CompletionVerdict.PASS
                && verificationReport.verdict() == CompletionVerdict.PASS
                ? EngineeringGateStatus.PASSED
                : EngineeringGateStatus.REPAIR_REQUIRED;
        if (!gates.finalizeGate(sourceId, reviewId, verificationId, gateStatus, Instant.now())) {
            throw new IllegalStateException("cannot finalize engineering gate for code-change source: " + sourceId.value());
        }
        var finalizedGate = gates.findBySourceTask(sourceId)
                .orElseThrow(() -> new IllegalStateException("finalized engineering gate not found: " + sourceId.value()));
        for (EngineeringGateFinalizedListener listener : gateListeners) {
            listener.onFinalized(finalizedGate);
        }
        if (gateStatus == EngineeringGateStatus.PASSED) {
            return List.of();
        }

        TaskId repairId = DevelopmentTaskIds.repair(sourceId);
        if (tasks.find(repairId).isPresent()) return List.of();

        var source = tasks.find(sourceId)
                .orElseThrow(() -> new IllegalStateException("code-change source task not found: " + sourceId.value()));
        var sourceResult = attempts.findCompletedInput(sourceId)
                .orElseThrow(() -> new IllegalStateException("code-change source result not found: " + sourceId.value()));

        taskService.createIfAbsent(new CreateWorkTaskCommand(
                repairId,
                TaskType.REPAIR,
                source.subjectType(),
                source.subjectId(),
                CodeChangeCompletedHandler.downstreamCapabilities(source.requiredCapabilities(), "repair"),
                CodeChangeCompletedHandler.isIntegrationLineage(source.type(), source.skillRef()) ? "integration-repair" : "repair",
                "Repair the authoritative code result using all upstream review and verification findings. " +
                        "Address every REPAIR_REQUIRED finding, run focused checks, and modify repository files as needed.",
                source.repositoryRef(),
                source.baseRevision() == null ? null : sourceResult.resultRevision(),
                source.priority(),
                Set.of(sourceId, reviewId, verificationId)));
        return List.of(repairId);
    }
}
