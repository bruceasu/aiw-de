package me.asu.aidev.workflow.application;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import me.asu.aidev.execution.application.TaskCompletionContext;
import me.asu.aidev.execution.application.TaskCompletionDispatcher;
import me.asu.aidev.execution.application.TaskCompletionHandler;
import me.asu.aidev.execution.domain.CompletionReport;
import me.asu.aidev.execution.domain.CompletionVerdict;
import me.asu.aidev.work.domain.TaskId;
import me.asu.aidev.work.domain.TaskPriority;
import me.asu.aidev.work.domain.TaskState;
import me.asu.aidev.work.domain.TaskType;
import me.asu.aidev.work.domain.WorkTask;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class WorkflowCompletionRoutingTest {
    @Test
    void springRegistersOneExternalWorkAuthorityAndRoutesRepairDecisionsToIt() {
        var plans = mock(WorkflowPlanService.class);
        var authorizations = mock(WorkflowRepairAuthorizationService.class);
        var decision = completion("RA-decision", WorkflowRepairAuthorizationService.SUBJECT, TaskType.EXTERNAL_WORK);
        var repair = new TaskId("FP-example~code~work-4");
        when(authorizations.decide(decision)).thenReturn(List.of(repair));
        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBean(WorkflowQuestionService.class, () -> mock(WorkflowQuestionService.class));
            context.registerBean(me.asu.aidev.output.jdbc.JdbcTaskOutputRepository.class,
                    () -> mock(me.asu.aidev.output.jdbc.JdbcTaskOutputRepository.class));
            context.registerBean(WorkflowPlanService.class, () -> plans);
            context.registerBean(WorkflowRepairAuthorizationService.class, () -> authorizations);
            context.register(WorkflowStepCompletedHandler.class, WorkflowRepairDecisionHandler.class,
                    TaskCompletionDispatcher.class);
            context.refresh();
            assertThat(context.getBeansOfType(TaskCompletionHandler.class)).hasSize(1);
            var handler = context.getBean(WorkflowStepCompletedHandler.class);
            assertThat(handler.supports(completion("FP-example~code~work-1", WorkflowPlanService.SUBJECT, TaskType.EXTERNAL_WORK))).isTrue();
            assertThat(context.getBean(TaskCompletionDispatcher.class).dispatch(decision)).containsExactly(repair);
            verify(authorizations).decide(decision);
            verifyNoInteractions(plans);
            assertThat(handler.supports(completion("unrelated", "other-subject", TaskType.EXTERNAL_WORK))).isFalse();
            assertThat(handler.supports(completion("wrong-type", WorkflowRepairAuthorizationService.SUBJECT, TaskType.REQUIREMENT_ANALYSIS))).isFalse();
        }
    }

    @Test
    void duplicateCompletionAuthoritiesAreStillRejected() {
        var first = mock(TaskCompletionHandler.class);
        var second = mock(TaskCompletionHandler.class);
        when(first.supportedTypes()).thenReturn(Set.of(TaskType.EXTERNAL_WORK));
        when(second.supportedTypes()).thenReturn(Set.of(TaskType.EXTERNAL_WORK));
        assertThatThrownBy(() -> new TaskCompletionDispatcher(List.of(first, second)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("duplicate completion handler authority for EXTERNAL_WORK");
    }

    private TaskCompletionContext completion(String id, String subject, TaskType type) {
        var task = new WorkTask(new TaskId(id), type, TaskState.COMPLETED, subject, "example",
                Set.of("workflow-recovery-decision"), "workflow-recovery-decider", "Decide", null, null,
                TaskPriority.NORMAL, 0, Instant.EPOCH, Instant.EPOCH);
        return new TaskCompletionContext(task, null, new CompletionReport(CompletionVerdict.PASS, "Approved"));
    }
}
