package me.asu.aidev.approval.application;

import me.asu.aidev.approval.domain.ApprovalChannel;
import me.asu.aidev.work.domain.TaskId;

public record RequestApprovalCommand(
        TaskId sourceTaskId,
        String requestedBy,
        ApprovalChannel channel,
        String channelRef) {
}
