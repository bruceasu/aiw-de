package me.asu.aidev.authority;

import java.util.*;

/** Durable delivery of a human confirmation, without credentials or private context. */
final class SecretaryAuthorizationNotice {
    private SecretaryAuthorizationNotice() {}
    record Approval(String ownerId,String workerId,String workerBoot,long generation,long expiresAt,String planId) {}
    static void record(AuthorityRepository repo,String worker,String plan,long expires,String request,String actor) {
        var row=repo.jdbc().queryForMap("SELECT owner_id,worker_boot,generation FROM authority_identity WHERE worker_id=?",worker);
        var approval=new Approval(String.valueOf(row.get("owner_id")),worker,String.valueOf(row.get("worker_boot")),
                ((Number)row.get("generation")).longValue(),expires,plan);
        repo.audit("WORKER:"+worker,null,request,actor,"SECRETARY_AUTHORIZATION_AVAILABLE",repo.encode(approval));
    }
    static Map<String,Object> latest(AuthorityRepository repo,String worker) {
        var rows=repo.jdbc().queryForList("SELECT id,actor,detail,created_ms FROM authority_audit WHERE target_key=? AND event='SECRETARY_AUTHORIZATION_AVAILABLE' ORDER BY created_ms DESC,id DESC LIMIT 1","WORKER:"+worker);
        return rows.isEmpty()?Map.of():rows.getFirst();
    }
    static Map<String,Object> publicState(AuthorityRepository repo,AuthorityBoundary.Principal principal) {
        var row=latest(repo,principal.worker());
        if(row.isEmpty())return Map.of();
        var approval=repo.decode(String.valueOf(row.get("detail")),Approval.class);
        if(!principal.owner().equals(approval.ownerId()))throw new SecurityException("Authorization owner mismatch");
        var result=new LinkedHashMap<String,Object>();
        result.put("id",row.get("id"));result.put("createdAt",row.get("created_ms"));result.put("expiresAt",approval.expiresAt());result.put("planId",approval.planId());
        return result;
    }
}
