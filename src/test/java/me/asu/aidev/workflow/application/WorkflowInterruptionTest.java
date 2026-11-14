package me.asu.aidev.workflow.application;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import me.asu.aidev.authority.AuthorityBoundary;
import me.asu.aidev.authority.AuthorityRepository;
import me.asu.aidev.execution.domain.CompletionReport;
import me.asu.aidev.execution.domain.CompletionVerdict;
import me.asu.aidev.execution.port.ExecutionAttemptRepository;
import me.asu.aidev.work.domain.*;
import me.asu.aidev.work.port.WorkTaskRepository;
import me.asu.aidev.workflow.domain.WorkflowPlan;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class WorkflowInterruptionTest {
    @TempDir Path directory;
    JdbcTemplate jdbc;
    AuthorityRepository db;
    WorkTaskRepository tasks;
    ExecutionAttemptRepository attempts;
    WorkflowRuns workflows;
    WorkflowRuns.Run run;
    WorkTask task;

    @BeforeEach @SuppressWarnings("unchecked") void setup() {
        jdbc=new JdbcTemplate(new DriverManagerDataSource("jdbc:sqlite:"+directory.resolve("workflow.db")));
        jdbc.execute("CREATE TABLE workflow_execution(id TEXT PRIMARY KEY,actor TEXT,version INTEGER,state TEXT,run_json TEXT,updated_ms INTEGER)");
        db=new AuthorityRepository(jdbc);tasks=mock(WorkTaskRepository.class);attempts=mock(ExecutionAttemptRepository.class);
        var boundary=mock(AuthorityBoundary.class);when(boundary.human()).thenReturn("owner");
        var plans=mock(WorkflowPlanService.class);
        ObjectProvider<WorkflowPlanService> provider=mock(ObjectProvider.class);when(provider.getObject()).thenReturn(plans);
        workflows=new WorkflowRuns(db,boundary,null,provider,null,null,tasks,attempts,null);
        task=task("plan~work~work-1",TaskState.RUNNING);
        when(tasks.find(task.id())).thenReturn(Optional.of(task));when(plans.effectiveSteps("plan")).thenReturn(List.of(task));
        var plan=mock(WorkflowPlan.class);when(plan.state()).thenReturn(WorkflowPlan.State.COMPLETED);when(plans.get("plan")).thenReturn(plan);
        run=new WorkflowRuns.Run();run.id="plan";run.actor="owner";run.state="RUNNING";run.current="work";run.taskId=task.id().value();run.context="";
        run.spec=new WorkflowRuns.Spec("Task","Produce the requested result","work",List.of(
            new WorkflowRuns.Node("work","Work",WorkflowRuns.Kind.TASK,"Return the actual result","end",null,null,null,List.of()),
            new WorkflowRuns.Node("end","End",WorkflowRuns.Kind.END,"",null,null,null,null,List.of())),List.of());
        save();
    }
    WorkTask task(String id,TaskState state) {
        return new WorkTask(new TaskId(id),TaskType.EXTERNAL_WORK,state,"flexible-plan","plan",Set.of("general-work"),"general-work","Do the work",1,null,null,TaskPriority.NORMAL,0,Instant.now(),Instant.now());
    }
    void save() {jdbc.update("INSERT OR REPLACE INTO workflow_execution VALUES(?,?,?,?,?,?)",run.id,run.actor,run.version,run.state,db.encode(run),0);}
    WorkflowRuns.Run saved() {return db.decode(jdbc.queryForObject("SELECT run_json FROM workflow_execution",String.class),WorkflowRuns.Run.class);}

    @Test void expiredExecutionIsParkedOnceWithoutReleasingOrReplaying() {
        assertThat(workflows.canClaim(task)).isTrue();
        assertThat(workflows.interrupted(task.id(),"Lease expired; inspect the original process")).isTrue();
        assertThat(workflows.canClaim(task)).isFalse();
        workflows.interrupted(task.id(),"Lease expired again");
        assertThat(saved().events).hasSize(1);assertThat(saved().state).isEqualTo("FAILED");
        verify(tasks,never()).retryFailed(any(),any());verify(tasks,never()).cancelInactive(any(),any(),any());
    }
    @Test void pausedDispatchCanContinueButStoppedAndObsoleteTasksCannotClaim() {
        run.state="PAUSED";save();assertThat(workflows.canClaim(task)).isTrue();
        assertThat(workflows.canClaim(task("old-task",TaskState.READY))).isFalse();
        run.state="STOPPING";save();assertThat(workflows.canClaim(task)).isFalse();
    }
    @Test void lateSuccessfulResultCanBeExplicitlyAccepted() {
        run.state="FAILED";save();
        var completed=task(task.id().value(),TaskState.COMPLETED);
        when(tasks.find(task.id())).thenReturn(Optional.of(completed));
        when(attempts.findCompletionReport(task.id())).thenReturn(Optional.of(new CompletionReport(CompletionVerdict.PASS,"Actual result")));
        var result=workflows.control("plan",0,"accept-result",null);
        assertThat(result.state).isEqualTo("COMPLETED");assertThat(result.context).contains("Actual result");
        verify(tasks,never()).retryFailed(any(),any());
    }
    @Test void unfinishedResultCannotBeAccepted() {
        run.state="FAILED";save();
        assertThatThrownBy(()->workflows.control("plan",0,"accept-result",null)).isInstanceOf(IllegalStateException.class);
        assertThat(saved().state).isEqualTo("FAILED");
    }
}
