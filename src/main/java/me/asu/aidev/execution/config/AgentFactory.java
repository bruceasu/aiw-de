package me.asu.aidev.execution.config;

import java.util.*;
import me.asu.aidev.authority.*;
import me.asu.aidev.ai.application.AiProfileCatalog;
import me.asu.aidev.execution.guard.*;
import me.asu.aidev.execution.port.ExecutionAttemptRepository;
import me.asu.aidev.work.application.*;
import me.asu.aidev.work.domain.*;
import me.asu.aidev.worker.port.WorkerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static me.asu.aidev.execution.config.ConfigurationContract.*;
import me.asu.aidev.execution.config.ConfigurationContract.Error;

/** Preparation is not an Attempt. No model, process, command or network runs in this transaction. */
@Service
public class AgentFactory {
    private final AuthorityRepository repo;private final AuthorityBoundary boundary;private final ExecutionGuard guard;
    private final ExecutionConfigurations configurations;private final WorkTaskService tasks;
    private final WorkerRepository workers;private final AiProfileCatalog ai;private final ExecutionAttemptRepository attempts;
    @org.springframework.beans.factory.annotation.Autowired private AgentFactorySettings factorySettings;
    @org.springframework.beans.factory.annotation.Autowired private ManagedWorkerProvisioner provisioner;
    @org.springframework.beans.factory.annotation.Autowired private me.asu.aidev.workflow.application.FactoryPlanLifecycle planLifecycle;
    public AgentFactory(AuthorityRepository repo,AuthorityBoundary boundary,ExecutionGuard guard,ExecutionConfigurations configurations,
                        WorkTaskService tasks,WorkerRepository workers,AiProfileCatalog ai,ExecutionAttemptRepository attempts) {
        this.repo=repo;this.boundary=boundary;this.guard=guard;this.configurations=configurations;this.tasks=tasks;this.workers=workers;this.ai=ai;this.attempts=attempts;
    }
    public record Details(List<String> missing,List<String> recoveryActions) {}
    public List<AiProfileCatalog.NamedProfile> supportedProfiles() {
        return (factorySettings==null?ai.approved(TaskType.EXTERNAL_WORK):factorySettings.profiles()).stream().filter(p->p.profile().provider().equals("openai") && !p.profile().fast()
                && Set.of("openai-agents","codex-app-server").contains(p.profile().runtime())
                && (p.profile().runtime().equals("codex-app-server")?Set.of("none","minimal","low","medium","high","xhigh"):Set.of("none","minimal","low","medium","high")).contains(p.profile().effort())).toList();
    }
    @Transactional public Preparation prepare(Intent intent) {
        boundary.lock();String owner=configurations.currentOwner();return prepareAuthorized(intent,owner);
    }
    /** Called only by a platform plan consumer after its own version/control checks. */
    @Transactional public Preparation prepareAuthorized(Intent intent,String owner) {
        boundary.lock();var policies=repo.jdbc().queryForList("SELECT owner_id FROM execution_policy WHERE plan_id=?",intent.planId());
        if(!policies.isEmpty() && !Objects.equals(policies.getFirst().get("owner_id"),owner)) throw new SecurityException("Preparation belongs to another owner");
        var fixedIntent=new Intent(intent.intentId(),intent.intentVersion(),0,intent.planId(),intent.policyRevision(),intent.repositoryRef(),intent.baseRevision(),intent.instruction(),intent.dependencies(),intent.runtime(),intent.aiProfileId(),intent.defaultRole(),intent.configRef(),intent.candidate());
        String encoded=repo.encode(fixedIntent);
        String digest=AuthorityRepository.hash(encoded);
        var previous=repo.jdbc().queryForList("SELECT * FROM execution_preparation WHERE intent_id=? AND intent_version=?",intent.intentId(),intent.intentVersion());
        if(!previous.isEmpty()) {
            var row=previous.getFirst();if(!Objects.equals(row.get("owner_id"),owner)) throw new SecurityException("Intent owner mismatch");
            String original=String.valueOf(row.get("intent_json"));
            if(!Objects.equals(row.get("intent_digest"),AuthorityRepository.hash(original)) || !repo.decode(original,Intent.class).equals(fixedIntent)) throw new IllegalStateException("SOURCE_CHANGED: use a new intent version");
            if("READY".equals(row.get("state"))) return view(row);
            if(((Number)row.get("generation")).longValue()!=intent.expectedGeneration()) throw new IllegalStateException("Preparation generation changed");
        } else {
            if(intent.expectedGeneration()!=0) throw new IllegalStateException("New preparation generation must be zero");
            Long latest=repo.jdbc().queryForObject("SELECT COALESCE(MAX(intent_version),0) FROM execution_preparation WHERE intent_id=?",Long.class,intent.intentId());
            if(intent.intentVersion()!=latest+1) throw new IllegalStateException("Exact next intent version required");
            repo.jdbc().update("INSERT INTO execution_preparation(intent_id,intent_version,owner_id,generation,state,intent_digest,intent_json,details_json) VALUES(?,?,?,0,'NEEDS_CONFIGURATION',?,?,?)",intent.intentId(),intent.intentVersion(),owner,digest,encoded,repo.encode(new Details(List.of(),List.of())));
        }
        long next=Math.addExact(intent.expectedGeneration(),1);
        if(repo.jdbc().update("UPDATE execution_preparation SET state='PREPARING',generation=?,error_code=NULL WHERE intent_id=? AND intent_version=? AND generation=? AND state IN ('NEEDS_CONFIGURATION','BLOCKED')",next,intent.intentId(),intent.intentVersion(),intent.expectedGeneration())!=1) throw new IllegalStateException("Preparation changed");
        if(policies.isEmpty()) return blocked(intent,Error.AUTHORIZATION_REQUIRED,"Confirm an execution policy for this exact target first",next);
        PreparedConfigRef ref;
        try {
            if(intent.configRef()!=null) { configurations.get(owner,intent.configRef());ref=intent.configRef(); }
            else {
                var candidate=intent.candidate()!=null?intent.candidate():configurations.defaultCandidate(intent.defaultRole(),owner,intent.repositoryRef());
                if(!candidate.ownerId().equals(owner) || !candidate.repositoryRef().equals(intent.repositoryRef())) throw new SecurityException("Configuration access scope differs from intent");
                var reused=configurations.existing(candidate);ref=reused!=null?reused:configurations.saveAuthorized(candidate);
            }
        } catch(IllegalArgumentException failure) { return blocked(intent,Error.INVALID_CONFIGURATION,"Correct the candidate, registered references or output contract",next); }
        repo.jdbc().update("UPDATE execution_preparation SET config_json=? WHERE intent_id=? AND intent_version=? AND generation=?",repo.encode(ref),intent.intentId(),intent.intentVersion(),next);
        var content=repo.decode(configurations.get(owner,ref).contentJson(),Content.class);
        if(!content.candidate().repositoryRef().equals(intent.repositoryRef())) return blocked(intent,Error.INVALID_CONFIGURATION,"Use the registered repository scope",next);
        var approved=supportedProfiles().stream().filter(p->p.id().equals(intent.aiProfileId()) && p.profile().runtime().equals(intent.runtime())).findFirst();
        if(approved.isEmpty()) return blocked(intent,Error.UNSUPPORTED_RUNTIME,"Configure an approved, supported AI profile for the exact requested runtime",next);
        var guidance=new AiProfileCatalog.Guidance(factorySettings==null?ai.version():factorySettings.version(),List.of(approved.get()),null,approved.get().id());
        var binding=new Binding(owner,ref,intent.runtime(),guidance);
        var capabilities=ConfigurationContract.requiredCapabilities(intent.repositoryRef());
        boolean available=repo.jdbc().queryForList("SELECT id FROM worker WHERE status='ACTIVE' AND last_heartbeat_at_epoch_ms>=?",String.class,java.time.Instant.now().minus(me.asu.aidev.worker.domain.WorkerLiveness.HEARTBEAT_WINDOW).toEpochMilli()).stream()
                .map(id->workers.find(new me.asu.aidev.worker.domain.WorkerId(id)).orElseThrow())
                .anyMatch(worker->worker.supports(capabilities) && configurations.compatible(worker,binding));
        GuardContract.Policy policy;
        try { policy=guard.preparationPolicy(intent.planId(),intent.policyRevision()); }
        catch(SecurityException failure) { return blocked(intent,Error.AUTHORIZATION_REQUIRED,"Confirm or resume the exact active execution policy",next); }
        catch(IllegalStateException failure) { return blocked(intent,Error.SOURCE_CHANGED,"Use a new intent version with the current confirmed policy",next); }
        if(!guard.preparationBudget(policy)) return blocked(intent,Error.BUDGET_CONFIGURATION_REQUIRED,"Configure required limits/pricing or obtain an explicit budget increment",next);
        for(var tool:ref.toolRequirements()) {
            boolean permitted=policy.rules().stream().anyMatch(rule->rule.tool()==tool && rule.repositoryRef().equals(intent.repositoryRef())
                    && (rule.baseRevision()==null || rule.baseRevision().equals(intent.baseRevision()))
                    && (!Set.of(GuardContract.Tool.GIT,GuardContract.Tool.MODEL).contains(tool) || Set.of("", ".").contains(rule.pathPrefix())));
            if(!permitted || tool==GuardContract.Tool.COMMAND && policy.commands().isEmpty()) return blocked(intent,Error.AUTHORIZATION_REQUIRED,"Obtain explicit "+tool+" permission and any required command profile",next);
        }
        if(intent.dependencies().stream().anyMatch(task->attempts.findCompletedInput(new TaskId(task)).isEmpty())) return blocked(intent,Error.SOURCE_CHANGED,"Wait for exact successful upstream results before preparing this task",next);
        planLifecycle.confirm(intent,owner,ref,content.candidate().method());
        if(!available) {
            var provisioning=provisioner==null?null:provisioner.request(intent.repositoryRef(),intent.runtime(),ref.toolRequirements().contains(GuardContract.Tool.COMMAND));
            return blocked(intent,provisioning!=null&&provisioning.starting()?Error.WORKER_STARTING:Error.NO_COMPATIBLE_WORKER,
                    provisioning==null?"Connect a compatible Worker for this repository and runtime":provisioning.message(),next);
        }
        String taskId="CF-"+AuthorityRepository.hash(owner+":"+intent.intentId()+":"+intent.intentVersion());
        var task=tasks.create(new CreateWorkTaskCommand(new TaskId(taskId),TaskType.EXTERNAL_WORK,"configured-plan",intent.planId(),capabilities,content.candidate().method().primarySkill(),intent.instruction(),intent.repositoryRef(),intent.baseRevision(),new TaskPriority(0),intent.dependencies().stream().map(TaskId::new).collect(java.util.stream.Collectors.toSet())));
        configurations.bindTask(taskId,binding);
        var sources=new ArrayList<GuardContract.FrozenRef>();ref.skills().forEach(r->sources.add(r.frozen("skill")));ref.knowledge().forEach(r->sources.add(r.frozen("knowledge")));
        sources.add(new GuardContract.FrozenRef("configuration",ref.id(),Long.toString(ref.revision()),ref.digest()));
        guard.bindPrepared(taskId,new GuardContract.Bind(intent.planId(),intent.policyRevision(),"PREP-"+AuthorityRepository.hash(intent.intentId()+":"+intent.intentVersion()),ref.digest(),List.copyOf(sources)),owner);
        planLifecycle.bind(intent,taskId,ref.resultKind());
        if(repo.jdbc().update("UPDATE execution_preparation SET state='READY',config_json=?,task_id=?,error_code=NULL,details_json=? WHERE intent_id=? AND intent_version=? AND generation=? AND state='PREPARING'",repo.encode(ref),task.id().value(),repo.encode(new Details(List.of(),List.of())),intent.intentId(),intent.intentVersion(),next)!=1) throw new IllegalStateException("Preparation lost materialization race");
        return view(repo.jdbc().queryForMap("SELECT * FROM execution_preparation WHERE intent_id=? AND intent_version=?",intent.intentId(),intent.intentVersion()));
    }
    private Preparation blocked(Intent intent,Error error,String detail,long generation) {
        var recovery=List.of("Correct the candidate and use a new intent version when its content changes", "Resolve the named capability/authorization/budget gap, then explicitly retry this unchanged intent with the current generation");
        repo.jdbc().update("UPDATE execution_preparation SET state='BLOCKED',error_code=?,details_json=? WHERE intent_id=? AND intent_version=? AND generation=? AND state='PREPARING'",error.name(),repo.encode(new Details(List.of(detail),recovery)),intent.intentId(),intent.intentVersion(),generation);
        return view(repo.jdbc().queryForMap("SELECT * FROM execution_preparation WHERE intent_id=? AND intent_version=?",intent.intentId(),intent.intentVersion()));
    }
    private Preparation view(Map<String,Object> row) {
        var details=repo.decode(String.valueOf(row.get("details_json")),Details.class);
        return new Preparation(String.valueOf(row.get("intent_id")),((Number)row.get("intent_version")).longValue(),((Number)row.get("generation")).longValue(),State.valueOf(String.valueOf(row.get("state"))),
                row.get("config_json")==null?null:repo.decode(String.valueOf(row.get("config_json")),PreparedConfigRef.class),row.get("error_code")==null?null:Error.valueOf(String.valueOf(row.get("error_code"))),
                (String)row.get("task_id"),details.missing(),details.recoveryActions());
    }
    public List<Preparation> preparations(String owner) { return repo.jdbc().queryForList("SELECT * FROM execution_preparation WHERE owner_id=? ORDER BY intent_id,intent_version",owner).stream().map(this::view).toList(); }
}
