package me.asu.aidev.workflow.application;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import me.asu.aidev.authority.*;
import me.asu.aidev.execution.run.NativePlanOwners;
import me.asu.aidev.webconsole.security.WebConsoleSecurityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class WorkflowSchedulesTest {
    @TempDir Path directory;
    JdbcTemplate jdbc;
    WorkflowRuns workflows;
    WorkflowSchedules schedules;
    DataSourceTransactionManager manager;
    String id;
    long originalDue;
    @BeforeEach void setup() throws Exception {
        var source=new DriverManagerDataSource("jdbc:sqlite:"+directory.resolve("schedules.db"));
        jdbc=new JdbcTemplate(source);manager=new DataSourceTransactionManager(source);
        jdbc.execute("CREATE TABLE authority_owner(id TEXT PRIMARY KEY,actor TEXT)");
        jdbc.update("INSERT INTO authority_owner VALUES('owner','admin')");
        jdbc.execute("CREATE TABLE workflow_execution(id TEXT,state TEXT)");
        jdbc.execute("CREATE TABLE work_task(subject_id TEXT,lease_token TEXT)");
        try(var stream=getClass().getResourceAsStream("/db/migration/V51__workflow_schedules.sql")) {
            for(String sql:new String(Objects.requireNonNull(stream).readAllBytes(),StandardCharsets.UTF_8).split(";"))if(!sql.isBlank())jdbc.execute(sql);
        }
        var boundary=mock(AuthorityBoundary.class);when(boundary.human()).thenReturn("admin");
        var owners=mock(NativePlanOwners.class);when(owners.currentOwner()).thenReturn("owner");
        workflows=mock(WorkflowRuns.class);
        schedules=new WorkflowSchedules(new AuthorityRepository(jdbc),boundary,mock(AuthorityGrantService.class),owners,mock(WebConsoleSecurityService.class),workflows,manager);
        var spec=new WorkflowRuns.Spec("Task","Return text","task",List.of(
            new WorkflowRuns.Node("task","Task",WorkflowRuns.Kind.TASK,"Return text","end",Map.of(),null,WorkflowRuns.Assignment.GENERAL,List.of()),
            new WorkflowRuns.Node("end","End",WorkflowRuns.Kind.END,"",null,Map.of(),null,null,List.of())),List.of());
        id=UUID.randomUUID().toString();originalDue=System.currentTimeMillis()+120000;
        schedules.save(new WorkflowSchedules.Save(id,spec,"DAILY","Asia/Tokyo",originalDue),null);
    }
    @Test void missedOccurrenceWaitsForDecisionWithoutStartingWork() {
        jdbc.update("UPDATE workflow_schedule SET next_ms=? WHERE id=?",System.currentTimeMillis()-120000,id);
        schedules.tick();
        assertThat(jdbc.queryForObject("SELECT state FROM workflow_schedule",String.class)).isEqualTo("NEEDS_CONFIRMATION");
        verifyNoInteractions(workflows);
    }
    @Test void stoppedArrangementDoesNotStopOrStartTheCurrentExecution() {
        jdbc.update("INSERT INTO workflow_execution VALUES('old','RUNNING')");
        jdbc.update("UPDATE workflow_schedule SET last_execution='old' WHERE id=?",id);
        schedules.control(id,new WorkflowSchedules.Control(0,"stop",null),null);
        assertThat(jdbc.queryForObject("SELECT state FROM workflow_schedule",String.class)).isEqualTo("STOPPED");
        assertThat(jdbc.queryForObject("SELECT state FROM workflow_execution",String.class)).isEqualTo("RUNNING");
        verifyNoInteractions(workflows);
    }
    @Test void manualRunCannotReplayAnInterruptedExecutionWithRetainedLease() {
        jdbc.update("INSERT INTO workflow_execution VALUES('old','FAILED')");
        jdbc.update("INSERT INTO work_task VALUES('old','retained-lease')");
        jdbc.update("UPDATE workflow_schedule SET last_execution='old' WHERE id=?",id);
        assertThatThrownBy(()->new TransactionTemplate(manager).executeWithoutResult(status->schedules.control(id,new WorkflowSchedules.Control(0,"run-now",null),null))).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT next_ms FROM workflow_schedule",Long.class)).isEqualTo(originalDue);
        verifyNoInteractions(workflows);
    }
    @Test void calendarTimesUseTheSavedZoneAndDoNotRepeatDuringDstOverlap() {
        assertThat(WorkflowSchedules.next("DAILY","Asia/Tokyo","09:00",1,Instant.parse("2026-11-14T00:30:00Z").toEpochMilli())).isEqualTo(Instant.parse("2026-11-15T00:00:00Z").toEpochMilli());
        assertThat(WorkflowSchedules.next("WEEKLY","Asia/Tokyo","09:00",1,Instant.parse("2026-11-14T00:30:00Z").toEpochMilli())).isEqualTo(Instant.parse("2026-11-16T00:00:00Z").toEpochMilli());
        assertThat(WorkflowSchedules.next("DAILY","America/New_York","02:30",1,Instant.parse("2026-03-07T08:00:00Z").toEpochMilli())).isEqualTo(Instant.parse("2026-03-08T07:30:00Z").toEpochMilli());
        assertThat(WorkflowSchedules.next("DAILY","America/New_York","01:30",1,Instant.parse("2026-11-01T05:45:00Z").toEpochMilli())).isEqualTo(Instant.parse("2026-11-02T06:30:00Z").toEpochMilli());
    }
}
