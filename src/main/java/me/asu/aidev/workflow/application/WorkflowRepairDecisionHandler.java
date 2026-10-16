package me.asu.aidev.workflow.application;

import java.util.List;
import me.asu.aidev.execution.application.TaskCompletionContext;
import me.asu.aidev.work.domain.TaskId;
import org.springframework.stereotype.Component;

/** Subject-specific delegate of the single EXTERNAL_WORK completion authority. */
@Component
public class WorkflowRepairDecisionHandler {
    private final WorkflowRepairAuthorizationService authorizations;
    public WorkflowRepairDecisionHandler(WorkflowRepairAuthorizationService authorizations) { this.authorizations = authorizations; }
    public boolean supports(TaskCompletionContext context) {
        return WorkflowRepairAuthorizationService.SUBJECT.equals(context.completedTask().subjectType());
    }
    public List<TaskId> onCompleted(TaskCompletionContext context) { return authorizations.decide(context); }
}
