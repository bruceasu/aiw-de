package me.asu.aidev.worker.protocol.http;

import me.asu.aidev.execution.application.TaskClaimService;
import me.asu.aidev.execution.application.TaskExecutionService;
import me.asu.aidev.execution.domain.WorkspaceResult;
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
import java.util.List;
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
class R6ResultHandoffIT {
    private static final String BASE = "1111111111111111111111111111111111111111";
    private static final String RESULT = "2222222222222222222222222222222222222222";
    private static final Path DB;
    static {
        try {
            DB = Files.createTempFile("r6-result-handoff-", ".db");
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

    @Test
    void completedDependencyResultIsMaterializedAsDownstreamInput() {
        TaskId implementationId = new TaskId("R6-IMPL-1");
        taskService.create(new CreateWorkTaskCommand(
                implementationId, TaskType.IMPLEMENTATION, "WORK_ITEM", "WI-R6-1",
                Set.of("coding", "java"), "coding", "Implement the change.",
                "repo-main", BASE, TaskPriority.HIGH, Set.of()));

        WorkerId codingWorker = new WorkerId("r6-coder");
        workerService.register(new RegisterWorkerCommand(
                codingWorker, "test", Set.of("coding", "java", "repo:repo-main"), 1));
        var implementation = claimService.claimNext(codingWorker).orElseThrow();
        executionService.start(implementationId, codingWorker, implementation.lease().token());
        executionService.complete(implementationId, codingWorker, implementation.lease().token(), "thread-r6-impl",
                new WorkspaceResult(RESULT, List.of("pom.xml", "src/main/java/App.java"),
                        "git:origin:" + implementation.workspace().branchName()));

        TaskId reviewId = new TaskId("R6-REVIEW-1");
        taskService.create(new CreateWorkTaskCommand(
                reviewId, TaskType.CODE_REVIEW, "WORK_ITEM", "WI-R6-1",
                Set.of("code-review"), "code-review", "Review the implementation result.",
                "repo-main", RESULT, TaskPriority.HIGH, Set.of(implementationId)));

        WorkerId reviewWorker = new WorkerId("r6-reviewer");
        workerService.register(new RegisterWorkerCommand(
                reviewWorker, "test", Set.of("code-review", "repo:repo-main"), 1));

        // Diagnostics are intentionally asserted before claim so a readiness/capability regression
        // fails at the actual invariant instead of surfacing as Optional.empty().
        var persistedReview = taskService.get(reviewId);
        assertThat(persistedReview.state()).isEqualTo(TaskState.READY);
        assertThat(persistedReview.requiredCapabilities())
                .containsExactlyInAnyOrder("code-review", "repo:repo-main");
        assertThat(workerService.get(reviewWorker).supports(persistedReview.requiredCapabilities())).isTrue();

        var review = claimService.claimNext(reviewWorker).orElseThrow();

        assertThat(review.task().id()).isEqualTo(reviewId);
        assertThat(review.workspace().baseRevision()).isEqualTo(RESULT);
        assertThat(review.inputs()).hasSize(1);
        var input = review.inputs().getFirst();
        assertThat(input.taskId()).isEqualTo(implementationId);
        assertThat(input.taskType()).isEqualTo(TaskType.IMPLEMENTATION);
        assertThat(input.repositoryRef()).isEqualTo("repo-main");
        assertThat(input.baseRevision()).isEqualTo(BASE);
        assertThat(input.resultRevision()).isEqualTo(RESULT);
        assertThat(input.publicationRef()).isEqualTo("git:origin:" + implementation.workspace().branchName());
        assertThat(input.changedFiles()).containsExactly("pom.xml", "src/main/java/App.java");
    }
}
