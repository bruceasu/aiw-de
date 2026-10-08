package me.asu.aidev.execution.domain;

import me.asu.aidev.work.domain.TaskId;
import me.asu.aidev.work.domain.TaskType;
import java.util.List;
import java.util.Objects;

/** Immutable result from a completed predecessor task, supplied to a downstream claimed task. */
public record TaskInput(
        TaskId taskId,
        TaskType taskType,
        String repositoryRef,
        String baseRevision,
        String resultRevision,
        String publicationRef,
        List<String> changedFiles,
        CompletionVerdict completionVerdict,
        String completionSummary) {
    public TaskInput {
        Objects.requireNonNull(taskId, "taskId");
        Objects.requireNonNull(taskType, "taskType");
        changedFiles = changedFiles == null ? List.of() : List.copyOf(changedFiles);
        publicationRef = normalize(publicationRef);
        completionSummary = normalize(completionSummary);
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
