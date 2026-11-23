package me.asu.aidev.execution.run;

import java.util.*;
import me.asu.aidev.authority.*;
import me.asu.aidev.execution.guard.ExecutionGuard;
import me.asu.aidev.work.domain.*;
import me.asu.aidev.work.port.WorkTaskRepository;
import me.asu.aidev.workflow.domain.*;
import me.asu.aidev.workflow.port.WorkflowPlanRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static me.asu.aidev.execution.guard.GuardContract.text;

/** Plan ownership is explicit. It does not create an execution policy or tool authority. */
@Service
public class NativePlanOwners {
    public static final String CAPABILITY="worker-loop-turn-v1";
    public record Bind(String requestId,String planId,long expectedPlanVersion) {}
    public record Basis(String ownerId,String planId,long sourceVersion,String sourceDigest,WorkTask task) {}
    private final AuthorityRepository repo;private final AuthorityBoundary boundary;private final AuthorityGrantService grants;
    private final WorkflowPlanRepository plans;private final WorkTaskRepository tasks;private final ExecutionGuard guard;
    public NativePlanOwners(AuthorityRepository repo,AuthorityBoundary boundary,AuthorityGrantService grants,
            WorkflowPlanRepository plans,WorkTaskRepository tasks,ExecutionGuard guard) {
        this.repo=repo;this.boundary=boundary;this.grants=grants;this.plans=plans;this.tasks=tasks;this.guard=guard;
    }
    public String currentOwner() {
        if(boundary.human()==null||boundary.delegated())throw new SecurityException("Authenticated human required");
        var rows=repo.jdbc().queryForList("SELECT id FROM authority_owner WHERE actor=?",String.class,boundary.human());
        if(rows.isEmpty())return grants.bind(boundary.human());
        if(rows.size()!=1)throw new SecurityException("Bind exactly one current Owner first");return rows.getFirst();
    }
    private String sourceDigest(String plan) {
        // Workflow-added execution slots do not change the initial source pin used by formal questions.
        var executions=repo.jdbc().queryForList("SELECT run_json FROM workflow_execution WHERE id=?",String.class,plan);
        if(!executions.isEmpty()) {
            var run=repo.decode(executions.getFirst(),Map.class);
            if(run.get("planSourceDigest") instanceof String digest)return digest;
        }
        // Hash persisted bytes; do not reserialize Java sets with a different JVM iteration order.
        return AuthorityRepository.hash(repo.jdbc().queryForObject("SELECT spec_json FROM flexible_workflow_plan WHERE id=?",String.class,plan));
    }
    public boolean bound(String plan) { return !repo.jdbc().queryForList("SELECT plan_id FROM plan_run_owner WHERE plan_id=?",String.class,plan).isEmpty(); }
    @Transactional public Object bind(Bind input) {
        boundary.lock();String owner=currentOwner();text(input.requestId(),80);text(input.planId(),128);grants.human(boundary.human(),"A05");
        return bind(input,owner,boundary.human());
    }
    /** Internal port: the exact plan-confirmation permission was consumed in the same transaction. */
    @Transactional public Object bindConfirmedAgent(Bind input,AuthorityBoundary.Principal principal,String confirmationRequest) {
        boundary.lock();
        if(!principal.equals(boundary.optionalPrincipal())||repo.jdbc().queryForObject("""
                SELECT COUNT(*) FROM authority_permission_child c
                JOIN authority_action_permission p ON p.id=c.permission_id
                JOIN authority_permission_use u ON u.permission_id=p.id AND u.request_id=c.request_id AND u.grant_id=c.grant_id
                WHERE c.source_plan=? AND c.request_id=? AND p.owner_id=? AND p.worker_id=? AND p.action='A05'
                """,Long.class,input.planId(),confirmationRequest,principal.owner(),principal.worker())!=1)
            throw new SecurityException("Exact consumed Agent plan confirmation required");
        text(input.requestId(),80);text(input.planId(),128);
        return bind(input,principal.owner(),repo.actor(principal.owner()));
    }
    private Object bind(Bind input,String owner,String actor) {
        String digest=AuthorityRepository.hash(repo.encode(input));
        var old=repo.jdbc().queryForList("SELECT * FROM plan_run_owner WHERE owner_id=? AND request_id=?",owner,input.requestId());
        if(!old.isEmpty()) {
            if(!digest.equals(old.getFirst().get("request_digest")))throw new IllegalStateException("Binding request identifier has different content");
            return Map.of("planId",input.planId(),"ownerId",owner,"bound",true);
        }
        var plan=plans.find(input.planId()).orElseThrow();
        if(!actor.equals(plan.createdBy()))throw new SecurityException("Only the actual Plan creator can bind its run Owner");
        if(bound(plan.id()))throw new IllegalStateException("Plan already has a fixed run Owner; do not rebind historical work");
        if(plan.version()!=input.expectedPlanVersion()||!Set.of(WorkflowPlan.State.DRAFT,WorkflowPlan.State.CONFIRMED).contains(plan.state()))throw new IllegalStateException("Plan changed or ended");
        if(tasks.findBySubject("flexible-plan",plan.id()).stream().anyMatch(t->Set.of(TaskState.CLAIMED,TaskState.RUNNING,TaskState.YIELDING).contains(t.state())))
            throw new IllegalStateException("Bind before active execution; never change an in-flight Attempt");
        repo.jdbc().update("INSERT INTO plan_run_owner VALUES(?,?,?,?,?,?,?,?)",plan.id(),owner,input.requestId(),digest,sourceDigest(plan.id()),Math.addExact(plan.version(),1),actor,System.currentTimeMillis());
        repo.audit("PLAN:"+plan.id(),null,input.requestId(),actor,"PLAN_RUN_OWNER_BOUND","Owner for native observations, control and professional questions only");
        return Map.of("planId",plan.id(),"ownerId",owner,"bound",true);
    }
    /** Internal scheduler port; a persisted active arrangement is the authority source. */
    @Transactional public Object bindScheduled(Bind input,String schedule,long due) {
        boundary.lock();
        if(due>System.currentTimeMillis())throw new IllegalStateException("Schedule is not due");
        var row=repo.jdbc().queryForMap("SELECT actor,owner_id FROM workflow_schedule WHERE id=? AND state='ACTIVE' AND next_ms=?",schedule,due);
        String actor=(String)row.get("actor"),owner=(String)row.get("owner_id");
        if(!actor.equals(repo.actor(owner)))throw new SecurityException("Schedule Owner changed");
        grants.human(actor,"A05");
        return bind(input,owner,actor);
    }
    public Basis task(String taskId,boolean active) {
        var task=tasks.find(new TaskId(taskId)).orElseThrow();
        if(!task.subjectType().equals("flexible-plan")||guard.managed(taskId))throw new SecurityException("Exact native Plan task required");
        var plan=plans.find(task.subjectId()).orElseThrow();
        if(WorkflowTaskPosition.parse(plan,task.id()).isEmpty())throw new SecurityException("Task is not a real slot of this Plan");
        var rows=repo.jdbc().queryForList("SELECT * FROM plan_run_owner WHERE plan_id=?",plan.id());
        if(rows.size()!=1)throw new IllegalStateException("Explicit Plan run Owner binding required");var row=rows.getFirst();
        String owner=(String)row.get("owner_id");
        if(!repo.actor(owner).equals(plan.createdBy())||!sourceDigest(plan.id()).equals(row.get("source_digest")))throw new SecurityException("Plan scope or Owner changed");
        if(active&&plan.state()!=WorkflowPlan.State.CONFIRMED)throw new IllegalStateException("Plan is not confirmed for execution");
        return new Basis(owner,plan.id(),((Number)row.get("source_version")).longValue(),(String)row.get("source_digest"),task);
    }
    /** Internal caretaker port. A failed, already bound Plan is the authority source. */
    @Transactional public Object bindCaretaker(Bind input,String failedTask) {
        boundary.lock();
        var row=repo.jdbc().queryForMap("""
                SELECT o.owner_id,p.created_by FROM work_task t
                  JOIN flexible_workflow_plan p ON p.id=t.subject_id
                  JOIN plan_run_owner o ON o.plan_id=p.id
                 WHERE t.id=? AND t.subject_type='flexible-plan' AND t.state='FAILED'
                   AND p.state='CONFIRMED'
                """,failedTask);
        String owner=(String)row.get("owner_id"),actor=(String)row.get("created_by");
        if(!actor.equals(repo.actor(owner)))throw new SecurityException("Failed Plan Owner changed");
        grants.human(actor,"A05");
        return bind(input,owner,actor);
    }
    public String owner(String plan) {
        var rows=repo.jdbc().queryForList("SELECT owner_id FROM plan_run_owner WHERE plan_id=?",String.class,plan);
        if(rows.size()!=1)throw new SecurityException("Explicit Plan run Owner required");return rows.getFirst();
    }
    public List<Map<String,Object>> visiblePlans() {
        String owner=currentOwner();var result=new ArrayList<Map<String,Object>>();
        for(var row:repo.jdbc().queryForList("SELECT id FROM flexible_workflow_plan WHERE created_by=? ORDER BY created_at_epoch_ms DESC LIMIT 100",boundary.human())) {
            var plan=plans.find((String)row.get("id")).orElseThrow();
            result.add(describe(plan,owner));
        }return result;
    }
    public Map<String,Object> visiblePlan(String id) {
        String owner=currentOwner();text(id,128);
        var plan=plans.find(id).orElseThrow();
        if(!boundary.human().equals(plan.createdBy()))throw new SecurityException("Owned Plan required");
        return describe(plan,owner);
    }
    private Map<String,Object> describe(WorkflowPlan plan,String owner) {
        var item=new LinkedHashMap<String,Object>();item.put("id",plan.id());item.put("version",plan.version());item.put("state",plan.state());
        String title=plan.spec().goal().lines().findFirst().orElse(plan.id());
        item.put("title",title.substring(0,title.offsetByCodePoints(0,Math.min(160,title.codePointCount(0,title.length())))));item.put("bound",bound(plan.id()));item.put("ownerId",owner);return item;
    }
}
