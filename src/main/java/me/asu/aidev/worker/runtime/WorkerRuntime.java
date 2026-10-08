package me.asu.aidev.worker.runtime;

import me.asu.aidev.execution.application.TaskClaimService;
import me.asu.aidev.execution.application.TaskExecutionService;
import me.asu.aidev.execution.domain.ClaimedTask;
import me.asu.aidev.worker.application.RegisterWorkerCommand;
import me.asu.aidev.worker.application.WorkerService;
import me.asu.aidev.worker.domain.WorkerId;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * In-process implementation of the R3 worker protocol semantics.
 *
 * The Control Plane remains authoritative for claim/start/heartbeat/complete/fail.
 * This runtime only performs the worker-side pull loop. R4 can replace this process
 * boundary with an external Codex/HTTP worker without changing task ownership rules.
 */
public final class WorkerRuntime implements AutoCloseable {
    private final RegisterWorkerCommand registration;
    private final WorkerService workers;
    private final TaskClaimService claims;
    private final TaskExecutionService executions;
    private final TaskExecutor executor;
    private final WorkerRuntimeConfig config;
    private final AtomicReference<WorkerRuntimeState> state = new AtomicReference<>(WorkerRuntimeState.NEW);
    private final AtomicInteger inFlight = new AtomicInteger();
    private final AtomicLong nextWorkerHeartbeatNanos = new AtomicLong();

    private volatile ExecutorService slots;
    private volatile ScheduledExecutorService heartbeats;

    WorkerRuntime(RegisterWorkerCommand registration,
                  WorkerService workers,
                  TaskClaimService claims,
                  TaskExecutionService executions,
                  TaskExecutor executor,
                  WorkerRuntimeConfig config) {
        this.registration = Objects.requireNonNull(registration, "registration");
        this.workers = Objects.requireNonNull(workers, "workers");
        this.claims = Objects.requireNonNull(claims, "claims");
        this.executions = Objects.requireNonNull(executions, "executions");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.config = Objects.requireNonNull(config, "config");
    }

    public synchronized void start() {
        if (!state.compareAndSet(WorkerRuntimeState.NEW, WorkerRuntimeState.RUNNING)) {
            throw new IllegalStateException("worker runtime can only be started once: " + state.get());
        }

        workers.register(registration);
        ThreadFactory slotFactory = namedFactory("worker-" + registration.workerId().value() + "-slot-");
        ThreadFactory heartbeatFactory = namedFactory("worker-" + registration.workerId().value() + "-heartbeat-");
        slots = Executors.newFixedThreadPool(registration.maxConcurrency(), slotFactory);
        heartbeats = Executors.newScheduledThreadPool(registration.maxConcurrency(), heartbeatFactory);
        for (int slot = 0; slot < registration.maxConcurrency(); slot++) {
            slots.submit(this::slotLoop);
        }
    }

    public WorkerRuntimeState state() {
        return state.get();
    }

    public WorkerId workerId() {
        return registration.workerId();
    }

    public int inFlight() {
        return inFlight.get();
    }

    public void drain() {
        WorkerRuntimeState current = state.get();
        if (current == WorkerRuntimeState.NEW || current == WorkerRuntimeState.STOPPED || current == WorkerRuntimeState.CRASHED) {
            return;
        }
        if (state.compareAndSet(WorkerRuntimeState.RUNNING, WorkerRuntimeState.DRAINING)) {
            workers.drain(registration.workerId());
        }
    }

    /**
     * Simulates abrupt process loss: no drain/offline update and no lease release.
     * Persisted leases are intentionally left for TaskExecutionService.recoverExpiredLeases().
     */
    public synchronized void crash() {
        WorkerRuntimeState current = state.getAndSet(WorkerRuntimeState.CRASHED);
        if (current == WorkerRuntimeState.STOPPED || current == WorkerRuntimeState.CRASHED) return;
        if (heartbeats != null) {
            heartbeats.shutdownNow();
            await(heartbeats, config.shutdownWait());
        }
        if (slots != null) slots.shutdownNow();
    }

    @Override
    public synchronized void close() {
        WorkerRuntimeState current = state.get();
        if (current == WorkerRuntimeState.STOPPED || current == WorkerRuntimeState.CRASHED) return;
        if (current == WorkerRuntimeState.NEW) {
            state.set(WorkerRuntimeState.STOPPED);
            return;
        }

        drain();
        ExecutorService localSlots = slots;
        if (localSlots != null) {
            localSlots.shutdown();
            await(localSlots, config.shutdownWait());
            if (!localSlots.isTerminated()) localSlots.shutdownNow();
        }
        ScheduledExecutorService localHeartbeats = heartbeats;
        if (localHeartbeats != null) localHeartbeats.shutdownNow();

        workers.offline(registration.workerId());
        state.set(WorkerRuntimeState.STOPPED);
    }

    private void slotLoop() {
        while (state.get() == WorkerRuntimeState.RUNNING) {
            try {
                heartbeatWorkerIfDue();
                var claimed = claims.claimNext(registration.workerId());
                if (claimed.isEmpty()) {
                    sleep(config.idleBackoff());
                    continue;
                }
                executeClaimed(claimed.orElseThrow());
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
            } catch (RuntimeException transientFailure) {
                if (state.get() != WorkerRuntimeState.RUNNING) return;
                // A single worker-side infrastructure failure must not permanently kill the slot.
                // Back off and retry; authoritative state remains in the Control Plane.
                try {
                    sleep(config.idleBackoff());
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    private void executeClaimed(ClaimedTask claimed) throws InterruptedException {
        inFlight.incrementAndGet();
        AtomicBoolean ownershipLost = new AtomicBoolean(false);
        AtomicBoolean stopRequested = new AtomicBoolean(false);
        ScheduledFuture<?> heartbeat = null;
        try {
            try {
                executions.start(claimed.task().id(), registration.workerId(), claimed.lease().token());
            } catch (IllegalStateException startFailure) {
                if (!executions.stopRequested(registration.workerId(), claimed.lease().token())) throw startFailure;
                executions.acknowledgeStop(claimed.task().id(), registration.workerId(), claimed.lease().token());
                return;
            }
            heartbeat = heartbeats.scheduleWithFixedDelay(
                    () -> heartbeat(claimed, ownershipLost, stopRequested),
                    config.heartbeatInterval().toMillis(),
                    config.heartbeatInterval().toMillis(),
                    TimeUnit.MILLISECONDS);

            Exception executionFailure = null;
            TaskExecutor.ExecutionOutcome executionOutcome = TaskExecutor.ExecutionOutcome.COMPLETED;
            try {
                executionOutcome = executor.executeWithStopSignal(claimed, stopRequested::get);
            } catch (InterruptedException interrupted) {
                if (state.get() == WorkerRuntimeState.CRASHED) {
                    Thread.currentThread().interrupt();
                    return;
                }
                executionFailure = interrupted;
                Thread.currentThread().interrupt();
            } catch (Exception failure) {
                executionFailure = failure;
            }

            if (state.get() == WorkerRuntimeState.CRASHED) return;
            if (stopRequested.get()) {
                if (executionOutcome == TaskExecutor.ExecutionOutcome.STOPPED) {
                    executions.acknowledgeStop(claimed.task().id(), registration.workerId(), claimed.lease().token());
                }
                return;
            }
            if (executionOutcome == TaskExecutor.ExecutionOutcome.STOPPED) {
                executionFailure = new IllegalStateException("executor reported stopped without a stop request");
            }
            if (ownershipLost.get()) return;

            if (executionFailure == null) {
                try {
                    executions.complete(claimed.task().id(), registration.workerId(), claimed.lease().token());
                } catch (IllegalStateException completionRace) {
                    acknowledgeStopAfterRace(claimed, completionRace, executionOutcome);
                }
            } else {
                try {
                    executions.fail(claimed.task().id(), registration.workerId(), claimed.lease().token());
                } catch (IllegalStateException failureRace) {
                    acknowledgeStopAfterRace(claimed, failureRace, executionOutcome);
                }
            }
        } catch (IllegalStateException ownershipOrTransitionFailure) {
            // If ownership was concurrently lost/recovered, the Control Plane state is authoritative.
            // The slot returns to polling instead of trying to repair task state locally.
        } finally {
            if (heartbeat != null) heartbeat.cancel(false);
            inFlight.decrementAndGet();
        }
    }


    private void heartbeatWorkerIfDue() {
        long now = System.nanoTime();
        long due = nextWorkerHeartbeatNanos.get();
        if (now < due) return;
        long next = now + config.heartbeatInterval().toNanos();
        if (nextWorkerHeartbeatNanos.compareAndSet(due, next)) {
            workers.heartbeat(registration.workerId());
        }
    }

    private void heartbeat(ClaimedTask claimed, AtomicBoolean ownershipLost, AtomicBoolean stopRequested) {
        if (state.get() == WorkerRuntimeState.CRASHED) return;
        try {
            if (executions.heartbeat(claimed.task().id(), registration.workerId(), claimed.lease().token())) {
                // The executor receives this signal and the attempt is acknowledged only after it returns.
                // The scheduled heartbeat thread owns the same atomic through this closure.
                stopRequested.set(true);
            }
        } catch (RuntimeException lost) {
            ownershipLost.set(true);
        }
    }

    private void acknowledgeStopAfterRace(ClaimedTask claimed, IllegalStateException race,
                                          TaskExecutor.ExecutionOutcome executionOutcome) {
        if (!executions.stopRequested(registration.workerId(), claimed.lease().token())) throw race;
        if (executionOutcome == TaskExecutor.ExecutionOutcome.STOPPED) {
            executions.acknowledgeStop(claimed.task().id(), registration.workerId(), claimed.lease().token());
        }
    }

    private static void sleep(Duration duration) throws InterruptedException {
        Thread.sleep(duration.toMillis());
    }

    private static void await(ExecutorService executor, Duration timeout) {
        try {
            executor.awaitTermination(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private static ThreadFactory namedFactory(String prefix) {
        AtomicInteger sequence = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, prefix + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }
}
