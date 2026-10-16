package me.asu.aidev.worker.domain;

import java.time.Duration;
import java.time.Instant;

/** Heartbeat freshness shared by scheduling and Fleet snapshots. */
public final class WorkerLiveness {
    public static final Duration HEARTBEAT_WINDOW = Duration.ofSeconds(90);

    private WorkerLiveness() {}

    public static WorkerStatus observedStatus(WorkerStatus status, Instant lastHeartbeatAt, Instant now) {
        return lastHeartbeatAt.isBefore(now.minus(HEARTBEAT_WINDOW)) ? WorkerStatus.OFFLINE : status;
    }
}
