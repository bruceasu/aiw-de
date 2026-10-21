package me.asu.aidev.authority;

import java.util.*;
import me.asu.aidev.execution.port.ExecutionAttemptRepository;
import me.asu.aidev.webconsole.security.WebConsoleSecurityService;
import me.asu.aidev.work.domain.*;
import me.asu.aidev.work.port.WorkTaskRepository;
import me.asu.aidev.workflow.application.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Existing bounded Plan coordination, requested by the independent private process. */
@Service
public class CoordinationService {
    private final AuthorityRepository repo;private final AuthorityBoundary boundary;private final AuthorityGrantService grants;
    private final ActionAdmissionService receipts;private final ManagedPlanAdmission manager;private final WorkflowPlanService plans;
    private final WorkflowStepCompletedHandler completion;private final WorkTaskRepository tasks;private final ExecutionAttemptRepository attempts;
    private final WebConsoleSecurityService security;
    public CoordinationService(AuthorityRepository repo,AuthorityBoundary boundary,AuthorityGrantService grants,ActionAdmissionService receipts,
            ManagedPlanAdmission manager,WorkflowPlanService plans,WorkflowStepCompletedHandler completion,WorkTaskRepository tasks,
            ExecutionAttemptRepository attempts,WebConsoleSecurityService security) {
        this.repo=repo;this.boundary=boundary;this.grants=grants;this.receipts=receipts;this.manager=manager;this.plans=plans;
        this.completion=completion;this.tasks=tasks;this.attempts=attempts;this.security=security;
    }
    public record Advance(String requestId,String planId,long expectedVersion,String specDigest,long expectedTargetGeneration,long restrictionRevision,String sourceTask,String sourceAttempt) {}
    @Transactional public void adopt(String owner,String worker,String plan,long version,String specDigest,long expires,String actor) {
        boundary.lock();boundary.requireMigrationSafeInstallation();grants.owns(owner,actor);grants.human(actor,"A05");
        boolean ownedPublication=repo.jdbc.queryForObject("SELECT COUNT(*) FROM authority_publication_plan p JOIN authority_target t ON t.target_key=p.target_key WHERE p.plan_id=? AND t.owner_id=?",Long.class,plan,owner)>0;
        if(expires<=System.currentTimeMillis() || plans.get(plan).version()!=version || !plans.get(plan).createdBy().equals(actor) && !ownedPublication
                || !AuthorityRepository.hash(repo.encode(plans.get(plan).spec())).equals(specDigest)
                || repo.jdbc.queryForObject("SELECT COUNT(*) FROM authority_identity WHERE owner_id=? AND worker_id=?",Long.class,owner,worker)!=1)
            throw new IllegalStateException("Explicit owned clean draft, manager and confirmed spec required");
        grants.bindTarget("PLAN:"+plan,owner);manager.adopt(plan,owner,worker,expires,security.currentOperatorAuthority(actor));
        repo.jdbc.update("INSERT INTO authority_participant VALUES(?,?,'COORDINATOR')",plan,worker);
        repo.audit("PLAN:"+plan,null,null,actor,"MANAGED_PLAN_ADOPTED","Exact business spec approved; no private text retained");
    }
    @Transactional public Map<String,Object> state(AuthorityBoundary.Principal principal,String plan) {
        boundary.lock();receipts.readable(principal,"PLAN:"+plan);var current=plans.get(plan);
        var sources=new ArrayList<Map<String,Object>>();
        for(var task:plans.steps(plan)) if(task.state()==TaskState.COMPLETED) {
            var input=attempts.findCompletedInput(task.id());var report=attempts.findCompletionReport(task.id());
            if(input.isPresent() && report.isPresent() && repo.jdbc.queryForObject("SELECT COUNT(*) FROM authority_coordination_source WHERE plan_id=? AND source_attempt=?",Long.class,plan,input.get().attemptId())==0)
                sources.add(Map.of("taskId",task.id().value(),"attemptId",input.get().attemptId(),"report",report.get()));
        }
        boolean active=plans.steps(plan).stream().anyMatch(task->!Set.of(TaskState.COMPLETED,TaskState.FAILED,TaskState.CANCELLED).contains(task.state()));
        if(active) sources.clear();
        else sources.sort(Comparator.comparingLong((Map<String,Object> row)->tasks.find(new TaskId(String.valueOf(row.get("taskId")))).orElseThrow().updatedAt().toEpochMilli()).reversed());
        if(sources.size()>1) sources.subList(1,sources.size()).clear();
        return Map.of("platformInstanceId",boundary.instance(),"planId",plan,"version",current.version(),"state",current.state().name(),
                "specDigest",AuthorityRepository.hash(repo.encode(current.spec())),"spec",current.spec(),"sources",sources,
                "generation",manager.generation("PLAN:"+plan),"restrictionRevision",manager.restriction("PLAN:"+plan));
    }
    @Transactional public ActionAdmissionService.Receipt advance(AuthorityBoundary.Principal principal,Advance input) {
        boundary.lock();receipts.enabled(principal);boundary.requireMigrationSafeInstallation();UUID.fromString(input.requestId());
        String digest=AuthorityRepository.hash(repo.encode(input));var existing=receipts.existing(principal.owner(),input.requestId());
        if(existing.isPresent()) {
            if(!existing.get().actionDigest().equals(digest)) throw new IllegalStateException("REQUEST_DIGEST_CONFLICT");
            return receipts.receipt(principal,input.requestId());
        }
        var row=repo.jdbc.queryForMap("SELECT * FROM authority_managed_plan WHERE plan_id=?",input.planId());
        if(!Objects.equals(row.get("owner_id"),principal.owner()) || !Objects.equals(row.get("worker_id"),principal.worker())
                || AuthorityRepository.flag(row.get("paused")) || !Objects.equals(row.get("authority"),security.currentOperatorAuthority(repo.actor(principal.owner()))))
            throw new SecurityException("Current owner management authority required");
        var plan=plans.get(input.planId());
        if(repo.jdbc.queryForObject("SELECT COUNT(*) FROM authority_task p JOIN work_task t ON t.id=p.task_id WHERE t.subject_id=? AND (p.state='UNKNOWN' OR (p.state='IN_PROGRESS' AND t.state<>'COMPLETED'))",Long.class,plan.id())>0)
            throw new IllegalStateException("Unconfirmed effects block new coordination until reconciliation");
        if(plan.version()!=input.expectedVersion() || !Objects.equals(row.get("spec_digest"),input.specDigest())
                || !AuthorityRepository.hash(repo.encode(plan.spec())).equals(input.specDigest())
                || manager.generation("PLAN:"+plan.id())!=input.expectedTargetGeneration()
                || manager.restriction("PLAN:"+plan.id())!=input.restrictionRevision()) throw new IllegalStateException("PLAN_SNAPSHOT_CHANGED");
        String source=input.sourceTask()==null?"START":input.sourceAttempt();
        if(source==null || repo.jdbc.queryForObject("SELECT COUNT(*) FROM authority_coordination_source WHERE plan_id=? AND source_attempt=?",Long.class,plan.id(),source)>0)
            throw new IllegalStateException("Source coordination was already consumed or is ambiguous");
        if(input.sourceTask()!=null) {
            var eligible=state(principal,plan.id()).get("sources");
            if(!(eligible instanceof List<?> list) || list.stream().noneMatch(value->value instanceof Map<?,?> entry && Objects.equals(entry.get("taskId"),input.sourceTask()) && Objects.equals(entry.get("attemptId"),input.sourceAttempt())))
                throw new IllegalStateException("Only the current eligible completed source may advance");
        }
        var generated=boundary.as(principal,()->manager.delegatedAdmission(plan.id(),principal.worker(),()->{
            if(input.sourceTask()==null) { plans.confirm(plan.id(),plan.version(),"personal:"+principal.worker());return plans.steps(plan.id()).stream().map(WorkTask::id).toList(); }
            var task=tasks.find(new TaskId(input.sourceTask())).orElseThrow();
            var evidence=attempts.findCompletedInput(task.id()).orElseThrow();
            if(!task.subjectId().equals(plan.id()) || task.state()!=TaskState.COMPLETED || !Objects.equals(evidence.attemptId(),input.sourceAttempt()))
                throw new IllegalStateException("Original completed source attempt required");
            return completion.advanceManaged(task,attempts.findCompletionReport(task.id()).orElseThrow());
        }));
        repo.jdbc.update("INSERT INTO authority_coordination_source VALUES(?,?,?)",plan.id(),source,input.requestId());
        for(var task:generated) {
            repo.jdbc.update("UPDATE authority_task SET post_version=?,request_id=? WHERE task_id=? AND started=FALSE AND request_id IS NULL",plans.get(plan.id()).version(),input.requestId(),task.value());
            if(input.sourceTask()!=null) {
                var parents=repo.jdbc.queryForList("SELECT * FROM authority_task WHERE task_id=? AND grant_id IS NOT NULL AND grant_id<>'HUMAN'",input.sourceTask());
                if(!parents.isEmpty()) {
                    var parent=parents.getFirst();var scope=repo.get(String.valueOf(parent.get("grant_id"))).rule();
                    var child=tasks.find(task).orElseThrow();var at=me.asu.aidev.workflow.domain.WorkflowTaskPosition.parse(plan,task).orElseThrow();
                    if(at.review() && Set.of(ActionScope.Action.A01,ActionScope.Action.A12_EXECUTE).contains(scope.action()))
                        repo.jdbc.update("UPDATE authority_task SET target_key=?,grant_id=?,grant_revision=?,worker_generation=?,enabled_boot=?,target_generation=?,restriction_revision=? WHERE task_id=? AND started=FALSE",
                                parent.get("target_key"),parent.get("grant_id"),parent.get("grant_revision"),principal.generation(),boundary.boot(),parent.get("target_generation"),parent.get("restriction_revision"),child.id().value());
                }
            }
        }
        repo.jdbc.update("UPDATE authority_managed_plan SET version=? WHERE plan_id=?",plans.get(plan.id()).version(),plan.id());
        boolean finished=false;
        if(input.sourceTask()!=null && generated.isEmpty()) {
            var task=tasks.find(new TaskId(input.sourceTask())).orElseThrow();
            var at=me.asu.aidev.workflow.domain.WorkflowTaskPosition.parse(plan,task.id()).orElseThrow();
            finished=at.review() && at.step()==plan.spec().steps().size()-1
                    && attempts.findCompletionReport(task.id()).orElseThrow().verdict()==me.asu.aidev.execution.domain.CompletionVerdict.PASS;
        }
        return receipts.save(principal,input.requestId(),digest,"MANAGE:"+plan.id(),1,generated.isEmpty()?"APPLIED":"ADMITTED",Map.of("taskIds",generated.stream().map(TaskId::value).toList(),"finished",finished));
    }
}
