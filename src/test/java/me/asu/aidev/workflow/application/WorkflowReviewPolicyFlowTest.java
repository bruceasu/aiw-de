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
    @org.junit.jupiter.api.io.TempDir java.nio.file.Path temp;
    WorkflowPlan plan;WorkflowPlanRepository repository;WorkflowPlanService service;WorkTaskService creation;
    ExecutionAttemptRepository attempts;WorkTaskRepository tasks;
    WorkflowStepCompletedHandler handler;Map<TaskId,WorkTask> rows;Map<TaskId,CompletionReport> reports;
    @BeforeEach void setup() {
        repository=mock(WorkflowPlanRepository.class);creation=mock(WorkTaskService.class);tasks=mock(WorkTaskRepository.class);attempts=mock(ExecutionAttemptRepository.class);
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
    @Test void independentAcceptanceCanCompleteAResultWhoseSelfCheckFailed() {
        plan(ReviewMode.SELF);var work=completed(0,false,CompletionVerdict.REPAIR_REQUIRED);
        service.resumeReview(plan.id(),work.id().value(),1,work.version(),"human","Inspect existing result",true);
        var review=completed(0,true,CompletionVerdict.PASS);
        handler.onCompleted(new TaskCompletionContext(review,null,reports.get(review.id())));
        assertThat(plan.state()).isEqualTo(WorkflowPlan.State.COMPLETED);
        assertThat(reports.get(work.id()).verdict()).isEqualTo(CompletionVerdict.REPAIR_REQUIRED);
    }
    @Test void earlierFailedSelfCheckDoesNotBlockCompletionAfterIndependentAcceptance() {
        plan(ReviewMode.SELF,ReviewMode.SELF);completed(0,false,CompletionVerdict.REPAIR_REQUIRED);completed(0,true,CompletionVerdict.PASS);
        var last=completed(1,false,CompletionVerdict.PASS);
        handler.onCompleted(new TaskCompletionContext(last,null,reports.get(last.id())));
        assertThat(plan.state()).isEqualTo(WorkflowPlan.State.COMPLETED);
    }
    /** Real SQL question facts, Owner validation and completed-input repository, without a Codex process. */
    org.springframework.jdbc.core.JdbcTemplate nativeCompletion(ReviewMode mode) {
        plan(mode);var root=completed(0,false,CompletionVerdict.PASS);reports.remove(root.id());
        var source=new WorkTask(root.id(),root.type(),TaskState.CONTINUED,root.subjectType(),root.subjectId(),root.requiredCapabilities(),root.skillRef(),root.instruction(),1,null,null,root.priority(),1,Instant.EPOCH,Instant.EPOCH);
        var next=new WorkTask(new TaskId(root.id().value()+"-resume-1"),root.type(),TaskState.COMPLETED,root.subjectType(),root.subjectId(),root.requiredCapabilities(),root.skillRef(),root.instruction(),1,null,null,root.priority(),1,Instant.EPOCH,Instant.EPOCH);
        rows.put(source.id(),source);rows.put(next.id(),next);
        var jdbc=new org.springframework.jdbc.core.JdbcTemplate(new org.springframework.jdbc.datasource.DriverManagerDataSource("jdbc:sqlite:"+temp.resolve("native.db")));
        jdbc.execute("CREATE TABLE formal_question_slot(source_task_id TEXT,question_id TEXT,successor_task_id TEXT)");
        jdbc.execute("CREATE TABLE formal_question(id TEXT,owner_id TEXT,plan_id TEXT,source_digest TEXT,payload_json TEXT,state TEXT,request_version INTEGER,answer_revision INTEGER,answer_json TEXT,successor_task_id TEXT,blocked_reason TEXT)");
        jdbc.execute("CREATE TABLE authority_owner(id TEXT,actor TEXT)");jdbc.update("INSERT INTO authority_owner VALUES('owner','human')");
        jdbc.execute("CREATE TABLE plan_run_owner(plan_id TEXT,owner_id TEXT,source_digest TEXT,source_version INTEGER)");
        jdbc.execute("CREATE TABLE flexible_workflow_plan(id TEXT,spec_json TEXT)");jdbc.update("INSERT INTO flexible_workflow_plan VALUES(?,'frozen-spec')",plan.id());
        jdbc.update("INSERT INTO plan_run_owner VALUES(?,'owner',?,1)",plan.id(),me.asu.aidev.authority.AuthorityRepository.hash("frozen-spec"));
        jdbc.execute("CREATE TABLE execution_context(task_id TEXT,successor_task_id TEXT)");
        jdbc.execute("CREATE TABLE work_task(id TEXT,task_type TEXT,repository_ref TEXT)");
        jdbc.update("INSERT INTO work_task VALUES(?,?,NULL)",next.id().value(),next.type().name());
        jdbc.execute("CREATE TABLE execution_attempt(id TEXT,task_id TEXT,state TEXT,attempt_no INTEGER,completion_verdict TEXT,completion_summary TEXT,base_revision TEXT,result_revision TEXT,publication_ref TEXT,changed_files TEXT)");
        jdbc.update("INSERT INTO execution_attempt VALUES('source-attempt',?,'YIELDED',1,NULL,NULL,NULL,NULL,NULL,NULL)",source.id().value());
        jdbc.update("INSERT INTO execution_attempt VALUES('next-attempt',?,'COMPLETED',1,'PASS','Done',NULL,NULL,NULL,NULL)",next.id().value());
        var authority=new me.asu.aidev.authority.AuthorityRepository(jdbc);
        var guard=mock(me.asu.aidev.execution.guard.ExecutionGuard.class);
        var owners=new me.asu.aidev.execution.run.NativePlanOwners(authority,null,null,repository,tasks,guard);
        var formal=new me.asu.aidev.questions.FormalQuestions(authority,null,null,null,null,tasks,service,null,null);
        var nativeQuestions=new me.asu.aidev.questions.NativeQuestions(authority,null,guard,owners,null,tasks,null,null,null,null,provider(formal),null,null);
        var questionSource=new me.asu.aidev.questions.QuestionContract.Source(me.asu.aidev.questions.QuestionContract.SourceKind.NATIVE_PLAN_TASK,plan.id(),1,source.id().value(),null);
        var answer=new me.asu.aidev.questions.QuestionContract.Answer("Use target A","human","CONSOLE","message",1,false,null);
        var question=new me.asu.aidev.questions.QuestionContract.Question("aiw.questions.v2","question","owner",questionSource,"source-attempt","gap",me.asu.aidev.questions.QuestionContract.Kind.PROFESSIONAL,"Which target?","",List.of(),"",1,me.asu.aidev.questions.QuestionContract.State.APPLIED,1,answer,null,null,next.id().value(),null);
        jdbc.update("INSERT INTO formal_question VALUES('question','owner',?,?,?,'APPLIED',1,1,?,?,NULL)",plan.id(),nativeQuestions.validate(questionSource,"owner",false),authority.encode(question),authority.encode(answer),next.id().value());
        jdbc.update("INSERT INTO formal_question_slot VALUES(?,'question',?)",source.id().value(),next.id().value());
        ReflectionTestUtils.setField(service,"nativeQuestions",provider(nativeQuestions));
        attempts=new me.asu.aidev.execution.jdbc.JdbcExecutionAttemptRepository(jdbc);
        ReflectionTestUtils.setField(service,"attempts",attempts);
        return jdbc;
    }
    static <T> ObjectProvider<T> provider(T value) {
        @SuppressWarnings("unchecked") ObjectProvider<T> provider=mock(ObjectProvider.class);when(provider.getObject()).thenReturn(value);return provider;
    }
    @Test void actualNativeQuestionSqlAndRepositoryResolveFinalSelfAcceptance() {
        nativeCompletion(ReviewMode.SELF);var next=rows.values().stream().filter(t->t.state()==TaskState.COMPLETED).findFirst().orElseThrow();
        handler.onCompleted(new TaskCompletionContext(next,null,attempts.findCompletionReport(next.id()).orElseThrow()));
        assertThat(plan.state()).isEqualTo(WorkflowPlan.State.COMPLETED);
    }
    @Test void questionResolutionRejectsUnappliedOrChangedOwnerAndSourceFacts() {
        var jdbc=nativeCompletion(ReviewMode.SELF);
        jdbc.update("UPDATE formal_question SET state='ANSWERED'");
        assertThatThrownBy(()->service.effectiveExecutionId(plan,0,1)).isInstanceOf(SecurityException.class);
        jdbc.update("UPDATE formal_question SET state='APPLIED',owner_id='other'");
        assertThatThrownBy(()->service.effectiveExecutionId(plan,0,1)).isInstanceOf(SecurityException.class);
        jdbc.update("UPDATE formal_question SET owner_id='owner',source_digest='changed'");
        assertThatThrownBy(()->service.effectiveExecutionId(plan,0,1)).isInstanceOf(SecurityException.class);
    }
    @Test void correctionAfterNativeQuestionDependsOnCompletedSuccessor() {
        var jdbc=nativeCompletion(ReviewMode.INDEPENDENT);var review=completed(0,true,CompletionVerdict.REPAIR_REQUIRED);
        jdbc.update("INSERT INTO execution_attempt VALUES('review',?,'COMPLETED',1,'REPAIR_REQUIRED','Fix finding',NULL,NULL,NULL,NULL)",review.id().value());
        handler.onCompleted(new TaskCompletionContext(review,null,attempts.findCompletionReport(review.id()).orElseThrow()));
        var command=ArgumentCaptor.forClass(CreateWorkTaskCommand.class);verify(creation).createIfAbsent(command.capture());
        assertThat(command.getValue().dependencies()).contains(new TaskId(WorkflowPlanService.executionId(plan,0,1).value()+"-resume-1"))
                .doesNotContain(WorkflowPlanService.executionId(plan,0,1));
    }
    @Test void guardedV1QuestionKeepsItsExistingExecutionContextResolver() {
        var jdbc=nativeCompletion(ReviewMode.SELF);var root=WorkflowPlanService.executionId(plan,0,1);
        jdbc.update("UPDATE formal_question SET payload_json=replace(payload_json,'aiw.questions.v2','aiw.questions.v1')");
        jdbc.update("INSERT INTO execution_context VALUES(?,?)",root.value(),root.value()+"-resume-1");
        @SuppressWarnings("unchecked") var provider=(ObjectProvider<me.asu.aidev.questions.NativeQuestions>)ReflectionTestUtils.getField(service,"nativeQuestions");
        var guard=(me.asu.aidev.execution.guard.ExecutionGuard)ReflectionTestUtils.getField(provider.getObject(),"guard");
        when(guard.managed(root.value())).thenReturn(true);
        assertThat(service.effectiveExecutionId(plan,0,1)).isEqualTo(new TaskId(root.value()+"-resume-1"));
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
