package me.asu.aidev.workflow.graph;

import java.util.*;
import me.asu.aidev.authority.*;
import me.asu.aidev.execution.config.*;
import me.asu.aidev.execution.guard.ExecutionGuard;
import me.asu.aidev.execution.port.ExecutionAttemptRepository;
import me.asu.aidev.webconsole.security.WebConsoleSecurityService;
import me.asu.aidev.work.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static me.asu.aidev.workflow.graph.GraphContract.*;

/** Model output is a retained candidate. Only the shared versioned change entry can adopt it. */
@Service
public class GraphRecommendations {
    private final AuthorityRepository repo;private final AuthorityBoundary boundary;private final GraphStore store;private final GraphValidator validator;
    private final AgentFactory factory;private final ExecutionGuard guard;private final WebConsoleSecurityService security;private final ExecutionAttemptRepository attempts;
    public GraphRecommendations(AuthorityRepository repo,AuthorityBoundary boundary,GraphStore store,GraphValidator validator,AgentFactory factory,ExecutionGuard guard,WebConsoleSecurityService security,ExecutionAttemptRepository attempts){this.repo=repo;this.boundary=boundary;this.store=store;this.validator=validator;this.factory=factory;this.guard=guard;this.security=security;this.attempts=attempts;}
    @Transactional public Object request(Recommendation input){
        boundary.lock();var p=store.get(input.planId());guard.owner(p.owner());if(security.currentDelegationAuthority(boundary.human(),"A19_ANSWER").isEmpty())throw new SecurityException("Current operation role required");
        if(p.version()!=input.expectedVersion()||!Set.of("DRAFT","ACTIVE").contains(p.state()))throw new IllegalStateException("GRAPH_VERSION_CHANGED");
        var t=input.planner();if(!"planner".equals(t.defaultRole())||t.configRef()!=null||t.candidate()!=null||t.baseFrom()!=null||!t.failureNext().isEmpty())throw new IllegalArgumentException("Use the registered read-only implementation-planner");
        var s=store.spec(p.id(),p.version());
        String prompt="Plan this software goal as declarative JSON data. Do not execute the plan. Return AIW_GRAPH_V1 followed by one raw JSON object in the formal completion summary. Use aiw.workflow.graph.v1. Fields: protocol, goal, scope, acceptance, regions, nodes. Regions have key,parent,nodeKeys,entryKeys,joinKey,loopId,loopHeader,imports,carry. ROOT is the only root. Each node has key,region,kind,dependsOn,next and exactly one matching payload: task,branch,jump,ask,end. Kinds: TASK,IF,GOTO,ASK,END. Task payload fields: repositoryRef,baseRevision,baseFrom,runtime,aiProfileId,defaultRole,configRef,candidate,instruction,failureNext. Use one registered defaultRole. IF branch fields: predicate(sourceNode,field,operator,value),trueRegion,falseRegion. Fields: VERDICT,RESULT_REVISION,CHANGED_FILE_COUNT,SUCCESS. Operators: EQ,NE,GT,GE,LT,LE. Branch regions are direct children and share a join node in their parent. Jump fields: target,loopId. Backjumps only target a declared ancestor loopHeader and use its loopId. Carry entries: sourceNode,targetNode,asBase. Ordinary control and dependencies form a DAG. Ask fields: question,context,options,answerRequirements. End: SUCCESS or FAILURE. Keep necessary parallel results explicit. Unknown facts block. No scripts, extra permissions or fixed review roles. Use current repository/runtime/profile metadata unless explicitly needed otherwise. Unclear requirements should be explicit ASK nodes.\nGoal: "+s.goal()+"\nScope: "+s.scope()+"\nAcceptance: "+s.acceptance()+"\nRepository/runtime data: "+repo.encode(t)+"\nCurrent editable graph: "+repo.encode(s);
        var policy=guard.policyRow(p.id());var intent=new ConfigurationContract.Intent(GraphRunner.key("GP",input.requestId()),1,0,p.id(),GraphStore.number(policy,"revision"),t.repositoryRef(),t.baseRevision(),prompt,List.of(),t.runtime(),t.aiProfileId(),"planner",null,null);
        var old=repo.jdbc().queryForList("SELECT * FROM workflow_graph_recommendation WHERE request_id=?",input.requestId());
        long generation=0;
        if(!old.isEmpty()){var row=old.getFirst();if(!p.id().equals(row.get("plan_id"))||!repo.encode(intent).equals(row.get("intent_json")))throw new IllegalStateException("GRAPH_RECOMMENDATION_CONFLICT");
            if(!"BLOCKED".equals(row.get("state")))return row;
            generation=repo.jdbc().queryForObject("SELECT generation FROM execution_preparation WHERE intent_id=? AND intent_version=1",Long.class,intent.intentId());
        }else repo.jdbc().update("INSERT INTO workflow_graph_recommendation(request_id,plan_id,plan_version,intent_json,state) VALUES(?,?,?,?,'PREPARING')",input.requestId(),p.id(),p.version(),repo.encode(intent));
        var actual=new ConfigurationContract.Intent(intent.intentId(),1,generation,intent.planId(),intent.policyRevision(),intent.repositoryRef(),intent.baseRevision(),intent.instruction(),intent.dependencies(),intent.runtime(),intent.aiProfileId(),intent.defaultRole(),null,null);
        var prepared=factory.prepareAuthorized(actual,p.owner());repo.jdbc().update("UPDATE workflow_graph_recommendation SET task_id=?,state=?,diagnostic=? WHERE request_id=?",prepared.taskId(),prepared.state()==ConfigurationContract.State.READY?"WAITING_RESULT":"BLOCKED",prepared.errorCode()==null?null:prepared.errorCode()+":"+String.join(";",prepared.missing()),input.requestId());
        return repo.jdbc().queryForMap("SELECT * FROM workflow_graph_recommendation WHERE request_id=?",input.requestId());
    }
    @Transactional public void capture(String request){
        boundary.lock();var row=repo.jdbc().queryForMap("SELECT * FROM workflow_graph_recommendation WHERE request_id=?",request);if(!"WAITING_RESULT".equals(row.get("state")))return;
        var input=attempts.findCompletedInput(new TaskId((String)row.get("task_id")));if(input.isEmpty()){
            var states=repo.jdbc().queryForList("SELECT state FROM work_task WHERE id=?",String.class,me.asu.aidev.execution.guard.TaskLineage.current(repo.jdbc(),new TaskId((String)row.get("task_id"))).value());
            if(states.stream().anyMatch(s->Set.of("FAILED","CANCELLED").contains(s)))repo.jdbc().update("UPDATE workflow_graph_recommendation SET state='MODEL_FAILED',diagnostic='Planning task failed; manual graph editing remains available' WHERE request_id=?",request);return;}
        String raw=input.get().completionSummary();String candidate=raw;String diagnostic=null;
        try {String marker="AIW_GRAPH_V1";if(raw==null||raw.indexOf(marker)<0||raw.indexOf(marker)!=raw.lastIndexOf(marker))throw new IllegalArgumentException("One AIW_GRAPH_V1 candidate required");
            candidate=raw.substring(raw.indexOf(marker)+marker.length()).strip();if(candidate.length()>131072)throw new IllegalArgumentException("Candidate envelope limit");
            var spec=repo.decode(candidate,Spec.class);validator.validate(spec);
        }catch(IllegalArgumentException e){diagnostic=e.getMessage();}
        repo.jdbc().update("UPDATE workflow_graph_recommendation SET state=?,candidate_json=?,diagnostic=? WHERE request_id=? AND state='WAITING_RESULT'",diagnostic==null?"CANDIDATE":"INVALID_CANDIDATE",candidate,diagnostic,request);
    }
}
