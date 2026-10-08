package me.asu.aidev.fleet.port;

import me.asu.aidev.fleet.domain.FleetAdmissionBudgetEvent;
import me.asu.aidev.fleet.domain.FleetAdmissionBudgetEventType;
import java.time.Instant;
import java.util.List;

public interface FleetAdmissionBudgetEventRepository {
    FleetAdmissionBudgetEvent append(
            FleetAdmissionBudgetEventType type,
            String actor,
            int previousMaxParallelism,
            int maxParallelism,
            int activeLeases,
            String reason,
            Instant occurredAt);

    /** Newest first. */
    List<FleetAdmissionBudgetEvent> findRecent(int limit);
}
