package me.asu.aidev.workflow.protocol.http;

import me.asu.aidev.execution.application.TaskClaimService;
import me.asu.aidev.execution.application.TaskExecutionService;
import me.asu.aidev.testsupport.DatabaseTestCleaner;
import me.asu.aidev.work.application.WorkTaskService;
import me.asu.aidev.work.domain.TaskId;
import me.asu.aidev.work.domain.TaskState;
import me.asu.aidev.worker.application.RegisterWorkerCommand;
import me.asu.aidev.worker.application.WorkerService;
import me.asu.aidev.worker.domain.WorkerId;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {"aidev.external-workflow.enabled=true", "AIW_EXTERNAL_WORKFLOW_TOKEN=test-token",
        "AIW_EXTERNAL_WORKFLOW_REQUEST_TOKEN=request-token"})
@ActiveProfiles("sqlite")
class ExternalWorkflowIntakeIT {
    private static final Path DB;
    static {
        try {
            DB = Files.createTempFile("external-workflow-intake-", ".db");
            Files.deleteIfExists(DB);
        } catch (Exception error) {
            throw new ExceptionInInitializerError(error);
        }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + DB.toAbsolutePath());
    }

    @Autowired ExternalWorkflowController controller;
    @Autowired WorkTaskService tasks;
    @Autowired WorkerService workers;
    @Autowired TaskClaimService claims;
    @Autowired TaskExecutionService executions;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void clearDatabase() {
        DatabaseTestCleaner.clear(jdbc);
    }

    @Test
    void controlPlaneAcceptsRequestBeforeAcceptorProposesSteps() {
        String workflow = "demo-java-deploy@v1";
        var run = controller.request(workflow, "request-token",
                new ExternalWorkflowController.IntakeRequest("demo-deploy-acceptor", "{\"request\":1}"));
        TaskId intake = new TaskId(run.runId() + "~intake");
        TaskId pack = new TaskId(run.runId() + "~package");
        TaskId upload = new TaskId(run.runId() + "~upload");
        assertThat(tasks.get(intake).state()).isEqualTo(TaskState.READY);

        WorkerId worker = new WorkerId("demo-deploy-acceptor");
        workers.register(new RegisterWorkerCommand(worker, "demo-typescript", Set.of("demo-deploy-acceptor"), 1));
        var claimed = claims.claimNext(worker).orElseThrow();
        assertThat(claimed.task().id()).isEqualTo(intake);
        executions.start(intake, worker, claimed.lease().token());

        var steps = new ExternalWorkflowController.StartRequest(List.of(
                new ExternalWorkflowController.Step("package", "demo-deploy-package", "{}", "demo-app",
                        "0123456789abcdef0123456789abcdef01234567", List.of()),
                new ExternalWorkflowController.Step("upload", "demo-deploy-upload", "{}", null, null,
                        List.of("package"))));
        controller.addSteps(workflow, run.runId(), "test-token", steps);
        controller.addSteps(workflow, run.runId(), "test-token", steps);
        var changedPlan = new ExternalWorkflowController.StartRequest(List.of(
                steps.steps().get(0),
                new ExternalWorkflowController.Step("upload", "demo-deploy-upload", "{}", null, null,
                        List.of())));
        assertThatThrownBy(() -> controller.addSteps(workflow, run.runId(), "test-token", changedPlan))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("incompatible dependencies");
        assertThat(tasks.get(pack).state()).isEqualTo(TaskState.BLOCKED);
        assertThat(tasks.get(upload).state()).isEqualTo(TaskState.BLOCKED);

        executions.complete(intake, worker, claimed.lease().token());
        assertThat(tasks.get(pack).state()).isEqualTo(TaskState.READY);
        assertThat(tasks.get(upload).state()).isEqualTo(TaskState.BLOCKED);
    }
}
