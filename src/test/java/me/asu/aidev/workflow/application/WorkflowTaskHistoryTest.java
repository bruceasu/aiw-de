package me.asu.aidev.workflow.application;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import me.asu.aidev.authority.AuthorityBoundary;
import me.asu.aidev.authority.AuthorityRepository;
import me.asu.aidev.webconsole.security.WebConsoleSecurityService;
import me.asu.aidev.workflow.domain.WorkflowPlan;
import me.asu.aidev.workflow.jdbc.JdbcWorkflowPlanArchiveRepository;
import me.asu.aidev.workflow.port.WorkflowPlanRepository;
import me.asu.aidev.workflow.protocol.http.WorkflowConsole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.ui.ExtendedModelMap;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class WorkflowTaskHistoryTest {
    @TempDir Path directory;
    JdbcTemplate jdbc;
    AuthorityRepository db;
    WorkflowRuns workflows;
    WorkflowPlanService plans;
    WorkflowPlanRepository planStore;

    @BeforeEach void setup() {
        jdbc=new JdbcTemplate(new DriverManagerDataSource("jdbc:sqlite:"+directory.resolve("history.db")));
        jdbc.execute("CREATE TABLE workflow_execution(id TEXT PRIMARY KEY,definition_id TEXT,actor TEXT,state TEXT,run_json TEXT,version INTEGER,updated_ms INTEGER)");
        jdbc.execute("CREATE TABLE flexible_workflow_plan(id TEXT PRIMARY KEY,spec_json TEXT)");
        jdbc.execute("CREATE TABLE plan_execution_location(scope_id TEXT PRIMARY KEY,location TEXT)");
        jdbc.execute("CREATE TABLE web_console_plan_archive(plan_id TEXT PRIMARY KEY,delete_after_epoch_ms INTEGER,delete_requested_by TEXT,delete_requested_at_epoch_ms INTEGER)");
        jdbc.execute("CREATE TABLE work_task(id TEXT PRIMARY KEY,subject_id TEXT,subject_type TEXT,state TEXT,lease_token TEXT)");
        db=new AuthorityRepository(jdbc);
        var boundary=mock(AuthorityBoundary.class);
        when(boundary.human()).thenReturn("owner");
        plans=mock(WorkflowPlanService.class);
        planStore=mock(WorkflowPlanRepository.class);
        @SuppressWarnings("unchecked") ObjectProvider<WorkflowPlanService> provider=mock(ObjectProvider.class);
        when(provider.getObject()).thenReturn(plans);
        workflows=new WorkflowRuns(db,boundary,planStore,provider,null,null,null,null,null);
    }

    void task(String id,String actor,String state,long time,String title) {
        var run=new WorkflowRuns.Run();
        run.id=id;run.actor=actor;run.state=state;
        run.spec=new WorkflowRuns.Spec(title,"Generate report","task",java.util.List.of(
                new WorkflowRuns.Node("task","Task",WorkflowRuns.Kind.TASK,"Generate report","end",null,null,null,null),
                new WorkflowRuns.Node("end","Done",WorkflowRuns.Kind.END,"",null,null,null,null,null)),null);
        jdbc.update("INSERT INTO workflow_execution VALUES(?,NULL,?,?,?,0,?)",id,actor,state,db.encode(run),time);
        jdbc.update("INSERT INTO flexible_workflow_plan VALUES(?,?)",id,db.encode(Map.of("goal",title)));
        jdbc.update("INSERT INTO work_task VALUES(?,?,'flexible-plan',?,NULL)",id+"~task~work-1",id,
                state.equals("STOPPED")?"CANCELLED":state);
    }

    @Test void scopedSearchAndPagingIncludeOlderTasksAndSeparateArchiveViews() {
        for(int i=0;i<22;i++)task("latest-"+i,"owner","RUNNING",i+2,"Status update");
        task("older","owner","COMPLETED",1,"Release validation");
        task("archived","owner","COMPLETED",33,"Existing archive");
        task("deleting","owner","COMPLETED",34,"Pending removal");
        task("foreign","another-owner","COMPLETED",35,"Release validation");
        jdbc.update("INSERT INTO plan_execution_location VALUES('older','D:/Projects/Release_2026')");
        jdbc.update("INSERT INTO web_console_plan_archive(plan_id,delete_after_epoch_ms) VALUES('archived',NULL),('deleting',?)",
                System.currentTimeMillis()+60_000);
        var first=workflows.taskHistory("current","","",1);
        assertThat(first.tasks()).hasSize(20);
        assertThat(first.hasMore()).isTrue();
        assertThat(workflows.taskHistory("current","","",2).tasks()).hasSize(3);
        for(String search:java.util.List.of("release validation","older~task~work-1","Release_2026")) {
            assertThat(workflows.taskHistory("current","COMPLETED",search,1).tasks())
                    .extracting(row->row.get("id")).containsExactly("older");
        }
        assertThat(workflows.taskHistory("current","","%",1).tasks()).isEmpty();
        assertThat(workflows.taskHistory("archived","","",1).tasks()).extracting(row->row.get("id")).containsExactly("archived");
        assertThat(workflows.taskHistory("deleting","","",1).tasks()).extracting(row->row.get("id")).containsExactly("deleting");
    }

    @Test void expiredSimpleTaskDisappearsFromListsAndDetailsWhilePurgeKeepsRecords() {
        var now=Instant.now();
        task("expired","owner","FAILED",1,"Expired task");
        task("future","owner","COMPLETED",2,"Pending task");
        task("workflow","owner","COMPLETED",3,"Reusable workflow");
        jdbc.update("UPDATE workflow_execution SET definition_id='workflow-1' WHERE id='workflow'");
        jdbc.update("INSERT INTO work_task VALUES('continuation','expired','flexible-plan','CONTINUED',NULL)");
        jdbc.update("""
                INSERT INTO web_console_plan_archive(plan_id,delete_after_epoch_ms) VALUES
                ('expired',?),('future',?),('workflow',?)
                """,now.toEpochMilli(),now.plusSeconds(60).toEpochMilli(),now.toEpochMilli());

        assertThat(workflows.taskHistory("deleting","","",1).tasks()).extracting(row->row.get("id")).containsExactly("future");
        assertThat(workflows.taskHistory("deleting","","Expired task",1).tasks()).isEmpty();
        assertThat(workflows.executions("tasks")).extracting(row->row.get("id")).containsExactly("future");
        assertThat(workflows.executions("all")).extracting(row->row.get("id")).containsExactly("workflow","future");
        assertThatThrownBy(()->workflows.view("expired")).isInstanceOf(java.util.NoSuchElementException.class);

        var security=mock(WebConsoleSecurityService.class);
        var session=new MockHttpSession();
        when(security.actor(session)).thenReturn("owner");
        var console=new WorkflowConsole(workflows,security);
        assertThatThrownBy(()->console.taskDetail("expired",new ExtendedModelMap(),session))
                .isInstanceOf(java.util.NoSuchElementException.class);
        assertThatThrownBy(()->console.tasks("expired",new ExtendedModelMap(),session))
                .isInstanceOf(java.util.NoSuchElementException.class);
        assertThat(console.taskDetail("future",new ExtendedModelMap(),session)).isEqualTo("ui/workflows");
        assertThat(console.missing().getStatusCode().value()).isEqualTo(404);

        var archives=new JdbcWorkflowPlanArchiveRepository(jdbc);
        assertThat(archives.dueIds(now)).containsExactly("workflow");
        assertThat(archives.purge("expired",now)).isFalse();
        assertThat(archives.undoDeletion("expired",now)).isFalse();
        assertThat(archives.undoDeletion("future",now)).isTrue();
        assertThat(workflows.taskHistory("archived","","",1).tasks()).extracting(row->row.get("id")).containsExactly("future");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM work_task WHERE subject_id='expired'",Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM flexible_workflow_plan WHERE id='expired'",Integer.class)).isEqualTo(1);
        assertThat(archives.deleteAfter("expired")).contains(Instant.ofEpochMilli(now.toEpochMilli()));
    }

    @Test void onlyFinishedOwnedTasksWithoutLeasesOrUnfinishedWorkReachArchiveService() {
        task("mine","owner","COMPLETED",1,"Done");
        task("pending","owner","RUNNING",2,"Pending");
        task("foreign","another-owner","COMPLETED",3,"Foreign");
        task("failed","owner","FAILED",4,"Failed");
        task("stopped","owner","STOPPED",5,"Stopped");
        task("leased","owner","FAILED",6,"Leased");
        task("unfinished","owner","FAILED",7,"Unfinished");
        jdbc.update("UPDATE work_task SET lease_token='lease' WHERE subject_id='leased'");
        jdbc.update("INSERT INTO work_task VALUES('replacement','unfinished','flexible-plan','READY',NULL)");
        jdbc.update("INSERT INTO work_task VALUES('continuation','failed','flexible-plan','CONTINUED',NULL)");
        var service=mock(WorkflowPlanArchiveService.class);
        ObjectProvider<?> provider=mock(ObjectProvider.class);
        doReturn(service).when(provider).getObject();
        ReflectionTestUtils.setField(workflows,"archives",provider);
        assertThat(workflows.taskHistory("current","","",1).tasks())
                .filteredOn(row->Boolean.TRUE.equals(row.get("manageable")))
                .extracting(row->row.get("id")).containsExactly("stopped","failed","mine");
        for(String id:java.util.List.of("pending","leased","unfinished")) {
            assertThatThrownBy(()->workflows.manageFinishedTask(id,"archive")).isInstanceOf(IllegalStateException.class);
        }
        assertThatThrownBy(()->workflows.manageFinishedTask("foreign","schedule-delete")).isInstanceOf(java.util.NoSuchElementException.class);
        verifyNoInteractions(service);
        when(plans.get("failed")).thenReturn(new WorkflowPlan("failed",WorkflowPlan.State.CONFIRMED,2,null,
                "owner","owner",null,null,null));
        when(planStore.transition(eq("failed"),eq(2L),eq(WorkflowPlan.State.CONFIRMED),eq(WorkflowPlan.State.STOPPED),
                eq("owner"),eq("Workflow execution FAILED"),any())).thenReturn(true);
        workflows.manageFinishedTask("mine","schedule-delete");
        workflows.manageFinishedTask("failed","schedule-delete");
        workflows.manageFinishedTask("stopped","archive");
        workflows.manageFinishedTask("failed","undo-delete");
        var order=inOrder(service,planStore);
        order.verify(service).archive("mine","owner");
        order.verify(service).scheduleDeletion("mine","owner");
        order.verify(planStore).transition(eq("failed"),eq(2L),eq(WorkflowPlan.State.CONFIRMED),eq(WorkflowPlan.State.STOPPED),
                eq("owner"),eq("Workflow execution FAILED"),any());
        order.verify(service).archive("failed","owner");
        order.verify(service).scheduleDeletion("failed","owner");
        order.verify(service).archive("stopped","owner");
        order.verify(service).undoDeletion("failed");
        when(service.archived("failed")).thenReturn(true);
        assertThatThrownBy(()->workflows.control("failed",0,"retry",null))
                .isInstanceOf(IllegalStateException.class).hasMessage("Archived tasks cannot be changed");
    }
}
