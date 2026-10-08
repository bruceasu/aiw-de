package me.asu.aidev.autonomous.application;

import me.asu.aidev.autonomous.port.AutonomousRunRepository;
import me.asu.aidev.execution.port.TaskClaimPolicy;
import me.asu.aidev.execution.port.TaskExecutionPolicy;
import me.asu.aidev.work.domain.WorkTask;
import org.springframework.stereotype.Component;

/** Enforces run-level operator/failure authority at the worker lease boundary. */
@Component
public class AutonomousRunExecutionPolicy implements TaskClaimPolicy, TaskExecutionPolicy {
    private final AutonomousRunRepository runs;

    public AutonomousRunExecutionPolicy(AutonomousRunRepository runs) { this.runs = runs; }

    @Override
    public boolean canClaim(WorkTask task) {
        if (!AutonomousRequirementService.SUBJECT_TYPE.equals(task.subjectType())) return true;
        return runs.find(task.subjectId())
                .orElseThrow(() -> new IllegalStateException("autonomous run not found: " + task.subjectId()))
                .executionAllowed();
    }

    @Override
    public void assertExecutionAllowed(WorkTask task) {
        if (!AutonomousRequirementService.SUBJECT_TYPE.equals(task.subjectType())) return;
        if (!runs.lock(task.subjectId())) throw new IllegalStateException("autonomous run not found: " + task.subjectId());
        var run = runs.find(task.subjectId()).orElseThrow();
        if (!run.executionAllowed()) {
            throw new IllegalStateException("autonomous run does not allow worker execution: status="
                    + run.status() + ", controlState=" + run.controlState());
        }
    }
}
