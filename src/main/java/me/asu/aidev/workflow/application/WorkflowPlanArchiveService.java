package me.asu.aidev.workflow.application;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import me.asu.aidev.execution.domain.CompletionVerdict;
import me.asu.aidev.execution.port.ExecutionAttemptRepository;
import me.asu.aidev.work.domain.TaskState;
import me.asu.aidev.workflow.domain.WorkflowPlan;
import me.asu.aidev.workflow.jdbc.JdbcWorkflowPlanArchiveRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkflowPlanArchiveService {
    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.beans.factory.ObjectProvider<WorkflowGitDeliveryService> gitDelivery;
    private me.asu.aidev.authority.AuthorityBoundary delegation;
    @org.springframework.beans.factory.annotation.Autowired
    public void setAuthorityBoundary(me.asu.aidev.authority.AuthorityBoundary boundary) { this.delegation=boundary; }
    private me.asu.aidev.authority.ManagedPlanAdmission managedPlans;
    @org.springframework.beans.factory.annotation.Autowired
    public void setManagedPlanAdmission(me.asu.aidev.authority.ManagedPlanAdmission gate) { this.managedPlans=gate; }
    private void managedSerialization(String id) { if(managedPlans!=null) managedPlans.serializePlan(id); }
    private static final Duration DELETE_DELAY = Duration.ofDays(30);
    private final WorkflowPlanService plans;
    private final ExecutionAttemptRepository attempts;
    private final JdbcWorkflowPlanArchiveRepository archives;
    private final me.asu.aidev.workflow.jdbc.JdbcWorkflowHumanOutcomeRepository outcomes;

    public WorkflowPlanArchiveService(WorkflowPlanService plans, ExecutionAttemptRepository attempts,
                                      JdbcWorkflowPlanArchiveRepository archives,
                                      me.asu.aidev.workflow.jdbc.JdbcWorkflowHumanOutcomeRepository outcomes) {
        this.plans = plans;
        this.attempts = attempts;
        this.archives = archives;
        this.outcomes = outcomes;
    }

    public boolean archived(String id) { return archives.archived(id); }

    public Optional<Instant> deleteAfter(String id) { return archives.deleteAfter(id); }

    public boolean archivable(String id) {
        WorkflowPlan plan = plans.get(id);
        if (gitDelivery != null && gitDelivery.getObject().pending(id)) return false;
        if (outcomes.find(id).filter(outcome -> outcome.status().equals("PENDING")).isPresent()) return false;
        if (plan.state() != WorkflowPlan.State.STOPPED && plan.state() != WorkflowPlan.State.WITHDRAWN
                && plan.state() != WorkflowPlan.State.COMPLETED) {
            if (plan.state() != WorkflowPlan.State.CONFIRMED) return false;
            int last = plan.spec().steps().size() - 1;
            boolean passed = false;
            for (int attempt : plans.rounds(plan, last)) {
                passed |= attempts.findCompletionReport(plans.effectiveReviewId(plan, last, attempt))
                        .map(report -> report.verdict() == CompletionVerdict.PASS).orElse(false);
            }
            if (!passed) return false;
        }
        return plans.steps(id).stream().allMatch(task -> task.state() == TaskState.COMPLETED
                || task.state() == TaskState.FAILED || task.state() == TaskState.CANCELLED);
    }

    @Transactional
    public void archive(String id, String actor) {
        if(delegation!=null) delegation.changing("PLAN:"+id);
        managedSerialization(id);
        if (!archivable(id)) throw new IllegalStateException("only finished plans can be archived");
        if (!archives.archive(id, actor, Instant.now())) throw new IllegalStateException("plan is already archived");
    }

    @Transactional
    public void scheduleDeletion(String id, String actor) {
        if(delegation!=null) delegation.changing("PLAN:"+id);
        managedSerialization(id);
        Instant now = Instant.now();
        if (!archives.scheduleDeletion(id, actor, now, now.plus(DELETE_DELAY))) {
            throw new IllegalStateException("plan must be archived and not already scheduled for deletion");
        }
    }

    @Transactional
    public void undoDeletion(String id) {
        if(delegation!=null) delegation.changing("PLAN:"+id);
        managedSerialization(id);
        if (!archives.undoDeletion(id, Instant.now())) {
            throw new IllegalStateException("plan has no pending deletion or the 30-day period expired");
        }
    }
}
