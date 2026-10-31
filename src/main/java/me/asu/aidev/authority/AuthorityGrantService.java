package me.asu.aidev.authority;

import java.util.*;
import me.asu.aidev.webconsole.security.WebConsoleSecurityService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthorityGrantService {
    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.beans.factory.ObjectProvider<BusinessActionPermissions> businessPermissions;
    private final AuthorityRepository repo;
    private final AuthorityBoundary boundary;
    private final WebConsoleSecurityService security;
    private final org.springframework.beans.factory.ObjectProvider<AuthorityActions> actions;
    @org.springframework.beans.factory.annotation.Autowired private org.springframework.beans.factory.ObjectProvider<AgentMessageService> agentMessages;
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
        for(int attempt=0;attempt<8;attempt++) {
            String id="o-"+UUID.randomUUID().toString().substring(0,8);
            if(repo.jdbc().queryForObject("SELECT COUNT(*) FROM authority_owner WHERE id=?",Long.class,id)==0) {
                repo.jdbc().update("INSERT INTO authority_owner VALUES(?,?)",id,actor);return id;
            }
        }
        throw new IllegalStateException("Unable to allocate a unique owner identity");
    }
    @Transactional public void enabled(String worker,long generation,String workerBoot,boolean enabled,String actor) {
        identityHuman(actor);boundary.lock();
        var identity=repo.jdbc().queryForMap("SELECT owner_id,approval_state FROM authority_identity WHERE worker_id=? AND identity_kind='AGENT'",worker);
        String owner=String.valueOf(identity.get("owner_id"));owns(owner,actor);
        if(!"APPROVED".equals(identity.get("approval_state"))) throw new IllegalStateException("Approve the Agent's exact initial Grant before enabling it");
        if(enabled) boundary.requireMigrationSafeInstallation();
        String currentBoot=repo.jdbc().queryForObject("SELECT worker_boot FROM authority_identity WHERE worker_id=?",String.class,worker);
        if(enabled && (currentBoot==null || !Objects.equals(currentBoot,workerBoot)))
            throw new IllegalStateException("The personal process must start a disabled session before explicit human enablement");
        if(repo.jdbc().update("UPDATE authority_identity SET enabled_boot=?,generation=generation+1,changed_by=?,changed_ms=? WHERE worker_id=? AND generation=?",
                enabled?boundary.boot():null,actor,System.currentTimeMillis(),worker,generation)!=1) throw new IllegalStateException("Identity generation changed; confirm the exact current process");
        if(enabled) repo.jdbc().update("UPDATE authority_metadata SET human_auth_required=TRUE WHERE id=1");
        repo.audit("AGENT:"+worker,null,null,actor,enabled?"AGENT_ENABLED":"AGENT_DISABLED",worker+"; earlier admissions invalidated");
    }
    @Transactional public Map<String,Object> session(AuthorityBoundary.Principal principal,String boot) {
        boundary.lock();
        if(boot==null || !UUID.fromString(boot).toString().equals(boot)) throw new IllegalArgumentException("Fresh canonical process UUID required");
        String previous=repo.jdbc().queryForObject("SELECT worker_boot FROM authority_identity WHERE worker_id=? AND identity_kind='AGENT'",String.class,principal.worker());
        if(!boot.equals(previous)) {
            if(repo.jdbc().update("UPDATE authority_identity SET worker_boot=?,enabled_boot=NULL,generation=generation+1,changed_by=?,changed_ms=? WHERE worker_id=? AND identity_kind='AGENT' AND generation=?",
                    boot,"agent:"+principal.worker(),System.currentTimeMillis(),principal.worker(),principal.generation())!=1)
                throw new IllegalStateException("Agent generation changed; reload session state");
            repo.audit("AGENT:"+principal.worker(),null,null,"agent:"+principal.worker(),"AGENT_SESSION_STARTED",
                    "New process boot; explicit human enablement required");
        }
        boolean enabled=boundary.boot().equals(repo.jdbc().queryForObject("SELECT enabled_boot FROM authority_identity WHERE worker_id=? AND identity_kind='AGENT'",String.class,principal.worker()));
        return Map.of("ownerId",principal.owner(),"agentId",principal.worker(),"agentBoot",boot,"state",enabled?"ENABLED":"DISABLED_UNTIL_HUMAN_ENABLE");
    }
    @Transactional public AuthorityRepository.Grant issue(String owner,String worker,ActionScope scope,long expiry,String actor) {
        return issue(owner,worker,scope,expiry,actor,false);
    }
    @Transactional public AuthorityRepository.Grant issueForPendingPairing(String owner,String worker,ActionScope scope,long expiry,String actor) {
        return issue(owner,worker,scope,expiry,actor,true);
    }
    private AuthorityRepository.Grant issue(String owner,String worker,ActionScope scope,long expiry,String actor,boolean pairingApproval) {
        Objects.requireNonNull(scope);human(actor,scope.action().name());owns(owner,actor);boundary.lock();
        if(businessPermissions.getObject().regime(owner,worker,scope.action()))throw new IllegalStateException("This action uses business permissions; legacy parallel grants are disabled");
        boundary.requireMigrationSafeInstallation();
        if(!scope.effect().equals("ALLOW") || expiry<=System.currentTimeMillis())
            throw new IllegalArgumentException("Explicit minimal ALLOW scope, expiry and separate restriction controls required");
        String requiredState=pairingApproval?"PENDING":"APPROVED";
        if(repo.jdbc().queryForObject("SELECT COUNT(*) FROM authority_identity WHERE worker_id=? AND owner_id=? AND identity_kind='AGENT' AND approval_state=?",Long.class,worker,owner,requiredState)!=1)
            throw new SecurityException(pairingApproval?"Pending owner/Agent pairing required":"Approved owner/Agent binding required");
        var adapter=actions.getObject();adapter.validate(scope);String target=adapter.key(scope);
        bindTarget(target,owner);
        String id="AG-"+UUID.randomUUID();long now=System.currentTimeMillis();
        repo.jdbc().update("INSERT INTO authority_grant VALUES(?,?,?,1,'ENABLED',?,?,?,?,?)",id,owner,worker,repo.encode(scope),
                security.currentDelegationAuthority(actor,scope.action().name()),now,expiry,target);
        repo.jdbc().update("INSERT INTO authority_budget VALUES(?,?,?,0,0) ON CONFLICT(target_key,action) DO NOTHING",target,scope.action().name(),scope.maximumUses());
        agentMessages.getObject().stateChanged(worker,"GRANT_CHANGED",id,1);
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
        if(repo.jdbc().update("UPDATE authority_grant SET state='REVOKED',revision=revision+1 WHERE id=? AND state='ENABLED'",id)!=1) throw new IllegalStateException("Grant already changed");
        repo.audit(grant.targetKey(),id,null,actor,"CAPABILITY_REVOKED","Past receipts and consumption retained");
        agentMessages.getObject().stateChanged(grant.workerId(),"GRANT_CHANGED",id,grant.revision()+1);
    }
}
