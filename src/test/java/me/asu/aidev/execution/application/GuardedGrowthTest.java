package me.asu.aidev.execution.application;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import me.asu.aidev.execution.domain.CompletionReport;
import me.asu.aidev.execution.domain.CompletionVerdict;
import me.asu.aidev.execution.guard.ExecutionGuard;
import me.asu.aidev.knowledge.application.KnowledgeService;
import me.asu.aidev.work.application.DependencyResolver;
import me.asu.aidev.work.domain.TaskId;
import me.asu.aidev.work.domain.TaskState;
import me.asu.aidev.work.domain.WorkTask;
import me.asu.aidev.work.port.WorkTaskRepository;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;

class GuardedGrowthTest {
    @Test void waitingAndSupersededResultsDoNotContributeLessons() {
        var guard=mock(ExecutionGuard.class);
        var tasks=mock(WorkTaskRepository.class);
        var knowledge=mock(KnowledgeService.class);
        var dispatcher=mock(TaskCompletionDispatcher.class);
        var dependencyResolver=mock(DependencyResolver.class);
        var execution=new TaskExecutionService(tasks,null,null,dependencyResolver,dispatcher,knowledge,
                List.of(),List.of(),List.of(),null);
        execution.setExecutionGuard(guard,null);

        var id=new TaskId("task");
        var task=mock(WorkTask.class);
        var finished=Instant.ofEpochSecond(5);
        var report=new CompletionReport(CompletionVerdict.PASS,"Done","Reusable result");
        when(tasks.find(id)).thenReturn(Optional.of(task));
        when(task.state()).thenReturn(TaskState.COMPLETED);
        when(task.updatedAt()).thenReturn(finished);
        when(guard.completions("plan")).thenReturn(List.of(Map.of(
                "task_id","task","attempt_id","attempt","event_type","COMPLETED","report_json","receipt")));
        when(guard.decode("receipt",CompletionReport.class)).thenReturn(report);

        execution.drainGuardedCompletions("plan");
        verify(guard,never()).completionApplied("attempt");
        verifyNoInteractions(knowledge,dispatcher);

        when(guard.mayAdvance("task")).thenReturn(true);
        execution.drainGuardedCompletions("plan");
        verifyNoInteractions(knowledge,dispatcher);

        when(guard.latestAttempt("task","attempt")).thenReturn(true);
        execution.drainGuardedCompletions("plan");
        var order=inOrder(knowledge,dispatcher,dependencyResolver);
        order.verify(knowledge).recordGrowth(task,report,finished);
        order.verify(dispatcher).dispatch(any());
        order.verify(dependencyResolver).refreshDependents(id);
    }
}
