package me.asu.aidev.execution.config;

import java.util.*;
import me.asu.aidev.authority.*;
import me.asu.aidev.execution.port.ExecutionAttemptRepository;
import me.asu.aidev.work.domain.TaskId;
import me.asu.aidev.work.port.WorkTaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static me.asu.aidev.execution.config.ConfigurationContract.*;
import static me.asu.aidev.execution.guard.GuardContract.text;

/** Machine preparation uses the current owner's existing policy and exact managed scope. */
@Service
public class PersonalTaskPreparations {
    public record Input(String requestId,String planId,String repositoryRef,String baseRevision,
                        String role,String aiProfileId,String instruction) {
        public Input {
            if(requestId==null || !UUID.fromString(requestId).toString().equals(requestId))
                throw new IllegalArgumentException("Canonical request ID required");
            text(planId,128);text(repositoryRef,120);text(baseRevision,64);text(role,96);
            text(aiProfileId,128);text(instruction,16000);
        }
    }
    private final AuthorityRepository repo;
    private final AuthorityBoundary boundary;
    private final ActionAdmissionService admission;
    private final ManagedPlanAdmission manager;
    private final ConfigurationUiOptions options;
    private final ExecutionConfigurations configurations;
    private final AgentFactory factory;
    private final WorkTaskRepository tasks;
    private final ExecutionAttemptRepository attempts;

    public PersonalTaskPreparations(AuthorityRepository repo,AuthorityBoundary boundary,ActionAdmissionService admission,
            ManagedPlanAdmission manager,ConfigurationUiOptions options,ExecutionConfigurations configurations,
            AgentFactory factory,WorkTaskRepository tasks,ExecutionAttemptRepository attempts) {
        this.repo=repo;this.boundary=boundary;this.admission=admission;this.manager=manager;this.options=options;
        this.configurations=configurations;this.factory=factory;this.tasks=tasks;this.attempts=attempts;
    }
    private String intentId(AuthorityBoundary.Principal principal,String requestId) {
        if(!UUID.fromString(requestId).toString().equals(requestId)) throw new IllegalArgumentException("Canonical request ID required");
        return "personal-"+AuthorityRepository.hash(principal.worker()+":"+requestId);
    }
    private boolean visible(AuthorityBoundary.Principal principal,String plan) {
        try {
            admission.readable(principal,"PLAN:"+plan);
            return repo.jdbc().queryForObject("SELECT COUNT(*) FROM authority_managed_plan WHERE plan_id=? AND owner_id=? AND worker_id=?",Long.class,
                    plan,principal.owner(),principal.worker())==1;
        } catch(SecurityException failure) { return false; }
    }
    @SuppressWarnings("unchecked")
    private List<ConfigurationUiOptions.PlanChoice> choices(AuthorityBoundary.Principal principal) {
        return ((List<ConfigurationUiOptions.PlanChoice>)options.snapshot(principal.owner()).get("plans")).stream()
                .filter(plan->visible(principal,plan.id())).toList();
    }
    @Transactional public Map<String,Object> options(AuthorityBoundary.Principal principal) {
        boundary.lock();admission.enabled(principal);
        return Map.of("platformInstanceId",boundary.instance(),"ownerId",principal.owner(),"workerId",principal.worker(),
                "plans",choices(principal),"models",factory.supportedProfiles(),"methods",configurations.catalogue().methods());
    }
    @Transactional public Map<String,Object> prepare(AuthorityBoundary.Principal principal,Input input) {
        boundary.lock();admission.enabled(principal);boundary.requireMigrationSafeInstallation();
        var plan=choices(principal).stream().filter(p->p.id().equals(input.planId())).findFirst()
                .orElseThrow(()->new SecurityException("Explicit managed Plan and read scope required"));
        if(plan.paused() || plan.revoked() || plan.expiresAt()<=System.currentTimeMillis())
            throw new SecurityException("Current active execution policy required");
        String target="PLAN:"+plan.id();
        if(repo.jdbc().queryForObject("SELECT COUNT(*) FROM authority_target WHERE target_key=? AND paused=TRUE",Long.class,target)>0
                || repo.jdbc().queryForObject("SELECT COUNT(*) FROM authority_restriction WHERE active=TRUE AND (target_key=? OR substr(target_key,1,?)=?)",
                    Long.class,target,(target+":").length(),target+":")>0)
            throw new SecurityException("Target paused or explicitly restricted");
        var repository=plan.repositories().stream().filter(r->r.ref().equals(input.repositoryRef())).findFirst()
                .orElseThrow(()->new SecurityException("Repository outside confirmed scope"));
        if(!repository.baselines().contains(input.baseRevision())) throw new IllegalArgumentException("Choose an existing immutable repository baseline");
        var model=factory.supportedProfiles().stream().filter(p->p.id().equals(input.aiProfileId())).findFirst()
                .orElseThrow(()->new IllegalArgumentException("Approved model required"));
        var intent=new Intent(intentId(principal,input.requestId()),1,0,plan.id(),plan.policyRevision(),repository.ref(),input.baseRevision(),
                input.instruction(),List.of(),model.profile().runtime(),model.id(),input.role(),null,null);
        boundary.as(principal,()->manager.delegatedAdmission(plan.id(),principal.worker(),()->factory.prepareAuthorized(intent,principal.owner())));
        repo.audit("PLAN:"+plan.id(),principal.worker(),input.requestId(),"personal-worker","PROFESSIONAL_TASK_PREPARED",intent.intentId());
        return read(principal,input.requestId());
    }
    @Transactional public Map<String,Object> read(AuthorityBoundary.Principal principal,String requestId) {
        boundary.lock();admission.enabled(principal);
        String id=intentId(principal,requestId);
        var preparation=factory.preparations(principal.owner()).stream().filter(p->p.intentId().equals(id)).findFirst()
                .orElseThrow(()->new NoSuchElementException("Original preparation not found"));
        String raw=repo.jdbc().queryForObject("SELECT intent_json FROM execution_preparation WHERE owner_id=? AND intent_id=? AND intent_version=1",
                String.class,principal.owner(),id);
        var intent=repo.decode(raw,Intent.class);
        if(!visible(principal,intent.planId())) throw new SecurityException("Plan no longer readable by this secretary");
        var result=new LinkedHashMap<String,Object>();
        result.put("requestId",requestId);result.put("platformInstanceId",boundary.instance());
        result.put("ownerId",principal.owner());result.put("workerId",principal.worker());result.put("planId",intent.planId());
        result.put("preparation",preparation);
        if(preparation.taskId()!=null) {
            var task=tasks.find(new TaskId(preparation.taskId())).orElseThrow();
            result.put("taskState",task.state().name());
            attempts.findCompletionReport(task.id()).ifPresent(report->result.put("report",report));
        }
        return result;
    }
}
