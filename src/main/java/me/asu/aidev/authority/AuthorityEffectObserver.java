package me.asu.aidev.authority;

import java.util.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Observation only: never starts/replays work, rolls back effects or refunds unknown consumption. */
@Component
public class AuthorityEffectObserver {
    private final AuthorityRepository repo;private final AuthorityBoundary boundary;private final TransactionTemplate transaction;
    private final AuthorityBudgets budgets;private final org.springframework.beans.factory.ObjectProvider<me.asu.aidev.workflow.application.WorkflowPlanService> plans;
    public AuthorityEffectObserver(AuthorityRepository repo,AuthorityBoundary boundary,PlatformTransactionManager tx,AuthorityBudgets budgets,
            org.springframework.beans.factory.ObjectProvider<me.asu.aidev.workflow.application.WorkflowPlanService> plans) {
        this.repo=repo;this.boundary=boundary;transaction=new TransactionTemplate(tx);this.budgets=budgets;this.plans=plans;
    }
    @Scheduled(fixedDelayString="${aidev.authority.observe-ms:5000}")
    public void observe() {
        transaction.executeWithoutResult(status->{
            boundary.lock();
            for(var row:repo.jdbc().queryForList("SELECT p.*,t.state AS task_state FROM authority_task p JOIN work_task t ON t.id=p.task_id WHERE p.state='IN_PROGRESS'")) {
                String taskState=String.valueOf(row.get("task_state"));
                if(taskState.equals("RUNNING") || taskState.equals("CLAIMED")) continue;
                String result=taskState.equals("COMPLETED")?"SUCCEEDED":"UNKNOWN";
                repo.jdbc().update("UPDATE authority_task SET state=?,settled=? WHERE task_id=? AND state='IN_PROGRESS'",result,result.equals("SUCCEEDED") && !AuthorityRepository.flag(row.get("reserved")),row.get("task_id"));
                if(row.get("request_id")!=null && !(result.equals("SUCCEEDED") && AuthorityRepository.flag(row.get("reserved")))) {
                    String owner=repo.jdbc().queryForObject("SELECT owner_id FROM authority_target WHERE target_key=?",String.class,row.get("target_key"));
                    repo.jdbc().update("UPDATE authority_receipt SET state=?,updated_ms=? WHERE owner_id=? AND request_id=? AND state IN ('ADMITTED','IN_PROGRESS')",
                            result,System.currentTimeMillis(),owner,row.get("request_id"));
                }
            }
            for(var row:repo.jdbc().queryForList("SELECT p.*,t.subject_id,t.state AS task_state FROM authority_task p JOIN work_task t ON t.id=p.task_id WHERE p.reserved=TRUE AND p.settled=FALSE")) {
                String task=String.valueOf(row.get("task_id"));
                if("CANCELLED".equals(row.get("task_state")) && !AuthorityRepository.flag(row.get("started"))) { budgets.settle(task,true);continue; }
                if(!AuthorityRepository.flag(row.get("started")) || "UNKNOWN".equals(row.get("state"))) continue;
                var plan=plans.getObject().get(String.valueOf(row.get("subject_id")));
                var position=me.asu.aidev.workflow.domain.WorkflowTaskPosition.parse(plan,new me.asu.aidev.work.domain.TaskId(task)).orElseThrow();
                boolean reviewed=plans.getObject().steps(plan.id()).stream().filter(t->t.state()==me.asu.aidev.work.domain.TaskState.COMPLETED)
                        .anyMatch(t->me.asu.aidev.workflow.domain.WorkflowTaskPosition.parse(plan,t.id()).filter(p->p.review() && p.step()==position.step() && p.round()==position.round()).isPresent());
                if(reviewed) {
                    budgets.settle(task,false);
                    String owner=repo.jdbc().queryForObject("SELECT owner_id FROM authority_target WHERE target_key=?",String.class,row.get("target_key"));
                    repo.jdbc().update("UPDATE authority_receipt SET state='SUCCEEDED',updated_ms=? WHERE owner_id=? AND request_id=? AND state IN ('ADMITTED','IN_PROGRESS')",System.currentTimeMillis(),owner,row.get("request_id"));
                }
            }
        });
    }
}
