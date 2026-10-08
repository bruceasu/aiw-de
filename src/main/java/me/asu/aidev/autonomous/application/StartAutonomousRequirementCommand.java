package me.asu.aidev.autonomous.application;

import me.asu.aidev.work.domain.TaskPriority;
import java.util.Set;

public record StartAutonomousRequirementCommand(
        String requestId,
        String requirement,
        String repositoryRef,
        String baseRevision,
        Set<String> technicalCapabilities,
        TaskPriority priority,
        Integer maxParallelism) {
    public StartAutonomousRequirementCommand {
        requestId = normalize(requestId);
        if (requestId != null && !requestId.matches("[A-Za-z0-9._-]{1,80}")) {
            throw new IllegalArgumentException("requestId must match [A-Za-z0-9._-]{1,80}");
        }
        if (requirement == null || requirement.isBlank()) throw new IllegalArgumentException("requirement is blank");
        requirement = requirement.strip();
        if (repositoryRef == null || repositoryRef.isBlank()) throw new IllegalArgumentException("repositoryRef is blank");
        repositoryRef = repositoryRef.strip();
        baseRevision = normalize(baseRevision);
        technicalCapabilities = technicalCapabilities == null ? Set.of() : Set.copyOf(technicalCapabilities);
        priority = priority == null ? TaskPriority.NORMAL : priority;
        if (maxParallelism != null && (maxParallelism < 0 || maxParallelism > 1024)) {
            throw new IllegalArgumentException("maxParallelism must be between 0 and 1024; 0 means unlimited");
        }
    }

    public StartAutonomousRequirementCommand(
            String requestId, String requirement, String repositoryRef, String baseRevision,
            Set<String> technicalCapabilities, TaskPriority priority) {
        this(requestId, requirement, repositoryRef, baseRevision, technicalCapabilities, priority, null);
    }

    public StartAutonomousRequirementCommand(
            String requirement, String repositoryRef, String baseRevision,
            Set<String> technicalCapabilities, TaskPriority priority) {
        this(null, requirement, repositoryRef, baseRevision, technicalCapabilities, priority, null);
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
