package me.asu.aidev.work.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

public record WorkTask(
        TaskId id,
        TaskType type,
        TaskState state,
        String subjectType,
        String subjectId,
        Set<String> requiredCapabilities,
        String skillRef,
        String instruction,
        int instructionRevision,
        String repositoryRef,
        String baseRevision,
        TaskPriority priority,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    public WorkTask {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(priority, "priority");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (subjectType == null || subjectType.isBlank()) throw new IllegalArgumentException("subjectType is blank");
        if (subjectId == null || subjectId.isBlank()) throw new IllegalArgumentException("subjectId is blank");
        requiredCapabilities = CapabilitySetCodec.decode(CapabilitySetCodec.encode(requiredCapabilities));
        instruction = instruction == null ? "" : instruction.strip();
        repositoryRef = normalize(repositoryRef);
        baseRevision = normalize(baseRevision);
        if (repositoryRef == null && baseRevision != null) {
            throw new IllegalArgumentException("baseRevision requires repositoryRef");
        }
        if (repositoryRef != null && (repositoryRef.length() > 120
                || !repositoryRef.matches("[A-Za-z0-9._-]{1,120}|[A-Za-z0-9._-]{1,60}/[A-Za-z0-9._-]{1,60}"))) {
            throw new IllegalArgumentException("repositoryRef must be a Workspace target or legacy repository id");
        }
        if (baseRevision != null && !baseRevision.matches("(?i)([0-9a-f]{40}|[0-9a-f]{64})")) {
            throw new IllegalArgumentException("baseRevision must be a full immutable Git commit id");
        }
        if (version < 0) throw new IllegalArgumentException("version must be >= 0");
        if (instructionRevision < 1) throw new IllegalArgumentException("instructionRevision must be >= 1");
    }

    public WorkTask(TaskId id, TaskType type, TaskState state, String subjectType, String subjectId,
                    Set<String> requiredCapabilities, String skillRef, String instruction,
                    String repositoryRef, String baseRevision, TaskPriority priority, long version,
                    Instant createdAt, Instant updatedAt) {
        this(id, type, state, subjectType, subjectId, requiredCapabilities, skillRef, instruction, 1,
                repositoryRef, baseRevision, priority, version, createdAt, updatedAt);
    }

    public boolean requiresWorkspace() {
        return repositoryRef != null;
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) return null;
        return value.strip();
    }
}
