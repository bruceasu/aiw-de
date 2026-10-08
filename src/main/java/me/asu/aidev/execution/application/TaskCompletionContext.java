package me.asu.aidev.execution.application;

import me.asu.aidev.execution.domain.CompletionReport;
import me.asu.aidev.execution.domain.WorkspaceResult;
import me.asu.aidev.work.domain.TaskState;
import me.asu.aidev.work.domain.WorkTask;
import java.util.Objects;

/** Immutable input supplied to completion handlers after authoritative completion is persisted. */
public record TaskCompletionContext(
        WorkTask completedTask,
        WorkspaceResult workspaceResult,
        CompletionReport completionReport) {
    public TaskCompletionContext {
        Objects.requireNonNull(completedTask, "completedTask");
        if (completedTask.state() != TaskState.COMPLETED) {
            throw new IllegalArgumentException("completion handler requires COMPLETED task");
        }
        if (completedTask.baseRevision() != null && workspaceResult == null) {
            throw new IllegalArgumentException("repository task completion requires workspace result");
        }
    }
}
