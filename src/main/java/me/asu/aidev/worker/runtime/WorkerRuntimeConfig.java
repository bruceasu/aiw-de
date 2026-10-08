package me.asu.aidev.worker.runtime;

import java.time.Duration;
import java.util.Objects;

public record WorkerRuntimeConfig(
        Duration idleBackoff,
        Duration heartbeatInterval,
        Duration shutdownWait) {

    public WorkerRuntimeConfig {
        idleBackoff = requirePositive(idleBackoff, "idleBackoff");
        heartbeatInterval = requirePositive(heartbeatInterval, "heartbeatInterval");
        shutdownWait = requirePositive(shutdownWait, "shutdownWait");
    }

    public static WorkerRuntimeConfig defaults() {
        return new WorkerRuntimeConfig(Duration.ofMillis(250), Duration.ofSeconds(20), Duration.ofSeconds(10));
    }

    private static Duration requirePositive(Duration value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be > 0");
        }
        return value;
    }
}
