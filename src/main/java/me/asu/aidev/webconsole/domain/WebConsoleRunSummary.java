package me.asu.aidev.webconsole.domain;

import java.time.Instant;

/** Read-only summary used by the Web Console run list. */
public record WebConsoleRunSummary(
        String runId,
        String requirement,
        String lifecycleStatus,
        String controlState,
        String effectiveStatus,
        String phase,
        int totalTasks,
        int completedTasks,
        int activeTasks,
        int failedTasks,
        int maxParallelism,
        int activeSlots,
        Instant startedAt,
        Instant statusUpdatedAt) {

    public int progressPercent() {
        if (totalTasks <= 0) return 0;
        return Math.min(100, Math.max(0, (completedTasks * 100) / totalTasks));
    }
}
