package me.asu.aidev.execution.port;

import me.asu.aidev.work.domain.WorkTask;
import me.asu.aidev.worker.domain.Worker;

/** Optional worker-specific authority without changing existing claim policies. */
public interface WorkerTaskClaimPolicy extends TaskClaimPolicy {
    boolean canClaim(WorkTask task, Worker worker);
}
