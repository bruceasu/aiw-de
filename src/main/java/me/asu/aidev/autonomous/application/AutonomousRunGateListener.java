package me.asu.aidev.autonomous.application;

import me.asu.aidev.autonomous.port.AutonomousRunRepository;
import me.asu.aidev.development.domain.EngineeringGate;
import me.asu.aidev.development.domain.EngineeringGateStatus;
import me.asu.aidev.development.port.EngineeringGateFinalizedListener;
import java.time.Clock;
import org.springframework.stereotype.Component;

/** Records the exact PASSED engineering result as run authority before/alongside human approval. */
@Component
public class AutonomousRunGateListener implements EngineeringGateFinalizedListener {
    private final AutonomousRunRepository runs;
    private final Clock clock = Clock.systemUTC();

    public AutonomousRunGateListener(AutonomousRunRepository runs) {
        this.runs = runs;
    }

    @Override
    public void onFinalized(EngineeringGate gate) {
        if (gate.status() != EngineeringGateStatus.PASSED
                || !AutonomousRequirementService.SUBJECT_TYPE.equals(gate.subjectType())) return;
        if (!runs.recordEngineeringPassed(gate.subjectId(), gate.sourceTaskId(), gate.resultRevision(), clock.instant())) {
            throw new IllegalStateException("cannot record PASSED engineering gate on autonomous run: " + gate.subjectId());
        }
    }
}
