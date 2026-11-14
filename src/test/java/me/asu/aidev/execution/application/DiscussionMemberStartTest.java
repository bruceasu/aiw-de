package me.asu.aidev.execution.application;

import java.time.Instant;
import java.util.*;
import me.asu.aidev.ai.application.AiProfileCatalog;
import me.asu.aidev.authority.AuthorityTaskPolicy;
import me.asu.aidev.execution.domain.*;
import me.asu.aidev.execution.port.ExecutionAttemptRepository;
import me.asu.aidev.goal.application.GoalTaskPolicy;
import me.asu.aidev.work.domain.*;
import me.asu.aidev.work.port.WorkTaskRepository;
import me.asu.aidev.worker.application.TeamMembers;
import me.asu.aidev.worker.domain.*;
import me.asu.aidev.worker.port.WorkerRepository;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class DiscussionMemberStartTest {
    @Test void frozenFactorySelectionStartsWithoutUsingTheGlobalCatalogVersion() {
        var tasks=mock(WorkTaskRepository.class);var workers=mock(WorkerRepository.class);
        var attempts=mock(ExecutionAttemptRepository.class);var catalog=mock(AiProfileCatalog.class);
        var service=new TaskExecutionService(tasks,workers,attempts,null,null,null,List.of(),List.of(),List.of(),catalog);
        var task=new WorkTask(new TaskId("GC-test"),TaskType.EXTERNAL_WORK,TaskState.CLAIMED,"goal-discussion","goal",
            Set.of("goal-discussion"),"goal-discussion","Clarify",null,null,TaskPriority.NORMAL,1,Instant.EPOCH,Instant.EPOCH);
        var worker=new WorkerId("worker");var lease=new LeaseToken("lease");
        when(tasks.find(task.id())).thenReturn(Optional.of(task));when(tasks.lock(task.id())).thenReturn(true);
        when(tasks.start(eq(task.id()),eq(worker),eq(lease),any())).thenReturn(true);
        when(attempts.transition(eq(lease),eq(ExecutionAttemptState.CLAIMED),eq(ExecutionAttemptState.RUNNING),any(),isNull())).thenReturn(true);
        when(attempts.attachAiSelection(eq(lease),eq(worker),any())).thenReturn(true);
        var actual=mock(Worker.class);when(actual.runtimeType()).thenReturn("openai-agents:openai:responses:goal-discussion");
        when(workers.find(worker)).thenReturn(Optional.of(actual));when(catalog.hasPolicy(TaskType.EXTERNAL_WORK)).thenReturn(true);
        var authority=mock(AuthorityTaskPolicy.class);
        doThrow(new IllegalStateException("Wrong global catalog version")).when(authority).selection(task,"factory-default","factory-one");
        service.setAuthorityPolicy(authority);
        service.setNativeQuestions(mock(org.springframework.beans.factory.ObjectProvider.class));
        var profile=new AiProfileCatalog.NamedProfile("factory-default",new AiProfileCatalog.Profile("openai","openai-agents","model-a","high",false,1));
        var member=new TeamMembers.Profile("member",1,"Analyst","Clarify",List.of(),"","ACTIVE",
            new TeamMembers.Settings("model-a","high",List.of(),List.of(),List.of()));
        var goal=spy(new GoalTaskPolicy(null,null));
        var frozen=goal.member(new GoalTaskPolicy.Frozen("openai","factory-one",List.of(profile),"responses"),member);
        doReturn(frozen).when(goal).frozen(task);service.setGoalPolicy(goal);
        service.start(task.id(),worker,lease,"factory-default","factory-one","Use the frozen member model");
        verify(authority,never()).selection(any(),any(),any());
        verify(attempts).attachAiSelection(eq(lease),eq(worker),argThat(selection -> selection.model().equals("model-a")));
        assertThatThrownBy(()->service.start(task.id(),worker,lease,"outside-member-range","factory-one","Try another model"))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("outside the frozen");
        verify(attempts,times(1)).attachAiSelection(eq(lease),eq(worker),any());
    }
}
