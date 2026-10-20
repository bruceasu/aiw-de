package me.asu.aidev.secretary.application;

import java.util.function.Supplier;
import me.asu.aidev.secretary.jdbc.SecretaryRepository;
import me.asu.aidev.work.domain.WorkTask;
import org.springframework.stereotype.Component;

/** A server-local scope, never an actor name or model-provided credential. */
@Component
public class SecretaryPlanGate {
    private final SecretaryRepository repo;
    private final ThreadLocal<String> permitted = new ThreadLocal<>();
    public SecretaryPlanGate(SecretaryRepository repo) { this.repo=repo; }
    public void check(String plan) {
        if (repo.plan(plan).isPresent() && !plan.equals(permitted.get()))
            throw new IllegalStateException("Secretary-managed plan: use Secretary. Direct start, retry, jump and repair are disabled.");
    }
    public void serializePlan(String plan) {
        if(repo.plan(plan).isPresent()) repo.lock();
    }
    /** Serialization only: stopping or reporting a cancelled lease must remain possible. */
    public void serializeTask(WorkTask task) {
        if("flexible-plan".equals(task.subjectType())) serializePlan(task.subjectId());
        else if("secretary-chat".equals(task.subjectType()) || "secretary-coordinate".equals(task.subjectType())) repo.lock();
    }
    public void initialConfirmation(String plan) {
        // Only initial confirmation and adoption share this lock. Legacy completion keeps its lock order.
        repo.lockPlan(plan);
        check(plan);
    }
    public void admitTask(String plan,String task) {
        // Draft-only serialization protects adoption; confirmed legacy completion takes no Plan row lock.
        repo.lockDraftPlan(plan);
        // A generic task created concurrently before adoption has no bounded admission receipt.
        repo.plan(plan).ifPresent(w->{
            if(!plan.equals(permitted.get())) throw new IllegalStateException("Secretary-managed task admission requires bounded coordination");
            repo.admit(task,w);
        });
    }
    public void checkTask(WorkTask task) {
        if ("flexible-plan".equals(task.subjectType())) check(task.subjectId());
        else if ("secretary-coordinate".equals(task.subjectType()) || "secretary-chat".equals(task.subjectType()))
            throw new IllegalStateException("Secretary requests are immutable; use Secretary to send a new request");
    }
    public <T> T advance(String plan,Supplier<T> action) {
        String previous=permitted.get();
        permitted.set(plan);
        try { return action.get(); }
        finally { if(previous==null) permitted.remove(); else permitted.set(previous); }
    }
}
