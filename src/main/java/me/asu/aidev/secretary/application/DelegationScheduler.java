package me.asu.aidev.secretary.application;

import me.asu.aidev.secretary.jdbc.DelegationRepository;
import me.asu.aidev.work.domain.*;
import me.asu.aidev.work.port.WorkTaskRepository;
import me.asu.aidev.workflow.domain.WorkflowTaskPosition;
import me.asu.aidev.workflow.port.WorkflowPlanRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Business decisions never run inside the task completion transaction. */
@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name="aidev.delegation.enabled",havingValue="true",matchIfMissing=true)
public class DelegationScheduler {
    private static final Logger log=LoggerFactory.getLogger(DelegationScheduler.class);
    private final DelegationRequests requests;
    private final DelegationRepository repo;
    private final DelegationBoundary boundary;
    private final DelegationBudgets budgets;
    private final JdbcTemplate jdbc;
    private final WorkTaskRepository tasks;
    private final WorkflowPlanRepository plans;
    private final TransactionTemplate transaction;
    private final DelegationTaskPolicy policy;
    public DelegationScheduler(DelegationRequests requests,DelegationRepository repo,DelegationBoundary boundary,
            DelegationBudgets budgets,JdbcTemplate jdbc,WorkTaskRepository tasks,WorkflowPlanRepository plans,PlatformTransactionManager manager,DelegationTaskPolicy policy) {
        this.requests=requests;this.repo=repo;this.boundary=boundary;this.budgets=budgets;this.jdbc=jdbc;this.tasks=tasks;this.plans=plans;
        this.transaction=new TransactionTemplate(manager);
        this.policy=policy;
    }
    @Scheduled(fixedDelayString="${aidev.delegation.interval-ms:5000}")
    public void scan() {
        for(var request:requests.list()) if(request.state().equals("PENDING")) requests.process(request.id());
        for(var grant:repo.grants()) if(grant.state().equals("ENABLED") && grant.rule().effect().equals("ALLOW")) {
            try { requests.propose(grant.id()); }
            catch(RuntimeException error) {
                try { requests.problem(grant.id(),error); }
                catch(RuntimeException recording) { log.error("Could not retain delegation evidence for {}",grant.id(),recording); }
            }
        }
        // Automatic publication repair/reclaim cannot manufacture a new execution permission.
        for(String id:jdbc.queryForList("SELECT t.id FROM work_task t JOIN delegation_publication_plan p ON p.plan_id=t.subject_id WHERE t.subject_type='flexible-plan' AND t.skill_ref<>'workflow-stage-reviewer' AND t.state IN ('READY','BLOCKED')",String.class)) {
            var task=tasks.find(new TaskId(id)).orElseThrow();
            if(policy.canClaim(task)) continue;
            repo.grants().stream().filter(g->g.rule().targetId().equals(task.subjectId()))
                    .sorted(java.util.Comparator.comparing(g->g.rule().action()!=me.asu.aidev.secretary.domain.DelegationRule.Action.A12_EXECUTE))
                    .findFirst().ifPresent(g->requests.problem(g.id(),new IllegalStateException("Publication task "+id+" has no current fixed-attempt admission. Human return and fresh A12_EXECUTE authority are required; it cannot run automatically")));
        }
        for(String id:jdbc.queryForList("SELECT task_id FROM delegation_task WHERE reserved=TRUE AND settled=FALSE",String.class)) {
            try { transaction.executeWithoutResult(status->settle(id)); }
            catch(RuntimeException error) { log.error("Delegated round {} remains reserved; no budget was released",id,error); }
        }
    }
    private void settle(String id) {
        boundary.lock();
        var task=tasks.find(new TaskId(id));
        if(task.isEmpty()) return;
        boolean started=jdbc.queryForObject("SELECT started FROM delegation_task WHERE task_id=?",Boolean.class,id);
        if(task.get().state()==TaskState.CANCELLED) { budgets.settle(id,!started); return; }
        var plan=plans.find(task.get().subjectId());
        if(plan.isEmpty()) return;
        var position=WorkflowTaskPosition.parse(plan.get(),task.get().id());
        if(position.isEmpty()) return;
        var at=position.get();
        boolean reviewed=tasks.findBySubject("flexible-plan",plan.get().id()).stream().filter(t->t.state()==TaskState.COMPLETED)
                .anyMatch(t->WorkflowTaskPosition.parse(plan.get(),t.id()).filter(p->p.review() && p.step()==at.step() && p.round()==at.round()).isPresent());
        if(reviewed) budgets.settle(id,false);
    }
}
