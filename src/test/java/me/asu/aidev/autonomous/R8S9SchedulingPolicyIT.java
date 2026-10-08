package me.asu.aidev.autonomous;

import me.asu.aidev.autonomous.application.AutonomousRequirementService;
import me.asu.aidev.autonomous.application.StartAutonomousRequirementCommand;
import me.asu.aidev.execution.application.TaskClaimService;
import me.asu.aidev.work.application.CreateWorkTaskCommand;
import me.asu.aidev.work.application.WorkTaskService;
import me.asu.aidev.work.domain.TaskId;
import me.asu.aidev.work.domain.TaskPriority;
import me.asu.aidev.work.domain.TaskState;
import me.asu.aidev.work.domain.TaskType;
import me.asu.aidev.work.domain.WorkTask;
import me.asu.aidev.work.port.WorkTaskRepository;
import me.asu.aidev.worker.application.RegisterWorkerCommand;
import me.asu.aidev.worker.application.WorkerService;
import me.asu.aidev.worker.domain.WorkerId;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("sqlite")
class R8S9SchedulingPolicyIT {
    private static final String BASE = "4444444444444444444444444444444444444444";
    private static final Path DB;
    static {
        try {
            DB = Files.createTempFile("r8-s9-scheduling-", ".db");
            Files.deleteIfExists(DB);
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + DB.toAbsolutePath());
        registry.add("aidev.approval.policy.mode", () -> "MANUAL");
    }

    @Autowired AutonomousRequirementService autonomous;
    @Autowired WorkTaskService taskService;
    @Autowired WorkTaskRepository taskRepository;
    @Autowired WorkerService workers;
    @Autowired TaskClaimService claims;

    @Test
    void pullSchedulerHonorsPrioritySpreadsEqualPriorityAcrossRunsAndAgesLongWaitingWork() {
        // 1. Explicit business priority wins over creation order.
        var normal = autonomous.start(new StartAutonomousRequirementCommand(
                "r8s9-priority-normal", "Normal scheduling task.", "repo-priority-normal", BASE,
                Set.of("sched-priority"), TaskPriority.NORMAL));
        var critical = autonomous.start(new StartAutonomousRequirementCommand(
                "r8s9-priority-critical", "Critical scheduling task.", "repo-priority-critical", BASE,
                Set.of("sched-priority"), TaskPriority.CRITICAL));
        WorkerId priorityWorker = register("r8s9-priority-worker", Set.of(
                "requirement-analysis", "sched-priority",
                "repo:repo-priority-normal", "repo:repo-priority-critical"), 1);
        var priorityClaim = claims.claimNext(priorityWorker).orElseThrow();
        assertThat(priorityClaim.task().subjectId()).isEqualTo(critical.runId());
        assertThat(priorityClaim.task().subjectId()).isNotEqualTo(normal.runId());

        // 2. Equal-priority work is spread across runs by active lease count.
        var runA = autonomous.start(new StartAutonomousRequirementCommand(
                "r8s9-fair-a", "Fairness run A.", "repo-fair", BASE,
                Set.of("sched-fair"), TaskPriority.HIGH));
        var runB = autonomous.start(new StartAutonomousRequirementCommand(
                "r8s9-fair-b", "Fairness run B.", "repo-fair", BASE,
                Set.of("sched-fair"), TaskPriority.HIGH));
        taskService.create(new CreateWorkTaskCommand(
                new TaskId(runA.runId() + "~extra-1"), TaskType.REQUIREMENT_ANALYSIS,
                AutonomousRequirementService.SUBJECT_TYPE, runA.runId(),
                Set.of("requirement-analysis", "sched-fair"), "r8s9-fair", "Extra A1",
                "repo-fair", BASE, TaskPriority.HIGH, Set.of()));
        taskService.create(new CreateWorkTaskCommand(
                new TaskId(runA.runId() + "~extra-2"), TaskType.REQUIREMENT_ANALYSIS,
                AutonomousRequirementService.SUBJECT_TYPE, runA.runId(),
                Set.of("requirement-analysis", "sched-fair"), "r8s9-fair", "Extra A2",
                "repo-fair", BASE, TaskPriority.HIGH, Set.of()));

        WorkerId fairWorker = register("r8s9-fair-worker", Set.of(
                "requirement-analysis", "sched-fair", "repo:repo-fair"), 2);
        var fairFirst = claims.claimNext(fairWorker).orElseThrow();
        var fairSecond = claims.claimNext(fairWorker).orElseThrow();
        assertThat(Set.of(fairFirst.task().subjectId(), fairSecond.task().subjectId()))
                .containsExactlyInAnyOrder(runA.runId(), runB.runId());

        // 3. Waiting-time aging eventually prevents a lower-priority READY task from starving forever.
        Instant now = Instant.now();
        taskRepository.insert(new WorkTask(
                new TaskId("R8S9-AGED-LOW"), TaskType.REQUIREMENT_ANALYSIS, TaskState.READY,
                "SCHEDULER_TEST", "aging-low", Set.of("sched-aging"), "r8s9-aging", "Old low priority",
                null, null, TaskPriority.LOW, 0,
                now.minus(9, ChronoUnit.HOURS), now.minus(9, ChronoUnit.HOURS)));
        taskRepository.insert(new WorkTask(
                new TaskId("R8S9-RECENT-HIGH"), TaskType.REQUIREMENT_ANALYSIS, TaskState.READY,
                "SCHEDULER_TEST", "aging-high", Set.of("sched-aging"), "r8s9-aging", "Recent high priority",
                null, null, TaskPriority.HIGH, 0, now, now));

        WorkerId agingWorker = register("r8s9-aging-worker", Set.of("sched-aging"), 1);
        var agingClaim = claims.claimNext(agingWorker).orElseThrow();
        assertThat(agingClaim.task().id().value()).isEqualTo("R8S9-AGED-LOW");
    }

    private WorkerId register(String id, Set<String> capabilities, int maxConcurrency) {
        WorkerId workerId = new WorkerId(id);
        workers.register(new RegisterWorkerCommand(workerId, "test", capabilities, maxConcurrency));
        return workerId;
    }
}
