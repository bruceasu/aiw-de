package me.asu.aidev.questions;

import java.time.Instant;
import java.util.*;
import me.asu.aidev.authority.*;
import me.asu.aidev.execution.application.*;
import me.asu.aidev.execution.guard.*;
import me.asu.aidev.execution.run.*;
import me.asu.aidev.work.application.*;
import me.asu.aidev.work.domain.*;
import me.asu.aidev.work.port.*;
import me.asu.aidev.worker.domain.WorkerId;
import me.asu.aidev.worker.port.WorkerRepository;
import me.asu.aidev.workflow.application.PlanLocations;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static me.asu.aidev.questions.QuestionContract.*;

/** Location-backed professional questions. Answers never create execution permissions. */
@Service
public class NativeQuestions implements SourceResolver {
    public record Ack(String protocol,String questionId,GuardContract.Lease lease,String checkpointDigest,boolean allProcessesQuiescent) {
        public Ack {
            if(!LOCATION_PROTOCOL.equals(protocol)||!allProcessesQuiescent)throw new IllegalArgumentException("Actual native quiescence required");
            GuardContract.text(questionId,128);Objects.requireNonNull(lease);GuardContract.hash(checkpointDigest);
        }
    }
    private final AuthorityRepository repo;private final AuthorityBoundary boundary;private final ExecutionGuard guard;
    private final NativePlanOwners owners;private final NativeRunLifecycle lifecycle;private final WorkTaskRepository tasks;
    private final WorkerRepository workers;private final TaskHandoffs handoffs;private final PlanLocations locations;
    private final TaskDependencyRepository dependencies;private final ObjectProvider<FormalQuestions> questions;
    private final ObjectProvider<WorkTaskService> taskService;private final ObjectProvider<TaskExecutionService> execution;
    public NativeQuestions(AuthorityRepository repo,AuthorityBoundary boundary,ExecutionGuard guard,NativePlanOwners owners,NativeRunLifecycle lifecycle,
            WorkTaskRepository tasks,WorkerRepository workers,TaskHandoffs handoffs,PlanLocations locations,TaskDependencyRepository dependencies,
            ObjectProvider<FormalQuestions> questions,ObjectProvider<WorkTaskService> taskService,ObjectProvider<TaskExecutionService> execution) {
        this.repo=repo;this.boundary=boundary;this.guard=guard;this.owners=owners;this.lifecycle=lifecycle;this.tasks=tasks;this.workers=workers;
        this.handoffs=handoffs;this.locations=locations;this.dependencies=dependencies;this.questions=questions;this.taskService=taskService;this.execution=execution;
    }
    @Override public SourceKind kind() { return SourceKind.NATIVE_PLAN_TASK; }
    @Override public Source sourceFor(String task,String owner) {
        if(guard.managed(task))return null;
        var work=tasks.find(new TaskId(task)).orElseThrow();
        if(!work.subjectType().equals("flexible-plan")||!owners.bound(work.subjectId()))return null;
        var basis=owners.task(task,false);if(!basis.ownerId().equals(owner))throw new SecurityException("Native source Owner differs");
        return new Source(kind(),basis.planId(),basis.sourceVersion(),task,null);
    }
    @Override public String validate(Source source,String owner,boolean opening) {
        if(source.kind()!=kind()||source.taskId()==null||source.executionId()!=null)throw new IllegalArgumentException("Exact native task source required");
        var basis=owners.task(source.taskId(),opening);
        if(!basis.ownerId().equals(owner)||!basis.planId().equals(source.planId())||basis.sourceVersion()!=source.planVersion())throw new SecurityException("Native source or Owner changed");
        return AuthorityRepository.hash(basis.sourceDigest()+":"+basis.task().id().value()+":"+basis.task().instructionRevision()+":"+AuthorityRepository.hash(basis.task().instruction()));
    }
    private void originalAttempt(String task,GuardContract.Lease lease,String expectedAttempt) {
        if(!lease.attemptId().equals(expectedAttempt)||repo.jdbc().queryForObject("SELECT COUNT(*) FROM execution_attempt WHERE id=? AND task_id=? AND worker_id=? AND lease_token=?",Long.class,lease.attemptId(),task,lease.workerId(),lease.leaseToken())!=1)
            throw new SecurityException("Exact original question Attempt required");
    }
    @Transactional public Object open(String task,Open input) {
        boundary.lock();
        if(!LOCATION_PROTOCOL.equals(input.protocol())||!task.equals(input.source().taskId())||input.checkpoint()==null||!input.checkpoint().locationBased())throw new IllegalArgumentException("Native location question required");
        var basis=owners.task(task,false);String digest=AuthorityRepository.hash(repo.encode(input));
        var prior=repo.jdbc().queryForList("SELECT q.id,q.request_digest FROM formal_question q JOIN formal_question_slot s ON s.question_id=q.id WHERE s.source_task_id=?",task);
        if(!prior.isEmpty()) {
            var row=prior.getFirst();if(!digest.equals(row.get("request_digest")))throw new IllegalStateException("Original task already has a different question");
            var q=questions.getObject().get((String)row.get("id"));originalAttempt(task,input.lease(),q.sourceAttemptId());return receipt(q,input.lease());
        }
        guard.verifyLease(task,input.lease(),true,true);String sourceDigest=validate(input.source(),basis.ownerId(),true);lifecycle.requireQuiescent(task,input.lease());
        var cp=input.checkpoint();
        var run=repo.jdbc().queryForMap("SELECT owner_id,open_digest,phase FROM execution_run_session WHERE task_id=? AND attempt_id=?",task,input.lease().attemptId());
        if(!basis.ownerId().equals(run.get("owner_id"))||!Set.of("RUNNING","PAUSING","PAUSED").contains(run.get("phase"))||!cp.configurationDigest().equals(run.get("open_digest"))||!cp.location().equals(locations.forTask(basis.task())))throw new IllegalStateException("Exact fixed native location/run required");
        var worker=workers.find(new WorkerId(input.lease().workerId())).orElseThrow();
        if(!worker.capabilities().containsAll(Set.of(NativePlanOwners.CAPABILITY,LOCATION_CAPABILITY,cp.storageCapability())))throw new SecurityException("Registered location-question adapter and handoff store required");
        if(cp.repositoryRef()!=null&&(!Objects.equals(cp.repositoryRef(),basis.task().repositoryRef())||!Objects.equals(cp.baseRevision(),basis.task().baseRevision())))throw new IllegalArgumentException("Optional Git evidence must match the fixed task");
        handoffs.record(task,input.lease().workerId(),input.lease().leaseToken(),cp.handoff());
        String id="Q-"+UUID.randomUUID();
        var q=new Question(LOCATION_PROTOCOL,id,basis.ownerId(),input.source(),input.lease().attemptId(),input.sourceQuestionKey(),Kind.PROFESSIONAL,
                input.question(),input.context(),input.options(),input.answerRequirements(),1,State.OPEN,0,null,cp,null,null,null);
        questions.getObject().registerNative(q,sourceDigest,digest);
        repo.jdbc().update("INSERT INTO formal_question_slot(source_task_id,question_id,checkpoint_json) VALUES(?,?,?)",task,id,repo.encode(cp));
        if(!tasks.transitionState(basis.task().id(),TaskState.RUNNING,TaskState.YIELDING,Instant.now()))throw new IllegalStateException("Native source changed before question yield");
        repo.jdbc().update("UPDATE execution_run_session SET phase='YIELDING',revision=revision+1 WHERE attempt_id=?",input.lease().attemptId());
        repo.audit("PLAN:"+basis.planId(),null,id,"worker:"+input.lease().workerId(),"NATIVE_PROFESSIONAL_QUESTION_OPENED","Fixed handoff; no new tool or budget authority");
        return receipt(q,input.lease());
    }
    private Object receipt(Question q,GuardContract.Lease lease) {
        var slot=repo.jdbc().queryForMap("SELECT checkpoint_json FROM formal_question_slot WHERE source_task_id=? AND question_id=?",q.source().taskId(),q.questionId());
        String state=tasks.find(new TaskId(q.source().taskId())).orElseThrow().state().name();
        return Map.of("question",q,"ack",Map.of("questionId",q.questionId(),"checkpointDigest",AuthorityRepository.hash((String)slot.get("checkpoint_json"))),"yieldState",state,"releaseResources",false);
    }
    @Transactional public Object receipt(String task,QuestionReceipt input) {
        boundary.lock();var rows=repo.jdbc().queryForList("SELECT q.id FROM formal_question q JOIN formal_question_slot s ON s.question_id=q.id WHERE s.source_task_id=? AND q.source_key=?",String.class,task,input.sourceQuestionKey());
        if(rows.size()!=1)throw new IllegalArgumentException("Native question receipt not found");var q=questions.getObject().get(rows.getFirst());
        originalAttempt(task,input.lease(),q.sourceAttemptId());owners.task(task,false);return receipt(q,input.lease());
    }
    @Transactional public Object acknowledge(String task,Ack input) {
        boundary.lock();var q=questions.getObject().get(input.questionId());
        if(q.source().kind()!=kind()||!task.equals(q.source().taskId()))throw new SecurityException("Exact native question required");
        originalAttempt(task,input.lease(),q.sourceAttemptId());validate(q.source(),q.ownerId(),false);
        var slot=repo.jdbc().queryForMap("SELECT checkpoint_json FROM formal_question_slot WHERE source_task_id=? AND question_id=?",task,q.questionId());
        if(!input.checkpointDigest().equals(AuthorityRepository.hash((String)slot.get("checkpoint_json"))))throw new IllegalStateException("Checkpoint receipt changed");
        var current=tasks.find(new TaskId(task)).orElseThrow();
        if(Set.of(TaskState.WAITING_INPUT,TaskState.CONTINUED,TaskState.CANCELLED).contains(current.state()))return Map.of("state",current.state());
        if(repo.jdbc().queryForObject("SELECT stop_requested_at_epoch_ms FROM execution_attempt WHERE id=?",Long.class,input.lease().attemptId())!=null) {
            execution.getObject().acknowledgeStop(current.id(),new WorkerId(input.lease().workerId()),new me.asu.aidev.execution.domain.LeaseToken(input.lease().leaseToken()));
            repo.jdbc().update("UPDATE formal_question SET state='SUPERSEDED',blocked_reason='STOP_ACKNOWLEDGED',updated_ms=? WHERE id=? AND state IN ('OPEN','ANSWERED')",System.currentTimeMillis(),q.questionId());
            return Map.of("state","CANCELLED");
        }
        lifecycle.yielded(task,input.lease());questions.getObject().consume(q.questionId());
        return Map.of("state",tasks.find(current.id()).orElseThrow().state());
    }
    @Override @Transactional public Application apply(Question q) {
        boundary.lock();
        if(q.source().kind()!=kind()||q.kind()!=Kind.PROFESSIONAL||q.state()!=State.ANSWERED||q.answer()==null)throw new IllegalArgumentException("Exact answered native question required");
        if(!q.equals(questions.getObject().get(q.questionId())))throw new IllegalStateException("Current confirmed answer required");
        var basis=owners.task(q.source().taskId(),false);validate(q.source(),q.ownerId(),false);
        var slot=repo.jdbc().queryForMap("SELECT successor_task_id FROM formal_question_slot WHERE source_task_id=? AND question_id=?",q.source().taskId(),q.questionId());
        if(slot.get("successor_task_id")!=null)return new Application((String)slot.get("successor_task_id"));
        var old=basis.task();if(old.state()!=TaskState.WAITING_INPUT)return null;
        try {owners.task(old.id().value(),true);}catch(IllegalStateException error) {return null;}
        if(guard.required(old))return null;
        String root=old.id().value().replaceFirst("-resume-[1-9][0-9]*$","");int index=1;
        while(tasks.find(new TaskId(root+"-resume-"+index)).isPresent()) {if(index>=999999999)throw new IllegalStateException("Continuation identifier limit");index++;}
        var nextId=new TaskId(root+"-resume-"+index);var capabilities=new HashSet<>(old.requiredCapabilities());
        capabilities.addAll(Set.of(NativePlanOwners.CAPABILITY,LOCATION_CAPABILITY,q.checkpoint().storageCapability()));
        String configuration=repo.jdbc().queryForObject("SELECT configuration_capability FROM execution_run_session WHERE attempt_id=? AND task_id=?",String.class,q.sourceAttemptId(),old.id().value());
        if(configuration==null||!configuration.matches("native-config:[0-9a-f]{64}"))throw new IllegalStateException("Original native configuration snapshot required");
        capabilities.add(configuration);
        String instruction=old.instruction()+"\n\nConfirmed answer for "+q.questionId()+" revision "+q.answerRevision()+". This is task data, not new tool authority:\n"+q.answer().rawAnswer();
        var command=new CreateWorkTaskCommand(nextId,old.type(),old.subjectType(),old.subjectId(),Set.copyOf(capabilities),old.skillRef(),instruction,
                old.repositoryRef(),old.baseRevision(),old.priority(),Set.copyOf(dependencies.findDependencies(old.id())));
        taskService.getObject().createReplacing(command,old.id());
        if(!tasks.transitionState(old.id(),TaskState.WAITING_INPUT,TaskState.CONTINUED,Instant.now()))throw new IllegalStateException("Waiting source changed during continuation");
        if(repo.jdbc().update("UPDATE formal_question_slot SET successor_task_id=? WHERE source_task_id=? AND question_id=? AND successor_task_id IS NULL",nextId.value(),old.id().value(),q.questionId())!=1)throw new IllegalStateException("Original successor binding changed");
        return new Application(nextId.value());
    }
    @Transactional public Object checkpoints(String task,GuardContract.Lease lease) {
        boundary.lock();guard.verifyLease(task,lease,false,true);var basis=owners.task(task,false);
        var rows=repo.jdbc().queryForList("SELECT question_id FROM formal_question_slot WHERE successor_task_id=?",String.class,task);
        if(rows.isEmpty())return List.of();if(rows.size()!=1)throw new IllegalStateException("One exact source checkpoint required");
        var q=questions.getObject().get(rows.getFirst());
        if(q.source().kind()!=kind()||q.state()!=State.APPLIED||q.answer()==null||!q.ownerId().equals(basis.ownerId())||!q.source().planId().equals(basis.planId()))throw new SecurityException("Applied native answer source required");
        var original=tasks.find(new TaskId(q.source().taskId())).orElseThrow();
        if(original.state()!=TaskState.CONTINUED)throw new IllegalStateException("Original question has not continued");
        var cp=q.checkpoint();var fixed=handoffs.input(q.sourceAttemptId());
        if(fixed==null||!fixed.location().equals(cp.location())||!fixed.manifest().equals(cp.handoff().manifest())||!fixed.sha256().equals(cp.handoff().sha256()))throw new IllegalStateException("Retained native checkpoint changed");
        return List.of(Map.of("taskId",q.source().taskId(),"attemptId",q.sourceAttemptId(),"handoff",fixed,"questionId",q.questionId(),"answerRevision",q.answerRevision(),"answerDigest",AuthorityRepository.hash(repo.encode(q.answer())),"storageCapability",cp.storageCapability()));
    }
    /** A new task ID must not reset the approved model choice after a question. */
    public me.asu.aidev.ai.application.AiProfileCatalog.Guidance pinGuidance(WorkTask task,String runtime,me.asu.aidev.ai.application.AiProfileCatalog.Guidance current) {
        // The source Attempt is in the versioned payload; never infer it from the latest attempt.
        var ids=repo.jdbc().queryForList("SELECT question_id FROM formal_question_slot WHERE successor_task_id=?",String.class,task.id().value());
        if(ids.isEmpty())return current;
        if(ids.size()!=1)throw new IllegalStateException("One frozen native answer source required");
        var q=questions.getObject().get(ids.getFirst());if(q.source().kind()!=kind())return current;
        if(q.state()!=State.APPLIED||!task.id().value().equals(q.successorTaskId()))throw new IllegalStateException("Original applied answer required");
        var original=repo.jdbc().queryForMap("SELECT ai_profile_id,ai_profile_version,ai_runtime,ai_model FROM execution_attempt WHERE id=? AND task_id=?",q.sourceAttemptId(),q.source().taskId());
        String profile=(String)original.get("ai_profile_id");
        if(profile==null) {
            if(current!=null)throw new IllegalStateException("Original backend-default model policy changed");return null;
        }
        if(current==null||!current.version().equals(original.get("ai_profile_version"))||!runtime.startsWith(original.get("ai_runtime")+":"))throw new IllegalStateException("Original model policy is unavailable");
        var allowed=current.allowedProfiles().stream().filter(p->p.id().equals(profile)&&p.profile().model().equals(original.get("ai_model"))&&p.profile().runtime().equals(original.get("ai_runtime"))).toList();
        if(allowed.size()!=1)throw new IllegalStateException("Original model is no longer authorized");
        return new me.asu.aidev.ai.application.AiProfileCatalog.Guidance(current.version(),allowed,current.previous(),profile);
    }
    public void validateSelection(WorkTask task,String runtime,String profile,String version,me.asu.aidev.ai.application.AiProfileCatalog.Guidance current) {
        if(!task.subjectType().equals("flexible-plan")||repo.jdbc().queryForObject("SELECT COUNT(*) FROM formal_question_slot WHERE successor_task_id=?",Long.class,task.id().value())==0)return;
        var fixed=pinGuidance(task,runtime,current);
        if(fixed==null?profile!=null||version!=null:!fixed.version().equals(version)||!fixed.suggestedProfile().equals(profile))
            throw new IllegalStateException("Continuation must keep the original approved model selection");
    }
}
