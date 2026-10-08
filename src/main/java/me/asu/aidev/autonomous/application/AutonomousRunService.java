package me.asu.aidev.autonomous.application;

import me.asu.aidev.autonomous.domain.AutonomousRun;
import me.asu.aidev.autonomous.domain.AutonomousRunControlState;
import me.asu.aidev.autonomous.domain.AutonomousRunEventType;
import me.asu.aidev.autonomous.domain.AutonomousRunStatus;
import me.asu.aidev.autonomous.port.AutonomousRunEventRepository;
import me.asu.aidev.autonomous.port.AutonomousRunRepository;
import me.asu.aidev.execution.port.ExecutionAttemptRepository;
import me.asu.aidev.execution.port.TaskConcurrencyBudget;
import me.asu.aidev.work.domain.TaskState;
import me.asu.aidev.work.port.WorkTaskRepository;
import me.asu.aidev.worker.port.WorkerRepository;
import java.time.Clock;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AutonomousRunService {
    private final AutonomousRunRepository runs;
    private final WorkTaskRepository tasks;
    private final AutonomousRunEventRepository events;
    private final ExecutionAttemptRepository attempts;
    private final WorkerRepository workers;
    private final java.util.List<TaskConcurrencyBudget> concurrencyBudgets;
    private final Clock clock;

    @Autowired
    public AutonomousRunService(AutonomousRunRepository runs, WorkTaskRepository tasks,
                                ExecutionAttemptRepository attempts, WorkerRepository workers,
                                AutonomousRunEventRepository events,
                                java.util.List<TaskConcurrencyBudget> concurrencyBudgets) {
        this(runs, tasks, attempts, workers, events, concurrencyBudgets, Clock.systemUTC());
    }

    AutonomousRunService(AutonomousRunRepository runs, WorkTaskRepository tasks,
                         ExecutionAttemptRepository attempts, WorkerRepository workers,
                         AutonomousRunEventRepository events, java.util.List<TaskConcurrencyBudget> concurrencyBudgets, Clock clock) {
        this.runs = Objects.requireNonNull(runs);
        this.tasks = Objects.requireNonNull(tasks);
        this.attempts = Objects.requireNonNull(attempts);
        this.workers = Objects.requireNonNull(workers);
        this.events = Objects.requireNonNull(events);
        this.concurrencyBudgets = java.util.List.copyOf(concurrencyBudgets);
        this.clock = Objects.requireNonNull(clock);
    }

    @Transactional
    public AutonomousRun createIfAbsent(String runId, me.asu.aidev.work.domain.TaskId requirementTaskId,
                                        int maxParallelism, boolean requireBudgetMatch) {
        var now = clock.instant();
        var candidate = new AutonomousRun(
                runId, requirementTaskId, AutonomousRunStatus.RUNNING, AutonomousRunControlState.ACTIVE,
                null, null, null, null, null, 0, 0, null, null,
                0, 0, null, null, null, null, null, maxParallelism, 0, now, now, null);
        if (runs.insertIfAbsent(candidate)) return candidate;
        var existing = runs.find(runId).orElseThrow();
        if (!existing.requirementTaskId().equals(requirementTaskId)
                || (requireBudgetMatch && existing.maxParallelism() != maxParallelism)) {
            throw new IllegalStateException("autonomous run id collision with incompatible requirement task or concurrency budget: " + runId);
        }
        return existing;
    }

    @Transactional(readOnly = true)
    public Optional<AutonomousRun> find(String runId) { return runs.find(normalizeRunId(runId)); }

    @Transactional
    public AutonomousRun recover(String runId, String recoveredBy) {
        String id = normalizeRunId(runId);
        String actor = normalizeActor(recoveredBy);
        lockRun(id);
        var run = required(id);
        if (run.controlState() != AutonomousRunControlState.ACTIVE) {
            throw new IllegalStateException("autonomous run recovery requires ACTIVE control state: " + run.controlState());
        }
        if (run.status() != AutonomousRunStatus.FAILED) {
            throw new IllegalStateException("autonomous run is not recoverable from current status: " + run.status());
        }
        var failedTasks = tasks.findBySubjectAndState(AutonomousRequirementService.SUBJECT_TYPE, id, TaskState.FAILED);
        if (failedTasks.isEmpty()) throw new IllegalStateException("autonomous run is FAILED but has no FAILED task: " + id);
        for (var failedTask : failedTasks) {
            if (!tasks.lock(failedTask.id())) throw new IllegalStateException("failed task not found: " + failedTask.id().value());
        }
        var now = clock.instant();
        for (var failedTask : failedTasks) {
            if (!tasks.retryFailed(failedTask.id(), now)) throw new IllegalStateException("cannot requeue failed task: " + failedTask.id().value());
        }
        if (!runs.markRecovered(id, actor, now)) throw new IllegalStateException("autonomous run recovery conflict: " + id);
        appendEvent(id, AutonomousRunEventType.RECOVERED, actor, null, "requeuedFailedTasks=" + failedTasks.size(), now);
        return required(id);
    }

    @Transactional
    public AutonomousRun pause(String runId, String controlledBy, String reason) {
        String id = normalizeRunId(runId);
        String actor = normalizeActor(controlledBy);
        String normalizedReason = normalizeReason(reason, false);
        lockRun(id);
        var run = required(id);
        if (run.controlState() == AutonomousRunControlState.PAUSED) return run;
        if (run.controlState() != AutonomousRunControlState.ACTIVE || run.status() != AutonomousRunStatus.RUNNING) {
            throw new IllegalStateException("autonomous run cannot be paused: status=" + run.status() + ", controlState=" + run.controlState());
        }
        var now = clock.instant();
        for (var task : tasks.findBySubject(AutonomousRequirementService.SUBJECT_TYPE, id)) {
            if (!tasks.lock(task.id())) throw new IllegalStateException("task not found while pausing: " + task.id().value());
            var current = tasks.find(task.id()).orElseThrow();
            if (current.state() != TaskState.CLAIMED && current.state() != TaskState.RUNNING) continue;
            var lease = tasks.findLease(task.id()).orElseThrow(() -> new IllegalStateException("active task has no lease: " + task.id().value()));
            if (!tasks.requeueOwnedForPause(task.id(), lease.workerId(), lease.token(), now)) {
                throw new IllegalStateException("cannot revoke active task for pause: " + task.id().value());
            }
            if (!attempts.terminateActiveLease(lease.token(), now, "OPERATOR_PAUSED")) {
                throw new IllegalStateException("cannot terminate active attempt for pause: " + task.id().value());
            }
            if (!workers.releaseCapacity(lease.workerId(), now)) {
                throw new IllegalStateException("worker capacity accounting underflow while pausing: " + lease.workerId().value());
            }
            releaseConcurrency(current, now);
        }
        if (!runs.markPaused(id, actor, normalizedReason, now)) throw new IllegalStateException("autonomous run pause conflict: " + id);
        appendEvent(id, AutonomousRunEventType.PAUSED, actor, null, normalizedReason, now);
        return required(id);
    }

    @Transactional
    public AutonomousRun resume(String runId, String controlledBy, String reason) {
        String id = normalizeRunId(runId);
        String actor = normalizeActor(controlledBy);
        String normalizedReason = normalizeReason(reason, false);
        lockRun(id);
        var run = required(id);
        if (run.controlState() == AutonomousRunControlState.ACTIVE && run.status() == AutonomousRunStatus.RUNNING) return run;
        if (run.controlState() != AutonomousRunControlState.PAUSED || run.status() != AutonomousRunStatus.RUNNING) {
            throw new IllegalStateException("autonomous run cannot be resumed: status=" + run.status() + ", controlState=" + run.controlState());
        }
        var now = clock.instant();
        if (!runs.markResumed(id, actor, normalizedReason, now)) throw new IllegalStateException("autonomous run resume conflict: " + id);
        appendEvent(id, AutonomousRunEventType.RESUMED, actor, null, normalizedReason, now);
        return required(id);
    }

    @Transactional
    public AutonomousRun updateMaxParallelism(String runId, int maxParallelism, String controlledBy, String reason) {
        String id = normalizeRunId(runId);
        String actor = normalizeActor(controlledBy);
        String normalizedReason = normalizeReason(reason, false);
        if (maxParallelism < 0 || maxParallelism > 1024) {
            throw new IllegalArgumentException("maxParallelism must be between 0 and 1024; 0 means unlimited");
        }
        lockRun(id);
        var run = required(id);
        var requirement = tasks.find(run.requirementTaskId()).orElseThrow();
        if (requirement.repositoryRef() != null && requirement.baseRevision() == null && maxParallelism != 1) {
            throw new IllegalArgumentException("direct directory Run maxParallelism must be 1");
        }
        if (run.controlState() == AutonomousRunControlState.CANCELLED
                || (run.status() != AutonomousRunStatus.RUNNING && run.status() != AutonomousRunStatus.FAILED)) {
            throw new IllegalStateException("autonomous run scheduling cannot be changed: status="
                    + run.status() + ", controlState=" + run.controlState());
        }
        if (run.maxParallelism() == maxParallelism) return run;

        var now = clock.instant();
        int old = run.maxParallelism();
        int active = run.activeSlots();
        if (!runs.updateMaxParallelism(id, maxParallelism, now)) {
            throw new IllegalStateException("autonomous run scheduling update conflict: " + id);
        }
        String detail = "maxParallelism=" + old + "->" + maxParallelism
                + ", activeSlots=" + active
                + (normalizedReason == null ? "" : ", reason=" + normalizedReason);
        if (detail.length() > 4096) detail = detail.substring(0, 4096);
        appendEvent(id, AutonomousRunEventType.MAX_PARALLELISM_CHANGED, actor, null, detail, now);
        return required(id);
    }

    @Transactional
    public AutonomousRun cancel(String runId, String controlledBy, String reason) {
        String id = normalizeRunId(runId);
        String actor = normalizeActor(controlledBy);
        String normalizedReason = normalizeReason(reason, true);
        lockRun(id);
        var run = required(id);
        if (run.controlState() == AutonomousRunControlState.CANCELLED) return run;
        if (run.status() != AutonomousRunStatus.RUNNING && run.status() != AutonomousRunStatus.FAILED) {
            throw new IllegalStateException("autonomous run cannot be cancelled after engineering/approval authority: " + run.status());
        }
        var now = clock.instant();
        for (var task : tasks.findBySubject(AutonomousRequirementService.SUBJECT_TYPE, id)) {
            if (!tasks.lock(task.id())) throw new IllegalStateException("task not found while cancelling: " + task.id().value());
            var current = tasks.find(task.id()).orElseThrow();
            switch (current.state()) {
                case COMPLETED, CANCELLED -> { }
                case CLAIMED, RUNNING -> {
                    var lease = tasks.findLease(task.id()).orElseThrow(() -> new IllegalStateException("active task has no lease: " + task.id().value()));
                    if (!tasks.cancelOwned(task.id(), lease.workerId(), lease.token(), now)) {
                        throw new IllegalStateException("cannot cancel active task: " + task.id().value());
                    }
                    if (!attempts.terminateActiveLease(lease.token(), now, "OPERATOR_CANCELLED")) {
                        throw new IllegalStateException("cannot terminate active attempt while cancelling: " + task.id().value());
                    }
                    if (!workers.releaseCapacity(lease.workerId(), now)) {
                        throw new IllegalStateException("worker capacity accounting underflow while cancelling: " + lease.workerId().value());
                    }
                    releaseConcurrency(current, now);
                }
                case BLOCKED, READY, FAILED -> {
                    if (!tasks.cancelInactive(task.id(), current.state(), now)) {
                        throw new IllegalStateException("cannot cancel task: " + task.id().value());
                    }
                }
            }
        }
        if (!runs.markCancelled(id, actor, normalizedReason, now)) throw new IllegalStateException("autonomous run cancel conflict: " + id);
        appendEvent(id, AutonomousRunEventType.CANCELLED, actor, null, normalizedReason, now);
        return required(id);
    }

    private void releaseConcurrency(me.asu.aidev.work.domain.WorkTask task, java.time.Instant at) {
        for (TaskConcurrencyBudget budget : concurrencyBudgets) budget.release(task, at);
    }

    private void appendEvent(String runId, AutonomousRunEventType type, String actor, me.asu.aidev.work.domain.TaskId taskId, String detail, java.time.Instant at) {
        events.append(runId, type, actor, taskId, detail, at);
    }

    private void lockRun(String id) {
        if (!runs.lock(id)) throw new NoSuchElementException("autonomous run not found: " + id);
    }
    private AutonomousRun required(String id) {
        return runs.find(id).orElseThrow(() -> new NoSuchElementException("autonomous run not found: " + id));
    }
    private static String normalizeRunId(String runId) {
        if (runId == null || runId.isBlank()) throw new IllegalArgumentException("runId is blank");
        return runId.strip();
    }
    private static String normalizeActor(String actor) {
        if (actor == null || actor.isBlank()) throw new IllegalArgumentException("controlledBy is blank");
        String v = actor.strip();
        if (v.length() > 256) throw new IllegalArgumentException("controlledBy exceeds 256 characters");
        return v;
    }
    private static String normalizeReason(String reason, boolean required) {
        if (reason == null || reason.isBlank()) {
            if (required) throw new IllegalArgumentException("reason is required for cancellation");
            return null;
        }
        String v = reason.strip();
        if (v.length() > 4096) throw new IllegalArgumentException("reason exceeds 4096 characters");
        return v;
    }
}
