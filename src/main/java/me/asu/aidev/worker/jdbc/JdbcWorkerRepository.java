package me.asu.aidev.worker.jdbc;

import me.asu.aidev.work.domain.CapabilitySetCodec;
import me.asu.aidev.worker.domain.Worker;
import me.asu.aidev.worker.domain.WorkerId;
import me.asu.aidev.worker.domain.WorkerStatus;
import me.asu.aidev.worker.port.WorkerRepository;
import java.time.Instant;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcWorkerRepository implements WorkerRepository {
    private final JdbcTemplate jdbc;

    public JdbcWorkerRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void register(Worker worker) {
        int changed = jdbc.update("""
                UPDATE worker
                   SET runtime_type=?, capabilities=?, max_concurrency=?, status='ACTIVE',
                       last_heartbeat_at_epoch_ms=?, updated_at_epoch_ms=?
                 WHERE id=?
                """, worker.runtimeType(), CapabilitySetCodec.encode(worker.capabilities()), worker.maxConcurrency(),
                worker.lastHeartbeatAt().toEpochMilli(), worker.updatedAt().toEpochMilli(), worker.id().value());
        if (changed == 0) {
            jdbc.update("""
                    INSERT INTO worker(
                        id,runtime_type,capabilities,max_concurrency,active_leases,status,
                        registered_at_epoch_ms,last_heartbeat_at_epoch_ms,updated_at_epoch_ms
                    ) VALUES(?,?,?,?,?,?,?,?,?)
                    """, worker.id().value(), worker.runtimeType(), CapabilitySetCodec.encode(worker.capabilities()),
                    worker.maxConcurrency(), worker.activeLeases(), worker.status().name(),
                    worker.registeredAt().toEpochMilli(), worker.lastHeartbeatAt().toEpochMilli(),
                    worker.updatedAt().toEpochMilli());
        }
    }

    @Override
    public Optional<Worker> find(WorkerId workerId) {
        return jdbc.query("""
                SELECT id,runtime_type,capabilities,max_concurrency,active_leases,status,
                       registered_at_epoch_ms,last_heartbeat_at_epoch_ms,updated_at_epoch_ms
                  FROM worker WHERE id=?
                """, rs -> rs.next() ? Optional.of(new Worker(
                        new WorkerId(rs.getString("id")),
                        rs.getString("runtime_type"),
                        CapabilitySetCodec.decode(rs.getString("capabilities")),
                        rs.getInt("max_concurrency"),
                        rs.getInt("active_leases"),
                        WorkerStatus.valueOf(rs.getString("status")),
                        Instant.ofEpochMilli(rs.getLong("registered_at_epoch_ms")),
                        Instant.ofEpochMilli(rs.getLong("last_heartbeat_at_epoch_ms")),
                        Instant.ofEpochMilli(rs.getLong("updated_at_epoch_ms"))))
                        : Optional.empty(), workerId.value());
    }

    @Override
    public boolean reserveCapacity(WorkerId workerId, Instant updatedAt) {
        return jdbc.update("""
                UPDATE worker
                   SET active_leases=active_leases+1, updated_at_epoch_ms=?
                 WHERE id=? AND status='ACTIVE' AND active_leases < max_concurrency
                """, updatedAt.toEpochMilli(), workerId.value()) == 1;
    }

    @Override
    public boolean releaseCapacity(WorkerId workerId, Instant updatedAt) {
        return jdbc.update("""
                UPDATE worker
                   SET active_leases=active_leases-1, updated_at_epoch_ms=?
                 WHERE id=? AND active_leases > 0
                """, updatedAt.toEpochMilli(), workerId.value()) == 1;
    }

    @Override
    public boolean heartbeat(WorkerId workerId, Instant heartbeatAt) {
        return jdbc.update("""
                UPDATE worker SET last_heartbeat_at_epoch_ms=?, updated_at_epoch_ms=? WHERE id=?
                """, heartbeatAt.toEpochMilli(), heartbeatAt.toEpochMilli(), workerId.value()) == 1;
    }

    @Override
    public boolean changeStatus(WorkerId workerId, WorkerStatus status, Instant updatedAt) {
        return jdbc.update("""
                UPDATE worker SET status=?, updated_at_epoch_ms=? WHERE id=?
                """, status.name(), updatedAt.toEpochMilli(), workerId.value()) == 1;
    }
}
