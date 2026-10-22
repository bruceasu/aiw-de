package me.asu.aidev.execution.application;

import me.asu.aidev.ai.application.AiProfileCatalog;
import me.asu.aidev.execution.domain.ClaimedTask;
import me.asu.aidev.execution.domain.ExecutionAttempt;
import me.asu.aidev.execution.domain.ExecutionAttemptId;
import me.asu.aidev.execution.domain.ExecutionAttemptState;
import me.asu.aidev.execution.domain.LeaseToken;
import me.asu.aidev.execution.domain.WorkspaceAssignment;
import me.asu.aidev.execution.port.ExecutionAttemptRepository;
import me.asu.aidev.execution.port.TaskClaimPolicy;
import me.asu.aidev.execution.port.WorkerTaskClaimPolicy;
import me.asu.aidev.execution.port.TaskConcurrencyBudget;
import me.asu.aidev.execution.scheduling.PullSchedulingPolicy;
import me.asu.aidev.work.port.TaskDependencyRepository;
import me.asu.aidev.work.port.WorkTaskRepository;
import me.asu.aidev.worker.domain.WorkerId;
import me.asu.aidev.worker.domain.WorkerLiveness;
import me.asu.aidev.worker.port.WorkerRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskClaimService {
    private me.asu.aidev.execution.config.ExecutionConfigurations configurations;
    @Autowired public void setExecutionConfigurations(me.asu.aidev.execution.config.ExecutionConfigurations configurations) { this.configurations=configurations; }
    private me.asu.aidev.execution.guard.ExecutionGuard guard;
    @Autowired
    public void setExecutionGuard(me.asu.aidev.execution.guard.ExecutionGuard guard) { this.guard=guard; }
    private static final Duration SPECIALIST_WINDOW = Duration.ofSeconds(30);
    private final WorkTaskRepository tasks;
    private final WorkerRepository workers;
    private final ExecutionAttemptRepository attempts;
    private final TaskDependencyRepository dependencies;
    private final List<TaskClaimPolicy> claimPolicies;
    private final List<TaskConcurrencyBudget> concurrencyBudgets;
    private final AiProfileCatalog aiProfiles;
    private final me.asu.aidev.workflow.port.WorkflowPlanRepository planPreferences;
    private final Clock clock;
    private final Duration leaseDuration;
    private me.asu.aidev.workflow.application.RecommendationTaskPolicy recommendationPolicy;
    private me.asu.aidev.goal.application.GoalTaskPolicy goalPolicy;

    @Autowired
    public void setGoalPolicy(me.asu.aidev.goal.application.GoalTaskPolicy policy) { this.goalPolicy = policy; }

    @Autowired
    public void setRecommendationPolicy(me.asu.aidev.workflow.application.RecommendationTaskPolicy policy) { this.recommendationPolicy = policy; }

    @Autowired
    public TaskClaimService(WorkTaskRepository tasks,
                            WorkerRepository workers,
                            ExecutionAttemptRepository attempts,
                            TaskDependencyRepository dependencies,
                            List<TaskClaimPolicy> claimPolicies,
                            List<TaskConcurrencyBudget> concurrencyBudgets,
                            AiProfileCatalog aiProfiles,
                            me.asu.aidev.workflow.port.WorkflowPlanRepository planPreferences) {
        this(tasks, workers, attempts, dependencies, claimPolicies, concurrencyBudgets, aiProfiles,
                Clock.systemUTC(), Duration.ofSeconds(60), planPreferences);
    }

    public TaskClaimService(WorkTaskRepository tasks, WorkerRepository workers,
                            ExecutionAttemptRepository attempts, TaskDependencyRepository dependencies,
                            List<TaskClaimPolicy> claimPolicies, List<TaskConcurrencyBudget> concurrencyBudgets,
                            AiProfileCatalog aiProfiles) {
        this(tasks, workers, attempts, dependencies, claimPolicies, concurrencyBudgets, aiProfiles,
                Clock.systemUTC(), Duration.ofSeconds(60), null);
    }

    TaskClaimService(WorkTaskRepository tasks,
                     WorkerRepository workers,
                     ExecutionAttemptRepository attempts,
                     TaskDependencyRepository dependencies,
                     List<TaskClaimPolicy> claimPolicies,
                     List<TaskConcurrencyBudget> concurrencyBudgets,
                     AiProfileCatalog aiProfiles,
                     Clock clock,
                     Duration leaseDuration) {
        this(tasks, workers, attempts, dependencies, claimPolicies, concurrencyBudgets, aiProfiles, clock, leaseDuration, null);
    }

    TaskClaimService(WorkTaskRepository tasks, WorkerRepository workers,
                     ExecutionAttemptRepository attempts, TaskDependencyRepository dependencies,
                     List<TaskClaimPolicy> claimPolicies, List<TaskConcurrencyBudget> concurrencyBudgets,
                     AiProfileCatalog aiProfiles, Clock clock, Duration leaseDuration,
                     me.asu.aidev.workflow.port.WorkflowPlanRepository planPreferences) {
        this.planPreferences = planPreferences;
        this.tasks = tasks;
        this.workers = workers;
        this.attempts = attempts;
        this.dependencies = dependencies;
        this.claimPolicies = List.copyOf(claimPolicies);
        this.concurrencyBudgets = List.copyOf(concurrencyBudgets);
        this.aiProfiles = aiProfiles;
        this.clock = clock;
        this.leaseDuration = leaseDuration;
    }

    @Transactional
    public Optional<ClaimedTask> claimNext(WorkerId workerId) {
        var now = clock.instant();
        var worker = workers.find(workerId)
                .orElseThrow(() -> new IllegalStateException("worker not found: " + workerId.value()));

        var preferred = planPreferences == null ? java.util.Map.<me.asu.aidev.work.domain.TaskId, java.util.Set<String>>of()
                : planPreferences.preferences();
        var available = preferred.isEmpty() ? List.<me.asu.aidev.worker.domain.Worker>of()
                : workers.findAvailable(now.minus(WorkerLiveness.HEARTBEAT_WINDOW));
        var rankedCandidates = PullSchedulingPolicy.rank(
                tasks.findReadySchedulingCandidates().stream()
                        .filter(candidate -> worker.supports(candidate.task().requiredCapabilities()))
                        .toList(),
                now, candidate -> {
                    var skills = preferred.getOrDefault(candidate.task().id(), java.util.Set.of());
                    return (int) skills.stream().filter(worker.capabilities()::contains).count();
                });
        for (var schedulingCandidate : rankedCandidates) {
            var candidate = schedulingCandidate.task();
            var skills = preferred.getOrDefault(candidate.id(), java.util.Set.<String>of());
            if (!skills.isEmpty() && now.isBefore(candidate.updatedAt().plus(SPECIALIST_WINDOW))) {
                int currentScore = (int) skills.stream().filter(worker.capabilities()::contains).count();
                boolean betterAvailable = available.stream().anyMatch(other -> !other.id().equals(workerId)
                        && other.supports(candidate.requiredCapabilities())
                        && aiProfiles.canRun(candidate.type(), other.runtimeType())
                        && skills.stream().filter(other.capabilities()::contains).count() > currentScore);
                if (betterAvailable) continue;
            }
            if ((recommendationPolicy == null || !recommendationPolicy.managed(candidate))
                    && (goalPolicy == null || !goalPolicy.managed(candidate))
                    && !aiProfiles.canRun(candidate.type(), worker.runtimeType())) continue;
            boolean allowed = true;
            for (TaskClaimPolicy policy : claimPolicies) {
                boolean accepted = policy instanceof WorkerTaskClaimPolicy workerPolicy
                        ? workerPolicy.canClaim(candidate, worker) : policy.canClaim(candidate);
                if (!accepted) { allowed = false; break; }
            }
            if (!allowed) continue;

            // Admission comes before worker capacity so autonomous operations use a stable
            // run -> worker -> task lock order. reserveExecutionSlot() is the atomic semaphore.
            if (!reserveConcurrency(candidate, now)) continue;
            if (!workers.reserveCapacity(workerId, now)) {
                releaseConcurrency(candidate, now);
                return Optional.empty();
            }

            var token = new LeaseToken(UUID.randomUUID().toString());
            var expiresAt = now.plus(leaseDuration);
            var lease = tasks.claim(candidate.id(), workerId, token, now, expiresAt, now);
            if (lease.isEmpty()) {
                releaseConcurrency(candidate, now);
                if (!workers.releaseCapacity(workerId, now)) {
                    throw new IllegalStateException("worker capacity accounting underflow after lost claim race: " + workerId.value());
                }
                // The admission row may remain transaction-locked until return; poll again rather
                // than acquiring another run's budget row in the same transaction.
                return Optional.empty();
            }

            int attemptNo = tasks.attemptNo(candidate.id());
            var attemptId = new ExecutionAttemptId(UUID.randomUUID().toString());
            var claimedTask = tasks.find(candidate.id()).orElseThrow();
            attempts.insert(new ExecutionAttempt(
                    attemptId, candidate.id(), attemptNo, claimedTask.instructionRevision(), workerId, token,
                    ExecutionAttemptState.CLAIMED, null, now, null, null));
            if(guard!=null) guard.claimed(claimedTask.id().value(),new me.asu.aidev.execution.guard.GuardContract.Lease(
                    workerId.value(),attemptId.value(),token.value()));
            if(configurations!=null) configurations.claimed(claimedTask.id().value(),attemptId.value(),workerId.value());

            var inputs = guard!=null && guard.managed(claimedTask.id().value()) ? guard.context(claimedTask.id().value()).snapshot().inputs()
                    : dependencies.findDependencies(claimedTask.id()).stream()
                    .map(dep -> attempts.findCompletedInput(dep)
                            .orElseThrow(() -> new IllegalStateException("completed dependency has no execution result: " + dep.value())))
                    .toList();
            return Optional.of(new ClaimedTask(
                    claimedTask, lease.orElseThrow(), attemptId, attemptNo,
                    WorkspaceAssignment.forTask(claimedTask, attemptNo), inputs));
        }
        return Optional.empty();
    }

    private boolean reserveConcurrency(me.asu.aidev.work.domain.WorkTask task, Instant now) {
        java.util.ArrayList<TaskConcurrencyBudget> acquired = new java.util.ArrayList<>();
        for (TaskConcurrencyBudget budget : concurrencyBudgets) {
            if (budget.tryReserve(task, now)) {
                acquired.add(budget);
                continue;
            }
            for (int i = acquired.size() - 1; i >= 0; i--) acquired.get(i).release(task, now);
            return false;
        }
        return true;
    }

    private void releaseConcurrency(me.asu.aidev.work.domain.WorkTask task, Instant now) {
        for (int i = concurrencyBudgets.size() - 1; i >= 0; i--) concurrencyBudgets.get(i).release(task, now);
    }
}
