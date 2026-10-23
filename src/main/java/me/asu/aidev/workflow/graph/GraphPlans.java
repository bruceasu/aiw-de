package me.asu.aidev.workflow.graph;

import java.time.Instant;
import java.util.*;
import me.asu.aidev.authority.*;
import me.asu.aidev.execution.config.*;
import me.asu.aidev.execution.guard.*;
import me.asu.aidev.execution.port.ExecutionAttemptRepository;
import me.asu.aidev.questions.FormalQuestions;
import me.asu.aidev.webconsole.security.WebConsoleSecurityService;
import me.asu.aidev.work.domain.*;
import me.asu.aidev.work.port.WorkTaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static me.asu.aidev.workflow.graph.GraphContract.*;
import static me.asu.aidev.workflow.graph.GraphState.*;

@Service
public class GraphPlans {
    private final AuthorityRepository repo;private final AuthorityBoundary boundary;private final GraphStore store;private final GraphValidator validator;private final GraphRunner runner;
    private final ExecutionGuard guard;private final ExecutionLedger ledger;private final WorkTaskRepository tasks;private final ExecutionAttemptRepository attempts;
    private final WebConsoleSecurityService security;private final FormalQuestions questions;
    public GraphPlans(AuthorityRepository repo,AuthorityBoundary boundary,GraphStore store,GraphValidator validator,GraphRunner runner,ExecutionGuard guard,ExecutionLedger ledger,WorkTaskRepository tasks,ExecutionAttemptRepository attempts,WebConsoleSecurityService security,FormalQuestions questions){
        this.repo=repo;this.boundary=boundary;this.store=store;this.validator=validator;this.runner=runner;this.guard=guard;this.ledger=ledger;this.tasks=tasks;this.attempts=attempts;this.security=security;this.questions=questions;
    }
    private void human(String owner,boolean write){guard.owner(owner);boolean operate=!security.currentDelegationAuthority(boundary.human(),"A19_ANSWER").isEmpty();
        if(!operate&&(write||security.currentDelegationAuthority(boundary.human(),"A18_BUDGET").isEmpty()))throw new SecurityException("Current owner role required");}
    private Map<String,Object> replay(String owner,String request,Object input){var rows=repo.jdbc().queryForList("SELECT digest,receipt_json FROM workflow_graph_request WHERE owner_id=? AND request_id=?",owner,request);
        if(rows.isEmpty())return null;if(!AuthorityRepository.hash(repo.encode(input)).equals(rows.getFirst().get("digest")))throw new IllegalStateException("GRAPH_REQUEST_CONFLICT");return repo.decode((String)rows.getFirst().get("receipt_json"),Map.class);}
    private Map<String,Object> receipt(String owner,String request,String plan,Object input,Map<String,Object> output){repo.jdbc().update("INSERT INTO workflow_graph_request VALUES(?,?,?,?,?)",owner,request,plan,AuthorityRepository.hash(repo.encode(input)),repo.encode(output));return output;}
    @Transactional public Map<String,Object> change(Change input){
        boundary.lock();human(input.ownerId(),true);var previous=replay(input.ownerId(),input.requestId(),input);if(previous!=null)return previous;
        policyOwner(input.planId(),input.ownerId());
        var targetOwners=repo.jdbc().queryForList("SELECT owner_id FROM authority_target WHERE target_key=?",String.class,"PLAN:"+input.planId());if(targetOwners.stream().anyMatch(owner->!owner.equals(input.ownerId())))throw new SecurityException("Plan target belongs to another owner");
        var legacyCreators=repo.jdbc().queryForList("SELECT created_by FROM flexible_workflow_plan WHERE id=?",String.class,input.planId());
        if(legacyCreators.stream().anyMatch(actor->!Objects.equals(actor,repo.actor(input.ownerId()))))throw new SecurityException("Existing Plan belongs to another creator");
        var rows=repo.jdbc().queryForList("SELECT id FROM workflow_graph WHERE id=?",input.planId());
        if(rows.isEmpty()){
            if(input.expectedVersion()!=0||input.region()!=null)throw new IllegalStateException("New draft version is zero");
            repo.jdbc().update("INSERT INTO workflow_graph VALUES(?,?,1,'DRAFT',0,?)",input.planId(),input.ownerId(),repo.encode(new GraphState()));
        }else{
            var p=store.get(input.planId());if(!p.owner().equals(input.ownerId()))throw new SecurityException("Graph owner mismatch");if(p.version()!=input.expectedVersion())throw new IllegalStateException("GRAPH_VERSION_CHANGED");
            if(!p.state().equals("DRAFT")) {
                if(!p.state().equals("ACTIVE")||input.region()==null||input.region().equals("ROOT"))throw new IllegalStateException("Only an unactivated child subtree may change");
                validator.validate(input.spec());var old=store.spec(p.id(),p.version());String region=input.region();
                if(!runner.interfaceDigest(old,region).equals(runner.interfaceDigest(input.spec(),region)))throw new IllegalStateException("GRAPH_EXTERNAL_INTERFACE_CHANGED");
                var rs=validator.regions(old);if(p.runtime().activations.stream().anyMatch(a->GraphValidator.ancestor(rs,region,a.region)))throw new IllegalStateException("GRAPH_REGION_ALREADY_ACTIVATED");
                if(!outside(old,region).equals(outside(input.spec(),region)))throw new IllegalStateException("GRAPH_PATCH_ESCAPES_SUBTREE");
                for(var a:p.runtime().activations)for(String child:new ArrayList<>(a.childVersions.keySet()))if(GraphValidator.ancestor(rs,child,region)&&p.runtime().activations.stream().noneMatch(b->b.region.equals(child)))a.childVersions.put(child,p.version()+1);
                store.save(p,p.state());
            }
            repo.jdbc().update("UPDATE workflow_graph SET edit_version=edit_version+1 WHERE id=? AND edit_version=?",p.id(),p.version());
        }
        long version=input.expectedVersion()+1;String json=repo.encode(input.spec());repo.jdbc().update("INSERT INTO workflow_graph_version VALUES(?,?,?,?,?,?)",input.planId(),version,json,AuthorityRepository.hash(json),input.source(),input.reason());
        store.event(input.planId(),input.requestId(),null,"GRAPH_CHANGED",Map.of("version",version,"source",input.source(),"reason",input.reason()));
        return receipt(input.ownerId(),input.requestId(),input.planId(),input,Map.of("planId",input.planId(),"version",version,"diagnostics",validator.diagnostics(input.spec())));
    }
    private Object outside(Spec s,String region){var rs=validator.regions(s);return List.of(s.goal(),s.scope(),s.acceptance(),s.regions().stream().filter(r->!GraphValidator.ancestor(rs,region,r.key())).toList(),s.nodes().stream().filter(n->!GraphValidator.ancestor(rs,region,n.region())).toList());}
    @Transactional public Map<String,Object> state(String owner,String plan){boundary.lock();human(owner,false);var p=store.get(plan);if(!p.owner().equals(owner))throw new SecurityException("Graph owner mismatch");
        policyOwner(plan,owner);
        return Map.of("plan",p,"spec",store.spec(plan,p.version()),"diagnostics",validator.diagnostics(store.spec(plan,p.version())),"budgets",ledger.snapshot(plan),"events",repo.jdbc().queryForList("SELECT * FROM workflow_graph_event WHERE plan_id=? ORDER BY created_ms,event_id",plan),"recommendations",repo.jdbc().queryForList("SELECT * FROM workflow_graph_recommendation WHERE plan_id=?",plan),
            "policy",repo.jdbc().queryForList("SELECT plan_id,revision,generation,revoked,paused FROM execution_policy WHERE plan_id=?",plan),
            "physicalTasks",repo.jdbc().queryForList("SELECT t.id,t.version,t.state,g.execution_id,a.id AS attempt_id,a.stop_requested_at_epoch_ms,a.stop_acknowledged_at_epoch_ms FROM work_task t JOIN execution_context x ON x.task_id=t.id LEFT JOIN workflow_graph_task g ON g.task_id=t.id LEFT JOIN execution_attempt a ON a.task_id=t.id AND a.attempt_no=t.attempt_no WHERE x.plan_id=? ORDER BY t.id",plan));}
    @Transactional public List<Map<String,Object>> list(String owner){boundary.lock();human(owner,false);return repo.jdbc().queryForList("SELECT id,edit_version,state FROM workflow_graph WHERE owner_id=? ORDER BY id",owner);}
    @Transactional public Map<String,Object> decide(Decision input){
        boundary.lock();var p=store.get(input.planId());human(p.owner(),true);var previous=replay(p.owner(),input.requestId(),input);if(previous!=null)return previous;
        if(p.version()!=input.expectedVersion())throw new IllegalStateException("GRAPH_VERSION_CHANGED");
        if(input.executionId()==null){switch(input.action()){
            case "activate"->{if(!p.state().equals("DRAFT"))throw new IllegalStateException("Graph is not a draft");validator.validate(store.spec(p.id(),p.version()));var policy=guard.policyRow(p.id());if(!policy.get("owner_id").equals(p.owner()))throw new SecurityException("Policy owner mismatch");store.save(p,"ACTIVE");}
            case "advance"->{if(!p.state().equals("ACTIVE"))throw new IllegalStateException("Graph is not active");}
            case "pause","resume"->questions.control(p.id(),GraphStore.number(guard.policyRow(p.id()),"revision"),input.action());
            case "cancel","terminate"->questions.control(p.id(),GraphStore.number(guard.policyRow(p.id()),"revision"),"cancel");
            default->throw new IllegalArgumentException("Unknown graph control");}
        }else{
            if(!p.state().equals("ACTIVE"))throw new IllegalStateException("Graph is not active");var a=p.runtime().containing(input.executionId());var e=p.runtime().execution(input.executionId());
            if(!a.state.equals("ACTIVE")||p.runtime().round(a).nodes.get(e.node)!=e||e.version!=input.expectedExecutionVersion())throw new IllegalStateException("GRAPH_EXECUTION_CHANGED");
            var n=validator.nodes(store.spec(p.id(),a.definitionVersion)).get(e.node);
            switch(input.action()){
                case "retry-preparation"->{if(!e.state.equals("PREPARATION_BLOCKED"))throw new IllegalStateException("No blocked preparation");if(!guard.mayAdvancePlan(p.id()))throw new IllegalStateException("Graph admission blocked");runner.retryPreparation(p,a,n,e);}
                case "retry"->{var task=checkedTask(input,e);if(task.state()!=TaskState.FAILED||!e.state.equals("FAILED_BLOCKED")||e.failureConsumed||e.emitted||!guard.canClaim(e.taskId))throw new IllegalStateException("FAILED_BLOCKED and unconsumed failure required");
                    if(!tasks.retryFailed(task.id(),Instant.now()))throw new IllegalStateException("TASK_RETRY_CHANGED");store.event(p.id(),input.requestId(),e.id,"TECHNICAL_RETRY",Map.of("taskId",e.taskId,"attemptId",input.expectedAttemptId(),"reason",input.reason()));e.fact=null;GraphRunner.set(e,"RETRY_PENDING",null);}
                case "cancel"->{e.cancelRequested=true;if(e.taskId!=null){tasks.lock(new TaskId(e.taskId));var t=tasks.find(new TaskId(e.taskId)).orElseThrow();if(input.expectedTaskVersion()==null||t.version()!=input.expectedTaskVersion())throw new IllegalStateException("TASK_VERSION_CHANGED");
                    var latest=repo.jdbc().queryForList("SELECT id FROM execution_attempt WHERE task_id=? ORDER BY attempt_no DESC LIMIT 1",String.class,e.taskId);if(!Objects.equals(latest.isEmpty()?null:latest.getFirst(),input.expectedAttemptId()))throw new IllegalStateException("ATTEMPT_CHANGED");
                    if(tasks.findLease(t.id()).isPresent()){var lease=tasks.findLease(t.id()).orElseThrow();attempts.requestStop(lease.token(),lease.workerId(),Instant.now());}
                    else if(Set.of(TaskState.READY,TaskState.BLOCKED,TaskState.WAITING_INPUT).contains(t.state()))tasks.cancelInactive(t.id(),t.state(),Instant.now());}
                    supersede(e);GraphRunner.set(e,e.taskId==null?"CANCELLED":"CANCELLING",e.taskId==null?"NO_PHYSICAL_TASK":"WAITING_REAL_STOP_CONFIRMATION");}
                case "replace"->{var task=checkedTask(input,e);if(n.kind()!=Kind.TASK||input.replacement()==null||!Set.of(TaskState.FAILED,TaskState.CANCELLED).contains(task.state())||e.emitted||a.exitDigest!=null)throw new IllegalStateException("Known failed/cancelled unconsumed output slot required");
                    if(!input.replacement().failureNext().equals(n.task().failureNext())||input.replacement().baseFrom()!=null&&!n.dependsOn().contains(input.replacement().baseFrom()))throw new IllegalArgumentException("Replacement must preserve control edges and declared input ports");
                    for(var downstream:p.runtime().round(a).nodes.values())if(!downstream.id.equals(e.id)&&downstream.taskId!=null){var definition=validator.nodes(store.spec(p.id(),a.definitionVersion)).get(downstream.node);if(definition.dependsOn().contains(e.node))throw new IllegalStateException("DEPENDENT_INPUT_ALREADY_CONSUMED");}
                    for(var active:p.runtime().activations)if(active.importBindings.containsValue(e.id))throw new IllegalStateException("ANCESTOR_INPUT_ALREADY_BOUND");
                    var replacement=new Execution();replacement.id=GraphRunner.key("GE",input.requestId());replacement.node=e.node;replacement.replacementFor=e.id;replacement.replacement=input.replacement();replacement.state="READY";
                    GraphRunner.set(e,"REPLACED",replacement.id);p.runtime().replaced.add(e);p.runtime().round(a).nodes.put(e.node,replacement);store.event(p.id(),input.requestId(),replacement.id,"OUTPUT_SLOT_REPLACED",Map.of("replacementFor",e.id,"replacement",replacement.id,"reason",input.reason()));}
                default->throw new IllegalArgumentException("Unknown execution control");}
            store.save(p,p.state());
        }
        store.event(p.id(),GraphRunner.key("control",input.requestId()),input.executionId(),"CONTROL_DECIDED",input);
        runner.tick(p.id());return receipt(p.owner(),input.requestId(),p.id(),input,Map.of("planId",p.id(),"version",p.version(),"action",input.action(),"accepted",true));
    }
    private WorkTask checkedTask(Decision input,Execution e){
        if(e.taskId==null)throw new IllegalStateException("Physical task required");var id=new TaskId(e.taskId);tasks.lock(id);var task=tasks.find(id).orElseThrow();
        if(input.expectedTaskVersion()==null||task.version()!=input.expectedTaskVersion()||guard.unknownTask(e.taskId)||tasks.findLease(id).isPresent())throw new IllegalStateException("Exact known quiescent task required");
        var ids=repo.jdbc().queryForList("SELECT id FROM execution_attempt WHERE task_id=? ORDER BY attempt_no DESC LIMIT 1",String.class,e.taskId);
        if(ids.size()!=1||!Objects.equals(ids.getFirst(),input.expectedAttemptId()))throw new IllegalStateException("ATTEMPT_CHANGED");return task;
    }
    private void policyOwner(String plan,String owner){if(repo.jdbc().queryForList("SELECT owner_id FROM execution_policy WHERE plan_id=?",String.class,plan).stream().anyMatch(o->!o.equals(owner)))throw new SecurityException("Graph and execution policy owners differ");}
    private void supersede(Execution e){if(e.questionId!=null)repo.jdbc().update("UPDATE formal_question SET state='SUPERSEDED',blocked_reason='GRAPH_CANCEL_REQUESTED' WHERE id=? AND state IN ('OPEN','ANSWERED')",e.questionId);
        if(e.taskId!=null)repo.jdbc().update("UPDATE formal_question SET state='SUPERSEDED',blocked_reason='GRAPH_CANCEL_REQUESTED' WHERE id IN (SELECT question_id FROM formal_question_slot WHERE source_task_id=?) AND state IN ('OPEN','ANSWERED')",e.taskId);}
}
