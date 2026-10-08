package me.asu.aidev.approval.port;

import me.asu.aidev.approval.domain.ApprovalRequest;
import me.asu.aidev.approval.domain.ApprovalRequestId;
import me.asu.aidev.approval.domain.ApprovalStatus;
import me.asu.aidev.work.domain.TaskId;
import java.time.Instant;
import java.util.Optional;

public interface ApprovalRequestRepository {
    boolean insertIfAbsent(ApprovalRequest request);
    Optional<ApprovalRequest> find(ApprovalRequestId id);
    Optional<ApprovalRequest> findBySourceTask(TaskId sourceTaskId);
    boolean decide(ApprovalRequestId id, ApprovalStatus expectedStatus, ApprovalStatus newStatus,
                   String decidedBy, String note, Instant decidedAt);
}
