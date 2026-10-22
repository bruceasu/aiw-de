package me.asu.aidev.authority;

import java.util.*;
import java.util.function.Supplier;
import me.asu.aidev.workflow.application.WorkflowPlanService;
import me.asu.aidev.workflow.domain.WorkflowPlan;
import org.springframework.stereotype.Component;

/** Generic platform Plan ownership and admission; no personal-store references. */
@Component
public class ManagedPlanAdmission {
    private final AuthorityRepository repo;
    private final AuthorityBoundary boundary;
    private final org.springframework.beans.factory.ObjectProvider<WorkflowPlanService> plans;
    private final ThreadLocal<String> allowed=new ThreadLocal<>();
    public ManagedPlanAdmission(AuthorityRepository repo,AuthorityBoundary boundary,
            org.springframework.beans.factory.ObjectProvider<WorkflowPlanService> plans) { this.repo=repo;this.boundary=boundary;this.plans=plans; }
    public boolean managed(String plan) { return repo.jdbc().queryForObject("SELECT COUNT(*) FROM authority_managed_plan WHERE plan_id=?",Long.class,plan)>0; }
    public boolean inScope(String plan) { return plan.equals(allowed.get()); }
    public void serializePlan(String plan) { if(managed(plan)) boundary.lock(); }
    public void serializeTask(me.asu.aidev.work.domain.WorkTask task) { if(task.subjectType().equals("flexible-plan")) serializePlan(task.subjectId()); }
    public void checkTask(me.asu.aidev.work.domain.WorkTask task) { if(task.subjectType().equals("flexible-plan")) check(task.subjectId()); }
    public void initialConfirmation(String plan) { boundary.lock();check(plan); }
    public void checkReplacement(String task) {
        boundary.lock();
        for(var permit:repo.jdbc().queryForList("SELECT state FROM authority_task WHERE task_id=?",task))
            if(Set.of("UNKNOWN","IN_PROGRESS").contains(String.valueOf(permit.get("state"))))
                throw new IllegalStateException("Reconcile the original execution intent with explicit evidence before replacing its permit");
    }
    public void humanTask(String task) {
        checkReplacement(task);
        var rows=repo.jdbc().queryForList("SELECT subject_type,subject_id,attempt_no FROM work_task WHERE id=?",task);
        if(rows.size()!=1 || !"flexible-plan".equals(rows.getFirst().get("subject_type"))) return;
        String plan=String.valueOf(rows.getFirst().get("subject_id"));if(!managed(plan)) return;check(plan);
        replaceHumanTask(plan,task,((Number)rows.getFirst().get("attempt_no")).longValue()+1);
    }
    private void replaceHumanTask(String plan,String task,long next) {
        checkReplacement(task);
        String target="PLAN:"+plan;
        for(var previous:repo.jdbc().queryForList("SELECT * FROM authority_task WHERE task_id=?",task))
            repo.jdbc().update("INSERT INTO authority_effect_history VALUES(?,?,?) ON CONFLICT(task_id,attempt) DO NOTHING",task,previous.get("allowed_attempt"),repo.encode(previous));
        repo.jdbc().update("INSERT INTO authority_task(task_id,target_key,allowed_attempt,reserved,started,settled,grant_id,target_generation,restriction_revision,post_version) VALUES(?,?,?,FALSE,FALSE,FALSE,'HUMAN',?,?,?) ON CONFLICT(task_id) DO UPDATE SET target_key=excluded.target_key,allowed_attempt=excluded.allowed_attempt,reserved=FALSE,started=FALSE,settled=FALSE,state='PREPARED',grant_id='HUMAN',grant_revision=NULL,worker_generation=NULL,enabled_boot=NULL,request_id=NULL,target_generation=excluded.target_generation,restriction_revision=excluded.restriction_revision,post_version=excluded.post_version",
                task,target,next,generation(target),restriction(target),plans.getObject().get(plan).version());
    }
    public void check(String plan) {
        if(managed(plan) && !inScope(plan)) {
            var row=repo.jdbc().queryForMap("SELECT owner_id,paused FROM authority_managed_plan WHERE plan_id=?",plan);
            if(boundary.human()!=null && AuthorityRepository.flag(row.get("paused")) && Objects.equals(repo.actor(String.valueOf(row.get("owner_id"))),boundary.human())) return;
            throw new IllegalStateException("Managed Plan requires a fresh authenticated admission; human takeover remains available");
        }
    }
    public void adopt(String plan,String owner,String worker,long expiry,String authority) {
        boundary.lock();var current=plans.getObject().get(plan);
        if(current.state()!=WorkflowPlan.State.DRAFT || !plans.getObject().steps(plan).isEmpty())
            throw new IllegalStateException("Only a clean draft can enter personal management; actual migration/cutover is disabled");
        repo.jdbc().update("INSERT INTO authority_managed_plan VALUES(?,?,?,?,?,0,?,?,FALSE)",plan,owner,worker,
                AuthorityRepository.hash(repo.encode(current.spec())),current.version(),expiry,authority);
    }
    public <T> T delegatedAdmission(String plan,String worker,Supplier<T> operation) {
        var principal=boundary.principal();
        if(!principal.worker().equals(worker)) throw new SecurityException("Fixed authenticated manager required");
        boundary.lock();
        if(!managed(plan))
            throw new IllegalStateException("Explicit adoption of the clean draft is required before delegated Plan advancement");
        if(managed(plan)) {
            var row=repo.jdbc().queryForMap("SELECT * FROM authority_managed_plan WHERE plan_id=?",plan);
            var current=plans.getObject().get(plan);
            if(!Objects.equals(row.get("owner_id"),principal.owner()) || !Objects.equals(row.get("worker_id"),worker)
                    || AuthorityRepository.flag(row.get("paused")) || ((Number)row.get("expires_ms")).longValue()<=System.currentTimeMillis()
                    || !AuthorityRepository.hash(repo.encode(current.spec())).equals(row.get("spec_digest")))
                throw new IllegalStateException("Managed Plan changed, paused or expired");
        }
        String previous=allowed.get();allowed.set(plan);
        try { return operation.get(); }
        finally { if(previous==null) allowed.remove();else allowed.set(previous); }
    }
    public void admitTask(String plan,String task) {
        if(!managed(plan)) return;
        check(plan);
        var current=boundary.optionalPrincipal();
        if(current==null) {
            var row=repo.jdbc().queryForMap("SELECT owner_id,paused FROM authority_managed_plan WHERE plan_id=?",plan);
            if(boundary.human()==null || !Objects.equals(repo.actor(String.valueOf(row.get("owner_id"))),boundary.human()) || !AuthorityRepository.flag(row.get("paused")))
                throw new SecurityException("Explicit authenticated human takeover required");
            var attempts=repo.jdbc().queryForList("SELECT attempt_no FROM work_task WHERE id=?",Long.class,task);
            replaceHumanTask(plan,task,attempts.isEmpty()?1:attempts.getFirst()+1);
            return;
        }
        repo.jdbc().update("INSERT INTO authority_task(task_id,target_key,allowed_attempt,reserved,started,settled,worker_generation,enabled_boot,target_generation,restriction_revision) VALUES(?,?,1,FALSE,FALSE,FALSE,?,?,?,?) ON CONFLICT(task_id) DO NOTHING",
                task,"PLAN:"+plan,current.generation(),boundary.boot(),generation("PLAN:"+plan),restriction("PLAN:"+plan));
    }
    public long generation(String key) { return repo.jdbc().queryForObject("SELECT generation FROM authority_target WHERE target_key=?",Long.class,key); }
    public long restriction(String key) { return repo.jdbc().queryForObject("SELECT restriction_revision FROM authority_target WHERE target_key=?",Long.class,key); }
}
