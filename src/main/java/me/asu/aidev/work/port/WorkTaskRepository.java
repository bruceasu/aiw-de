package me.asu.aidev.work.port;

import me.asu.aidev.execution.domain.ExpiredLease;
import me.asu.aidev.execution.domain.LeaseToken;
import me.asu.aidev.execution.domain.TaskLease;
import me.asu.aidev.work.domain.ReadyTaskCandidate;
import me.asu.aidev.work.domain.TaskId;
import me.asu.aidev.work.domain.TaskInstructionRevision;
import me.asu.aidev.work.domain.TaskState;
import me.asu.aidev.work.domain.TaskType;
import me.asu.aidev.work.domain.WorkTask;
import me.asu.aidev.worker.domain.WorkerId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface WorkTaskRepository {
    void insert(WorkTask task);
    boolean insertIfAbsent(WorkTask task);
    Optional<WorkTask> find(TaskId id);
    List<TaskInstructionRevision> instructionRevisions(TaskId id);
    boolean reviseInstruction(TaskId id, TaskState expectedState, long expectedTaskVersion,
                              int expectedInstructionRevision, String instruction,
                              String actor, String reason, Instant updatedAt);
    boolean requeueRevoked(TaskId id, TaskState expectedState, WorkerId workerId,
                           LeaseToken leaseToken, Instant updatedAt);
    void recordControlEvent(String eventId, TaskId id, String attemptId, String action,
                            String actor, String reason, boolean warningConfirmed, Instant createdAt);
    void linkCorrection(TaskId completedTaskId, TaskId correctionTaskId,
                        String actor, String reason, Instant createdAt);
    List<WorkTask> findBySubjectAndType(String subjectType, String subjectId, TaskType type);
    List<WorkTask> findBySubjectAndState(String subjectType, String subjectId, TaskState state);
    List<WorkTask> findBySubject(String subjectType, String subjectId);
    boolean lock(TaskId id);
    boolean transitionState(TaskId id, TaskState expected, TaskState target, Instant updatedAt);

    List<WorkTask> findReadyCandidates();
    List<ReadyTaskCandidate> findReadySchedulingCandidates();
    Optional<TaskLease> claim(TaskId taskId, WorkerId workerId, LeaseToken leaseToken,
                              Instant acquiredAt, Instant expiresAt, Instant heartbeatAt);
    Optional<TaskLease> findLease(TaskId taskId);
    boolean start(TaskId taskId, WorkerId workerId, LeaseToken leaseToken, Instant updatedAt);
    boolean heartbeatLease(TaskId taskId, WorkerId workerId, LeaseToken leaseToken,
                           Instant heartbeatAt, Instant expiresAt);
    boolean completeOwned(TaskId taskId, WorkerId workerId, LeaseToken leaseToken, Instant updatedAt);
    boolean failOwned(TaskId taskId, WorkerId workerId, LeaseToken leaseToken, Instant updatedAt);
    List<ExpiredLease> findExpiredLeases(Instant now, int limit);
    boolean requeueExpired(TaskId taskId, WorkerId workerId, LeaseToken leaseToken, Instant updatedAt);
    boolean requeueOwnedForPause(TaskId taskId, WorkerId workerId, LeaseToken leaseToken, Instant updatedAt);
    boolean cancelOwned(TaskId taskId, WorkerId workerId, LeaseToken leaseToken, Instant updatedAt);
    boolean cancelInactive(TaskId taskId, TaskState expected, Instant updatedAt);
    boolean retryFailed(TaskId taskId, Instant updatedAt);
    int attemptNo(TaskId taskId);
}
