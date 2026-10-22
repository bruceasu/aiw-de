package me.asu.aidev.execution.guard;

import java.time.Instant;
import java.util.*;
import me.asu.aidev.authority.*;
import me.asu.aidev.execution.port.TaskConcurrencyBudget;
import me.asu.aidev.work.domain.*;
import me.asu.aidev.work.port.WorkTaskRepository;
import me.asu.aidev.worker.domain.WorkerId;
import me.asu.aidev.worker.port.WorkerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static me.asu.aidev.execution.guard.GuardContract.*;

/** Quiescence is acknowledged by the trusted adapter, never inferred from a model's text. */
@Service
public class ExecutionYieldService {
    private final AuthorityRepository repo;private final AuthorityBoundary boundary;private final ExecutionGuard guard;
    private final ExecutionLedger ledger;private final WorkTaskRepository tasks;private final WorkerRepository workers;
    private final List<TaskConcurrencyBudget> concurrency;
    public ExecutionYieldService(AuthorityRepository repo,AuthorityBoundary boundary,ExecutionGuard guard,ExecutionLedger ledger,
            WorkTaskRepository tasks,WorkerRepository workers,List<TaskConcurrencyBudget> concurrency) {
        this.repo=repo;this.boundary=boundary;this.guard=guard;this.ledger=ledger;this.tasks=tasks;this.workers=workers;this.concurrency=concurrency;
    }
    @Transactional public Object prepare(String task,Yield input) {
        boundary.lock();String digest=AuthorityRepository.hash(repo.encode(input));
        var previous=repo.jdbc().queryForList("SELECT * FROM execution_yield WHERE task_id=?",task);
        if(!previous.isEmpty()) {
            if(!Objects.equals(previous.getFirst().get("request_digest"),digest)) throw new IllegalStateException("YIELD_ID_CONFLICT");return previous.getFirst();
        }
        guard.verifyLease(task,input.lease(),true,true);
        tasks.lock(new TaskId(task));
        if(!tasks.transitionState(new TaskId(task),TaskState.RUNNING,TaskState.YIELDING,Instant.now())) throw new IllegalStateException("Yield lost state race");
        repo.jdbc().update("INSERT INTO execution_yield(task_id,operation_id,attempt_id,worker_id,lease_token,checkpoint_json,request_digest,state) VALUES(?,?,?,?,?,?,?,'PREPARED')",
                task,input.operationId(),input.lease().attemptId(),input.lease().workerId(),input.lease().leaseToken(),repo.encode(input.checkpoint()),digest);
        return Map.of("state","PREPARED","releaseResources",false);
    }
    @Transactional public Object acknowledge(String task,Yield input) {
        boundary.lock();var row=repo.jdbc().queryForMap("SELECT * FROM execution_yield WHERE task_id=?",task);
        if(!Objects.equals(row.get("request_digest"),AuthorityRepository.hash(repo.encode(input)))) throw new IllegalStateException("Exact original yield required");
        if("ACKNOWLEDGED".equals(row.get("state"))) return Map.of("state","WAITING_INPUT");
        guard.verifyLease(task,input.lease(),false,false);
        if(guard.unknownTask(task)) throw new IllegalStateException("Confirm every tool/process outcome before releasing resources");
        var now=Instant.now();var t=tasks.find(new TaskId(task)).orElseThrow();
        if(t.state()!=TaskState.YIELDING) throw new IllegalStateException("Not yielding");
        ledger.settle(input.lease().attemptId(),now.toEpochMilli());
        if(repo.jdbc().update("""
                UPDATE work_task SET state='WAITING_INPUT',current_worker_id=NULL,lease_token=NULL,lease_acquired_at_epoch_ms=NULL,
                lease_expires_at_epoch_ms=NULL,lease_heartbeat_at_epoch_ms=NULL,version=version+1,updated_at_epoch_ms=?
                WHERE id=? AND state='YIELDING' AND lease_token=? AND current_worker_id=?
                """,now.toEpochMilli(),task,input.lease().leaseToken(),input.lease().workerId())!=1) throw new IllegalStateException("Yield Lease changed");
        if(repo.jdbc().update("UPDATE execution_attempt SET state='YIELDED',finished_at_epoch_ms=? WHERE id=? AND state='RUNNING' AND lease_token=?",
                now.toEpochMilli(),input.lease().attemptId(),input.lease().leaseToken())!=1) throw new IllegalStateException("Yield attempt changed");
        if(!workers.releaseCapacity(new WorkerId(input.lease().workerId()),now)) throw new IllegalStateException("Worker resource underflow");
        for(int i=concurrency.size()-1;i>=0;i--) concurrency.get(i).release(t,now);
        repo.jdbc().update("UPDATE execution_yield SET state='ACKNOWLEDGED',acknowledged_ms=? WHERE task_id=?",now.toEpochMilli(),task);
        return Map.of("state","WAITING_INPUT");
    }
    /** Actual process/tool quiescence precedes this trusted adapter acknowledgment. Never requeues. */
    @Transactional public void stopped(String task,Lease lease) {
        boundary.lock();
        if(repo.jdbc().queryForObject("SELECT COUNT(*) FROM execution_attempt a JOIN work_task t ON t.id=a.task_id WHERE a.id=? AND a.task_id=? AND a.worker_id=? AND a.lease_token=? AND a.stop_acknowledged_at_epoch_ms IS NOT NULL AND t.state='CANCELLED'",Long.class,lease.attemptId(),task,lease.workerId(),lease.leaseToken())==1) return;
        guard.verifyLease(task,lease,false,false);
        var t=tasks.find(new TaskId(task)).orElseThrow();
        if(!Set.of(TaskState.CLAIMED,TaskState.RUNNING,TaskState.YIELDING).contains(t.state())) throw new IllegalStateException("Not an active guarded task");
        var row=repo.jdbc().queryForMap("SELECT stop_requested_at_epoch_ms FROM execution_attempt WHERE id=?",lease.attemptId());
        long expiry=repo.jdbc().queryForObject("SELECT lease_expires_at_epoch_ms FROM work_task WHERE id=?",Long.class,task);
        if(row.get("stop_requested_at_epoch_ms")==null && expiry>System.currentTimeMillis()) throw new IllegalStateException("Explicit stop request or expired Lease required");
        if(guard.unknownTask(task)) throw new IllegalStateException("Reconcile outstanding operations before confirming stopped");
        var now=Instant.now();ledger.settle(lease.attemptId(),now.toEpochMilli());
        repo.jdbc().update("""
                UPDATE work_task SET state='CANCELLED',current_worker_id=NULL,lease_token=NULL,lease_acquired_at_epoch_ms=NULL,
                lease_expires_at_epoch_ms=NULL,lease_heartbeat_at_epoch_ms=NULL,version=version+1,updated_at_epoch_ms=?
                WHERE id=? AND lease_token=? AND current_worker_id=?
                """,now.toEpochMilli(),task,lease.leaseToken(),lease.workerId());
        repo.jdbc().update("UPDATE execution_attempt SET state='FAILED',finished_at_epoch_ms=?,stop_acknowledged_at_epoch_ms=? WHERE id=? AND state IN ('CLAIMED','RUNNING')",now.toEpochMilli(),now.toEpochMilli(),lease.attemptId());
        if(!workers.releaseCapacity(new WorkerId(lease.workerId()),now)) throw new IllegalStateException("Worker resource underflow");
        for(int i=concurrency.size()-1;i>=0;i--) concurrency.get(i).release(t,now);
    }
    @Transactional public Object humanStopped(String task,StopEvidence input) {
        boundary.lock();guard.owner(String.valueOf(guard.policyRow(String.valueOf(guard.contextRow(task).get("plan_id"))).get("owner_id")));
        String json=repo.encode(input);var previous=repo.jdbc().queryForList("SELECT decision_json FROM execution_stop_evidence WHERE attempt_id=?",String.class,input.attemptId());
        if(!previous.isEmpty()) { if(!previous.getFirst().equals(json)) throw new IllegalStateException("STOP_CONFIRMATION_CONFLICT");return Map.of("state","CANCELLED","replay",false); }
        var row=repo.jdbc().queryForMap("SELECT worker_id,lease_token,stop_acknowledged_at_epoch_ms FROM execution_attempt WHERE id=? AND task_id=?",input.attemptId(),task);
        if(row.get("stop_acknowledged_at_epoch_ms")!=null) return Map.of("state","CANCELLED","replay",false);
        if(guard.unknownTask(task)) throw new IllegalStateException("Reconcile each outstanding effect first");
        repo.jdbc().update("UPDATE execution_attempt SET stop_requested_at_epoch_ms=COALESCE(stop_requested_at_epoch_ms,?) WHERE id=?",System.currentTimeMillis(),input.attemptId());
        stopped(task,new Lease(String.valueOf(row.get("worker_id")),input.attemptId(),String.valueOf(row.get("lease_token"))));
        repo.jdbc().update("INSERT INTO execution_stop_evidence VALUES(?,?)",input.attemptId(),json);
        repo.audit("PLAN:"+guard.contextRow(task).get("plan_id"),null,input.attemptId(),boundary.human(),"GUARDED_PROCESS_STOP_CONFIRMED",repo.encode(input));
        return Map.of("state","CANCELLED","replay",false);
    }
    /** Called in the formal answer APPLIED transaction. Never starts work or dispatches a question. */
    @Transactional public TaskId continueTask(String source,long expectedVersion,WorkTask successor,String decisionId,List<FrozenRef> additionalRefs) {
        boundary.lock();var c=guard.context(source);var old=tasks.find(new TaskId(source)).orElseThrow();
        var row=guard.contextRow(source);
        if(row.get("successor_task_id")!=null) return new TaskId(String.valueOf(row.get("successor_task_id")));
        if(old.state()!=TaskState.WAITING_INPUT || old.version()!=expectedVersion || successor.id().value().equals(source)
                || !successor.subjectId().equals(old.subjectId()) || !successor.subjectType().equals(old.subjectType())
                || successor.type()!=old.type() || !successor.requiredCapabilities().equals(old.requiredCapabilities())
                || !Objects.equals(successor.repositoryRef(),old.repositoryRef()) || !Objects.equals(successor.baseRevision(),old.baseRevision()))
            throw new IllegalStateException("Exact waiting source and fixed successor required");
        if(!guard.canClaim(source)) throw new IllegalStateException("Continuation control or budget blocked");
        tasks.lock(old.id());tasks.insert(successor);
        var refs=new ArrayList<>(c.snapshot().references());refs.addAll(additionalRefs);
        // This is a continuation of the original grant. Replacing a policy never expands it.
        ledger.task(c.planId());String json=repo.encode(new ExecutionGuard.Snapshot(successor,c.snapshot().inputs(),List.copyOf(refs)));
        repo.jdbc().update("""
                INSERT INTO execution_context(task_id,plan_id,policy_revision,generation,decision_id,config_digest,policy_json,input_json,input_digest,task_version,resumes_task_id)
                VALUES(?,?,?,?,?,?,?,?,?,?,?)
                """,successor.id().value(),c.planId(),row.get("policy_revision"),row.get("generation"),decisionId,c.configurationDigest(),row.get("policy_json"),json,AuthorityRepository.hash(json),successor.version(),source);
        if(!tasks.transitionState(old.id(),TaskState.WAITING_INPUT,TaskState.CONTINUED,Instant.now())) throw new IllegalStateException("Continuation lost source race");
        repo.jdbc().update("UPDATE execution_context SET successor_task_id=? WHERE task_id=? AND successor_task_id IS NULL",successor.id().value(),source);
        return successor.id();
    }
}
