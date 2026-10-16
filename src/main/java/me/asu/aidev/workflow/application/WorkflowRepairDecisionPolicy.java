package me.asu.aidev.workflow.application;

import java.time.Instant;
import me.asu.aidev.execution.port.WorkerTaskClaimPolicy;
import me.asu.aidev.work.domain.WorkTask;
import me.asu.aidev.worker.domain.Worker;
import me.asu.aidev.workflow.jdbc.JdbcWorkflowRepairAuthorizationRepository;
import org.springframework.stereotype.Component;

/** A capability alone does not authorize another Worker to consume a named delegation. */
@Component
public class WorkflowRepairDecisionPolicy implements WorkerTaskClaimPolicy {
    private final JdbcWorkflowRepairAuthorizationRepository authorizations;
    public WorkflowRepairDecisionPolicy(JdbcWorkflowRepairAuthorizationRepository authorizations) { this.authorizations = authorizations; }
    @Override public boolean canClaim(WorkTask task) {
        return !WorkflowRepairAuthorizationService.SUBJECT.equals(task.subjectType());
    }
    @Override public boolean canClaim(WorkTask task, Worker worker) {
        if (!WorkflowRepairAuthorizationService.SUBJECT.equals(task.subjectType())) return true;
        return authorizations.decision(task.id().value()).filter(grant -> grant.status().equals("PENDING")
                && worker.id().value().equals(grant.delegateWorkerId()) && grant.expiresAt().isAfter(Instant.now())
                && authorizations.planUnchanged(grant)).isPresent();
    }
}
