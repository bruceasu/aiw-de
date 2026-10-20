package me.asu.aidev.workflow.application;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import me.asu.aidev.execution.domain.WorkspaceAssignment;
import me.asu.aidev.execution.port.ExecutionAttemptRepository;
import me.asu.aidev.work.domain.TaskState;
import me.asu.aidev.work.domain.WorkTask;
import me.asu.aidev.work.port.WorkTaskRepository;
import me.asu.aidev.workflow.domain.WorkflowPlan;
import me.asu.aidev.workflow.jdbc.JdbcWorkflowHumanOutcomeRepository;
import me.asu.aidev.workflow.port.WorkflowPlanRepository;
import me.asu.aidev.workspace.application.ManagedWorkspaceService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Cancels a plan only after its local, isolated Git branches can be safely discarded. */
@Service
public class WorkflowCancellationService {
    private me.asu.aidev.secretary.application.SecretaryPlanGate secretaryGate;
    @org.springframework.beans.factory.annotation.Autowired
    public void setSecretaryGate(me.asu.aidev.secretary.application.SecretaryPlanGate gate) { this.secretaryGate=gate; }
    private static final Duration GIT_TIMEOUT = Duration.ofSeconds(15);
    private final WorkflowPlanService service;
    private final WorkflowPlanRepository plans;
    private final WorkTaskRepository tasks;
    private final ExecutionAttemptRepository attempts;
    private final ManagedWorkspaceService workspaces;
    private final JdbcWorkflowHumanOutcomeRepository outcomes;

    public WorkflowCancellationService(WorkflowPlanService service, WorkflowPlanRepository plans,
                                       WorkTaskRepository tasks, ExecutionAttemptRepository attempts,
                                       ManagedWorkspaceService workspaces,
                                       JdbcWorkflowHumanOutcomeRepository outcomes) {
        this.service = service;
        this.plans = plans;
        this.tasks = tasks;
        this.attempts = attempts;
        this.workspaces = workspaces;
        this.outcomes = outcomes;
    }

    @Transactional
    public WorkflowPlan cancel(String id, long version, String actor, String reason) {
        if(secretaryGate!=null) secretaryGate.serializePlan(id);
        if (reason == null || reason.isBlank() || reason.length() > 2000) {
            throw new IllegalArgumentException("cancellation reason must contain 1 to 2000 characters");
        }
        if (actor == null || actor.isBlank()) throw new IllegalArgumentException("actor is required");
        WorkflowPlan plan = service.get(id);
        if (plan.version() != version || (plan.state() != WorkflowPlan.State.CONFIRMED
                && plan.state() != WorkflowPlan.State.STOPPED)) {
            throw new IllegalStateException("plan state or version changed; reload before cancelling");
        }
        if (outcomes.find(id).isPresent()) throw new IllegalStateException("plan has a human final decision");
        List<WorkTask> work = service.steps(id).stream().sorted(Comparator.comparing(t -> t.id().value())).toList();
        for (WorkTask task : work) if (!tasks.lock(task.id())) throw new IllegalStateException("plan task disappeared");
        work = work.stream().map(t -> tasks.find(t.id()).orElseThrow()).toList();
        if (work.stream().anyMatch(t -> t.state() == TaskState.CLAIMED || t.state() == TaskState.RUNNING)) {
            throw new IllegalStateException("active Worker must be stopped and acknowledged before cancellation");
        }
        List<Branch> branches = preflight(work);
        for (Branch branch : branches) {
            if (git(branch.repository(), "branch", "-D", branch.name()).code() != 0) {
                throw new IllegalStateException("local branch could not be removed; cancellation was not recorded");
            }
        }
        if (plan.state() == WorkflowPlan.State.CONFIRMED) {
            service.withdrawAfterRollback(id, version, actor, reason);
        } else if (!plans.transition(id, version, WorkflowPlan.State.STOPPED, WorkflowPlan.State.WITHDRAWN,
                actor, reason.strip(), Instant.now())) {
            throw new IllegalStateException("plan changed during cancellation");
        }
        outcomes.insert(id, "CANCEL", "APPLIED", actor, reason.strip(), Instant.now());
        return service.get(id);
    }

    private List<Branch> preflight(List<WorkTask> work) {
        List<Branch> branches = new ArrayList<>();
        for (WorkTask task : work) {
            if (!task.requiresWorkspace()) continue;
            if (task.baseRevision() == null) {
                throw new IllegalStateException("direct shared Workspace cannot be automatically restored");
            }
            var target = workspaces.findTarget(task.repositoryRef())
                    .orElseThrow(() -> new IllegalStateException("Workspace target is unavailable"));
            Path repository = Path.of(target.repositoryPath());
            var output = attempts.findCompletedInput(task.id());
            if (output.isPresent() && output.orElseThrow().publicationRef() != null) {
                throw new IllegalStateException("published Git result requires manual reversal");
            }
            for (int attempt = 1; attempt <= tasks.attemptNo(task.id()); attempt++) {
                String branch = WorkspaceAssignment.forTask(task, attempt).branchName();
                GitResult ref = git(repository, "rev-parse", "--verify", "refs/heads/" + branch + "^{commit}");
                if (ref.code() != 0) {
                    if (output.isPresent() && output.orElseThrow().resultRevision() != null
                            && !output.orElseThrow().resultRevision().equalsIgnoreCase(task.baseRevision())) {
                        GitResult merged = git(repository, "merge-base", "--is-ancestor",
                                output.orElseThrow().resultRevision(), "HEAD");
                        if (merged.code() != 1) {
                            throw new IllegalStateException("missing branch result may have reached the shared branch");
                        }
                    }
                    continue;
                }
                String tip = ref.output().strip();
                if (output.isPresent() && output.orElseThrow().resultRevision() != null
                        && !tip.equalsIgnoreCase(output.orElseThrow().resultRevision())) {
                    throw new IllegalStateException("local result branch changed after Worker completion");
                }
                if (output.isEmpty() && !tip.equalsIgnoreCase(task.baseRevision())) {
                    throw new IllegalStateException("unfinished branch contains unverified changes");
                }
                if (!tip.equalsIgnoreCase(task.baseRevision())) {
                    GitResult merged = git(repository, "merge-base", "--is-ancestor", tip, "HEAD");
                    if (merged.code() != 1) {
                        throw new IllegalStateException("result may have reached the shared branch; manual reversal required");
                    }
                }
                branches.add(new Branch(repository, branch));
            }
        }
        return branches;
    }

    private static GitResult git(Path repository, String... args) {
        List<String> command = new ArrayList<>(List.of("git", "-C", repository.toString()));
        command.addAll(List.of(args));
        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            if (!process.waitFor(GIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                throw new IllegalStateException("local Git operation timed out");
            }
            return new GitResult(process.exitValue(), new String(process.getInputStream().readNBytes(256)));
        } catch (IOException e) {
            throw new IllegalStateException("local Git operation is unavailable", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("local Git operation interrupted", e);
        }
    }

    private record Branch(Path repository, String name) {}
    private record GitResult(int code, String output) {}
}
