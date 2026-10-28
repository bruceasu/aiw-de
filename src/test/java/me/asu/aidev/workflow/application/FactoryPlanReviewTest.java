package me.asu.aidev.workflow.application;

import java.nio.file.Path;
import java.nio.file.Files;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import me.asu.aidev.authority.*;
import me.asu.aidev.execution.domain.*;
import me.asu.aidev.work.domain.*;
import me.asu.aidev.work.port.WorkTaskRepository;
import me.asu.aidev.worker.domain.*;
import me.asu.aidev.workflow.domain.*;
import me.asu.aidev.workflow.jdbc.JdbcConfiguredPlanHistoryRepository;
import me.asu.aidev.workflow.port.WorkflowPlanRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;
import me.asu.aidev.execution.port.ExecutionAttemptRepository;
import me.asu.aidev.work.application.CreateWorkTaskCommand;
import me.asu.aidev.work.application.WorkTaskService;
import me.asu.aidev.output.jdbc.JdbcTaskOutputRepository;
import me.asu.aidev.workspace.application.ManagedWorkspaceService;
import org.springframework.transaction.PlatformTransactionManager;
import me.asu.aidev.workflow.jdbc.JdbcWorkflowPlanArchiveRepository;
import me.asu.aidev.workflow.jdbc.JdbcWorkflowHumanOutcomeRepository;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FactoryPlanReviewTest {
    @TempDir Path temp;
    JdbcTemplate jdbc;
    FactoryPlanLifecycle lifecycle;
    WorkflowPlanRepository plans;
    WorkflowPlan plan;
    WorkTask review;
    AuthorityBoundary boundary;
    WorkTaskRepository tasks;
    JdbcConfiguredPlanHistoryRepository history;

    @BeforeEach void setup() {
        jdbc=new JdbcTemplate(new DriverManagerDataSource("jdbc:sqlite:"+temp.resolve("review.db")));
        jdbc.execute("CREATE TABLE factory_plan_review(plan_id TEXT PRIMARY KEY,source_task_id TEXT,review_task_id TEXT,state TEXT,updated_at_epoch_ms INTEGER)");
        jdbc.execute("CREATE TABLE execution_attempt(task_id TEXT,worker_id TEXT)");
        jdbc.execute("CREATE TABLE authority_managed_plan(plan_id TEXT,version INTEGER)");
        jdbc.execute("CREATE TABLE workflow_factory_task(plan_id TEXT,task_id TEXT,current_task BOOLEAN,result_kind TEXT)");
        jdbc.update("INSERT INTO workflow_factory_task VALUES('FP-one','source',TRUE,'REPORT')");
        jdbc.execute("CREATE TABLE workflow_factory_plan(plan_id TEXT,original_draft_json TEXT,confirmed_by TEXT,confirmed_ms INTEGER)");
        jdbc.update("INSERT INTO workflow_factory_plan VALUES('FP-one','{}','owner',0)");
        jdbc.execute("CREATE TABLE web_console_plan_archive(plan_id TEXT)");
        jdbc.execute("CREATE TABLE plan_git_delivery(plan_id TEXT,state TEXT)");
        jdbc.update("INSERT INTO factory_plan_review VALUES('FP-one','source','FP-one~execute~review-1','PENDING',0)");
        var repo=mock(AuthorityRepository.class); when(repo.jdbc()).thenReturn(jdbc);
        plans=mock(WorkflowPlanRepository.class);
        var spec=new WorkflowPlanSpec("goal",WorkflowPlanSpec.Source.MANAGER,List.of(new WorkflowPlanSpec.Step(
                "execute",Set.of("coding"),Set.of(),"coding-work-package-executor","do work","repo",null,Set.of())));
        plan=new WorkflowPlan("FP-one",WorkflowPlan.State.CONFIRMED,3,spec,"owner","owner","",Instant.EPOCH,Instant.EPOCH);
        when(plans.find("FP-one")).thenReturn(Optional.of(plan));
        when(plans.transition(eq("FP-one"),eq(3L),eq(WorkflowPlan.State.CONFIRMED),eq(WorkflowPlan.State.COMPLETED),anyString(),anyString(),any())).thenReturn(true);
        boundary=mock(AuthorityBoundary.class); tasks=mock(WorkTaskRepository.class); history=mock(JdbcConfiguredPlanHistoryRepository.class);
        lifecycle=new FactoryPlanLifecycle(repo,boundary,mock(ManagedPlanAdmission.class),
                mock(BusinessActionPermissions.class),mock(AuthorityGrantService.class),plans,tasks,history);
        review=new WorkTask(new TaskId("FP-one~execute~review-1"),TaskType.EXTERNAL_WORK,TaskState.COMPLETED,
                "flexible-plan","FP-one",Set.of("workflow-review"),"workflow-stage-reviewer","review","repo",null,
                TaskPriority.NORMAL,0,Instant.EPOCH,Instant.EPOCH);
    }

    @Test void passCompletesPlanAndClearsReviewGate() {
        assertThat(lifecycle.reviewRequired("FP-one")).isTrue();
        assertThat(lifecycle.completeReview(review,new CompletionReport(CompletionVerdict.PASS,"accepted"))).isTrue();
        assertThat(lifecycle.reviewRequired("FP-one")).isFalse();
        verify(plans).transition(eq("FP-one"),eq(3L),eq(WorkflowPlan.State.CONFIRMED),eq(WorkflowPlan.State.COMPLETED),anyString(),anyString(),any());
    }

    @Test void reviewRequiringChangesKeepsGateClosed() {
        lifecycle.completeReview(review,new CompletionReport(CompletionVerdict.REPAIR_REQUIRED,"needs changes"));
        assertThat(lifecycle.review("FP-one").state()).isEqualTo("CHANGES_REQUIRED");
        assertThat(lifecycle.reviewRequired("FP-one")).isTrue();
        verify(plans,never()).transition(anyString(),anyLong(),any(),any(),anyString(),anyString(),any());
    }

    @Test void reviewCannotCompleteAfterSourceChanges() {
        jdbc.update("UPDATE workflow_factory_task SET task_id='other' WHERE plan_id='FP-one'");
        assertThatThrownBy(() -> lifecycle.completeReview(review,new CompletionReport(CompletionVerdict.PASS,"accepted")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("no longer current");
        assertThat(lifecycle.reviewRequired("FP-one")).isTrue();
        verify(plans,never()).transition(anyString(),anyLong(),any(),any(),anyString(),anyString(),any());
    }

    @Test void authorCannotClaimReviewAndOtherWorkerCan() {
        jdbc.update("INSERT INTO execution_attempt VALUES('source','author')");
        var policy=new FactoryReviewClaimPolicy(jdbc);
        assertThat(policy.canClaim(review,worker("author"))).isFalse();
        assertThat(policy.canClaim(review,worker("reviewer"))).isTrue();
        jdbc.update("UPDATE factory_plan_review SET state='PASSED' WHERE plan_id='FP-one'");
        assertThat(policy.canClaim(review,worker("reviewer"))).isFalse();
    }

    @Test void migrationRetainsPlanCleanupWhenReviewTaskIsDeleted() throws Exception {
        var source=new DriverManagerDataSource("jdbc:sqlite:"+temp.resolve("migration.db")+"?foreign_keys=on");
        var db=new JdbcTemplate(source);
        db.execute("CREATE TABLE workflow_factory_plan(plan_id TEXT PRIMARY KEY)");
        db.execute("CREATE TABLE work_task(id TEXT PRIMARY KEY)");
        for (String sql:Files.readString(Path.of("src/main/resources/db/migration/V24__factory_plan_review.sql")).split(";"))
            if (!sql.isBlank()) db.execute(sql);
        db.update("INSERT INTO workflow_factory_plan VALUES('p')");
        db.update("INSERT INTO work_task VALUES('source'),('review')");
        db.update("INSERT INTO factory_plan_review VALUES('p','source','review','PASSED',0)");
        db.update("DELETE FROM work_task WHERE id='review'");
        assertThat(db.queryForObject("SELECT review_task_id FROM factory_plan_review WHERE plan_id='p'",String.class)).isNull();
        db.update("DELETE FROM workflow_factory_plan WHERE plan_id='p'");
        assertThat(db.queryForObject("SELECT COUNT(*) FROM factory_plan_review",Long.class)).isZero();
    }

    @Test void pendingFactoryReviewBlocksManualCompletionAndArchive() {
        var factory=mock(FactoryPlanLifecycle.class);
        when(factory.view("FP-one")).thenReturn(new FactoryPlanLifecycle.View(null,"owner",0));
        when(factory.reviewRequired("FP-one")).thenReturn(true);
        @SuppressWarnings("unchecked") ObjectProvider<FactoryPlanLifecycle> provider=mock(ObjectProvider.class);
        when(provider.getObject()).thenReturn(factory);
        var service=mock(WorkflowPlanService.class,CALLS_REAL_METHODS);
        ReflectionTestUtils.setField(service,"factoryPlans",provider);
        assertThatThrownBy(() -> service.complete("FP-one",3,"owner","done"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("review must pass");
        service.completeReviewed("FP-one",review.id());
        verify(service,never()).get("FP-one");
        var plansService=mock(WorkflowPlanService.class);
        when(plansService.get("FP-one")).thenReturn(plan);
        var archive=new WorkflowPlanArchiveService(plansService,mock(ExecutionAttemptRepository.class),
                mock(JdbcWorkflowPlanArchiveRepository.class),mock(JdbcWorkflowHumanOutcomeRepository.class));
        ReflectionTestUtils.setField(archive,"factoryPlans",provider);
        assertThat(archive.archivable("FP-one")).isFalse();
    }

    @Test void requestBindsReviewToRecordedSourceAndRejectsDuplicate() {
        jdbc.update("UPDATE factory_plan_review SET state='WAITING',source_task_id=NULL,review_task_id=NULL WHERE plan_id='FP-one'");
        when(boundary.human()).thenReturn("owner");
        var attempt=new JdbcConfiguredPlanHistoryRepository.Attempt("attempt",1,"author","COMPLETED",
                Instant.EPOCH,Instant.EPOCH,null,"PASS","done",null,null,null,null,null);
        var source=new JdbcConfiguredPlanHistoryRepository.Task("source","COMPLETED","repo","work",Instant.EPOCH,List.of(attempt));
        when(history.read("FP-one")).thenReturn(new JdbcConfiguredPlanHistoryRepository.History("DELEGATED_COMPLETED",List.of(),List.of(source),List.of(),false));
        when(history.successful(source)).thenReturn(true);
        var attempts=mock(ExecutionAttemptRepository.class);
        when(attempts.findCompletedInput(new TaskId("source"))).thenReturn(Optional.of(new TaskInput(new TaskId("source"),TaskType.EXTERNAL_WORK,"repo",null,null,null,List.of(),CompletionVerdict.PASS,"done")));
        var output=mock(JdbcTaskOutputRepository.class); when(output.completed("source")).thenReturn(Optional.empty());
        var taskService=mock(WorkTaskService.class);
        ReflectionTestUtils.setField(lifecycle,"attempts",attempts);
        ReflectionTestUtils.setField(lifecycle,"taskOutputs",output);
        ReflectionTestUtils.setField(lifecycle,"taskService",taskService);
        when(tasks.findBySubject("flexible-plan","FP-one")).thenReturn(List.of());
        var result=lifecycle.requestReview("FP-one",3,"owner");
        assertThat(result.state()).isEqualTo("PENDING");
        assertThat(result.sourceTaskId()).isEqualTo("source");
        var command=org.mockito.ArgumentCaptor.forClass(CreateWorkTaskCommand.class);
        verify(taskService).createIfAbsent(command.capture());
        assertThat(command.getValue().dependencies()).containsExactly(new TaskId("source"));
        when(tasks.find(new TaskId(result.reviewTaskId()))).thenReturn(Optional.of(review));
        assertThatThrownBy(() -> lifecycle.requestReview("FP-one",3,"owner"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("already pending");
    }

    @Test void deliveryPreviewRejectsPendingReview() {
        var factory=mock(FactoryPlanLifecycle.class);
        when(factory.view("FP-one")).thenReturn(new FactoryPlanLifecycle.View(null,"owner",0));
        when(factory.reviewRequired("FP-one")).thenReturn(true);
        @SuppressWarnings("unchecked") ObjectProvider<FactoryPlanLifecycle> provider=mock(ObjectProvider.class);
        when(provider.getObject()).thenReturn(factory);
        @SuppressWarnings("unchecked") ObjectProvider<WorkflowPlanService> workflow=mock(ObjectProvider.class);
        var delivery=new WorkflowGitDeliveryService(plans,workflow,provider,tasks,mock(ExecutionAttemptRepository.class),
                mock(ManagedWorkspaceService.class),jdbc,boundary,mock(PlatformTransactionManager.class));
        assertThatThrownBy(() -> delivery.preview("FP-one","repo"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("review must pass");
    }

    private Worker worker(String id) {
        return new Worker(new WorkerId(id),"test",Set.of("workflow-review"),1,0,WorkerStatus.ACTIVE,Instant.EPOCH,Instant.EPOCH,Instant.EPOCH);
    }
}
