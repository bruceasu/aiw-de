package me.asu.aidev.work.application;

import me.asu.aidev.work.domain.TaskId;
import me.asu.aidev.work.domain.TaskState;
import me.asu.aidev.work.port.TaskDependencyRepository;
import me.asu.aidev.work.port.WorkTaskRepository;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class DependencyResolver {
    private final WorkTaskRepository tasks;
    private final TaskDependencyRepository dependencies;
    private final Clock clock;

    @Autowired
    public DependencyResolver(WorkTaskRepository tasks, TaskDependencyRepository dependencies) {
        this(tasks, dependencies, Clock.systemUTC());
    }

    DependencyResolver(WorkTaskRepository tasks, TaskDependencyRepository dependencies, Clock clock) {
        this.tasks = tasks;
        this.dependencies = dependencies;
        this.clock = clock;
    }

    /**
     * Resolves a newly-created dependent task after its dependency edges have been persisted.
     *
     * Locking predecessors closes the race where a predecessor completes concurrently with
     * dependent-task creation: either creation observes the committed completion, or completion
     * runs after the new dependency edge becomes visible and refreshes the dependent itself.
     */
    public boolean refreshAfterCreation(TaskId taskId) {
        var dependencyIds = dependencies.findDependencies(taskId).stream()
                .sorted(Comparator.comparing(TaskId::value))
                .toList();
        for (TaskId dependencyId : dependencyIds) {
            if (!tasks.lock(dependencyId)) {
                throw new IllegalStateException("dependency not found: " + dependencyId.value());
            }
        }
        return refresh(taskId);
    }

    public List<TaskId> refreshDependents(TaskId completedTaskId) {
        List<TaskId> newlyReady = new ArrayList<>();
        for (TaskId dependentId : dependencies.findDependents(completedTaskId)) {
            if (refresh(dependentId)) newlyReady.add(dependentId);
        }
        return List.copyOf(newlyReady);
    }

    public boolean refresh(TaskId taskId) {
        if (!tasks.lock(taskId)) return false;
        var task = tasks.find(taskId).orElseThrow(() -> new IllegalStateException("task not found: " + taskId.value()));
        if (task.state() != TaskState.BLOCKED) return false;

        boolean allCompleted = dependencies.findDependencies(taskId).stream()
                .map(id -> tasks.find(id).orElseThrow(() -> new IllegalStateException("dependency not found: " + id.value())))
                .allMatch(dep -> dep.state() == TaskState.COMPLETED);
        return allCompleted && tasks.transitionState(taskId, TaskState.BLOCKED, TaskState.READY, clock.instant());
    }
}
