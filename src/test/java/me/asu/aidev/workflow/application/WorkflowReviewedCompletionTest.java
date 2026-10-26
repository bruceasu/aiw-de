package me.asu.aidev.workflow.application;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import me.asu.aidev.authority.ManagedPlanAdmission;
import me.asu.aidev.execution.application.TaskCompletionContext;
import me.asu.aidev.execution.domain.CompletionReport;
import me.asu.aidev.execution.domain.CompletionVerdict;
import me.asu.aidev.execution.port.ExecutionAttemptRepository;
import me.asu.aidev.work.application.WorkTaskService;
import me.asu.aidev.work.domain.*;
import me.asu.aidev.work.port.WorkTaskRepository;
import me.asu.aidev.workflow.domain.*;
import me.asu.aidev.workflow.jdbc.JdbcWorkflowControlRepository;
import me.asu.aidev.workflow.jdbc.JdbcWorkflowHumanOutcomeRepository;
import me.asu.aidev.workflow.jdbc.JdbcWorkflowPlanArchiveRepository;
import me.asu.aidev.workflow.port.WorkflowPlanRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WorkflowReviewedCompletionTest {
    private final WorkflowPlanRepository repository = mock(WorkflowPlanRepository.class);
    private final WorkTaskRepository tasks = mock(WorkTaskRepository.class);
    private final ExecutionAttemptRepository attempts = mock(ExecutionAttemptRepository.class);
    private final JdbcWorkflowControlRepository controls = mock(JdbcWorkflowControlRepository.class);
    private final JdbcWorkflowHumanOutcomeRepository outcomes = mock(JdbcWorkflowHumanOutcomeRepository.class);
    private final JdbcWorkflowPlanArchiveRepository archives = mock(JdbcWorkflowPlanArchiveRepository.class);
    private final WorkflowPlanService service = new WorkflowPlanService(repository, mock(WorkTaskService.class),
            tasks, attempts, controls, outcomes, archives);
    private final Map<TaskId, WorkTask> current = new LinkedHashMap<>();
    private final Map<TaskId, CompletionReport> reports = new LinkedHashMap<>();
    private WorkflowPlan plan;
    private TaskId review;

    @BeforeEach
    void setup() {
        var spec = new WorkflowPlanSpec("Accepted work", WorkflowPlanSpec.Source.MANAGER, List.of(
                new WorkflowPlanSpec.Step("analyze", Set.of("requirement-analysis"), Set.of(),
                        "software-requirements-analyst", "Analyze", null, null, Set.of()),
                new WorkflowPlanSpec.Step("code", Set.of("coding"), Set.of(),
                        "coding-work-package-executor", "Implement", null, null, Set.of("analyze"))));
        plan = new WorkflowPlan("FP-completion", WorkflowPlan.State.CONFIRMED, 7, spec,
                "operator", "operator", "", Instant.EPOCH, Instant.EPOCH);
        for (int step = 0; step < 2; step++) {
            add(WorkflowPlanService.executionId(plan, step, 1), TaskState.COMPLETED, CompletionVerdict.PASS);
            add(WorkflowPlanService.reviewId(plan, step, 1), TaskState.COMPLETED, CompletionVerdict.PASS);
        }
        review = WorkflowPlanService.reviewId(plan, 1, 1);
        when(repository.find(plan.id())).thenAnswer(call -> Optional.of(plan));
        when(tasks.findBySubject(WorkflowPlanService.SUBJECT, plan.id()))
                .thenAnswer(call -> List.copyOf(current.values()));
        when(tasks.find(any())).thenAnswer(call -> Optional.ofNullable(current.get(call.getArgument(0))));
        when(attempts.findCompletionReport(any()))
                .thenAnswer(call -> Optional.ofNullable(reports.get(call.getArgument(0))));
        when(repository.transition(anyString(), anyLong(), any(), any(), anyString(), anyString(), any()))
                .thenAnswer(call -> {
                    if (plan.version() != call.getArgument(1, Long.class)) return false;
                    plan = new WorkflowPlan(plan.id(), call.getArgument(3), plan.version() + 1, plan.spec(),
                            plan.createdBy(), call.getArgument(4), call.getArgument(5), plan.createdAt(), call.getArgument(6));
                    return true;
                });
    }

    private void add(TaskId id, TaskState state, CompletionVerdict verdict) {
        current.put(id, new WorkTask(id, TaskType.EXTERNAL_WORK, state, WorkflowPlanService.SUBJECT, plan.id(),
                Set.of("workflow-review"), "workflow-stage-reviewer", "Review", null, null,
                TaskPriority.NORMAL, 1, Instant.EPOCH, Instant.EPOCH));
        reports.put(id, new CompletionReport(verdict, verdict.name()));
    }

    private void assertUnchanged() {
        assertThat(plan.state()).isEqualTo(WorkflowPlan.State.CONFIRMED);
        verify(repository, never()).transition(anyString(), anyLong(), any(), any(), anyString(), anyString(), any());
    }

    @Test
    void finalReviewCompletionPersistsPlanStateAndIsIdempotent() {
        var handler = new WorkflowStepCompletedHandler(service, mock(WorkflowRepairDecisionHandler.class));
        ReflectionTestUtils.setField(handler, "personal", mock(ManagedPlanAdmission.class));
        var completion = new TaskCompletionContext(current.get(review), null, reports.get(review));
        assertThat(handler.onCompleted(completion)).isEmpty();
        assertThat(plan.state()).isEqualTo(WorkflowPlan.State.COMPLETED);
        assertThat(plan.version()).isEqualTo(8);
        assertThat(plan.reason()).contains(review.value());
        assertThat(handler.onCompleted(completion)).isEmpty();
        verify(repository, times(1)).transition(eq(plan.id()), eq(7L), eq(WorkflowPlan.State.CONFIRMED),
                eq(WorkflowPlan.State.COMPLETED), eq("workflow"), anyString(), any());
    }

    @Test
    void repairVerdictDoesNotCompletePlan() {
        reports.put(review, new CompletionReport(CompletionVerdict.REPAIR_REQUIRED, "Repair required"));
        service.completeReviewed(plan.id(), review);
        assertUnchanged();
    }

    @Test
    void unpersistedPassClaimDoesNotCompletePlan() {
        reports.remove(review);
        service.completeReviewed(plan.id(), review);
        assertUnchanged();
    }

    @Test
    void nonfinalReviewDoesNotCompletePlan() {
        service.completeReviewed(plan.id(), WorkflowPlanService.reviewId(plan, 0, 1));
        assertUnchanged();
    }

    @Test
    void activeExecutionDoesNotCompletePlan() {
        add(WorkflowPlanService.executionId(plan, 1, 1), TaskState.RUNNING, CompletionVerdict.PASS);
        service.completeReviewed(plan.id(), review);
        assertUnchanged();
    }

    @Test
    void executionRequiringRepairCannotBeCompletedByAReviewPass() {
        reports.put(WorkflowPlanService.executionId(plan, 1, 1),
                new CompletionReport(CompletionVerdict.REPAIR_REQUIRED, "No file changes"));
        service.completeReviewed(plan.id(), review);
        assertUnchanged();
    }

    @Test
    void supersededReviewCannotCompletePlan() {
        add(new TaskId(review.value() + "-resume-1"), TaskState.COMPLETED, CompletionVerdict.REPAIR_REQUIRED);
        service.completeReviewed(plan.id(), review);
        assertUnchanged();
    }

    @Test
    void olderRoundCannotCompletePlan() {
        add(WorkflowPlanService.executionId(plan, 1, 2), TaskState.COMPLETED, CompletionVerdict.PASS);
        add(WorkflowPlanService.reviewId(plan, 1, 2), TaskState.COMPLETED, CompletionVerdict.REPAIR_REQUIRED);
        service.completeReviewed(plan.id(), review);
        assertUnchanged();
    }

    @Test
    void missingUpstreamAcceptanceBlocksCompletion() {
        reports.remove(WorkflowPlanService.reviewId(plan, 0, 1));
        assertThatThrownBy(() -> service.completeReviewed(plan.id(), review))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("no accepted review");
        assertUnchanged();
    }

    @Test
    void archivedPlanDoesNotComplete() {
        when(archives.archived(plan.id())).thenReturn(true);
        service.completeReviewed(plan.id(), review);
        assertUnchanged();
    }

    @Test
    void pendingStopDoesNotComplete() {
        when(controls.pending(plan.id())).thenReturn(Optional.of(mock(JdbcWorkflowControlRepository.Control.class)));
        service.completeReviewed(plan.id(), review);
        assertUnchanged();
    }

    @Test
    void concurrentPlanChangeDoesNotGetOverwritten() {
        when(repository.transition(anyString(), anyLong(), any(), any(), anyString(), anyString(), any())).thenReturn(false);
        assertThatThrownBy(() -> service.completeReviewed(plan.id(), review))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Plan changed");
        assertThat(plan.state()).isEqualTo(WorkflowPlan.State.CONFIRMED);
    }
}
