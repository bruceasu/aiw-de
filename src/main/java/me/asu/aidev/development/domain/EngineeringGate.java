package me.asu.aidev.development.domain;

import me.asu.aidev.execution.domain.CompletionReport;
import me.asu.aidev.work.domain.TaskId;
import me.asu.aidev.work.domain.TaskType;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Immutable engineering evidence for one completed IMPLEMENTATION, INTEGRATION, or REPAIR result.
 * The gate never mutates from REPAIR_REQUIRED to PASSED; a later repair produces a new gate.
 */
public record EngineeringGate(
        TaskId sourceTaskId,
        TaskType sourceTaskType,
        String subjectType,
        String subjectId,
        EngineeringGateStatus status,
        String repositoryRef,
        String baseRevision,
        String resultRevision,
        String publicationRef,
        List<String> changedFiles,
        TaskId reviewTaskId,
        CompletionReport reviewReport,
        TaskId verificationTaskId,
        CompletionReport verificationReport,
        Instant closedAt) {

    public EngineeringGate {
        Objects.requireNonNull(sourceTaskId, "sourceTaskId");
        Objects.requireNonNull(sourceTaskType, "sourceTaskType");
        Objects.requireNonNull(subjectType, "subjectType");
        Objects.requireNonNull(subjectId, "subjectId");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(reviewTaskId, "reviewTaskId");
        Objects.requireNonNull(reviewReport, "reviewReport");
        Objects.requireNonNull(verificationTaskId, "verificationTaskId");
        Objects.requireNonNull(verificationReport, "verificationReport");
        Objects.requireNonNull(closedAt, "closedAt");
        if (sourceTaskType != TaskType.IMPLEMENTATION
                && sourceTaskType != TaskType.INTEGRATION
                && sourceTaskType != TaskType.REPAIR) {
            throw new IllegalArgumentException("engineering gate source must be IMPLEMENTATION, INTEGRATION, or REPAIR");
        }
        changedFiles = changedFiles == null ? List.of() : List.copyOf(changedFiles);
    }
}
