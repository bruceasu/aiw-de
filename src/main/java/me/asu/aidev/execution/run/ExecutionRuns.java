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
    @org.springframework.beans.factory.annotation.Autowired private NativePlanOwners nativeOwners;
    @org.springframework.beans.factory.annotation.Autowired private NativeRunLifecycle nativeLifecycle;
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
        String task=(String)run.get("task_id");
        String actual=guard.managed(task)?guard.context(task).ownerId():nativeOwners.task(task,false).ownerId();
        if(!owner.equals(run.get("owner_id"))||!owner.equals(actual))throw new SecurityException("Exact owned task required");
    }
    private void machine(String task,GuardContract.Lease lease,boolean running) {
        Objects.requireNonNull(lease);guard.verifyLease(task,lease,running,true);
        if(!guard.managed(task))nativeOwners.task(task,running);
    }
    private Map<String,Object> view(Map<String,Object> run) {
        var attempt=repo.jdbc().queryForMap("SELECT state,stop_requested_at_epoch_ms,stop_acknowledged_at_epoch_ms FROM execution_attempt WHERE id=?",run.get("attempt_id"));
        var result=new LinkedHashMap<String,Object>();result.put("protocol",PROTOCOL);
        result.put("progressEventsSupported",true);
        result.put("progressReviewSupported",true);
        result.put("taskId",run.get("task_id"));result.put("attemptId",run.get("attempt_id"));result.put("instructionRevision",run.get("instruction_revision"));
        result.put("revision",run.get("revision"));result.put("acknowledgedSequence",run.get("ack_sequence"));result.put("lastContact",run.get("last_contact_ms"));
        String phase=(String)run.get("phase");
        if(!"RUNNING".equals(attempt.get("state")))phase="SETTLED";
        else if(attempt.get("stop_requested_at_epoch_ms")!=null)phase="STOPPING";
        result.put("phase",phase);result.put("attemptState",attempt.get("state"));
        result.put("capabilities",repo.decode((String)run.get("capabilities_json"),Map.class));
        result.put("configurationDigest",run.get("open_digest"));
        result.put("sourceKind",guard.managed((String)run.get("task_id"))?"GUARDED":"NATIVE");
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
        boolean managed=guard.managed(task);String owner;int revision;
        if(managed) {
            var fixed=guard.context(task);owner=fixed.ownerId();revision=fixed.snapshot().task().instructionRevision();
            if(!"TOOL".equals(input.capabilities().get("pauseBoundary"))||!"TOOL".equals(input.capabilities().get("dispatchBoundary"))||!"HOST".equals(input.capabilities().get("toolExecutor")))throw new IllegalArgumentException("Guarded host boundary required");
        } else {
            var basis=nativeOwners.task(task,true);owner=basis.ownerId();revision=basis.task().instructionRevision();
            var worker=repo.jdbc().queryForMap("SELECT runtime_type,capabilities FROM worker WHERE id=?",input.lease().workerId());
            var registered=me.asu.aidev.work.domain.CapabilitySetCodec.decode((String)worker.get("capabilities"));
            if(!String.valueOf(worker.get("runtime_type")).startsWith("codex-app-server:")||!registered.contains(NativePlanOwners.CAPABILITY)||input.configurationCapability()==null||!input.configurationCapability().matches("native-config:[0-9a-f]{64}")||!registered.contains(input.configurationCapability()))throw new SecurityException("Registered native turn adapter and fixed configuration required");
            if(!"TURN".equals(input.capabilities().get("pauseBoundary"))||!"TURN".equals(input.capabilities().get("dispatchBoundary"))||!"BACKEND".equals(input.capabilities().get("toolExecutor"))||!"BACKEND".equals(input.capabilities().get("requestLoopOwner"))||!"NONE".equals(input.capabilities().get("supplementBoundary"))||!"NONE".equals(input.capabilities().get("sessionReuse"))||!"UNKNOWN".equals(input.capabilities().get("usageScope"))||!"BACKEND_TURN".equals(input.capabilities().get("requestAccountingScope")))throw new IllegalArgumentException("Native turn limits must be explicit");
        }
        if(input.instructionRevision()!=revision)throw new IllegalStateException("Frozen task revision changed");
        String digest=AuthorityRepository.hash(repo.encode(input)),attempt=input.lease().attemptId();
        var prior=repo.jdbc().queryForList("SELECT * FROM execution_run_session WHERE attempt_id=?",attempt);
        if(!prior.isEmpty()) {
            if(!digest.equals(prior.getFirst().get("open_digest")))throw new IllegalStateException("Run capability declaration changed");
            return view(prior.getFirst());
        }
        repo.jdbc().update("INSERT INTO execution_run_session(attempt_id,task_id,owner_id,instruction_revision,capabilities_json,open_digest,phase,revision,ack_sequence,event_bytes,last_contact_ms) VALUES(?,?,?,?,?,?,'RUNNING',1,0,0,?)",attempt,task,owner,input.instructionRevision(),repo.encode(input.capabilities()),digest,System.currentTimeMillis());
        if(!managed)repo.jdbc().update("UPDATE execution_run_session SET configuration_capability=? WHERE attempt_id=?",input.configurationCapability(),attempt);
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
        var allowed=Set.of("context-projected","context-component","guard-intent","guard-start","guard-finished","host-batch-validated","validation-failed","validation-passed","native-turn-started","execution-quiescent","delivery-pending","progress-stalled","progress-review");
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
                if(!guard.managed(task))nativeLifecycle.requireQuiescent(task,input.lease());
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
        boolean cancel="CANCEL".equals(input.action()),nativeRun=!guard.managed(input.taskId());
        if(!"RUNNING".equals(actual.get("state"))||!("RUNNING".equals(actual.get("task_state"))||cancel&&nativeRun&&"YIELDING".equals(actual.get("task_state")))||!Objects.equals(actual.get("lease_token"),actual.get("current_lease"))||number(actual,"lease_expires_at_epoch_ms")<=System.currentTimeMillis()&&!(cancel&&nativeRun))throw new IllegalStateException("Original Attempt is no longer running");
        if(actual.get("stop_requested_at_epoch_ms")!=null||Set.of("BROKEN","FINALIZING","YIELDING").contains(run.get("phase"))&&!cancel)throw new IllegalStateException("Run is stopping or sealed");
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
        if(nativeLifecycle!=null)nativeLifecycle.requireQuiescent(task,input.lease());
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
        if(input.attemptId()==null) {
            var result=new LinkedHashMap<String,Object>();result.put("protocol",PROTOCOL);result.put("ownerId",owner);
            result.put("runs",repo.jdbc().queryForList("SELECT task_id,attempt_id,instruction_revision,phase,last_contact_ms FROM execution_run_session WHERE owner_id=? ORDER BY last_contact_ms DESC LIMIT 100",owner));
            result.put("plans",nativeOwners.visiblePlans());
            if(input.planId()!=null) {
                var visible=nativeOwners.visiblePlans().stream().anyMatch(p->input.planId().equals(p.get("id")));
                if(!visible)throw new SecurityException("Owned Plan required");
                result.put("nativeTasks",repo.jdbc().queryForList("SELECT t.id AS task_id,a.id AS attempt_id,a.state FROM work_task t JOIN execution_attempt a ON a.task_id=t.id WHERE t.subject_type='flexible-plan' AND t.subject_id=? ORDER BY a.created_at_epoch_ms DESC LIMIT 100",input.planId()));
            } else result.put("nativeTasks",List.of());
            return result;
        }
        if(input.afterSequence()<0)throw new IllegalArgumentException("Invalid cursor");var run=session(input.taskId(),input.attemptId());owned(owner,run);
        var result=new LinkedHashMap<>(view(run));result.put("events",repo.jdbc().queryForList("SELECT sequence_no,event_type,occurred_ms,payload_json FROM execution_run_event WHERE attempt_id=? AND sequence_no>? ORDER BY sequence_no LIMIT 64",input.attemptId(),input.afterSequence()));return result;
    }
    @Transactional public Object receipt(ReceiptQuery input) {
        String owner=owner();text(input.requestId(),80);var rows=repo.jdbc().queryForList("SELECT task_id,attempt_id FROM execution_run_command WHERE owner_id=? AND request_id=?",owner,input.requestId());
        if(rows.isEmpty())return Map.of("found",false);var run=session((String)rows.getFirst().get("task_id"),(String)rows.getFirst().get("attempt_id"));owned(owner,run);return view(run);
    }
    @Transactional public Object availability(String task,GuardContract.Lease lease) {
        boundary.lock();guard.verifyLease(task,lease,true,true);
        if(guard.managed(task))return Map.of("supported",false);
        var rows=repo.jdbc().queryForList("SELECT subject_type,subject_id FROM work_task WHERE id=?",task);
        if(rows.size()!=1||!"flexible-plan".equals(rows.getFirst().get("subject_type"))||!nativeOwners.bound((String)rows.getFirst().get("subject_id")))return Map.of("supported",false);
        var basis=nativeOwners.task(task,true);
        long checkpoints=repo.jdbc().queryForObject("SELECT COUNT(*) FROM formal_question_slot WHERE successor_task_id=?",Long.class,task);
        if(checkpoints>1)throw new IllegalStateException("One exact continuation source required");
        return Map.of("supported",true,"planId",basis.planId(),"sourceVersion",basis.sourceVersion(),"sourceDigest",basis.sourceDigest(),"checkpointRequired",checkpoints==1);
    }
    @Transactional public Object humanNativeStopped(String task,GuardContract.StopEvidence input) {
        boundary.lock();String current=owner();var basis=nativeOwners.task(task,false);
        if(!current.equals(basis.ownerId()))throw new SecurityException("Exact current Owner required");
        var prior=repo.jdbc().queryForList("SELECT decision_json FROM execution_stop_evidence WHERE attempt_id=?",String.class,input.attemptId());
        String json=repo.encode(input);
        if(!prior.isEmpty()) {
            if(!json.equals(prior.getFirst()))throw new IllegalStateException("Stop evidence identifier has different content");
            return view(session(task,input.attemptId()));
        }
        var row=repo.jdbc().queryForMap("SELECT a.worker_id,a.lease_token,t.lease_token AS current_lease FROM execution_attempt a JOIN work_task t ON t.id=a.task_id WHERE a.task_id=? AND a.id=? AND a.state='RUNNING'",task,input.attemptId());
        if(!Objects.equals(row.get("lease_token"),row.get("current_lease"))||!nativeLifecycle.managed(task,input.attemptId()))throw new IllegalStateException("Original active native run required");
        repo.jdbc().update("INSERT INTO execution_stop_evidence VALUES(?,?)",input.attemptId(),json);
        repo.jdbc().update("UPDATE execution_attempt SET stop_requested_at_epoch_ms=COALESCE(stop_requested_at_epoch_ms,?) WHERE id=?",System.currentTimeMillis(),input.attemptId());
        var lease=new GuardContract.Lease((String)row.get("worker_id"),input.attemptId(),(String)row.get("lease_token"));
        boolean planControl=repo.jdbc().queryForObject("SELECT COUNT(*) FROM flexible_workflow_control WHERE plan_id=? AND task_id=? AND status='PENDING_STOP'",Long.class,basis.planId(),task)>0;
        if(planControl)tasks.getObject().acknowledgeStop(new TaskId(task),new me.asu.aidev.worker.domain.WorkerId(lease.workerId()),new me.asu.aidev.execution.domain.LeaseToken(lease.leaseToken()));
        else nativeLifecycle.cancelled(task,lease);
        repo.audit("PLAN:"+basis.planId(),null,input.attemptId(),boundary.human(),"NATIVE_PROCESS_STOP_CONFIRMED",json);
        return view(session(task,input.attemptId()));
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
