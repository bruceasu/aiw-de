package me.asu.aidev.secretary.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import me.asu.aidev.secretary.jdbc.SecretaryRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** First lock for human/delegate mutations. Completion exceptions are server-local only. */
@Component
public class DelegationBoundary {
    private final JdbcTemplate jdbc;
    private final String boot = UUID.randomUUID().toString();
    private final ThreadLocal<Boolean> delegate = new ThreadLocal<>();
    private final ThreadLocal<Boolean> completion = new ThreadLocal<>();
    private final ThreadLocal<String> human = new ThreadLocal<>();

    public DelegationBoundary(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public void lock() { jdbc.update("UPDATE delegation_lock SET version=version WHERE id=1"); }
    public String boot() { return boot; }
    public boolean delegated() { return Boolean.TRUE.equals(delegate.get()); }
    public boolean completing() { return Boolean.TRUE.equals(completion.get()); }
    public boolean legacyAuthRequired() { return Boolean.TRUE.equals(jdbc.queryForObject("SELECT legacy_auth_required FROM delegation_security WHERE id=1",Boolean.class)); }
    public void authenticatedHuman(String actor) { human.set(actor); }
    public String authenticatedHuman() { return human.get(); }
    public void clearHuman() { human.remove(); }
    public boolean managedPlan(String plan) {
        String prefix = "PLAN:" + plan;
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM delegation_target WHERE target_key=? OR substr(target_key,1,?)=?",
                Integer.class, prefix, (prefix + ":").length(), prefix + ":");
        return count != null && count > 0 || managedPublication(plan);
    }
    public boolean managedPublication(String plan) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM delegation_publication_plan WHERE plan_id=?",Long.class,plan)>0;
    }
    /** Called only after an explicit authenticated human's original create/retry operation. */
    public void allowHumanTask(String task) {
        if(delegated() || authenticatedHuman()==null && legacyAuthRequired()) return;
        var rows=jdbc.queryForList("SELECT subject_id,attempt_no FROM work_task WHERE id=?",task);
        if(rows.isEmpty() || !managedPlan(String.valueOf(rows.getFirst().get("subject_id")))) return;
        long next=((Number)rows.getFirst().get("attempt_no")).longValue()+1;
        jdbc.update("INSERT INTO delegation_task VALUES(?,?,?,FALSE,FALSE,FALSE) ON CONFLICT(task_id) DO UPDATE SET allowed_attempt=excluded.allowed_attempt",
                task,"PLAN:"+rows.getFirst().get("subject_id"),next);
        if(managedPublication(String.valueOf(rows.getFirst().get("subject_id"))))
            jdbc.update("INSERT INTO delegation_publication_attempt VALUES(?,?,NULL,NULL,NULL,NULL) ON CONFLICT(task_id) DO UPDATE SET allowed_attempt=excluded.allowed_attempt,grant_id=NULL,grant_revision=NULL,worker_generation=NULL,enabled_boot=NULL",task,next);
    }

    public <T> T asDelegate(Supplier<T> operation) { return scoped(delegate, operation); }
    public <T> T legacyCompletion(Supplier<T> operation) { return scoped(completion, operation); }
    private <T> T scoped(ThreadLocal<Boolean> scope, Supplier<T> operation) {
        Boolean previous = scope.get();
        scope.set(true);
        try { return operation.get(); }
        finally { if (previous == null) scope.remove(); else scope.set(previous); }
    }

    /** Must run at application entry, before secretary, plan, fleet, worker or task locks. */
    public void changing(String target) {
        if (completing()) return;
        lock();
        if (delegated()) return;
        if(legacyAuthRequired() && authenticatedHuman()==null)
            throw new SecurityException("A verified human session is required for management mutations");
        invalidateTarget(target, "Human changed the target. Explicit return and a fresh candidate are required.");
        if(target.startsWith("PLAN:"))
            for(String publication:jdbc.queryForList("SELECT target_key FROM delegation_publication_plan WHERE plan_id=?",String.class,target.substring(5)))
                invalidateTarget(publication,"Human changed the publication Plan. Explicit return and a fresh candidate are required.");
    }
    private void invalidateTarget(String target, String detail) {
        String prefix = target + ":";
        jdbc.update("UPDATE delegation_target SET generation=generation+1 WHERE target_key=? OR substr(target_key,1,?)=?", target, prefix.length(), prefix);
        jdbc.update("UPDATE delegation_request SET state='HUMAN',detail=?,updated_ms=? WHERE (target_key=? OR substr(target_key,1,?)=?) AND state='PENDING'",
                detail, Instant.now().toEpochMilli(), target, prefix.length(), prefix);
    }

    public void authenticate(String worker, String supplied) {
        List<String> hashes = jdbc.queryForList("SELECT key_hash FROM delegation_identity WHERE worker_id=?", String.class, worker);
        if (hashes.isEmpty()) return; // Legacy non-delegate identities keep their protocol.
        String actual = SecretaryRepository.hash(supplied == null ? "" : supplied);
        if (!MessageDigest.isEqual(hashes.getFirst().getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8)))
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED,
                    "Dedicated delegate credential required");
    }

    public void registered(String worker) {
        if (completing()) return;
        lock();
        invalidateTarget("WORKER:"+worker, "Worker registered again. Explicit return and a fresh candidate are required.");
        jdbc.update("UPDATE delegation_identity SET enabled_boot=NULL,generation=generation+1,changed_ms=? WHERE worker_id=?",
                Instant.now().toEpochMilli(), worker);
        jdbc.update("UPDATE delegation_request SET state='HUMAN',detail=?,updated_ms=? WHERE worker_id=? AND state='PENDING'",
                "Worker registered again; delegation requires explicit human enablement.", Instant.now().toEpochMilli(), worker);
    }
}
