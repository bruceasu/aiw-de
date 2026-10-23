package me.asu.aidev.work.application;

import me.asu.aidev.work.domain.TaskId;
import me.asu.aidev.work.domain.TaskPriority;
import me.asu.aidev.work.domain.TaskType;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

public record CreateWorkTaskCommand(
        TaskId id,
        TaskType type,
        String subjectType,
        String subjectId,
        Set<String> requiredCapabilities,
        String skillRef,
        String instruction,
        String repositoryRef,
        String baseRevision,
        TaskPriority priority,
        Set<TaskId> dependencies) {

    public CreateWorkTaskCommand {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(priority, "priority");
        var capabilities = new LinkedHashSet<String>();
        if (requiredCapabilities != null) capabilities.addAll(requiredCapabilities);
        if(type==TaskType.EXTERNAL_WORK) capabilities.add(me.asu.aidev.execution.skills.SkillFileContract.CAPABILITY);
        repositoryRef = normalize(repositoryRef);
        baseRevision = normalize(baseRevision);
        if (repositoryRef == null && baseRevision != null) {
            throw new IllegalArgumentException("baseRevision requires repositoryRef");
        }
        if (repositoryRef != null && !repositoryRef.contains("/")) capabilities.add("repo:" + repositoryRef);
        requiredCapabilities = Set.copyOf(capabilities);
        dependencies = dependencies == null ? Set.of() : Set.copyOf(dependencies);
        instruction = instruction == null ? "" : instruction.strip();
        if (dependencies.contains(id)) throw new IllegalArgumentException("task cannot depend on itself");
    }

    public CreateWorkTaskCommand(
            TaskId id, TaskType type, String subjectType, String subjectId,
            Set<String> requiredCapabilities, String skillRef, String instruction,
            TaskPriority priority, Set<TaskId> dependencies) {
        this(id, type, subjectType, subjectId, requiredCapabilities, skillRef, instruction,
                null, null, priority, dependencies);
    }

    /** Compatibility convenience for non-agent tests/tasks that do not carry executable instructions. */
    public CreateWorkTaskCommand(
            TaskId id, TaskType type, String subjectType, String subjectId,
            Set<String> requiredCapabilities, String skillRef,
            TaskPriority priority, Set<TaskId> dependencies) {
        this(id, type, subjectType, subjectId, requiredCapabilities, skillRef, "",
                null, null, priority, dependencies);
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) return null;
        return value.strip();
    }
}
