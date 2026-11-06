package me.asu.aidev.execution.run;

import me.asu.aidev.execution.guard.ExecutionGuard;
import me.asu.aidev.execution.port.WorkerTaskClaimPolicy;
import me.asu.aidev.work.domain.WorkTask;
import me.asu.aidev.worker.domain.Worker;
import org.springframework.stereotype.Component;

/** A bound native Plan cannot silently fall back to an older, uncontrolled Worker. */
@Component
public class NativeRunClaimPolicy implements WorkerTaskClaimPolicy {
    @org.springframework.beans.factory.annotation.Autowired private org.springframework.beans.factory.ObjectProvider<me.asu.aidev.questions.NativeQuestions> questions;
    @org.springframework.beans.factory.annotation.Autowired private me.asu.aidev.ai.application.AiProfileCatalog profiles;
    @org.springframework.beans.factory.annotation.Autowired private org.springframework.beans.factory.ObjectProvider<me.asu.aidev.authority.AuthorityTaskPolicy> policy;
    private final NativePlanOwners owners;private final ExecutionGuard guard;
    public NativeRunClaimPolicy(NativePlanOwners owners,ExecutionGuard guard) { this.owners=owners;this.guard=guard; }
    @Override public boolean canClaim(WorkTask task) { return valid(task,null); }
    @Override public boolean canClaim(WorkTask task,Worker worker) { return valid(task,worker); }
    private boolean valid(WorkTask task,Worker worker) {
        boolean requires=task.requiredCapabilities().contains(NativePlanOwners.CAPABILITY);
        if(!task.subjectType().equals("flexible-plan")||guard.managed(task.id().value())||!owners.bound(task.subjectId()))return !requires;
        try {
            owners.task(task.id().value(),true);
            if(worker!=null) {
                var current=profiles.guidance(task.type(),worker.runtimeType(),null);
                current=policy.getObject().guidance(task,worker.runtimeType(),current);
                questions.getObject().pinGuidance(task,worker.runtimeType(),current);
            }
            return worker==null||worker.capabilities().containsAll(java.util.Set.of(NativePlanOwners.CAPABILITY,me.asu.aidev.questions.QuestionContract.LOCATION_CAPABILITY))&&worker.capabilities().stream().anyMatch(c->c.matches("native-config:[0-9a-f]{64}"))&&worker.capabilities().stream().anyMatch(c->c.matches("handoff-store:[0-9a-f]{64}"))&&worker.runtimeType().startsWith("codex-app-server:");
        }catch(IllegalArgumentException|IllegalStateException|SecurityException|java.util.NoSuchElementException error) { return false; }
    }
}
