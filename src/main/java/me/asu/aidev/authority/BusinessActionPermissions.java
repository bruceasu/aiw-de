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
    private final AuthorityRepository repo;
    private final AuthorityBoundary boundary;
    private final AuthorityGrantService grants;
    private final WebConsoleSecurityService security;
    private final ObjectProvider<PermissionTargetResolver> targets;
    private final ThreadLocal<String> request=new ThreadLocal<>();
    public BusinessActionPermissions(AuthorityRepository repo,AuthorityBoundary boundary,AuthorityGrantService grants,
            WebConsoleSecurityService security,ObjectProvider<PermissionTargetResolver> targets) {
        this.repo=repo;this.boundary=boundary;this.grants=grants;this.security=security;this.targets=targets;
    }
    public record Permission(String id,String ownerId,String workerId,ActionScope.Action action,Scope scopeKind,String scopeId,
                             long maximumUses,long usedUses,String state,String authority,long effectiveMs,long expiresMs,
                             String creationRequest,String creationDigest) {}
    public record Child(String grantId,String permissionId,String requestId,String sourcePlan,String sourceTask,String questionId) {}
    public List<Permission> list(String owner) {
        return repo.jdbc().query("SELECT * FROM authority_action_permission WHERE owner_id=? ORDER BY effective_ms DESC,id",(r,n)->new Permission(
                r.getString("id"),r.getString("owner_id"),r.getString("worker_id"),ActionScope.Action.valueOf(r.getString("action")),
                Scope.valueOf(r.getString("scope_kind")),r.getString("scope_id"),r.getLong("maximum_uses"),r.getLong("used_uses"),
                r.getString("state"),r.getString("authority"),r.getLong("effective_ms"),r.getLong("expires_ms"),r.getString("creation_request"),r.getString("creation_digest")),owner);
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
    @Transactional public Permission issue(Issue input,String actor) {
        boundary.lock();human(input.ownerId(),input.action(),actor);boundary.requireMigrationSafeInstallation();
        String digest=AuthorityRepository.hash(repo.encode(input));
        var previous=list(input.ownerId()).stream().filter(p->p.creationRequest().equals(input.requestId())).findFirst();
        if(previous.isPresent()) {
            if(!previous.get().creationDigest().equals(digest))throw new IllegalStateException("Creation request changed");
            return previous.get();
        }
        if(input.expiresAt()<=System.currentTimeMillis()||!scopes(input.action()).contains(input.scopeKind()))throw new IllegalArgumentException("Future expiry and compatible scope required");
        if(repo.jdbc().queryForObject("SELECT COUNT(*) FROM authority_identity WHERE owner_id=? AND worker_id=?",Long.class,input.ownerId(),input.workerId())!=1)
            throw new SecurityException("Existing owner/secretary binding required");
        targets.getObject().validateScope(input.ownerId(),input.action(),input.scopeKind(),input.scopeId());
        var current=list(input.ownerId()).stream().filter(p->p.workerId().equals(input.workerId())&&p.action()==input.action()&&p.state().equals("ENABLED")).findFirst();
        if(!Objects.equals(current.map(Permission::id).orElse(null),input.expectedPriorId()))throw new IllegalStateException("Previous permission changed");
        // Only this explicit human confirmation may first establish a global/ordinary-Worker binding.
        if(input.action()==ActionScope.Action.A13)grants.bindTarget("GLOBAL:FLEET",input.ownerId());
        if(input.scopeKind()==Scope.WORKER)grants.bindTarget("WORKER:"+input.scopeId(),input.ownerId());
        repo.jdbc().update("UPDATE authority_action_permission SET state='REPLACED' WHERE owner_id=? AND worker_id=? AND action=? AND state='ENABLED'",input.ownerId(),input.workerId(),input.action().name());
        for(var old:repo.grants())if(old.owner().equals(input.ownerId())&&old.workerId().equals(input.workerId())&&old.rule().action()==input.action()&&old.state().equals("ENABLED"))
            repo.jdbc().update("UPDATE authority_grant SET state='REVOKED',revision=revision+1 WHERE id=?",old.id());
        String id="AP-"+UUID.randomUUID();long now=System.currentTimeMillis();
        repo.jdbc().update("INSERT INTO authority_action_permission VALUES(?,?,?,?,?,?,?,0,'ENABLED',?,?,?,?,?)",id,input.ownerId(),input.workerId(),input.action().name(),input.scopeKind().name(),input.scopeId(),input.maximumUses(),
                security.currentDelegationAuthority(actor,input.action().name()),now,input.expiresAt(),input.requestId(),digest);
        repo.audit(null,id,input.requestId(),actor,"ACTION_PERMISSION_ISSUED","New explicit period; scope="+input.scopeKind()+"; count="+input.maximumUses()+"; prior records retained");
        return get(id);
    }
    @Transactional public void revoke(String id,String actor) {
        boundary.lock();var p=get(id);human(p.ownerId(),p.action(),actor);
        repo.jdbc().update("UPDATE authority_action_permission SET state='REVOKED' WHERE id=? AND state='ENABLED'",id);
        repo.audit(null,id,null,actor,"ACTION_PERMISSION_REVOKED","Past consumption, candidates and receipts retained");
    }
    public <T> T inRequest(String id,Supplier<T> work) {
        String prior=request.get();request.set(id);
        try{return work.get();}finally{if(prior==null)request.remove();else request.set(prior);}
    }
    public String requestId() {return request.get();}
    public void consume(AuthorityRepository.Grant grant,String originalRequest) {
        var linked=child(grant.id());
        if(linked.isEmpty()) {
            if(regime(grant.owner(),grant.workerId(),grant.rule().action()))throw new SecurityException("Old parallel permission is superseded");
            return;
        }
        var c=linked.get();var p=get(c.permissionId());String id=originalRequest==null?request.get():originalRequest;
        if(!grantActive(grant)||!Objects.equals(id,c.requestId()))throw new SecurityException("Current parent and exact original request required");
        targets.getObject().check(p,grant.rule(),c.sourceTask(),c.questionId());
        if(repo.jdbc().queryForObject("SELECT COUNT(*) FROM authority_permission_use WHERE permission_id=? AND request_id=?",Long.class,p.id(),id)>0)return;
        if(repo.jdbc().update("UPDATE authority_action_permission SET used_uses=used_uses+1 WHERE id=? AND state='ENABLED' AND used_uses<maximum_uses",p.id())!=1)
            throw new IllegalStateException("Action permission count exhausted");
        repo.jdbc().update("INSERT INTO authority_permission_use VALUES(?,?,?,?)",p.id(),id,grant.id(),System.currentTimeMillis());
        repo.audit(grant.targetKey(),p.id(),id,"personal:"+grant.workerId(),"ACTION_PERMISSION_CONSUMED","One accepted operation across all targets; no accounting-mode bypass");
    }
    public Map<String,Object> view(Permission p) {
        var result=new LinkedHashMap<String,Object>();
        result.put("id",p.id());result.put("ownerId",p.ownerId());result.put("workerId",p.workerId());result.put("action",p.action());
        result.put("scopeKind",p.scopeKind());result.put("scopeId",p.scopeId());result.put("maximumUses",p.maximumUses());result.put("usedUses",p.usedUses());
        result.put("remaining",p.maximumUses()-p.usedUses());result.put("state",p.state());result.put("expiresAt",p.expiresMs());
        result.put("effectiveState",!p.state().equals("ENABLED")?p.state():p.expiresMs()<=System.currentTimeMillis()?"EXPIRED":!active(p)?"ROLE_CHANGED":p.usedUses()>=p.maximumUses()?"EXHAUSTED":"ENABLED");
        result.put("creationRequestId",p.creationRequest());return result;
    }
}
