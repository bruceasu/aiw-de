package me.asu.aidev.fleet.application;

import me.asu.aidev.fleet.domain.FleetAdmissionBudget;
import me.asu.aidev.fleet.domain.FleetAdmissionBudgetEvent;
import me.asu.aidev.fleet.domain.FleetAdmissionBudgetEventType;
import me.asu.aidev.fleet.port.FleetAdmissionBudgetEventRepository;
import me.asu.aidev.fleet.port.FleetAdmissionBudgetRepository;
import java.time.Clock;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FleetAdmissionBudgetService {
    private me.asu.aidev.authority.AuthorityBoundary delegation;
    @Autowired
    public void setAuthorityBoundary(me.asu.aidev.authority.AuthorityBoundary boundary) { this.delegation=boundary; }
    private final FleetAdmissionBudgetRepository repository;
    private final FleetAdmissionBudgetEventRepository events;
    private final int configuredMaxParallelism;
    private final Clock clock;

    @Autowired
    public FleetAdmissionBudgetService(
            FleetAdmissionBudgetRepository repository,
            FleetAdmissionBudgetEventRepository events,
            @Value("${aidev.fleet.max-parallelism:0}") int configuredMaxParallelism) {
        this(repository, events, configuredMaxParallelism, Clock.systemUTC());
    }

    FleetAdmissionBudgetService(
            FleetAdmissionBudgetRepository repository,
            FleetAdmissionBudgetEventRepository events,
            int configuredMaxParallelism,
            Clock clock) {
        validate(configuredMaxParallelism);
        this.repository = repository;
        this.events = events;
        this.configuredMaxParallelism = configuredMaxParallelism;
        this.clock = clock;
    }

    /**
     * AIW_FLEET_MAX_PARALLELISM is a bootstrap default. Runtime scheduling authority, once persisted,
     * survives Control Plane restarts and is never silently overwritten by startup.
     */
    @Transactional
    public FleetAdmissionBudget configureFromDeployment() {
        repository.configureIfUninitialized(configuredMaxParallelism, clock.instant());
        return repository.snapshot();
    }

    @Transactional
    public FleetAdmissionBudget updateMaxParallelism(int maxParallelism, String controlledBy, String reason) {
        if(delegation!=null) delegation.changing("GLOBAL:FLEET");
        validate(maxParallelism);
        String actor = normalizeActor(controlledBy);
        String normalizedReason = normalizeReason(reason);

        FleetAdmissionBudget before = repository.lockAndSnapshot();
        if (before.maxParallelism() == maxParallelism) return before;

        var now = clock.instant();
        if (!repository.updateMaxParallelism(maxParallelism, now)) {
            throw new IllegalStateException("GLOBAL fleet admission budget row is missing");
        }
        events.append(
                FleetAdmissionBudgetEventType.MAX_PARALLELISM_CHANGED,
                actor,
                before.maxParallelism(),
                maxParallelism,
                before.activeLeases(),
                normalizedReason,
                now);
        return repository.snapshot();
    }

    @Transactional(readOnly = true)
    public FleetAdmissionBudget snapshot() {
        return repository.snapshot();
    }

    @Transactional(readOnly = true)
    public List<FleetAdmissionBudgetEvent> recentEvents(int limit) {
        return events.findRecent(limit);
    }

    private static void validate(int maxParallelism) {
        if (maxParallelism < 0 || maxParallelism > 65535) {
            throw new IllegalArgumentException("maxParallelism must be between 0 and 65535; 0 means unlimited");
        }
    }

    private static String normalizeActor(String actor) {
        if (actor == null || actor.isBlank()) throw new IllegalArgumentException("controlledBy is blank");
        String normalized = actor.strip();
        if (normalized.length() > 256) throw new IllegalArgumentException("controlledBy exceeds 256 characters");
        return normalized;
    }

    private static String normalizeReason(String reason) {
        if (reason == null) return null;
        String normalized = reason.strip();
        if (normalized.isEmpty()) return null;
        if (normalized.length() > 4096) throw new IllegalArgumentException("reason exceeds 4096 characters");
        return normalized;
    }
}
