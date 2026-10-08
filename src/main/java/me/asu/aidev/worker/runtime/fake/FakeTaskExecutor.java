package me.asu.aidev.worker.runtime.fake;

import me.asu.aidev.execution.domain.ClaimedTask;
import me.asu.aidev.work.domain.TaskId;
import me.asu.aidev.worker.runtime.TaskExecutor;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

/** Deterministic fake execution adapter used to validate the R3 worker runtime before Codex integration. */
public final class FakeTaskExecutor implements TaskExecutor {
    private final Duration executionTime;
    private final Set<TaskId> failTasks;
    private final Set<TaskId> observed = ConcurrentHashMap.newKeySet();
    private final AtomicInteger active = new AtomicInteger();
    private final AtomicInteger maxActive = new AtomicInteger();

    public FakeTaskExecutor(Duration executionTime) {
        this(executionTime, Set.of());
    }

    public FakeTaskExecutor(Duration executionTime, Set<TaskId> failTasks) {
        if (executionTime == null || executionTime.isNegative()) {
            throw new IllegalArgumentException("executionTime must be >= 0");
        }
        this.executionTime = executionTime;
        this.failTasks = failTasks == null ? Set.of() : Set.copyOf(failTasks);
    }

    @Override
    public void execute(ClaimedTask task) throws Exception {
        executeWithStopSignal(task, () -> false);
    }

    @Override
    public void execute(ClaimedTask task, BooleanSupplier stopRequested) throws Exception {
        executeWithStopSignal(task, stopRequested);
    }

    @Override
    public ExecutionOutcome executeWithStopSignal(ClaimedTask task, BooleanSupplier stopRequested) throws Exception {
        int running = active.incrementAndGet();
        maxActive.accumulateAndGet(running, Math::max);
        try {
            observed.add(task.task().id());
            long remaining = executionTime.toMillis();
            while (remaining > 0 && !stopRequested.getAsBoolean()) {
                long interval = Math.min(remaining, 50);
                Thread.sleep(interval);
                remaining -= interval;
            }
            if (remaining > 0 && stopRequested.getAsBoolean()) return ExecutionOutcome.STOPPED;
            if (failTasks.contains(task.task().id())) {
                throw new FakeExecutionException("configured fake failure: " + task.task().id().value());
            }
            return ExecutionOutcome.COMPLETED;
        } finally {
            active.decrementAndGet();
        }
    }

    public Set<TaskId> observedTasks() {
        return Set.copyOf(observed);
    }

    public int maxActive() {
        return maxActive.get();
    }

    public static final class FakeExecutionException extends RuntimeException {
        public FakeExecutionException(String message) {
            super(message);
        }
    }
}
