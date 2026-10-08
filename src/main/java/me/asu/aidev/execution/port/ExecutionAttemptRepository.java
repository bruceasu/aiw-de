package me.asu.aidev.execution.port;

import me.asu.aidev.execution.domain.CompletionReport;
import me.asu.aidev.execution.domain.AiAttemptHistory;
import me.asu.aidev.execution.domain.AiSelection;
import me.asu.aidev.execution.domain.ExecutionAttempt;
import me.asu.aidev.execution.domain.ExecutionAttemptId;
import me.asu.aidev.execution.domain.ExecutionAttemptState;
import me.asu.aidev.execution.domain.LeaseToken;
import me.asu.aidev.execution.domain.TaskInput;
import me.asu.aidev.execution.domain.WorkspaceAssignment;
import me.asu.aidev.execution.domain.WorkspaceResult;
import me.asu.aidev.work.domain.TaskId;
import me.asu.aidev.worker.domain.WorkerId;
import java.time.Instant;
import java.util.Optional;

public interface ExecutionAttemptRepository {
    void insert(ExecutionAttempt attempt);
    boolean transition(LeaseToken leaseToken, ExecutionAttemptState expected, ExecutionAttemptState target,
                       Instant startedAt, Instant finishedAt);
    boolean attachExternalSession(LeaseToken leaseToken, WorkerId workerId, String externalSessionId);
    boolean attachWorkspaceResult(LeaseToken leaseToken, WorkerId workerId,
                                  WorkspaceAssignment assignment, WorkspaceResult result);
    boolean attachCompletionReport(LeaseToken leaseToken, WorkerId workerId, CompletionReport report);
    boolean attachAiSelection(LeaseToken leaseToken, WorkerId workerId, AiSelection selection);
    boolean attachFailureCategory(LeaseToken leaseToken, WorkerId workerId, String category);
    boolean requestStop(LeaseToken leaseToken, WorkerId workerId, Instant requestedAt);
    boolean stopRequested(LeaseToken leaseToken, WorkerId workerId);
    boolean acknowledgeStop(LeaseToken leaseToken, WorkerId workerId, Instant acknowledgedAt);
    boolean stopAcknowledged(TaskId taskId, WorkerId workerId, LeaseToken leaseToken);
    Optional<ExecutionAttemptId> findAttemptId(LeaseToken leaseToken);
    Optional<AiAttemptHistory> findPreviousAiAttempt(TaskId taskId, int beforeAttemptNo);
    boolean terminateActiveLease(LeaseToken leaseToken, Instant finishedAt, String terminationReason);
    Optional<CompletionReport> findCompletionReport(TaskId taskId);
    Optional<TaskInput> findCompletedInput(TaskId taskId);
}
