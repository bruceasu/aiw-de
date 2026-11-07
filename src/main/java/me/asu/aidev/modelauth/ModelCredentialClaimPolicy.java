package me.asu.aidev.modelauth;

import me.asu.aidev.execution.port.WorkerTaskClaimPolicy;
import me.asu.aidev.work.domain.WorkTask;
import me.asu.aidev.worker.domain.Worker;
import org.springframework.stereotype.Component;

@Component
public class ModelCredentialClaimPolicy implements WorkerTaskClaimPolicy {
    private final ModelConnections credentials;
    public ModelCredentialClaimPolicy(ModelConnections credentials) {this.credentials=credentials;}
    @Override public boolean canClaim(WorkTask task) {return false;}
    @Override public boolean canClaim(WorkTask task,Worker worker) {return credentials.canClaim(task,worker);}
}
