package me.asu.aidev.work.jdbc;

import java.nio.file.Path;
import java.time.Instant;
import me.asu.aidev.execution.domain.LeaseToken;
import me.asu.aidev.work.domain.TaskId;
import me.asu.aidev.worker.domain.WorkerId;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.assertj.core.api.Assertions.*;

class WorkspaceBaselineRepositoryTest {
    @TempDir Path temp;
    JdbcTemplate db; JdbcWorkTaskRepository tasks;
    final TaskId task=new TaskId("task"); final WorkerId worker=new WorkerId("worker"); final LeaseToken lease=new LeaseToken("lease");
    final String revision="a".repeat(40);
    @BeforeEach void setup() {
        var source=new DriverManagerDataSource("jdbc:sqlite:"+temp.resolve("baseline.db"));db=new JdbcTemplate(source);
        db.execute("CREATE TABLE work_task(id TEXT,state TEXT,current_worker_id TEXT,lease_token TEXT,lease_expires_at_epoch_ms BIGINT,instruction_revision INT,base_revision TEXT,version INT,updated_at_epoch_ms BIGINT)");
        db.execute("CREATE TABLE execution_attempt(lease_token TEXT,worker_id TEXT,state TEXT,instruction_revision INT)");
        db.update("INSERT INTO work_task VALUES('task','RUNNING','worker','lease',2000,1,NULL,1,0)");
        db.update("INSERT INTO execution_attempt VALUES('lease','worker','RUNNING',1)");
        tasks=new JdbcWorkTaskRepository(db,source,new SqliteWorkDatabaseDialect());
    }
    @Test void ownerPinsAnUnspecifiedBaselineAndCanReplaySameValue() {
        assertThat(tasks.pinBaselineOwned(task,worker,lease,revision,Instant.ofEpochMilli(1000))).isTrue();
        assertThat(tasks.pinBaselineOwned(task,worker,lease,revision,Instant.ofEpochMilli(1000))).isTrue();
        assertThat(db.queryForObject("SELECT base_revision FROM work_task",String.class)).isEqualTo(revision);
        assertThat(tasks.pinBaselineOwned(task,worker,lease,"b".repeat(40),Instant.ofEpochMilli(1000))).isFalse();
    }
    @Test void wrongWorkerOrLeaseCannotPin() {
        assertThat(tasks.pinBaselineOwned(task,new WorkerId("other"),lease,revision,Instant.ofEpochMilli(1000))).isFalse();
        assertThat(tasks.pinBaselineOwned(task,worker,new LeaseToken("other"),revision,Instant.ofEpochMilli(1000))).isFalse();
    }
    @Test void expiredOrCompletedAttemptCannotPin() {
        assertThat(tasks.pinBaselineOwned(task,worker,lease,revision,Instant.ofEpochMilli(2000))).isFalse();
        db.update("UPDATE execution_attempt SET state='COMPLETED'");
        assertThat(tasks.pinBaselineOwned(task,worker,lease,revision,Instant.ofEpochMilli(1000))).isFalse();
    }
    @Test void changedInstructionCannotPin() {
        db.update("UPDATE work_task SET instruction_revision=2");
        assertThat(tasks.pinBaselineOwned(task,worker,lease,revision,Instant.ofEpochMilli(1000))).isFalse();
    }
}
