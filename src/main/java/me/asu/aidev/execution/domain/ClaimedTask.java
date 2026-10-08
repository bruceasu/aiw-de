package me.asu.aidev.execution.domain;

import me.asu.aidev.work.domain.WorkTask;
import java.util.List;
import java.util.Objects;

public record ClaimedTask(
        WorkTask task,
        TaskLease lease,
        ExecutionAttemptId attemptId,
        int attemptNo,
        WorkspaceAssignment workspace,
        List<TaskInput> inputs) {
    public ClaimedTask {
        Objects.requireNonNull(task, "task");
        Objects.requireNonNull(lease, "lease");
        Objects.requireNonNull(attemptId, "attemptId");
        if (attemptNo < 1) throw new IllegalArgumentException("attemptNo must be >= 1");
        if (workspace != null && !task.requiresWorkspace()) {
            throw new IllegalArgumentException("workspace assignment provided for task without repository");
        }
        inputs = inputs == null ? List.of() : List.copyOf(inputs);
    }
}
