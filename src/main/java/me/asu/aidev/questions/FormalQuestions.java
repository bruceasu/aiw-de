package me.asu.aidev.questions;

import java.time.Instant;
import java.util.*;
import me.asu.aidev.authority.*;
import me.asu.aidev.execution.guard.*;
import me.asu.aidev.webconsole.security.WebConsoleSecurityService;
import me.asu.aidev.work.domain.*;
import me.asu.aidev.work.port.WorkTaskRepository;
import me.asu.aidev.workflow.application.WorkflowPlanService;
import me.asu.aidev.workflow.domain.WorkflowPlan;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static me.asu.aidev.questions.QuestionContract.*;
import static me.asu.aidev.execution.guard.GuardContract.*;

/** Durable question facts and a single transactional consumer; transports have no progression authority. */
@Service
public class FormalQuestions {
    @org.springframework.beans.factory.annotation.Autowired private me.asu.aidev.worker.port.WorkerRepository workers;
    private final AuthorityRepository repo;private final AuthorityBoundary boundary;private final ExecutionGuard guard;
    private final ExecutionYieldService yields;private final ExecutionLedger ledger;private final WorkTaskRepository tasks;
    private final WorkflowPlanService plans;private final WebConsoleSecurityService security;
    private final ObjectProvider<SourceResolver> resolvers;
    public FormalQuestions(AuthorityRepository repo,AuthorityBoundary boundary,ExecutionGuard guard,ExecutionYieldService yields,
            ExecutionLedger ledger,WorkTaskRepository tasks,WorkflowPlanService plans,WebConsoleSecurityService security,ObjectProvider<SourceResolver> resolvers) {
        this.repo=repo;this.boundary=boundary;this.guard=guard;this.yields=yields;this.ledger=ledger;this.tasks=tasks;this.plans=plans;this.security=security;this.resolvers=resolvers;
    }
    public Question get(String id) {
        var r=repo.jdbc().queryForMap("SELECT * FROM formal_question WHERE id=?",id);
        var q=repo.decode(String.valueOf(r.get("payload_json")),Question.class);
        return new Question(q.protocol(),q.questionId(),q.ownerId(),q.source(),q.sourceAttemptId(),q.sourceQuestionKey(),q.kind(),q.question(),q.context(),q.options(),q.answerRequirements(),
                ((Number)r.get("request_version")).longValue(),State.valueOf(String.valueOf(r.get("state"))),((Number)r.get("answer_revision")).longValue(),
                r.get("answer_json")==null?null:repo.decode(String.valueOf(r.get("answer_json")),Answer.class),q.checkpoint(),q.authorization(),
                (String)r.get("successor_task_id"),(String)r.get("blocked_reason"));
    }
    public String owner(String plan) { return String.valueOf(guard.policyRow(plan).get("owner_id")); }
    @Transactional public Source source(String task,Lease lease) {
        boundary.lock();guard.verifyLease(task,lease,true,true);var c=guard.context(task);var t=tasks.find(new TaskId(task)).orElseThrow();
        var graph=resolvers.orderedStream().map(r->r.sourceFor(task,c.ownerId())).filter(Objects::nonNull).toList();
        if(graph.size()>1) throw new IllegalStateException("Ambiguous graph task source");if(graph.size()==1) return graph.getFirst();
        if(t.subjectType().equals("flexible-plan")) return new Source(SourceKind.LEGACY_PLAN_TASK,c.planId(),plans.get(c.planId()).version(),task,null);
        if(t.subjectType().equals("configured-plan")) return new Source(SourceKind.LEGACY_PLAN_TASK,c.planId(),c.grantRevision(),task,null);
        var sources=resolvers.orderedStream().map(r->r.sourceFor(task,c.ownerId())).filter(Objects::nonNull).toList();
        if(sources.size()!=1) throw new IllegalArgumentException("Unsupported or ambiguous task source");return sources.getFirst();
    }
    public void human(String owner,String action) {
        guard.owner(owner);if(security.currentDelegationAuthority(boundary.human(),action).isEmpty()) throw new SecurityException("Current human role required");
    }
    public void readHuman(String owner) {
        guard.owner(owner);if(security.currentDelegationAuthority(boundary.human(),"A19_ANSWER").isEmpty()&&security.currentDelegationAuthority(boundary.human(),"A18_BUDGET").isEmpty()) throw new SecurityException("Current owner operation or approval role required to read work questions");
    }
    @Transactional public List<Question> humanList(String plan) { boundary.lock();readHuman(owner(plan));return list(plan); }
    public List<Question> list(String plan) { return repo.jdbc().queryForList("SELECT id FROM formal_question WHERE plan_id=? ORDER BY created_ms,id",String.class,plan).stream().map(this::get).toList(); }
    private SourceResolver resolver(Source source) {
        var found=resolvers.orderedStream().filter(r->r.kind()==source.kind()).toList();
        if(found.size()!=1) throw new IllegalStateException("Question source resolver unavailable or ambiguous");return found.getFirst();
    }
    private String validate(Source source,String owner,boolean opening) {
        if(!owner(source.planId()).equals(owner)) throw new SecurityException("Question source owner mismatch");
        if(AuthorityRepository.flag(guard.policyRow(source.planId()).get("revoked"))) throw new IllegalStateException("Question source cancelled");
        if(source.kind()==SourceKind.WORKFLOW_GRAPH_EXECUTION) return resolver(source).validate(source,owner,opening);
        if(source.taskId()==null||source.executionId()!=null) throw new IllegalArgumentException("Exact existing task source required");
        var task=tasks.find(new TaskId(source.taskId())).orElseThrow();var c=guard.context(source.taskId());
        if(resolvers.orderedStream().anyMatch(r->r.sourceFor(source.taskId(),owner)!=null)) throw new IllegalArgumentException("Graph source cannot use the existing-task consumer");
        if(!c.ownerId().equals(owner)||!c.planId().equals(source.planId())||!task.subjectId().equals(source.planId())) throw new SecurityException("Task is outside this plan");
        if(task.subjectType().equals("flexible-plan")) {
            var p=plans.get(source.planId());
            if(opening && (p.version()!=source.planVersion()||p.state()!=WorkflowPlan.State.CONFIRMED)) throw new IllegalStateException("Plan changed");
            if(me.asu.aidev.workflow.domain.WorkflowTaskPosition.parse(p,task.id()).isEmpty()) throw new IllegalArgumentException("Unknown workflow slot");
            return AuthorityRepository.hash(repo.encode(p.spec()));
        }
        if(!task.subjectType().equals("configured-plan")||repo.jdbc().queryForObject("SELECT COUNT(*) FROM execution_task_configuration WHERE task_id=?",Long.class,source.taskId())!=1)
            throw new IllegalArgumentException("Unsupported existing task source");
        if(opening && c.grantRevision()!=source.planVersion()) throw new IllegalStateException("Prepared plan policy changed");
        return AuthorityRepository.hash(c.planId()+":"+c.configurationDigest());
    }
    private Question insert(Question q,String sourceDigest,String requestDigest) {
        long now=System.currentTimeMillis();repo.jdbc().update("INSERT INTO formal_question(id,owner_id,plan_id,source_key,source_digest,request_digest,payload_json,state,request_version,created_ms,updated_ms) VALUES(?,?,?,?,?,?,?,'OPEN',1,?,?)",
                q.questionId(),q.ownerId(),q.source().planId(),q.sourceQuestionKey(),sourceDigest,requestDigest,repo.encode(q),now,now);return q;
    }
    /** Trusted graph ask port. FD-026 owns source validation and advancement, never the question store. */
    @Transactional public Question openGraph(Source source,String sourceKey,String question,String context,List<String> options,String requirements,String expectedOwner) {
        boundary.lock();if(source.kind()!=SourceKind.WORKFLOW_GRAPH_EXECUTION||source.taskId()!=null) throw new IllegalArgumentException("Exact graph ask execution required");
        text(sourceKey,128);text(question,8000);context=context==null?"":context;requirements=requirements==null?"":requirements;
        if(context.length()>8000||requirements.length()>2000||context.indexOf('\0')>=0||requirements.indexOf('\0')>=0)throw new IllegalArgumentException("Question projection limit");
        options=options==null?List.of():List.copyOf(options);if(options.size()>12) throw new IllegalArgumentException("Option limit");options.forEach(s->text(s,1000));
        String sourceDigest=validate(source,expectedOwner,true),digest=AuthorityRepository.hash(repo.encode(Arrays.asList(source,sourceKey,question,context,options,requirements)));
        var old=repo.jdbc().queryForList("SELECT id,request_digest FROM formal_question WHERE owner_id=? AND plan_id=? AND source_key=?",expectedOwner,source.planId(),sourceKey);
        if(!old.isEmpty()) {if(!digest.equals(old.getFirst().get("request_digest"))) throw new IllegalStateException("QUESTION_SOURCE_CONFLICT");return get(String.valueOf(old.getFirst().get("id")));}
        return insert(new Question(QuestionContract.PROTOCOL,"Q-"+UUID.randomUUID(),expectedOwner,source,null,sourceKey,Kind.PROFESSIONAL,question,context,options,requirements,1,State.OPEN,0,null,null,null,null,null),sourceDigest,digest);
    }
    @Transactional public Object open(String taskId,Open input) {
        boundary.lock();if(!taskId.equals(input.source().taskId())) throw new IllegalArgumentException("Exact task source required");
        guard.verifyLease(taskId,input.lease(),true,true);var c=guard.context(taskId);String digest=AuthorityRepository.hash(repo.encode(input));
        var previous=repo.jdbc().queryForList("SELECT id,request_digest FROM formal_question WHERE owner_id=? AND plan_id=? AND source_key=?",c.ownerId(),c.planId(),input.sourceQuestionKey());
        if(!previous.isEmpty()) {if(!digest.equals(previous.getFirst().get("request_digest"))) throw new IllegalStateException("QUESTION_SOURCE_CONFLICT");return opened(get(String.valueOf(previous.getFirst().get("id"))),input.lease());}
        String sourceDigest=validate(input.source(),c.ownerId(),true);
        var checkpoint=input.checkpoint();var original=tasks.find(new TaskId(taskId)).orElseThrow();
        if(checkpoint==null||!Objects.equals(checkpoint.repositoryRef(),original.repositoryRef())||!Objects.equals(checkpoint.baseRevision(),original.baseRevision())||!checkpoint.configurationDigest().equals(c.configurationDigest()))
            throw new IllegalArgumentException("Fixed task checkpoint required");
        if(!workers.find(new me.asu.aidev.worker.domain.WorkerId(input.lease().workerId())).orElseThrow().capabilities().contains(checkpoint.storageCapability())) throw new SecurityException("Checkpoint store is not registered by this Worker");
        String operation="Q-"+AuthorityRepository.hash(taskId+":"+input.sourceQuestionKey());
        var permit=guard.begin(taskId,new Begin(GuardContract.PROTOCOL,c.instanceId(),input.lease(),operation,Tool.QUESTION,checkpoint.repositoryRef(),".",digest,c.grantRevision()));
        if(!permit.execute()) throw new SecurityException("Question permission required");
        String id="Q-"+UUID.randomUUID();
        var q=new Question(QuestionContract.PROTOCOL,id,c.ownerId(),input.source(),input.lease().attemptId(),input.sourceQuestionKey(),Kind.PROFESSIONAL,input.question(),input.context(),input.options(),input.answerRequirements(),1,State.OPEN,0,null,checkpoint,null,null,null);
        insert(q,sourceDigest,digest);
        repo.jdbc().update("INSERT INTO formal_question_slot(source_task_id,question_id,checkpoint_json) VALUES(?,?,?)",taskId,id,repo.encode(checkpoint));
        guard.finish(taskId,new Finish(input.lease(),operation,"SUCCEEDED",AuthorityRepository.hash(id),"question:"+id,null));
        var inputYield=yieldInput(q,input.lease());yields.prepare(taskId,inputYield);return opened(q,input.lease());
    }
    private GuardContract.Yield yieldInput(Question q,Lease lease) { return new GuardContract.Yield(lease,q.questionId(),checkpointRef(q)); }
    private FrozenRef checkpointRef(Question q) { return new FrozenRef("checkpoint",q.source().taskId(),q.checkpoint().resultRevision(),AuthorityRepository.hash(repo.encode(q.checkpoint()))); }
    private Object opened(Question q,Lease lease) { return Map.of("question",q,"yield",yieldInput(q,lease),"releaseResources",false); }
    @Transactional public Object receipt(String task,QuestionReceipt input) {
        boundary.lock();var lease=input.lease();
        if(repo.jdbc().queryForObject("SELECT COUNT(*) FROM execution_attempt WHERE task_id=? AND id=? AND worker_id=? AND lease_token=?",Long.class,task,lease.attemptId(),lease.workerId(),lease.leaseToken())!=1) throw new SecurityException("Original owned question Attempt required");
        var rows=repo.jdbc().queryForList("SELECT q.id FROM formal_question q JOIN formal_question_slot s ON s.question_id=q.id WHERE s.source_task_id=? AND q.source_key=?",String.class,task,input.sourceQuestionKey());
        if(rows.size()!=1) throw new IllegalArgumentException("Question receipt not found");var q=get(rows.getFirst());
        if(!Objects.equals(q.sourceAttemptId(),lease.attemptId())) throw new SecurityException("Question belongs to another Attempt");
        return Map.of("question",q,"yield",yieldInput(q,lease),"yieldState",repo.jdbc().queryForObject("SELECT state FROM execution_yield WHERE task_id=?",String.class,task));
    }
    @Transactional public Object acknowledge(String task,GuardContract.Yield input) { boundary.lock();var result=yields.acknowledge(task,input);var q=get(input.operationId());if(!Objects.equals(q.source().taskId(),task)) throw new IllegalArgumentException("Question yield mismatch");return result; }
    @Transactional public Question budget(BudgetQuestion input) {
        boundary.lock();String owner=owner(input.planId());human(owner,"A18_BUDGET");return insertBudget(input,owner);
    }
    /** Trusted platform request only. Never authorizes an increment or impersonates a human. */
    @Transactional public Question requestBudget(BudgetQuestion input,String expectedOwner) {
        boundary.lock();if(!owner(input.planId()).equals(expectedOwner)) throw new SecurityException("Budget request owner mismatch");
        var a=input.authorization();var rows=ledger.snapshot(input.planId()).stream().filter(r->r.get("dimension").equals(a.dimension().name())&&Objects.equals(r.get("loop_id"),a.loopId()==null?"":a.loopId())).toList();
        if(rows.size()!=1) throw new IllegalStateException("Configure the required limit first");var row=rows.getFirst();
        if(row.get("limit_value")==null||((Number)row.get("unknown_count")).longValue()!=0)throw new IllegalStateException("Configure or reconcile this dimension first");
        var policy=repo.decode((String)guard.policyRow(input.planId()).get("policy_json"),GuardContract.Policy.class);
        long needed=switch(a.dimension()){case LOOP_BACKJUMPS,TASKS->1;case TIME_MS->policy.budget().attemptTimeMs();case COST_MICROS->policy.budget().attemptCostMicros();};
        if(java.math.BigInteger.valueOf(((Number)row.get("used_value")).longValue()).add(java.math.BigInteger.valueOf(((Number)row.get("reserved_value")).longValue())).add(java.math.BigInteger.valueOf(needed)).compareTo(java.math.BigInteger.valueOf(((Number)row.get("limit_value")).longValue()))<=0)throw new IllegalStateException("No measured admission gap for this request");
        if(Math.addExact(((Number)row.get("limit_value")).longValue(),a.amount())!=a.cumulativeCeiling())throw new IllegalArgumentException("Exact cumulative request required");
        return insertBudget(input,expectedOwner);
    }
    private Question insertBudget(BudgetQuestion input,String owner) {
        var policy=guard.policyRow(input.planId());
        if(((Number)policy.get("revision")).longValue()!=input.policyRevision()) throw new IllegalStateException("Policy changed");
        var source=new Source(SourceKind.LEGACY_PLAN_TASK,input.planId(),input.policyRevision(),null,null);String key="budget:"+input.authorization().requestId();
        var previous=repo.jdbc().queryForList("SELECT id,request_digest FROM formal_question WHERE owner_id=? AND plan_id=? AND source_key=?",owner,input.planId(),key);
        String digest=AuthorityRepository.hash(repo.encode(input));if(!previous.isEmpty()) {if(!digest.equals(previous.getFirst().get("request_digest"))) throw new IllegalStateException("AUTHORIZATION_REQUEST_CONFLICT");return get(String.valueOf(previous.getFirst().get("id")));}
        return insert(new Question(QuestionContract.PROTOCOL,"Q-"+UUID.randomUUID(),owner,source,null,key,Kind.AUTHORIZATION,input.question(),"Exact requested increment: "+repo.encode(input.authorization()),List.of("APPROVE","REJECT"),"Approve only this request and scope.",1,State.OPEN,0,null,null,input.authorization(),null,null),String.valueOf(policy.get("digest")),digest);
    }
    @Transactional public Question humanReply(Reply input) { boundary.lock();var q=get(input.questionId());human(q.ownerId(),q.kind()==Kind.AUTHORIZATION?"A18_BUDGET":"A19_ANSWER");return answer(input,q.ownerId(),boundary.human(),"CONSOLE",false); }
    /** Only called from the existing action admission transaction, or an authenticated human transport. */
    public Question answer(Reply input,String owner,String actor,String channel,boolean delegated) {
        boundary.lock();var q=get(input.questionId());if(!q.ownerId().equals(owner)||q.requestVersion()!=input.requestVersion()) throw new SecurityException("Question owner or request version changed");
        String digest=AuthorityRepository.hash(repo.encode(input));var prior=repo.jdbc().queryForList("SELECT * FROM formal_question_inbox WHERE owner_id=? AND channel=? AND message_id=?",owner,channel,input.messageId());
        if(!prior.isEmpty()) {if(!digest.equals(prior.getFirst().get("request_digest"))) throw new IllegalStateException("ANSWER_MESSAGE_CONFLICT");return repo.decode(String.valueOf(prior.getFirst().get("receipt_json")),Question.class);}
        if(q.state()==State.SUPERSEDED) throw new IllegalStateException("Question superseded");
        if(q.state()==State.APPLIED&&q.answer()!=null&&q.answer().rawAnswer().equals(input.rawAnswer())){receipt(owner,channel,input,digest,q);return q;}
        if(q.kind()==Kind.PROFESSIONAL) {
            String expected=repo.jdbc().queryForObject("SELECT source_digest FROM formal_question WHERE id=?",String.class,q.questionId());
            if(!expected.equals(validate(q.source(),owner,false))) throw new IllegalStateException("Question source changed");
        }
        if(q.answer()!=null) {
            if(q.answer().rawAnswer().equals(input.rawAnswer())) {receipt(owner,channel,input,digest,q);return q;}
            if(q.state()==State.APPLIED||delegated||!q.answer().delegated()||input.overrideReason()==null||!Objects.equals(input.expectedAnswerRevision(),q.answerRevision())) throw new IllegalStateException("ANSWER_REVISION_CONFLICT");
        }
        var a=new Answer(input.rawAnswer(),actor,channel,input.messageId(),q.answerRevision()+1,delegated,input.overrideReason());
        if(repo.jdbc().update("UPDATE formal_question SET state='ANSWERED',answer_revision=?,answer_json=?,updated_ms=? WHERE id=? AND state IN ('OPEN','ANSWERED') AND answer_revision=?",a.revision(),repo.encode(a),System.currentTimeMillis(),q.questionId(),q.answerRevision())!=1) throw new IllegalStateException("ANSWER_CHANGED");
        repo.jdbc().update("INSERT INTO formal_question_answer VALUES(?,?,?)",q.questionId(),a.revision(),repo.encode(a));
        if(q.kind()==Kind.AUTHORIZATION) {
            if(!Set.of("APPROVE","REJECT").contains(a.rawAnswer())) throw new IllegalArgumentException("Exact authorization choice required");
            if(delegated) throw new SecurityException("Budget answers require A18 admission, never A19");
            if(a.rawAnswer().equals("APPROVE")) ledger.increment(q.authorization().increment(q.source().planId()),actor);
            applied(q.questionId(),a.revision(),null);
        }
        var result=get(q.questionId());receipt(owner,channel,input,digest,result);return result;
    }
    private void receipt(String owner,String channel,Reply input,String digest,Question q) { repo.jdbc().update("INSERT INTO formal_question_inbox VALUES(?,?,?,?,?,?)",owner,channel,input.messageId(),q.questionId(),digest,repo.encode(q)); }
    public void applied(String id,long revision,String successor) { if(repo.jdbc().update("UPDATE formal_question SET state='APPLIED',successor_task_id=?,blocked_reason=NULL,updated_ms=? WHERE id=? AND state='ANSWERED' AND answer_revision=?",successor,System.currentTimeMillis(),id,revision)!=1) throw new IllegalStateException("Question application changed"); }
    @Transactional public void consume(String id) {
        boundary.lock();var q=get(id);if(q.state()!=State.ANSWERED||q.kind()!=Kind.PROFESSIONAL) return;
        String expected=repo.jdbc().queryForObject("SELECT source_digest FROM formal_question WHERE id=?",String.class,id);
        if(!expected.equals(validate(q.source(),q.ownerId(),false))) {block(id,"SOURCE_CHANGED");return;}
        if(q.source().kind()==SourceKind.WORKFLOW_GRAPH_EXECUTION) {
            var application=resolver(q.source()).apply(q);if(application==null) {block(id,"WAITING_GRAPH_CONTROL_OR_PREREQUISITES");return;}
            applied(id,q.answerRevision(),application.successorTaskId());return;
        }
        String successor=continueExisting(q);
        if(successor==null) {block(id,"WAITING_CONTROL_BUDGET_OR_QUIESCENCE");return;}applied(id,q.answerRevision(),successor);
    }
    private void block(String id,String reason) {repo.jdbc().update("UPDATE formal_question SET blocked_reason=? WHERE id=? AND state='ANSWERED'",reason,id);}
    private String continueExisting(Question q) {
        var old=tasks.find(new TaskId(q.source().taskId())).orElseThrow();
        if(old.state()!=TaskState.WAITING_INPUT||!guard.canClaim(old.id().value())) return null;
        if(old.subjectType().equals("flexible-plan") && plans.get(q.source().planId()).state()!=WorkflowPlan.State.CONFIRMED) return null;
        String root=old.id().value().replaceFirst("-resume-[1-9][0-9]*$","");int n=1;
        while(tasks.find(new TaskId(root+"-resume-"+n)).isPresent()) n++;
        String next=root+"-resume-"+n;var now=Instant.now();
        var capabilities=new HashSet<>(old.requiredCapabilities());capabilities.add(q.checkpoint().storageCapability());
        var task=new WorkTask(new TaskId(next),old.type(),TaskState.READY,old.subjectType(),old.subjectId(),Set.copyOf(capabilities),old.skillRef(),
                old.instruction()+"\nFormal question: "+q.question()+"\nOriginal answer: "+q.answer().rawAnswer(),old.instructionRevision()+1,old.repositoryRef(),q.checkpoint().resultRevision(),old.priority(),0,now,now);
        var refs=List.of(checkpointRef(q),new FrozenRef("question",q.questionId(),Long.toString(q.answerRevision()),AuthorityRepository.hash(repo.encode(q.answer()))));
        String result=yields.continueTask(old.id().value(),old.version(),task,q.questionId(),refs).value();
        repo.jdbc().update("UPDATE formal_question_slot SET successor_task_id=? WHERE source_task_id=? AND successor_task_id IS NULL",result,old.id().value());return result;
    }
    /** Graph resolver uses the same continuation, within the formal-question APPLIED transaction. */
    public String continueRegisteredGraph(Question q) {
        boundary.lock();if(q.source().kind()!=SourceKind.WORKFLOW_GRAPH_EXECUTION||q.state()!=State.ANSWERED||!q.equals(get(q.questionId())))throw new IllegalStateException("Exact answered graph question required");
        validate(q.source(),q.ownerId(),false);return continueExisting(q);
    }
    @Transactional public Object checkpoints(String task,Lease lease) {
        boundary.lock();guard.verifyLease(task,lease,false,true);var refs=guard.context(task).snapshot().references();var result=new ArrayList<Map<String,Object>>();
        for(var ref:refs) if(ref.kind().equals("checkpoint")) {
            var row=repo.jdbc().queryForMap("SELECT s.checkpoint_json,y.state FROM formal_question_slot s JOIN execution_yield y ON y.task_id=s.source_task_id WHERE s.source_task_id=?",ref.id());
            var checkpoint=repo.decode(String.valueOf(row.get("checkpoint_json")),Checkpoint.class);
            if(!"ACKNOWLEDGED".equals(row.get("state"))||!ref.sha256().equals(AuthorityRepository.hash(repo.encode(checkpoint)))||!ref.revision().equals(checkpoint.resultRevision())) throw new IllegalStateException("Registered checkpoint changed");
            result.add(Map.of("reference",ref,"checkpoint",checkpoint,"checkpointJson",String.valueOf(row.get("checkpoint_json"))));
        }return List.copyOf(result);
    }
    @Transactional public Object control(String plan,long revision,String action) {
        boundary.lock();human(owner(plan),"A19_ANSWER");var result=guard.control(plan,revision,action.equals("cancel")?"revoke":action);
        if(action.equals("cancel")) {
            long now=System.currentTimeMillis();
            repo.jdbc().update("UPDATE execution_attempt SET stop_requested_at_epoch_ms=COALESCE(stop_requested_at_epoch_ms,?) WHERE state IN ('CLAIMED','RUNNING') AND task_id IN (SELECT task_id FROM execution_context WHERE plan_id=?)",now,plan);
            repo.jdbc().update("UPDATE work_task SET state='CANCELLED',version=version+1,updated_at_epoch_ms=? WHERE state IN ('READY','BLOCKED','WAITING_INPUT') AND id IN (SELECT task_id FROM execution_context WHERE plan_id=?)",now,plan);
            repo.jdbc().update("UPDATE formal_question SET state='SUPERSEDED',blocked_reason='CANCEL_REQUESTED',updated_ms=? WHERE plan_id=? AND state IN ('OPEN','ANSWERED')",now,plan);
        }resolvers.orderedStream().forEach(r->r.planControlled(plan,action));return result;
    }
    @Transactional public Object progress(String plan) {
        boundary.lock();readHuman(owner(plan));var active=repo.jdbc().queryForList("SELECT t.id,t.state,a.stop_requested_at_epoch_ms,a.stop_acknowledged_at_epoch_ms FROM work_task t JOIN execution_context c ON c.task_id=t.id LEFT JOIN execution_attempt a ON a.task_id=t.id AND a.attempt_no=t.attempt_no WHERE c.plan_id=? ORDER BY t.id",plan);
        return Map.of("policy",guard.policyRow(plan),"budgets",ledger.snapshot(plan),"tasks",active,"questions",list(plan),"stopSemantics","WAITING_STOP_CONFIRMATION until actual acknowledgment; UNKNOWN effects require human reconciliation");
    }
}
