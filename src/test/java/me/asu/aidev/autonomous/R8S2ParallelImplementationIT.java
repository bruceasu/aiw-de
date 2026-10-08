package me.asu.aidev.autonomous;

import me.asu.aidev.autonomous.application.AutonomousRequirementService;
import me.asu.aidev.autonomous.application.StartAutonomousRequirementCommand;
import me.asu.aidev.execution.application.TaskClaimService;
import me.asu.aidev.execution.application.TaskExecutionService;
import me.asu.aidev.execution.domain.CompletionReport;
import me.asu.aidev.execution.domain.CompletionVerdict;
import me.asu.aidev.execution.domain.WorkspaceResult;
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
class R8S2ParallelImplementationIT {
    private static final String BASE = "2222222222222222222222222222222222222222";
    private static final Path DB;
    static {
        try {
            DB = Files.createTempFile("r8-s2-parallel-", ".db");
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

    @Test
    void planningManifestMaterializesIndependentImplementationTasksThatDifferentWorkersCanClaimInParallel() {
        autonomous.start(new StartAutonomousRequirementCommand(
                "Add a health endpoint, metrics wiring, and regression tests.",
                "repo-main", BASE, Set.of("java", "maven"), TaskPriority.HIGH));

        WorkerId analyst = register("r8s2-analyst", Set.of("requirement-analysis", "java", "maven", "repo:repo-main"));
        WorkerId architect = register("r8s2-architect", Set.of("solution-architecture", "java", "maven", "repo:repo-main"));
        WorkerId planner = register("r8s2-planner", Set.of("implementation-planning", "java", "maven", "repo:repo-main"));
        WorkerId coder1 = register("r8s2-coder-1", Set.of("coding", "java", "maven", "repo:repo-main"));
        WorkerId coder2 = register("r8s2-coder-2", Set.of("coding", "java", "maven", "repo:repo-main"));
        WorkerId coder3 = register("r8s2-coder-3", Set.of("coding", "java", "maven", "repo:repo-main"));

        var requirement = claims.claimNext(analyst).orElseThrow();
        completePlanningStage(requirement.task().id(), analyst, requirement.lease().token(),
                "Requirement specification with acceptance criteria.");

        var architecture = claims.claimNext(architect).orElseThrow();
        completePlanningStage(architecture.task().id(), architect, architecture.lease().token(),
                "Architecture permits endpoint, metrics, and tests to be implemented independently from the same base.");

        var planning = claims.claimNext(planner).orElseThrow();
        String manifest = """
                Plan: three independent work items sharing the same immutable base.

                AIW_IMPLEMENTATION_WORK_ITEMS_V1
                AIW_WORK_ITEM_BEGIN endpoint
                AIW_CAPABILITIES java,maven
                Implement the health endpoint and application wiring.
                AIW_WORK_ITEM_END
                AIW_WORK_ITEM_BEGIN metrics
                AIW_CAPABILITIES java,maven
                Implement metrics registration without touching endpoint code.
                AIW_WORK_ITEM_END
                AIW_WORK_ITEM_BEGIN tests
                AIW_CAPABILITIES java,maven
                Add regression tests in isolated test files.
                AIW_WORK_ITEM_END
                AIW_IMPLEMENTATION_WORK_ITEMS_END
                """;
        executions.start(planning.task().id(), planner, planning.lease().token());
        var planningResult = executions.complete(planning.task().id(), planner, planning.lease().token(),
                "thread-r8s2-plan", new WorkspaceResult(BASE, List.of()),
                new CompletionReport(CompletionVerdict.PASS, manifest));

        assertThat(planningResult.generatedTaskIds()).hasSize(3);
        assertThat(planningResult.generatedTaskIds()).doesNotHaveDuplicates();
        assertThat(planningResult.generatedTaskIds())
                .allMatch(id -> tasks.get(id).state() == TaskState.READY)
                .allMatch(id -> tasks.get(id).type() == TaskType.IMPLEMENTATION)
                .allMatch(id -> BASE.equals(tasks.get(id).baseRevision()));

        var first = claims.claimNext(coder1).orElseThrow();
        var second = claims.claimNext(coder2).orElseThrow();
        var third = claims.claimNext(coder3).orElseThrow();

        assertThat(Set.of(first.task().id(), second.task().id(), third.task().id())).hasSize(3);
        assertThat(List.of(first, second, third)).allSatisfy(claimed -> {
            assertThat(claimed.task().type()).isEqualTo(TaskType.IMPLEMENTATION);
            assertThat(claimed.task().baseRevision()).isEqualTo(BASE);
            assertThat(claimed.workspace()).isNotNull();
            assertThat(claimed.inputs()).hasSize(3);
            assertThat(claimed.inputs()).allMatch(input -> input.completionSummary() != null);
        });
        assertThat(Set.of(first.workspace().branchName(), second.workspace().branchName(), third.workspace().branchName())).hasSize(3);
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
