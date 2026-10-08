package me.asu.aidev.fleet.application;

import me.asu.aidev.execution.port.TaskConcurrencyBudget;
import me.asu.aidev.fleet.port.FleetAdmissionBudgetRepository;
import me.asu.aidev.work.domain.WorkTask;
import java.time.Instant;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Global hard ceiling for worker-pull claims.
 *
 * The repository takes a database mutex and counts live CLAIMED/RUNNING tasks while that
 * mutex is held. No separate global active-slot ledger exists, so release is intentionally
 * a no-op and completion/failure cannot drift a second counter.
 */
@Component
@Order(200)
public class FleetAdmissionConcurrencyBudget implements TaskConcurrencyBudget {
    private final FleetAdmissionBudgetRepository repository;

    public FleetAdmissionConcurrencyBudget(FleetAdmissionBudgetRepository repository) {
        this.repository = repository;
    }

    @Override
    public boolean tryReserve(WorkTask task, Instant at) {
        return repository.tryAdmit();
    }

    @Override
    public void release(WorkTask task, Instant at) {
        // No persisted global active-slot counter. The live task states are the authority.
    }
}
