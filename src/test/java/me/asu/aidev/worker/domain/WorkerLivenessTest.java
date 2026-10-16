package me.asu.aidev.worker.domain;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class WorkerLivenessTest {
    @Test
    void heartbeatWindowIncludesItsBoundaryAndPreservesManagementState() {
        Instant now = Instant.parse("2026-10-16T00:00:00Z");
        for (WorkerStatus status : WorkerStatus.values()) {
            assertThat(WorkerLiveness.observedStatus(status, now.minusSeconds(90), now)).isEqualTo(status);
            assertThat(WorkerLiveness.observedStatus(status, now.minusSeconds(90).minusMillis(1), now))
                    .isEqualTo(WorkerStatus.OFFLINE);
            assertThat(WorkerLiveness.observedStatus(status, now, now)).isEqualTo(status);
        }
    }
}
