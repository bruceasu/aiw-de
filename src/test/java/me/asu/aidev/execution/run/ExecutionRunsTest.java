package me.asu.aidev.execution.run;

import java.nio.file.Path;
import java.util.*;
import me.asu.aidev.authority.*;
import me.asu.aidev.execution.application.TaskExecutionService;
import me.asu.aidev.execution.config.*;
import me.asu.aidev.execution.guard.*;
import me.asu.aidev.work.domain.TaskId;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static me.asu.aidev.execution.run.RunContract.*;

class ExecutionRunsTest {
    @TempDir Path temp;
    JdbcTemplate jdbc;AuthorityRepository repo;AuthorityBoundary boundary;
    ExecutionGuard guard;ExecutionWorkbench workbench;TaskExecutionService tasks;ExecutionRuns runs;
    GuardContract.Lease lease=new GuardContract.Lease("worker","attempt","lease");

    @BeforeEach void setup() {
        var datasource=new DriverManagerDataSource("jdbc:sqlite:"+temp.resolve("runs.db"));
        jdbc=new JdbcTemplate(datasource);
        jdbc.execute("CREATE TABLE authority_lock(id INTEGER,version INTEGER)");jdbc.update("INSERT INTO authority_lock VALUES(1,0)");
        jdbc.execute("CREATE TABLE authority_owner(id TEXT PRIMARY KEY,actor TEXT)");jdbc.update("INSERT INTO authority_owner VALUES('owner','human')");
        jdbc.execute("CREATE TABLE work_task(id TEXT PRIMARY KEY,state TEXT,lease_token TEXT,lease_expires_at_epoch_ms BIGINT,instruction_revision INTEGER)");
        jdbc.update("INSERT INTO work_task VALUES('task','RUNNING','lease',?,1)",System.currentTimeMillis()+60000);
        jdbc.execute("CREATE TABLE execution_attempt(id TEXT PRIMARY KEY,task_id TEXT,state TEXT,lease_token TEXT,stop_requested_at_epoch_ms BIGINT,stop_acknowledged_at_epoch_ms BIGINT,instruction_revision INTEGER,finished_at_epoch_ms BIGINT)");
        jdbc.update("INSERT INTO execution_attempt VALUES('attempt','task','RUNNING','lease',NULL,NULL,1,NULL)");
        jdbc.execute("CREATE TABLE execution_operation(attempt_id TEXT,state TEXT)");
        jdbc.execute("CREATE TABLE execution_workbench_session(attempt_id TEXT)");
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V28__worker_execution_runs.sql")).execute(datasource);
        repo=new AuthorityRepository(jdbc);boundary=new AuthorityBoundary(repo);boundary.human("human");
        guard=mock(ExecutionGuard.class);workbench=mock(ExecutionWorkbench.class);tasks=mock(TaskExecutionService.class);
        when(guard.managed("task")).thenReturn(true);
        when(guard.context("task")).thenReturn(new ExecutionGuard.Context("guard","instance","task","plan","owner",1,"config","input",null,null,null,List.of()));
        @SuppressWarnings("unchecked") ObjectProvider<TaskExecutionService> provider=mock(ObjectProvider.class);when(provider.getObject()).thenReturn(tasks);
        runs=new ExecutionRuns(repo,boundary,guard,workbench,provider);
        jdbc.update("INSERT INTO execution_run_session VALUES('attempt','task','owner',1,?,'digest','RUNNING',1,0,0,?)",repo.encode(caps()),System.currentTimeMillis());
    }
    Map<String,String> caps() {
        return Map.of("requestLoopOwner","HOST","toolExecutor","HOST","contextVisibility","WORKER_PROJECTION","dispatchBoundary","TOOL",
            "supplementBoundary","NONE","sessionReuse","NONE","pauseBoundary","TOOL","cancelEvidence","BEST_EFFORT","usageScope","UNKNOWN","requestAccountingScope","BACKEND_TURN");
    }
    Event event(long sequence,int bytes) {
        Map<String,Object> payload=Map.of("inputBytes",bytes,"usageKnown",false);
        return new Event(sequence,"context-projected",1000,payload,AuthorityRepository.hash(repo.encode(payload)));
    }
    String phase() { return jdbc.queryForObject("SELECT phase FROM execution_run_session",String.class); }
    @Test void contiguousAcknowledgementsNeverSkipGapsAndReplaysDoNotAppend() {
        runs.events("task",new Events(lease,List.of(event(1,10),event(3,30))));
        assertThat(jdbc.queryForObject("SELECT ack_sequence FROM execution_run_session",Long.class)).isEqualTo(1);
        runs.events("task",new Events(lease,List.of(event(1,10),event(2,20),event(3,30))));
        assertThat(jdbc.queryForObject("SELECT ack_sequence FROM execution_run_session",Long.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM execution_run_event",Long.class)).isEqualTo(3);
        verify(guard,times(2)).verifyLease("task",lease,false,true);
    }
    @Test void sameSequenceDifferentContentClosesTheSessionWithoutOverwritingEvidence() {
        runs.events("task",new Events(lease,List.of(event(1,10))));
        runs.events("task",new Events(lease,List.of(event(1,11))));
        assertThat(phase()).isEqualTo("BROKEN");
        assertThat(jdbc.queryForObject("SELECT payload_json FROM execution_run_event",String.class)).contains("10");
        assertThatThrownBy(()->runs.events("task",new Events(lease,List.of(event(2,20))))).isInstanceOf(IllegalStateException.class);
    }
    @Test void foreignOwnerCannotReadOrControlAnAttempt() {
        jdbc.update("UPDATE execution_run_session SET owner_id='foreign'");
        assertThatThrownBy(()->runs.read(new Query("task","attempt",0))).isInstanceOf(SecurityException.class);
        assertThatThrownBy(()->runs.command(new Command("pause","task","attempt",1,1,"PAUSE"))).isInstanceOf(SecurityException.class);
        verifyNoInteractions(tasks);
    }
    @Test void unknownOrActiveOperationsPreventAppliedPauseReceipts() {
        runs.command(new Command("pause","task","attempt",1,1,"PAUSE"));
        runs.poll("task",new Poll(lease,List.of(new Receipt(1,"RECEIVED"))));
        jdbc.update("INSERT INTO execution_operation VALUES('attempt','UNKNOWN')");
        assertThatThrownBy(()->runs.poll("task",new Poll(lease,List.of(new Receipt(1,"APPLIED"))))).isInstanceOf(IllegalStateException.class);
        assertThat(phase()).isEqualTo("PAUSING");
        jdbc.update("DELETE FROM execution_operation");
        runs.poll("task",new Poll(lease,List.of(new Receipt(1,"APPLIED"))));
        assertThat(phase()).isEqualTo("PAUSED");
    }
    @Test void cancellationUsesAuthoritativeStopBeforeRecordingAcceptanceAndReplaysOnlyOnce() {
        doAnswer(call->{
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM execution_run_command",Long.class)).isZero();
            jdbc.update("UPDATE execution_attempt SET stop_requested_at_epoch_ms=1");return null;
        }).when(tasks).requestStop(eq(new TaskId("task")),eq("human"),anyString());
        var input=new Command("cancel","task","attempt",1,1,"CANCEL");
        runs.command(input);runs.command(input);
        verify(tasks).requestStop(eq(new TaskId("task")),eq("human"),anyString());assertThat(phase()).isEqualTo("STOPPING");
        assertThatThrownBy(()->runs.command(new Command("cancel","task","attempt",1,1,"PAUSE"))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(()->runs.completing("task",lease)).isInstanceOf(IllegalStateException.class);
    }
    @Test void pendingPauseOrNewInputPreventsFinalization() {
        runs.command(new Command("pause","task","attempt",1,1,"PAUSE"));
        runs.seal("task",new Seal(lease,2,0,"boundary"));assertThat(phase()).isEqualTo("PAUSING");
        jdbc.update("UPDATE execution_run_session SET phase='RUNNING'");
        jdbc.update("INSERT INTO execution_workbench_session VALUES('attempt')");
        var inputBatch=new WorkbenchContract.Batch(WorkbenchContract.PROTOCOL,"RUNNING","boundary","digest","RESERVED",List.of());
        when(workbench.machine(eq("task"),any())).thenReturn(inputBatch);
        @SuppressWarnings("unchecked") var response=(Map<String,Object>)runs.seal("task",new Seal(lease,2,0,"boundary"));
        assertThat(response.get("batch")).isEqualTo(inputBatch);assertThat(phase()).isEqualTo("RUNNING");
        verify(workbench).machine("task",new WorkbenchContract.MachineCommand(lease,"DRAIN","boundary",null,null,null));
    }
    @Test void rawContentAndUnorderedEventsAreRejected() {
        assertThatThrownBy(()->metadata(Map.of("prompt","private"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->metadata(Map.of("inputBytes",1.2))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->runs.events("task",new Events(lease,List.of(event(2,20),event(1,10))))).isInstanceOf(IllegalArgumentException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM execution_run_event",Long.class)).isZero();
    }
    void nativeRun() {
        when(guard.managed("task")).thenReturn(false);
        var owners=mock(NativePlanOwners.class);
        when(owners.task(eq("task"),anyBoolean())).thenReturn(new NativePlanOwners.Basis("owner","plan",1,"a".repeat(64),null));
        var lifecycle=new NativeRunLifecycle(repo,boundary,guard,mock(me.asu.aidev.work.port.WorkTaskRepository.class),mock(me.asu.aidev.worker.port.WorkerRepository.class),List.of());
        org.springframework.test.util.ReflectionTestUtils.setField(runs,"nativeOwners",owners);
        org.springframework.test.util.ReflectionTestUtils.setField(runs,"nativeLifecycle",lifecycle);
        jdbc.execute("CREATE TABLE execution_stop_evidence(attempt_id TEXT)");
    }
    void nativeEvent(long sequence,String event,Map<String,Object> payload) {
        runs.events("task",new Events(lease,List.of(new Event(sequence,event,1000,payload,AuthorityRepository.hash(repo.encode(payload))))));
    }
    @Test void nativePauseNeedsRecordedQuiescenceAndANewTurnInvalidatesIt() {
        nativeRun();runs.command(new Command("pause","task","attempt",1,1,"PAUSE"));
        runs.poll("task",new Poll(lease,List.of(new Receipt(1,"RECEIVED"))));
        assertThatThrownBy(()->runs.poll("task",new Poll(lease,List.of(new Receipt(1,"APPLIED"))))).isInstanceOf(IllegalStateException.class);
        nativeEvent(1,"execution-quiescent",Map.of("count",0,"complete",true));
        runs.poll("task",new Poll(lease,List.of(new Receipt(1,"APPLIED"))));assertThat(phase()).isEqualTo("PAUSED");
        runs.command(new Command("resume","task","attempt",1,3,"RESUME"));
        runs.poll("task",new Poll(lease,List.of(new Receipt(2,"RECEIVED"))));
        nativeEvent(2,"native-turn-started",Map.of("status","running"));
        assertThatThrownBy(()->runs.poll("task",new Poll(lease,List.of(new Receipt(2,"APPLIED"))))).isInstanceOf(IllegalStateException.class);
    }
    @Test void nativeFinalizationRequiresActualRecordedQuiescence() {
        nativeRun();assertThatThrownBy(()->runs.seal("task",new Seal(lease,1,0,"boundary"))).isInstanceOf(IllegalStateException.class);
        nativeEvent(1,"execution-quiescent",Map.of("count",0,"complete",true));
        runs.seal("task",new Seal(lease,1,1,"boundary"));assertThat(phase()).isEqualTo("FINALIZING");
    }
}
