package me.asu.aidev.execution.config;

import java.util.*;
import me.asu.aidev.authority.*;
import me.asu.aidev.execution.port.ExecutionAttemptRepository;
import me.asu.aidev.work.domain.TaskId;
import me.asu.aidev.work.port.WorkTaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static me.asu.aidev.execution.config.ConfigurationContract.*;
import me.asu.aidev.execution.config.ConfigurationContract.Error;
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
    private final me.asu.aidev.autonomous.application.WorkspaceRegistry workspaces;
    private final me.asu.aidev.execution.guard.ExecutionGuard guard;

    public PersonalTaskPreparations(AuthorityRepository repo,AuthorityBoundary boundary,ActionAdmissionService admission,
            ManagedPlanAdmission manager,ConfigurationUiOptions options,ExecutionConfigurations configurations,
            AgentFactory factory,WorkTaskRepository tasks,ExecutionAttemptRepository attempts,me.asu.aidev.autonomous.application.WorkspaceRegistry workspaces,
            me.asu.aidev.execution.guard.ExecutionGuard guard) {
        this.repo=repo;this.boundary=boundary;this.admission=admission;this.manager=manager;this.options=options;
        this.configurations=configurations;this.factory=factory;this.tasks=tasks;this.attempts=attempts;
        this.workspaces=workspaces;
        this.guard=guard;
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
        var visible=choices(principal).stream().map(plan->Map.of("id",plan.id(),"title",plan.title(),"paused",plan.paused(),"revoked",plan.revoked(),
                "expiresAt",plan.expiresAt(),"policyRevision",plan.policyRevision(),"url",plan.url(),"repositories",plan.repositories().stream().map(repository->{
                    var value=new LinkedHashMap<String,Object>();value.put("ref",repository.ref());value.put("baselines",repository.baselines());
                    try {value.put("path",workspaces.repositoryPath(repository.ref()).toString());}catch(IllegalArgumentException ignored){/* Registration must be repaired before execution. */}
                    return value;
                }).toList())).toList();
        return Map.of("platformInstanceId",boundary.instance(),"ownerId",principal.owner(),"workerId",principal.worker(),
                "plans",visible,"models",factory.supportedProfiles(),"methods",configurations.catalogue().methods());
    }
    /** Read-only explanation for one owned Plan, including a disabled current process. */
    @Transactional public Map<String,Object> diagnostics(AuthorityBoundary.Principal principal,String planId) {
        boundary.lock();text(planId,128);
        if(repo.jdbc().queryForObject("SELECT COUNT(*) FROM flexible_workflow_plan WHERE id=? AND created_by=?",Long.class,planId,repo.actor(principal.owner()))!=1)
            throw new SecurityException("Owned personal Plan required");
        var missing=new ArrayList<Map<String,String>>();long now=System.currentTimeMillis();
        if(!guard.machineConnectionConfigured())missing.add(Map.of("code","WORKER_TOKEN_REQUIRED","message","平台尚未配置专业 Worker 连接令牌，这是平台运行配置缺项，需要配置后重启平台；重复计划授权不会解决。"));
        if(!principal.enabled())missing.add(Map.of("code","PROCESS_ENABLE_REQUIRED","message","平台已连接，但当前秘书进程尚未启用。请确认当前秘书进程，保留原授权。"));
        var managed=repo.jdbc().queryForList("SELECT owner_id,worker_id,paused,expires_ms FROM authority_managed_plan WHERE plan_id=?",planId);
        if(managed.isEmpty())missing.add(Map.of("code","PLAN_MANAGEMENT_REQUIRED","message","此计划尚未交给秘书管理，请在计划与文件范围页面选择原计划并保存。"));
        else if(!principal.owner().equals(managed.getFirst().get("owner_id"))||!principal.worker().equals(managed.getFirst().get("worker_id")))
            missing.add(Map.of("code","PLAN_MANAGER_MISMATCH","message","此计划已交给另一位秘书，请核对所选秘书和原计划。"));
        else if(AuthorityRepository.flag(managed.getFirst().get("paused"))||((Number)managed.getFirst().get("expires_ms")).longValue()<=now)
            missing.add(Map.of("code","PLAN_MANAGEMENT_INACTIVE","message","此计划的托管已暂停或到期。仅启用秘书进程不会恢复计划托管。"));
        var scope=Arrays.asList(repo.decode(repo.jdbc().queryForObject("SELECT read_scope FROM authority_identity WHERE worker_id=?",String.class,principal.worker()),String[].class));
        if(!scope.contains("PLAN:"+planId))missing.add(Map.of("code","PLAN_READ_SCOPE_REQUIRED","message","此秘书缺少原计划的读取范围，请重新保存原计划的代理设置。"));
        var policies=repo.jdbc().queryForList("SELECT owner_id,revision,paused,revoked,expires_ms FROM execution_policy WHERE plan_id=?",planId);
        if(policies.isEmpty())missing.add(Map.of("code","FILE_SCOPE_REQUIRED","message","此计划尚未保存文件执行范围。请勾选“同时确认此计划的仓库与文件执行范围”，核对仓库、文件和基线后保存。"));
        else if(!principal.owner().equals(policies.getFirst().get("owner_id")))throw new SecurityException("Policy owner mismatch");
        else if(AuthorityRepository.flag(policies.getFirst().get("paused"))||AuthorityRepository.flag(policies.getFirst().get("revoked"))||((Number)policies.getFirst().get("expires_ms")).longValue()<=now)
            missing.add(Map.of("code","EXECUTION_SCOPE_INACTIVE","message","此计划的文件执行范围已暂停、撤销或到期，请在执行控制中核对。"));
        if(missing.isEmpty()) {
            var choice=choices(principal).stream().filter(plan->plan.id().equals(planId)).findFirst();
            if(choice.isEmpty())missing.add(Map.of("code","PLAN_NOT_READABLE","message","原计划尚不可读取，请检查计划所属账号、读取范围和托管记录。"));
            else if(choice.get().repositories().stream().noneMatch(repository->!repository.baselines().isEmpty()))
                missing.add(Map.of("code","BASELINE_REQUIRED","message","此计划的执行范围已存在，但没有固定 Git 基线。请重新确认文件执行范围。"));
        }
        if(factory.supportedProfiles().isEmpty())missing.add(Map.of("code","PROFESSIONAL_MODEL_REQUIRED","message","Agent 工厂尚未选择默认模型。请打开“Agent 工厂设置”保存一次模型与自动 Worker 设置，之后由工厂自动准备。"));
        return Map.of("planId",planId,"processEnabled",principal.enabled(),"generation",principal.generation(),
                "missing",missing,"scopeReady",missing.isEmpty(),"policyRevision",policies.isEmpty()?0:policies.getFirst().get("revision"));
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
        String identity=intentId(principal,input.requestId());
        long generation=factory.preparations(principal.owner()).stream().filter(p->p.intentId().equals(identity)).mapToLong(Preparation::generation).findFirst().orElse(0);
        var intent=new Intent(identity,1,generation,plan.id(),plan.policyRevision(),repository.ref(),input.baseRevision(),
                input.instruction(),List.of(),model.profile().runtime(),model.id(),input.role(),null,null);
        boundary.as(principal,()->manager.delegatedAdmission(plan.id(),principal.worker(),()->factory.prepareAuthorized(intent,principal.owner())));
        repo.audit("PLAN:"+plan.id(),principal.worker(),input.requestId(),"personal-worker","PROFESSIONAL_TASK_PREPARED",intent.intentId());
        return read(principal,input.requestId());
    }
    /** Retry only the same accepted preparation while its managed Worker is starting. */
    @Transactional public Map<String,Object> continuePreparation(AuthorityBoundary.Principal principal,String requestId) {
        boundary.lock();admission.enabled(principal);
        String id=intentId(principal,requestId);
        var preparation=factory.preparations(principal.owner()).stream().filter(p->p.intentId().equals(id)).findFirst().orElseThrow();
        if(preparation.errorCode()!=Error.WORKER_STARTING)return read(principal,requestId);
        String raw=repo.jdbc().queryForObject("SELECT intent_json FROM execution_preparation WHERE owner_id=? AND intent_id=? AND intent_version=1",String.class,principal.owner(),id);
        var intent=repo.decode(raw,Intent.class);
        return prepare(principal,new Input(requestId,intent.planId(),intent.repositoryRef(),intent.baseRevision(),intent.defaultRole(),intent.aiProfileId(),intent.instruction()));
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
