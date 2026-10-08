package me.asu.aidev.worker.protocol.http;

import me.asu.aidev.development.application.CodeChangeCompletedHandler;
import me.asu.aidev.execution.application.TaskClaimService;
import me.asu.aidev.execution.application.TaskExecutionService;
import me.asu.aidev.execution.application.TaskCompletionContext;
import me.asu.aidev.execution.domain.WorkspaceResult;
import me.asu.aidev.work.application.CreateWorkTaskCommand;
import me.asu.aidev.work.application.WorkTaskService;
import me.asu.aidev.work.domain.TaskId;
import me.asu.aidev.work.domain.TaskPriority;
import me.asu.aidev.work.domain.TaskState;
import me.asu.aidev.work.domain.TaskType;
import me.asu.aidev.work.port.TaskDependencyRepository;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("sqlite")
class R6S2ImplementationCompletionIT {
    private static final String BASE = "1111111111111111111111111111111111111111";
    private static final String RESULT = "2222222222222222222222222222222222222222";
    private static final Path DB;
    static {
        try {
            DB = Files.createTempFile("r6-s2-completion-", ".db");
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
    @Autowired CodeChangeCompletedHandler codeChangeCompletedHandler;
    @Autowired TaskDependencyRepository dependencies;
    @Autowired JdbcTemplate jdbc;

    @Test
    void implementationCompletionCreatesIndependentReviewAndVerificationTasks() {
        TaskId implementationId = new TaskId("R6-S2-IMPL-1");
        taskService.create(new CreateWorkTaskCommand(
                implementationId, TaskType.IMPLEMENTATION, "WORK_ITEM", "WI-R6-S2-1",
                Set.of("coding", "java", "maven"), "coding", "Implement the requested change.",
                "repo-main", BASE, TaskPriority.HIGH, Set.of()));

        WorkerId coder = new WorkerId("r6-s2-coder");
        workerService.register(new RegisterWorkerCommand(
                coder, "test", Set.of("coding", "java", "maven", "repo:repo-main"), 1));
        var implementation = claimService.claimNext(coder).orElseThrow();
        executionService.start(implementationId, coder, implementation.lease().token());
        var workspaceResult = new WorkspaceResult(RESULT, List.of("pom.xml", "src/main/java/App.java"),
                "git:origin:" + implementation.workspace().branchName());
        var completion = executionService.complete(
                implementationId, coder, implementation.lease().token(), "thread-r6-s2-impl", workspaceResult);

        assertThat(completion.generatedTaskIds()).hasSize(2);
        var generatedTasks = completion.generatedTaskIds().stream().map(taskService::get).toList();
        var review = generatedTasks.stream().filter(t -> t.type() == TaskType.CODE_REVIEW).findFirst().orElseThrow();
        var verification = generatedTasks.stream().filter(t -> t.type() == TaskType.VERIFICATION).findFirst().orElseThrow();

        // Completion materialization is deliberately idempotent. Event replay, transaction retry,
        // or a temporarily duplicated legacy handler must converge on the same deterministic tasks.
        var replayed = codeChangeCompletedHandler.onCompleted(new TaskCompletionContext(
                taskService.get(implementationId), workspaceResult, null));
        assertThat(replayed).containsExactlyInAnyOrder(review.id(), verification.id());
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM work_task WHERE id IN (?,?)", Integer.class,
                review.id().value(), verification.id().value())).isEqualTo(2);

        assertThat(review.state()).isEqualTo(TaskState.READY);
        assertThat(verification.state()).isEqualTo(TaskState.READY);
        assertThat(review.subjectId()).isEqualTo("WI-R6-S2-1");
        assertThat(verification.subjectId()).isEqualTo("WI-R6-S2-1");
        assertThat(review.repositoryRef()).isEqualTo("repo-main");
        assertThat(verification.repositoryRef()).isEqualTo("repo-main");
        assertThat(review.baseRevision()).isEqualTo(RESULT);
        assertThat(verification.baseRevision()).isEqualTo(RESULT);

        assertThat(review.requiredCapabilities())
                .contains("code-review", "java", "maven", "repo:repo-main")
                .doesNotContain("coding", "verification");
        assertThat(verification.requiredCapabilities())
                .contains("verification", "java", "maven", "repo:repo-main")
                .doesNotContain("coding", "code-review");

        assertThat(dependencies.findDependencies(review.id())).containsExactly(implementationId);
        assertThat(dependencies.findDependencies(verification.id())).containsExactly(implementationId);
        assertThat(dependencies.findDependencies(review.id())).doesNotContain(verification.id());
        assertThat(dependencies.findDependencies(verification.id())).doesNotContain(review.id());

        WorkerId reviewer = new WorkerId("r6-s2-reviewer");
        workerService.register(new RegisterWorkerCommand(
                reviewer, "test", Set.of("code-review", "java", "maven", "repo:repo-main"), 1));
        var claimedReview = claimService.claimNext(reviewer).orElseThrow();
        assertThat(claimedReview.task().id()).isEqualTo(review.id());
        assertThat(claimedReview.workspace().baseRevision()).isEqualTo(RESULT);
        assertThat(claimedReview.inputs()).hasSize(1);
        assertThat(claimedReview.inputs().getFirst().taskId()).isEqualTo(implementationId);
        assertThat(claimedReview.inputs().getFirst().resultRevision()).isEqualTo(RESULT);

        WorkerId verifier = new WorkerId("r6-s2-verifier");
        workerService.register(new RegisterWorkerCommand(
                verifier, "test", Set.of("verification", "java", "maven", "repo:repo-main"), 1));
        var claimedVerification = claimService.claimNext(verifier).orElseThrow();
        assertThat(claimedVerification.task().id()).isEqualTo(verification.id());
        assertThat(claimedVerification.workspace().baseRevision()).isEqualTo(RESULT);
        assertThat(claimedVerification.inputs()).hasSize(1);
        assertThat(claimedVerification.inputs().getFirst().taskId()).isEqualTo(implementationId);
    }
}
