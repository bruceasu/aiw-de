package me.asu.aidev.workflow.application;

import me.asu.aidev.work.domain.WorkTask;
import me.asu.aidev.workflow.domain.WorkflowTaskPosition;
import me.asu.aidev.workflow.port.WorkflowPlanRepository;
import org.springframework.stereotype.Component;

/** Optional preferences from the confirmed plan; they never grant model or execution authority. */
@Component
public class WorkflowAiPreferences {
    private final WorkflowPlanRepository plans;

    public WorkflowAiPreferences(WorkflowPlanRepository plans) { this.plans = plans; }

    public Preferences forTask(WorkTask task) {
        if (!"flexible-plan".equals(task.subjectType())) return null;
        return plans.find(task.subjectId()).flatMap(plan -> WorkflowTaskPosition.parse(plan, task.id())
                .map(position -> plan.spec().steps().get(position.step())))
                .filter(step -> step.model() != null || step.effort() != null)
                .map(step -> new Preferences(step.model(), step.effort())).orElse(null);
    }

    public record Preferences(String model, String effort) {}
}
