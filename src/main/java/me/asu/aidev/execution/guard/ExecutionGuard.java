package me.asu.aidev.execution.guard;

import java.util.*;
import me.asu.aidev.authority.*;
import me.asu.aidev.work.domain.*;
import me.asu.aidev.work.port.*;
import me.asu.aidev.execution.port.ExecutionAttemptRepository;
import me.asu.aidev.execution.domain.TaskInput;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static me.asu.aidev.execution.guard.GuardContract.*;
import static me.asu.aidev.execution.guard.ExecutionLedger.number;

/** The platform linearization point. No external operation runs inside these database transactions. */
@Service
public class ExecutionGuard {
    @org.springframework.beans.factory.annotation.Value("${aidev.worker-protocol.token:}")
    private String machineToken;
    @org.springframework.beans.factory.annotation.Autowired
    private me.asu.aidev.webconsole.security.WebConsoleSecurityService humanSecurity;
    private final AuthorityRepository repo; private final AuthorityBoundary boundary;
    private final ExecutionLedger ledger; private final WorkTaskRepository tasks;
    private final TaskDependencyRepository dependencies; private final ExecutionAttemptRepository attempts;
    public ExecutionGuard(AuthorityRepository repo,AuthorityBoundary boundary,ExecutionLedger ledger,
            WorkTaskRepository tasks,TaskDependencyRepository dependencies,ExecutionAttemptRepository attempts) {
        this.repo=repo;this.boundary=boundary;this.ledger=ledger;this.tasks=tasks;this.dependencies=dependencies;this.attempts=attempts;
    }
    public record TaskBasis(TaskId id,TaskType taskType,String instruction,int instructionRevision,String repositoryRef,
                            String baseRevision,Set<String> requiredCapabilities,String skillRef,String subjectType,String subjectId) {
        static TaskBasis of(WorkTask t) { return new TaskBasis(t.id(),t.type(),t.instruction(),t.instructionRevision(),t.repositoryRef(),t.baseRevision(),t.requiredCapabilities(),t.skillRef(),t.subjectType(),t.subjectId()); }
    }
    public record Snapshot(TaskBasis task,List<TaskInput> inputs,List<FrozenRef> references) {
        public Snapshot(WorkTask task,List<TaskInput> inputs,List<FrozenRef> references) { this(TaskBasis.of(task),inputs,references); }
    }
    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.beans.factory.ObjectProvider<me.asu.aidev.execution.application.TaskExecutionService> executionService;
    public record Context(String protocol,String instanceId,String taskId,String planId,String ownerId,
                          long grantRevision,String configurationDigest,String inputDigest,Snapshot snapshot,
                          Policy originalPolicy,Policy currentPolicy,List<Map<String,Object>> budgets) {}
    public record Receipt(String protocol,String operationId,String state,boolean execute,String code) {}
    public boolean managed(String task) { return !repo.jdbc().queryForList("SELECT task_id FROM execution_context WHERE task_id=?",String.class,task).isEmpty(); }
    public boolean required(WorkTask task) {
        return task.requiredCapabilities().contains(CAPABILITY) || managed(task.id().value())
                || task.subjectType().equals("flexible-plan") && repo.jdbc().queryForObject("SELECT COUNT(*) FROM execution_policy WHERE plan_id=?",Long.class,task.subjectId())>0;
    }
    public void serializeCandidate(WorkTask task) { if(task.subjectType().equals("flexible-plan") || required(task)) boundary.lock(); }
    public boolean requiresCreationGuard(me.asu.aidev.work.application.CreateWorkTaskCommand command) {
        return command.requiredCapabilities().contains(CAPABILITY)
            || command.subjectType().equals("flexible-plan") && repo.jdbc().queryForObject("SELECT COUNT(*) FROM execution_policy WHERE plan_id=?",Long.class,command.subjectId())>0;
    }
    public void beforeTaskCreation(me.asu.aidev.work.application.CreateWorkTaskCommand command) {
        if(!requiresCreationGuard(command)) return;boundary.lock();
        if(tasks.find(command.id()).isPresent()) return;
        var row=policyRow(command.subjectId());
        if(!active(row) || AuthorityRepository.flag(row.get("paused")) || !ledger.configurationAllowed(policy(row))) throw new IllegalStateException("Configure and activate the execution policy before creating guarded business tasks");
        if(!ledger.available(command.subjectId(),Dimension.TASKS,"",1)) throw new IllegalStateException("TASK_BUDGET_INCREMENT_REQUIRED");
    }
    public void taskCreated(me.asu.aidev.work.application.CreateWorkTaskCommand command) {
        if(!requiresCreationGuard(command)) return;chargeTask(command.id().value(),command.subjectId());
    }
    private void chargeTask(String task,String plan) {
        var previous=repo.jdbc().queryForList("SELECT plan_id FROM execution_task_charge WHERE task_id=?",String.class,task);
        if(!previous.isEmpty()) { if(!previous.getFirst().equals(plan)) throw new IllegalStateException("Task budget target is immutable");return; }
        ledger.task(plan);repo.jdbc().update("INSERT INTO execution_task_charge VALUES(?,?)",task,plan);
    }
    Map<String,Object> contextRow(String task) { return repo.jdbc().queryForMap("SELECT * FROM execution_context WHERE task_id=?",task); }
    public Map<String,Object> policyRow(String plan) { return repo.jdbc().queryForMap("SELECT * FROM execution_policy WHERE plan_id=?",plan); }
    Policy policy(Map<String,Object> row) { return repo.decode(String.valueOf(row.get("policy_json")),Policy.class); }
    public void owner(String owner) {
        if(boundary.human()==null || boundary.delegated() || !Objects.equals(repo.actor(owner),boundary.human()))
            throw new SecurityException("Current authenticated execution owner required");
    }
    public void requirePolicyActor(String plan) {
        String owner=String.valueOf(policyRow(plan).get("owner_id"));
        if(boundary.delegated()) {
            if(!owner.equals(boundary.principal().owner())) throw new SecurityException("Policy belongs to another owner");
        } else owner(owner);
    }
    @Transactional public Context confirm(Policy input) {
        boundary.lock();owner(input.ownerId());
        if(machineToken==null || machineToken.isBlank()) throw new IllegalStateException("Configure the shared machine token before enabling guarded execution");
        var owners=repo.jdbc().queryForList("SELECT owner_id FROM authority_target WHERE target_key=?",String.class,"PLAN:"+input.planId());
        if(!owners.isEmpty() && !owners.getFirst().equals(input.ownerId())) throw new SecurityException("Existing target belongs to another owner");
        var graphOwners=repo.jdbc().queryForList("SELECT owner_id FROM workflow_graph WHERE id=?",String.class,input.planId());
        if(graphOwners.stream().anyMatch(o->!o.equals(input.ownerId())))throw new SecurityException("Existing graph belongs to another owner");
        var creators=repo.jdbc().queryForList("SELECT created_by FROM flexible_workflow_plan WHERE id=?",String.class,input.planId());
        if(!creators.isEmpty() && !Objects.equals(creators.getFirst(),boundary.human())) throw new SecurityException("Existing Plan belongs to another creator");
        if(input.expiresAt()<=System.currentTimeMillis()) throw new IllegalArgumentException("Future policy expiry required");
        var previous=repo.jdbc().queryForList("SELECT * FROM execution_policy WHERE plan_id=?",input.planId());
        String encoded=repo.encode(input),digest=AuthorityRepository.hash(encoded);
        if(!previous.isEmpty()) {
            var p=previous.getFirst();if(!Objects.equals(p.get("owner_id"),input.ownerId())) throw new SecurityException("Policy owner is fixed");
            if(number(p,"revision")==input.revision() && Objects.equals(p.get("digest"),digest)) return policyView(input.planId());
            if(input.revision()!=number(p,"revision")+1) throw new IllegalStateException("Exact next policy revision required");
        } else {
            if(input.revision()!=1) throw new IllegalArgumentException("Initial policy revision must be one");
            if(repo.jdbc().queryForObject("SELECT COUNT(*) FROM work_task WHERE subject_type='flexible-plan' AND subject_id=? AND attempt_no>0",Long.class,input.planId())>0)
                throw new IllegalStateException("A running legacy Plan cannot be adopted into guarded execution; create a fresh Plan");
        }
        repo.jdbc().update("""
                INSERT INTO execution_policy(plan_id,owner_id,revision,digest,policy_json,expires_ms,confirmed_by,confirmed_ms)
                VALUES(?,?,?,?,?,?,?,?) ON CONFLICT(plan_id) DO UPDATE SET revision=excluded.revision,digest=excluded.digest,
                policy_json=excluded.policy_json,expires_ms=excluded.expires_ms,confirmed_by=excluded.confirmed_by,confirmed_ms=excluded.confirmed_ms
                """,input.planId(),input.ownerId(),input.revision(),digest,encoded,input.expiresAt(),boundary.human(),System.currentTimeMillis());
        ledger.configure(input);
        executionService.getObject().requestGuardedAdvance(input.planId());
        repo.audit("PLAN:"+input.planId(),null,null,boundary.human(),"EXECUTION_POLICY_CONFIRMED",digest);
        return policyView(input.planId());
    }
    private Context policyView(String plan) {
        var row=policyRow(plan);var p=policy(row);
        return new Context(PROTOCOL,boundary.instance(),null,plan,p.ownerId(),p.revision(),null,null,null,p,p,ledger.snapshot(plan));
    }
    @Transactional public Object control(String plan,long revision,String action) {
        boundary.lock();var row=policyRow(plan);owner(String.valueOf(row.get("owner_id")));
        if(number(row,"revision")!=revision) throw new IllegalStateException("POLICY_REVISION_CHANGED");
        switch(action) {
            case "pause" -> repo.jdbc().update("UPDATE execution_policy SET paused=TRUE WHERE plan_id=?",plan);
            case "resume" -> repo.jdbc().update("UPDATE execution_policy SET paused=FALSE WHERE plan_id=? AND revoked=FALSE",plan);
            case "revoke" -> repo.jdbc().update("UPDATE execution_policy SET revoked=TRUE,generation=generation+1 WHERE plan_id=? AND revoked=FALSE",plan);
            default -> throw new IllegalArgumentException("Unsupported execution control");
        }
        repo.audit("PLAN:"+plan,null,null,boundary.human(),"EXECUTION_POLICY_"+action.toUpperCase(Locale.ROOT),"revision="+revision);
        if(action.equals("resume")) executionService.getObject().requestGuardedAdvance(plan);
        return Map.of("state",action);
    }
    @Transactional public Context bind(String taskId,Bind input) {
        boundary.lock();var row=policyRow(input.planId());owner(String.valueOf(row.get("owner_id")));
        return bindPrepared(taskId,input,String.valueOf(row.get("owner_id")));
    }
    /** Trusted platform materializer port; no machine or human HTTP mapping. Does not grant policy. */
    @Transactional public Context bindPrepared(String taskId,Bind input,String expectedOwner) {
        boundary.lock();var row=policyRow(input.planId());
        if(!Objects.equals(row.get("owner_id"),expectedOwner)) throw new SecurityException("Prepared target owner mismatch");
        if(managed(taskId)) {
            var c=contextRow(taskId);
            var fixed=repo.decode(String.valueOf(c.get("input_json")),Snapshot.class);
            if(!Objects.equals(c.get("decision_id"),input.sourceDecisionId()) || !Objects.equals(c.get("config_digest"),input.configurationDigest())
                    || !Objects.equals(c.get("plan_id"),input.planId()) || number(c,"policy_revision")!=input.policyRevision() || !fixed.references().equals(input.references()))
                throw new IllegalStateException("FIXED_CONTEXT_CONFLICT");
            return context(taskId);
        }
        if(number(row,"revision")!=input.policyRevision() || !active(row) || !ledger.configurationAllowed(policy(row))) throw new IllegalStateException("Policy changed, inactive or missing required budget configuration");
        tasks.lock(new TaskId(taskId));var task=tasks.find(new TaskId(taskId)).orElseThrow();
        if(tasks.attemptNo(task.id())!=0 || !Set.of(TaskState.BLOCKED,TaskState.READY).contains(task.state())
                || !task.requiredCapabilities().contains(CAPABILITY) || task.repositoryRef()==null || task.baseRevision()==null)
            throw new IllegalStateException("Unclaimed guard-capable task with immutable repository base required");
        if(!task.subjectId().equals(input.planId())) throw new SecurityException("Task and execution policy must identify the same Plan");
        var inputs=dependencies.findDependencies(task.id()).stream().map(id->attempts.findCompletedInput(id)
                .orElseThrow(()->new IllegalStateException("Fixed upstream result not available"))).toList();
        var snapshot=new Snapshot(task,inputs,input.references());String json=repo.encode(snapshot);
        chargeTask(taskId,input.planId());
        repo.jdbc().update("""
                INSERT INTO execution_context(task_id,plan_id,policy_revision,generation,decision_id,config_digest,policy_json,input_json,input_digest,task_version)
                VALUES(?,?,?,?,?,?,?,?,?,?)
                """,taskId,input.planId(),input.policyRevision(),number(row,"generation"),input.sourceDecisionId(),input.configurationDigest(),
                row.get("policy_json"),json,AuthorityRepository.hash(json),task.version());
        return context(taskId);
    }
    public Context context(String task) {
        var c=contextRow(task);var row=policyRow(String.valueOf(c.get("plan_id")));var p=policy(row);
        String json=String.valueOf(c.get("input_json"));
        if(!AuthorityRepository.hash(json).equals(c.get("input_digest"))) throw new IllegalStateException("FIXED_INPUT_CORRUPTED");
        return new Context(PROTOCOL,boundary.instance(),task,p.planId(),p.ownerId(),p.revision(),String.valueOf(c.get("config_digest")),
                String.valueOf(c.get("input_digest")),repo.decode(json,Snapshot.class),repo.decode(String.valueOf(c.get("policy_json")),Policy.class),p,ledger.snapshot(p.planId()));
    }
    public boolean canClaim(String task) {
        if(!managed(task)) return false;
        boundary.lock();var c=contextRow(task);var row=policyRow(String.valueOf(c.get("plan_id")));
        var frozen=repo.decode(String.valueOf(c.get("policy_json")),Policy.class);
        return ledger.pricingCompatible(frozen,policy(row))
                && active(row) && !AuthorityRepository.flag(row.get("paused")) && number(row,"generation")==number(c,"generation")
                && !unknownTask(task) && ledger.admits(policy(row)) && ledger.admits(frozen) && unchanged(task,c);
    }
    public Policy preparationPolicy(String plan,long expectedRevision) {
        boundary.lock();var row=policyRow(plan);
        if(number(row,"revision")!=expectedRevision) throw new IllegalStateException("SOURCE_CHANGED");
        if(!active(row) || AuthorityRepository.flag(row.get("paused"))) throw new SecurityException("AUTHORIZATION_REQUIRED");
        return policy(row);
    }
    public boolean preparationBudget(Policy policy) {
        return ledger.admits(policy) && ledger.available(policy.planId(),Dimension.TASKS,"",1);
    }
    private boolean unchanged(String id,Map<String,Object> c) {
        var frozen=repo.decode(String.valueOf(c.get("input_json")),Snapshot.class).task();var actual=tasks.find(new TaskId(id)).orElseThrow();
        return actual.instructionRevision()==frozen.instructionRevision() && actual.instruction().equals(frozen.instruction())
                && Objects.equals(actual.baseRevision(),frozen.baseRevision()) && Objects.equals(actual.repositoryRef(),frozen.repositoryRef())
                && actual.requiredCapabilities().equals(frozen.requiredCapabilities()) && Objects.equals(actual.skillRef(),frozen.skillRef());
    }
    /** Runs in the successful claim transaction, before its Lease becomes visible. */
    public void claimed(String task,Lease lease) {
        if(!managed(task)) return;
        if(!canClaim(task)) throw new IllegalStateException("GUARD_ADMISSION_BLOCKED");
        verifyLease(task,lease,false,true);
        ledger.reserve(task,lease.attemptId(),repo.decode(String.valueOf(contextRow(task).get("policy_json")),Policy.class));
    }
    public void starting(String task,Lease lease) {
        if(!managed(task)) {
            if(required(tasks.find(new TaskId(task)).orElseThrow())) throw new IllegalStateException("Guard policy and fixed context required before execution");
            return;
        }
        boundary.lock();var c=contextRow(task);var row=policyRow(String.valueOf(c.get("plan_id")));
        // A committed claim is in flight. Pause and later budget consumption do not revoke it.
        if(!active(row) || number(row,"generation")!=number(c,"generation") || unknownTask(task) || !unchanged(task,c))
            throw new IllegalStateException("GUARD_EXECUTION_BLOCKED");
        verifyLease(task,lease,false,true);ledger.start(task,lease.attemptId());
    }
    public void serialize(String task) { if(managed(task)) boundary.lock(); }
    public void serializePlan(String plan) { boundary.lock(); }
    public void advanceAfterCommit(String plan) { boundary.completionScope(()->{ executionService.getObject().drainGuardedCompletions(plan);return null; }); }
    public boolean mayAdvance(String task) {
        var row=policyRow(String.valueOf(contextRow(task).get("plan_id")));
        return active(row) && !AuthorityRepository.flag(row.get("paused")) && ledger.admits(policy(row));
    }
    /** Same admission rule for graph control nodes that have no physical Task yet. */
    public boolean mayAdvancePlan(String plan) {
        boundary.lock();var row=policyRow(plan);
        return active(row) && !AuthorityRepository.flag(row.get("paused")) && ledger.admits(policy(row));
    }
    public void deferCompletion(String task,String attempt,me.asu.aidev.execution.domain.WorkspaceResult workspace,me.asu.aidev.execution.domain.CompletionReport report) {
        repo.jdbc().update("INSERT INTO execution_completion(attempt_id,task_id,workspace_json,report_json) VALUES(?,?,?,?) ON CONFLICT(attempt_id) DO NOTHING",attempt,task,workspace==null?null:repo.encode(workspace),report==null?null:repo.encode(report));
    }
    public void deferFailure(String task,String attempt,String reason) {
        repo.jdbc().update("INSERT INTO execution_completion(attempt_id,task_id,event_type,failure_reason) VALUES(?,?,'FAILED',?) ON CONFLICT(attempt_id) DO NOTHING",attempt,task,reason==null?null:reason.substring(0,Math.min(reason.length(),4096)));
    }
    public List<Map<String,Object>> completions(String plan) { return repo.jdbc().queryForList("SELECT c.* FROM execution_completion c JOIN execution_context x ON x.task_id=c.task_id WHERE x.plan_id=? AND c.applied=FALSE ORDER BY c.task_id",plan); }
    public void completionApplied(String attempt) { repo.jdbc().update("UPDATE execution_completion SET applied=TRUE WHERE attempt_id=? AND applied=FALSE",attempt); }
    public boolean latestAttempt(String task,String attempt) { return attempt.equals(repo.jdbc().queryForObject("SELECT id FROM execution_attempt WHERE task_id=? ORDER BY attempt_no DESC LIMIT 1",String.class,task)); }
    public <T> T decode(String json,Class<T> type) { return json==null?null:repo.decode(json,type); }
    private boolean active(Map<String,Object> row) { return !AuthorityRepository.flag(row.get("revoked")) && number(row,"expires_ms")>System.currentTimeMillis()
            && !humanSecurity.currentDelegationAuthority(repo.actor(String.valueOf(row.get("owner_id"))),"A05").isBlank(); }
    public void verifyLease(String task,Lease lease,boolean running,boolean live) {
        var rows=repo.jdbc().queryForList("""
                SELECT t.state,t.current_worker_id,t.lease_token,t.lease_expires_at_epoch_ms,a.id,a.state AS attempt_state,
                a.stop_requested_at_epoch_ms FROM work_task t JOIN execution_attempt a ON a.task_id=t.id
                WHERE t.id=? AND a.id=? AND a.worker_id=? AND a.lease_token=?
                """,task,lease.attemptId(),lease.workerId(),lease.leaseToken());
        if(rows.size()!=1) throw new SecurityException("Exact owned task/attempt/Lease required");var row=rows.getFirst();
        if(!Objects.equals(row.get("current_worker_id"),lease.workerId()) || !Objects.equals(row.get("lease_token"),lease.leaseToken())
                || live && number(row,"lease_expires_at_epoch_ms")<=System.currentTimeMillis()
                || running && (!"RUNNING".equals(row.get("state")) || !"RUNNING".equals(row.get("attempt_state")) || row.get("stop_requested_at_epoch_ms")!=null))
            throw new SecurityException("Current owned execution Lease required");
    }
    @Transactional public Context read(String task,Lease lease) { boundary.lock();verifyLease(task,lease,false,true);return context(task); }
    @Transactional public Receipt begin(String task,Begin input) {
        boundary.lock();if(!boundary.instance().equals(input.platformInstanceId())) throw new SecurityException("Platform instance mismatch");
        verifyLease(task,input.lease(),true,true);
        var previous=repo.jdbc().queryForList("SELECT * FROM execution_operation WHERE operation_id=?",input.operationId());
        String digest=AuthorityRepository.hash(repo.encode(input));
        if(!previous.isEmpty()) {
            var op=previous.getFirst();if(!Objects.equals(op.get("request_digest"),digest) || !Objects.equals(op.get("task_id"),task)) throw new IllegalStateException("OPERATION_ID_CONFLICT");
            return receipt(op,false,"QUERY_ORIGINAL_OUTCOME_ONLY");
        }
        var c=contextRow(task);var row=policyRow(String.valueOf(c.get("plan_id")));var original=repo.decode(String.valueOf(c.get("policy_json")),Policy.class);var current=policy(row);
        boolean allowed=active(row) && number(c,"generation")==number(row,"generation") && input.expectedGrantRevision()==current.revision()
                && !uncertain(task,input.lease().attemptId()) && unchanged(task,c) && permits(original,input) && permits(current,input);
        if(input.tool()==Tool.COMMAND) allowed=allowed && original.commands().stream().anyMatch(cmd->cmd.id().equals(input.resource()) && current.commands().contains(cmd));
        if(repo.jdbc().queryForObject("SELECT COUNT(*) FROM execution_meter WHERE attempt_id=? AND settled=FALSE",Long.class,input.lease().attemptId())!=1) allowed=false;
        repo.jdbc().update("""
                INSERT INTO execution_operation(operation_id,task_id,attempt_id,worker_id,lease_token,plan_id,request_digest,tool_id,repository_ref,resource,state,started_ms)
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?)
                """,input.operationId(),task,input.lease().attemptId(),input.lease().workerId(),input.lease().leaseToken(),current.planId(),digest,input.tool().name(),input.repositoryRef(),input.resource(),allowed?"STARTED":"REJECTED",System.currentTimeMillis());
        return new Receipt(PROTOCOL,input.operationId(),allowed?"STARTED":"REJECTED",allowed,allowed?"AUTHORIZED_START":"SCOPE_OR_STATE_DENIED");
    }
    private boolean permits(Policy policy,Begin input) {
        return policy.rules().stream().anyMatch(rule->rule.tool()==input.tool() && rule.repositoryRef().equals(input.repositoryRef())
                && (rule.pathPrefix().isEmpty() || rule.pathPrefix().equals(".") || input.resource().equals(rule.pathPrefix()) || input.resource().startsWith(rule.pathPrefix()+"/")));
    }
    public boolean unknownTask(String task) { return repo.jdbc().queryForObject("SELECT COUNT(*) FROM execution_operation WHERE task_id=? AND state IN ('STARTED','UNKNOWN')",Long.class,task)>0; }
    private boolean uncertain(String task,String attempt) {
        return repo.jdbc().queryForObject("SELECT COUNT(*) FROM execution_operation WHERE task_id=? AND (state='UNKNOWN' OR (state='STARTED' AND attempt_id<>?))",Long.class,task,attempt)>0;
    }
    @Transactional public Receipt finish(String task,Finish input) {
        boundary.lock();var op=ownedOperation(task,input.lease(),input.operationId());String digest=AuthorityRepository.hash(repo.encode(input));
        if(!"STARTED".equals(op.get("state"))) {
            if(!Objects.equals(op.get("finish_digest"),digest)) throw new IllegalStateException("OPERATION_OUTCOME_CONFLICT");return receipt(op,false,"RECORDED");
        }
        repo.jdbc().update("UPDATE execution_operation SET state=?,finished_ms=?,finish_digest=?,result_digest=?,evidence_ref=? WHERE operation_id=? AND state='STARTED'",
                input.state(),System.currentTimeMillis(),digest,input.resultDigest(),input.evidenceRef(),input.operationId());
        if("MODEL".equals(op.get("tool_id"))) ledger.usage(input.lease().attemptId(),input.usage(),input.usage()==null || input.state().equals("UNKNOWN"));
        return new Receipt(PROTOCOL,input.operationId(),input.state(),false,"RECORDED");
    }
    private Map<String,Object> ownedOperation(String task,Lease lease,String operation) {
        var op=repo.jdbc().queryForMap("SELECT * FROM execution_operation WHERE operation_id=?",operation);
        if(!Objects.equals(op.get("task_id"),task) || !Objects.equals(op.get("attempt_id"),lease.attemptId())
                || !Objects.equals(op.get("worker_id"),lease.workerId()) || !Objects.equals(op.get("lease_token"),lease.leaseToken())) throw new SecurityException("Original operation owner required");
        return op;
    }
    @Transactional public Receipt query(String task,Lease lease,String operation) { return receipt(ownedOperation(task,lease,operation),false,"QUERY_ONLY"); }
    private Receipt receipt(Map<String,Object> op,boolean execute,String code) { return new Receipt(PROTOCOL,String.valueOf(op.get("operation_id")),String.valueOf(op.get("state")),execute,code); }
    @Transactional public Object reconcile(Reconcile input) {
        boundary.lock();var op=repo.jdbc().queryForMap("SELECT * FROM execution_operation WHERE operation_id=?",input.operationId());
        owner(String.valueOf(policyRow(String.valueOf(op.get("plan_id"))).get("owner_id")));
        String json=repo.encode(input);
        if("RECONCILED".equals(op.get("state"))) { if(!json.equals(op.get("reconciliation_json"))) throw new IllegalStateException("RECONCILIATION_CONFLICT");return receipt(op,false,"NO_AUTOMATIC_REPLAY"); }
        if(!Set.of("STARTED","UNKNOWN").contains(op.get("state"))) throw new IllegalStateException("Only unknown effects require reconciliation");
        repo.jdbc().update("UPDATE execution_operation SET state='RECONCILED',reconciliation_json=?,finished_ms=? WHERE operation_id=?",json,System.currentTimeMillis(),input.operationId());
        repo.jdbc().update("UPDATE execution_meter SET time_unknown=TRUE WHERE attempt_id=? AND settled=FALSE",op.get("attempt_id"));
        repo.audit("PLAN:"+op.get("plan_id"),null,input.operationId(),boundary.human(),"EXECUTION_EFFECT_RECONCILED",AuthorityRepository.hash(json));
        return Map.of("state","RECONCILED","replay",false,"budgetExposureRetained",true);
    }
    /** Guarded lost leases remain occupied and non-replayable until actual quiescence is confirmed. */
    public boolean retainExpired(String task,String attempt) {
        if(!managed(task)) return false;boundary.lock();
        repo.jdbc().update("UPDATE execution_operation SET state='UNKNOWN' WHERE attempt_id=? AND state='STARTED'",attempt);
        // No operation can start before RUNNING. An unstarted claim holds its reservation,
        // but confirmed cancellation can settle its known zero execution without inventing usage.
        repo.jdbc().update("UPDATE execution_meter SET usage_unknown=TRUE,time_unknown=TRUE WHERE attempt_id=? AND settled=FALSE AND started_ms IS NOT NULL",attempt);
        return true;
    }
    public void finishing(String task,Lease lease,boolean success) {
        if(!managed(task)) return;boundary.lock();
        if(unknownTask(task)) throw new IllegalStateException("UNKNOWN_EFFECT_OR_IN_FLIGHT_OPERATION_RETAINS_RESOURCES");
        if(success && repo.jdbc().queryForObject("SELECT COUNT(*) FROM execution_delivery WHERE task_id=? AND attempt_id=?",Long.class,task,lease.attemptId())!=1)
            throw new IllegalStateException("Formal delivery with actual/unrun checks required");
        ledger.settle(lease.attemptId(),System.currentTimeMillis());
    }
    public void validateResult(String task,Lease lease,me.asu.aidev.execution.domain.WorkspaceResult result) {
        if(!managed(task)) return;
        var value=repo.jdbc().queryForObject("SELECT delivery_json FROM execution_delivery WHERE attempt_id=? AND task_id=?",String.class,lease.attemptId(),task);
        var delivery=repo.decode(value,Delivery.class);
        if(result==null || !Objects.equals(delivery.resultRevision(),result.resultRevision()) || !new HashSet<>(delivery.changes()).equals(new HashSet<>(result.changedFiles())))
            throw new IllegalStateException("Formal delivery and actual Git result differ");
    }
    public boolean completed(String task,Lease lease,me.asu.aidev.execution.domain.WorkspaceResult result,me.asu.aidev.execution.domain.CompletionReport report) {
        if(!managed(task)) return false;boundary.lock();
        if(repo.jdbc().queryForObject("SELECT COUNT(*) FROM execution_attempt WHERE id=? AND task_id=? AND worker_id=? AND lease_token=? AND state='COMPLETED'",Long.class,lease.attemptId(),task,lease.workerId(),lease.leaseToken())!=1) return false;
        var row=repo.jdbc().queryForMap("SELECT workspace_json,report_json FROM execution_completion WHERE attempt_id=? AND task_id=?",lease.attemptId(),task);
        if(!Objects.equals(result,decode((String)row.get("workspace_json"),me.asu.aidev.execution.domain.WorkspaceResult.class))
                || !Objects.equals(report,decode((String)row.get("report_json"),me.asu.aidev.execution.domain.CompletionReport.class))) throw new IllegalStateException("COMPLETION_RECEIPT_CONFLICT");
        return true;
    }
    @Transactional public Object delivery(String task,Lease lease,Delivery input) {
        boundary.lock();verifyLease(task,lease,true,true);var c=context(task);
        if(!Objects.equals(input.baseline(),c.snapshot().task().baseRevision()) || !input.sources().containsAll(c.snapshot().references())) throw new IllegalArgumentException("Delivery must identify fixed sources and baseline");
        if(input.resultRevision()!=null && !input.resultRevision().matches("[0-9a-f]{40}|[0-9a-f]{64}")) throw new IllegalArgumentException("Immutable result revision required");
        String json=repo.encode(input);if(json.length()>65536) throw new IllegalArgumentException("Delivery exceeds limit");String digest=AuthorityRepository.hash(json);
        var previous=repo.jdbc().queryForList("SELECT digest FROM execution_delivery WHERE attempt_id=?",String.class,lease.attemptId());
        if(!previous.isEmpty()) { if(!previous.getFirst().equals(digest)) throw new IllegalStateException("DELIVERY_CONFLICT"); }
        else repo.jdbc().update("INSERT INTO execution_delivery VALUES(?,?,?,?,?)",task,lease.attemptId(),digest,json,System.currentTimeMillis());
        return Map.of("digest",digest,"state","RECORDED");
    }
}
