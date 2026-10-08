package me.asu.aidev.fleet.domain;

import java.time.Instant;
import java.util.Objects;

/** Persisted global worker-pull admission authority plus a live lease count snapshot. */
public record FleetAdmissionBudget(
        int maxParallelism,
        int activeLeases,
        Instant updatedAt) {

    public FleetAdmissionBudget {
        if (maxParallelism < 0 || maxParallelism > 65535) {
            throw new IllegalArgumentException("maxParallelism must be between 0 and 65535");
        }
        if (activeLeases < 0) throw new IllegalArgumentException("activeLeases < 0");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }

    public boolean unlimited() { return maxParallelism == 0; }

    public boolean saturated() { return !unlimited() && activeLeases >= maxParallelism; }

    public Integer availableSlots() {
        return unlimited() ? null : Math.max(0, maxParallelism - activeLeases);
    }

    public int overcommittedSlots() {
        return unlimited() ? 0 : Math.max(0, activeLeases - maxParallelism);
    }
}
