package me.asu.aidev.workflow.application;

import java.util.List;
import java.util.Set;
import java.util.Optional;
import java.time.Instant;
import java.nio.file.Path;
import com.fasterxml.jackson.databind.ObjectMapper;
import me.asu.aidev.workflow.domain.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import me.asu.aidev.workflow.port.WorkflowPlanRepository;
import me.asu.aidev.work.domain.*;
import me.asu.aidev.worker.domain.*;
import static me.asu.aidev.workflow.domain.WorkflowPlanSpec.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class WorkflowReviewPolicyTest {
    @TempDir Path temp;
    static Step step(String key,ReviewMode mode,ReviewRisk risk) {
        return new Step(key,Set.of("coding"),Set.of(),"coding-work-package-executor","Acceptance for "+key,null,null,Set.of(),null,null,mode,risk);
    }
    @Test void highAndUnknownRiskCannotChooseSelfOrMilestoneAcceptance() {
        for(var risk:List.of(ReviewRisk.HIGH,ReviewRisk.UNKNOWN))for(var mode:List.of(ReviewMode.SELF,ReviewMode.MILESTONE))
            assertThatThrownBy(()->step("code",mode,risk)).isInstanceOf(IllegalArgumentException.class);
        assertThatCode(()->step("code",ReviewMode.INDEPENDENT,ReviewRisk.HIGH)).doesNotThrowAnyException();
    }
    @Test void missingPersistedFieldsKeepConservativeIndependentReview() throws Exception {
        var legacy=new ObjectMapper().readValue("""
                {"key":"code","requiredSkills":["coding"],"preferredSkills":[],"skillRef":"coding-work-package-executor",
                 "instruction":"Implement acceptance","dependsOn":[]}
                """,Step.class);
        assertThat(legacy.effectiveReviewMode()).isEqualTo(ReviewMode.INDEPENDENT);
        assertThat(legacy.effectiveReviewRisk()).isEqualTo(ReviewRisk.UNKNOWN);
        assertThat(new ObjectMapper().writeValueAsString(legacy)).doesNotContain("reviewMode","reviewRisk");
    }
    @Test void explicitPolicySurvivesPersistenceAndCannotBeChangedThroughTheRecord() throws Exception {
        var original=step("code",ReviewMode.SELF,ReviewRisk.LOW);var json=new ObjectMapper();
        assertThat(json.readValue(json.writeValueAsString(original),Step.class)).isEqualTo(original);
    }
    @Test void milestoneCoversOnlyTheOrdinaryStageSinceTheLastReviewBoundary() {
        var spec=new WorkflowPlanSpec("Goal",Source.MANAGER,List.of(
                step("a",ReviewMode.INDEPENDENT,ReviewRisk.HIGH),step("b",ReviewMode.SELF,ReviewRisk.LOW),
                step("c",ReviewMode.SELF,ReviewRisk.LOW),step("d",ReviewMode.MILESTONE,ReviewRisk.LOW),
                step("e",ReviewMode.INDEPENDENT,ReviewRisk.UNKNOWN),step("f",ReviewMode.MILESTONE,ReviewRisk.LOW)));
        assertThat(WorkflowReviewPolicy.scope(spec,3)).containsExactly(1,2,3);
        assertThat(WorkflowReviewPolicy.scope(spec,4)).containsExactly(4);
        assertThat(WorkflowReviewPolicy.scope(spec,5)).containsExactly(5);
    }
    @Test void milestoneReviewerCannotHaveExecutedAnyCoveredOrdinaryStep() {
        var jdbc=new JdbcTemplate(new DriverManagerDataSource("jdbc:sqlite:"+temp.resolve("independence.db")));
        jdbc.execute("CREATE TABLE work_task(id TEXT,subject_type TEXT,subject_id TEXT)");
        jdbc.execute("CREATE TABLE execution_attempt(task_id TEXT,worker_id TEXT)");
        jdbc.update("INSERT INTO work_task VALUES('plan~a~work-1','flexible-plan','plan')");
        jdbc.update("INSERT INTO execution_attempt VALUES('plan~a~work-1','participant')");
        var spec=new WorkflowPlanSpec("Goal",Source.MANAGER,List.of(step("a",ReviewMode.SELF,ReviewRisk.LOW),step("b",ReviewMode.MILESTONE,ReviewRisk.LOW)));
        var plan=new WorkflowPlan("plan",WorkflowPlan.State.CONFIRMED,1,spec,"human","human","",Instant.EPOCH,Instant.EPOCH);
        var plans=mock(WorkflowPlanRepository.class);when(plans.find("plan")).thenReturn(Optional.of(plan));
        var policy=new WorkflowReviewClaimPolicy(jdbc,plans);var review=mock(WorkTask.class);
        when(review.id()).thenReturn(new TaskId("plan~b~review-1"));when(review.subjectType()).thenReturn("flexible-plan");when(review.subjectId()).thenReturn("plan");when(review.skillRef()).thenReturn("workflow-stage-reviewer");
        var worker=mock(Worker.class);when(worker.id()).thenReturn(new WorkerId("participant"));
        assertThat(policy.canClaim(review,worker)).isFalse();assertThat(policy.canClaim(review)).isFalse();
        when(worker.id()).thenReturn(new WorkerId("independent"));assertThat(policy.canClaim(review,worker)).isTrue();
        var later=new WorkflowPlan("plan",WorkflowPlan.State.CONFIRMED,1,new WorkflowPlanSpec("Goal",Source.MANAGER,List.of(
                step("a",ReviewMode.MILESTONE,ReviewRisk.LOW),step("b",ReviewMode.MILESTONE,ReviewRisk.LOW))),"human","human","",Instant.EPOCH,Instant.EPOCH);
        when(plans.find("plan")).thenReturn(Optional.of(later));when(worker.id()).thenReturn(new WorkerId("participant"));
        assertThat(policy.canClaim(review,worker)).isTrue();
    }
}
