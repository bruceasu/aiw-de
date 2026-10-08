package me.asu.aidev.fleet.port;

import me.asu.aidev.fleet.domain.FleetAdmissionBudget;
import java.time.Instant;

public interface FleetAdmissionBudgetRepository {
    /**
     * Bootstrap-only deployment configuration. The row is changed only while it still has the V13
     * uninitialized marker (updated_at_epoch_ms=0), so later runtime authority survives restarts.
     */
    boolean configureIfUninitialized(int maxParallelism, Instant updatedAt);

    /** Lock GLOBAL and return a live budget snapshot inside the current transaction. */
    FleetAdmissionBudget lockAndSnapshot();

    /** Update GLOBAL after the caller has acquired the budget lock. */
    boolean updateMaxParallelism(int maxParallelism, Instant updatedAt);

    /**
     * Serializes all claim admission decisions and evaluates the live CLAIMED/RUNNING count.
     * Must be called inside the worker claim transaction.
     */
    boolean tryAdmit();

    /** Read-only dashboard snapshot. */
    FleetAdmissionBudget snapshot();
}
