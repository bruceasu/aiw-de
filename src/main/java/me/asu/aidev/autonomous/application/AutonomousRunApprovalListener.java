package me.asu.aidev.autonomous.application;

import me.asu.aidev.approval.domain.ApprovalStatus;
import me.asu.aidev.approval.domain.ApprovalRequest;
import me.asu.aidev.approval.port.ApprovalLifecycleListener;
import me.asu.aidev.autonomous.port.AutonomousRunRepository;
import java.time.Clock;
import org.springframework.stereotype.Component;

/** Keeps run authority synchronized with the durable approval authority, transactionally. */
@Component
public class AutonomousRunApprovalListener implements ApprovalLifecycleListener {
    private final AutonomousRunRepository runs;
    private final Clock clock = Clock.systemUTC();

    public AutonomousRunApprovalListener(AutonomousRunRepository runs) {
        this.runs = runs;
    }

    @Override
    public void onRequested(ApprovalRequest request) {
        if (!AutonomousRequirementService.SUBJECT_TYPE.equals(request.subjectType())) return;
        if (!runs.markApprovalPending(request.subjectId(), request.id(), request.sourceTaskId(),
                request.resultRevision(), clock.instant())) {
            throw new IllegalStateException("cannot mark autonomous run waiting for approval: " + request.subjectId());
        }
    }

    @Override
    public void onDecided(ApprovalRequest request) {
        if (!AutonomousRequirementService.SUBJECT_TYPE.equals(request.subjectType())) return;
        boolean changed = switch (request.status()) {
            case APPROVED -> runs.markSucceeded(request.subjectId(), request.id(), clock.instant());
            case REJECTED -> runs.markRejected(request.subjectId(), request.id(), clock.instant());
            case PENDING -> true;
        };
        if (!changed) {
            var current = runs.find(request.subjectId()).orElseThrow();
            if ((request.status() == ApprovalStatus.APPROVED
                    && current.status() == me.asu.aidev.autonomous.domain.AutonomousRunStatus.SUCCEEDED)
                    || (request.status() == ApprovalStatus.REJECTED
                    && current.status() == me.asu.aidev.autonomous.domain.AutonomousRunStatus.REJECTED)) {
                return;
            }
            throw new IllegalStateException("cannot apply approval decision to autonomous run: " + request.subjectId());
        }
    }
}
