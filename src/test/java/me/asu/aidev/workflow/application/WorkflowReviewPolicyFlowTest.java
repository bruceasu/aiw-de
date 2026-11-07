package me.asu.aidev.workflow.application;

import java.time.Instant;
import java.util.*;
import me.asu.aidev.authority.ManagedPlanAdmission;
import me.asu.aidev.execution.application.TaskCompletionContext;
import me.asu.aidev.execution.domain.*;
import me.asu.aidev.execution.port.ExecutionAttemptRepository;
import me.asu.aidev.work.application.*;
import me.asu.aidev.work.domain.*;
import me.asu.aidev.work.port.WorkTaskRepository;
import me.asu.aidev.workflow.domain.*;
import me.asu.aidev.workflow.jdbc.*;
import me.asu.aidev.workflow.port.WorkflowPlanRepository;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;
import static me.asu.aidev.workflow.domain.WorkflowPlanSpec.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WorkflowReviewPolicyFlowTest {
    WorkflowPlan plan;WorkflowPlanRepository repository;WorkflowPlanService service;WorkTaskService creation;
    ExecutionAttemptRepository attempts;
    WorkflowStepCompletedHandler handler;Map<TaskId,WorkTask> rows;Map<TaskId,CompletionReport> reports;
    @BeforeEach void setup() {
        repository=mock(WorkflowPlanRepository.class);creation=mock(WorkTaskService.class);var tasks=mock(WorkTaskRepository.class);attempts=mock(ExecutionAttemptRepository.class);
        service=new WorkflowPlanService(repository,creation,tasks,attempts,mock(JdbcWorkflowControlRepository.class),mock(JdbcWorkflowHumanOutcomeRepository.class),mock(JdbcWorkflowPlanArchiveRepository.class));
        rows=new LinkedHashMap<>();reports=new HashMap<>();
        when(repository.find("FP-policy")).thenAnswer(call->Optional.of(plan));
        when(tasks.findBySubject("flexible-plan","FP-policy")).thenAnswer(call->List.copyOf(rows.values()));
        when(tasks.find(any())).thenAnswer(call->Optional.ofNullable(rows.get(call.getArgument(0))));
        when(tasks.lock(any())).thenReturn(true);
        when(attempts.findCompletionReport(any())).thenAnswer(call->Optional.ofNullable(reports.get(call.getArgument(0))));
        when(attempts.findCompletedInput(any())).thenAnswer(call->{
            TaskId id=call.getArgument(0);var task=rows.get(id);var report=reports.get(id);
            return task==null||task.state()!=TaskState.COMPLETED||report==null?Optional.empty():Optional.of(new TaskInput(id,task.type(),null,null,null,null,List.of(),report.verdict(),report.summary()));
        });
        when(repository.transition(anyString(),anyLong(),any(),any(),anyString(),anyString(),any())).thenAnswer(call->{plan=new WorkflowPlan(plan.id(),call.getArgument(3),plan.version()+1,plan.spec(),plan.createdBy(),call.getArgument(4),call.getArgument(5),plan.createdAt(),call.getArgument(6));return true;});
        handler=new WorkflowStepCompletedHandler(service,mock(WorkflowRepairDecisionHandler.class));
        ReflectionTestUtils.setField(handler,"personal",mock(ManagedPlanAdmission.class));
        @SuppressWarnings("unchecked") ObjectProvider<FactoryPlanLifecycle> factory=mock(ObjectProvider.class);when(factory.getObject()).thenReturn(mock(FactoryPlanLifecycle.class));
        ReflectionTestUtils.setField(handler,"factoryPlans",factory);
    }
    void plan(ReviewMode... modes) {
        var steps=new ArrayList<Step>();for(int i=0;i<modes.length;i++)steps.add(WorkflowReviewPolicyTest.step("s"+i,modes[i],ReviewRisk.LOW));
        plan=new WorkflowPlan("FP-policy",WorkflowPlan.State.CONFIRMED,1,new WorkflowPlanSpec("Goal",Source.MANAGER,steps),"human","human","",Instant.EPOCH,Instant.EPOCH);
    }
    WorkTask completed(int step,boolean review,CompletionVerdict verdict) {
        var id=review?WorkflowPlanService.reviewId(plan,step,1):WorkflowPlanService.executionId(plan,step,1);
        var task=new WorkTask(id,TaskType.EXTERNAL_WORK,TaskState.COMPLETED,"flexible-plan",plan.id(),Set.of("coding"),review?"workflow-stage-reviewer":"coding-work-package-executor","Instruction",1,null,null,TaskPriority.NORMAL,1,Instant.EPOCH,Instant.EPOCH);
        rows.put(id,task);reports.put(id,new CompletionReport(verdict,"Report"));return task;
    }
    @Test void ordinarySelfCheckPassCreatesNextWorkWithoutAReviewer() {
        plan(ReviewMode.SELF,ReviewMode.MILESTONE);var task=completed(0,false,CompletionVerdict.PASS);
        assertThat(handler.onCompleted(new TaskCompletionContext(task,null,reports.get(task.id())))).containsExactly(WorkflowPlanService.executionId(plan,1,1));
        var command=ArgumentCaptor.forClass(CreateWorkTaskCommand.class);verify(creation).createIfAbsent(command.capture());
        assertThat(command.getValue().skillRef()).isEqualTo("coding-work-package-executor");
        assertThat(command.getValue().dependencies()).contains(task.id());
    }
    @Test void failedSelfCheckDoesNotDispatchWorkOrReviewAndOffersExplicitRecovery() {
        plan(ReviewMode.SELF);var task=completed(0,false,CompletionVerdict.REPAIR_REQUIRED);
        assertThat(handler.onCompleted(new TaskCompletionContext(task,null,reports.get(task.id())))).isEmpty();
        verifyNoInteractions(creation);assertThat(service.reviewRecovery(plan.id()).allowed()).isTrue();
        assertThat(plan.state()).isEqualTo(WorkflowPlan.State.CONFIRMED);
    }
    @Test void milestoneDispatchesOneReviewWithAllCoveredAcceptanceCriteria() {
        plan(ReviewMode.SELF,ReviewMode.SELF,ReviewMode.MILESTONE);
        completed(0,false,CompletionVerdict.PASS);completed(1,false,CompletionVerdict.PASS);var task=completed(2,false,CompletionVerdict.PASS);
        assertThat(handler.onCompleted(new TaskCompletionContext(task,null,reports.get(task.id())))).containsExactly(WorkflowPlanService.reviewId(plan,2,1));
        var command=ArgumentCaptor.forClass(CreateWorkTaskCommand.class);verify(creation).createIfAbsent(command.capture());
        assertThat(command.getValue().skillRef()).isEqualTo("workflow-stage-reviewer");
        assertThat(command.getValue().instruction()).contains("Acceptance for s0","Acceptance for s1","Acceptance for s2");
        assertThat(command.getValue().dependencies()).contains(WorkflowPlanService.executionId(plan,0,1),WorkflowPlanService.executionId(plan,1,1),task.id());
    }
    @Test void finalSelfCheckCanCompleteOnlyUnderItsConfirmedPolicy() {
        plan(ReviewMode.SELF);var task=completed(0,false,CompletionVerdict.PASS);
        assertThat(handler.onCompleted(new TaskCompletionContext(task,null,reports.get(task.id())))).isEmpty();
        assertThat(plan.state()).isEqualTo(WorkflowPlan.State.COMPLETED);
        assertThat(plan.reason()).contains("self-check");verifyNoInteractions(creation);
    }
    @Test void independentStepCannotUseAWorkerPassAsItsFinalAcceptance() {
        plan(ReviewMode.INDEPENDENT);var task=completed(0,false,CompletionVerdict.PASS);
        service.completeReviewed(plan.id(),task.id());assertThat(plan.state()).isEqualTo(WorkflowPlan.State.CONFIRMED);
        verify(repository,never()).transition(anyString(),anyLong(),any(),any(),anyString(),anyString(),any());
    }
    @Test void aReviewFailureKeepsItsCorrectionRoundUnderIndependentRecheck() {
        plan(ReviewMode.SELF);completed(0,false,CompletionVerdict.PASS);completed(0,true,CompletionVerdict.REPAIR_REQUIRED);
        assertThat(service.requiresReview(plan,0,2)).isTrue();
    }
    @Test void failedSelfCheckCanRequestOneVersionedReviewWithoutRerunningWork() {
        plan(ReviewMode.SELF);var task=completed(0,false,CompletionVerdict.REPAIR_REQUIRED);
        service.resumeReview(plan.id(),task.id().value(),1,task.version(),"human","Please inspect the blocked result",true);
        var command=ArgumentCaptor.forClass(CreateWorkTaskCommand.class);verify(creation).createIfAbsent(command.capture());
        assertThat(command.getValue().id()).isEqualTo(WorkflowPlanService.reviewId(plan,0,1));
        assertThat(command.getValue().skillRef()).isEqualTo("workflow-stage-reviewer");
    }
    @Test void selfAcceptanceUsesTheCompletedQuestionSuccessorInsteadOfTheYieldedSource() {
        plan(ReviewMode.SELF);var root=completed(0,false,CompletionVerdict.PASS);reports.remove(root.id());
        rows.put(root.id(),new WorkTask(root.id(),root.type(),TaskState.CONTINUED,root.subjectType(),root.subjectId(),root.requiredCapabilities(),root.skillRef(),root.instruction(),1,null,null,root.priority(),1,Instant.EPOCH,Instant.EPOCH));
        var next=new WorkTask(new TaskId(root.id().value()+"-resume-1"),root.type(),TaskState.COMPLETED,root.subjectType(),root.subjectId(),root.requiredCapabilities(),root.skillRef(),root.instruction(),1,null,null,root.priority(),1,Instant.EPOCH,Instant.EPOCH);
        rows.put(next.id(),next);var report=new CompletionReport(CompletionVerdict.PASS,"Confirmed continuation complete");reports.put(next.id(),report);
        when(attempts.findCompletedInput(root.id())).thenReturn(Optional.of(new TaskInput(next.id(),next.type(),null,null,null,null,List.of(),report.verdict(),report.summary())));
        handler.onCompleted(new TaskCompletionContext(next,null,report));assertThat(plan.state()).isEqualTo(WorkflowPlan.State.COMPLETED);
    }
}
