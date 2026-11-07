package me.asu.aidev.execution.run;

import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import me.asu.aidev.authority.*;
import me.asu.aidev.execution.guard.*;
import me.asu.aidev.execution.port.TaskConcurrencyBudget;
import me.asu.aidev.work.domain.*;
import me.asu.aidev.work.port.WorkTaskRepository;
import me.asu.aidev.worker.domain.WorkerId;
import me.asu.aidev.worker.port.WorkerRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class NativeRunLifecycleTest {
    @TempDir Path temp;
    JdbcTemplate jdbc;NativeRunLifecycle lifecycle;WorkerRepository workers;TaskConcurrencyBudget budget;
    GuardContract.Lease lease=new GuardContract.Lease("worker","attempt","lease");
    @BeforeEach void setup() {
        jdbc=new JdbcTemplate(new DriverManagerDataSource("jdbc:sqlite:"+temp.resolve("yield.db")));
        jdbc.execute("CREATE TABLE work_task(id TEXT PRIMARY KEY,state TEXT,current_worker_id TEXT,lease_token TEXT,lease_acquired_at_epoch_ms BIGINT,lease_expires_at_epoch_ms BIGINT,lease_heartbeat_at_epoch_ms BIGINT,version BIGINT,updated_at_epoch_ms BIGINT)");
        jdbc.update("INSERT INTO work_task VALUES('task','YIELDING','worker','lease',1,9999999999999,1,1,1)");
        jdbc.execute("CREATE TABLE execution_attempt(id TEXT PRIMARY KEY,task_id TEXT,worker_id TEXT,lease_token TEXT,state TEXT,stop_requested_at_epoch_ms BIGINT,stop_acknowledged_at_epoch_ms BIGINT,finished_at_epoch_ms BIGINT)");
        jdbc.update("INSERT INTO execution_attempt VALUES('attempt','task','worker','lease','RUNNING',NULL,NULL,NULL)");
        jdbc.execute("CREATE TABLE execution_run_session(attempt_id TEXT,task_id TEXT,phase TEXT,revision BIGINT,last_contact_ms BIGINT)");
        jdbc.update("INSERT INTO execution_run_session VALUES('attempt','task','YIELDING',1,1)");
        jdbc.execute("CREATE TABLE execution_run_event(attempt_id TEXT,event_type TEXT,payload_json TEXT,sequence_no BIGINT)");
        jdbc.execute("CREATE TABLE execution_stop_evidence(attempt_id TEXT)");
        jdbc.execute("CREATE TABLE execution_run_command(attempt_id TEXT,action TEXT,status TEXT,updated_ms BIGINT)");
        jdbc.execute("ALTER TABLE work_task ADD COLUMN subject_id TEXT");jdbc.update("UPDATE work_task SET subject_id='plan'");
        jdbc.execute("CREATE TABLE flexible_workflow_control(plan_id TEXT,task_id TEXT,status TEXT)");
        var repo=new AuthorityRepository(jdbc);var tasks=mock(WorkTaskRepository.class);workers=mock(WorkerRepository.class);budget=mock(TaskConcurrencyBudget.class);
        var source=new WorkTask(new TaskId("task"),TaskType.IMPLEMENTATION,TaskState.YIELDING,"flexible-plan","plan",Set.of(),null,"Original goal",1,null,null,TaskPriority.NORMAL,1,Instant.now(),Instant.now());
        when(tasks.find(source.id())).thenReturn(Optional.of(source));when(tasks.lock(source.id())).thenReturn(true);when(workers.releaseCapacity(any(),any())).thenReturn(true);
        lifecycle=new NativeRunLifecycle(repo,new AuthorityBoundary(repo),mock(ExecutionGuard.class),tasks,workers,List.of(budget));
    }
    @Test void unknownResourcesRetainLeaseAndCapacity() {
        assertThatThrownBy(()->lifecycle.yielded("task",lease)).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT state FROM work_task",String.class)).isEqualTo("YIELDING");verifyNoInteractions(workers,budget);
    }
    @Test void quiescentYieldReleasesCapacityOnceAndLeavesAnUncompletedWaitingTask() {
        jdbc.update("INSERT INTO execution_run_event VALUES('attempt','execution-quiescent','{\"complete\":true,\"count\":0}',1)");
        lifecycle.yielded("task",lease);
        assertThat(jdbc.queryForObject("SELECT state FROM work_task",String.class)).isEqualTo("WAITING_INPUT");
        assertThat(jdbc.queryForObject("SELECT lease_token FROM work_task",String.class)).isNull();
        assertThat(jdbc.queryForObject("SELECT state FROM execution_attempt",String.class)).isEqualTo("YIELDED");
        assertThatThrownBy(()->lifecycle.yielded("task",lease)).isInstanceOf(IllegalStateException.class);
        verify(workers).releaseCapacity(eq(new WorkerId("worker")),any());verify(budget).release(any(),any());
    }
    @Test void wholePlanStopRetainsItsCallbackEvenAfterAnAcceptedRunCancellation() {
        jdbc.update("INSERT INTO execution_run_command VALUES('attempt','CANCEL','ACCEPTED',1)");
        assertThat(lifecycle.cancellation("task","attempt")).isTrue();
        jdbc.update("INSERT INTO flexible_workflow_control VALUES('plan','task','PENDING_STOP')");
        assertThat(lifecycle.cancellation("task","attempt")).isFalse();
        jdbc.update("UPDATE flexible_workflow_control SET status='APPLIED'");
        assertThat(lifecycle.cancellation("task","attempt")).isTrue();
    }
}
