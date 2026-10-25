package me.asu.aidev.authority;

import java.nio.charset.StandardCharsets;
import java.util.*;
import me.asu.aidev.workflow.application.WorkflowPlanService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static me.asu.aidev.authority.ActionPermissionContract.*;

/** A human confirms one readable profile; existing authority services remain decisive. */
@Service
public class SecretarySetupService {
    private static final Set<ActionScope.Action> ACTIONS=Set.of(ActionScope.Action.A05,ActionScope.Action.A03,
            ActionScope.Action.A19_ANSWER,ActionScope.Action.A11_APPROVE,ActionScope.Action.A11_REJECT,ActionScope.Action.A07);
    private final AuthorityRepository repo;private final AuthorityBoundary boundary;private final AuthorityGrantService grants;
    private final BusinessActionPermissions permissions;private final PermissionTargetResolver targets;
    private final WorkflowPlanService plans;private final CoordinationService coordination;
    private final SecretaryExecutionSetup execution;
    public SecretarySetupService(AuthorityRepository repo,AuthorityBoundary boundary,AuthorityGrantService grants,
            BusinessActionPermissions permissions,PermissionTargetResolver targets,WorkflowPlanService plans,CoordinationService coordination,SecretaryExecutionSetup execution) {
        this.repo=repo;this.boundary=boundary;this.grants=grants;this.permissions=permissions;this.targets=targets;this.plans=plans;this.coordination=coordination;
        this.execution=execution;
    }
    public record Input(String requestId,String workerId,String workerBoot,long generation,String planId,Long planVersion,
                        String specDigest,long expiresAt,long maximumUses,Set<ActionScope.Action> actions,Map<String,String> expectedPriorIds,
                        SecretaryExecutionSetup.Selection execution,boolean keepExistingPermissions) {}
    public Map<String,Object> state(String actor) {
        grants.identityHuman(actor);
        var workers=repo.jdbc().queryForList("SELECT worker_id,owner_id,generation,worker_boot,enabled_boot,read_scope FROM authority_identity WHERE owner_id IN (SELECT id FROM authority_owner WHERE actor=?) ORDER BY worker_id",actor);
        var identities=workers.stream().map(row->{var item=new LinkedHashMap<>(row);item.put("enabled",boundary.boot().equals(item.remove("enabled_boot")));return item;}).toList();
        var choices=plans.list().stream().filter(plan->actor.equals(plan.createdBy())).map(plan->{
            var value=new LinkedHashMap<String,Object>();value.put("id",plan.id());value.put("title",plan.spec().goal().strip().split("\\R",2)[0]);
            value.put("description",plan.spec().goal());value.put("version",plan.version());value.put("specDigest",AuthorityRepository.hash(repo.encode(plan.spec())));
            value.put("steps",plan.spec().steps());
            value.put("execution",execution.state(plan.id()));
            value.put("managed",repo.jdbc().queryForList("SELECT worker_id FROM authority_managed_plan WHERE plan_id=?",String.class,plan.id()));
            return value;
        }).toList();
        var available=new ArrayList<String>();
        for(var action:ACTIONS)try{grants.human(actor,action.name());available.add(action.name());}catch(SecurityException ignored){/* The current role remains decisive. */}
        return Map.of("workers",identities,"plans",choices,"actions",available,"repositories",execution.repositories());
    }
    @Transactional public Map<String,Object> authorize(Input input,String actor) {
        grants.identityHuman(actor);boundary.lock();uuid(input.requestId());
        if(input.workerId()==null||input.workerBoot()==null||input.actions()==null||!ACTIONS.containsAll(input.actions())
                ||input.expectedPriorIds()==null||input.expectedPriorIds().size()>ACTIONS.size()
                ||input.maximumUses()<1||input.maximumUses()>10000||input.expiresAt()<=System.currentTimeMillis()
                ||input.expiresAt()>System.currentTimeMillis()+366L*24*60*60*1000)throw new IllegalArgumentException("请确认秘书、代理动作、期限和累计次数。");
        uuid(input.workerBoot());
        var rows=repo.jdbc().queryForList("SELECT owner_id,generation,worker_boot,enabled_boot,read_scope FROM authority_identity WHERE worker_id=?",input.workerId());
        if(rows.size()!=1)throw new SecurityException("请选择当前账号已登记的秘书。");
        var identity=rows.getFirst();String owner=String.valueOf(identity.get("owner_id"));grants.owns(owner,actor);
        String profileDigest=AuthorityRepository.hash(repo.encode(input));
        var savedProfiles=repo.jdbc().queryForList("SELECT detail FROM authority_audit WHERE request_id=? AND actor=? AND event='SECRETARY_SETUP_PROFILE'",String.class,input.requestId(),actor);
        if(!savedProfiles.isEmpty()) {
            if(!savedProfiles.getFirst().equals(profileDigest))throw new IllegalStateException("原代理设置申请不能更改，请重新读取当前状态。");
            return Map.of("state","RECORDED","executionScopeRequired",input.planId()!=null&&((Number)execution.state(input.planId()).get("revision")).longValue()==0);
        }
        if(!input.workerBoot().equals(identity.get("worker_boot")))throw new IllegalStateException("秘书进程已变化，请重新读取连接状态。");
        boolean hasPlan=input.planId()!=null&&!input.planId().isBlank();
        if(!hasPlan&&!input.actions().isEmpty())throw new IllegalArgumentException("代理动作需要明确的计划范围。");
        if(!hasPlan&&input.execution()!=null)throw new IllegalArgumentException("文件执行范围需要明确的计划。");
        var originals=permissions.list(owner);
        boolean recorded=!input.actions().isEmpty()&&input.actions().stream().allMatch(action->originals.stream().anyMatch(p->p.creationRequest().equals(child(input.requestId(),action))));
        if(!recorded&&((Number)identity.get("generation")).longValue()!=input.generation())throw new IllegalStateException("秘书状态已变化，请重新核对。");
        if(hasPlan)targets.plan(owner,input.planId());
        var granted=new ArrayList<Map<String,Object>>();
        for(var action:input.actions().stream().sorted().toList()) {
            if(input.keepExistingPermissions()) {
                grants.human(actor,action.name());
                var original=originals.stream().filter(p->p.id().equals(input.expectedPriorIds().get(action.name()))&&p.workerId().equals(input.workerId())
                        &&p.action()==action&&p.scopeKind()==Scope.PLAN&&Objects.equals(p.scopeId(),input.planId())).findFirst()
                        .orElseThrow(()->new IllegalStateException("原代理权限已变化，请重新核对。"));
                var view=permissions.view(original);
                if(!"ENABLED".equals(view.get("effectiveState"))||((Number)view.get("remaining")).longValue()<1)throw new IllegalStateException("原代理权限已到期或用尽，请明确更新代理范围。");
                granted.add(view);
            } else granted.add(permissions.view(permissions.issue(new Issue(child(input.requestId(),action),owner,input.workerId(),action,
                    Scope.PLAN,input.planId(),input.maximumUses(),input.expiresAt(),input.expectedPriorIds().get(action.name())),actor)));
        }
        if(recorded) {
            if(input.execution()!=null)execution.confirm(owner,input.planId(),input.expiresAt(),input.execution(),actor);
            SecretaryAuthorizationNotice.record(repo,input.workerId(),input.planId(),input.expiresAt(),input.requestId(),actor);
            repo.audit(hasPlan?"PLAN:"+input.planId():null,null,input.requestId(),actor,"SECRETARY_SETUP_PROFILE",profileDigest);
            return Map.of("state","RECORDED","permissions",granted,"executionScopeRequired",hasPlan&&input.execution()==null&&((Number)execution.state(input.planId()).get("revision")).longValue()==0);
        }
        if(hasPlan) {
            var managers=repo.jdbc().queryForList("SELECT owner_id,worker_id FROM authority_managed_plan WHERE plan_id=?",input.planId());
            if(managers.isEmpty()) {
                if(input.planVersion()==null||input.specDigest()==null)throw new IllegalArgumentException("请先读取并核对计划内容。");
                coordination.adopt(owner,input.workerId(),input.planId(),input.planVersion(),input.specDigest(),input.expiresAt(),actor);
            } else if(!owner.equals(managers.getFirst().get("owner_id"))||!input.workerId().equals(managers.getFirst().get("worker_id"))) {
                throw new SecurityException("该计划已经交由其他秘书管理。");
            }
            var readScope=new LinkedHashSet<>(Arrays.asList(repo.decode(String.valueOf(identity.get("read_scope")),String[].class)));
            readScope.add("PLAN:"+input.planId());if(readScope.size()>128)throw new IllegalArgumentException("读取范围已满，请在高级配置中整理。");
            repo.jdbc().update("UPDATE authority_identity SET read_scope=? WHERE worker_id=?",repo.encode(readScope),input.workerId());
        }
        if(!boundary.boot().equals(identity.get("enabled_boot")))grants.enabled(input.workerId(),input.generation(),input.workerBoot(),true,actor);
        if(input.execution()!=null)execution.confirm(owner,input.planId(),input.expiresAt(),input.execution(),actor);
        SecretaryAuthorizationNotice.record(repo,input.workerId(),input.planId(),input.expiresAt(),input.requestId(),actor);
        repo.audit(hasPlan?"PLAN:"+input.planId():null,null,input.requestId(),actor,"SECRETARY_SETUP_CONFIRMED","Explicit profile, current process and selected file execution scope");
        repo.audit(hasPlan?"PLAN:"+input.planId():null,null,input.requestId(),actor,"SECRETARY_SETUP_PROFILE",profileDigest);
        return Map.of("state","CONFIRMED","permissions",granted,"executionScopeRequired",hasPlan&&repo.jdbc().queryForObject("SELECT COUNT(*) FROM execution_policy WHERE plan_id=?",Long.class,input.planId())==0);
    }
    private static String child(String request,ActionScope.Action action) {return UUID.nameUUIDFromBytes((request+"\n"+action.name()).getBytes(StandardCharsets.UTF_8)).toString();}
}
