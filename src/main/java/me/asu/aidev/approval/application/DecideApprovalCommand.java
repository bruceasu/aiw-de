package me.asu.aidev.approval.application;

import me.asu.aidev.approval.domain.ApprovalDecision;
import me.asu.aidev.approval.domain.ApprovalRequestId;

public record DecideApprovalCommand(
        ApprovalRequestId approvalRequestId,
        ApprovalDecision decision,
        String decidedBy,
        String note) {
}
