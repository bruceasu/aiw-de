package me.asu.aidev.approval.domain;

import me.asu.aidev.work.domain.TaskId;
import java.time.Instant;
import java.util.Objects;

/** Human authority over one exact PASSED engineering gate. */
public record ApprovalRequest(
        ApprovalRequestId id,
        TaskId sourceTaskId,
        String subjectType,
        String subjectId,
        String resultRevision,
        Instant engineeringGateClosedAt,
        ApprovalStatus status,
        String requestedBy,
        ApprovalChannel channel,
        String channelRef,
        Instant requestedAt,
        String decidedBy,
        String decisionNote,
        Instant decidedAt) {

    public ApprovalRequest {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(sourceTaskId, "sourceTaskId");
        Objects.requireNonNull(subjectType, "subjectType");
        Objects.requireNonNull(subjectId, "subjectId");
        Objects.requireNonNull(engineeringGateClosedAt, "engineeringGateClosedAt");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(requestedBy, "requestedBy");
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(requestedAt, "requestedAt");
        if (subjectType.isBlank()) throw new IllegalArgumentException("subjectType is blank");
        if (subjectId.isBlank()) throw new IllegalArgumentException("subjectId is blank");
        resultRevision = normalize(resultRevision);
        if (requestedBy.isBlank()) throw new IllegalArgumentException("requestedBy is blank");
        channelRef = normalize(channelRef);
        decidedBy = normalize(decidedBy);
        decisionNote = normalize(decisionNote);
        if (status == ApprovalStatus.PENDING) {
            if (decidedBy != null || decidedAt != null) {
                throw new IllegalArgumentException("pending approval cannot have a decision");
            }
        } else {
            if (decidedBy == null || decidedAt == null) {
                throw new IllegalArgumentException("terminal approval requires decision actor and time");
            }
        }
    }

    private static String normalize(String value) {
        if (value == null) return null;
        String normalized = value.strip();
        return normalized.isEmpty() ? null : normalized;
    }
}
