package me.asu.aidev.workflow.application;

import java.util.List;
import java.util.Set;
import me.asu.aidev.execution.application.TaskCompletionContext;
import me.asu.aidev.execution.application.TaskCompletionHandler;
import me.asu.aidev.execution.domain.CompletionVerdict;
import me.asu.aidev.work.domain.TaskId;
import me.asu.aidev.work.domain.TaskType;
import me.asu.aidev.workflow.domain.WorkflowPlan;
import me.asu.aidev.workflow.domain.WorkflowTaskPosition;
import org.springframework.stereotype.Component;

/** Advances a selected workflow only after an independent verdict for the current step. */
@Component
public class WorkflowStepCompletedHandler implements TaskCompletionHandler {
    private final WorkflowPlanService plans;
    private final WorkflowRepairDecisionHandler repairDecisions;

    public WorkflowStepCompletedHandler(WorkflowPlanService plans, WorkflowRepairDecisionHandler repairDecisions) {
        this.plans = plans;
        this.repairDecisions = repairDecisions;
    }

    @Override public Set<TaskType> supportedTypes() { return Set.of(TaskType.EXTERNAL_WORK); }

    @Override public boolean supports(TaskCompletionContext context) {
        return TaskCompletionHandler.super.supports(context)
                && (WorkflowPlanService.SUBJECT.equals(context.completedTask().subjectType())
                    || repairDecisions.supports(context));
    }

    @Override public List<TaskId> onCompleted(TaskCompletionContext context) {
        if (repairDecisions.supports(context)) return repairDecisions.onCompleted(context);
        var task = context.completedTask();
        WorkflowPlan plan = plans.get(task.subjectId());
        if (plan.state() != WorkflowPlan.State.CONFIRMED) return List.of();
        var position = WorkflowTaskPosition.parse(plan, task.id())
                .orElseThrow(() -> new IllegalStateException("completed task does not belong to its plan"));
        int step = position.step(), round = position.round();
        if (context.completionReport() == null) throw new IllegalStateException("workflow completion requires a report");
        if (!position.review()) {
            plans.createReview(plan, step, round);
            return List.of(plans.effectiveReviewId(plan, step, round));
        }
        if (!task.id().equals(plans.effectiveReviewId(plan, step, round))) return List.of();
        if (context.completionReport().verdict() == CompletionVerdict.REPAIR_REQUIRED) {
            // Every round beyond the initial automatic budget needs a separate explicit authorization.
            if (round >= WorkflowPlanService.MAX_ATTEMPTS_PER_STEP) return List.of();
            var inputs = new java.util.LinkedHashSet<TaskId>(plans.acceptedInputs(plan, step - 1));
            inputs.add(WorkflowPlanService.executionId(plan, step, round));
            inputs.add(task.id());
            plans.createExecution(plan, step, round + 1, Set.copyOf(inputs));
            return List.of(WorkflowPlanService.executionId(plan, step, round + 1));
        }
        if (step + 1 == plan.spec().steps().size()) return List.of();
        TaskId next = WorkflowPlanService.executionId(plan, step + 1, 1);
        plans.createExecution(plan, step + 1, 1, plans.acceptedInputs(plan, step));
        return List.of(next);
    }
}
