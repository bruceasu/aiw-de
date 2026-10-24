package me.asu.aidev.execution.config;

import java.util.*;
import me.asu.aidev.authority.*;
import me.asu.aidev.execution.guard.ExecutionGuard;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static me.asu.aidev.execution.config.WorkbenchContract.*;
import static me.asu.aidev.execution.guard.GuardContract.text;

@Service
public class ExecutionWorkbench {
    private final AuthorityRepository repo;private final AuthorityBoundary boundary;private final ExecutionGuard guard;
    public ExecutionWorkbench(AuthorityRepository repo,AuthorityBoundary boundary,ExecutionGuard guard) {
        this.repo=repo;this.boundary=boundary;this.guard=guard;
    }
    private String owner() {
        if(boundary.human()==null||boundary.delegated())throw new SecurityException("Authenticated human required");
        var owners=repo.jdbc().queryForList("SELECT id FROM authority_owner WHERE actor=?",String.class,boundary.human());
        if(owners.size()!=1)throw new SecurityException("Bind one current owner first");return owners.getFirst();
    }
    private Map<String,Object> ownedAttempt(String owner,String task,String attempt) {
        text(task,128);text(attempt,128);
        var rows=repo.jdbc().queryForList("""
                SELECT t.instruction_revision,a.instruction_revision AS attempt_revision,t.state AS task_state,a.state AS attempt_state,a.ai_runtime,
                       t.lease_token AS current_lease,a.lease_token AS attempt_lease,t.lease_expires_at_epoch_ms,
                       a.result_revision,a.stop_requested_at_epoch_ms
                  FROM execution_context x JOIN execution_policy p ON p.plan_id=x.plan_id
                  JOIN work_task t ON t.id=x.task_id JOIN execution_attempt a ON a.task_id=t.id
                 WHERE p.owner_id=? AND t.id=? AND a.id=?
                """,owner,task,attempt);
        if(rows.size()!=1)throw new SecurityException("Owned guarded task and Attempt required");return rows.getFirst();
    }
    private void revision(Map<String,Object> task,int expected) {
        if(number(task,"instruction_revision")!=expected||task.containsKey("attempt_revision")&&number(task,"attempt_revision")!=expected)
            throw new IllegalStateException("Task instruction revision changed");
    }
    private Map<String,Object> session(String owner,String task,String attempt) {
        var rows=repo.jdbc().queryForList("SELECT * FROM execution_workbench_session WHERE owner_id=? AND task_id=? AND attempt_id=?",owner,task,attempt);
        if(rows.size()!=1)throw new IllegalStateException("This Attempt has not opened supported live inputs");return rows.getFirst();
    }
    private Object oldReceipt(String owner,String request,String digest) {
        var rows=repo.jdbc().queryForList("SELECT command_digest,result_json FROM execution_workbench_receipt WHERE owner_id=? AND request_id=?",owner,request);
        if(rows.isEmpty())return null;
        if(!digest.equals(rows.getFirst().get("command_digest")))throw new IllegalStateException("Original request identifier has different content");
        return repo.decode((String)rows.getFirst().get("result_json"),Object.class);
    }
    @Transactional public Object receipt(ReceiptQuery input) {
        String owner=owner();text(input.requestId(),80);
        var rows=repo.jdbc().queryForList("SELECT result_json FROM execution_workbench_receipt WHERE owner_id=? AND request_id=?",String.class,owner,input.requestId());
        return rows.isEmpty()?Map.of("found",false):Map.of("found",true,"result",repo.decode(rows.getFirst(),Object.class));
    }
    @Transactional public Object command(HumanCommand input) {
        boundary.lock();String owner=owner();text(input.requestId(),80);text(input.action(),24);
        String digest=AuthorityRepository.hash(repo.encode(input));Object previous=oldReceipt(owner,input.requestId(),digest);if(previous!=null)return previous;
        var task=ownedAttempt(owner,input.taskId(),input.attemptId());
        if(number(task,"attempt_revision")!=input.instructionRevision())throw new IllegalStateException("Original Attempt revision changed");
        Object result=switch(input.action()) {
            case "SEND" -> enqueue(owner,input,task);
            case "CANCEL" -> cancel(owner,input);
            case "BRANCH" -> branch(owner,input,task);
            case "SELECT","DISCARD" -> choose(owner,input);
            default -> throw new IllegalArgumentException("Unknown workbench action");
        };
        repo.jdbc().update("INSERT INTO execution_workbench_receipt VALUES(?,?,?,?)",owner,input.requestId(),digest,repo.encode(result));
        return result;
    }
    private Object enqueue(String owner,HumanCommand input,Map<String,Object> task) {
        var session=session(owner,input.taskId(),input.attemptId());
        if(!"RUNNING".equals(session.get("phase"))||!"RUNNING".equals(task.get("task_state"))||!"RUNNING".equals(task.get("attempt_state"))||task.get("stop_requested_at_epoch_ms")!=null
                ||!Objects.equals(task.get("current_lease"),task.get("attempt_lease"))||number(task,"lease_expires_at_epoch_ms")<=System.currentTimeMillis())
            throw new IllegalStateException("Attempt is stopping, finalizing or no longer running");
        revision(session,input.instructionRevision());text(input.text(),4000);
        if(!Set.of("STEER","FOLLOW_UP").contains(input.mode()))throw new IllegalArgumentException("Choose current input or follow-up");
        long count=repo.jdbc().queryForObject("SELECT COUNT(*) FROM execution_workbench_message WHERE attempt_id=?",Long.class,input.attemptId());
        if(count>=128)throw new IllegalStateException("This Attempt input record is full");
        String id="input-"+AuthorityRepository.hash(owner+":"+input.requestId()).substring(0,20);long now=System.currentTimeMillis();
        repo.jdbc().update("""
                INSERT INTO execution_workbench_message(id,owner_id,task_id,attempt_id,instruction_revision,request_id,mode,state,body,digest,sequence_no,created_ms,updated_ms)
                VALUES(?,?,?,?,?,?,?,'QUEUED',?,?,?,?,?)
                """,id,owner,input.taskId(),input.attemptId(),input.instructionRevision(),input.requestId(),input.mode(),input.text(),AuthorityRepository.hash(input.text()),count+1,now,now);
        return Map.of("id",id,"state","QUEUED");
    }
    private Object cancel(String owner,HumanCommand input) {
        text(input.messageId(),96);
        if(repo.jdbc().update("UPDATE execution_workbench_message SET state='CANCELLED',updated_ms=? WHERE owner_id=? AND task_id=? AND attempt_id=? AND id=? AND state='QUEUED'",
                System.currentTimeMillis(),owner,input.taskId(),input.attemptId(),input.messageId())!=1)
            throw new IllegalStateException("Only an input not yet reserved can be withdrawn");
        return Map.of("id",input.messageId(),"state","CANCELLED");
    }
    private Object branch(String owner,HumanCommand input,Map<String,Object> task) {
        text(input.title(),120);text(input.text(),8000);
        if(input.parentId()!=null)node(owner,input.taskId(),input.parentId());
        if(repo.jdbc().queryForObject("SELECT COUNT(*) FROM execution_exploration WHERE owner_id=? AND task_id=?",Long.class,owner,input.taskId())>=100)
            throw new IllegalStateException("Task exploration record is full");
        if(input.resultRevision()!=null&&!input.resultRevision().equals(task.get("result_revision")))
            throw new IllegalArgumentException("Only this Attempt's existing result revision can be attached");
        String id="branch-"+AuthorityRepository.hash(owner+":"+input.requestId()).substring(0,20);
        repo.jdbc().update("INSERT INTO execution_exploration VALUES(?,?,?,?,?,?,?,?,?,?,?)",id,owner,input.taskId(),input.attemptId(),input.instructionRevision(),input.parentId(),input.title(),input.text(),input.resultRevision(),boundary.human(),System.currentTimeMillis());
        return Map.of("id",id,"state","RECORDED");
    }
    private void node(String owner,String task,String id) {
        text(id,96);
        if(repo.jdbc().queryForObject("SELECT COUNT(*) FROM execution_exploration WHERE owner_id=? AND task_id=? AND id=?",Long.class,owner,task,id)!=1)
            throw new SecurityException("Exploration parent must belong to the same owned task");
    }
    private Object choose(String owner,HumanCommand input) {
        node(owner,input.taskId(),input.messageId());text(input.text(),2000);
        if(repo.jdbc().queryForObject("SELECT COUNT(*) FROM execution_exploration_choice WHERE owner_id=? AND node_id=?",Long.class,owner,input.messageId())>=128)
            throw new IllegalStateException("Exploration choice record is full");
        repo.jdbc().update("INSERT INTO execution_exploration_choice VALUES(?,?,?,?,?,?,?)",owner,input.requestId(),input.messageId(),input.action(),input.text(),boundary.human(),System.currentTimeMillis());
        return Map.of("id",input.messageId(),"decision",input.action());
    }
    public Object branchHistory(BranchQuery input) {
        String owner=owner();node(owner,input.taskId(),input.nodeId());
        return Map.of("protocol",PROTOCOL,"ownerId",owner,"nodeId",input.nodeId(),"history",repo.jdbc().queryForList(
                "SELECT decision,reason,actor,created_ms FROM execution_exploration_choice WHERE owner_id=? AND node_id=? ORDER BY created_ms DESC LIMIT 128",owner,input.nodeId()));
    }

    @Transactional public Batch machine(String task,MachineCommand input) {
        boundary.lock();Objects.requireNonNull(input.lease());guard.verifyLease(task,input.lease(),true,true);
        var fixed=guard.context(task);String owner=fixed.ownerId(),attempt=input.lease().attemptId();
        var actual=ownedAttempt(owner,task,attempt);int revision=fixed.snapshot().task().instructionRevision();revision(actual,revision);
        if("OPEN".equals(input.action())) {
            if(actual.get("ai_runtime")!=null&&!"openai-agents".equals(actual.get("ai_runtime")))throw new IllegalStateException("This backend does not implement live inputs");
            if(repo.jdbc().queryForObject("SELECT COUNT(*) FROM execution_workbench_session WHERE attempt_id=?",Long.class,attempt)==0)
                repo.jdbc().update("INSERT INTO execution_workbench_session VALUES(?,?,?,?,?,'RUNNING',?)",attempt,task,owner,revision,"python-api",System.currentTimeMillis());
            var opened=session(owner,task,attempt);revision(opened,revision);return empty((String)opened.get("phase"));
        }
        var session=session(owner,task,attempt);revision(session,revision);
        if(!"RUNNING".equals(session.get("phase"))) {
            if("DRAIN".equals(input.action()))return empty("FINALIZING");
            throw new IllegalStateException("Attempt input window is sealed");
        }
        return switch(input.action()) {
            case "RESERVE","DRAIN" -> reserve(attempt,input);
            case "LINK","ACK" -> linked(attempt,input);
            default -> throw new IllegalArgumentException("Unknown machine workbench action");
        };
    }
    private Batch reserve(String attempt,MachineCommand input) {
        text(input.batchId(),96);
        var prior=repo.jdbc().queryForList("SELECT * FROM execution_workbench_batch WHERE attempt_id=? AND batch_id=?",attempt,input.batchId());
        if(!prior.isEmpty())return batch(prior.getFirst());
        if(repo.jdbc().queryForObject("SELECT COUNT(*) FROM execution_workbench_batch WHERE attempt_id=? AND state<>'DELIVERED'",Long.class,attempt)>0)
            throw new IllegalStateException("An original reserved batch still requires confirmation");
        boolean drain="DRAIN".equals(input.action());
        if(!drain&&!"STEER".equals(input.mode()))throw new IllegalArgumentException("Only STEER is reserved during an active run");
        var rows=repo.jdbc().queryForList("SELECT id,mode,body,digest,sequence_no FROM execution_workbench_message WHERE attempt_id=? AND state='QUEUED'"+(drain?"":" AND mode='STEER'")+" ORDER BY sequence_no LIMIT 8",attempt);
        if(rows.isEmpty()) {
            if(drain) {
                if(repo.jdbc().queryForObject("SELECT COUNT(*) FROM execution_operation WHERE attempt_id=? AND state IN ('STARTED','UNKNOWN')",Long.class,attempt)>0)
                    throw new IllegalStateException("Active or unknown operations prevent finalization");
                repo.jdbc().update("UPDATE execution_workbench_session SET phase='FINALIZING',updated_ms=? WHERE attempt_id=? AND phase='RUNNING'",System.currentTimeMillis(),attempt);
                return empty("FINALIZING");
            }
            return empty("RUNNING");
        }
        var messages=rows.stream().map(r->new Input((String)r.get("id"),(String)r.get("mode"),(String)r.get("body"),(String)r.get("digest"),number(r,"sequence_no"))).toList();
        String json=repo.encode(messages),digest=AuthorityRepository.hash(json);long now=System.currentTimeMillis();
        repo.jdbc().update("INSERT INTO execution_workbench_batch VALUES(?,?,?,?,?,NULL,'RESERVED',?)",attempt,input.batchId(),drain?"DRAIN":"STEER",digest,json,now);
        for(var message:messages) {
            if(repo.jdbc().update("UPDATE execution_workbench_message SET state='RESERVED',batch_id=?,updated_ms=? WHERE attempt_id=? AND id=? AND state='QUEUED'",input.batchId(),now,attempt,message.id())!=1)
                throw new IllegalStateException("Input reservation changed");
        }
        return new Batch(PROTOCOL,"RUNNING",input.batchId(),digest,"RESERVED",messages);
    }
    private Batch linked(String attempt,MachineCommand input) {
        text(input.batchId(),96);text(input.operationId(),128);text(input.batchDigest(),64);
        var row=repo.jdbc().queryForMap("SELECT * FROM execution_workbench_batch WHERE attempt_id=? AND batch_id=?",attempt,input.batchId());
        if(!input.batchDigest().equals(row.get("digest")))throw new IllegalStateException("Exact reserved input digest required");
        var operations=repo.jdbc().queryForList("SELECT state FROM execution_operation WHERE attempt_id=? AND operation_id=? AND tool_id='MODEL'",attempt,input.operationId());
        if(operations.size()!=1)throw new SecurityException("Exact MODEL operation from this Attempt required");
        if("LINK".equals(input.action())) {
            if(row.get("operation_id")!=null&&!input.operationId().equals(row.get("operation_id")))throw new IllegalStateException("Reserved batch already bound to another operation");
            if(row.get("operation_id")==null) {
                if(!"STARTED".equals(operations.getFirst().get("state")))throw new IllegalStateException("Bind before dispatching the model");
                repo.jdbc().update("UPDATE execution_workbench_batch SET operation_id=? WHERE attempt_id=? AND batch_id=? AND operation_id IS NULL",input.operationId(),attempt,input.batchId());
            }
        } else {
            if(!input.operationId().equals(row.get("operation_id"))||!"SUCCEEDED".equals(operations.getFirst().get("state")))throw new IllegalStateException("Original bound MODEL has no successful receipt");
            repo.jdbc().update("UPDATE execution_workbench_batch SET state='DELIVERED' WHERE attempt_id=? AND batch_id=?",attempt,input.batchId());
            repo.jdbc().update("UPDATE execution_workbench_message SET state='DELIVERED',updated_ms=? WHERE attempt_id=? AND batch_id=? AND state='RESERVED'",System.currentTimeMillis(),attempt,input.batchId());
            row.put("state","DELIVERED");
        }
        repo.jdbc().update("UPDATE execution_workbench_session SET updated_ms=? WHERE attempt_id=?",System.currentTimeMillis(),attempt);
        return batch(row);
    }
    private Batch batch(Map<String,Object> row) {
        String json=(String)row.get("contents_json");if(!AuthorityRepository.hash(json).equals(row.get("digest")))throw new IllegalStateException("Reserved input content changed");
        return new Batch(PROTOCOL,"RUNNING",(String)row.get("batch_id"),(String)row.get("digest"),(String)row.get("state"),List.of(repo.decode(json,Input[].class)));
    }
    private Batch empty(String phase) { return new Batch(PROTOCOL,phase,null,null,"NONE",List.of()); }
    private static long number(Map<String,Object> row,String key) { return ((Number)row.get(key)).longValue(); }

    @Transactional public Object snapshot(ExecutionObservations.Snapshot observations,Query input) {
        String owner=owner();if(!owner.equals(observations.ownerId()))throw new SecurityException("Snapshot identity changed");
        var entries=observations.entries();
        String requested=input.taskId()==null||input.taskId().isBlank()?(entries.isEmpty()?null:entries.getFirst().taskId()):input.taskId();
        var visible=entries.stream().filter(e->e.taskId().equals(requested)).toList();
        String task=visible.isEmpty()?null:requested;
        String attempt=input.attemptId()==null||input.attemptId().isBlank()?(visible.isEmpty()?null:visible.getFirst().attemptId()):input.attemptId();
        if(attempt!=null&&visible.stream().noneMatch(e->e.attemptId().equals(attempt)))throw new SecurityException("Attempt belongs to another task or view");
        var result=new LinkedHashMap<String,Object>();result.put("protocol",PROTOCOL);result.put("ownerId",owner);result.put("observedAt",observations.observedAt());
        result.put("attempts",entries);result.put("taskId",task);result.put("attemptId",attempt);
        result.put("notice",requested!=null&&task==null?"所选任务未出现在最近的计量记录中。可稍后刷新，或选择其他已有记录的任务。":null);
        if(attempt==null)result.put("basis",null);
        else {
            var basis=ownedAttempt(owner,task,attempt);
            result.put("basis",Map.of("instructionRevision",number(basis,"attempt_revision"),"currentInstructionRevision",number(basis,"instruction_revision"),
                    "live",Objects.equals(basis.get("current_lease"),basis.get("attempt_lease"))&&basis.get("lease_expires_at_epoch_ms")!=null&&number(basis,"lease_expires_at_epoch_ms")>System.currentTimeMillis(),
                    "stopRequested",basis.get("stop_requested_at_epoch_ms")!=null));
        }
        result.put("sessions",repo.jdbc().queryForList("SELECT task_id,attempt_id,instruction_revision,runtime,phase,updated_ms FROM execution_workbench_session WHERE owner_id=? ORDER BY CASE WHEN attempt_id=? THEN 0 ELSE 1 END,updated_ms DESC LIMIT 100",owner,attempt));
        result.put("messages",attempt==null?List.of():repo.jdbc().queryForList("""
                SELECT id,request_id,instruction_revision,mode,state,body,digest,sequence_no,batch_id,created_ms,updated_ms
                  FROM execution_workbench_message WHERE owner_id=? AND attempt_id=? ORDER BY sequence_no LIMIT 128
                """,owner,attempt));
        result.put("batches",attempt==null?List.of():repo.jdbc().queryForList("""
                SELECT b.batch_id,b.state,b.operation_id,o.state AS operation_state
                  FROM execution_workbench_batch b JOIN execution_workbench_session s ON s.attempt_id=b.attempt_id
                  LEFT JOIN execution_operation o ON o.operation_id=b.operation_id AND o.attempt_id=b.attempt_id
                 WHERE s.owner_id=? AND b.attempt_id=? ORDER BY b.created_ms LIMIT 128
                """,owner,attempt));
        result.put("operations",attempt==null?List.of():repo.jdbc().queryForList("""
                SELECT o.operation_id,o.tool_id,o.resource,o.state,o.started_ms,o.finished_ms,o.result_digest,o.evidence_ref
                  FROM execution_operation o JOIN execution_policy p ON p.plan_id=o.plan_id
                 WHERE p.owner_id=? AND o.attempt_id=? ORDER BY o.started_ms DESC LIMIT 50
                """,owner,attempt));
        result.put("branches",task==null?List.of():repo.jdbc().queryForList("SELECT id,attempt_id,instruction_revision,parent_id,title,body,result_revision,actor,created_ms FROM execution_exploration WHERE owner_id=? AND task_id=? ORDER BY created_ms,id LIMIT 100",owner,task));
        result.put("choices",task==null?List.of():repo.jdbc().queryForList("""
                SELECT c.node_id,c.decision,c.reason,c.actor,c.created_ms FROM execution_exploration_choice c
                  JOIN execution_exploration n ON n.id=c.node_id AND n.owner_id=c.owner_id
                 WHERE c.owner_id=? AND n.task_id=? ORDER BY c.created_ms DESC LIMIT 200
                """,owner,task));
        return result;
    }
}
