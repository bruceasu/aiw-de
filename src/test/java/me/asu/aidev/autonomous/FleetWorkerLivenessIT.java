package me.asu.aidev.autonomous;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Set;
import me.asu.aidev.fleet.application.FleetDashboardService;
import me.asu.aidev.worker.application.RegisterWorkerCommand;
import me.asu.aidev.worker.application.WorkerService;
import me.asu.aidev.worker.domain.WorkerId;
import me.asu.aidev.worker.domain.WorkerStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("sqlite")
class FleetWorkerLivenessIT {
    private static final Path DB;
    static {
        try {
            DB = Files.createTempFile("fleet-worker-liveness-", ".db");
            Files.deleteIfExists(DB);
        } catch (Exception error) {
            throw new ExceptionInInitializerError(error);
        }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + DB.toAbsolutePath());
    }

    @Autowired WorkerService workers;
    @Autowired FleetDashboardService fleet;
    @Autowired JdbcTemplate jdbc;

    @Test
    void expiredWorkersLeaveAvailableCapacityAndHeartbeatsPreserveManagementState() {
        WorkerId active = new WorkerId("liveness-active");
        WorkerId draining = new WorkerId("liveness-draining");
        WorkerId offline = new WorkerId("liveness-offline");
        for (WorkerId id : Set.of(active, draining, offline)) {
            workers.register(new RegisterWorkerCommand(id, "test", Set.of("coding"), 2));
        }
        workers.drain(draining);
        workers.offline(offline);
        var fresh = fleet.dashboard(10).workers();
        assertThat(fresh.active()).isEqualTo(1);
        assertThat(fresh.draining()).isEqualTo(1);
        assertThat(fresh.offline()).isEqualTo(1);
        assertThat(fresh.availableClaimCapacity()).isEqualTo(2);

        jdbc.update("UPDATE worker SET last_heartbeat_at_epoch_ms=?",
                Instant.now().minusSeconds(91).toEpochMilli());
        var expired = fleet.dashboard(10).workers();
        assertThat(expired.total()).isEqualTo(3);
        assertThat(expired.active()).isZero();
        assertThat(expired.draining()).isZero();
        assertThat(expired.offline()).isEqualTo(3);
        assertThat(expired.claimableCapacity()).isZero();
        assertThat(expired.availableClaimCapacity()).isZero();
        assertThat(expired.workers()).allSatisfy(worker -> {
            assertThat(worker.status()).isEqualTo("OFFLINE");
            assertThat(worker.availableCapacity()).isZero();
        });
        assertThat(workers.get(active).status()).isEqualTo(WorkerStatus.ACTIVE);
        assertThat(workers.get(draining).status()).isEqualTo(WorkerStatus.DRAINING);

        for (WorkerId id : Set.of(active, draining, offline)) workers.heartbeat(id);
        var recovered = fleet.dashboard(10).workers();
        assertThat(recovered.active()).isEqualTo(1);
        assertThat(recovered.draining()).isEqualTo(1);
        assertThat(recovered.offline()).isEqualTo(1);
        assertThat(recovered.availableClaimCapacity()).isEqualTo(2);
    }
}
