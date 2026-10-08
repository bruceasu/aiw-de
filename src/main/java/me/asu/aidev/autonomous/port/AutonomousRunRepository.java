package me.asu.aidev.autonomous.port;

import me.asu.aidev.approval.domain.ApprovalRequestId;
import me.asu.aidev.autonomous.domain.AutonomousRun;
import me.asu.aidev.work.domain.TaskId;
import java.time.Instant;
import java.util.Optional;

public interface AutonomousRunRepository {
    boolean insertIfAbsent(AutonomousRun run);
    Optional<AutonomousRun> find(String runId);

    /** Serializes execution/operator-control decisions for one run. Claim admission uses reserveExecutionSlot(). */
    boolean lock(String runId);
    boolean reserveExecutionSlot(String runId);
    boolean releaseExecutionSlot(String runId);

    /** Runtime scheduling authority. A lower limit never revokes existing leases. */
    boolean updateMaxParallelism(String runId, int maxParallelism, Instant updatedAt);

    /** Records the exact PASSED engineering result without downgrading WAITING_APPROVAL. */
    boolean recordEngineeringPassed(String runId, TaskId sourceTaskId, String resultRevision, Instant updatedAt);

    /** Records the exact approval request and transitions the run to WAITING_APPROVAL. */
    boolean markApprovalPending(String runId, ApprovalRequestId approvalRequestId,
                                TaskId sourceTaskId, String resultRevision, Instant updatedAt);

    boolean markSucceeded(String runId, ApprovalRequestId approvalRequestId, Instant completedAt);
    boolean markRejected(String runId, ApprovalRequestId approvalRequestId, Instant completedAt);

    boolean markFailed(String runId, TaskId failedTaskId, String reason, Instant failedAt);
    boolean markRecovered(String runId, String recoveredBy, Instant recoveredAt);

    boolean markPaused(String runId, String actor, String reason, Instant controlledAt);
    boolean markResumed(String runId, String actor, String reason, Instant controlledAt);
    boolean markCancelled(String runId, String actor, String reason, Instant controlledAt);
}
