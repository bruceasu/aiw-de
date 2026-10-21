package me.asu.aidev.authority;

import java.util.*;
import me.asu.aidev.ai.application.AiProfileCatalog;
import me.asu.aidev.execution.port.*;
import me.asu.aidev.work.domain.*;
import me.asu.aidev.worker.domain.Worker;
import me.asu.aidev.workflow.domain.WorkflowTaskPosition;
import me.asu.aidev.workflow.application.WorkflowPlanService;
import org.springframework.stereotype.Component;

/** All permission checks are platform-owned, including the actual fixed attempt start. */
@Component
public class AuthorityTaskPolicy implements WorkerTaskClaimPolicy,TaskExecutionPolicy {
    private final AuthorityRepository repo;
    private final AuthorityBoundary boundary;
    private final AuthorityGrantService grants;
    private final ManagedPlanAdmission manager;
    private final org.springframework.beans.factory.ObjectProvider<WorkflowPlanService> plans;
    private final AiProfileCatalog profiles;
    public AuthorityTaskPolicy(AuthorityRepository repo,AuthorityBoundary boundary,AuthorityGrantService grants,
            ManagedPlanAdmission manager,org.springframework.beans.factory.ObjectProvider<WorkflowPlanService> plans,AiProfileCatalog profiles) {
        this.repo=repo;this.boundary=boundary;this.grants=grants;this.manager=manager;this.plans=plans;this.profiles=profiles;
    }
    public boolean managed(WorkTask task) {
        if(!task.subjectType().equals("flexible-plan")) return false;
        String prefix="PLAN:"+task.subjectId();
        return manager.managed(task.subjectId()) || repo.jdbc.queryForObject("SELECT COUNT(*) FROM authority_target WHERE target_key=? OR substr(target_key,1,?)=?",Long.class,prefix,(prefix+":").length(),prefix+":")>0
                || repo.jdbc.queryForObject("SELECT COUNT(*) FROM authority_publication_plan WHERE plan_id=?",Long.class,task.subjectId())>0;
    }
    private boolean live(WorkTask task,boolean starting) {
        if(!managed(task)) return true;
        var rows=repo.jdbc.queryForList("SELECT * FROM authority_task WHERE task_id=?",task.id().value());
        if(rows.size()!=1) return false;
        var permit=rows.getFirst();
        long attempt=repo.jdbc.queryForObject("SELECT attempt_no FROM work_task WHERE id=?",Long.class,task.id().value());
        long allowed=((Number)permit.get("allowed_attempt")).longValue();
        if((starting?attempt:attempt+1)!=allowed || AuthorityRepository.flag(permit.get("started"))) return false;
        String target=String.valueOf(permit.get("target_key"));
        var targets=repo.jdbc.queryForList("SELECT * FROM authority_target WHERE target_key=?",target);
        if(targets.size()!=1) return false;
        var current=targets.getFirst();
        boolean human="HUMAN".equals(permit.get("grant_id"));
        if(!human && AuthorityRepository.flag(current.get("paused")) || !Objects.equals(current.get("generation"),permit.get("target_generation"))
                || !Objects.equals(current.get("restriction_revision"),permit.get("restriction_revision"))) return false;
        var plan=plans.getObject().get(task.subjectId());
        if(permit.get("post_version")==null || ((Number)permit.get("post_version")).longValue()!=plan.version()) return false;
        String worker;
        ActionScope scope=null;
        if(human) {
            try { grants.human(repo.actor(String.valueOf(current.get("owner_id"))),"A05");return true; }
            catch(SecurityException error) { return false; }
        } else if(permit.get("grant_id")!=null) {
            var grant=repo.get(String.valueOf(permit.get("grant_id")));
            if(!grants.current(grant) || ((Number)permit.get("grant_revision")).longValue()!=grant.revision()) return false;
            worker=grant.workerId();scope=grant.rule();
        } else {
            var managers=repo.jdbc.queryForList("SELECT * FROM authority_managed_plan WHERE plan_id=?",task.subjectId());
            if(managers.size()!=1) return false;
            var row=managers.getFirst();worker=String.valueOf(row.get("worker_id"));
            if(AuthorityRepository.flag(row.get("paused")) || ((Number)row.get("expires_ms")).longValue()<=System.currentTimeMillis()
                    || !Objects.equals(current.get("owner_id"),row.get("owner_id"))) return false;
            try { grants.human(repo.actor(String.valueOf(row.get("owner_id"))),"A05"); }
            catch(SecurityException error) { return false; }
        }
        if(repo.jdbc.queryForObject("SELECT COUNT(*) FROM authority_identity WHERE worker_id=? AND generation=? AND enabled_boot=?",Long.class,
                worker,permit.get("worker_generation"),boundary.boot())!=1 || !boundary.boot().equals(permit.get("enabled_boot"))) return false;
        var position=WorkflowTaskPosition.parse(plan,task.id()).orElseThrow();
        Map<String,Object> snapshot=Map.of("state",plan.state().name(),"rounds",plans.getObject().steps(plan.id()).stream()
                .filter(t->!t.skillRef().equals("workflow-stage-reviewer"))
                .map(t->WorkflowTaskPosition.parse(plan,t.id())).filter(Optional::isPresent).map(Optional::get)
                .filter(p->p.step()==position.step()).count());
        if(scope!=null && ((Number)snapshot.get("rounds")).longValue()<scope.minimumRounds()) return false;
        String prefix="PLAN:"+plan.id();
        for(String raw:repo.jdbc.queryForList("SELECT scope_json FROM authority_restriction WHERE active=TRUE AND (target_key=? OR substr(target_key,1,?)=?)",String.class,prefix,(prefix+":").length(),prefix+":")) {
            var deny=repo.decode(raw,ActionScope.class);
            if((scope!=null?deny.action()==scope.action():deny.action()==ActionScope.Action.A05)
                    && (deny.step()==null || deny.step()==position.step()) && AuthorityGrantService.matches(deny,snapshot)) return false;
        }
        if(repo.jdbc.queryForObject("SELECT COUNT(*) FROM authority_publication_plan WHERE plan_id=?",Long.class,task.subjectId())>0
                && !position.review()) {
            var publications=repo.jdbc.queryForList("SELECT * FROM authority_publication_attempt WHERE task_id=? AND allowed_attempt=? AND enabled_boot=?",task.id().value(),allowed,boundary.boot());
            if(publications.size()!=1) return false;
            var publication=publications.getFirst();
            if(publication.get("grant_id")!=null) {
                var publicationGrant=repo.get(String.valueOf(publication.get("grant_id")));
                if(!grants.current(publicationGrant) || !Objects.equals(publicationGrant.targetKey(),publication.get("target_key"))
                        || !Objects.equals(publicationGrant.owner(),publication.get("owner_id"))
                        || !grants.targetCurrent(publicationGrant,((Number)publication.get("target_generation")).longValue(),((Number)publication.get("restriction_revision")).longValue())
                        || publicationGrant.revision()!=((Number)publication.get("grant_revision")).longValue()
                        || repo.jdbc.queryForObject("SELECT COUNT(*) FROM authority_identity WHERE worker_id=? AND generation=? AND enabled_boot=?",Long.class,
                            publicationGrant.workerId(),publication.get("worker_generation"),boundary.boot())!=1) return false;
                for(String raw:repo.jdbc.queryForList("SELECT scope_json FROM authority_restriction WHERE target_key=? AND active=TRUE",String.class,publicationGrant.targetKey())) {
                    var deny=repo.decode(raw,ActionScope.class);
                    if(deny.action()==ActionScope.Action.A12_EXECUTE && AuthorityGrantService.matches(deny,snapshot)) return false;
                }
            }
        }
        return true;
    }
    private boolean independent(WorkTask task,String worker) {
        if(!managed(task)) return true;
        var plan=plans.getObject().get(task.subjectId());
        var position=WorkflowTaskPosition.parse(plan,task.id()).orElseThrow();
        if(repo.jdbc.queryForObject("SELECT COUNT(*) FROM authority_participant WHERE plan_id=? AND worker_id=? AND role='COORDINATOR'",Long.class,plan.id(),worker)>0) return false;
        return repo.jdbc.queryForList("SELECT DISTINCT t.id FROM execution_attempt a JOIN work_task t ON t.id=a.task_id WHERE t.subject_type='flexible-plan' AND t.subject_id=? AND a.worker_id=?",String.class,plan.id(),worker)
                .stream().map(id->WorkflowTaskPosition.parse(plan,new TaskId(id))).filter(Optional::isPresent).map(Optional::get)
                .noneMatch(p->p.step()==position.step() && p.review()!=position.review());
    }
    private boolean retired(WorkTask task) { return Set.of("secretary-chat","secretary-coordinate","delegated-decision").contains(task.subjectType()); }
    @Override public boolean canClaim(WorkTask task) { return !retired(task) && live(task,false); }
    @Override public boolean canClaim(WorkTask task,Worker worker) {
        if(repo.jdbc.queryForObject("SELECT COUNT(*) FROM authority_identity WHERE worker_id=?",Long.class,worker.id().value())>0) return false;
        if(retired(task)) return false;
        return live(task,false) && independent(task,worker.id().value());
    }
    @Override public void assertExecutionAllowed(WorkTask task) {
        if(task.state()==TaskState.RUNNING || task.state()==TaskState.COMPLETED) return;
        if(retired(task)) throw new IllegalStateException("Retired private task cannot start; keep its history sealed");
        if(!live(task,true)) throw new IllegalStateException("Current fixed-attempt authority is required");
    }
    public void beforeStart(WorkTask task) { if(managed(task)) boundary.lock(); }
    public void starting(WorkTask task,String worker) {
        if(retired(task) || !live(task,true) || !independent(task,worker)) throw new IllegalStateException("Admission changed or Worker history is not independent");
    }
    public void selection(WorkTask task,String profile,String version) {
        if(!managed(task)) return;
        var plan=plans.getObject().get(task.subjectId());var at=WorkflowTaskPosition.parse(plan,task.id()).orElseThrow();
        String key="PLAN:"+plan.id()+":STEP:"+at.step();String role=at.review()?"REVIEW":"EXECUTION";
        for(var row:repo.jdbc.queryForList("SELECT * FROM authority_model_scope WHERE target_key=? AND role=?",key,role)) {
            var grant=repo.get(String.valueOf(row.get("grant_id")));
            if(!grants.current(grant) || grant.revision()!=((Number)row.get("grant_revision")).longValue())
                throw new IllegalStateException("Model authority changed or expired");
            var allowed=Arrays.asList(repo.decode(String.valueOf(row.get("profiles_json")),String[].class));
            if(!Objects.equals(version,row.get("catalog_version")) || !profiles.version().equals(version) || !allowed.contains(profile))
                throw new IllegalStateException("Actual model selection violates the confirmed scope");
            repo.jdbc.update("INSERT INTO authority_model_constraint VALUES(?,?,?,?,?) ON CONFLICT(task_id) DO NOTHING",task.id().value(),row.get("grant_id"),row.get("grant_revision"),version,row.get("profiles_json"));
        }
    }
    public void started(WorkTask task) { repo.jdbc.update("UPDATE authority_task SET started=TRUE,state='IN_PROGRESS' WHERE task_id=?",task.id().value()); }
    public AiProfileCatalog.Guidance guidance(WorkTask task,String runtime,AiProfileCatalog.Guidance fallback) {
        if(!managed(task) || fallback==null) return fallback;
        var plan=plans.getObject().get(task.subjectId());var at=WorkflowTaskPosition.parse(plan,task.id()).orElseThrow();
        var constraints=repo.jdbc.queryForList("SELECT catalog_version,profiles_json FROM authority_model_scope WHERE target_key=? AND role=?",
                "PLAN:"+plan.id()+":STEP:"+at.step(),at.review()?"REVIEW":"EXECUTION");
        if(constraints.isEmpty()) return fallback;var row=constraints.getFirst();
        var allowed=Arrays.asList(repo.decode(String.valueOf(row.get("profiles_json")),String[].class));
        var selected=fallback.allowedProfiles().stream().filter(p->allowed.contains(p.id()) && fallback.version().equals(row.get("catalog_version"))).toList();
        return new AiProfileCatalog.Guidance(fallback.version(),selected,fallback.previous(),selected.stream().map(AiProfileCatalog.NamedProfile::id).findFirst().orElse(""));
    }
}
