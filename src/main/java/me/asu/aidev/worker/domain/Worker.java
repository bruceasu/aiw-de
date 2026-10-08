package me.asu.aidev.worker.domain;

import me.asu.aidev.work.domain.CapabilitySetCodec;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;

public record Worker(
        WorkerId id,
        String runtimeType,
        Set<String> capabilities,
        int maxConcurrency,
        int activeLeases,
        WorkerStatus status,
        Instant registeredAt,
        Instant lastHeartbeatAt,
        Instant updatedAt) {

    public Worker {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(registeredAt, "registeredAt");
        Objects.requireNonNull(lastHeartbeatAt, "lastHeartbeatAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (runtimeType == null || runtimeType.isBlank()) throw new IllegalArgumentException("runtimeType is blank");
        capabilities = CapabilitySetCodec.decode(CapabilitySetCodec.encode(capabilities));
        if (maxConcurrency < 1) throw new IllegalArgumentException("maxConcurrency must be >= 1");
        if (activeLeases < 0 || activeLeases > maxConcurrency) {
            throw new IllegalArgumentException("activeLeases is outside worker capacity");
        }
    }

    public boolean supports(Set<String> requiredCapabilities) {
        return capabilities.contains("*") || capabilities.containsAll(requiredCapabilities);
    }
}
