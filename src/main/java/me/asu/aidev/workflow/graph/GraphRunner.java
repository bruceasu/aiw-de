package me.asu.aidev.workflow.graph;

import java.math.BigInteger;
import java.util.*;
import me.asu.aidev.authority.*;
import me.asu.aidev.execution.config.*;
import me.asu.aidev.execution.domain.TaskInput;
import me.asu.aidev.execution.guard.*;
import me.asu.aidev.execution.port.ExecutionAttemptRepository;
import me.asu.aidev.questions.*;
import me.asu.aidev.work.domain.*;
import me.asu.aidev.work.port.WorkTaskRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static me.asu.aidev.workflow.graph.GraphContract.*;
import static me.asu.aidev.workflow.graph.GraphState.*;
import static me.asu.aidev.workflow.graph.GraphStore.number;

/** Pure database state machine. Never calls a model, process, command, Git or transport. */
@Service
public class GraphRunner {
    private final GraphStore store;private final GraphValidator validator;private final AuthorityRepository repo;private final AuthorityBoundary boundary;
    private final ExecutionGuard guard;private final ExecutionLedger ledger;private final AgentFactory factory;
    private final WorkTaskRepository tasks;private final ExecutionAttemptRepository attempts;private final ObjectProvider<FormalQuestions> questions;
    public GraphRunner(GraphStore store,GraphValidator validator,AuthorityRepository repo,AuthorityBoundary boundary,
        ExecutionGuard guard,ExecutionLedger ledger,AgentFactory factory,WorkTaskRepository tasks,ExecutionAttemptRepository attempts,ObjectProvider<FormalQuestions> questions){
        this.store=store;this.validator=validator;this.repo=repo;this.boundary=boundary;this.guard=guard;this.ledger=ledger;this.factory=factory;this.tasks=tasks;this.attempts=attempts;this.questions=questions;
    }
    @Transactional public void tick(String planId){
        boundary.lock();var p=store.get(planId);if(p.state().equals("DRAFT"))return;String before=repo.encode(p.runtime());
        capture(p);
        if(p.state().equals("ACTIVE")&&admitted(p)) {
            if(p.runtime().rootActivation==null){validator.validate(store.spec(p.id(),p.version()));var root=activate(p,"ROOT",p.version(),null);p.runtime().rootActivation=root.id;}
            boolean changed;
            do {String previous=repo.encode(p.runtime());
                for(var a:new ArrayList<>(p.runtime().activations)) if(a.state.equals("ACTIVE")) advance(p,a);
                changed=!previous.equals(repo.encode(p.runtime()));
            }while(changed&&admitted(p));
        } else if(p.state().equals("ACTIVE")) requestGaps(p);
        String next=p.state();
        if(p.runtime().rootActivation!=null){var root=p.runtime().activation(p.runtime().rootActivation);
            if(root.state.equals("SUCCEEDED"))next="COMPLETED";else if(root.state.equals("FAILED"))next="FAILED";}
        if(p.state().equals("CANCELLING")&&planQuiescent(p))next="CANCELLED";
        if(!before.equals(repo.encode(p.runtime()))||!next.equals(p.state()))store.save(p,next);
    }
    private boolean admitted(GraphStore.Plan p){
        var rows=repo.jdbc().queryForList("SELECT owner_id FROM execution_policy WHERE plan_id=?",String.class,p.id());
        return rows.size()==1&&rows.getFirst().equals(p.owner())&&guard.mayAdvancePlan(p.id());
    }
    private Activation activate(GraphStore.Plan p,String region,long version,Activation parent){
        var s=store.spec(p.id(),version);var r=validator.regions(s).get(region);var a=new Activation();
        a.id=key("GA",p.id()+":"+(parent==null?"ROOT":parent.id+":"+parent.iteration)+":"+region);a.region=region;a.definitionVersion=version;
        a.parent=parent==null?null:parent.id;a.parentIteration=parent==null?0:parent.iteration;a.interfaceDigest=interfaceDigest(s,region);
        if(parent!=null) for(String imported:r.imports()){var source=resolve(p,parent,imported);if(source==null)throw new IllegalStateException("Missing fixed ancestor slot "+imported);a.importBindings.put(imported,source.id);}
        for(var child:s.regions())if(region.equals(child.parent()))a.childVersions.put(child.key(),version);
        p.runtime().activations.add(a);newRound(p,a,Map.of());
        store.event(p.id(),key("entry",a.id),null,"REGION_ENTERED",Map.of("activation",a.id,"definitionVersion",version,"interfaceDigest",a.interfaceDigest));return a;
    }
    private void newRound(GraphStore.Plan p,Activation a,Map<String,Fact> carry){
        var s=store.spec(p.id(),a.definitionVersion);var region=validator.regions(s).get(a.region);var r=new Round();r.number=a.iteration;r.carry.putAll(carry);
        for(var n:s.nodes())if(n.region().equals(a.region)){var e=new Execution();e.id=key("GE",a.id+":"+a.iteration+":"+n.key());e.node=n.key();r.nodes.put(n.key(),e);
            n.next().forEach(to->r.edges.add(new Edge(n.key(),to,"SUCCESS")));if(n.task()!=null)n.task().failureNext().forEach(to->r.edges.add(new Edge(n.key(),to,"FAILURE")));
            if(n.branch()!=null)r.edges.add(new Edge(n.key(),validator.regions(s).get(n.branch().trueRegion()).joinKey(),"CHILD_RETURN"));
            if(n.jump()!=null&&n.jump().loopId()==null)r.edges.add(new Edge(n.key(),n.jump().target(),"JUMP"));}
        var directChildren=new LinkedHashSet<String>();var nodes=validator.nodes(s);
        for(String entry:region.entryKeys()) {
            if(nodes.get(entry).region().equals(a.region)){var edge=new Edge("@entry",entry,"ENTRY");edge.state="TAKEN";edge.evidence=a.id+":"+a.iteration;r.edges.add(edge);}
            else directChildren.add(nodes.get(entry).region());
        }
        for(String child:directChildren)r.edges.add(new Edge("@child:"+child,validator.regions(s).get(child).joinKey(),"PARALLEL_RETURN"));
        a.rounds.add(r);
    }
    private boolean importsReady(GraphStore.Plan p,Activation a){
        for(var b:a.importBindings.entrySet()) if(!a.imports.containsKey(b.getKey())) {
            var e=p.runtime().execution(b.getValue());if(e.fact==null||!e.state.equals("DONE"))return false;a.imports.put(b.getKey(),e.fact);
        }return true;
    }
    private void advance(GraphStore.Plan p,Activation a){
        if(!importsReady(p,a)||!admitted(p))return;var s=store.spec(p.id(),a.definitionVersion);var r=p.runtime().round(a);var definitions=validator.nodes(s);
        for(var edge:r.edges) if(edge.kind.equals("PARALLEL_RETURN")) {
            String child=edge.source.substring(7);var ca=child(p,a,child,true);if(ca.state.equals("SUCCEEDED"))decide(p,a,edge,"TAKEN",ca.exitDigest);else if(ca.state.equals("FAILED"))edge.evidence="NECESSARY_REGION_FAILED";
        }
        for(var e:new ArrayList<>(r.nodes.values())) {
            if(!admitted(p)||!a.state.equals("ACTIVE")||r!=p.runtime().round(a))return;
            var n=definitions.get(e.node);
            if(e.cancelRequested){e.reason="CANCEL_REQUESTED";continue;}
            if(e.state.equals("WAITING_SELECTION")) {
                var incoming=r.edges.stream().filter(x->x.target.equals(e.node)).toList();
                if(incoming.isEmpty()||incoming.stream().anyMatch(x->x.state.equals("PENDING")))continue;
                if(incoming.stream().allMatch(x->x.state.equals("SKIPPED"))){set(e,"NOT_SELECTED",null);skip(p,a,n,e);continue;}
                set(e,"READY",null);
            }
            if(e.state.equals("NOT_SELECTED")||e.state.equals("TRANSFERRED")||e.state.equals("REPLACED"))continue;
            if(e.state.equals("RESULT_RECORDED")){set(e,"DONE",null);emit(p,a,n,e);}
            if(e.state.equals("DONE")){if(!e.emitted)emit(p,a,n,e);continue;}
            if(e.state.equals("FAILED_RECORDED")){set(e,"FAILED_BLOCKED","TECHNICAL_FAILURE");
                if(n.task()!=null&&!n.task().failureNext().isEmpty()){for(var edge:r.edges)if(edge.source.equals(n.key())&&edge.state.equals("PENDING")){
                    decide(p,a,edge,edge.kind.equals("FAILURE")?"TAKEN":"SKIPPED",e.fact.digest());e.failureConsumed=true;}}}
            if(!e.state.equals("READY")&&!e.state.equals("WAITING_CHILD"))continue;
            if(!dependenciesReady(p,a,n,e))continue;
            switch(n.kind()) {
                case TASK -> prepare(p,a,n,e,false);
                case IF -> branch(p,a,n,e);
                case ASK -> {var source=new QuestionContract.Source(QuestionContract.SourceKind.WORKFLOW_GRAPH_EXECUTION,p.id(),a.definitionVersion,null,e.id);
                    store.stage(p);
                    var q=questions.getObject().openGraph(source,e.id,n.ask().question(),n.ask().context(),n.ask().options(),n.ask().answerRequirements(),p.owner());e.questionId=q.questionId();set(e,"WAITING_QUESTION",null);}
                case GOTO -> jump(p,a,n,e);
                case END -> {set(e,"DONE",null);e.emitted=true;store.event(p.id(),key("end",e.id),e.id,"PATH_ENDED",n.end());}
            }
        }
        if(a.state.equals("ACTIVE")&&r==p.runtime().round(a))finishRegion(p,a);
    }
    private Activation child(GraphStore.Plan p,Activation parent,String region,boolean selected){
        var round=p.runtime().round(parent);String existing=round.children.get(region);if(existing!=null)return p.runtime().activation(existing);
        Long version=parent.childVersions.get(region);if(version==null)throw new IllegalStateException("Child version not bound");
        var a=activate(p,region,version,parent);round.children.put(region,a.id);
        if(!selected){a.state="NOT_SELECTED";a.rounds.clear();a.exitDigest=AuthorityRepository.hash(a.id+":NOT_SELECTED");store.event(p.id(),key("skip",a.id),null,"REGION_NOT_SELECTED",a.exitDigest);}
        return a;
    }
    private boolean dependenciesReady(GraphStore.Plan p,Activation a,Node n,Execution target){
        for(String dependency:n.dependsOn()){
            var fact=dependency(p,a,dependency);var e=resolve(p,a,dependency);
            if(e!=null&&e.state.equals("NOT_SELECTED"))continue;
            if(fact==null||!Set.of("COMPLETED","ANSWER").contains(fact.kind())){target.reason="WAITING_NECESSARY_RESULT:"+dependency;return false;}
        }
        target.reason=null;return true;
    }
    private Fact dependency(GraphStore.Plan p,Activation a,String node){
        if(a.imports.containsKey(node))return a.imports.get(node);var e=p.runtime().round(a).nodes.get(node);return e!=null&&e.state.equals("DONE")?e.fact:null;
    }
    private Execution resolve(GraphStore.Plan p,Activation a,String node){
        var own=p.runtime().round(a).nodes.get(node);if(own!=null)return own;
        if(a.importBindings.containsKey(node))return p.runtime().execution(a.importBindings.get(node));
        if(a.parent!=null){var parent=p.runtime().activation(a.parent);var pr=parent.rounds.stream().filter(r->r.number==a.parentIteration).findFirst().orElseThrow();var e=pr.nodes.get(node);if(e!=null)return e;return resolve(p,parent,node);}return null;
    }
    private void prepare(GraphStore.Plan p,Activation a,Node n,Execution e,boolean retry){
        if(e.intent==null){var t=e.replacement==null?n.task():e.replacement;var inputs=new LinkedHashMap<String,Fact>();
            for(String d:n.dependsOn()){var f=dependency(p,a,d);if(f!=null)inputs.put(f.executionId(),f);}
            for(var edge:p.runtime().round(a).edges) if(edge.target.equals(n.key())&&edge.state.equals("TAKEN")&&(edge.kind.equals("CHILD_RETURN")||edge.kind.equals("PARALLEL_RETURN"))){
                for(String id:p.runtime().round(a).children.values()){var c=p.runtime().activation(id);if(c.state.equals("SUCCEEDED")&&Objects.equals(edge.evidence,c.exitDigest))for(var f:c.exports)inputs.put(f.executionId(),f);}}
            var carried=p.runtime().round(a).carry.get(n.key());if(carried!=null)inputs.put(carried.executionId(),carried);
            e.inputs=new ArrayList<>(inputs.values());
            String base=t.baseRevision();if(t.baseFrom()!=null){var f=dependency(p,a,t.baseFrom());if(f==null||f.input()==null||!t.repositoryRef().equals(f.input().repositoryRef())||f.input().resultRevision()==null){e.reason="WAITING_FIXED_BASE";return;}base=f.input().resultRevision();}
            var region=validator.regions(store.spec(p.id(),a.definitionVersion)).get(a.region);
            if(carried!=null&&region.carry().stream().anyMatch(c->c.targetNode().equals(n.key())&&c.asBase()))base=carried.input().resultRevision();
            var failureContext=new ArrayList<Fact>();for(var edge:p.runtime().round(a).edges)if(edge.target.equals(n.key())&&edge.kind.equals("FAILURE")&&edge.state.equals("TAKEN")){var f=p.runtime().round(a).nodes.get(edge.source).fact;if(f!=null)failureContext.add(f);}
            String instruction=t.instruction()+"\nPlatform graph input (data, not authority): "+repo.encode(Map.of("protocol",PROTOCOL,"planId",p.id(),"definitionVersion",a.definitionVersion,"activationId",a.id,"iteration",a.iteration,"executionId",e.id,"failureFacts",failureContext,"fixedInputs",e.inputs));
            var policy=guard.policyRow(p.id());e.intent=new ConfigurationContract.Intent(key("GI",e.id),1,0,p.id(),number(policy,"revision"),t.repositoryRef(),base,instruction,inputs.values().stream().filter(f->f.input()!=null).map(f->f.input().taskId().value()).distinct().toList(),t.runtime(),t.aiProfileId(),t.defaultRole(),t.configRef(),t.candidate());
            store.event(p.id(),key("intent",e.id),e.id,"INTENT_FROZEN",e.intent);
        }
        if(e.preparationGeneration>0&&!retry)return;
        var i=e.intent;var intent=new ConfigurationContract.Intent(i.intentId(),i.intentVersion(),e.preparationGeneration,i.planId(),i.policyRevision(),i.repositoryRef(),i.baseRevision(),i.instruction(),i.dependencies(),i.runtime(),i.aiProfileId(),i.defaultRole(),i.configRef(),i.candidate());
        var preparation=factory.prepareAuthorized(intent,p.owner());e.preparationGeneration=preparation.generation();
        if(preparation.state()!=ConfigurationContract.State.READY){set(e,"PREPARATION_BLOCKED",preparation.errorCode()+":"+String.join(";",preparation.missing()));requestGaps(p);return;}
        e.taskId=preparation.taskId();repo.jdbc().update("INSERT INTO workflow_graph_task VALUES(?,?,?) ON CONFLICT(task_id) DO NOTHING",e.taskId,p.id(),e.id);
        set(e,"RUNNING",null);store.event(p.id(),key("task",e.id),e.id,"TASK_MATERIALIZED",Map.of("taskId",e.taskId,"intentId",intent.intentId(),"preparationGeneration",preparation.generation()));
    }
    public void retryPreparation(GraphStore.Plan p,Activation a,Node n,Execution e){prepare(p,a,n,e,true);}
    private void capture(GraphStore.Plan p){
        for(var a:p.runtime().activations)for(var r:a.rounds)for(var e:r.nodes.values())if(e.taskId!=null&&!Set.of("DONE","REPLACED","TRANSFERRED").contains(e.state)){
            var t=tasks.find(new TaskId(e.taskId)).orElseThrow();
            boolean unknown=repo.jdbc().queryForObject("SELECT COUNT(*) FROM execution_operation WHERE task_id=? AND state='UNKNOWN'",Long.class,e.taskId)>0
                ||Set.of(TaskState.COMPLETED,TaskState.FAILED,TaskState.CANCELLED).contains(t.state())&&guard.unknownTask(e.taskId);
            if(unknown){if(!e.state.equals("UNKNOWN"))set(e,"UNKNOWN","HUMAN_RECONCILIATION_REQUIRED");continue;}
            if(t.state()==TaskState.COMPLETED){var input=attempts.findCompletedInput(t.id()).orElseThrow();if(!input.taskId().equals(t.id()))throw new IllegalStateException("Graph slot lineage not registered");
                var f=new Fact(e.id,"COMPLETED",AuthorityRepository.hash(repo.encode(input)),input,null,null);if(!f.equals(e.fact)){e.fact=f;set(e,"RESULT_RECORDED",null);store.event(p.id(),key("fact",e.id+":"+input.attemptId()+":"+f.digest()),e.id,"COMPLETION_RECORDED",f);}}
            else if(t.state()==TaskState.FAILED||t.state()==TaskState.CANCELLED){
                var rows=repo.jdbc().queryForList("SELECT id,failure_category,termination_reason AS failure_code FROM execution_attempt WHERE task_id=? ORDER BY attempt_no DESC LIMIT 1",e.taskId);var row=rows.isEmpty()?Map.<String,Object>of():rows.getFirst();
                var f=new Failure(e.taskId,(String)row.get("id"),t.state().name(),(String)row.get("failure_category"),(String)row.get("failure_code"),AuthorityRepository.hash(repo.encode(row)));
                var fact=new Fact(e.id,t.state().name(),AuthorityRepository.hash(repo.encode(f)),null,f,null);
                if(!fact.equals(e.fact)){e.fact=fact;set(e,t.state()==TaskState.FAILED?"FAILED_RECORDED":"CANCELLED",t.state().name());store.event(p.id(),key("fact",e.id+":"+fact.digest()),e.id,"FAILURE_RECORDED",fact);}}
            else if(t.state()==TaskState.WAITING_INPUT||t.state()==TaskState.YIELDING){if(!e.state.equals("WAITING_QUESTION"))set(e,"WAITING_QUESTION",null);}
            else if(e.state.equals("UNKNOWN")||e.state.equals("RETRY_PENDING"))set(e,"RUNNING",null);
        }
    }
    private void branch(GraphStore.Plan p,Activation a,Node n,Execution e){
        var predicate=n.branch().predicate();var source=dependency(p,a,predicate.sourceNode());if(source==null||source.input()==null){e.reason="PREDICATE_SOURCE_UNKNOWN";return;}
        if(predicate.field()==Field.VERDICT&&source.input().completionVerdict()==null||predicate.field()==Field.RESULT_REVISION&&source.input().resultRevision()==null){e.reason="PREDICATE_FIELD_UNKNOWN";return;}
        boolean selected=evaluate(predicate,source.input());String yes=selected?n.branch().trueRegion():n.branch().falseRegion(),no=selected?n.branch().falseRegion():n.branch().trueRegion();
        var chosen=child(p,a,yes,true);child(p,a,no,false);
        store.event(p.id(),key("branch",e.id),e.id,"BRANCH_SELECTED",Map.of("source",source,"selectedRegion",yes,"notSelectedRegion",no));
        if(chosen.state.equals("SUCCEEDED")){set(e,"DONE",null);e.emitted=true;for(var edge:p.runtime().round(a).edges)if(edge.source.equals(n.key()))decide(p,a,edge,"TAKEN",chosen.exitDigest);}
        else set(e,"WAITING_CHILD",chosen.state.equals("FAILED")?"NECESSARY_REGION_FAILED":null);
    }
    private boolean evaluate(Predicate p,TaskInput input){
        String actual=switch(p.field()){case VERDICT->input.completionVerdict()==null?null:input.completionVerdict().name();case RESULT_REVISION->input.resultRevision();case CHANGED_FILE_COUNT->Integer.toString(input.changedFiles().size());case SUCCESS->"true";};
        if(actual==null)throw new IllegalStateException("PREDICATE_FIELD_UNKNOWN");int cmp=p.field()==Field.CHANGED_FILE_COUNT?Long.compare(Long.parseLong(actual),Long.parseLong(p.value())):actual.compareTo(p.value());
        return switch(p.operator()){case EQ->cmp==0;case NE->cmp!=0;case GT->cmp>0;case GE->cmp>=0;case LT->cmp<0;case LE->cmp<=0;};
    }
    private void emit(GraphStore.Plan p,Activation a,Node n,Execution e){
        if(n.kind()==Kind.IF||n.kind()==Kind.GOTO||n.kind()==Kind.END)return;
        for(var edge:p.runtime().round(a).edges)if(edge.source.equals(n.key())){
            if(e.replacementFor!=null&&!edge.state.equals("PENDING"))continue;
            decide(p,a,edge,edge.kind.equals("FAILURE")?"SKIPPED":"TAKEN",e.fact==null?e.id:e.fact.digest());}e.emitted=true;
    }
    private void skip(GraphStore.Plan p,Activation a,Node n,Execution e){
        if(n.branch()!=null){child(p,a,n.branch().trueRegion(),false);child(p,a,n.branch().falseRegion(),false);}
        for(var edge:p.runtime().round(a).edges)if(edge.source.equals(n.key()))decide(p,a,edge,"SKIPPED",e.id+":NOT_SELECTED");e.emitted=true;
    }
    private void decide(GraphStore.Plan p,Activation a,Edge edge,String state,String evidence){
        if(!edge.state.equals("PENDING")){if(!edge.state.equals(state)||!Objects.equals(edge.evidence,evidence))throw new IllegalStateException("IMMUTABLE_EDGE_DECISION");return;}
        edge.state=state;edge.evidence=evidence;store.event(p.id(),key("edge",a.id+":"+a.iteration+":"+edge.source+":"+edge.target+":"+edge.kind),null,"EDGE_DECIDED",Map.of("activation",a.id,"iteration",a.iteration,"edge",edge));
    }
    private void jump(GraphStore.Plan p,Activation a,Node n,Execution e){
        if(n.jump().loopId()==null){var target=p.runtime().round(a).nodes.get(n.jump().target());
            if(!target.state.equals("WAITING_SELECTION")&&!target.state.equals("READY")){e.reason="GOTO_TARGET_ALREADY_ACTIVE";return;}
            var spec=store.spec(p.id(),a.definitionVersion);var tn=validator.nodes(spec).get(target.node);if(!dependenciesReady(p,a,tn,target)){e.reason="GOTO_NECESSARY_INPUT_MISSING";return;}
            for(var edge:p.runtime().round(a).edges)if(edge.source.equals(n.key()))decide(p,a,edge,"TAKEN",e.id);set(e,"DONE",null);e.emitted=true;return;}
        Activation loop=a;while(!Objects.equals(validator.regions(store.spec(p.id(),loop.definitionVersion)).get(loop.region).loopId(),n.jump().loopId())){if(loop.parent==null)throw new IllegalStateException("Loop ancestor missing");loop=p.runtime().activation(loop.parent);}
        var affected=descendants(p,loop);if(!allQuiescent(p,affected)){e.reason="BACKJUMP_WAITING_QUIESCENCE";return;}
        for(var region:affected)if(!region.rounds.isEmpty())for(var slot:p.runtime().round(region).nodes.values()){
            var nd=validator.nodes(store.spec(p.id(),region.definitionVersion)).get(slot.node);
            boolean started=nd.kind()==Kind.TASK&&slot.taskId!=null||nd.kind()==Kind.ASK&&slot.questionId!=null;
            if(started&&!Set.of("DONE","FAILED_BLOCKED","FAILED_RECORDED","CANCELLED").contains(slot.state)){e.reason="BACKJUMP_WAITING_SELECTED_WORK";return;}}
        var definition=validator.regions(store.spec(p.id(),loop.definitionVersion)).get(loop.region);var carry=new LinkedHashMap<String,Fact>();
        for(var c:definition.carry()){var f=p.runtime().round(loop).nodes.get(c.sourceNode()).fact;if(f==null||f.input()==null||f.input().resultRevision()==null){e.reason="BACKJUMP_CARRY_RESULT_MISSING";return;}carry.put(c.targetNode(),f);}
        String transition=key("backjump",e.id);var budgets=ledger.snapshot(p.id()).stream().filter(b->b.get("dimension").equals("LOOP_BACKJUMPS")&&b.get("loop_id").equals(n.jump().loopId())).toList();
        if(!budgets.isEmpty()&&!available(budgets.getFirst(),1)){requestIncrement(p,budgets.getFirst(),1,transition);e.reason="LOOP_INCREMENT_REQUIRED";return;}
        ledger.backjump(p.id(),n.jump().loopId(),transition);
        for(var region:affected)if(!region.rounds.isEmpty()){
            var old=p.runtime().round(region);old.state="REPEATED";for(var slot:old.nodes.values())if(!Set.of("DONE","FAILED_BLOCKED","FAILED_RECORDED","CANCELLED","NOT_SELECTED").contains(slot.state))set(slot,"TRANSFERRED",transition);
            if(region!=loop)region.state="TRANSFERRED";}
        set(e,"TRANSFERRED",transition);loop.iteration=Math.incrementExact(loop.iteration);newRound(p,loop,carry);
        store.event(p.id(),transition,e.id,"LOOP_BACKJUMP",Map.of("activation",loop.id,"iteration",loop.iteration,"carry",carry));
    }
    private void finishRegion(GraphStore.Plan p,Activation a){
        var r=p.runtime().round(a);if(!allQuiescent(p,descendants(p,a)))return;
        if(a.parent==null&&!planQuiescent(p))return;
        var spec=store.spec(p.id(),a.definitionVersion);var definitions=validator.nodes(spec);
        boolean failure=r.nodes.values().stream().anyMatch(e->e.state.equals("DONE")&&definitions.get(e.node).end()==Conclusion.FAILURE);
        if(failure){
            var blocked=new HashSet<String>();for(var e:r.nodes.values())if(Set.of("FAILED_BLOCKED","FAILED_RECORDED","CANCELLED").contains(e.state)||e.state.equals("WAITING_CHILD")&&"NECESSARY_REGION_FAILED".equals(e.reason))blocked.add(e.node);
            for(var edge:r.edges)if(edge.kind.equals("PARALLEL_RETURN")&&edge.evidence!=null&&edge.evidence.equals("NECESSARY_REGION_FAILED"))blocked.add(edge.target);
            boolean progress;do{int before=blocked.size();for(var edge:r.edges)if(edge.state.equals("PENDING")&&blocked.contains(edge.source))blocked.add(edge.target);progress=blocked.size()!=before;}while(progress);
            if(r.nodes.values().stream().anyMatch(e->!Set.of("DONE","NOT_SELECTED","FAILED_BLOCKED","FAILED_RECORDED","CANCELLED").contains(e.state)&&!(blocked.contains(e.node)&&Set.of("WAITING_SELECTION","READY","WAITING_CHILD").contains(e.state))))return;
            for(String child:r.children.values())if(!Set.of("SUCCEEDED","FAILED","NOT_SELECTED").contains(p.runtime().activation(child).state))return;
            a.state="FAILED";r.state="FAILED";a.exitDigest=AuthorityRepository.hash(a.id+":"+a.iteration+":FAILED");store.event(p.id(),key("failed-exit",a.id),null,"REGION_FAILED",a.exitDigest);return;}
        if(r.nodes.values().stream().anyMatch(e->!Set.of("DONE","NOT_SELECTED").contains(e.state)))return;
        for(String child:r.children.values())if(!Set.of("SUCCEEDED","NOT_SELECTED").contains(p.runtime().activation(child).state))return;
        var exports=new ArrayList<Fact>();for(var e:r.nodes.values())if(e.state.equals("DONE")&&e.fact!=null)exports.add(e.fact);
        for(String child:r.children.values())if(p.runtime().activation(child).state.equals("SUCCEEDED"))exports.addAll(p.runtime().activation(child).exports);
        a.exports=exports;a.exitDigest=AuthorityRepository.hash(repo.encode(Map.of("activation",a.id,"iteration",a.iteration,"results",exports)));a.state="SUCCEEDED";r.state="SUCCEEDED";
        store.event(p.id(),key("exit",a.id),null,"REGION_EXIT_PUBLISHED",Map.of("activation",a.id,"iteration",a.iteration,"digest",a.exitDigest,"results",exports));
    }
    private List<Activation> descendants(GraphStore.Plan p,Activation root){var result=new ArrayList<Activation>();result.add(root);for(int i=0;i<result.size();i++){var a=result.get(i);for(var c:p.runtime().activations)if(Objects.equals(c.parent,a.id)&&c.parentIteration==a.iteration&&!c.state.equals("TRANSFERRED"))result.add(c);}return result;}
    private boolean allQuiescent(GraphStore.Plan p,List<Activation> regions){
        for(var a:regions)for(var r:a.rounds)for(var e:r.nodes.values())if(e.taskId!=null){var t=tasks.find(new TaskId(e.taskId)).orElseThrow();if(guard.unknownTask(e.taskId)||!Set.of(TaskState.COMPLETED,TaskState.FAILED,TaskState.CANCELLED,TaskState.CONTINUED).contains(t.state())||tasks.findLease(t.id()).isPresent())return false;}
        return true;
    }
    /** Whole-plan completion includes planning tasks and every physical continuation, not only graph nodes. */
    private boolean planQuiescent(GraphStore.Plan p){
        for(String id:repo.jdbc().queryForList("SELECT task_id FROM execution_context WHERE plan_id=?",String.class,p.id())){
            var task=tasks.find(new TaskId(id)).orElseThrow();
            if(!Set.of(TaskState.COMPLETED,TaskState.FAILED,TaskState.CANCELLED,TaskState.CONTINUED).contains(task.state())||tasks.findLease(task.id()).isPresent()||guard.unknownTask(id))return false;
            if(repo.jdbc().queryForObject("SELECT COUNT(*) FROM execution_attempt WHERE task_id=? AND state IN ('CLAIMED','RUNNING')",Long.class,id)>0)return false;
        }
        return ledger.snapshot(p.id()).stream().noneMatch(b->number(b,"unknown_count")>0);
    }
    private void requestGaps(GraphStore.Plan p){
        var policies=repo.jdbc().queryForList("SELECT * FROM execution_policy WHERE plan_id=?",p.id());if(policies.size()!=1||!policies.getFirst().get("owner_id").equals(p.owner()))return;var row=policies.getFirst();
        if(AuthorityRepository.flag(row.get("paused"))||AuthorityRepository.flag(row.get("revoked")))return;
        var policy=repo.decode((String)row.get("policy_json"),GuardContract.Policy.class);if(!policy.budget().configured())return;
        for(var b:ledger.snapshot(p.id())){if(b.get("limit_value")==null||number(b,"unknown_count")!=0)continue;
            long needed=switch(String.valueOf(b.get("dimension"))){case "TIME_MS"->policy.budget().attemptTimeMs();case "COST_MICROS"->policy.budget().attemptCostMicros();case "TASKS"->1L;default->0L;};
            if(needed>0&&!available(b,needed))requestIncrement(p,b,needed,key("gap",p.id()+":"+b.get("dimension")+":"+b.get("limit_value")));}
    }
    private boolean available(Map<String,Object> b,long needed){return b.get("limit_value")!=null&&number(b,"unknown_count")==0&&BigInteger.valueOf(number(b,"limit_value")).subtract(BigInteger.valueOf(number(b,"used_value"))).subtract(BigInteger.valueOf(number(b,"reserved_value"))).compareTo(BigInteger.valueOf(needed))>=0;}
    private void requestIncrement(GraphStore.Plan p,Map<String,Object> b,long needed,String transition){
        if(b.get("limit_value")==null||number(b,"unknown_count")!=0)return;
        long limit=number(b,"limit_value");long amount=BigInteger.valueOf(number(b,"used_value")).add(BigInteger.valueOf(number(b,"reserved_value"))).add(BigInteger.valueOf(needed)).subtract(BigInteger.valueOf(limit)).max(BigInteger.ONE).longValueExact();
        String request=UUID.nameUUIDFromBytes((p.id()+":"+transition+":"+b.get("dimension")+":"+b.get("loop_id")+":"+limit).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
        if(repo.jdbc().queryForObject("SELECT COUNT(*) FROM formal_question WHERE owner_id=? AND plan_id=? AND source_key=?",Long.class,p.owner(),p.id(),"budget:"+request)>0)return;
        var auth=new QuestionContract.Authorization(request,GuardContract.Dimension.valueOf((String)b.get("dimension")),String.valueOf(b.get("loop_id")).isEmpty()?null:String.valueOf(b.get("loop_id")),amount,Math.addExact(limit,amount));
        questions.getObject().requestBudget(new QuestionContract.BudgetQuestion(p.id(),number(guard.policyRow(p.id()),"revision"),"Execution is waiting for an explicit "+auth.dimension()+" increment: "+amount+"; cumulative ceiling "+auth.cumulativeCeiling(),auth),p.owner());
    }
    public static String key(String prefix,String value){return prefix+"-"+AuthorityRepository.hash(value);}
    public static void set(Execution e,String state,String reason){if(!e.state.equals(state)||!Objects.equals(e.reason,reason)){e.state=state;e.reason=reason;e.version++;}}
    public String interfaceDigest(Spec spec,String region){var r=validator.regions(spec).get(region);var external=new ArrayList<Object>();var ns=validator.nodes(spec);var rs=validator.regions(spec);
        for(var n:spec.nodes()){boolean inside=GraphValidator.ancestor(rs,region,n.region());if(inside){for(String d:n.dependsOn())if(!GraphValidator.ancestor(rs,region,ns.get(d).region()))external.add(List.of(n.key(),"dependsOn",d));if(n.task()!=null&&n.task().baseFrom()!=null&&!GraphValidator.ancestor(rs,region,ns.get(n.task().baseFrom()).region()))external.add(List.of(n.key(),"baseFrom",n.task().baseFrom()));if(n.branch()!=null&&!GraphValidator.ancestor(rs,region,ns.get(n.branch().predicate().sourceNode()).region()))external.add(List.of(n.key(),"predicate",n.branch().predicate()));}
            else for(String d:n.dependsOn())if(GraphValidator.ancestor(rs,region,ns.get(d).region()))external.add(List.of(n.key(),"export",d));}
        return AuthorityRepository.hash(repo.encode(Arrays.asList(r.key(),r.parent(),r.entryKeys(),r.joinKey(),r.loopId(),r.loopHeader(),r.imports(),r.carry(),external)));}
}
