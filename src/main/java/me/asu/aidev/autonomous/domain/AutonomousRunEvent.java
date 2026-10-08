package me.asu.aidev.autonomous.domain;

import me.asu.aidev.work.domain.TaskId;
import java.time.Instant;
import java.util.Objects;

/** Immutable append-only run-level event retained for audit/diagnostics. */
public record AutonomousRunEvent(
        String runId,
        long sequenceNo,
        AutonomousRunEventType type,
        String actor,
        TaskId relatedTaskId,
        String detail,
        Instant occurredAt) {

    public AutonomousRunEvent {
        if (runId == null || runId.isBlank()) throw new IllegalArgumentException("runId is blank");
        if (sequenceNo < 1) throw new IllegalArgumentException("sequenceNo must be >= 1");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(occurredAt, "occurredAt");
        runId = runId.strip();
        actor = normalize(actor);
        detail = normalize(detail);
        if (actor != null && actor.length() > 256) throw new IllegalArgumentException("actor exceeds 256 characters");
        if (detail != null && detail.length() > 4096) throw new IllegalArgumentException("detail exceeds 4096 characters");
    }

    private static String normalize(String value) {
        if (value == null) return null;
        String v = value.strip();
        return v.isEmpty() ? null : v;
    }
}
