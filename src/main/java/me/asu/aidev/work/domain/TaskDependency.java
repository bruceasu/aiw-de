package me.asu.aidev.work.domain;

import java.util.Objects;

public record TaskDependency(TaskId taskId, TaskId dependsOnTaskId) {
    public TaskDependency {
        Objects.requireNonNull(taskId, "taskId");
        Objects.requireNonNull(dependsOnTaskId, "dependsOnTaskId");
        if (taskId.equals(dependsOnTaskId)) throw new IllegalArgumentException("task cannot depend on itself");
    }
}
