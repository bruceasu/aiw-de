package me.asu.aidev.execution.run;

import java.time.Instant;
import java.util.*;
import me.asu.aidev.authority.*;
import me.asu.aidev.execution.guard.*;
import me.asu.aidev.execution.port.*;
import me.asu.aidev.work.domain.*;
import me.asu.aidev.work.port.WorkTaskRepository;
import me.asu.aidev.worker.domain.WorkerId;
import me.asu.aidev.worker.port.WorkerRepository;
import org.springframework.stereotype.Service;

/** Release native resources only after exact, recorded quiescence; never replay an expired run. */
@Service
public class NativeRunLifecycle {
    private final AuthorityRepository repo;private final AuthorityBoundary boundary;private final ExecutionGuard guard;
    private final WorkTaskRepository tasks;private final WorkerRepository workers;private final List<TaskConcurrencyBudget> budgets;
    public NativeRunLifecycle(AuthorityRepository repo,AuthorityBoundary boundary,ExecutionGuard guard,WorkTaskRepository tasks,WorkerRepository workers,List<TaskConcurrencyBudget> budgets) {
        this.repo=repo;this.boundary=boundary;this.guard=guard;this.tasks=tasks;this.workers=workers;this.budgets=List.copyOf(budgets);
    }
    public void serialize(String task) {
        if(boundTask(task))boundary.lock();
    }
    public boolean boundTask(String task) {
        return !guard.managed(task)&&!repo.jdbc().queryForList("SELECT t.id FROM work_task t JOIN plan_run_owner p ON p.plan_id=t.subject_id WHERE t.id=? AND t.subject_type='flexible-plan'",String.class,task).isEmpty();
    }
    public boolean managed(String task,String attempt) {
        return !guard.managed(task)&&!repo.jdbc().queryForList("SELECT attempt_id FROM execution_run_session WHERE task_id=? AND attempt_id=?",String.class,task,attempt).isEmpty();
    }
    public boolean quiescent(String attempt) {
        if(repo.jdbc().queryForObject("SELECT COUNT(*) FROM execution_stop_evidence WHERE attempt_id=?",Long.class,attempt)>0)return true;
        var events=repo.jdbc().queryForList("SELECT event_type,payload_json FROM execution_run_event WHERE attempt_id=? AND event_type IN ('native-turn-started','execution-quiescent') ORDER BY sequence_no DESC LIMIT 1",attempt);
        if(events.size()!=1||!"execution-quiescent".equals(events.getFirst().get("event_type")))return false;
        var payload=repo.decode((String)events.getFirst().get("payload_json"),Map.class);
        return Boolean.TRUE.equals(payload.get("complete"))&&payload.get("count") instanceof Number n&&n.longValue()==0;
    }
    public void requireQuiescent(String task,GuardContract.Lease lease) {
        if(managed(task,lease.attemptId())&&!quiescent(lease.attemptId()))throw new IllegalStateException("Native resource outcome is not confirmed; retain the run for reconciliation");
    }
    public boolean cancellation(String task,String attempt) {
        if(!managed(task,attempt)||repo.jdbc().queryForObject("SELECT COUNT(*) FROM execution_run_command WHERE attempt_id=? AND action='CANCEL' AND status IN ('ACCEPTED','RECEIVED','APPLIED')",Long.class,attempt)==0)return false;
        // Whole-Plan stop/jump retains its authoritative callback, even after a run CANCEL.
        return repo.jdbc().queryForObject("SELECT COUNT(*) FROM flexible_workflow_control c JOIN work_task t ON t.id=c.task_id AND t.subject_id=c.plan_id WHERE t.id=? AND c.status='PENDING_STOP'",Long.class,task)==0;
    }
    /** Caller holds the authority/task transaction. A native question is not a completed task. */
    public void yielded(String task,GuardContract.Lease lease) {
        guard.verifyLease(task,lease,false,false);requireQuiescent(task,lease);
        var source=tasks.find(new TaskId(task)).orElseThrow();
        if(source.state()!=TaskState.YIELDING)throw new IllegalStateException("Exact yielding source required");
        if(repo.jdbc().queryForObject("SELECT stop_requested_at_epoch_ms FROM execution_attempt WHERE id=?",Long.class,lease.attemptId())!=null)throw new IllegalStateException("Stop takes priority over input waiting");
        clear(source,lease,"WAITING_INPUT","YIELDED",false);settled(lease.attemptId());
    }
    /** Run CANCEL differs from the existing Plan stop/jump flow, which keeps its own callback. */
    public void cancelled(String task,GuardContract.Lease lease) {
        guard.verifyLease(task,lease,false,false);requireQuiescent(task,lease);
        var source=tasks.find(new TaskId(task)).orElseThrow();
        if(!Set.of(TaskState.RUNNING,TaskState.YIELDING).contains(source.state())||repo.jdbc().queryForObject("SELECT stop_requested_at_epoch_ms FROM execution_attempt WHERE id=?",Long.class,lease.attemptId())==null)throw new IllegalStateException("Exact native stop request required");
        clear(source,lease,"CANCELLED","EXPIRED",true);
        repo.jdbc().update("UPDATE formal_question SET state='SUPERSEDED',blocked_reason='RUN_CANCELLED',updated_ms=? WHERE id IN (SELECT question_id FROM formal_question_slot WHERE source_task_id=?) AND state IN ('OPEN','ANSWERED')",System.currentTimeMillis(),task);
        repo.jdbc().update("UPDATE execution_run_command SET status='APPLIED',updated_ms=? WHERE attempt_id=? AND action='CANCEL'",System.currentTimeMillis(),lease.attemptId());
        tasks.recordControlEvent(UUID.randomUUID().toString(),source.id(),lease.attemptId(),"STOP_ACKNOWLEDGED","worker:"+lease.workerId(),"Native run cancellation confirmed after quiescence",false,Instant.now());
        settled(lease.attemptId());
    }
    private void clear(WorkTask source,GuardContract.Lease lease,String taskState,String attemptState,boolean stop) {
        var now=Instant.now();
        if(!tasks.lock(source.id()))throw new IllegalStateException("Source task disappeared");
        if(repo.jdbc().update("""
                UPDATE work_task SET state=?,current_worker_id=NULL,lease_token=NULL,lease_acquired_at_epoch_ms=NULL,
                  lease_expires_at_epoch_ms=NULL,lease_heartbeat_at_epoch_ms=NULL,version=version+1,updated_at_epoch_ms=?
                 WHERE id=? AND state=? AND current_worker_id=? AND lease_token=?
                """,taskState,now.toEpochMilli(),source.id().value(),source.state().name(),lease.workerId(),lease.leaseToken())!=1)throw new IllegalStateException("Native source lease changed");
        if(repo.jdbc().update("UPDATE execution_attempt SET state=?,finished_at_epoch_ms=?,stop_acknowledged_at_epoch_ms=CASE WHEN ? THEN ? ELSE stop_acknowledged_at_epoch_ms END WHERE id=? AND task_id=? AND state='RUNNING' AND worker_id=? AND lease_token=?",
                attemptState,now.toEpochMilli(),stop,now.toEpochMilli(),lease.attemptId(),source.id().value(),lease.workerId(),lease.leaseToken())!=1)throw new IllegalStateException("Native attempt changed");
        if(!workers.releaseCapacity(new WorkerId(lease.workerId()),now))throw new IllegalStateException("Worker capacity underflow");
        for(int index=budgets.size()-1;index>=0;index--)budgets.get(index).release(source,now);
    }
    public void settled(String attempt) {
        long now=System.currentTimeMillis();
        repo.jdbc().update("UPDATE execution_run_session SET phase='SETTLED',revision=revision+1,last_contact_ms=? WHERE attempt_id=? AND phase<>'SETTLED'",now,attempt);
        repo.jdbc().update("UPDATE execution_run_command SET status='REJECTED',updated_ms=? WHERE attempt_id=? AND action<>'CANCEL' AND status IN ('ACCEPTED','RECEIVED')",now,attempt);
    }
}
