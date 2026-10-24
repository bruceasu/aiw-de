package me.asu.aidev.authority;

import java.util.*;
import me.asu.aidev.workflow.application.WorkflowPlanService;
import me.asu.aidev.work.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.dao.DataAccessException;

/** Deduplication, authority and database effects commit atomically. External work uses fixed permits. */
@Service
public class ActionAdmissionService {
    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.beans.factory.ObjectProvider<me.asu.aidev.questions.PersonalQuestions> personalQuestions;
    private final AuthorityRepository repo;
    private final AuthorityBoundary boundary;
    private final AuthorityGrantService grants;
    private final AuthorityActions actions;
    private final AuthorityBudgets budgets;
    private final ManagedPlanAdmission manager;
    private final WorkflowPlanService plans;
    private final TransactionTemplate transaction;
    public ActionAdmissionService(AuthorityRepository repo,AuthorityBoundary boundary,AuthorityGrantService grants,
            AuthorityActions actions,AuthorityBudgets budgets,ManagedPlanAdmission manager,WorkflowPlanService plans,PlatformTransactionManager tx) {
        this.repo=repo;this.boundary=boundary;this.grants=grants;this.actions=actions;this.budgets=budgets;this.manager=manager;this.plans=plans;
        this.transaction=new TransactionTemplate(tx);
    }
    public record Envelope(int protocolVersion,String requestId,String grantId,long grantRevision,long restrictionRevision,
            ActionScope.Action action,String targetId,Integer step,long expectedTargetGeneration,String expectedSnapshot,
            Long expectedVersion,Long expectedTaskVersion,String taskId,Long attempt,Map<String,String> parameters,List<String> evidence) {}
    public record Receipt(String requestId,String actionDigest,String state,Object result,long createdAt,long updatedAt) {}
    public void enabled(AuthorityBoundary.Principal principal) {
        grants.identityHuman(repo.actor(principal.owner()));
        if(!principal.enabled()) throw new SecurityException("Personal identity needs current human enablement");
        if(repo.jdbc().queryForObject("SELECT COUNT(*) FROM authority_identity WHERE owner_id=? AND worker_id=? AND generation=? AND enabled_boot=?",Long.class,
                principal.owner(),principal.worker(),principal.generation(),boundary.boot())!=1) throw new SecurityException("Personal identity changed");
    }
    public void readable(AuthorityBoundary.Principal principal,String target) {
        enabled(principal);
        var scope=Arrays.asList(repo.decode(repo.jdbc().queryForObject("SELECT read_scope FROM authority_identity WHERE worker_id=?",String.class,principal.worker()),String[].class));
        if(!scope.contains(target) || repo.jdbc().queryForObject("SELECT COUNT(*) FROM authority_target WHERE target_key=? AND owner_id=?",Long.class,target,principal.owner())!=1)
            throw new SecurityException("Explicit owner/target read scope required");
    }
    public Map<String,Object> state(AuthorityBoundary.Principal principal,String grantId) {
        return transaction.execute(status->{
            boundary.lock();var grant=repo.get(grantId);
            if(!grant.owner().equals(principal.owner()) || !grant.workerId().equals(principal.worker())) throw new SecurityException("Grant not visible");
            String target=actions.key(grant.rule());readable(principal,target);var snapshot=actions.snapshot(grant.rule());
            return Map.of("platformInstanceId",boundary.instance(),"target",target,"snapshot",snapshot,"snapshotDigest",AuthorityRepository.hash(repo.encode(snapshot)),
                    "generation",manager.generation(target),"restrictionRevision",manager.restriction(target),"budget",budgets.current(grant.rule()));
        });
    }
    private String businessDigest(Envelope input) {
        var data=new TreeMap<String,Object>();
        data.put("protocolVersion",input.protocolVersion());data.put("action",input.action());data.put("targetId",input.targetId());data.put("step",input.step());
        data.put("expectedSnapshot",input.expectedSnapshot());data.put("expectedVersion",input.expectedVersion());data.put("expectedTaskVersion",input.expectedTaskVersion());
        data.put("taskId",input.taskId());data.put("attempt",input.attempt());data.put("parameters",input.parameters());data.put("evidence",input.evidence());
        return AuthorityRepository.hash(repo.encode(data));
    }
    public Map<String,Object> targetState(AuthorityBoundary.Principal principal,String target) {
        return transaction.execute(status->{
            boundary.lock();readable(principal,target);
            var scope=repo.grants().stream().filter(g->g.owner().equals(principal.owner()) && g.targetKey().equals(target)).map(AuthorityRepository.Grant::rule).findFirst()
                    .orElseThrow(()->new IllegalArgumentException("No visible target state"));
            return Map.of("target",target,"snapshot",actions.snapshot(scope),"generation",manager.generation(target),"restrictionRevision",manager.restriction(target));
        });
    }
    Optional<Receipt> existing(String owner,String request) {
        String instance=repo.jdbc().queryForObject("SELECT instance_id FROM authority_metadata WHERE id=1",String.class);
        return repo.jdbc().query("SELECT * FROM authority_receipt WHERE instance_id=? AND owner_id=? AND request_id=?",(rs,n)->new Receipt(
                rs.getString("request_id"),rs.getString("digest"),rs.getString("state"),repo.decode(rs.getString("result_json"),Object.class),rs.getLong("created_ms"),rs.getLong("updated_ms")),instance,owner,request).stream().findFirst();
    }
    public Receipt receipt(AuthorityBoundary.Principal principal,String request) {
        enabled(principal);
        var receipt=existing(principal.owner(),request).orElseThrow(()->new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND,"Receipt not visible"));
        String target=repo.jdbc().queryForObject("SELECT target_key FROM authority_receipt WHERE owner_id=? AND request_id=?",String.class,principal.owner(),request);
        readable(principal,target);return receipt;
    }
    Receipt save(AuthorityBoundary.Principal principal,String request,String digest,String grant,long revision,String state,Object result) {
        long now=System.currentTimeMillis();
        String target=grant.startsWith("MANAGE:")?"PLAN:"+grant.substring(7):repo.get(grant).targetKey();
        repo.jdbc().update("INSERT INTO authority_receipt VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",boundary.instance(),principal.owner(),request,digest,
                principal.worker(),grant,revision,target,state,repo.encode(result),now,now);
        return new Receipt(request,digest,state,result,now,now);
    }
    /** Historical accounting cannot freeze unrelated work; live executions still occupy the Plan. */
    public boolean blocksNewExecution(String plan) {
        return repo.jdbc().queryForObject("""
                SELECT COUNT(*) FROM authority_task p JOIN work_task t ON t.id=p.task_id
                WHERE t.subject_id=? AND (
                    (? AND (p.state='UNKNOWN' OR (p.state='IN_PROGRESS' AND t.state<>'COMPLETED')))
                    OR t.state IN ('CLAIMED','RUNNING') OR t.lease_token IS NOT NULL
                    OR EXISTS (SELECT 1 FROM execution_attempt a WHERE a.task_id=t.id AND a.state IN ('CLAIMED','RUNNING')))
                """,Long.class,plan,budgets.enforced())>0;
    }
    public Receipt apply(AuthorityBoundary.Principal principal,Envelope input) {
        if(input.protocolVersion()!=1 || input.action()==null || input.requestId()==null || !UUID.fromString(input.requestId()).toString().equals(input.requestId())
                || input.parameters()==null || input.evidence()==null || input.evidence().size()>32 || input.expectedSnapshot()==null)
            throw new IllegalArgumentException("Exact versioned action envelope required");
        String digest=businessDigest(input);
        try {
            return transaction.execute(status->boundary.as(principal,()->{
                boundary.lock();enabled(principal);
                personalQuestions.getObject().validateEnvelope(principal,input);
                var previous=existing(principal.owner(),input.requestId());
                if(previous.isPresent()) {
                    if(!previous.get().actionDigest().equals(digest)) throw new IllegalStateException("REQUEST_DIGEST_CONFLICT");
                    return receipt(principal,input.requestId());
                }
                boundary.requireMigrationSafeInstallation();
                var grant=repo.get(input.grantId());var scope=grant.rule();
                if(!grant.owner().equals(principal.owner()) || !grant.workerId().equals(principal.worker())) throw new SecurityException("Action grant not visible");
                String target=actions.key(scope);
                if(scope.action()!=input.action() || !scope.targetId().equals(input.targetId()) || !Objects.equals(scope.step(),input.step())
                        || !scope.parameters().equals(input.parameters()) || grant.revision()!=input.grantRevision())
                    throw new IllegalStateException("Action differs from the exact human-confirmed capability");
                var row=repo.jdbc().queryForMap("SELECT * FROM authority_target WHERE target_key=?",target);
                if(AuthorityRepository.flag(row.get("paused")) || manager.generation(target)!=input.expectedTargetGeneration()
                        || manager.restriction(target)!=input.restrictionRevision()) throw new IllegalStateException("TARGET_OR_RESTRICTION_CHANGED");
                actions.admit(scope);var snapshot=actions.snapshot(scope);
                if(!AuthorityRepository.hash(repo.encode(snapshot)).equals(input.expectedSnapshot()) || !grants.permitted(grant,snapshot))
                    throw new IllegalStateException("Current business snapshot or authority changed");
                if(snapshot.get("version") instanceof Number version && (input.expectedVersion()==null || version.longValue()!=input.expectedVersion()))
                    throw new IllegalStateException("EXPECTED_VERSION_CHANGED");
                if(Set.of(ActionScope.Action.A01,ActionScope.Action.A03,ActionScope.Action.A04,ActionScope.Action.A05,ActionScope.Action.A06,ActionScope.Action.A12_EXECUTE).contains(scope.action())
                        && blocksNewExecution(scope.targetId()))
                    throw new IllegalStateException("Existing live execution or enforced reconciliation blocks new admission");
                if(scope.action()==ActionScope.Action.A03 && ("CURRENT".equals(scope.parameters().get("taskId")) || !Objects.equals(input.taskId(),scope.parameters().get("taskId"))))
                    throw new IllegalArgumentException("Retry requires the exact task identity");
                if(input.taskId()!=null) {
                    var task=repo.jdbc().queryForMap("SELECT attempt_no,version FROM work_task WHERE id=?",input.taskId());
                    if(input.expectedTaskVersion()==null || ((Number)task.get("version")).longValue()!=input.expectedTaskVersion()
                            || input.attempt()==null || ((Number)task.get("attempt_no")).longValue()+1!=input.attempt())
                        throw new IllegalStateException("FIXED_ATTEMPT_CHANGED");
                    if(repo.jdbc().queryForObject("SELECT COUNT(*) FROM authority_task WHERE task_id=? AND state='UNKNOWN'",Long.class,input.taskId())>0)
                        throw new IllegalStateException("UNKNOWN effects require human reconciliation, not replay");
                    for(var permit:repo.jdbc().queryForList("SELECT * FROM authority_task WHERE task_id=? AND started=TRUE",input.taskId()))
                        repo.jdbc().update("INSERT INTO authority_effect_history VALUES(?,?,?) ON CONFLICT(task_id,attempt) DO NOTHING",input.taskId(),permit.get("allowed_attempt"),repo.encode(permit));
                }
                budgets.consume(grant,input.requestId(),scope.action()==ActionScope.Action.A01);
                Set<String> previousTasks=new HashSet<>();
                if(!repo.jdbc().queryForList("SELECT id FROM flexible_workflow_plan WHERE id=?",String.class,scope.targetId()).isEmpty())
                    plans.steps(scope.targetId()).forEach(t->previousTasks.add(t.id().value()));
                Object result=actions.execute(scope,"personal:"+principal.worker(),grant.id(),grant.revision(),input.requestId());
                bindPermits(principal,grant,input.requestId(),previousTasks);
                boolean admitted=repo.jdbc().queryForObject("SELECT COUNT(*) FROM authority_task p JOIN authority_target t ON t.target_key=p.target_key WHERE p.request_id=? AND t.owner_id=?",Long.class,input.requestId(),principal.owner())>0;
                repo.audit(target,grant.id(),input.requestId(),"personal:"+principal.worker(),"ACTION_ADMITTED",scope.action().name()+"; fixed business parameters only");
                return save(principal,input.requestId(),digest,grant.id(),grant.revision(),admitted?"ADMITTED":"APPLIED",Map.of("value",result==null?"Applied":result));
            }));
        } catch(DataAccessException error) {
            // Commit acknowledgement may itself be lost. Never invent a failure/refund here.
            throw new IllegalStateException("COMMIT_OUTCOME_UNCONFIRMED; query the original requestId");
        } catch(IllegalStateException | IllegalArgumentException error) {
            return transaction.execute(status->{
                boundary.lock();enabled(principal);var previous=existing(principal.owner(),input.requestId());
                if(previous.isPresent()) {
                    if(!previous.get().actionDigest().equals(digest)) throw new IllegalStateException("REQUEST_DIGEST_CONFLICT");
                    return receipt(principal,input.requestId());
                }
                var grant=repo.get(input.grantId());
                if(!grant.owner().equals(principal.owner()) || !grant.workerId().equals(principal.worker())) throw new SecurityException("Grant not visible");
                return save(principal,input.requestId(),digest,grant.id(),grant.revision(),"REJECTED",Map.of("code","BUSINESS_REJECTED","message","Current capability, snapshot, budget or original business gate rejected the action"));
            });
        }
    }
    public void bindPermits(AuthorityBoundary.Principal principal,AuthorityRepository.Grant grant,String request,Set<String> previousTasks) {
        if(!Set.of(ActionScope.Action.A01,ActionScope.Action.A03,ActionScope.Action.A04,ActionScope.Action.A05,ActionScope.Action.A06,ActionScope.Action.A12_EXECUTE).contains(grant.rule().action())) return;
        String plan=grant.rule().targetId();
        if(!repo.jdbc().queryForList("SELECT id FROM flexible_workflow_plan WHERE id=?",String.class,plan).isEmpty()) {
            for(var task:plans.steps(plan)) if(Set.of(TaskState.READY,TaskState.BLOCKED).contains(task.state())) {
                if(previousTasks.contains(task.id().value()) && !(grant.rule().action()==ActionScope.Action.A03 && task.id().value().equals(grant.rule().parameters().get("taskId")))
                        && grant.rule().action()!=ActionScope.Action.A12_EXECUTE) continue;
                long attempt=repo.jdbc().queryForObject("SELECT attempt_no+1 FROM work_task WHERE id=?",Long.class,task.id().value());
                String target=actions.key(grant.rule());
                repo.jdbc().update("INSERT INTO authority_task(task_id,target_key,allowed_attempt,reserved,started,settled) VALUES(?,?,?,FALSE,FALSE,FALSE) ON CONFLICT(task_id) DO NOTHING",task.id().value(),target,attempt);
                if(repo.jdbc().update("UPDATE authority_task SET target_key=?,grant_id=?,grant_revision=?,worker_generation=?,enabled_boot=?,target_generation=?,restriction_revision=?,request_id=?,post_version=? WHERE task_id=? AND started=FALSE AND (request_id IS NULL OR allowed_attempt<?)",
                        target,grant.id(),grant.revision(),principal.generation(),boundary.boot(),manager.generation(target),manager.restriction(target),request,plans.get(plan).version(),task.id().value(),attempt)>0)
                    repo.jdbc().update("UPDATE authority_task SET allowed_attempt=?,state='PREPARED' WHERE task_id=?",attempt,task.id().value());
            }
        }
    }
}
