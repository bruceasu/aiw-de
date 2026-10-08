package me.asu.aidev.work.jdbc;

import me.asu.aidev.execution.application.TaskClaimService;
import me.asu.aidev.execution.application.TaskExecutionService;
import me.asu.aidev.work.application.CreateWorkTaskCommand;
import me.asu.aidev.work.application.WorkTaskService;
import me.asu.aidev.work.domain.TaskId;
import me.asu.aidev.work.domain.TaskPriority;
import me.asu.aidev.work.domain.TaskState;
import me.asu.aidev.work.domain.TaskType;
import me.asu.aidev.worker.application.RegisterWorkerCommand;
import me.asu.aidev.worker.application.WorkerService;
import me.asu.aidev.worker.domain.WorkerId;
import me.asu.aidev.testsupport.DatabaseTestCleaner;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
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
class R3S1WorkTaskGraphIT {
    private static final Path DB;
    static {
        try {
            DB = Files.createTempFile("r3-clean-s2-", ".db");
            Files.deleteIfExists(DB);
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + DB.toAbsolutePath());
    }

    @Autowired WorkTaskService taskService;
    @Autowired WorkerService workerService;
    @Autowired TaskClaimService claimService;
    @Autowired TaskExecutionService executionService;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void clearDatabase() {
        DatabaseTestCleaner.clear(jdbc);
    }

    @Test
    void graphUnlocksOnlyAfterAllDependenciesComplete() {
        var t1 = taskService.create(command("T1", Set.of()));
        var t2 = taskService.create(command("T2", Set.of()));
        var t3 = taskService.create(command("T3", Set.of(t1.id(), t2.id())));
        var workerId = new WorkerId("worker-java");
        workerService.register(new RegisterWorkerCommand(workerId, "fake", Set.of("coding", "java"), 1));

        assertThat(t1.state()).isEqualTo(TaskState.READY);
        assertThat(t2.state()).isEqualTo(TaskState.READY);
        assertThat(t3.state()).isEqualTo(TaskState.BLOCKED);

        var c1 = claimService.claimNext(workerId).orElseThrow();
        assertThat(c1.task().id()).isEqualTo(t1.id());
        executionService.start(c1.task().id(), workerId, c1.lease().token());
        executionService.complete(c1.task().id(), workerId, c1.lease().token());
        assertThat(taskService.get(t3.id()).state()).isEqualTo(TaskState.BLOCKED);

        var c2 = claimService.claimNext(workerId).orElseThrow();
        assertThat(c2.task().id()).isEqualTo(t2.id());
        executionService.start(c2.task().id(), workerId, c2.lease().token());
        executionService.complete(c2.task().id(), workerId, c2.lease().token());

        var ready = taskService.get(t3.id());
        assertThat(ready.state()).isEqualTo(TaskState.READY);
        assertThat(ready.requiredCapabilities()).containsExactlyInAnyOrder("coding", "java");
    }

    @Test
    void taskCreatedAfterDependencyCompletedStartsReady() {
        TaskId predecessor = new TaskId("R3-LATE-PREDECESSOR");
        taskService.create(command(predecessor.value(), Set.of()));

        WorkerId worker = new WorkerId("r3-late-worker");
        workerService.register(new RegisterWorkerCommand(
                worker, "test", Set.of("coding", "java"), 1));
        var claimed = claimService.claimNext(worker).orElseThrow();
        executionService.start(predecessor, worker, claimed.lease().token());
        executionService.complete(predecessor, worker, claimed.lease().token());

        TaskId dependent = new TaskId("R3-LATE-DEPENDENT");
        var created = taskService.create(command(dependent.value(), Set.of(predecessor)));

        assertThat(created.state()).isEqualTo(TaskState.READY);
    }

    private CreateWorkTaskCommand command(String id, Set<TaskId> dependencies) {
        return new CreateWorkTaskCommand(new TaskId(id), TaskType.IMPLEMENTATION, "WORK_ITEM", "WI-" + id,
                Set.of("coding", "java"), "coding-work-package-executor", TaskPriority.NORMAL, dependencies);
    }
}
