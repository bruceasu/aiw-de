package me.asu.aidev.autonomous.application;

import me.asu.aidev.execution.application.TaskCompletionContext;
import me.asu.aidev.work.application.CreateWorkTaskCommand;
import me.asu.aidev.work.application.WorkTaskService;
import me.asu.aidev.work.domain.TaskId;
import me.asu.aidev.work.domain.TaskState;
import me.asu.aidev.work.domain.TaskType;
import me.asu.aidev.work.domain.WorkTask;
import me.asu.aidev.work.port.WorkTaskRepository;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * R8-S3 join for autonomous runs that materialized more than one IMPLEMENTATION task.
 * The last completed branch creates exactly one INTEGRATION task depending on every branch result.
 */
@Component
public class ParallelImplementationIntegrationCoordinator {
    public static final String AUTONOMOUS_SUBJECT = "AUTONOMOUS_REQUIREMENT";

    private final WorkTaskRepository tasks;
    private final WorkTaskService taskService;

    public ParallelImplementationIntegrationCoordinator(WorkTaskRepository tasks, WorkTaskService taskService) {
        this.tasks = tasks;
        this.taskService = taskService;
    }

    /**
     * Empty Optional means this is not a parallel autonomous implementation and normal code-change fan-out applies.
     * Present Optional means this coordinator owns the completion, even when it must wait for sibling branches.
     */
    public Optional<List<TaskId>> onImplementationCompleted(TaskCompletionContext context) {
        WorkTask completed = context.completedTask();
        if (completed.type() != TaskType.IMPLEMENTATION
                || !AUTONOMOUS_SUBJECT.equals(completed.subjectType())) {
            return Optional.empty();
        }

        List<WorkTask> implementations = tasks.findBySubjectAndType(
                completed.subjectType(), completed.subjectId(), TaskType.IMPLEMENTATION);
        if (implementations.size() <= 1) return Optional.empty();

        if (implementations.stream().anyMatch(task -> task.state() != TaskState.COMPLETED)) {
            return Optional.of(List.of());
        }

        String repositoryRef = requireCommonRepository(implementations);
        String baseRevision = requireCommonBaseRevision(implementations);
        WorkTask planning = tasks.find(AutonomousTaskIds.planning(completed.subjectId()))
                .orElseThrow(() -> new IllegalStateException("implementation planning task not found for run: "
                        + completed.subjectId()));

        TaskId integrationId = AutonomousTaskIds.integration(completed.subjectId());
        var dependencyIds = new LinkedHashSet<TaskId>();
        implementations.forEach(task -> dependencyIds.add(task.id()));

        taskService.createIfAbsent(new CreateWorkTaskCommand(
                integrationId,
                TaskType.INTEGRATION,
                completed.subjectType(),
                completed.subjectId(),
                AutonomousCapabilities.forRole(planning.requiredCapabilities(), "integration"),
                "integration",
                "Integrate every authoritative upstream implementation result into one coherent repository state. " +
                        "Start from the immutable base revision, inspect every inputs[] resultRevision/publicationRef, " +
                        "merge or cherry-pick all required changes, resolve conflicts without dropping any work item, " +
                        "run focused build/tests, and leave one reviewable integrated commit. Do not silently omit a branch.",
                repositoryRef,
                baseRevision,
                completed.priority(),
                Set.copyOf(dependencyIds)));
        return Optional.of(List.of(integrationId));
    }

    private static String requireCommonRepository(List<WorkTask> tasks) {
        String value = tasks.getFirst().repositoryRef();
        if (value == null) throw new IllegalStateException("parallel implementation requires repository workspace");
        if (tasks.stream().anyMatch(task -> !value.equals(task.repositoryRef()))) {
            throw new IllegalStateException("parallel implementation tasks must share one repositoryRef");
        }
        return value;
    }

    private static String requireCommonBaseRevision(List<WorkTask> tasks) {
        String value = tasks.getFirst().baseRevision();
        if (value == null) throw new IllegalStateException("parallel implementation requires immutable baseRevision");
        if (tasks.stream().anyMatch(task -> !value.equals(task.baseRevision()))) {
            throw new IllegalStateException("parallel implementation tasks must share one baseRevision");
        }
        return value;
    }
}
