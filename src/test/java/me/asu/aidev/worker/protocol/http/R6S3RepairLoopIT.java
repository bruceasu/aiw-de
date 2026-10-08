package me.asu.aidev.worker.protocol.http;

import me.asu.aidev.execution.application.TaskClaimService;
import me.asu.aidev.execution.application.TaskExecutionService;
import me.asu.aidev.execution.domain.CompletionReport;
import me.asu.aidev.execution.domain.CompletionVerdict;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("sqlite")
class R6S3RepairLoopIT {
    private static final String BASE = "1111111111111111111111111111111111111111";
    private static final String IMPLEMENTED = "2222222222222222222222222222222222222222";
    private static final String REPAIRED = "3333333333333333333333333333333333333333";
    private static final Path DB;
    static {
        try {
            DB = Files.createTempFile("r6-s3-repair-loop-", ".db");
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
    @Autowired TaskDependencyRepository dependencies;

    @Test
    void reviewOrVerificationFindingCreatesSingleRepairAndRepairFansOutAgain() {
        TaskId implementationId = new TaskId("R6-S3-IMPL-1");
        taskService.create(new CreateWorkTaskCommand(
                implementationId, TaskType.IMPLEMENTATION, "WORK_ITEM", "WI-R6-S3-1",
                Set.of("coding", "java", "maven"), "coding", "Implement the requested change.",
                "repo-main", BASE, TaskPriority.HIGH, Set.of()));

        WorkerId coder = register("r6-s3-coder", Set.of("coding", "java", "maven", "repo:repo-main"));
        var implementation = claimService.claimNext(coder).orElseThrow();
        executionService.start(implementationId, coder, implementation.lease().token());
        var implementationCompletion = executionService.complete(
                implementationId, coder, implementation.lease().token(), "thread-r6-s3-impl",
                new WorkspaceResult(IMPLEMENTED, List.of("src/main/java/App.java"),
                        "git:origin:" + implementation.workspace().branchName()));

        assertThat(implementationCompletion.generatedTaskIds()).hasSize(2);
        TaskId reviewId = implementationCompletion.generatedTaskIds().stream()
                .filter(id -> taskService.get(id).type() == TaskType.CODE_REVIEW).findFirst().orElseThrow();
        TaskId verificationId = implementationCompletion.generatedTaskIds().stream()
                .filter(id -> taskService.get(id).type() == TaskType.VERIFICATION).findFirst().orElseThrow();

        WorkerId reviewer = register("r6-s3-reviewer", Set.of("code-review", "java", "maven", "repo:repo-main"));
        var review = claimService.claimNext(reviewer).orElseThrow();
        assertThat(review.task().id()).isEqualTo(reviewId);
        executionService.start(reviewId, reviewer, review.lease().token());
        var reviewCompletion = executionService.complete(
                reviewId, reviewer, review.lease().token(), "thread-r6-s3-review",
                new WorkspaceResult(IMPLEMENTED, List.of()),
                new CompletionReport(CompletionVerdict.REPAIR_REQUIRED,
                        "Null handling is missing in App.java; add a guard.\nAIW_VERDICT: REPAIR_REQUIRED"));
        assertThat(reviewCompletion.generatedTaskIds()).isEmpty();

        WorkerId verifier = register("r6-s3-verifier", Set.of("verification", "java", "maven", "repo:repo-main"));
        var verification = claimService.claimNext(verifier).orElseThrow();
        assertThat(verification.task().id()).isEqualTo(verificationId);
        executionService.start(verificationId, verifier, verification.lease().token());
        var verificationCompletion = executionService.complete(
                verificationId, verifier, verification.lease().token(), "thread-r6-s3-verify",
                new WorkspaceResult(IMPLEMENTED, List.of()),
                new CompletionReport(CompletionVerdict.PASS,
                        "mvn verify passed.\nAIW_VERDICT: PASS"));

        assertThat(verificationCompletion.generatedTaskIds()).hasSize(1);
        TaskId repairId = verificationCompletion.generatedTaskIds().getFirst();
        var repair = taskService.get(repairId);
        assertThat(repair.type()).isEqualTo(TaskType.REPAIR);
        assertThat(repair.state()).isEqualTo(TaskState.READY);
        assertThat(repair.repositoryRef()).isEqualTo("repo-main");
        assertThat(repair.baseRevision()).isEqualTo(IMPLEMENTED);
        assertThat(repair.requiredCapabilities())
                .contains("repair", "java", "maven", "repo:repo-main")
                .doesNotContain("coding", "code-review", "verification");
        assertThat(dependencies.findDependencies(repairId))
                .containsExactlyInAnyOrder(implementationId, reviewId, verificationId);

        WorkerId repairer = register("r6-s3-repairer", Set.of("repair", "java", "maven", "repo:repo-main"));
        var claimedRepair = claimService.claimNext(repairer).orElseThrow();
        assertThat(claimedRepair.task().id()).isEqualTo(repairId);
        assertThat(claimedRepair.inputs()).hasSize(3);
        var reviewInput = claimedRepair.inputs().stream().filter(i -> i.taskId().equals(reviewId)).findFirst().orElseThrow();
        var verificationInput = claimedRepair.inputs().stream().filter(i -> i.taskId().equals(verificationId)).findFirst().orElseThrow();
        assertThat(reviewInput.completionVerdict()).isEqualTo(CompletionVerdict.REPAIR_REQUIRED);
        assertThat(reviewInput.completionSummary()).contains("Null handling is missing");
        assertThat(verificationInput.completionVerdict()).isEqualTo(CompletionVerdict.PASS);

        executionService.start(repairId, repairer, claimedRepair.lease().token());
        var repairCompletion = executionService.complete(
                repairId, repairer, claimedRepair.lease().token(), "thread-r6-s3-repair",
                new WorkspaceResult(REPAIRED, List.of("src/main/java/App.java"),
                        "git:origin:" + claimedRepair.workspace().branchName()));

        assertThat(repairCompletion.generatedTaskIds()).hasSize(2);
        var nextTasks = repairCompletion.generatedTaskIds().stream().map(taskService::get).toList();
        assertThat(nextTasks).extracting(t -> t.type())
                .containsExactlyInAnyOrder(TaskType.CODE_REVIEW, TaskType.VERIFICATION);
        assertThat(nextTasks).allSatisfy(t -> {
            assertThat(t.baseRevision()).isEqualTo(REPAIRED);
            assertThat(t.state()).isEqualTo(TaskState.READY);
            assertThat(dependencies.findDependencies(t.id())).containsExactly(repairId);
        });

        TaskId secondReviewId = nextTasks.stream().filter(t -> t.type() == TaskType.CODE_REVIEW).findFirst().orElseThrow().id();
        TaskId secondVerificationId = nextTasks.stream().filter(t -> t.type() == TaskType.VERIFICATION).findFirst().orElseThrow().id();

        var secondReview = claimService.claimNext(reviewer).orElseThrow();
        assertThat(secondReview.task().id()).isEqualTo(secondReviewId);
        executionService.start(secondReviewId, reviewer, secondReview.lease().token());
        var secondReviewCompletion = executionService.complete(
                secondReviewId, reviewer, secondReview.lease().token(), "thread-r6-s3-review-2",
                new WorkspaceResult(REPAIRED, List.of()),
                new CompletionReport(CompletionVerdict.PASS, "Review passed.\nAIW_VERDICT: PASS"));
        assertThat(secondReviewCompletion.generatedTaskIds()).isEmpty();

        var secondVerification = claimService.claimNext(verifier).orElseThrow();
        assertThat(secondVerification.task().id()).isEqualTo(secondVerificationId);
        executionService.start(secondVerificationId, verifier, secondVerification.lease().token());
        var secondVerificationCompletion = executionService.complete(
                secondVerificationId, verifier, secondVerification.lease().token(), "thread-r6-s3-verify-2",
                new WorkspaceResult(REPAIRED, List.of()),
                new CompletionReport(CompletionVerdict.PASS, "Verification passed.\nAIW_VERDICT: PASS"));
        assertThat(secondVerificationCompletion.generatedTaskIds()).isEmpty();
    }

    private WorkerId register(String id, Set<String> capabilities) {
        WorkerId workerId = new WorkerId(id);
        workerService.register(new RegisterWorkerCommand(workerId, "test", capabilities, 1));
        return workerId;
    }
}
