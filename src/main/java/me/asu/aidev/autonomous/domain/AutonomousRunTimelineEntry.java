package me.asu.aidev.autonomous.domain;

import java.time.Instant;
import java.util.Objects;

/** Read-side projection entry. Null optional fields mean that source does not own that datum. */
public record AutonomousRunTimelineEntry(
        Instant occurredAt,
        AutonomousRunTimelineCategory category,
        AutonomousRunTimelineEventType eventType,
        Long runEventSequence,
        String taskId,
        String taskType,
        Integer attemptNo,
        String workerId,
        String approvalRequestId,
        String state,
        String actor,
        String detail,
        String resultRevision,
        String publicationRef) {

    public AutonomousRunTimelineEntry {
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(eventType, "eventType");
        if (runEventSequence != null && runEventSequence < 1) {
            throw new IllegalArgumentException("runEventSequence must be >= 1");
        }
    }

    public AutonomousRunTimelineEntry(
            Instant occurredAt,
            AutonomousRunTimelineCategory category,
            AutonomousRunTimelineEventType eventType,
            String taskId,
            String taskType,
            Integer attemptNo,
            String workerId,
            String approvalRequestId,
            String state,
            String actor,
            String detail,
            String resultRevision,
            String publicationRef) {
        this(occurredAt, category, eventType, null, taskId, taskType, attemptNo, workerId,
                approvalRequestId, state, actor, detail, resultRevision, publicationRef);
    }
}
