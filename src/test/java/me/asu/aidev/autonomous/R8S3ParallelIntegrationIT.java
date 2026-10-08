package me.asu.aidev.autonomous;

import me.asu.aidev.autonomous.application.AutonomousRequirementService;
import me.asu.aidev.autonomous.application.AutonomousRunService;
import me.asu.aidev.autonomous.domain.AutonomousRunStatus;
import me.asu.aidev.autonomous.application.StartAutonomousRequirementCommand;
import me.asu.aidev.development.application.EngineeringGateService;
import me.asu.aidev.development.domain.EngineeringGateStatus;
import me.asu.aidev.execution.application.TaskClaimService;
import me.asu.aidev.execution.application.TaskExecutionService;
import me.asu.aidev.execution.domain.CompletionReport;
import me.asu.aidev.execution.domain.CompletionVerdict;
import me.asu.aidev.execution.domain.WorkspaceResult;
import me.asu.aidev.work.application.WorkTaskService;
import me.asu.aidev.work.domain.TaskPriority;
import me.asu.aidev.work.domain.TaskType;
import me.asu.aidev.worker.application.RegisterWorkerCommand;
import me.asu.aidev.worker.application.WorkerService;
import me.asu.aidev.worker.domain.WorkerId;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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
class R8S3ParallelIntegrationIT {
    private static final String BASE = "1111111111111111111111111111111111111111";
    private static final String A = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String B = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
    private static final String C = "cccccccccccccccccccccccccccccccccccccccc";
    private static final String INTEGRATED = "dddddddddddddddddddddddddddddddddddddddd";
    private static final Path DB;
    static {
        try {
            DB = Files.createTempFile("r8-s3-integration-", ".db");
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
    @Autowired WorkTaskService tasks;
    @Autowired WorkerService workers;
    @Autowired TaskClaimService claims;
    @Autowired TaskExecutionService executions;
    @Autowired EngineeringGateService gates;
    @Autowired AutonomousRunService runs;

    @Test
    void parallelBranchesJoinIntoOneIntegratedResultBeforeFinalGate() {
        autonomous.start(new StartAutonomousRequirementCommand(
                "Implement three independent pieces and integrate them into one release candidate.",
                "repo-main", BASE, Set.of("java", "maven"), TaskPriority.HIGH));

        WorkerId analyst = register("r8s3-analyst", Set.of("requirement-analysis", "java", "maven", "repo:repo-main"));
        WorkerId architect = register("r8s3-architect", Set.of("solution-architecture", "java", "maven", "repo:repo-main"));
        WorkerId planner = register("r8s3-planner", Set.of("implementation-planning", "java", "maven", "repo:repo-main"));
        WorkerId coder1 = register("r8s3-coder-1", Set.of("coding", "java", "maven", "repo:repo-main"));
        WorkerId coder2 = register("r8s3-coder-2", Set.of("coding", "java", "maven", "repo:repo-main"));
        WorkerId coder3 = register("r8s3-coder-3", Set.of("coding", "java", "maven", "repo:repo-main"));
        WorkerId integrator = register("r8s3-integrator", Set.of("integration", "java", "maven", "repo:repo-main"));
        WorkerId reviewer = register("r8s3-reviewer", Set.of("code-review", "java", "maven", "repo:repo-main"));
        WorkerId verifier = register("r8s3-verifier", Set.of("integration-verification", "java", "maven", "repo:repo-main"));

        var requirement = claims.claimNext(analyst).orElseThrow();
        completePlanningStage(requirement.task().id(), analyst, requirement.lease().token(),
                "Requirement specification with acceptance criteria.");
        var architecture = claims.claimNext(architect).orElseThrow();
        completePlanningStage(architecture.task().id(), architect, architecture.lease().token(),
                "Architecture confirms three branches can start from the same immutable base and must later be integrated.");

        var planning = claims.claimNext(planner).orElseThrow();
        String manifest = """
                AIW_IMPLEMENTATION_WORK_ITEMS_V1
                AIW_WORK_ITEM_BEGIN alpha
                AIW_CAPABILITIES java,maven
                Implement alpha independently.
                AIW_WORK_ITEM_END
                AIW_WORK_ITEM_BEGIN beta
                AIW_CAPABILITIES java,maven
                Implement beta independently.
                AIW_WORK_ITEM_END
                AIW_WORK_ITEM_BEGIN gamma
                AIW_CAPABILITIES java,maven
                Implement gamma independently.
                AIW_WORK_ITEM_END
                AIW_IMPLEMENTATION_WORK_ITEMS_END
                """;
        executions.start(planning.task().id(), planner, planning.lease().token());
        executions.complete(planning.task().id(), planner, planning.lease().token(), "thread-plan",
                new WorkspaceResult(BASE, List.of()), new CompletionReport(CompletionVerdict.PASS, manifest));

        var implementations = List.of(
                claims.claimNext(coder1).orElseThrow(),
                claims.claimNext(coder2).orElseThrow(),
                claims.claimNext(coder3).orElseThrow());
        assertThat(implementations).allSatisfy(claimed -> assertThat(claimed.task().type()).isEqualTo(TaskType.IMPLEMENTATION));

        List<String> revisions = List.of(A, B, C);
        List<me.asu.aidev.work.domain.TaskId> branchIds = new ArrayList<>();
        me.asu.aidev.work.application.TaskCompletionResult last = null;
        for (int i = 0; i < implementations.size(); i++) {
            var claimed = implementations.get(i);
            WorkerId worker = List.of(coder1, coder2, coder3).get(i);
            branchIds.add(claimed.task().id());
            executions.start(claimed.task().id(), worker, claimed.lease().token());
            last = executions.complete(claimed.task().id(), worker, claimed.lease().token(), "thread-impl-" + i,
                    new WorkspaceResult(revisions.get(i), List.of("src/part" + i + ".java"),
                            "git:origin:" + claimed.workspace().branchName()));
            if (i < implementations.size() - 1) {
                assertThat(last.generatedTaskIds()).isEmpty();
            }
        }

        assertThat(last).isNotNull();
        assertThat(last.generatedTaskIds()).hasSize(1);
        var integrationId = last.generatedTaskIds().getFirst();
        assertThat(tasks.get(integrationId).type()).isEqualTo(TaskType.INTEGRATION);
        assertThat(tasks.get(integrationId).baseRevision()).isEqualTo(BASE);
        branchIds.forEach(id ->
                assertThat(tasks.get(id).state()).isEqualTo(me.asu.aidev.work.domain.TaskState.COMPLETED));

        var integration = claims.claimNext(integrator).orElseThrow();
        assertThat(integration.task().id()).isEqualTo(integrationId);
        assertThat(integration.inputs()).hasSize(3);
        assertThat(integration.inputs()).extracting(input -> input.resultRevision())
                .containsExactlyInAnyOrder(A, B, C);
        assertThat(integration.inputs()).allMatch(input -> input.publicationRef() != null);

        executions.start(integrationId, integrator, integration.lease().token());
        var integrationCompletion = executions.complete(
                integrationId, integrator, integration.lease().token(), "thread-integration",
                new WorkspaceResult(INTEGRATED, List.of("src/part0.java", "src/part1.java", "src/part2.java"),
                        "git:origin:" + integration.workspace().branchName()));

        assertThat(integrationCompletion.generatedTaskIds()).hasSize(2);
        var reviewId = integrationCompletion.generatedTaskIds().stream()
                .filter(id -> tasks.get(id).type() == TaskType.CODE_REVIEW).findFirst().orElseThrow();
        var verificationId = integrationCompletion.generatedTaskIds().stream()
                .filter(id -> tasks.get(id).type() == TaskType.INTEGRATION_VERIFICATION).findFirst().orElseThrow();

        var review = claims.claimNext(reviewer).orElseThrow();
        executions.start(reviewId, reviewer, review.lease().token());
        executions.complete(reviewId, reviewer, review.lease().token(), "thread-review",
                new WorkspaceResult(INTEGRATED, List.of()),
                new CompletionReport(CompletionVerdict.PASS, "Integrated review passed.\nAIW_VERDICT: PASS"));

        var verification = claims.claimNext(verifier).orElseThrow();
        executions.start(verificationId, verifier, verification.lease().token());
        executions.complete(verificationId, verifier, verification.lease().token(), "thread-integration-verify",
                new WorkspaceResult(INTEGRATED, List.of()),
                new CompletionReport(CompletionVerdict.PASS, "Integrated verification passed.\nAIW_VERDICT: PASS"));

        var gate = gates.find(integrationId).orElseThrow();
        assertThat(gate.status()).isEqualTo(EngineeringGateStatus.PASSED);
        assertThat(gate.sourceTaskType()).isEqualTo(TaskType.INTEGRATION);
        assertThat(gate.resultRevision()).isEqualTo(INTEGRATED);
        assertThat(gate.verificationTaskId()).isEqualTo(verificationId);
        assertThat(runs.find(integration.task().subjectId()).orElseThrow().status())
                .isEqualTo(AutonomousRunStatus.ENGINEERING_PASSED);
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
