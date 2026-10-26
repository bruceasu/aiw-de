package me.asu.aidev.workflow.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import me.asu.aidev.execution.application.TaskCompletionContext;
import me.asu.aidev.execution.domain.CompletionReport;
import me.asu.aidev.execution.domain.CompletionVerdict;
import me.asu.aidev.execution.domain.TaskInput;
import me.asu.aidev.execution.port.ExecutionAttemptRepository;
import me.asu.aidev.work.application.CreateWorkTaskCommand;
import me.asu.aidev.work.application.WorkTaskService;
import me.asu.aidev.work.domain.TaskId;
import me.asu.aidev.work.domain.TaskPriority;
import me.asu.aidev.work.domain.TaskState;
import me.asu.aidev.work.domain.TaskType;
import me.asu.aidev.work.domain.WorkTask;
import me.asu.aidev.work.port.WorkTaskRepository;
import me.asu.aidev.workflow.domain.WorkflowPlan;
import me.asu.aidev.workflow.domain.WorkflowPlanSpec;
import me.asu.aidev.workflow.jdbc.JdbcWorkflowControlRepository;
import me.asu.aidev.workflow.jdbc.JdbcWorkflowHumanOutcomeRepository;
import me.asu.aidev.workflow.jdbc.JdbcWorkflowPlanArchiveRepository;
import me.asu.aidev.workflow.port.WorkflowPlanRepository;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WorkflowReviewRecoveryTest {
    private final WorkflowPlanRepository repository = mock(WorkflowPlanRepository.class);
    private final WorkTaskRepository tasks = mock(WorkTaskRepository.class);
    private final WorkTaskService taskService = mock(WorkTaskService.class);
    private final ExecutionAttemptRepository attempts = mock(ExecutionAttemptRepository.class);
    private final JdbcWorkflowControlRepository controls = mock(JdbcWorkflowControlRepository.class);
    private final JdbcWorkflowHumanOutcomeRepository outcomes = mock(JdbcWorkflowHumanOutcomeRepository.class);
    private final JdbcWorkflowPlanArchiveRepository archives = mock(JdbcWorkflowPlanArchiveRepository.class);
    private final WorkflowPlanService service = new WorkflowPlanService(repository, taskService, tasks, attempts, controls, outcomes, archives);
    private final Map<TaskId, WorkTask> current = new LinkedHashMap<>();
    private final Map<TaskId, CompletionReport> reports = new LinkedHashMap<>();
    private final List<CreateWorkTaskCommand> created = new ArrayList<>();
    private WorkflowPlan plan;

    private void setup() {
        var spec = new WorkflowPlanSpec("Implement the accepted plan", WorkflowPlanSpec.Source.MANAGER, List.of(
                new WorkflowPlanSpec.Step("plan", Set.of("implementation-planning"), Set.of(), "implementation-planner", "Plan", null, null, Set.of()),
                new WorkflowPlanSpec.Step("code", Set.of("coding"), Set.of(), "coding-work-package-executor", "Implement", "test/root", null, Set.of("plan")),
                new WorkflowPlanSpec.Step("handoff", Set.of("requirement-analysis"), Set.of(), "software-requirements-analyst", "Summarize", null, null, Set.of("code"))));
        plan = new WorkflowPlan("FP-recovery", WorkflowPlan.State.CONFIRMED, 7, spec, "test", "test", "", Instant.EPOCH, Instant.EPOCH);
        when(repository.find(plan.id())).thenAnswer(invocation -> Optional.of(plan));
        when(tasks.findBySubject(WorkflowPlanService.SUBJECT, plan.id())).thenAnswer(invocation -> List.copyOf(current.values()));
        when(tasks.lock(any())).thenReturn(true);
        when(tasks.find(any())).thenAnswer(invocation -> Optional.ofNullable(current.get(invocation.getArgument(0))));
        when(attempts.findCompletionReport(any())).thenAnswer(invocation -> Optional.ofNullable(reports.get(invocation.getArgument(0))));
        when(attempts.findCompletedInput(any())).thenAnswer(invocation -> {
            TaskId id = invocation.getArgument(0);
            return Optional.of(new TaskInput(id, TaskType.EXTERNAL_WORK, "test/root", null, null, null,
                    List.of(), CompletionVerdict.PASS, "Authoritative output"));
        });
        when(repository.transition(anyString(), anyLong(), any(), any(), anyString(), anyString(), any())).thenAnswer(invocation -> {
            if (plan.version() != (long) invocation.getArgument(1)) return false;
            plan = new WorkflowPlan(plan.id(), plan.state(), plan.version() + 1, plan.spec(), "test", "operator", "Recovery", Instant.EPOCH, Instant.now());
            return true;
        });
        when(taskService.createIfAbsent(any())).thenAnswer(invocation -> {
            CreateWorkTaskCommand command = invocation.getArgument(0);
            created.add(command);
            WorkTask task = new WorkTask(command.id(), TaskType.EXTERNAL_WORK, TaskState.READY, command.subjectType(), command.subjectId(),
                    command.requiredCapabilities(), command.skillRef(), command.instruction(), command.repositoryRef(), command.baseRevision(),
                    command.priority(), 0, Instant.EPOCH, Instant.EPOCH);
            current.put(task.id(), task);
            return task;
        });
        add(WorkflowPlanService.executionId(plan, 0, 1), CompletionVerdict.PASS);
        add(WorkflowPlanService.reviewId(plan, 0, 1), CompletionVerdict.PASS);
        for (int round = 1; round <= 3; round++) {
            add(WorkflowPlanService.executionId(plan, 1, round), CompletionVerdict.PASS);
            add(WorkflowPlanService.reviewId(plan, 1, round), CompletionVerdict.REPAIR_REQUIRED);
        }
    }

    private WorkTask task(TaskId id, TaskState state) {
        return new WorkTask(id, TaskType.EXTERNAL_WORK, state, WorkflowPlanService.SUBJECT, plan.id(), Set.of("workflow-review"),
                "workflow-stage-reviewer", "Review", null, null, TaskPriority.NORMAL, 5, Instant.EPOCH, Instant.EPOCH);
    }

    private void add(TaskId id, CompletionVerdict verdict) {
        current.put(id, task(id, TaskState.COMPLETED));
        reports.put(id, new CompletionReport(verdict, verdict.name()));
    }

    @Test
    void newReviewsReceiveTheAcceptedPlanAndItsIndependentAcceptance() {
        setup();
        service.createReview(plan, 1, 3);
        assertThat(created.getFirst().dependencies()).containsExactlyInAnyOrder(
                WorkflowPlanService.executionId(plan, 0, 1), WorkflowPlanService.reviewId(plan, 0, 1),
                WorkflowPlanService.executionId(plan, 1, 3));
    }

    @Test
    void resumedReviewReceivesTheAuthenticatedRecoveryReasonAsContext() {
        setup();
        var recovery = service.reviewRecovery(plan.id());
        String evidence = "Focused offline behavior checks passed for the unchanged committed revision; reassess the missing verification evidence.";
        service.resumeReview(plan.id(), recovery.taskId(), recovery.planVersion(), recovery.taskVersion(),
                "operator", evidence, true);
        assertThat(created.getFirst().instruction()).contains("Human review recovery context:\n" + evidence);
        assertThat(created.getFirst().dependencies()).contains(WorkflowPlanService.executionId(plan, 1, 3));
        assertThat(reports.get(new TaskId(recovery.taskId())).verdict()).isEqualTo(CompletionVerdict.REPAIR_REQUIRED);
    }

    @Test
    void manualRecoveryPreservesHistoryAndOnlyANewPassAdvancesTheOriginalPlan() {
        setup();
        var recovery = service.reviewRecovery(plan.id());
        assertThat(recovery.allowed()).isTrue();
        service.resumeReview(plan.id(), recovery.taskId(), recovery.planVersion(), recovery.taskVersion(), "operator", "Upstream context fixed", true);
        TaskId resumed = new TaskId("FP-recovery~code~review-3-resume-1");
        assertThat(created).hasSize(1);
        assertThat(created.getFirst().id()).isEqualTo(resumed);
        assertThat(reports.get(new TaskId(recovery.taskId())).verdict()).isEqualTo(CompletionVerdict.REPAIR_REQUIRED);
        assertThat(service.reviewRecovery(plan.id()).allowed()).isFalse();
        assertThatThrownBy(() -> service.resumeReview(plan.id(), recovery.taskId(), recovery.planVersion(), recovery.taskVersion(), "operator", "Duplicate", true))
                .isInstanceOf(IllegalStateException.class);
        verify(tasks).linkCorrection(eq(new TaskId(recovery.taskId())), eq(resumed), eq("operator"), eq("Upstream context fixed"), any(Instant.class));

        add(resumed, CompletionVerdict.REPAIR_REQUIRED);
        var handler = new WorkflowStepCompletedHandler(service, mock(WorkflowRepairDecisionHandler.class));
        assertThat(handler.onCompleted(new TaskCompletionContext(current.get(resumed), null, reports.get(resumed)))).isEmpty();
        assertThat(service.reviewRecovery(plan.id()).allowed()).isTrue();
        var second = service.reviewRecovery(plan.id());
        service.resumeReview(plan.id(), second.taskId(), second.planVersion(), second.taskVersion(), "operator", "Evidence supplied", true);
        TaskId accepted = new TaskId("FP-recovery~code~review-3-resume-2");
        add(accepted, CompletionVerdict.PASS);
        TaskId next = WorkflowPlanService.executionId(plan, 2, 1);
        assertThat(handler.onCompleted(new TaskCompletionContext(current.get(accepted), null, reports.get(accepted)))).containsExactly(next);
        assertThat(created.getLast().dependencies()).contains(accepted).doesNotContain(resumed, new TaskId(recovery.taskId()));
        assertThat(service.reviewRecovery(plan.id()).allowed()).isFalse();
    }

    @Test
    void staleVersionOrEndedPlanCannotCreateARecoveryTask() {
        setup();
        var recovery = service.reviewRecovery(plan.id());
        assertThatThrownBy(() -> service.resumeReview(plan.id(), recovery.taskId(), recovery.planVersion() - 1,
                recovery.taskVersion(), "operator", "Stale page", true)).isInstanceOf(IllegalStateException.class);
        assertThat(created).isEmpty();
        when(archives.archived(plan.id())).thenReturn(true);
        assertThat(service.reviewRecovery(plan.id()).allowed()).isFalse();
        assertThatThrownBy(() -> service.resumeReview(plan.id(), recovery.taskId(), recovery.planVersion(),
                recovery.taskVersion(), "operator", "Archived", true)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void authorizedFourthRoundUsesLatestReviewAndStopsAgainIfCorrectionIsRequired() {
        setup();
        var snapshot = service.reviewRecovery(plan.id());
        String scope = "Use Node.js 22 and npm 10. Reject extra arguments and empty names. Run the approved focused checks.";
        TaskId execution = service.continueAuthorizedRepair(plan.id(), snapshot.taskId(), snapshot.planVersion(), snapshot.taskVersion(),
                "operator", "Approved one more round", scope).orElseThrow();
        assertThat(execution.value()).endsWith("~code~work-4");
        assertThat(created.getLast().instruction()).contains(scope, "Previous independent review (attempt 3)");
        assertThat(created.getLast().dependencies()).contains(new TaskId(snapshot.taskId()));
        assertThat(service.continueAuthorizedRepair(plan.id(), snapshot.taskId(), snapshot.planVersion(), snapshot.taskVersion(),
                "operator", "Duplicate", scope)).isEmpty();
        WorkTask work = current.get(execution);
        current.put(execution, new WorkTask(work.id(), work.type(), TaskState.COMPLETED, work.subjectType(), work.subjectId(),
                work.requiredCapabilities(), work.skillRef(), work.instruction(), work.repositoryRef(), work.baseRevision(),
                work.priority(), 1, work.createdAt(), Instant.now()));
        reports.put(execution, new CompletionReport(CompletionVerdict.PASS, "Correction completed"));
        var handler = new WorkflowStepCompletedHandler(service, mock(WorkflowRepairDecisionHandler.class));
        TaskId review = handler.onCompleted(new TaskCompletionContext(current.get(execution), null, reports.get(execution))).getFirst();
        assertThat(review.value()).endsWith("~code~review-4");
        assertThat(created.getLast().instruction()).contains(scope);
        add(review, CompletionVerdict.REPAIR_REQUIRED);
        int before = created.size();
        assertThat(handler.onCompleted(new TaskCompletionContext(current.get(review), null, reports.get(review)))).isEmpty();
        assertThat(created).hasSize(before);
        assertThat(service.reviewRecovery(plan.id()).allowed()).isTrue();
        add(review, CompletionVerdict.PASS);
        assertThat(handler.onCompleted(new TaskCompletionContext(current.get(review), null, reports.get(review))))
                .containsExactly(WorkflowPlanService.executionId(plan, 2, 1));
        assertThat(created.getLast().dependencies()).contains(execution, review);
    }
    @Test
    void authorizedExecutionAndItsReviewReceiveConfirmedQuestionDecisions() {
        setup();
        var decisions = mock(WorkflowQuestionService.class);
        when(decisions.withContext(anyString(), eq(plan.id()))).thenAnswer(call -> call.getArgument(0, String.class)
                + "\nConfirmed decision Q-001: Reject multiple arguments.\n");
        service.setQuestionDecisions(decisions);
        var source = current.get(service.effectiveReviewId(plan, 1, 3));
        service.continueAuthorizedRepair(plan.id(), source.id().value(), plan.version(), source.version(),
                "operator", "Apply confirmed decisions", "Fix the review findings.").orElseThrow();
        var execution = created.getLast();
        assertThat(execution.instruction()).contains("Confirmed decision Q-001: Reject multiple arguments.");
        assertThat(execution.instruction().indexOf("Confirmed decision Q-001"))
                .isLessThan(execution.instruction().indexOf("Manager-authorized correction scope"));
        service.createReview(plan, 1, 4);
        assertThat(created.getLast().instruction()).contains("Confirmed decision Q-001: Reject multiple arguments.");
    }

}
