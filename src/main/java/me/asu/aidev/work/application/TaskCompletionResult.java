package me.asu.aidev.work.application;

import me.asu.aidev.work.domain.TaskId;
import java.util.List;

public record TaskCompletionResult(
        TaskId completedTaskId,
        List<TaskId> newlyReadyTaskIds,
        List<TaskId> generatedTaskIds) {
    public TaskCompletionResult {
        newlyReadyTaskIds = newlyReadyTaskIds == null ? List.of() : List.copyOf(newlyReadyTaskIds);
        generatedTaskIds = generatedTaskIds == null ? List.of() : List.copyOf(generatedTaskIds);
    }
}
