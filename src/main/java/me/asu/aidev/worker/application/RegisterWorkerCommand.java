package me.asu.aidev.worker.application;

import me.asu.aidev.worker.domain.WorkerId;
import java.util.Set;

public record RegisterWorkerCommand(
        WorkerId workerId,
        String runtimeType,
        Set<String> capabilities,
        int maxConcurrency) {

    public RegisterWorkerCommand {
        if (workerId == null) throw new IllegalArgumentException("workerId is null");
        capabilities = capabilities == null ? Set.of() : Set.copyOf(capabilities);
        if (runtimeType == null || runtimeType.isBlank()) throw new IllegalArgumentException("runtimeType is blank");
        if (maxConcurrency < 1) throw new IllegalArgumentException("maxConcurrency must be >= 1");
    }
}
