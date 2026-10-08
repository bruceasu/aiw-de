package me.asu.aidev.autonomous.application;

import me.asu.aidev.autonomous.port.AutonomousRunRepository;
import me.asu.aidev.execution.port.TaskConcurrencyBudget;
import me.asu.aidev.work.domain.WorkTask;
import java.time.Instant;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Database-backed per-run semaphore for worker-pull admission control. */
@Component
@Order(100)
public class AutonomousRunConcurrencyBudget implements TaskConcurrencyBudget {
    private final AutonomousRunRepository runs;

    public AutonomousRunConcurrencyBudget(AutonomousRunRepository runs) {
        this.runs = runs;
    }

    @Override
    public boolean tryReserve(WorkTask task, Instant at) {
        if (!AutonomousRequirementService.SUBJECT_TYPE.equals(task.subjectType())) return true;
        return runs.reserveExecutionSlot(task.subjectId());
    }

    @Override
    public void release(WorkTask task, Instant at) {
        if (!AutonomousRequirementService.SUBJECT_TYPE.equals(task.subjectType())) return;
        if (!runs.releaseExecutionSlot(task.subjectId())) {
            throw new IllegalStateException("autonomous run concurrency accounting underflow: " + task.subjectId());
        }
    }
}
