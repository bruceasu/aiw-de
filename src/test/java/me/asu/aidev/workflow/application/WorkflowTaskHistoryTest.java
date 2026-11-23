package me.asu.aidev.workflow.application;

import java.nio.file.Path;
import java.util.Map;
import me.asu.aidev.authority.AuthorityBoundary;
import me.asu.aidev.authority.AuthorityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class WorkflowTaskHistoryTest {
    @TempDir Path directory;
    JdbcTemplate jdbc;
    AuthorityRepository db;
    WorkflowRuns workflows;

    @BeforeEach void setup() {
        jdbc=new JdbcTemplate(new DriverManagerDataSource("jdbc:sqlite:"+directory.resolve("history.db")));
        jdbc.execute("CREATE TABLE workflow_execution(id TEXT PRIMARY KEY,definition_id TEXT,actor TEXT,state TEXT,run_json TEXT,version INTEGER,updated_ms INTEGER)");
        jdbc.execute("CREATE TABLE flexible_workflow_plan(id TEXT PRIMARY KEY,spec_json TEXT)");
        jdbc.execute("CREATE TABLE plan_execution_location(scope_id TEXT PRIMARY KEY,location TEXT)");
        jdbc.execute("CREATE TABLE web_console_plan_archive(plan_id TEXT PRIMARY KEY,delete_after_epoch_ms INTEGER)");
        jdbc.execute("CREATE TABLE work_task(id TEXT PRIMARY KEY,subject_id TEXT,subject_type TEXT)");
        db=new AuthorityRepository(jdbc);
        var boundary=mock(AuthorityBoundary.class);
        when(boundary.human()).thenReturn("owner");
        workflows=new WorkflowRuns(db,boundary,null,null,null,null,null,null,null);
    }

    void task(String id,String actor,String state,long time,String title) {
        var run=new WorkflowRuns.Run();
        run.id=id;run.actor=actor;run.state=state;
        run.spec=new WorkflowRuns.Spec(title,"Generate report","task",java.util.List.of(
                new WorkflowRuns.Node("task","Task",WorkflowRuns.Kind.TASK,"Generate report","end",null,null,null,null),
                new WorkflowRuns.Node("end","Done",WorkflowRuns.Kind.END,"",null,null,null,null,null)),null);
        jdbc.update("INSERT INTO workflow_execution VALUES(?,NULL,?,?,?,0,?)",id,actor,state,db.encode(run),time);
        jdbc.update("INSERT INTO flexible_workflow_plan VALUES(?,?)",id,db.encode(Map.of("goal",title)));
        jdbc.update("INSERT INTO work_task VALUES(?,?,'flexible-plan')",id+"~task~work-1",id);
    }

    @Test void scopedSearchAndPagingIncludeOlderTasksAndSeparateArchiveViews() {
        for(int i=0;i<22;i++)task("latest-"+i,"owner","RUNNING",i+2,"Status update");
        task("older","owner","COMPLETED",1,"Release validation");
        task("archived","owner","COMPLETED",33,"Existing archive");
        task("deleting","owner","COMPLETED",34,"Pending removal");
        task("foreign","another-owner","COMPLETED",35,"Release validation");
        jdbc.update("INSERT INTO plan_execution_location VALUES('older','D:/Projects/Release_2026')");
        jdbc.update("INSERT INTO web_console_plan_archive VALUES('archived',NULL),('deleting',999999)");
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

    @Test void onlyCompletedOwnedTasksReachArchiveService() {
        task("mine","owner","COMPLETED",1,"Done");
        task("pending","owner","RUNNING",2,"Pending");
        task("foreign","another-owner","COMPLETED",3,"Foreign");
        var service=mock(WorkflowPlanArchiveService.class);
        ObjectProvider<?> provider=mock(ObjectProvider.class);
        doReturn(service).when(provider).getObject();
        ReflectionTestUtils.setField(workflows,"archives",provider);
        assertThatThrownBy(()->workflows.manageCompletedTask("pending","archive")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(()->workflows.manageCompletedTask("foreign","schedule-delete")).isInstanceOf(java.util.NoSuchElementException.class);
        verifyNoInteractions(service);
        workflows.manageCompletedTask("mine","schedule-delete");
        var order=inOrder(service);
        order.verify(service).archive("mine","owner");
        order.verify(service).scheduleDeletion("mine","owner");
    }
}
