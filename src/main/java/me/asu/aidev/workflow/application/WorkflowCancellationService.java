package me.asu.aidev.workflow.application;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import me.asu.aidev.execution.domain.WorkspaceAssignment;
import me.asu.aidev.execution.port.ExecutionAttemptRepository;
import me.asu.aidev.work.domain.TaskState;
import me.asu.aidev.work.domain.WorkTask;
import me.asu.aidev.work.port.WorkTaskRepository;
import me.asu.aidev.workflow.domain.WorkflowPlan;
import me.asu.aidev.workflow.jdbc.JdbcWorkflowHumanOutcomeRepository;
import me.asu.aidev.workflow.port.WorkflowPlanRepository;
import me.asu.aidev.workspace.application.ManagedWorkspaceService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Cancels a plan only after its local, isolated Git branches can be safely discarded. */
@Service
public class WorkflowCancellationService {
    @org.springframework.beans.factory.annotation.Autowired private org.springframework.jdbc.core.JdbcTemplate guardJdbc;
    private me.asu.aidev.authority.AuthorityBoundary delegation;
    @org.springframework.beans.factory.annotation.Autowired
    public void setAuthorityBoundary(me.asu.aidev.authority.AuthorityBoundary boundary) { this.delegation=boundary; }
    private me.asu.aidev.authority.ManagedPlanAdmission managedPlans;
    @org.springframework.beans.factory.annotation.Autowired
    public void setManagedPlanAdmission(me.asu.aidev.authority.ManagedPlanAdmission gate) { this.managedPlans=gate; }
    private static final Duration GIT_TIMEOUT = Duration.ofSeconds(15);
    private final WorkflowPlanService service;
    private final WorkflowPlanRepository plans;
    private final WorkTaskRepository tasks;
    private final ExecutionAttemptRepository attempts;
    private final ManagedWorkspaceService workspaces;
    private final JdbcWorkflowHumanOutcomeRepository outcomes;

    public WorkflowCancellationService(WorkflowPlanService service, WorkflowPlanRepository plans,
                                       WorkTaskRepository tasks, ExecutionAttemptRepository attempts,
                                       ManagedWorkspaceService workspaces,
                                       JdbcWorkflowHumanOutcomeRepository outcomes) {
        this.service = service;
        this.plans = plans;
        this.tasks = tasks;
        this.attempts = attempts;
        this.workspaces = workspaces;
        this.outcomes = outcomes;
    }

    @Transactional
    public WorkflowPlan cancel(String id, long version, String actor, String reason) {
        if(guardJdbc!=null&&guardJdbc.queryForObject("SELECT COUNT(*) FROM execution_policy WHERE plan_id=?",Long.class,id)>0)
            throw new IllegalStateException("Use guarded question controls: request cancellation and retain local Git evidence until actual stop confirmation");
        if(delegation!=null) delegation.changing("PLAN:"+id);
        if(managedPlans!=null) managedPlans.serializePlan(id);
        if (reason == null || reason.isBlank() || reason.length() > 2000) {
            throw new IllegalArgumentException("cancellation reason must contain 1 to 2000 characters");
        }
        if (actor == null || actor.isBlank()) throw new IllegalArgumentException("actor is required");
        WorkflowPlan plan = service.get(id);
        if (plan.version() != version || (plan.state() != WorkflowPlan.State.CONFIRMED
                && plan.state() != WorkflowPlan.State.STOPPED)) {
            throw new IllegalStateException("plan state or version changed; reload before cancelling");
        }
        if (outcomes.find(id).isPresent()) throw new IllegalStateException("plan has a human final decision");
        List<WorkTask> work = service.steps(id).stream().sorted(Comparator.comparing(t -> t.id().value())).toList();
        for (WorkTask task : work) if (!tasks.lock(task.id())) throw new IllegalStateException("plan task disappeared");
        work = work.stream().map(t -> tasks.find(t.id()).orElseThrow()).toList();
        if (work.stream().anyMatch(t -> t.state() == TaskState.CLAIMED || t.state() == TaskState.RUNNING)) {
            throw new IllegalStateException("active Worker must be stopped and acknowledged before cancellation");
        }
        if (plan.state() == WorkflowPlan.State.CONFIRMED) {
            service.withdrawAfterRollback(id, version, actor, reason);
        } else if (!plans.transition(id, version, WorkflowPlan.State.STOPPED, WorkflowPlan.State.WITHDRAWN,
                actor, reason.strip(), Instant.now())) {
            throw new IllegalStateException("plan changed during cancellation");
        }
        outcomes.insert(id, "CANCEL", "APPLIED", actor, reason.strip(), Instant.now());
        return service.get(id);
    }

}
