package me.asu.aidev.secretary.application;

import java.util.*;
import me.asu.aidev.ai.application.AiProfileCatalog;
import me.asu.aidev.execution.port.TaskExecutionPolicy;
import me.asu.aidev.execution.port.WorkerTaskClaimPolicy;
import me.asu.aidev.secretary.jdbc.DelegationRepository;
import me.asu.aidev.work.domain.*;
import me.asu.aidev.worker.domain.Worker;
import me.asu.aidev.workflow.domain.WorkflowTaskPosition;
import me.asu.aidev.workflow.port.WorkflowPlanRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class DelegationTaskPolicy implements WorkerTaskClaimPolicy, TaskExecutionPolicy {
    private final JdbcTemplate jdbc;
    private final DelegationBoundary boundary;
    private final DelegationRepository repo;
    private final WorkflowPlanRepository plans;
    private final AiProfileCatalog profiles;
    private final me.asu.aidev.webconsole.security.WebConsoleSecurityService security;
    public DelegationTaskPolicy(JdbcTemplate jdbc,DelegationBoundary boundary,DelegationRepository repo,
                                WorkflowPlanRepository plans,AiProfileCatalog profiles,
                                me.asu.aidev.webconsole.security.WebConsoleSecurityService security) {
        this.jdbc=jdbc;this.boundary=boundary;this.repo=repo;this.plans=plans;this.profiles=profiles;this.security=security;
    }
    private boolean candidate(WorkTask task) { return task.subjectType().equals("delegated-decision"); }
    private boolean enrolled(String worker) { return jdbc.queryForObject("SELECT COUNT(*) FROM delegation_identity WHERE worker_id=?",Long.class,worker)>0; }
    private boolean retryAllowed(WorkTask task,boolean starting) {
        if(!task.subjectType().equals("flexible-plan") || !boundary.managedPlan(task.subjectId())) return true;
        long attempt=jdbc.queryForObject("SELECT attempt_no FROM work_task WHERE id=?",Long.class,task.id().value());
        if(boundary.managedPublication(task.subjectId()) && !task.skillRef().equals("workflow-stage-reviewer")) {
            var admitted=jdbc.queryForList("SELECT * FROM delegation_publication_attempt WHERE task_id=?",task.id().value());
            if(admitted.isEmpty()) return false;
            var receipt=admitted.getFirst();
            long allowed=((Number)receipt.get("allowed_attempt")).longValue();
            if(starting ? attempt!=allowed : attempt+1!=allowed) return false;
            if(receipt.get("grant_id")!=null) {
                var grant=repo.get(String.valueOf(receipt.get("grant_id")));
                if(!grant.state().equals("ENABLED") || grant.revision()!=((Number)receipt.get("grant_revision")).longValue()
                        || grant.expiresMs()<=System.currentTimeMillis() || !grant.authority().equals(security.currentDelegationAuthority(grant.owner(),grant.rule().action().name()))) return false;
                if(jdbc.queryForObject("SELECT COUNT(*) FROM delegation_identity WHERE worker_id=? AND generation=? AND enabled_boot=?",Long.class,
                        grant.workerId(),receipt.get("worker_generation"),boundary.boot())!=1 || !boundary.boot().equals(receipt.get("enabled_boot"))) return false;
                String target=jdbc.queryForObject("SELECT target_key FROM delegation_publication_plan WHERE plan_id=?",String.class,task.subjectId());
                var state=Map.<String,Object>of("state",plans.find(task.subjectId()).orElseThrow().state().name());
                for(var deny:repo.grants()) if(deny.rule().action()==me.asu.aidev.secretary.domain.DelegationRule.Action.A12_EXECUTE
                        && deny.rule().effect().equals("DENY") && deny.state().equals("ENABLED") && deny.expiresMs()>System.currentTimeMillis()
                        && deny.authority().equals(security.currentDelegationAuthority(deny.owner(),deny.rule().action().name()))
                        && DelegationConditions.matches(deny.rule(),state)
                        && jdbc.queryForObject("SELECT COUNT(*) FROM delegation_publication_plan WHERE plan_id=? AND target_key=?",Long.class,deny.rule().targetId(),target)>0) return false;
            }
        }
        var receipts=jdbc.queryForList("SELECT allowed_attempt FROM delegation_task WHERE task_id=?",Long.class,task.id().value());
        long permitted=receipts.isEmpty()?1:receipts.getFirst();
        return starting?attempt<=permitted:attempt<permitted;
    }
    @Override public boolean canClaim(WorkTask task) { return !candidate(task) && retryAllowed(task,false); }
    @Override public boolean canClaim(WorkTask task,Worker worker) {
        if(candidate(task)) {
            return jdbc.queryForObject("""
                    SELECT COUNT(*) FROM delegation_request r JOIN delegation_identity i ON i.worker_id=r.worker_id
                    JOIN delegation_grant g ON g.id=r.grant_id
                    WHERE r.task_id=? AND r.worker_id=? AND r.state='PENDING' AND i.generation=r.worker_generation
                    AND i.enabled_boot=? AND g.revision=r.grant_revision AND g.state='ENABLED' AND g.expires_ms>?
                    """,Long.class,task.id().value(),worker.id().value(),boundary.boot(),System.currentTimeMillis())==1
                    && worker.runtimeType().endsWith(":decision-delegate");
        }
        if(enrolled(worker.id().value())) return false;
        if(!retryAllowed(task,false) || !independent(task,worker.id().value())) return false;
        if(!task.subjectType().equals("flexible-plan")) return true;
        AiProfileCatalog.Guidance current=guidance(task,worker.runtimeType(),profiles.guidance(task.type(),worker.runtimeType(),null));
        return current==null || !current.allowedProfiles().isEmpty();
    }
    private boolean independent(WorkTask task,String worker) {
        if(!task.subjectType().equals("flexible-plan") || !boundary.managedPlan(task.subjectId())) return true;
        var plan=plans.find(task.subjectId()).orElseThrow();
        var position=WorkflowTaskPosition.parse(plan,task.id()).orElseThrow();
        // Keep role separation across retries, recovery reviews and all historical rounds of this step.
        return jdbc.queryForList("SELECT DISTINCT t.id FROM execution_attempt a JOIN work_task t ON t.id=a.task_id WHERE t.subject_type='flexible-plan' AND t.subject_id=? AND a.worker_id=?",String.class,task.subjectId(),worker)
                .stream().map(id->WorkflowTaskPosition.parse(plan,new TaskId(id))).filter(Optional::isPresent).map(Optional::get)
                .noneMatch(p->p.step()==position.step() && p.review()!=position.review());
    }
    /** Start obtains the global lock before the task lock; claim only performs reads. */
    public void beforeStart(WorkTask task) {
        if(task.subjectType().equals("flexible-plan") && boundary.managedPlan(task.subjectId())) boundary.lock();
    }
    public void starting(WorkTask task,String worker) {
        if(!retryAllowed(task,true) || !independent(task,worker))
            throw new IllegalStateException("A current fixed-attempt admission and an independent execution/review Worker are required");
    }
    @Override public void assertExecutionAllowed(WorkTask task) {
        if(task.state()==TaskState.RUNNING || task.state()==TaskState.COMPLETED) return; // Retain real reports and failures.
        if(!retryAllowed(task,true)) throw new IllegalStateException("Interrupted work needs an explicit separately budgeted retry");
        if(candidate(task) && jdbc.queryForObject("SELECT COUNT(*) FROM delegation_request WHERE task_id=? AND state='PENDING'",Long.class,task.id().value())!=1)
            throw new IllegalStateException("Delegate candidate was superseded or returned to a human");
    }
    private record Constraint(String version,List<String> ids,String grant,long revision) {}
    private Constraint constraint(WorkTask task) {
        if(!task.subjectType().equals("flexible-plan")) return null;
        var plan=plans.find(task.subjectId());
        if(plan.isEmpty()) return null;
        var position=WorkflowTaskPosition.parse(plan.get(),task.id());
        if(position.isEmpty()) return null;
        String key="PLAN:"+plan.get().id()+":STEP:"+position.get().step();
        String role=position.get().review()?"REVIEW":"EXECUTION";
        return jdbc.query("SELECT catalog_version,profiles_json,grant_id,grant_revision FROM delegation_model_scope WHERE target_key=? AND role=?",
                (rs,n)->new Constraint(rs.getString(1),Arrays.asList(repo.decode(rs.getString(2),String[].class)),rs.getString(3),rs.getLong(4)),key,role)
                .stream().findFirst().orElse(null);
    }
    public AiProfileCatalog.Guidance guidance(WorkTask task,String runtime,AiProfileCatalog.Guidance fallback) {
        Constraint rule=constraint(task);
        if(rule==null) return fallback;
        if(fallback==null || !fallback.version().equals(rule.version()))
            return new AiProfileCatalog.Guidance(profiles.version(),List.of(),null,"");
        var allowed=fallback.allowedProfiles().stream().filter(p->rule.ids().contains(p.id())).toList();
        return new AiProfileCatalog.Guidance(fallback.version(),allowed,fallback.previous(),
                allowed.stream().map(AiProfileCatalog.NamedProfile::id).findFirst().orElse(""));
    }
    /** Called after the task lock and before persisting the actual AiSelection. */
    public void selection(WorkTask task,String profile,String version) {
        Constraint rule=constraint(task);
        if(rule==null) return;
        if(!rule.version().equals(version) || !profiles.version().equals(version) || !rule.ids().contains(profile))
            throw new IllegalStateException("Actual model selection is outside the frozen delegated whitelist");
        jdbc.update("INSERT INTO delegation_model_constraint VALUES(?,?,?,?,?) ON CONFLICT(task_id) DO NOTHING",
                task.id().value(),rule.grant(),rule.revision(),rule.version(),repo.encode(rule.ids()));
    }
    public void started(WorkTask task) {
        jdbc.update("UPDATE delegation_task SET started=TRUE WHERE task_id=?",task.id().value());
    }
}
