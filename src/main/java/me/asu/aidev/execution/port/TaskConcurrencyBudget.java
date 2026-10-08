package me.asu.aidev.execution.port;

import me.asu.aidev.work.domain.WorkTask;
import java.time.Instant;

/**
 * Capacity accounting applied at the worker-pull claim boundary.
 * Implementations must reserve atomically and release in the same transaction as lease termination.
 */
public interface TaskConcurrencyBudget {
    /** Returns true when capacity was reserved (or the task is outside this budget's scope). */
    boolean tryReserve(WorkTask task, Instant at);

    /** Releases a previously reserved slot. Tasks outside the budget scope are a no-op. */
    void release(WorkTask task, Instant at);
}
