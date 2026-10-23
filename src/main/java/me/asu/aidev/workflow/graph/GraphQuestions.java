package me.asu.aidev.workflow.graph;

import java.util.*;
import me.asu.aidev.authority.*;
import me.asu.aidev.execution.guard.*;
import me.asu.aidev.questions.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import static me.asu.aidev.questions.QuestionContract.*;

/** One source resolver. FormalQuestions remains the sole APPLIED writer. */
@Component
public class GraphQuestions implements SourceResolver {
    private final GraphStore store;private final AuthorityRepository repo;private final ExecutionGuard guard;private final ObjectProvider<FormalQuestions> questions;
    public GraphQuestions(GraphStore store,AuthorityRepository repo,ExecutionGuard guard,ObjectProvider<FormalQuestions> questions){this.store=store;this.repo=repo;this.guard=guard;this.questions=questions;}
    @Override public SourceKind kind(){return SourceKind.WORKFLOW_GRAPH_EXECUTION;}
    @Override public Source sourceFor(String task,String owner){
        var mappings=repo.jdbc().queryForList("SELECT plan_id,execution_id FROM workflow_graph_task WHERE task_id=?",task);if(mappings.isEmpty())return null;
        var row=mappings.getFirst();var p=store.get((String)row.get("plan_id"));if(!p.owner().equals(owner))throw new SecurityException("Graph source owner mismatch");
        String execution=(String)row.get("execution_id");var a=p.runtime().containing(execution);return new Source(kind(),p.id(),a.definitionVersion,task,execution);
    }
    @Override public String validate(Source source,String owner,boolean opening){
        if(source.kind()!=kind()||source.executionId()==null)throw new IllegalArgumentException("Exact graph execution required");
        var p=store.get(source.planId());if(!p.owner().equals(owner))throw new SecurityException("Graph source owner mismatch");
        var a=p.runtime().containing(source.executionId());var e=p.runtime().execution(source.executionId());
        if(a.definitionVersion!=source.planVersion()||!a.state.equals("ACTIVE")||!p.runtime().round(a).nodes.containsValue(e)||e.cancelRequested||!p.state().equals("ACTIVE")||e.state.equals("REPLACED"))throw new IllegalStateException("Graph source is no longer current");
        var spec=store.spec(p.id(),a.definitionVersion);var n=spec.nodes().stream().filter(x->x.key().equals(e.node)).findFirst().orElseThrow();
        if(source.taskId()==null){if(n.kind()!=GraphContract.Kind.ASK||e.taskId!=null||!Set.of("READY","WAITING_QUESTION").contains(e.state))throw new IllegalStateException("Graph ask source mismatch");}
        else {
            var row=repo.jdbc().queryForMap("SELECT plan_id,execution_id FROM workflow_graph_task WHERE task_id=?",source.taskId());
            if(!p.id().equals(row.get("plan_id"))||!e.id.equals(row.get("execution_id"))||!TaskLineage.current(repo.jdbc(),new me.asu.aidev.work.domain.TaskId(source.taskId())).value().equals(e.taskId))throw new IllegalStateException("Graph task source mismatch");
            if(opening&&!source.taskId().equals(e.taskId))throw new IllegalStateException("Only the current physical task may ask");
        }
        return AuthorityRepository.hash(repo.encode(Arrays.asList(p.id(),p.owner(),a.id,a.definitionVersion,e.id,source.taskId(),n)));
    }
    @Override public Application apply(Question q){
        validate(q.source(),q.ownerId(),false);var p=store.get(q.source().planId());var e=p.runtime().execution(q.source().executionId());
        if(!guard.mayAdvancePlan(p.id())||!e.state.equals("WAITING_QUESTION"))return null;
        if(q.source().taskId()==null){
            if(!Objects.equals(q.questionId(),e.questionId))throw new IllegalStateException("Graph question changed");
            e.fact=new GraphState.Fact(e.id,"ANSWER",AuthorityRepository.hash(repo.encode(q.answer())),null,null,q.answer().rawAnswer());GraphRunner.set(e,"DONE",null);
        }else{
            if(!Objects.equals(q.source().taskId(),e.taskId))throw new IllegalStateException("Current graph task changed");
            String next=questions.getObject().continueRegisteredGraph(q);if(next==null)return null;
            e.taskId=next;e.fact=null;repo.jdbc().update("INSERT INTO workflow_graph_task VALUES(?,?,?)",next,p.id(),e.id);GraphRunner.set(e,"RUNNING",null);
        }
        store.event(p.id(),GraphRunner.key("answer",q.questionId()+":"+q.answerRevision()+":"+e.id),e.id,"ANSWER_CONSUMED",Map.of("questionId",q.questionId(),"answerRevision",q.answerRevision(),"executionId",e.id));
        store.save(p,p.state());return new Application(q.source().taskId()==null?null:e.taskId);
    }
    @Override public void planControlled(String plan,String action){
        if(!action.equals("cancel")||repo.jdbc().queryForObject("SELECT COUNT(*) FROM workflow_graph WHERE id=?",Long.class,plan)==0)return;
        var p=store.get(plan);if(Set.of("COMPLETED","FAILED","CANCELLED").contains(p.state()))return;store.save(p,"CANCELLING");
    }
}
