package me.asu.aidev.worker.protocol.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import me.asu.aidev.ai.application.AiProfileCatalog;
import me.asu.aidev.execution.application.TaskClaimService;
import me.asu.aidev.execution.application.TaskExecutionService;
import me.asu.aidev.execution.application.TaskHandoffs;
import me.asu.aidev.execution.domain.*;
import me.asu.aidev.execution.port.ExecutionAttemptRepository;
import me.asu.aidev.work.application.TaskCompletionResult;
import me.asu.aidev.work.domain.*;
import me.asu.aidev.worker.application.WorkerService;
import me.asu.aidev.worker.domain.*;
import me.asu.aidev.workflow.application.PlanLocations;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PlanLocationProtocolTest {
    private final WorkerService workers = mock(WorkerService.class);
    private final TaskClaimService claims = mock(TaskClaimService.class);
    private final TaskExecutionService executions = mock(TaskExecutionService.class);
    private final ExecutionAttemptRepository attempts = mock(ExecutionAttemptRepository.class);
    private final PlanLocations locations = mock(PlanLocations.class);
    private final TaskHandoffs handoffs = mock(TaskHandoffs.class);
    private final WorkerId worker = new WorkerId("worker-1");
    private final TaskId taskId = new TaskId("task-1");
    private final LeaseToken lease = new LeaseToken("lease-1");
    private final TaskHandoffs.Reference reference = new TaskHandoffs.Reference(
            ".ai/handoffs/" + "a".repeat(64) + "/" + "b".repeat(64) + "/manifest.json", "c".repeat(64));
    private WorkerProtocolV1Controller controller;

    @BeforeEach void setup() {
        controller = new WorkerProtocolV1Controller(workers, claims, executions, attempts, mock(AiProfileCatalog.class));
        ReflectionTestUtils.setField(controller, "locations", locations);
        ReflectionTestUtils.setField(controller, "handoffs", handoffs);
    }

    @Test void claimCarriesLocationAndPinnedUpstreamHandoffWithoutWorkspaceAssignment() {
        var now = Instant.EPOCH;
        var task = new WorkTask(taskId, TaskType.EXTERNAL_WORK, TaskState.CLAIMED,
                "flexible-plan", "plan-1", Set.of("coding"), "coding-work-package-executor",
                "Implement the task", null, null, TaskPriority.NORMAL, 0, now, now);
        var input = new TaskInput(new TaskId("source-task"), TaskType.EXTERNAL_WORK, null, null,
                null, null, List.of(), CompletionVerdict.PASS, "Source result", "source-attempt");
        var claim = new ClaimedTask(task, new TaskLease(taskId, worker, lease, now, now.plusSeconds(60), now),
                new ExecutionAttemptId("attempt-1"), 1, null, List.of(input));
        var upstream = new TaskHandoffs.Input("shared-location", reference.manifest(), reference.sha256());
        when(claims.claimNext(worker)).thenReturn(Optional.of(claim));
        when(workers.get(worker)).thenReturn(new Worker(worker, "openai-agents:coder", Set.of("coding"),
                1, 1, WorkerStatus.ACTIVE, now, now, now));
        when(attempts.findPreviousAiAttempt(taskId, 1)).thenReturn(Optional.empty());
        when(locations.forTask(task)).thenReturn("not-yet-existing-location");
        when(handoffs.input("source-attempt")).thenReturn(upstream);

        var response = controller.claim(new WorkerProtocolV1Controller.ClaimRequest(worker.value()));

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        var body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.planId()).isEqualTo("plan-1");
        assertThat(body.location()).isEqualTo("not-yet-existing-location");
        assertThat(body.handoffProtocol()).isEqualTo("aiw.handoff.v1");
        assertThat(body.inputs().getFirst().attemptId()).isEqualTo("source-attempt");
        assertThat(body.inputs().getFirst().handoff()).isEqualTo(upstream);
        assertThat(WorkerProtocolV1Controller.ClaimedTaskResponse.class.getRecordComponents())
                .extracting(java.lang.reflect.RecordComponent::getName).doesNotContain("workspace", "outputStore");
    }

    @Test void nonGitCompletionRecordsHandoffBeforeCompletingTask() throws Exception {
        var report = new CompletionReport(CompletionVerdict.PASS, "Implemented without Git");
        when(executions.complete(eq(taskId), eq(worker), eq(lease), eq("thread-1"), isNull(), eq(report)))
                .thenReturn(new TaskCompletionResult(taskId, List.of(), List.of()));
        var request = new WorkerProtocolV1Controller.CompleteTaskRequest(worker.value(), lease.value(), "thread-1",
                null, new WorkerProtocolV1Controller.CompletionReportRequest("PASS", report.summary()), reference);

        var response = controller.complete(taskId.value(), request);

        assertThat(response.taskId()).isEqualTo(taskId.value());
        var ordered = inOrder(handoffs, executions);
        ordered.verify(handoffs).record(taskId.value(), worker.value(), lease.value(), reference);
        ordered.verify(executions).complete(taskId, worker, lease, "thread-1", null, report);
        var payload = new ObjectMapper().readTree(new ObjectMapper().writeValueAsString(request));
        assertThat(payload.path("gitResult").isNull()).isTrue();
        assertThat(payload.path("handoff").path("manifest").asText()).isEqualTo(reference.manifest());
        assertThat(payload.has("workspaceResult")).isFalse();
    }

    @Test void missingReportCannotCompleteEvenWithHandoff() {
        var request = new WorkerProtocolV1Controller.CompleteTaskRequest(worker.value(), lease.value(), null,
                null, null, reference);

        assertThatThrownBy(() -> controller.complete(taskId.value(), request))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("report required");
        verifyNoInteractions(handoffs, executions);
    }

    @Test void rejectedHandoffDoesNotCompleteTask() {
        doThrow(new IllegalStateException("Running owned lease required"))
                .when(handoffs).record(taskId.value(), worker.value(), lease.value(), reference);
        var request = new WorkerProtocolV1Controller.CompleteTaskRequest(worker.value(), lease.value(), null,
                null, new WorkerProtocolV1Controller.CompletionReportRequest("PASS", "Done"), reference);

        assertThatThrownBy(() -> controller.complete(taskId.value(), request))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("owned lease");
        verify(executions, never()).complete(any(TaskId.class), any(WorkerId.class), any(LeaseToken.class),
                any(), any(), any());
    }
}
