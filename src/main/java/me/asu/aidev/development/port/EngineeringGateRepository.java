package me.asu.aidev.development.port;

import me.asu.aidev.development.domain.EngineeringGate;
import me.asu.aidev.development.domain.EngineeringGateStatus;
import me.asu.aidev.work.domain.TaskId;
import java.time.Instant;
import java.util.Optional;

public interface EngineeringGateRepository {
    /** Idempotently closes the gate for the latest completed execution attempt of the source task. */
    boolean finalizeGate(TaskId sourceTaskId,
                         TaskId reviewTaskId,
                         TaskId verificationTaskId,
                         EngineeringGateStatus status,
                         Instant closedAt);

    Optional<EngineeringGate> findBySourceTask(TaskId sourceTaskId);

    Optional<EngineeringGate> findLatestPassed(String subjectType, String subjectId);
}
