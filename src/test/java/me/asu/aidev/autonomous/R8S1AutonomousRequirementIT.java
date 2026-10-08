package me.asu.aidev.autonomous;

import me.asu.aidev.approval.application.ApprovalService;
import me.asu.aidev.approval.domain.ApprovalStatus;
import me.asu.aidev.autonomous.application.AutonomousRequirementService;
import me.asu.aidev.autonomous.application.StartAutonomousRequirementCommand;
import me.asu.aidev.development.application.EngineeringGateService;
import me.asu.aidev.development.domain.EngineeringGateStatus;
import me.asu.aidev.execution.application.TaskClaimService;
import me.asu.aidev.execution.application.TaskExecutionService;
import me.asu.aidev.execution.domain.CompletionReport;
import me.asu.aidev.execution.domain.CompletionVerdict;
import me.asu.aidev.execution.domain.WorkspaceResult;
import me.asu.aidev.knowledge.application.KnowledgeService;
import me.asu.aidev.work.application.WorkTaskService;
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
class R8S1AutonomousRequirementIT {
    private static final String BASE = "1111111111111111111111111111111111111111";
    private static final String RESULT = "8888888888888888888888888888888888888888";
    private static final Path DB;
    static {
        try {
            DB = Files.createTempFile("r8-s1-autonomous-", ".db");
            Files.deleteIfExists(DB);
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + DB.toAbsolutePath());
        registry.add("aidev.approval.policy.mode", () -> "REQUIRED");
        registry.add("aidev.approval.policy.channel", () -> "API");
    }

    @Autowired AutonomousRequirementService autonomous;
    @Autowired WorkTaskService tasks;
    @Autowired WorkerService workers;
    @Autowired TaskClaimService claims;
    @Autowired TaskExecutionService executions;
    @Autowired EngineeringGateService gates;
    @Autowired ApprovalService approvals;
    @Autowired KnowledgeService knowledge;

    @Test
    void requirementAutonomouslyMaterializesArchitecturePlanImplementationGateAndApproval() {
        var run = autonomous.start(new StartAutonomousRequirementCommand(
                "Add a health endpoint and tests without breaking existing behavior.",
                "repo-main", BASE, Set.of("java", "maven"), TaskPriority.HIGH));

        WorkerId analyst = register("r8-analyst", Set.of("requirement-analysis", "java", "maven", "repo:repo-main"));
        WorkerId architect = register("r8-architect", Set.of("solution-architecture", "java", "maven", "repo:repo-main"));
        WorkerId planner = register("r8-planner", Set.of("implementation-planning", "java", "maven", "repo:repo-main"));
        WorkerId coder = register("r8-coder", Set.of("coding", "java", "maven", "repo:repo-main"));
        WorkerId reviewer = register("r8-reviewer", Set.of("code-review", "java", "maven", "repo:repo-main"));
        WorkerId verifier = register("r8-verifier", Set.of("verification", "java", "maven", "repo:repo-main"));

        var requirement = claims.claimNext(analyst).orElseThrow();
        assertThat(requirement.task().type()).isEqualTo(TaskType.REQUIREMENT_ANALYSIS);
        completePlanningStage(requirement.task().id(), analyst, requirement.lease().token(),
                "Requirement specification with acceptance criteria.");
        var requirementDocument = knowledge.find(requirement.task().id().value()).orElseThrow();
        assertThat(requirementDocument.markdown()).isEqualTo("Requirement specification with acceptance criteria.");
        assertThat(requirementDocument.repositoryRef()).isEqualTo("repo-main");
        assertThat(requirementDocument.baseRevision()).isEqualTo(BASE);
        assertThat(requirementDocument.upstreamDocumentIds()).isEmpty();

        var architecture = claims.claimNext(architect).orElseThrow();
        assertThat(architecture.task().type()).isEqualTo(TaskType.SOLUTION_ARCHITECTURE);
        assertThat(architecture.inputs()).hasSize(1);
        assertThat(architecture.inputs().getFirst().completionSummary()).contains("Requirement specification");
        completePlanningStage(architecture.task().id(), architect, architecture.lease().token(),
                "Architecture: controller, service boundary, and focused integration test.");
        assertThat(knowledge.find(architecture.task().id().value()).orElseThrow().upstreamDocumentIds())
                .containsExactly(requirement.task().id().value());

        var planning = claims.claimNext(planner).orElseThrow();
        assertThat(planning.task().type()).isEqualTo(TaskType.IMPLEMENTATION_PLANNING);
        assertThat(planning.inputs()).hasSize(2);
        completePlanningStage(planning.task().id(), planner, planning.lease().token(),
                "Plan: implement endpoint, add tests, run mvn verify.");
        assertThat(knowledge.find(planning.task().id().value()).orElseThrow().upstreamDocumentIds())
                .containsExactlyInAnyOrder(requirement.task().id().value(), architecture.task().id().value());
        assertThat(knowledge.search("repo-main", run.runId(), null, 10)).hasSize(3);
        assertThat(knowledge.search("repo-main", null, "focused integration test", 10))
                .extracting(document -> document.id()).contains(architecture.task().id().value());

        var implementation = claims.claimNext(coder).orElseThrow();
        assertThat(implementation.task().type()).isEqualTo(TaskType.IMPLEMENTATION);
        assertThat(implementation.inputs()).hasSize(3);
        assertThat(implementation.inputs()).allMatch(input -> input.completionSummary() != null);
        executions.start(implementation.task().id(), coder, implementation.lease().token());
        var implementationCompletion = executions.complete(
                implementation.task().id(), coder, implementation.lease().token(), "thread-r8-impl",
                new WorkspaceResult(RESULT, List.of("src/main/java/HealthController.java", "src/test/java/HealthIT.java"),
                        "git:origin:" + implementation.workspace().branchName()));
        assertThat(implementationCompletion.generatedTaskIds()).hasSize(2);

        var review = claims.claimNext(reviewer).orElseThrow();
        executions.start(review.task().id(), reviewer, review.lease().token());
        executions.complete(review.task().id(), reviewer, review.lease().token(), "thread-r8-review",
                new WorkspaceResult(RESULT, List.of()),
                new CompletionReport(CompletionVerdict.PASS, "Review passed.\nAIW_VERDICT: PASS"));

        var verification = claims.claimNext(verifier).orElseThrow();
        executions.start(verification.task().id(), verifier, verification.lease().token());
        executions.complete(verification.task().id(), verifier, verification.lease().token(), "thread-r8-verify",
                new WorkspaceResult(RESULT, List.of()),
                new CompletionReport(CompletionVerdict.PASS, "Verification passed.\nAIW_VERDICT: PASS"));

        var gate = gates.find(implementation.task().id()).orElseThrow();
        assertThat(gate.status()).isEqualTo(EngineeringGateStatus.PASSED);
        assertThat(gate.resultRevision()).isEqualTo(RESULT);
        assertThat(approvals.findBySourceTask(implementation.task().id()).orElseThrow().status())
                .isEqualTo(ApprovalStatus.PENDING);

        assertThat(tasks.get(run.requirementTaskId()).state()).isEqualTo(TaskState.COMPLETED);
    }

    private void completePlanningStage(me.asu.aidev.work.domain.TaskId taskId, WorkerId workerId,
                                       me.asu.aidev.execution.domain.LeaseToken token, String summary) {
        executions.start(taskId, workerId, token);
        executions.complete(taskId, workerId, token, "thread-" + taskId.value(),
                new WorkspaceResult(BASE, List.of()), new CompletionReport(CompletionVerdict.PASS, summary));
    }

    private WorkerId register(String id, Set<String> capabilities) {
        WorkerId workerId = new WorkerId(id);
        workers.register(new RegisterWorkerCommand(workerId, "test", capabilities, 1));
        return workerId;
    }
}
