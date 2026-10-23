package me.asu.aidev.software;

import java.util.*;
import me.asu.aidev.authority.*;
import me.asu.aidev.execution.config.*;
import me.asu.aidev.execution.domain.*;
import me.asu.aidev.execution.guard.*;
import me.asu.aidev.execution.port.ExecutionAttemptRepository;
import me.asu.aidev.questions.FormalQuestions;
import me.asu.aidev.work.domain.*;
import me.asu.aidev.work.port.WorkTaskRepository;
import me.asu.aidev.workflow.graph.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only composition of authoritative child contracts. Does not publish a second Delivery. */
@Service
public class SoftwareDelivery {
    private final AuthorityRepository repo;private final AuthorityBoundary boundary;private final GraphPlans plans;private final GraphStore graphs;
    private final ExecutionGuard guard;private final WorkTaskRepository tasks;private final ExecutionAttemptRepository attempts;
    private final AgentFactory factory;private final ExecutionConfigurations configurations;private final FormalQuestions questions;
    public SoftwareDelivery(AuthorityRepository repo,AuthorityBoundary boundary,GraphPlans plans,GraphStore graphs,ExecutionGuard guard,WorkTaskRepository tasks,ExecutionAttemptRepository attempts,AgentFactory factory,ExecutionConfigurations configurations,FormalQuestions questions){this.repo=repo;this.boundary=boundary;this.plans=plans;this.graphs=graphs;this.guard=guard;this.tasks=tasks;this.attempts=attempts;this.factory=factory;this.configurations=configurations;this.questions=questions;}
    public record Evidence(String taskId,String attemptId,String status,String detail,Object input,Object delivery,String inputDigest,String configurationDigest) {}
    @Transactional public Object read(String owner,String plan){
        boundary.lock();var progress=plans.state(owner,plan);var p=graphs.get(plan);var evidence=new ArrayList<Evidence>();String status="WAITING_FINAL_EXIT";var notes=new ArrayList<String>();
        if(p.runtime().rootActivation!=null){var root=p.runtime().activation(p.runtime().rootActivation);
            if(root.state.equals("SUCCEEDED")){
                String expected=AuthorityRepository.hash(repo.encode(Map.of("activation",root.id,"iteration",root.iteration,"results",root.exports)));
                if(!expected.equals(root.exitDigest)){status="CONFLICT";notes.add("ROOT exit digest differs from its frozen exports");}
                else {var seen=new HashMap<String,String>();for(var f:root.exports){if(!f.kind().equals("COMPLETED")||f.input()==null)continue;String key=f.input().taskId().value()+":"+f.input().attemptId();
                        if(seen.containsKey(key)){if(!seen.get(key).equals(f.digest())){status="CONFLICT";notes.add("Same Task/Attempt has conflicting frozen facts");}continue;}seen.put(key,f.digest());evidence.add(check(p,f));}
                    if(!status.equals("CONFLICT"))status=evidence.isEmpty()?"NO_SOFTWARE_DELIVERY":evidence.stream().allMatch(e->e.status().equals("RECORDED"))?"EVIDENCE_RECORDED":"EVIDENCE_INCOMPLETE";}
            }
        }
        if(evidence.stream().anyMatch(e->e.status().equals("CONFLICT")))status="CONFLICT";
        if(evidence.stream().anyMatch(e->e.input() instanceof TaskInput input&&input.completionVerdict()==CompletionVerdict.REPAIR_REQUIRED))notes.add("REPAIR_REQUIRED is a recorded business verdict, not business acceptance");
        var budgetRows=repo.jdbc().queryForList("SELECT unknown_count FROM execution_budget WHERE plan_id=?",plan);if(budgetRows.stream().anyMatch(b->((Number)b.get("unknown_count")).longValue()>0)){status="UNKNOWN";notes.add("Human consumption reconciliation is required");}
        if(repo.jdbc().queryForObject("SELECT COUNT(*) FROM execution_operation WHERE plan_id=? AND state='UNKNOWN'",Long.class,plan)>0){status="UNKNOWN";notes.add("Human external-outcome reconciliation is required");}
        var intentKeys=new HashSet<String>();for(var row:repo.jdbc().queryForList("SELECT intent_id,intent_version,intent_json FROM execution_preparation WHERE owner_id=?",owner))if(repo.decode((String)row.get("intent_json"),ConfigurationContract.Intent.class).planId().equals(plan))intentKeys.add(row.get("intent_id")+":"+row.get("intent_version"));
        return Map.of("protocol","aiw.software-development.v1","progress",progress,"preparations",factory.preparations(owner).stream().filter(v->intentKeys.contains(v.intentId()+":"+v.intentVersion())).toList(),
            "questions",questions.list(plan),"categories",configurations.categories(),"deliveryStatus",status,"evidence",evidence,"notes",notes,"runtimeCertified",false);
    }
    private Evidence check(GraphStore.Plan p,GraphState.Fact f){
        var input=f.input();String taskId=input.taskId().value(),attempt=input.attemptId();
        try {
            if(!f.digest().equals(AuthorityRepository.hash(repo.encode(input)))||!f.kind().equals("COMPLETED"))throw new IllegalStateException("Frozen result digest changed");
            if(attempt==null||input.baseRevision()==null||input.resultRevision()==null)return new Evidence(taskId,attempt,"MISSING","Exact Attempt and immutable baseline/result are required",input,null,null,null);
            var task=tasks.find(new TaskId(taskId)).orElseThrow();var actual=attempts.findCompletedInput(task.id()).orElseThrow();
            if(task.state()!=TaskState.COMPLETED||!input.equals(actual)||!p.id().equals(task.subjectId()))throw new IllegalStateException("Frozen result differs from actual completed task");
            var mapping=repo.jdbc().queryForMap("SELECT plan_id,execution_id FROM workflow_graph_task WHERE task_id=?",taskId);if(!p.id().equals(mapping.get("plan_id"))||!f.executionId().equals(mapping.get("execution_id")))throw new IllegalStateException("Graph execution provenance changed");
            var a=p.runtime().containing(f.executionId());var e=p.runtime().execution(f.executionId());if(!a.state.equals("SUCCEEDED")||p.runtime().round(a).nodes.get(e.node)!=e||!e.state.equals("DONE")||!Objects.equals(e.fact,f))throw new IllegalStateException("Only the final successful execution may supply delivery");
            var physical=repo.jdbc().queryForMap("SELECT task_id,state,base_revision,result_revision FROM execution_attempt WHERE id=?",attempt);
            if(!taskId.equals(physical.get("task_id"))||!"COMPLETED".equals(physical.get("state"))||!input.baseRevision().equals(physical.get("base_revision"))||!input.resultRevision().equals(physical.get("result_revision")))throw new IllegalStateException("Attempt result provenance changed");
            var context=guard.context(taskId);var contextRow=repo.jdbc().queryForMap("SELECT input_json,input_digest FROM execution_context WHERE task_id=?",taskId);
            if(!p.owner().equals(context.ownerId())||!p.id().equals(context.planId())||!context.inputDigest().equals(AuthorityRepository.hash((String)contextRow.get("input_json")))||!task.id().equals(context.snapshot().task().id())||!Objects.equals(task.repositoryRef(),input.repositoryRef())||!Objects.equals(context.snapshot().task().baseRevision(),input.baseRevision()))throw new IllegalStateException("Fixed input context differs");
            var binding=configurations.binding(taskId);if(binding==null||!context.configurationDigest().equals(binding.ref().digest())||context.snapshot().references().stream().noneMatch(r->r.kind().equals("configuration")&&r.sha256().equals(binding.ref().digest())&&r.id().equals(binding.ref().id())&&r.revision().equals(Long.toString(binding.ref().revision()))))throw new IllegalStateException("Fixed configuration provenance differs");
            var rows=repo.jdbc().queryForList("SELECT * FROM execution_delivery WHERE task_id=? AND attempt_id=?",taskId,attempt);if(rows.isEmpty())return new Evidence(taskId,attempt,"MISSING","Worker did not record a formal Delivery",input,null,context.inputDigest(),context.configurationDigest());
            var row=rows.getFirst();String raw=(String)row.get("delivery_json");if(!AuthorityRepository.hash(raw).equals(row.get("digest")))throw new IllegalStateException("Delivery raw digest changed");var delivery=repo.decode(raw,GuardContract.Delivery.class);
            if(!input.baseRevision().equals(delivery.baseline())||!input.resultRevision().equals(delivery.resultRevision())||!new HashSet<>(input.changedFiles()).equals(new HashSet<>(delivery.changes()))||!delivery.sources().containsAll(context.snapshot().references()))throw new IllegalStateException("Delivery result or frozen sources differ");
            if(!input.baseRevision().matches("[0-9a-f]{40}|[0-9a-f]{64}")||!input.resultRevision().matches("[0-9a-f]{40}|[0-9a-f]{64}"))throw new IllegalStateException("Full immutable result revision required");
            return new Evidence(taskId,attempt,"RECORDED","Commands/checks are Worker declarations; no independent runtime certification",input,delivery,context.inputDigest(),context.configurationDigest());
        }catch(RuntimeException error){return new Evidence(taskId,attempt,"CONFLICT",Objects.toString(error.getMessage(),"Result provenance unavailable"),input,null,null,null);}
    }
}
