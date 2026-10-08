package me.asu.aidev.worker.port;

import me.asu.aidev.worker.domain.Worker;
import me.asu.aidev.worker.domain.WorkerId;
import me.asu.aidev.worker.domain.WorkerStatus;
import java.time.Instant;
import java.util.Optional;

public interface WorkerRepository {
    void register(Worker worker);
    Optional<Worker> find(WorkerId workerId);
    boolean reserveCapacity(WorkerId workerId, Instant updatedAt);
    boolean releaseCapacity(WorkerId workerId, Instant updatedAt);
    boolean heartbeat(WorkerId workerId, Instant heartbeatAt);
    boolean changeStatus(WorkerId workerId, WorkerStatus status, Instant updatedAt);
}
