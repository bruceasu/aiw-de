package me.asu.aidev.execution.scheduling;

import me.asu.aidev.execution.application.TaskExecutionService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Periodically requeues tasks whose external/in-process worker lease has expired. */
@Component
@ConditionalOnProperty(name = "aidev.lease-recovery.enabled", havingValue = "true", matchIfMissing = true)
public class LeaseRecoveryScheduler {
    private final TaskExecutionService executions;
    private final int batchSize;

    public LeaseRecoveryScheduler(
            TaskExecutionService executions,
            @org.springframework.beans.factory.annotation.Value("${aidev.lease-recovery.batch-size:100}") int batchSize) {
        this.executions = executions;
        this.batchSize = Math.max(1, batchSize);
    }

    @Scheduled(fixedDelayString = "${aidev.lease-recovery.interval-ms:5000}")
    public void recover() {
        executions.recoverExpiredLeases(batchSize);
    }
}
