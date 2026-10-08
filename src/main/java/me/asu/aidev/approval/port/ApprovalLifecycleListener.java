package me.asu.aidev.approval.port;

import me.asu.aidev.approval.domain.ApprovalRequest;

/** Transactional authority listener; unlike notification ports this is not an external transport. */
public interface ApprovalLifecycleListener {
    default void onRequested(ApprovalRequest request) {}
    default void onDecided(ApprovalRequest request) {}
}
