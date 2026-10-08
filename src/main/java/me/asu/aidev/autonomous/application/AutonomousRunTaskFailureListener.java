package me.asu.aidev.autonomous.application;

import me.asu.aidev.autonomous.domain.AutonomousRunEventType;
import me.asu.aidev.autonomous.port.AutonomousRunEventRepository;
import me.asu.aidev.autonomous.port.AutonomousRunRepository;
import me.asu.aidev.execution.port.TaskFailureListener;
import me.asu.aidev.work.domain.WorkTask;
import java.time.Clock;
import org.springframework.stereotype.Component;

/** Converts an explicit worker failure into durable run-level failure authority and audit history. */
@Component
public class AutonomousRunTaskFailureListener implements TaskFailureListener {
    private final AutonomousRunRepository runs;
    private final AutonomousRunEventRepository events;
    private final Clock clock = Clock.systemUTC();

    public AutonomousRunTaskFailureListener(AutonomousRunRepository runs, AutonomousRunEventRepository events) {
        this.runs = runs;
        this.events = events;
    }

    @Override
    public void onFailed(WorkTask failedTask, String failureReason) {
        if (!AutonomousRequirementService.SUBJECT_TYPE.equals(failedTask.subjectType())) return;
        var now = clock.instant();
        if (!runs.markFailed(failedTask.subjectId(), failedTask.id(), failureReason, now)) {
            var current = runs.find(failedTask.subjectId())
                    .orElseThrow(() -> new IllegalStateException("autonomous run not found for failed task: "
                            + failedTask.subjectId()));
            if (current.status() == me.asu.aidev.autonomous.domain.AutonomousRunStatus.FAILED
                    && failedTask.id().equals(current.lastFailedTaskId())) {
                return; // idempotent observation of the same failure
            }
            throw new IllegalStateException("cannot mark autonomous run failed from status " + current.status());
        }
        events.append(failedTask.subjectId(), AutonomousRunEventType.FAILED,
                null, failedTask.id(), failureReason, now);
    }
}
