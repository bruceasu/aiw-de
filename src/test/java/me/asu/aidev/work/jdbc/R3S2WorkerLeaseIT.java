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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
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
class R3S2WorkerLeaseIT {
    private static final Path DB;
    static {
        try {
            DB = Files.createTempFile("r3-s2-worker-", ".db");
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

    @Test
    void capabilityAndCapacityAreEnforcedAndExpiredLeaseReturnsToReady() {
        var task = taskService.create(new CreateWorkTaskCommand(
                new TaskId("JAVA-1"), TaskType.IMPLEMENTATION, "WORK_ITEM", "WI-JAVA-1",
                Set.of("coding", "java"), "coding", TaskPriority.NORMAL, Set.of()));

        var python = new WorkerId("worker-python");
        workerService.register(new RegisterWorkerCommand(python, "fake", Set.of("coding", "python"), 1));
        assertThat(claimService.claimNext(python)).isEmpty();

        var java = new WorkerId("worker-java-s2");
        workerService.register(new RegisterWorkerCommand(java, "fake", Set.of("coding", "java"), 1));
        var claimed = claimService.claimNext(java).orElseThrow();
        assertThat(claimed.task().id()).isEqualTo(task.id());
        assertThat(taskService.get(task.id()).state()).isEqualTo(TaskState.CLAIMED);
        assertThat(claimService.claimNext(java)).isEmpty();

        jdbc.update("UPDATE work_task SET lease_expires_at_epoch_ms=0 WHERE id=?", task.id().value());
        assertThat(executionService.recoverExpiredLeases(10)).isEqualTo(1);
        assertThat(taskService.get(task.id()).state()).isEqualTo(TaskState.READY);
        assertThat(workerService.get(java).activeLeases()).isZero();

        var reclaimed = claimService.claimNext(java).orElseThrow();
        assertThat(reclaimed.attemptNo()).isEqualTo(2);
        executionService.start(task.id(), java, reclaimed.lease().token());
        executionService.complete(task.id(), java, reclaimed.lease().token());
        assertThat(taskService.get(task.id()).state()).isEqualTo(TaskState.COMPLETED);
        assertThat(workerService.get(java).activeLeases()).isZero();
    }
}
