package me.asu.aidev.approval.port;

import me.asu.aidev.approval.domain.ApprovalRequest;

/** Transport adapter boundary. Telegram/Teams/Web adapters implement this. */
public interface ApprovalNotificationPort {
    default boolean supports(ApprovalRequest request) {
        return true;
    }

    void approvalRequested(ApprovalRequest request);
}
