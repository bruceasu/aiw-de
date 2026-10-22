package me.asu.aidev.authority;

import java.util.*;
import java.util.function.Supplier;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Component;

/** Server-owned serialization and authenticated execution context. Never callable by a private DB. */
@Component
public class AuthorityBoundary {
    private final AuthorityRepository repo;
    private final String boot=UUID.randomUUID().toString();
    private final ThreadLocal<Principal> current=new ThreadLocal<>();
    private final ThreadLocal<String> human=new ThreadLocal<>();
    private final ThreadLocal<Boolean> completion=new ThreadLocal<>();
    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.beans.factory.ObjectProvider<ManagedPlanAdmission> manager;
    public record Principal(String owner,String worker,long generation,boolean enabled) {}
    public AuthorityBoundary(AuthorityRepository repo) { this.repo=repo; }
    public String boot() { return boot; }
    public void lock() { repo.jdbc().update("UPDATE authority_lock SET version=version WHERE id=1"); }
    public boolean delegated() { return current.get()!=null; }
    public Principal principal() { return Objects.requireNonNull(current.get(),"Authenticated platform action context required"); }
    public Principal optionalPrincipal() { return current.get(); }
    public void human(String actor) { if(actor==null) human.remove();else human.set(actor); }
    public String human() { return human.get(); }
    public <T> T as(Principal principal,Supplier<T> action) {
        var previous=current.get();current.set(principal);
        try { return action.get(); } finally { if(previous==null) current.remove();else current.set(previous); }
    }
    public boolean humanAuthRequired() { return Boolean.TRUE.equals(repo.jdbc().queryForObject("SELECT human_auth_required FROM authority_metadata WHERE id=1",Boolean.class)); }
    public void requireMigrationSafeInstallation() {
        // This release cannot migrate/cut over an existing installation. No escape flag.
        if(Boolean.TRUE.equals(repo.jdbc().queryForObject("SELECT retired_data_present FROM authority_metadata WHERE id=1",Boolean.class)))
            throw new IllegalStateException("Existing private records are sealed; actual migration and cutover are disabled");
    }
    public String instance() {
        lock();
        repo.jdbc().update("UPDATE authority_metadata SET instance_id=? WHERE id=1 AND instance_id IS NULL",UUID.randomUUID().toString());
        return repo.jdbc().queryForObject("SELECT instance_id FROM authority_metadata WHERE id=1",String.class);
    }
    public Principal authenticate(String worker,String credential) {
        if(worker==null || credential==null) throw new SecurityException("Dedicated personal Worker credential required");
        var rows=repo.jdbc().queryForList("SELECT owner_id,key_hash,generation,enabled_boot FROM authority_identity WHERE worker_id=?",worker);
        if(rows.size()!=1 || !MessageDigest.isEqual(AuthorityRepository.hash(credential).getBytes(StandardCharsets.US_ASCII),
                String.valueOf(rows.getFirst().get("key_hash")).getBytes(StandardCharsets.US_ASCII)))
            throw new SecurityException("Invalid personal Worker identity");
        var row=rows.getFirst();
        return new Principal(String.valueOf(row.get("owner_id")),worker,((Number)row.get("generation")).longValue(),boot.equals(row.get("enabled_boot")));
    }
    public boolean processBoot(String worker,String boot) {
        return boot!=null && repo.jdbc().queryForObject("SELECT COUNT(*) FROM authority_identity WHERE worker_id=? AND worker_boot=?",Long.class,worker,boot)==1;
    }
    public void changing(String target) {
        lock();
        if(Boolean.TRUE.equals(completion.get())) return;
        if(!delegated() && humanAuthRequired() && human()==null) throw new SecurityException("Current authenticated human required for administrative writes");
        if(delegated()) return;
        repo.jdbc().update("UPDATE authority_target SET generation=generation+1,paused=TRUE WHERE target_key=? OR substr(target_key,1,?)=?",target,(target+":").length(),target+":");
        if(target.startsWith("PLAN:")) repo.jdbc().update("UPDATE authority_managed_plan SET paused=TRUE,generation=generation+1 WHERE plan_id=?",target.substring(5));
    }
    public boolean managedPlan(String plan) { return manager.getObject().managed(plan); }
    public void allowHumanTask(String task) { if(human()!=null && !delegated()) manager.getObject().humanTask(task); }
    public void beforeHumanTaskReplacement(String task) { if(human()!=null && !delegated()) manager.getObject().checkReplacement(task); }
    public void registered(String worker) {
        lock();
        if(repo.jdbc().queryForObject("SELECT COUNT(*) FROM authority_identity WHERE worker_id=?",Long.class,worker)>0)
            throw new SecurityException("Personal identity cannot register as a lease Worker");
        repo.jdbc().update("UPDATE authority_target SET generation=generation+1,paused=TRUE WHERE target_key=?","WORKER:"+worker);
    }
    public <T> T completionScope(Supplier<T> operation) {
        Boolean previous=completion.get();completion.set(true);
        try { return operation.get(); } finally { if(previous==null) completion.remove();else completion.set(previous); }
    }

}
