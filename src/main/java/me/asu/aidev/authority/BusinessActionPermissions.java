package me.asu.aidev.authority;

import java.util.*;
import java.util.function.Supplier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import me.asu.aidev.webconsole.security.WebConsoleSecurityService;
import static me.asu.aidev.authority.ActionPermissionContract.*;

/** One hard, human-confirmed counter across all concrete targets of a permission period. */
@Service
public class BusinessActionPermissions {
    private static final Set<ActionScope.Action> DEFAULT_PLAN_TASK_ACTIONS = EnumSet.of(
            ActionScope.Action.A01, ActionScope.Action.A02, ActionScope.Action.A03, ActionScope.Action.A04,
            ActionScope.Action.A05, ActionScope.Action.A06, ActionScope.Action.A07, ActionScope.Action.A08,
            ActionScope.Action.A09, ActionScope.Action.A10, ActionScope.Action.A19_ANSWER);
    private static final String DEFAULT_DIGEST = AuthorityRepository.hash("DEFAULT_PLAN_TASK_V1");
    private static final long DEFAULT_EXPIRY_MS = 253402300799000L;
    private final AuthorityRepository repo;
    private final AuthorityBoundary boundary;
    private final AuthorityGrantService grants;
    private final WebConsoleSecurityService security;
    private final ObjectProvider<PermissionTargetResolver> targets;
    private final AgentMessageService agentMessages;
    private final ThreadLocal<String> request=new ThreadLocal<>();
    public BusinessActionPermissions(AuthorityRepository repo,AuthorityBoundary boundary,AuthorityGrantService grants,
            WebConsoleSecurityService security,ObjectProvider<PermissionTargetResolver> targets,AgentMessageService agentMessages) {
        this.repo=repo;this.boundary=boundary;this.grants=grants;this.security=security;this.targets=targets;this.agentMessages=agentMessages;
    }
    public record Permission(String id,String ownerId,String workerId,ActionScope.Action action,Scope scopeKind,String scopeId,
                             long maximumUses,long usedUses,String state,String authority,long effectiveMs,long expiresMs,
                             String creationRequest,String creationDigest,long revision) {}
    public record Child(String grantId,String permissionId,String requestId,String sourcePlan,String sourceTask,String questionId) {}
    public List<Permission> list(String owner) {
        return repo.jdbc().query("SELECT * FROM authority_action_permission WHERE owner_id=? ORDER BY effective_ms DESC,id",(r,n)->new Permission(
                r.getString("id"),r.getString("owner_id"),r.getString("worker_id"),ActionScope.Action.valueOf(r.getString("action")),
                Scope.valueOf(r.getString("scope_kind")),r.getString("scope_id"),r.getLong("maximum_uses"),r.getLong("used_uses"),
                r.getString("state"),r.getString("authority"),r.getLong("effective_ms"),r.getLong("expires_ms"),r.getString("creation_request"),r.getString("creation_digest"),r.getLong("revision")),owner);
    }
    public Permission get(String id) {
        String owner=repo.jdbc().queryForObject("SELECT owner_id FROM authority_action_permission WHERE id=?",String.class,id);
        return list(owner).stream().filter(p->p.id().equals(id)).findFirst().orElseThrow();
    }
    public Optional<Child> child(String grant) {
        return repo.jdbc().query("SELECT * FROM authority_permission_child WHERE grant_id=?",(r,n)->new Child(r.getString("grant_id"),
                r.getString("permission_id"),r.getString("request_id"),r.getString("source_plan"),r.getString("source_task"),r.getString("question_id")),grant).stream().findFirst();
    }
    public boolean regime(String owner,String worker,ActionScope.Action action) {
        return repo.jdbc().queryForObject("SELECT COUNT(*) FROM authority_action_permission WHERE owner_id=? AND worker_id=? AND action=?",Long.class,owner,worker,action.name())>0;
    }
    public boolean active(Permission p) {
        return p.state().equals("ENABLED")&&p.effectiveMs()<=System.currentTimeMillis()&&p.expiresMs()>System.currentTimeMillis()
                &&p.authority().equals(security.currentDelegationAuthority(repo.actor(p.ownerId()),p.action().name()));
    }
    /** Exhaustion blocks the next admission, never the last already accepted permit. */
    public boolean grantActive(AuthorityRepository.Grant grant) {
        var linked=child(grant.id());
        if(linked.isEmpty())return !regime(grant.owner(),grant.workerId(),grant.rule().action());
        var p=get(linked.get().permissionId());
        if(!active(p)||!p.ownerId().equals(grant.owner())||!p.workerId().equals(grant.workerId())||p.action()!=grant.rule().action())return false;
        try {targets.getObject().check(p,grant.rule(),linked.get().sourceTask(),linked.get().questionId());return true;}
        catch(SecurityException|IllegalArgumentException error){return false;}
    }
    public boolean admitted(AuthorityRepository.Grant grant) {
        var c=child(grant.id());return c.isEmpty()||repo.jdbc().queryForObject(
                "SELECT COUNT(*) FROM authority_permission_use WHERE permission_id=? AND request_id=?",Long.class,c.get().permissionId(),c.get().requestId())==1;
    }
    private void human(String owner,ActionScope.Action action,String actor) {
        grants.human(actor,action.name());
        if(!Objects.equals(repo.actor(owner),actor))throw new SecurityException("Current permission owner required");
    }
    public static boolean isDefaultAction(ActionScope.Action action) {return DEFAULT_PLAN_TASK_ACTIONS.contains(action);}
    private static boolean isDefault(Permission p) {return DEFAULT_DIGEST.equals(p.creationDigest());}
    public boolean defaultGrant(AuthorityRepository.Grant grant) {
        return child(grant.id()).map(c->isDefault(get(c.permissionId()))).orElse(false);
    }
    /** Provision owner-wide routine plan/task actions when a human enables this exact Agent process. */
    @Transactional public void ensurePlanTaskDefaults(String owner,String worker,String actor) {
        boundary.lock();grants.owns(owner,actor);boundary.requireMigrationSafeInstallation();
        if(repo.jdbc().queryForObject("SELECT COUNT(*) FROM authority_identity WHERE owner_id=? AND worker_id=? AND identity_kind='AGENT' AND approval_state='APPROVED'",Long.class,owner,worker)!=1)
            throw new SecurityException("Approved owner/Agent binding required");
        for(var action:DEFAULT_PLAN_TASK_ACTIONS) {
            String authority=security.currentDelegationAuthority(actor,action.name());
            var current=list(owner).stream().filter(p->p.workerId().equals(worker)&&p.action()==action&&p.state().equals("ENABLED")).findFirst();
            if(current.isPresent()&&isDefault(current.get())&&current.get().authority().equals(authority)
                    &&current.get().usedUses()<current.get().maximumUses()&&current.get().expiresMs()>System.currentTimeMillis())continue;
            if(current.isPresent()) {
                var old=current.get();
                if(repo.jdbc().update("UPDATE authority_action_permission SET state='REPLACED',revision=revision+1 WHERE id=? AND state='ENABLED'",old.id())!=1)
                    throw new IllegalStateException("Previous permission changed");
                agentMessages.stateChanged(worker,"PERMISSION_CHANGED",old.id(),old.revision()+1);
            }
            if(authority.isBlank())continue;
            String id="AP-"+UUID.randomUUID(),requestId=UUID.randomUUID().toString();
            repo.jdbc().update("INSERT INTO authority_action_permission(id,owner_id,worker_id,action,scope_kind,scope_id,maximum_uses,used_uses,state,authority,effective_ms,expires_ms,creation_request,creation_digest) VALUES(?,?,?,?,?,NULL,?,0,'ENABLED',?,?,?,?,?)",
                    id,owner,worker,action.name(),Scope.ALL.name(),Integer.MAX_VALUE,authority,System.currentTimeMillis(),DEFAULT_EXPIRY_MS,requestId,DEFAULT_DIGEST);
            repo.audit(null,id,requestId,actor,"DEFAULT_PLAN_TASK_PERMISSION_ENABLED","Owner-wide routine action="+action+"; prior period retained");
            agentMessages.stateChanged(worker,"PERMISSION_CHANGED",id,1);
        }
    }
    @Transactional public Permission issue(Issue input,String actor) {
        boundary.lock();human(input.ownerId(),input.action(),actor);boundary.requireMigrationSafeInstallation();
        if(isDefaultAction(input.action()))throw new IllegalArgumentException("Routine plan/task action is enabled with the Agent process");
        if(repo.jdbc().queryForObject("SELECT COUNT(*) FROM authority_identity WHERE owner_id=? AND worker_id=? AND identity_kind='AGENT' AND approval_state='APPROVED'",Long.class,input.ownerId(),input.agentId())!=1)
            throw new SecurityException("Approved owner/Agent binding required");
        String digest=AuthorityRepository.hash(repo.encode(input));
        var previous=list(input.ownerId()).stream().filter(p->p.creationRequest().equals(input.requestId())).findFirst();
        if(previous.isPresent()) {
            if(!previous.get().creationDigest().equals(digest))throw new IllegalStateException("Creation request changed");
            return previous.get();
        }
        if(input.expiresAt()<=System.currentTimeMillis()||!scopes(input.action()).contains(input.scopeKind()))throw new IllegalArgumentException("Future expiry and compatible scope required");
        targets.getObject().validateScope(input.ownerId(),input.action(),input.scopeKind(),input.scopeId());
        var current=list(input.ownerId()).stream().filter(p->p.workerId().equals(input.agentId())&&p.action()==input.action()&&p.state().equals("ENABLED")).findFirst();
        if(!Objects.equals(current.map(Permission::id).orElse(null),input.expectedPriorId()))throw new IllegalStateException("Previous permission changed");
        // Only this explicit human confirmation may first establish a global/ordinary-Worker binding.
        if(input.action()==ActionScope.Action.A13)grants.bindTarget("GLOBAL:FLEET",input.ownerId());
        if(input.scopeKind()==Scope.WORKER)grants.bindTarget("WORKER:"+input.scopeId(),input.ownerId());
        if(current.isPresent()) {
            var old=current.get();
            if(repo.jdbc().update("UPDATE authority_action_permission SET state='REPLACED',revision=revision+1 WHERE id=? AND state='ENABLED'",old.id())!=1) throw new IllegalStateException("Previous permission changed");
            agentMessages.stateChanged(input.agentId(),"PERMISSION_CHANGED",old.id(),old.revision()+1);
        }
        for(var old:repo.grants())if(old.owner().equals(input.ownerId())&&old.workerId().equals(input.agentId())&&old.rule().action()==input.action()&&old.state().equals("ENABLED"))
            if(repo.jdbc().update("UPDATE authority_grant SET state='REVOKED',revision=revision+1 WHERE id=? AND state='ENABLED'",old.id())==1)
                agentMessages.stateChanged(input.agentId(),"GRANT_CHANGED",old.id(),old.revision()+1);
        String id="AP-"+UUID.randomUUID();long now=System.currentTimeMillis();
        repo.jdbc().update("INSERT INTO authority_action_permission(id,owner_id,worker_id,action,scope_kind,scope_id,maximum_uses,used_uses,state,authority,effective_ms,expires_ms,creation_request,creation_digest) VALUES(?,?,?,?,?,?,?,0,'ENABLED',?,?,?,?,?)",id,input.ownerId(),input.agentId(),input.action().name(),input.scopeKind().name(),input.scopeId(),input.maximumUses(),
                security.currentDelegationAuthority(actor,input.action().name()),now,input.expiresAt(),input.requestId(),digest);
        repo.audit(null,id,input.requestId(),actor,"ACTION_PERMISSION_ISSUED","New explicit period; scope="+input.scopeKind()+"; count="+input.maximumUses()+"; prior records retained");
        agentMessages.stateChanged(input.agentId(),"PERMISSION_CHANGED",id,1);
        return get(id);
    }
    @Transactional public void revoke(String id,String actor) {
        boundary.lock();var p=get(id);human(p.ownerId(),p.action(),actor);
        if(isDefault(p))throw new IllegalArgumentException("Disable or revoke the Agent identity to stop default plan/task actions");
        if(repo.jdbc().update("UPDATE authority_action_permission SET state='REVOKED',revision=revision+1 WHERE id=? AND state='ENABLED'",id)!=1) throw new IllegalStateException("Permission already changed");
        repo.audit(null,id,null,actor,"ACTION_PERMISSION_REVOKED","Past consumption, candidates and receipts retained");
        agentMessages.stateChanged(p.workerId(),"PERMISSION_CHANGED",id,p.revision()+1);
    }
    public <T> T inRequest(String id,Supplier<T> work) {
        String prior=request.get();request.set(id);
        try{return work.get();}finally{if(prior==null)request.remove();else request.set(prior);}
    }
    public String requestId() {return request.get();}
    /** Consume the existing plan-confirmation permission for one frozen factory plan, not model/file access. */
    @Transactional public void confirmFactoryPlan(AuthorityBoundary.Principal principal, String plan, String requestId) {
        boundary.lock();
        var permission = list(principal.owner()).stream().filter(p -> p.workerId().equals(principal.worker())
                && p.action() == ActionScope.Action.A05 && active(p) && p.usedUses() < p.maximumUses()
                && (p.scopeKind() == Scope.ALL || p.scopeKind() == Scope.PLAN && plan.equals(p.scopeId())))
                .findFirst().orElseThrow(() -> new SecurityException("PLAN_CONFIRMATION_REQUIRED: authorize plan confirmation before factory execution"));
        var rule = new ActionScope(ActionScope.Action.A05, plan, null, "ALLOW", Set.of("DRAFT"), 0, permission.maximumUses(), Map.of());
        var grant = grants.issueForPermission(permission, rule, repo.actor(permission.ownerId()));
        targets.getObject().check(permission, rule, null, null);
        repo.jdbc().update("INSERT INTO authority_permission_child(grant_id,permission_id,request_id,source_plan,source_task,question_id) VALUES(?,?,?,?,NULL,NULL)",
                grant.id(), permission.id(), requestId, plan);
        consume(grant, requestId);
    }
    @Transactional public void consume(AuthorityRepository.Grant grant,String originalRequest) {
        var linked=child(grant.id());
        if(linked.isEmpty()) {
            if(regime(grant.owner(),grant.workerId(),grant.rule().action()))throw new SecurityException("Old parallel permission is superseded");
            return;
        }
        var c=linked.get();var p=get(c.permissionId());String id=originalRequest==null?request.get():originalRequest;
        if(!grantActive(grant)||!Objects.equals(id,c.requestId()))throw new SecurityException("Current parent and exact original request required");
        targets.getObject().check(p,grant.rule(),c.sourceTask(),c.questionId());
        if(repo.jdbc().queryForObject("SELECT COUNT(*) FROM authority_permission_use WHERE permission_id=? AND request_id=?",Long.class,p.id(),id)>0)return;
        if(repo.jdbc().update("UPDATE authority_action_permission SET used_uses=used_uses+1,revision=revision+1 WHERE id=? AND state='ENABLED' AND used_uses<maximum_uses",p.id())!=1)
            throw new IllegalStateException("Action permission count exhausted");
        repo.jdbc().update("INSERT INTO authority_permission_use VALUES(?,?,?,?)",p.id(),id,grant.id(),System.currentTimeMillis());
        agentMessages.stateChanged(p.workerId(),"PERMISSION_CHANGED",p.id(),p.revision()+1);
        repo.audit(grant.targetKey(),p.id(),id,"agent:"+grant.workerId(),"ACTION_PERMISSION_CONSUMED","One accepted operation across all targets; no accounting-mode bypass");
    }
    public Map<String,Object> view(Permission p) {
        var result=new LinkedHashMap<String,Object>();
        result.put("id",p.id());result.put("ownerId",p.ownerId());result.put("agentId",p.workerId());result.put("action",p.action());
        result.put("scopeKind",p.scopeKind());result.put("scopeId",p.scopeId());result.put("maximumUses",p.maximumUses());result.put("usedUses",p.usedUses());
        result.put("remaining",p.maximumUses()-p.usedUses());result.put("state",p.state());result.put("expiresAt",p.expiresMs());
        result.put("effectiveState",!p.state().equals("ENABLED")?p.state():p.expiresMs()<=System.currentTimeMillis()?"EXPIRED":!active(p)?"ROLE_CHANGED":p.usedUses()>=p.maximumUses()?"EXHAUSTED":"ENABLED");
        result.put("creationRequestId",p.creationRequest());result.put("defaultPermission",isDefault(p));result.put("revision",p.revision());return result;
    }
}
