package me.asu.aidev.worker.application;

import me.asu.aidev.worker.domain.Worker;
import me.asu.aidev.worker.domain.WorkerId;
import me.asu.aidev.worker.domain.WorkerStatus;
import me.asu.aidev.worker.port.WorkerRepository;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkerService {
    private final WorkerRepository workers;
    private final Clock clock;

    @Autowired
    public WorkerService(WorkerRepository workers) {
        this(workers, Clock.systemUTC());
    }

    WorkerService(WorkerRepository workers, Clock clock) {
        this.workers = workers;
        this.clock = clock;
    }

    @Transactional
    public Worker register(RegisterWorkerCommand command) {
        var now = clock.instant();
        Worker worker = new Worker(command.workerId(), command.runtimeType(), command.capabilities(),
                command.maxConcurrency(), 0, WorkerStatus.ACTIVE, now, now, now);
        workers.register(worker);
        return workers.find(command.workerId()).orElseThrow();
    }

    @Transactional
    public void heartbeat(WorkerId workerId) {
        if (!workers.heartbeat(workerId, clock.instant())) {
            throw new IllegalStateException("worker not found: " + workerId.value());
        }
    }

    @Transactional
    public void drain(WorkerId workerId) {
        if (!workers.changeStatus(workerId, WorkerStatus.DRAINING, clock.instant())) {
            throw new IllegalStateException("worker not found: " + workerId.value());
        }
    }

    @Transactional
    public void offline(WorkerId workerId) {
        if (!workers.changeStatus(workerId, WorkerStatus.OFFLINE, clock.instant())) {
            throw new IllegalStateException("worker not found: " + workerId.value());
        }
    }

    @Transactional(readOnly = true)
    public Worker get(WorkerId workerId) {
        return workers.find(workerId).orElseThrow(() -> new IllegalStateException("worker not found: " + workerId.value()));
    }
}
