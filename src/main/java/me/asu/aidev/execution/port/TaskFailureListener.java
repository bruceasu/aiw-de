package me.asu.aidev.execution.port;

import me.asu.aidev.work.domain.WorkTask;

/** Synchronous transactional observer for an explicit worker-reported task failure. */
public interface TaskFailureListener {
    void onFailed(WorkTask failedTask, String failureReason);
}
