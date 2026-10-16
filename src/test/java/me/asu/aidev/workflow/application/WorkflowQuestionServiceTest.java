package me.asu.aidev.workflow.application;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import me.asu.aidev.output.domain.TaskOutputBundle;
import me.asu.aidev.output.jdbc.JdbcTaskOutputRepository;
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
import me.asu.aidev.workflow.jdbc.JdbcWorkflowQuestionRepository;
import me.asu.aidev.workflow.jdbc.JdbcWorkflowQuestionRepository.Answer;
import me.asu.aidev.workflow.jdbc.JdbcWorkflowRepairAuthorizationRepository;
import me.asu.aidev.workflow.port.WorkflowPlanRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WorkflowQuestionServiceTest {
    private final WorkflowPlanRepository plans = mock(WorkflowPlanRepository.class);
    private final WorkTaskRepository tasks = mock(WorkTaskRepository.class);
    private final JdbcTaskOutputRepository outputs = mock(JdbcTaskOutputRepository.class);
    private final JdbcWorkflowQuestionRepository decisions = mock(JdbcWorkflowQuestionRepository.class);
    private final JdbcWorkflowPlanArchiveRepository archives = mock(JdbcWorkflowPlanArchiveRepository.class);
    private final JdbcWorkflowHumanOutcomeRepository outcomes = mock(JdbcWorkflowHumanOutcomeRepository.class);
    private final JdbcWorkflowControlRepository controls = mock(JdbcWorkflowControlRepository.class);
    private final JdbcWorkflowRepairAuthorizationRepository repairs = mock(JdbcWorkflowRepairAuthorizationRepository.class);
    private final WorkflowQuestionService service = new WorkflowQuestionService(plans, tasks, outputs, decisions, archives, outcomes, controls, repairs);
    private final List<Answer> history = new ArrayList<>();
    private WorkflowPlan plan;
    private TaskState state = TaskState.COMPLETED;
    private TaskOutputBundle.File file;
    private String body;
    private static final String ID = "FP-questions", TASK = ID + "~requirements~work-1";

    @BeforeEach
    void setup() {
        var spec = new WorkflowPlanSpec("Greeting", WorkflowPlanSpec.Source.MANAGER, List.of(
                new WorkflowPlanSpec.Step("requirements", Set.of("requirement-analysis"), Set.of(),
                        "software-requirements-analyst", "Clarify", null, null, Set.of())));
        plan = new WorkflowPlan(ID, WorkflowPlan.State.CONFIRMED, 7, spec, "human", "human", "", Instant.EPOCH, Instant.EPOCH);
        file = new TaskOutputBundle.File("file-one", "artifacts/requirements.json", "DOCUMENT", "a".repeat(64), 512);
        body = """
                {"payload":{"openQuestions":[
                  {"id":"Q-001","state":"UNKNOWN","impact":"IMPORTANT","question":"How should multiple arguments work?","affects":["FR-001"],"temporaryAssumption":"Only one argument is specified."},
                  {"id":"Q-002","state":"UNKNOWN","question":"How should an empty argument work?"}
                ]}}
                """;
        when(plans.find(ID)).thenAnswer(call -> Optional.of(plan));
        when(tasks.findBySubject(WorkflowPlanService.SUBJECT, ID)).thenAnswer(call -> List.of(task()));
        when(tasks.lock(any())).thenReturn(true);
        when(outputs.completed(TASK)).thenAnswer(call -> Optional.of(new TaskOutputBundle("attempt", TASK, "repo", null, null, "output", 1, "COMPLETED", "PASS", List.of(file))));
        when(outputs.file(anyString())).thenAnswer(call -> new JdbcTaskOutputRepository.StoredFile("attempt", file,
                Base64.getEncoder().encodeToString(body.getBytes(StandardCharsets.UTF_8))));
        when(decisions.history(ID)).thenAnswer(call -> history.stream().sorted(Comparator.comparingLong(Answer::planVersion).reversed()).toList());
        doAnswer(call -> { history.add(call.getArgument(0)); return null; }).when(decisions).insert(any());
        when(repairs.list(ID)).thenReturn(List.of());
        when(plans.transition(eq(ID), anyLong(), eq(WorkflowPlan.State.CONFIRMED), eq(WorkflowPlan.State.CONFIRMED), anyString(), anyString(), any()))
                .thenAnswer(call -> {
                    if (plan.version() != (long) call.getArgument(1)) return false;
                    plan = new WorkflowPlan(ID, plan.state(), plan.version() + 1, plan.spec(), "human", call.getArgument(4), call.getArgument(5), Instant.EPOCH, Instant.now());
                    return true;
                });
    }

    private WorkTask task() { return new WorkTask(new TaskId(TASK), TaskType.EXTERNAL_WORK, state,
            WorkflowPlanService.SUBJECT, ID, Set.of("requirement-analysis"), "software-requirements-analyst",
            "Clarify", null, null, TaskPriority.NORMAL, 1, Instant.EPOCH, Instant.EPOCH); }
    private WorkflowQuestionService.Question first() { return service.board(ID).questions().getFirst(); }
    private Answer confirm(WorkflowQuestionService.Question question, String answer) {
        return service.answer(ID, plan.version(), question.key(), file.id(), file.sha256(), answer, true, "human");
    }

    @Test
    void extractsQuestionsAndLinksTheExactFileWithoutApprovingTemporaryAssumptions() {
        var board = service.board(ID);
        assertThat(board.questions()).hasSize(2);
        assertThat(board.blockedReason()).isNull();
        assertThat(first().decision()).isNull();
        assertThat(first().source().file().id()).isEqualTo("file-one");
        assertThat(service.withContext("Original instruction", ID)).isEqualTo("Original instruction");
        assertThat(service.preview(ID, file.id()).content()).contains("Q-001");
        assertThatThrownBy(() -> service.preview(ID, "other-plan-file")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void confirmedAnswersAreVersionedAndReplaceTheOldSnapshotWithoutChangingHistory() {
        var first = first();
        var original = confirm(first, "Reject multiple arguments with stderr and a nonzero exit.");
        String snapshot = service.withContext("Implement the greeting", ID);
        assertThat(snapshot).contains(original.id(), file.sha256(), "Confirmed answer: Reject multiple arguments");
        confirm(first, "Use the first argument and ignore the rest.");
        String updated = service.withContext(snapshot, ID);
        assertThat(updated).contains("Use the first argument").doesNotContain("Confirmed answer: Reject multiple arguments");
        assertThat(snapshot).contains("Confirmed answer: Reject multiple arguments");
        assertThat(service.board(ID).history()).hasSize(2);
        verify(tasks, never()).insert(any()); // Answering never creates execution work.
    }

    @Test
    void rejectsUnconfirmedStaleAndChangedSourcesAndNeverSilentlyApprovesThem() {
        var q = first();
        assertThatThrownBy(() -> service.answer(ID, 7, q.key(), file.id(), file.sha256(), "Reject", false, "human"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.answer(ID, 6, q.key(), file.id(), file.sha256(), "Reject", true, "human"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> service.answer(ID, 7, q.key(), file.id(), "b".repeat(64), "Reject", true, "human"))
                .isInstanceOf(IllegalStateException.class);
        assertThat(history).isEmpty();
        confirm(q, "Reject");
        file = new TaskOutputBundle.File("file-two", file.path(), "DOCUMENT", "b".repeat(64), 512);
        assertThat(first().decision()).isNull();
        assertThat(service.withContext("Implement", ID)).isEqualTo("Implement");
        assertThat(service.board(ID).history()).hasSize(1);
    }

    @Test
    void activeWorkBlocksConfirmationAndMalformedJsonDoesNotInventQuestions() {
        var q = first(); state = TaskState.RUNNING;
        assertThat(service.board(ID).blockedReason()).contains("running tasks");
        assertThatThrownBy(() -> confirm(q, "Reject")).isInstanceOf(IllegalStateException.class);
        state = TaskState.COMPLETED; body = "null";
        assertThat(service.board(ID).questions()).isEmpty();
        body = "{broken";
        assertThat(service.board(ID).warnings()).hasSize(1);
    }
}
