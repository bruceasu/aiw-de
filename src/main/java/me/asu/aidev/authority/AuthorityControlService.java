package me.asu.aidev.authority;

import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthorityControlService {
    private final AuthorityRepository repo;
    private final AuthorityBoundary boundary;
    private final AuthorityGrantService grants;
    private final AuthorityActions actions;
    public AuthorityControlService(AuthorityRepository repo,AuthorityBoundary boundary,AuthorityGrantService grants,AuthorityActions actions) {
        this.repo=repo;this.boundary=boundary;this.grants=grants;this.actions=actions;
    }
    public record Restriction(String requestId,ActionScope scope,long expectedRevision,String privateRevisionDigest) {}
    @Transactional public Map<String,Object> restrict(AuthorityBoundary.Principal principal,Restriction input) {
        boundary.lock();
        if(!principal.enabled() || repo.jdbc.queryForObject("SELECT COUNT(*) FROM authority_identity WHERE worker_id=? AND generation=? AND enabled_boot=?",Long.class,principal.worker(),principal.generation(),boundary.boot())!=1)
            throw new SecurityException("Enabled dedicated identity required");
        if(input.scope()==null || !input.scope().effect().equals("DENY") || input.privateRevisionDigest()==null || !input.privateRevisionDigest().matches("[a-f0-9]{64}"))
            throw new IllegalArgumentException("Only minimal prohibition projections are accepted; no private text or expansion");
        var keyFields=input.scope().action()==ActionScope.Action.A12_CREATE?Set.of("targetPath","reviewTaskId"):Set.<String>of();
        if(!keyFields.containsAll(input.scope().parameters().keySet())) throw new IllegalArgumentException("Only necessary target-key fields belong in a prohibition");
        actions.validate(input.scope());
        String id="AR-"+UUID.fromString(input.requestId());String target=actions.key(input.scope());
        if(repo.jdbc.queryForObject("SELECT COUNT(*) FROM authority_target WHERE target_key=? AND owner_id=?",Long.class,target,principal.owner())!=1)
            throw new SecurityException("Target not visible");
        if(!Arrays.asList(repo.decode(repo.jdbc.queryForObject("SELECT read_scope FROM authority_identity WHERE worker_id=?",String.class,principal.worker()),String[].class)).contains(target))
            throw new SecurityException("Explicit target scope required for a narrowing projection");
        String encoded=repo.encode(input.scope());
        var previous=repo.jdbc.queryForList("SELECT owner_id,scope_json,private_digest FROM authority_restriction WHERE id=?",id);
        if(!previous.isEmpty()) {
            if(!Objects.equals(previous.getFirst().get("owner_id"),principal.owner()) || !Objects.equals(previous.getFirst().get("scope_json"),encoded) || !Objects.equals(previous.getFirst().get("private_digest"),input.privateRevisionDigest()))
                throw new IllegalStateException("RESTRICTION_REQUEST_CONFLICT");
        } else {
            if(repo.jdbc.update("UPDATE authority_target SET paused=TRUE,restriction_revision=restriction_revision+1,generation=generation+1 WHERE target_key=? AND restriction_revision=?",target,input.expectedRevision())!=1)
                throw new IllegalStateException("RESTRICTION_VERSION_CHANGED");
            repo.jdbc.update("INSERT INTO authority_restriction VALUES(?,?,?,?,TRUE,?,?)",id,principal.owner(),target,encoded,System.currentTimeMillis(),input.privateRevisionDigest());
            repo.audit(target,null,null,"personal:"+principal.worker(),"CAPABILITY_NARROWED",id+"; explicit human return required");
        }
        return Map.of("id",id,"target",target,"paused",true,"restrictionRevision",repo.jdbc.queryForObject("SELECT restriction_revision FROM authority_target WHERE target_key=?",Long.class,target));
    }
    @Transactional public void returnTarget(String target,long generation,boolean giveBack,String actor) {
        boundary.lock();String owner=repo.jdbc.queryForObject("SELECT owner_id FROM authority_target WHERE target_key=?",String.class,target);
        grants.owns(owner,actor);grants.identityHuman(actor);
        if(repo.jdbc.update("UPDATE authority_target SET paused=?,generation=generation+1 WHERE target_key=? AND generation=?",!giveBack,target,generation)!=1)
            throw new IllegalStateException("TARGET_GENERATION_CHANGED");
        if(target.startsWith("PLAN:")) repo.jdbc.update("UPDATE authority_managed_plan SET paused=?,generation=generation+1 WHERE plan_id=?",!giveBack,target.substring(5));
        repo.audit(target,null,null,actor,giveBack?"TARGET_RETURNED":"HUMAN_TAKEOVER","Only new candidates may execute; prior requests not replayed");
    }
    @Transactional public void removeRestriction(String id,String actor) {
        boundary.lock();var restriction=repo.jdbc.queryForMap("SELECT * FROM authority_restriction WHERE id=?",id);
        grants.owns(String.valueOf(restriction.get("owner_id")),actor);
        var scope=repo.decode(String.valueOf(restriction.get("scope_json")),ActionScope.class);grants.human(actor,scope.action().name());
        repo.jdbc.update("UPDATE authority_restriction SET active=FALSE WHERE id=?",id);
        repo.jdbc.update("UPDATE authority_target SET paused=TRUE,generation=generation+1,restriction_revision=restriction_revision+1 WHERE target_key=?",restriction.get("target_key"));
        repo.audit(String.valueOf(restriction.get("target_key")),null,null,actor,"PROHIBITION_EXPLICITLY_REMOVED",id+"; target remains paused");
    }
    @Transactional public void supplement(String target,String action,long amount,String actor,AuthorityBudgets budgets) {
        grants.human(actor,action);grants.owns(repo.jdbc.queryForObject("SELECT owner_id FROM authority_target WHERE target_key=?",String.class,target),actor);
        budgets.supplement(target,action,amount,actor);
    }
    @Transactional public void reconcile(String task,long attempt,String conclusion,String evidence,String actor) {
        boundary.lock();
        if(conclusion==null || !Set.of("NO_EFFECT_CONFIRMED","EFFECT_CONFIRMED").contains(conclusion) || evidence==null || evidence.isBlank() || evidence.length()>2000)
            throw new IllegalArgumentException("Explicit non-ambiguous effect conclusion and evidence required");
        var permit=repo.jdbc.queryForMap("SELECT * FROM authority_task WHERE task_id=?",task);
        var target=repo.jdbc.queryForMap("SELECT * FROM authority_target WHERE target_key=?",permit.get("target_key"));
        grants.owns(String.valueOf(target.get("owner_id")),actor);grants.human(actor,"A05");
        if(!"UNKNOWN".equals(permit.get("state")) || ((Number)permit.get("allowed_attempt")).longValue()!=attempt)
            throw new IllegalStateException("Exact unknown attempt required");
        repo.jdbc.update("INSERT INTO authority_effect_history VALUES(?,?,?) ON CONFLICT(task_id,attempt) DO NOTHING",task,attempt,repo.encode(permit));
        String state=conclusion.equals("EFFECT_CONFIRMED")?"SUCCEEDED":"FAILED_NO_EFFECT";
        repo.jdbc.update("UPDATE authority_task SET state=? WHERE task_id=?",state,task);
        repo.jdbc.update("UPDATE authority_receipt SET state=?,updated_ms=? WHERE owner_id=? AND request_id=? AND state='UNKNOWN'",state.equals("SUCCEEDED")?state:"FAILED",System.currentTimeMillis(),target.get("owner_id"),permit.get("request_id"));
        repo.audit(String.valueOf(permit.get("target_key")),null,String.valueOf(permit.get("request_id")),actor,"EFFECT_RECONCILED",task+"; attempt "+attempt+"; "+conclusion+"; evidence "+evidence+"; no replay or refund");
    }
}
