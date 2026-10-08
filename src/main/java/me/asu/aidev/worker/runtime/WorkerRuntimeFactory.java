package me.asu.aidev.worker.runtime;

import me.asu.aidev.execution.application.TaskClaimService;
import me.asu.aidev.execution.application.TaskExecutionService;
import me.asu.aidev.worker.application.RegisterWorkerCommand;
import me.asu.aidev.worker.application.WorkerService;
import org.springframework.stereotype.Component;

@Component
public class WorkerRuntimeFactory {
    private final WorkerService workers;
    private final TaskClaimService claims;
    private final TaskExecutionService executions;

    public WorkerRuntimeFactory(WorkerService workers,
                                TaskClaimService claims,
                                TaskExecutionService executions) {
        this.workers = workers;
        this.claims = claims;
        this.executions = executions;
    }

    public WorkerRuntime create(RegisterWorkerCommand registration,
                                TaskExecutor executor,
                                WorkerRuntimeConfig config) {
        return new WorkerRuntime(registration, workers, claims, executions, executor, config);
    }
}
