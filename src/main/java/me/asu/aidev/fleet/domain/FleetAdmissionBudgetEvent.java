package me.asu.aidev.fleet.domain;

import java.time.Instant;
import java.util.Objects;

/** Immutable fleet scheduling-control audit event. */
public record FleetAdmissionBudgetEvent(
        long sequenceNo,
        FleetAdmissionBudgetEventType type,
        String actor,
        int previousMaxParallelism,
        int maxParallelism,
        int activeLeases,
        String reason,
        Instant occurredAt) {

    public FleetAdmissionBudgetEvent {
        if (sequenceNo < 1) throw new IllegalArgumentException("sequenceNo must be >= 1");
        Objects.requireNonNull(type, "type");
        actor = normalize(actor);
        if (actor == null) throw new IllegalArgumentException("actor is blank");
        if (actor.length() > 256) throw new IllegalArgumentException("actor exceeds 256 characters");
        if (previousMaxParallelism < 0 || previousMaxParallelism > 65535) {
            throw new IllegalArgumentException("previousMaxParallelism must be between 0 and 65535");
        }
        if (maxParallelism < 0 || maxParallelism > 65535) {
            throw new IllegalArgumentException("maxParallelism must be between 0 and 65535");
        }
        if (activeLeases < 0) throw new IllegalArgumentException("activeLeases < 0");
        reason = normalize(reason);
        if (reason != null && reason.length() > 4096) throw new IllegalArgumentException("reason exceeds 4096 characters");
        Objects.requireNonNull(occurredAt, "occurredAt");
    }

    private static String normalize(String value) {
        if (value == null) return null;
        String normalized = value.strip();
        return normalized.isEmpty() ? null : normalized;
    }
}
