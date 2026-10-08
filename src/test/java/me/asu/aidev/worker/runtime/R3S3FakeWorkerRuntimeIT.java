package me.asu.aidev.worker.runtime;

import me.asu.aidev.execution.application.TaskExecutionService;
import me.asu.aidev.work.application.CreateWorkTaskCommand;
import me.asu.aidev.work.application.WorkTaskService;
import me.asu.aidev.work.domain.TaskId;
import me.asu.aidev.work.domain.TaskPriority;
import me.asu.aidev.work.domain.TaskState;
import me.asu.aidev.work.domain.TaskType;
import me.asu.aidev.worker.application.RegisterWorkerCommand;
import me.asu.aidev.worker.domain.WorkerId;
import me.asu.aidev.worker.runtime.fake.FakeTaskExecutor;
import me.asu.aidev.testsupport.DatabaseTestCleaner;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
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
class R3S3FakeWorkerRuntimeIT {
    private static final Path DB;
    static {
        try {
            DB = Files.createTempFile("r3-s3-runtime-", ".db");
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
    @Autowired WorkerRuntimeFactory runtimeFactory;
    @Autowired TaskExecutionService executionService;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void clearDatabase() {
        DatabaseTestCleaner.clear(jdbc);
    }

    @Test
    void tenWorkersExecuteOneHundredTasksWithoutDuplicateOwnership() throws Exception {
        Set<String> capabilities = Set.of("coding", "java", "s3-fleet");
        for (int i = 0; i < 100; i++) {
            taskService.create(new CreateWorkTaskCommand(
                    new TaskId("S3-FLEET-" + i), TaskType.IMPLEMENTATION, "WORK_ITEM", "WI-S3-" + i,
                    capabilities, "fake-coding", TaskPriority.NORMAL, Set.of()));
        }

        var fakeExecutor = new FakeTaskExecutor(Duration.ofMillis(8));
        var config = new WorkerRuntimeConfig(Duration.ofMillis(5), Duration.ofMillis(25), Duration.ofSeconds(3));
        List<WorkerRuntime> runtimes = new ArrayList<>();
        try {
            for (int i = 0; i < 10; i++) {
                var runtime = runtimeFactory.create(
                        new RegisterWorkerCommand(new WorkerId("s3-fleet-worker-" + i), "fake", capabilities, 1),
                        fakeExecutor, config);
                runtimes.add(runtime);
                runtime.start();
            }

            await(Duration.ofSeconds(15), () -> countTasks("S3-FLEET-%", "COMPLETED") == 100);

            assertThat(fakeExecutor.observedTasks()).hasSize(100);
            assertThat(fakeExecutor.maxActive()).isBetween(2, 10);
            assertThat(countAttempts("S3-FLEET-%")).isEqualTo(100);
            assertThat(countDistinctAttemptTasks("S3-FLEET-%")).isEqualTo(100);
            assertThat(countAttemptsByState("S3-FLEET-%", "COMPLETED")).isEqualTo(100);
            assertThat(sumActiveLeases("s3-fleet-worker-%")).isZero();
        } finally {
            runtimes.forEach(WorkerRuntime::close);
        }

        assertThat(countWorkersByStatus("s3-fleet-worker-%", "OFFLINE")).isEqualTo(10);
    }

    @Test
    void crashedWorkerLeavesLeaseForRecoveryAndAnotherWorkerCompletesSecondAttempt() throws Exception {
        var taskId = new TaskId("S3-CRASH-1");
        Set<String> capabilities = Set.of("coding", "java", "s3-crash");
        taskService.create(new CreateWorkTaskCommand(
                taskId, TaskType.IMPLEMENTATION, "WORK_ITEM", "WI-S3-CRASH-1",
                capabilities, "fake-crash", TaskPriority.HIGH, Set.of()));

        var started = new CountDownLatch(1);
        TaskExecutor blockingExecutor = task -> {
            started.countDown();
            Thread.sleep(Duration.ofMinutes(5).toMillis());
        };
        var config = new WorkerRuntimeConfig(Duration.ofMillis(5), Duration.ofMillis(20), Duration.ofSeconds(2));
        var crashed = runtimeFactory.create(
                new RegisterWorkerCommand(new WorkerId("s3-crash-worker"), "fake", capabilities, 1),
                blockingExecutor, config);
        crashed.start();
        assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
        await(Duration.ofSeconds(3), () -> taskService.get(taskId).state() == TaskState.RUNNING);

        crashed.crash();
        assertThat(crashed.state()).isEqualTo(WorkerRuntimeState.CRASHED);
        assertThat(taskService.get(taskId).state()).isEqualTo(TaskState.RUNNING);

        jdbc.update("UPDATE work_task SET lease_expires_at_epoch_ms=0 WHERE id=?", taskId.value());
        assertThat(executionService.recoverExpiredLeases(10)).isEqualTo(1);
        assertThat(taskService.get(taskId).state()).isEqualTo(TaskState.READY);

        var rescueExecutor = new FakeTaskExecutor(Duration.ofMillis(5));
        var rescue = runtimeFactory.create(
                new RegisterWorkerCommand(new WorkerId("s3-rescue-worker"), "fake", capabilities, 1),
                rescueExecutor, config);
        try {
            rescue.start();
            await(Duration.ofSeconds(5), () -> taskService.get(taskId).state() == TaskState.COMPLETED);
        } finally {
            rescue.close();
        }

        assertThat(jdbc.queryForObject(
                "SELECT attempt_no FROM work_task WHERE id=?", Integer.class, taskId.value())).isEqualTo(2);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM execution_attempt WHERE task_id=? AND state='EXPIRED'", Integer.class,
                taskId.value())).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM execution_attempt WHERE task_id=? AND state='COMPLETED'", Integer.class,
                taskId.value())).isEqualTo(1);
        assertThat(rescueExecutor.observedTasks()).containsExactly(taskId);
    }

    private int countTasks(String idLike, String state) {
        Integer value = jdbc.queryForObject(
                "SELECT COUNT(*) FROM work_task WHERE id LIKE ? AND state=?", Integer.class, idLike, state);
        return value == null ? 0 : value;
    }

    private int countAttempts(String taskLike) {
        Integer value = jdbc.queryForObject(
                "SELECT COUNT(*) FROM execution_attempt WHERE task_id LIKE ?", Integer.class, taskLike);
        return value == null ? 0 : value;
    }

    private int countDistinctAttemptTasks(String taskLike) {
        Integer value = jdbc.queryForObject(
                "SELECT COUNT(DISTINCT task_id) FROM execution_attempt WHERE task_id LIKE ?", Integer.class, taskLike);
        return value == null ? 0 : value;
    }

    private int countAttemptsByState(String taskLike, String state) {
        Integer value = jdbc.queryForObject(
                "SELECT COUNT(*) FROM execution_attempt WHERE task_id LIKE ? AND state=?", Integer.class, taskLike, state);
        return value == null ? 0 : value;
    }

    private int sumActiveLeases(String workerLike) {
        Integer value = jdbc.queryForObject(
                "SELECT COALESCE(SUM(active_leases),0) FROM worker WHERE id LIKE ?", Integer.class, workerLike);
        return value == null ? 0 : value;
    }

    private int countWorkersByStatus(String workerLike, String status) {
        Integer value = jdbc.queryForObject(
                "SELECT COUNT(*) FROM worker WHERE id LIKE ? AND status=?", Integer.class, workerLike, status);
        return value == null ? 0 : value;
    }

    private static void await(Duration timeout, CheckedCondition condition) throws Exception {
        long deadline = System.nanoTime() + timeout.toNanos();
        Throwable lastFailure = null;
        while (System.nanoTime() < deadline) {
            try {
                if (condition.test()) return;
            } catch (Throwable failure) {
                lastFailure = failure;
            }
            Thread.sleep(10);
        }
        AssertionError timeoutError = new AssertionError("condition not satisfied within " + timeout);
        if (lastFailure != null) timeoutError.initCause(lastFailure);
        throw timeoutError;
    }

    @FunctionalInterface
    private interface CheckedCondition {
        boolean test() throws Exception;
    }
}
