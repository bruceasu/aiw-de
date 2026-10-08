package me.asu.aidev.worker.runtime;

import me.asu.aidev.execution.domain.ClaimedTask;
import java.util.function.BooleanSupplier;

@FunctionalInterface
public interface TaskExecutor {
    void execute(ClaimedTask task) throws Exception;

    default void execute(ClaimedTask task, BooleanSupplier stopRequested) throws Exception {
        execute(task);
    }

    /**
     * Executes with cooperative-stop support. Returning {@link ExecutionOutcome#STOPPED} is an
     * explicit confirmation that the executor observed the stop request and stopped its work.
     * Legacy executors remain source-compatible, but a normal return is never treated as a stop
     * acknowledgement.
     */
    default ExecutionOutcome executeWithStopSignal(ClaimedTask task, BooleanSupplier stopRequested)
            throws Exception {
        execute(task, stopRequested);
        return ExecutionOutcome.COMPLETED;
    }

    enum ExecutionOutcome {
        COMPLETED,
        STOPPED
    }
}
