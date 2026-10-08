package me.asu.aidev.approval.domain;

public enum ApprovalDecision {
    APPROVE,
    REJECT;

    public ApprovalStatus terminalStatus() {
        return this == APPROVE ? ApprovalStatus.APPROVED : ApprovalStatus.REJECTED;
    }
}
