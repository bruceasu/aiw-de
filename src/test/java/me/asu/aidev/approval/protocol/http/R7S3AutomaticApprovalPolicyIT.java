package me.asu.aidev.approval.protocol.http;

import me.asu.aidev.approval.application.ApprovalPolicyGateListener;
import me.asu.aidev.approval.application.ApprovalService;
import me.asu.aidev.approval.domain.ApprovalChannel;
import me.asu.aidev.approval.domain.ApprovalStatus;
import me.asu.aidev.development.application.EngineeringGateService;
import me.asu.aidev.execution.application.TaskClaimService;
import me.asu.aidev.execution.application.TaskExecutionService;
import me.asu.aidev.execution.domain.CompletionReport;
import me.asu.aidev.execution.domain.CompletionVerdict;
import me.asu.aidev.execution.domain.WorkspaceResult;
import me.asu.aidev.work.application.CreateWorkTaskCommand;
import me.asu.aidev.work.application.WorkTaskService;
import me.asu.aidev.work.domain.TaskId;
import me.asu.aidev.work.domain.TaskPriority;
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
class R7S3AutomaticApprovalPolicyIT {
    private static final String BASE = "1111111111111111111111111111111111111111";
    private static final String RESULT = "9999999999999999999999999999999999999999";
    private static final Path DB;
    static {
        try {
            DB = Files.createTempFile("r7-s3-auto-approval-", ".db");
            Files.deleteIfExists(DB);
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + DB.toAbsolutePath());
        registry.add("aidev.approval.policy.mode", () -> "REQUIRED");
        registry.add("aidev.approval.policy.requested-by", () -> "control-plane:r7-s3-policy");
        registry.add("aidev.approval.policy.channel", () -> "API");
    }

    @Autowired WorkTaskService taskService;
    @Autowired WorkerService workerService;
    @Autowired TaskClaimService claimService;
    @Autowired TaskExecutionService executionService;
    @Autowired ApprovalService approvalService;
    @Autowired EngineeringGateService engineeringGates;
    @Autowired ApprovalPolicyGateListener policyListener;

    @Test
    void passedEngineeringGateAutomaticallyMaterializesExactlyOnePendingApproval() {
        TaskId implementationId = new TaskId("R7-S3-IMPL-1");
        taskService.create(new CreateWorkTaskCommand(
                implementationId, TaskType.IMPLEMENTATION, "WORK_ITEM", "WI-R7-S3-1",
                Set.of("coding", "java", "maven"), "coding", "Implement automatic approval policy test change.",
                "repo-main", BASE, TaskPriority.HIGH, Set.of()));

        WorkerId coder = register("r7-s3-coder", Set.of("coding", "java", "maven", "repo:repo-main"));
        WorkerId reviewer = register("r7-s3-reviewer", Set.of("code-review", "java", "maven", "repo:repo-main"));
        WorkerId verifier = register("r7-s3-verifier", Set.of("verification", "java", "maven", "repo:repo-main"));

        var implementation = claimService.claimNext(coder).orElseThrow();
        executionService.start(implementationId, coder, implementation.lease().token());
        var completed = executionService.complete(
                implementationId, coder, implementation.lease().token(), "thread-r7-s3-impl",
                new WorkspaceResult(RESULT, List.of("src/main/java/App.java"),
                        "git:origin:" + implementation.workspace().branchName()));

        TaskId reviewId = completed.generatedTaskIds().stream()
                .filter(id -> taskService.get(id).type() == TaskType.CODE_REVIEW).findFirst().orElseThrow();
        TaskId verificationId = completed.generatedTaskIds().stream()
                .filter(id -> taskService.get(id).type() == TaskType.VERIFICATION).findFirst().orElseThrow();

        var review = claimService.claimNext(reviewer).orElseThrow();
        executionService.start(reviewId, reviewer, review.lease().token());
        executionService.complete(reviewId, reviewer, review.lease().token(), "thread-r7-s3-review",
                new WorkspaceResult(RESULT, List.of()),
                new CompletionReport(CompletionVerdict.PASS, "Review passed.\nAIW_VERDICT: PASS"));

        var verification = claimService.claimNext(verifier).orElseThrow();
        executionService.start(verificationId, verifier, verification.lease().token());
        executionService.complete(verificationId, verifier, verification.lease().token(), "thread-r7-s3-verify",
                new WorkspaceResult(RESULT, List.of()),
                new CompletionReport(CompletionVerdict.PASS, "Verification passed.\nAIW_VERDICT: PASS"));

        var approval = approvalService.findBySourceTask(implementationId).orElseThrow();
        assertThat(approval.status()).isEqualTo(ApprovalStatus.PENDING);
        assertThat(approval.resultRevision()).isEqualTo(RESULT);
        assertThat(approval.requestedBy()).isEqualTo("control-plane:r7-s3-policy");
        assertThat(approval.channel()).isEqualTo(ApprovalChannel.API);
        assertThat(approval.channelRef()).isNull();

        // Finalized-gate observation is replay-safe: policy materialization remains exactly one request.
        var gate = engineeringGates.find(implementationId).orElseThrow();
        String approvalId = approval.id().value();
        policyListener.onFinalized(gate);
        policyListener.onFinalized(gate);
        assertThat(approvalService.findBySourceTask(implementationId).orElseThrow().id().value())
                .isEqualTo(approvalId);
    }

    private WorkerId register(String id, Set<String> capabilities) {
        WorkerId workerId = new WorkerId(id);
        workerService.register(new RegisterWorkerCommand(workerId, "test", capabilities, 1));
        return workerId;
    }
}
