package me.asu.aidev.execution.application;

import me.asu.aidev.work.domain.TaskId;
import me.asu.aidev.work.domain.TaskType;
import java.util.List;
import java.util.Set;

/** Extension point for materializing new work after a task completes. */
public interface TaskCompletionHandler {
    /** Task types for which this handler is the single deterministic completion authority. */
    Set<TaskType> supportedTypes();

    default boolean supports(TaskCompletionContext context) {
        return supportedTypes().contains(context.completedTask().type());
    }

    List<TaskId> onCompleted(TaskCompletionContext context);
}
