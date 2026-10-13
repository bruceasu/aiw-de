package me.asu.aidev.workflow.application;

import java.util.List;
import java.util.Set;
import me.asu.aidev.execution.application.TaskCompletionContext;
import me.asu.aidev.execution.application.TaskCompletionHandler;
import me.asu.aidev.execution.domain.CompletionVerdict;
import me.asu.aidev.work.domain.TaskId;
import me.asu.aidev.work.domain.TaskType;
import me.asu.aidev.workflow.domain.WorkflowPlan;
import org.springframework.stereotype.Component;

/** Advances a selected workflow only after an independent verdict for the current step. */
@Component
public class WorkflowStepCompletedHandler implements TaskCompletionHandler {
    private final WorkflowPlanService plans;

    public WorkflowStepCompletedHandler(WorkflowPlanService plans) { this.plans = plans; }

    @Override public Set<TaskType> supportedTypes() { return Set.of(TaskType.EXTERNAL_WORK); }

    @Override public boolean supports(TaskCompletionContext context) {
        return WorkflowPlanService.SUBJECT.equals(context.completedTask().subjectType());
    }

    @Override public List<TaskId> onCompleted(TaskCompletionContext context) {
        var task = context.completedTask();
        WorkflowPlan plan = plans.get(task.subjectId());
        if (plan.state() != WorkflowPlan.State.CONFIRMED) return List.of();
        for (int step = 0; step < plan.spec().steps().size(); step++) {
            for (int attempt = 1; attempt <= WorkflowPlanService.MAX_ATTEMPTS_PER_STEP; attempt++) {
                if (task.id().equals(WorkflowPlanService.executionId(plan, step, attempt))) {
                    if (context.completionReport() == null) {
                        throw new IllegalStateException("workflow execution requires an output report");
                    }
                    plans.createReview(plan, step, attempt);
                    return List.of(WorkflowPlanService.reviewId(plan, step, attempt));
                }
                if (!task.id().equals(WorkflowPlanService.reviewId(plan, step, attempt))) continue;
                if (context.completionReport() == null) {
                    throw new IllegalStateException("workflow review requires a verdict report");
                }
                if (context.completionReport().verdict() == CompletionVerdict.REPAIR_REQUIRED) {
                    if (attempt == WorkflowPlanService.MAX_ATTEMPTS_PER_STEP) return List.of();
                    TaskId next = WorkflowPlanService.executionId(plan, step, attempt + 1);
                    var inputs = new java.util.LinkedHashSet<TaskId>();
                    if (step > 0) inputs.addAll(plans.acceptedInputs(plan, step - 1));
                    inputs.add(WorkflowPlanService.executionId(plan, step, attempt));
                    inputs.add(task.id());
                    plans.createExecution(plan, step, attempt + 1, Set.copyOf(inputs));
                    return List.of(next);
                }
                if (step + 1 == plan.spec().steps().size()) return List.of();
                TaskId next = WorkflowPlanService.executionId(plan, step + 1, 1);
                plans.createExecution(plan, step + 1, 1, plans.acceptedInputs(plan, step));
                return List.of(next);
            }
        }
        throw new IllegalStateException("completed workflow task does not belong to its plan: " + task.id().value());
    }
}
