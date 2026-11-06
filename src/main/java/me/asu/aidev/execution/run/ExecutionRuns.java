package me.asu.aidev.execution.run;

import java.util.*;
import me.asu.aidev.authority.*;
import me.asu.aidev.execution.application.TaskExecutionService;
import me.asu.aidev.execution.config.*;
import me.asu.aidev.execution.guard.*;
import me.asu.aidev.work.domain.TaskId;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static me.asu.aidev.execution.run.RunContract.*;
import static me.asu.aidev.execution.guard.GuardContract.text;

/** Durable metadata and control receipts, serialized with existing task authority. */
@Service
public class ExecutionRuns {
    private final AuthorityRepository repo;
    private final AuthorityBoundary boundary;
    private final ExecutionGuard guard;
    private final ExecutionWorkbench workbench;
    private final ObjectProvider<TaskExecutionService> tasks;
    public ExecutionRuns(AuthorityRepository repo,AuthorityBoundary boundary,ExecutionGuard guard,ExecutionWorkbench workbench,ObjectProvider<TaskExecutionService> tasks) {
        this.repo=repo;this.boundary=boundary;this.guard=guard;this.workbench=workbench;this.tasks=tasks;
    }
    private long number(Map<String,Object> row,String key) { return ((Number)row.get(key)).longValue(); }
    private Map<String,Object> session(String task,String attempt) {
        id(task);id(attempt);
        var rows=repo.jdbc().queryForList("SELECT * FROM execution_run_session WHERE task_id=? AND attempt_id=?",task,attempt);
        if(rows.size()!=1)throw new IllegalStateException("Run session not opened");return rows.getFirst();
    }
    private String owner() {
        if(boundary.human()==null||boundary.delegated())throw new SecurityException("Authenticated human required");
        var rows=repo.jdbc().queryForList("SELECT id FROM authority_owner WHERE actor=?",String.class,boundary.human());
        if(rows.size()!=1)throw new SecurityException("Bind one owner first");return rows.getFirst();
    }
    private void owned(String owner,Map<String,Object> run) {
        if(!owner.equals(run.get("owner_id"))||!owner.equals(guard.context((String)run.get("task_id")).ownerId()))
            throw new SecurityException("Exact owned guarded task required");
    }
    private void machine(String task,GuardContract.Lease lease,boolean running) {
        Objects.requireNonNull(lease);guard.verifyLease(task,lease,running,true);
        if(!guard.managed(task))throw new IllegalStateException("Run control requires an existing guarded execution policy");
    }
    private Map<String,Object> view(Map<String,Object> run) {
        var attempt=repo.jdbc().queryForMap("SELECT state,stop_requested_at_epoch_ms,stop_acknowledged_at_epoch_ms FROM execution_attempt WHERE id=?",run.get("attempt_id"));
        var result=new LinkedHashMap<String,Object>();result.put("protocol",PROTOCOL);
        result.put("taskId",run.get("task_id"));result.put("attemptId",run.get("attempt_id"));result.put("instructionRevision",run.get("instruction_revision"));
        result.put("revision",run.get("revision"));result.put("acknowledgedSequence",run.get("ack_sequence"));result.put("lastContact",run.get("last_contact_ms"));
        String phase=(String)run.get("phase");
        if(!"RUNNING".equals(attempt.get("state")))phase="SETTLED";
        else if(attempt.get("stop_requested_at_epoch_ms")!=null)phase="STOPPING";
        result.put("phase",phase);result.put("attemptState",attempt.get("state"));
        result.put("capabilities",repo.decode((String)run.get("capabilities_json"),Map.class));
        result.put("stale",System.currentTimeMillis()-number(run,"last_contact_ms")>5000);
        result.put("eventHistoryAvailable",number(run,"ack_sequence")==0||repo.jdbc().queryForObject("SELECT COUNT(*) FROM execution_run_event WHERE attempt_id=?",Long.class,run.get("attempt_id"))>0);
        var commands=repo.jdbc().queryForList("SELECT request_id,sequence_no,action,status,created_ms,updated_ms FROM execution_run_command WHERE attempt_id=? ORDER BY sequence_no",run.get("attempt_id"));
        for(var command:commands)if("CANCEL".equals(command.get("action"))&&attempt.get("stop_acknowledged_at_epoch_ms")!=null)command.put("status","APPLIED");
        result.put("commands",commands);
        return result;
    }
    @Transactional public Object open(String task,Open input) {
        boundary.lock();machine(task,input.lease(),true);
        if(!PROTOCOL.equals(input.protocol()))throw new IllegalArgumentException("Unsupported loop protocol");capabilities(input.capabilities());
        var fixed=guard.context(task);
        if(input.instructionRevision()!=fixed.snapshot().task().instructionRevision())throw new IllegalStateException("Frozen task revision changed");
        String digest=AuthorityRepository.hash(repo.encode(input)),attempt=input.lease().attemptId();
        var prior=repo.jdbc().queryForList("SELECT * FROM execution_run_session WHERE attempt_id=?",attempt);
        if(!prior.isEmpty()) {
            if(!digest.equals(prior.getFirst().get("open_digest")))throw new IllegalStateException("Run capability declaration changed");
            return view(prior.getFirst());
        }
        repo.jdbc().update("INSERT INTO execution_run_session VALUES(?,?,?,?,?,?,'RUNNING',1,0,0,?)",attempt,task,fixed.ownerId(),input.instructionRevision(),repo.encode(input.capabilities()),digest,System.currentTimeMillis());
        return view(session(task,attempt));
    }
    @Transactional public Object events(String task,Events input) {
        boundary.lock();machine(task,input.lease(),false);var run=session(task,input.lease().attemptId());
        if(Set.of("BROKEN","FINALIZING").contains(run.get("phase")))throw new IllegalStateException("Run events are sealed");
        if(input.events()==null||input.events().size()>64)throw new IllegalArgumentException("Bounded event batch required");
        long ack=number(run,"ack_sequence"),bytes=number(run,"event_bytes");String attempt=input.lease().attemptId();
        long previousSequence=0;
        for(var event:input.events()) {
            if(event.sequence()<=previousSequence)throw new IllegalArgumentException("Events must be strictly ordered");
            previousSequence=event.sequence();
        }
        var allowed=Set.of("context-projected","context-component","guard-intent","guard-start","guard-finished","host-batch-validated","validation-failed","validation-passed","execution-quiescent","delivery-pending");
        for(var event:input.events()) {
            if(event.sequence()<1||event.occurredAt()<1||!allowed.contains(event.eventType()))throw new IllegalArgumentException("Invalid run event");
            metadata(event.payload());String json=repo.encode(event.payload());
            if(json.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>8192||!AuthorityRepository.hash(json).equals(event.payloadDigest()))throw new IllegalArgumentException("Event digest or size differs");
            String identity=AuthorityRepository.hash(repo.encode(event));
            if(event.sequence()<=ack) {
                String previous=repo.jdbc().queryForObject("SELECT digest FROM execution_run_event WHERE attempt_id=? AND sequence_no=?",String.class,attempt,event.sequence());
                if(!identity.equals(previous)) {
                    repo.jdbc().update("UPDATE execution_run_session SET phase='BROKEN',revision=revision+1 WHERE attempt_id=?",attempt);
                    return view(session(task,attempt));
                }
                continue;
            }
            if(event.sequence()!=ack+1)break;
            bytes+=json.getBytes(java.nio.charset.StandardCharsets.UTF_8).length+256;
            if(bytes>4*1024*1024)throw new IllegalStateException("Run metadata capacity exceeded");
            repo.jdbc().update("INSERT INTO execution_run_event VALUES(?,?,?,?,?,?)",attempt,event.sequence(),event.eventType(),event.occurredAt(),json,identity);ack++;
        }
        repo.jdbc().update("UPDATE execution_run_session SET ack_sequence=?,event_bytes=?,last_contact_ms=? WHERE attempt_id=?",ack,bytes,System.currentTimeMillis(),attempt);
        return view(session(task,attempt));
    }
    @Transactional public Object poll(String task,Poll input) {
        boundary.lock();machine(task,input.lease(),false);String attempt=input.lease().attemptId();var run=session(task,attempt);
        if(input.receipts()==null||input.receipts().size()>128)throw new IllegalArgumentException("Bounded command receipts required");
        for(var receipt:input.receipts()) {
            var command=repo.jdbc().queryForMap("SELECT * FROM execution_run_command WHERE attempt_id=? AND sequence_no=?",attempt,receipt.sequence());
            if("STOPPING".equals(run.get("phase"))&&!"CANCEL".equals(command.get("action")))continue;
            if(!Set.of("RECEIVED","APPLIED","UNKNOWN").contains(receipt.status()))throw new IllegalArgumentException("Unsupported receipt");
            String previous=(String)command.get("status");if(previous.equals(receipt.status()))continue;
            if(Set.of("APPLIED","UNKNOWN").contains(previous)||"ACCEPTED".equals(previous)&&!"RECEIVED".equals(receipt.status()))throw new IllegalStateException("Command receipt transition differs");
            if("APPLIED".equals(receipt.status())) {
                if("CANCEL".equals(command.get("action")))throw new IllegalStateException("Cancellation requires authoritative stop acknowledgement");
                if(repo.jdbc().queryForObject("SELECT COUNT(*) FROM execution_operation WHERE attempt_id=? AND state IN ('STARTED','UNKNOWN')",Long.class,attempt)>0)
                    throw new IllegalStateException("Active or unknown operations prevent pause acknowledgement");
                repo.jdbc().update("UPDATE execution_run_session SET phase=?,revision=revision+1 WHERE attempt_id=?","PAUSE".equals(command.get("action"))?"PAUSED":"RUNNING",attempt);
            }
            repo.jdbc().update("UPDATE execution_run_command SET status=?,updated_ms=? WHERE attempt_id=? AND sequence_no=?",receipt.status(),System.currentTimeMillis(),attempt,receipt.sequence());
        }
        repo.jdbc().update("UPDATE execution_run_session SET last_contact_ms=? WHERE attempt_id=?",System.currentTimeMillis(),attempt);
        return view(session(task,attempt));
    }
    @Transactional public Object command(Command input) {
        boundary.lock();String owner=owner();text(input.requestId(),80);String digest=AuthorityRepository.hash(repo.encode(input));
        var old=repo.jdbc().queryForList("SELECT * FROM execution_run_command WHERE owner_id=? AND request_id=?",owner,input.requestId());
        if(!old.isEmpty()) {
            if(!digest.equals(old.getFirst().get("command_digest")))throw new IllegalStateException("Request identifier has different content");
            return view(session(input.taskId(),input.attemptId()));
        }
        var run=session(input.taskId(),input.attemptId());owned(owner,run);
        if(number(run,"instruction_revision")!=input.instructionRevision()||number(run,"revision")!=input.expectedRunRevision())throw new IllegalStateException("Run revision changed");
        if(!Set.of("PAUSE","RESUME","CANCEL").contains(input.action()))throw new IllegalArgumentException("Unknown run command");
        var actual=repo.jdbc().queryForMap("SELECT a.state,t.state AS task_state,a.lease_token,t.lease_token AS current_lease,t.lease_expires_at_epoch_ms,a.stop_requested_at_epoch_ms,t.instruction_revision,a.instruction_revision AS attempt_revision FROM execution_attempt a JOIN work_task t ON t.id=a.task_id WHERE a.id=?",input.attemptId());
        if(number(actual,"instruction_revision")!=input.instructionRevision()||number(actual,"attempt_revision")!=input.instructionRevision())throw new IllegalStateException("Task instruction revision changed");
        if(!"RUNNING".equals(actual.get("state"))||!"RUNNING".equals(actual.get("task_state"))||!Objects.equals(actual.get("lease_token"),actual.get("current_lease"))||number(actual,"lease_expires_at_epoch_ms")<=System.currentTimeMillis())throw new IllegalStateException("Original Attempt is no longer running");
        if(actual.get("stop_requested_at_epoch_ms")!=null||"BROKEN".equals(run.get("phase"))||"FINALIZING".equals(run.get("phase"))&&!"CANCEL".equals(input.action()))throw new IllegalStateException("Run is stopping or sealed");
        if(repo.jdbc().queryForObject("SELECT COUNT(*) FROM execution_run_command WHERE attempt_id=? AND status IN ('ACCEPTED','RECEIVED')",Long.class,input.attemptId())>0&&!"CANCEL".equals(input.action()))throw new IllegalStateException("Previous control still requires acknowledgement");
        if("RESUME".equals(input.action())&&!"PAUSED".equals(run.get("phase"))||"PAUSE".equals(input.action())&&!"RUNNING".equals(run.get("phase")))throw new IllegalStateException("Control does not match run phase");
        long sequence=repo.jdbc().queryForObject("SELECT COUNT(*) FROM execution_run_command WHERE attempt_id=?",Long.class,input.attemptId())+1,now=System.currentTimeMillis();
        if(sequence>128)throw new IllegalStateException("Run control record is full");
        if("CANCEL".equals(input.action())) {
            tasks.getObject().requestStop(new TaskId(input.taskId()),boundary.human(),"Cancel execution run "+input.requestId());
            repo.jdbc().update("UPDATE execution_run_command SET status='REJECTED',updated_ms=? WHERE attempt_id=? AND status IN ('ACCEPTED','RECEIVED')",now,input.attemptId());
        }
        repo.jdbc().update("INSERT INTO execution_run_command VALUES(?,?,?,?,?,?,?,'ACCEPTED',?,?,?)",owner,input.requestId(),input.taskId(),input.attemptId(),sequence,input.action(),digest,boundary.human(),now,now);
        repo.jdbc().update("UPDATE execution_run_session SET phase=?,revision=revision+1 WHERE attempt_id=?",switch(input.action()) {case "PAUSE"->"PAUSING";case "CANCEL"->"STOPPING";default->"PAUSED";},input.attemptId());
        return view(session(input.taskId(),input.attemptId()));
    }
    @Transactional public Object seal(String task,Seal input) {
        boundary.lock();machine(task,input.lease(),true);var run=session(task,input.lease().attemptId());
        if("FINALIZING".equals(run.get("phase")))return Map.of("run",view(run));
        if(number(run,"revision")!=input.expectedRunRevision()||number(run,"ack_sequence")!=input.acknowledgedSequence()||!"RUNNING".equals(run.get("phase")))return Map.of("run",view(run));
        Object batch=null;
        if(repo.jdbc().queryForObject("SELECT COUNT(*) FROM execution_workbench_session WHERE attempt_id=?",Long.class,input.lease().attemptId())>0) {
            batch=workbench.machine(task,new WorkbenchContract.MachineCommand(input.lease(),"DRAIN",input.batchId(),null,null,null));
            if(!"FINALIZING".equals(((WorkbenchContract.Batch)batch).phase()))return Map.of("run",view(run),"batch",batch);
        }
        if(repo.jdbc().queryForObject("SELECT COUNT(*) FROM execution_operation WHERE attempt_id=? AND state IN ('STARTED','UNKNOWN')",Long.class,input.lease().attemptId())>0)throw new IllegalStateException("Active or unknown operations prevent finalization");
        repo.jdbc().update("UPDATE execution_run_session SET phase='FINALIZING',revision=revision+1 WHERE attempt_id=?",input.lease().attemptId());
        return batch==null?Map.of("run",view(session(task,input.lease().attemptId()))):Map.of("run",view(session(task,input.lease().attemptId())),"batch",batch);
    }
    /** Called under the task transaction and authority lock before any completion writes. */
    public void completing(String task,GuardContract.Lease lease) {
        var rows=repo.jdbc().queryForList("SELECT phase FROM execution_run_session WHERE task_id=? AND attempt_id=?",task,lease.attemptId());
        if(!rows.isEmpty()&&!"FINALIZING".equals(rows.getFirst().get("phase")))throw new IllegalStateException("Execution run has not sealed");
        if(!rows.isEmpty())guard.verifyLease(task,lease,true,true);
    }
    @Transactional public Object read(Query input) {
        String owner=owner();
        if(input.attemptId()==null) return Map.of("protocol",PROTOCOL,"runs",repo.jdbc().queryForList("SELECT task_id,attempt_id,instruction_revision,phase,last_contact_ms FROM execution_run_session WHERE owner_id=? ORDER BY last_contact_ms DESC LIMIT 100",owner));
        if(input.afterSequence()<0)throw new IllegalArgumentException("Invalid cursor");var run=session(input.taskId(),input.attemptId());owned(owner,run);
        var result=new LinkedHashMap<>(view(run));result.put("events",repo.jdbc().queryForList("SELECT sequence_no,event_type,occurred_ms,payload_json FROM execution_run_event WHERE attempt_id=? AND sequence_no>? ORDER BY sequence_no LIMIT 64",input.attemptId(),input.afterSequence()));return result;
    }
    @Transactional public Object receipt(ReceiptQuery input) {
        String owner=owner();text(input.requestId(),80);var rows=repo.jdbc().queryForList("SELECT task_id,attempt_id FROM execution_run_command WHERE owner_id=? AND request_id=?",owner,input.requestId());
        if(rows.isEmpty())return Map.of("found",false);var run=session((String)rows.getFirst().get("task_id"),(String)rows.getFirst().get("attempt_id"));owned(owner,run);return view(run);
    }
    @Transactional
    @org.springframework.scheduling.annotation.Scheduled(fixedDelay=86400000,initialDelay=300000)
    public void pruneMetadata() {
        boundary.lock();
        repo.jdbc().update("""
                DELETE FROM execution_run_event WHERE attempt_id IN (
                  SELECT a.id FROM execution_attempt a WHERE a.state NOT IN ('CLAIMED','RUNNING')
                    AND a.finished_at_epoch_ms<?
                    AND NOT EXISTS (SELECT 1 FROM execution_run_command c WHERE c.attempt_id=a.id AND c.status IN ('ACCEPTED','RECEIVED','UNKNOWN'))
                    AND NOT EXISTS (SELECT 1 FROM execution_operation o WHERE o.attempt_id=a.id AND o.state IN ('STARTED','UNKNOWN'))
                )
                """,System.currentTimeMillis()-30L*86400000);
    }
}
