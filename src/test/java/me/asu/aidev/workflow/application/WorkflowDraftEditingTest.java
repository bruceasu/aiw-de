package me.asu.aidev.workflow.application;

import java.time.Instant;
import java.util.*;
import me.asu.aidev.authority.ManagedPlanAdmission;
import me.asu.aidev.execution.port.ExecutionAttemptRepository;
import me.asu.aidev.work.application.WorkTaskService;
import me.asu.aidev.work.domain.*;
import me.asu.aidev.work.port.WorkTaskRepository;
import me.asu.aidev.workflow.domain.*;
import me.asu.aidev.workflow.jdbc.*;
import me.asu.aidev.workflow.port.WorkflowPlanRepository;
import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WorkflowDraftEditingTest {
    final WorkflowPlanRepository repository=mock(WorkflowPlanRepository.class);
    final WorkTaskRepository tasks=mock(WorkTaskRepository.class);
    final WorkTaskService creation=mock(WorkTaskService.class);
    final JdbcWorkflowPlanArchiveRepository archives=mock(JdbcWorkflowPlanArchiveRepository.class);
    final WorkflowPlanService service=new WorkflowPlanService(repository,creation,tasks,mock(ExecutionAttemptRepository.class),
            mock(JdbcWorkflowControlRepository.class),mock(JdbcWorkflowHumanOutcomeRepository.class),archives);
    WorkflowPlan plan;
    WorkflowPlanSpec spec(String goal) {
        return new WorkflowPlanSpec(goal,WorkflowPlanSpec.Source.MANAGER,List.of(new WorkflowPlanSpec.Step("analyze",
                Set.of("requirement-analysis"),Set.of(),"software-requirements-analyst","Clarify",null,null,Set.of())));
    }
    @BeforeEach void setup() {
        plan=new WorkflowPlan("plan",WorkflowPlan.State.DRAFT,3,
                new WorkflowPlanSpec("Original",WorkflowPlanSpec.Source.AI,spec("Original").steps()),
                "owner","owner","",Instant.EPOCH,Instant.EPOCH);
        when(repository.find("plan")).thenAnswer(call -> Optional.of(plan));
        when(tasks.findBySubject(WorkflowPlanService.SUBJECT,"plan")).thenReturn(List.of());
        when(repository.editDraft(eq("plan"),eq(3L),any(),eq("owner"),any())).thenAnswer(call -> {
            plan=new WorkflowPlan(plan.id(),plan.state(),4,call.getArgument(2),plan.createdBy(),"owner","Edited",plan.createdAt(),Instant.now());
            return true;
        });
    }
    @Test void editsSameDraftAndPreservesSourceWithoutStartingTasks() {
        var saved=service.editDraft("plan",3,spec("Updated"),"owner");
        assertThat(saved.id()).isEqualTo("plan");assertThat(saved.version()).isEqualTo(4);
        assertThat(saved.state()).isEqualTo(WorkflowPlan.State.DRAFT);
        assertThat(saved.spec().goal()).isEqualTo("Updated");assertThat(saved.spec().source()).isEqualTo(WorkflowPlanSpec.Source.AI);
        verify(repository,never()).insert(any());verifyNoInteractions(creation);
    }
    @Test void anotherOwnerCannotEdit() {
        assertThatThrownBy(() -> service.editDraft("plan",3,spec("Updated"),"another")).isInstanceOf(SecurityException.class);
        verify(repository,never()).editDraft(anyString(),anyLong(),any(),anyString(),any());
    }
    @Test void staleVersionCannotEdit() {
        assertThatThrownBy(() -> service.editDraft("plan",2,spec("Updated"),"owner")).isInstanceOf(IllegalStateException.class);
        verify(repository,never()).editDraft(anyString(),anyLong(),any(),anyString(),any());
    }
    @Test void confirmedPlanCannotEdit() {
        plan=new WorkflowPlan(plan.id(),WorkflowPlan.State.CONFIRMED,3,plan.spec(),"owner","owner","",Instant.EPOCH,Instant.EPOCH);
        assertThatThrownBy(() -> service.editableDraft("plan","owner")).isInstanceOf(IllegalStateException.class);
    }
    @Test void archivedDraftCannotEdit() {
        when(archives.archived("plan")).thenReturn(true);
        assertThatThrownBy(() -> service.editableDraft("plan","owner")).isInstanceOf(IllegalStateException.class);
    }
    @Test void managedDraftCannotBypassItsScope() {
        var managed=mock(ManagedPlanAdmission.class);when(managed.managed("plan")).thenReturn(true);
        service.setManagedPlanAdmission(managed);
        assertThatThrownBy(() -> service.editDraft("plan",3,spec("Updated"),"owner")).isInstanceOf(IllegalStateException.class);
    }
    @Test void existingTasksPreventEditing() {
        when(tasks.findBySubject(WorkflowPlanService.SUBJECT,"plan")).thenReturn(List.of(mock(WorkTask.class)));
        assertThatThrownBy(() -> service.editableDraft("plan","owner")).isInstanceOf(IllegalStateException.class);
    }
    @Test void concurrentConfirmationOrSaveRejectsTheWrite() {
        when(repository.editDraft(eq("plan"),eq(3L),any(),eq("owner"),any())).thenReturn(false);
        assertThatThrownBy(() -> service.editDraft("plan",3,spec("Updated"),"owner")).isInstanceOf(IllegalStateException.class);
        assertThat(plan.version()).isEqualTo(3);verifyNoInteractions(creation);
    }
}
