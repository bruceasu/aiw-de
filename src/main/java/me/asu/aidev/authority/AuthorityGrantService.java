package me.asu.aidev.authority;

import java.util.*;
import me.asu.aidev.webconsole.security.WebConsoleSecurityService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthorityGrantService {
    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.beans.factory.ObjectProvider<BusinessActionPermissions> businessPermissions;
    public static final class InvalidIdentityInput extends IllegalArgumentException {
        private final String field;
        private InvalidIdentityInput(String field,String message) { super(message);this.field=field; }
        public String field() { return field; }
    }
    private final AuthorityRepository repo;
    private final AuthorityBoundary boundary;
    private final WebConsoleSecurityService security;
    private final org.springframework.beans.factory.ObjectProvider<AuthorityActions> actions;
    public AuthorityGrantService(AuthorityRepository repo,AuthorityBoundary boundary,WebConsoleSecurityService security,
            org.springframework.beans.factory.ObjectProvider<AuthorityActions> actions) {
        this.repo=repo;this.boundary=boundary;this.security=security;this.actions=actions;
    }
    public void human(String actor,String action) {
        if(security.currentDelegationAuthority(actor,action).isBlank())
            throw new SecurityException("Current authenticated human role required");
    }
    public void identityHuman(String actor) {
        if(security.currentDelegationAuthority(actor,"A05").isBlank()
                && security.currentDelegationAuthority(actor,"A11_APPROVE").isBlank()) throw new SecurityException("Current identified human role required");
    }
    public void owns(String owner,String actor) {
        if(!Objects.equals(repo.actor(owner),actor)) throw new SecurityException("Owner binding does not match the authenticated human");
    }
    @Transactional public String bind(String actor) {
        identityHuman(actor);boundary.lock();boundary.instance();
        var existing=repo.jdbc().queryForList("SELECT id FROM authority_owner WHERE actor=?",String.class,actor);
        if(!existing.isEmpty()) return existing.getFirst();
        String id=shortIdentityId(true);repo.jdbc().update("INSERT INTO authority_owner VALUES(?,?)",id,actor);return id;
    }
    private String shortIdentityId(boolean ownerIdentity) {
        for(int attempt=0;attempt<8;attempt++) {
            String id=(ownerIdentity?"o-":"s-")+UUID.randomUUID().toString().substring(0,8);
            boolean exists=ownerIdentity
                    ? repo.jdbc().queryForObject("SELECT COUNT(*) FROM authority_owner WHERE id=?",Long.class,id)>0
                    : repo.jdbc().queryForObject("SELECT COUNT(*) FROM authority_identity WHERE worker_id=?",Long.class,id)>0
                        || repo.jdbc().queryForObject("SELECT COUNT(*) FROM worker WHERE id=?",Long.class,id)>0;
            if(!exists) return id;
        }
        throw new IllegalStateException("Unable to allocate a unique personal identity; retry registration explicitly");
    }
    @Transactional public Map<String,Object> register(String key,List<String> readScope,String actor) {
        String owner=bind(actor); // Explicit registration owns this transaction; a page view never calls it.
        String worker=shortIdentityId(false);
        enroll(owner,worker,key,readScope,actor);
        return identityResult(owner,worker,readScope);
    }
    @Transactional public Map<String,Object> rotateCredential(String worker,String key,List<String> readScope,String actor) {
        identityHuman(actor);boundary.lock();
        var owners=repo.jdbc().queryForList("SELECT i.owner_id FROM authority_identity i JOIN authority_owner o ON o.id=i.owner_id WHERE i.worker_id=? AND o.actor=?",String.class,worker,actor);
        if(owners.size()!=1) throw new SecurityException("Current owner must select an existing personal Worker");
        String owner=owners.getFirst();
        enroll(owner,worker,key,readScope,actor);
        return identityResult(owner,worker,readScope);
    }
    private Map<String,Object> identityResult(String owner,String worker,List<String> readScope) {
        return Map.of("ownerId",owner,"workerId",worker,"generation",repo.jdbc().queryForObject("SELECT generation FROM authority_identity WHERE worker_id=?",Long.class,worker),"readScope",readScope,"state","DISABLED");
    }
    @Transactional public void enroll(String owner,String worker,String key,List<String> readScope,String actor) {
        identityHuman(actor);owns(owner,actor);boundary.lock();
        if(worker==null || !worker.matches("[A-Za-z0-9_.-]{1,128}"))
            throw new InvalidIdentityInput("workerId","Worker ID 必须为 1–128 位英文字母、数字、下划线、点或短横线，例如 my-secretary。");
        if(key==null || !key.matches("[A-Za-z0-9_-]{43,128}"))
            throw new InvalidIdentityInput("credential","独立凭证必须为 43–128 位英文字母、数字、下划线或短横线。请使用“生成并复制”重新生成。");
        if(readScope==null || readScope.size()>128)
            throw new InvalidIdentityInput("readScope","读取范围必须为列表，最多包含 128 个目标；暂不读取目标时可提交空列表。");
        if(readScope.stream().anyMatch(k->k==null || k.length()>1024))
            throw new InvalidIdentityInput("readScope","读取范围的每个目标必须为文本，长度不得超过 1024 个字符。");
        if(repo.jdbc().queryForObject("SELECT COUNT(*) FROM worker WHERE id=?",Long.class,worker)>0
                || repo.jdbc().queryForObject("SELECT COUNT(*) FROM authority_identity WHERE worker_id=? AND owner_id<>?",Long.class,worker,owner)>0)
            throw new IllegalStateException("Worker identity already belongs to a different runtime or owner");
        repo.jdbc().update("""
                INSERT INTO authority_identity(worker_id,owner_id,key_hash,generation,enabled_boot,changed_by,changed_ms,read_scope)
                VALUES(?,?,?,1,NULL,?,?,?) ON CONFLICT(worker_id) DO UPDATE SET
                key_hash=excluded.key_hash,generation=authority_identity.generation+1,enabled_boot=NULL,
                changed_by=excluded.changed_by,changed_ms=excluded.changed_ms,read_scope=excluded.read_scope,worker_boot=NULL
                """,worker,owner,AuthorityRepository.hash(key),actor,System.currentTimeMillis(),repo.encode(readScope));
        repo.audit(null,null,null,actor,"PERSONAL_IDENTITY_ENROLLED",worker+"; disabled; no credential retained in audit");
    }
    @Transactional public void enabled(String worker,long generation,String workerBoot,boolean enabled,String actor) {
        identityHuman(actor);boundary.lock();
        String owner=repo.jdbc().queryForObject("SELECT owner_id FROM authority_identity WHERE worker_id=?",String.class,worker);owns(owner,actor);
        if(enabled) boundary.requireMigrationSafeInstallation();
        String currentBoot=repo.jdbc().queryForObject("SELECT worker_boot FROM authority_identity WHERE worker_id=?",String.class,worker);
        if(enabled && (currentBoot==null || !Objects.equals(currentBoot,workerBoot)))
            throw new IllegalStateException("The personal process must start a disabled session before explicit human enablement");
        if(repo.jdbc().update("UPDATE authority_identity SET enabled_boot=?,generation=generation+1,changed_by=?,changed_ms=? WHERE worker_id=? AND generation=?",
                enabled?boundary.boot():null,actor,System.currentTimeMillis(),worker,generation)!=1) throw new IllegalStateException("Identity generation changed; confirm the exact current process");
        if(enabled) repo.jdbc().update("UPDATE authority_metadata SET human_auth_required=TRUE WHERE id=1");
        repo.audit(null,null,null,actor,enabled?"PERSONAL_WORKER_ENABLED":"PERSONAL_WORKER_DISABLED",worker+"; earlier admissions invalidated");
    }
    @Transactional public Map<String,Object> session(AuthorityBoundary.Principal principal,String boot) {
        boundary.lock();
        if(boot==null || !UUID.fromString(boot).toString().equals(boot)) throw new IllegalArgumentException("Fresh canonical process UUID required");
        String previous=repo.jdbc().queryForObject("SELECT worker_boot FROM authority_identity WHERE worker_id=?",String.class,principal.worker());
        if(!boot.equals(previous)) {
            if(repo.jdbc().update("UPDATE authority_identity SET worker_boot=?,enabled_boot=NULL,generation=generation+1,changed_by=?,changed_ms=? WHERE worker_id=? AND generation=?",
                    boot,"personal:"+principal.worker(),System.currentTimeMillis(),principal.worker(),principal.generation())!=1)
                throw new IllegalStateException("Personal process generation changed; reload session state");
            repo.audit(null,null,null,"personal:"+principal.worker(),"PERSONAL_SESSION_STARTED","New claimed process boot; explicit human enablement required");
        }
        return Map.of("ownerId",principal.owner(),"workerId",principal.worker(),"workerBoot",boot,"state","DISABLED_UNTIL_HUMAN_ENABLE");
    }
    @Transactional public AuthorityRepository.Grant issue(String owner,String worker,ActionScope scope,long expiry,String actor) {
        Objects.requireNonNull(scope);human(actor,scope.action().name());owns(owner,actor);boundary.lock();
        if(businessPermissions.getObject().regime(owner,worker,scope.action()))throw new IllegalStateException("This action uses business permissions; legacy parallel grants are disabled");
        boundary.requireMigrationSafeInstallation();
        if(!scope.effect().equals("ALLOW") || expiry<=System.currentTimeMillis())
            throw new IllegalArgumentException("Explicit minimal ALLOW scope, expiry and separate restriction controls required");
        if(repo.jdbc().queryForObject("SELECT COUNT(*) FROM authority_identity WHERE worker_id=? AND owner_id=?",Long.class,worker,owner)!=1)
            throw new SecurityException("Enrolled owner/Worker binding required");
        var adapter=actions.getObject();adapter.validate(scope);String target=adapter.key(scope);
        bindTarget(target,owner);
        String id="AG-"+UUID.randomUUID();long now=System.currentTimeMillis();
        repo.jdbc().update("INSERT INTO authority_grant VALUES(?,?,?,1,'ENABLED',?,?,?,?,?)",id,owner,worker,repo.encode(scope),
                security.currentDelegationAuthority(actor,scope.action().name()),now,expiry,target);
        repo.jdbc().update("INSERT INTO authority_budget VALUES(?,?,?,0,0) ON CONFLICT(target_key,action) DO NOTHING",target,scope.action().name(),scope.maximumUses());
        repo.audit(target,id,null,actor,"MINIMAL_CAPABILITY_ISSUED",scope.action().name()+"; explicit total ceiling "+scope.maximumUses());return repo.get(id);
    }
    public void bindTarget(String target,String owner) {
        repo.jdbc().update("INSERT INTO authority_target VALUES(?,?,0,?,FALSE,0) ON CONFLICT(target_key) DO NOTHING",target,owner,System.currentTimeMillis());
        if(!Objects.equals(owner,repo.jdbc().queryForObject("SELECT owner_id FROM authority_target WHERE target_key=?",String.class,target)))
            throw new SecurityException("Target belongs to another owner");
    }
    public boolean current(AuthorityRepository.Grant grant) {
        return grant.state().equals("ENABLED") && grant.effectiveMs()<=System.currentTimeMillis() && grant.expiresMs()>System.currentTimeMillis()
                && businessPermissions.getObject().grantActive(grant)
                && grant.authority().equals(security.currentDelegationAuthority(repo.actor(grant.owner()),grant.rule().action().name()));
    }
    public boolean permitted(AuthorityRepository.Grant grant,Map<String,Object> snapshot) {
        if(!current(grant) || !targetCurrent(grant,null,null) || !matches(grant.rule(),snapshot)) return false;
        String target=actions.getObject().key(grant.rule());
        for(String raw:repo.jdbc().queryForList("SELECT scope_json FROM authority_restriction WHERE owner_id=? AND target_key=? AND active=TRUE",String.class,grant.owner(),target)) {
            var deny=repo.decode(raw,ActionScope.class);
            if(deny.action()==grant.rule().action() && matches(deny,snapshot)) return false;
        }
        return true;
    }
    public boolean targetCurrent(AuthorityRepository.Grant grant,Long generation,Long restriction) {
        var rows=repo.jdbc().queryForList("SELECT owner_id,paused,generation,restriction_revision FROM authority_target WHERE target_key=?",grant.targetKey());
        if(rows.size()!=1) return false;
        var target=rows.getFirst();
        return Objects.equals(grant.owner(),target.get("owner_id")) && !AuthorityRepository.flag(target.get("paused"))
                && (generation==null || generation.longValue()==((Number)target.get("generation")).longValue())
                && (restriction==null || restriction.longValue()==((Number)target.get("restriction_revision")).longValue());
    }
    public static boolean matches(ActionScope scope,Map<String,Object> snapshot) {
        return scope.states().contains(String.valueOf(snapshot.get("state")))
                && ((Number)snapshot.getOrDefault("rounds",0)).longValue()>=scope.minimumRounds();
    }
    @Transactional public void revoke(String id,String actor) {
        boundary.lock();var grant=repo.get(id);owns(grant.owner(),actor);human(actor,grant.rule().action().name());
        repo.jdbc().update("UPDATE authority_grant SET state='REVOKED',revision=revision+1 WHERE id=?",id);
        repo.audit(grant.targetKey(),id,null,actor,"CAPABILITY_REVOKED","Past receipts and consumption retained");
    }
}
