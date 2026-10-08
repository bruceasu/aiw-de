package me.asu.aidev.work.jdbc;

import me.asu.aidev.execution.domain.ExpiredLease;
import me.asu.aidev.execution.domain.LeaseToken;
import me.asu.aidev.execution.domain.TaskLease;
import me.asu.aidev.work.domain.CapabilitySetCodec;
import me.asu.aidev.work.domain.ReadyTaskCandidate;
import me.asu.aidev.work.domain.TaskId;
import me.asu.aidev.work.domain.TaskInstructionRevision;
import me.asu.aidev.work.domain.TaskPriority;
import me.asu.aidev.work.domain.TaskState;
import me.asu.aidev.work.domain.TaskType;
import me.asu.aidev.work.domain.WorkTask;
import me.asu.aidev.work.port.WorkTaskRepository;
import me.asu.aidev.worker.domain.WorkerId;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcWorkTaskRepository implements WorkTaskRepository {
    private final JdbcTemplate jdbc;
    private final DataSource dataSource;
    private final WorkDatabaseDialect dialect;

    public JdbcWorkTaskRepository(JdbcTemplate jdbc, DataSource dataSource, WorkDatabaseDialect dialect) {
        this.jdbc = jdbc;
        this.dataSource = dataSource;
        this.dialect = dialect;
    }

    @Override
    @Transactional
    public void insert(WorkTask task) {
        jdbc.update("""
                INSERT INTO work_task(
                    id,task_type,state,subject_type,subject_id,required_capabilities,skill_ref,instruction,
                    repository_ref,base_revision,priority,version,attempt_no,created_at_epoch_ms,updated_at_epoch_ms
                ) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """,
                task.id().value(), task.type().name(), task.state().name(), task.subjectType(), task.subjectId(),
                CapabilitySetCodec.encode(task.requiredCapabilities()), task.skillRef(), task.instruction(),
                task.repositoryRef(), task.baseRevision(), task.priority().value(), task.version(),
                0, task.createdAt().toEpochMilli(), task.updatedAt().toEpochMilli());
        insertInitialRevision(task);
    }

    @Override
    @Transactional
    public boolean insertIfAbsent(WorkTask task) {
        boolean inserted = jdbc.update("""
                INSERT INTO work_task(
                    id,task_type,state,subject_type,subject_id,required_capabilities,skill_ref,instruction,
                    repository_ref,base_revision,priority,version,attempt_no,created_at_epoch_ms,updated_at_epoch_ms
                ) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                ON CONFLICT(id) DO NOTHING
                """,
                task.id().value(), task.type().name(), task.state().name(), task.subjectType(), task.subjectId(),
                CapabilitySetCodec.encode(task.requiredCapabilities()), task.skillRef(), task.instruction(),
                task.repositoryRef(), task.baseRevision(), task.priority().value(), task.version(),
                0, task.createdAt().toEpochMilli(), task.updatedAt().toEpochMilli()) == 1;
        if (inserted) insertInitialRevision(task);
        return inserted;
    }

    @Override
    public Optional<WorkTask> find(TaskId id) {
        return jdbc.query("""
                SELECT id,task_type,state,subject_type,subject_id,required_capabilities,skill_ref,instruction,
                       repository_ref,base_revision,priority,version,instruction_revision,created_at_epoch_ms,updated_at_epoch_ms
                  FROM work_task WHERE id=?
                """, rs -> rs.next() ? Optional.of(mapTask(rs)) : Optional.empty(), id.value());
    }

    @Override
    public List<TaskInstructionRevision> instructionRevisions(TaskId id) {
        return jdbc.query("""
                SELECT revision, instruction, actor, reason, predecessor_revision, created_at_epoch_ms
                  FROM work_task_instruction_revision
                 WHERE task_id=? ORDER BY revision
                """, (rs, rowNum) -> new TaskInstructionRevision(id, rs.getInt("revision"),
                rs.getString("instruction"), rs.getString("actor"), rs.getString("reason"),
                nullableInteger(rs, "predecessor_revision"),
                Instant.ofEpochMilli(rs.getLong("created_at_epoch_ms"))), id.value());
    }

    @Override
    @Transactional
    public boolean reviseInstruction(TaskId id, TaskState expectedState, long expectedTaskVersion,
                                     int expectedInstructionRevision, String instruction,
                                     String actor, String reason, Instant updatedAt) {
        String normalizedInstruction = instruction == null ? "" : instruction.strip();
        if (actor == null || actor.isBlank()) throw new IllegalArgumentException("actor is blank");
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("reason is blank");
        int changed = jdbc.update("""
                UPDATE work_task
                   SET instruction=?, instruction_revision=instruction_revision+1,
                       version=version+1, updated_at_epoch_ms=?
                 WHERE id=? AND version=? AND instruction_revision=? AND state=?
                """, normalizedInstruction, updatedAt.toEpochMilli(), id.value(),
                expectedTaskVersion, expectedInstructionRevision, expectedState.name());
        if (changed != 1) return false;
        jdbc.update("""
                INSERT INTO work_task_instruction_revision(
                    task_id, revision, instruction, actor, reason, predecessor_revision, created_at_epoch_ms
                ) VALUES(?,?,?,?,?,?,?)
                """, id.value(), expectedInstructionRevision + 1, normalizedInstruction,
                actor.strip(), reason.strip(), expectedInstructionRevision, updatedAt.toEpochMilli());
        return true;
    }

    @Override
    public boolean requeueRevoked(TaskId id, TaskState expectedState, WorkerId workerId,
                                  LeaseToken leaseToken, Instant updatedAt) {
        return jdbc.update("""
                UPDATE work_task
                   SET state='READY', current_worker_id=NULL, lease_token=NULL,
                       lease_acquired_at_epoch_ms=NULL, lease_expires_at_epoch_ms=NULL,
                       lease_heartbeat_at_epoch_ms=NULL, version=version+1, updated_at_epoch_ms=?
                 WHERE id=? AND state=? AND current_worker_id=? AND lease_token=?
                """, updatedAt.toEpochMilli(), id.value(), expectedState.name(),
                workerId.value(), leaseToken.value()) == 1;
    }

    @Override
    public void recordControlEvent(String eventId, TaskId id, String attemptId, String action,
                                   String actor, String reason, boolean warningConfirmed, Instant createdAt) {
        jdbc.update("""
                INSERT INTO work_task_control_event(
                    id, task_id, attempt_id, action, actor, reason, warning_confirmed, created_at_epoch_ms
                ) VALUES(?,?,?,?,?,?,?,?)
                """, eventId, id.value(), attemptId, action, actor, reason,
                warningConfirmed ? 1 : 0, createdAt.toEpochMilli());
    }

    @Override
    public void linkCorrection(TaskId completedTaskId, TaskId correctionTaskId,
                               String actor, String reason, Instant createdAt) {
        jdbc.update("""
                INSERT INTO work_task_correction(
                    completed_task_id, correction_task_id, created_by, reason, created_at_epoch_ms
                ) VALUES(?,?,?,?,?)
                """, completedTaskId.value(), correctionTaskId.value(), actor, reason, createdAt.toEpochMilli());
    }

    @Override
    public List<WorkTask> findBySubjectAndType(String subjectType, String subjectId, TaskType type) {
        return jdbc.query("""
                SELECT id,task_type,state,subject_type,subject_id,required_capabilities,skill_ref,instruction,
                       repository_ref,base_revision,priority,version,instruction_revision,created_at_epoch_ms,updated_at_epoch_ms
                  FROM work_task
                 WHERE subject_type=? AND subject_id=? AND task_type=?
                 ORDER BY created_at_epoch_ms,id
                """, (rs, rowNum) -> mapTask(rs), subjectType, subjectId, type.name());
    }

    @Override
    public List<WorkTask> findBySubject(String subjectType, String subjectId) {
        return jdbc.query("""
                SELECT id,task_type,state,subject_type,subject_id,required_capabilities,skill_ref,instruction,
                       repository_ref,base_revision,priority,version,instruction_revision,created_at_epoch_ms,updated_at_epoch_ms
                  FROM work_task
                 WHERE subject_type=? AND subject_id=?
                 ORDER BY id
                """, (rs, rowNum) -> mapTask(rs), subjectType, subjectId);
    }

    @Override
    public List<WorkTask> findBySubjectAndState(String subjectType, String subjectId, TaskState state) {
        return jdbc.query("""
                SELECT id,task_type,state,subject_type,subject_id,required_capabilities,skill_ref,instruction,
                       repository_ref,base_revision,priority,version,instruction_revision,created_at_epoch_ms,updated_at_epoch_ms
                  FROM work_task
                 WHERE subject_type=? AND subject_id=? AND state=?
                 ORDER BY id
                """, (rs, rowNum) -> mapTask(rs), subjectType, subjectId, state.name());
    }

    @Override
    public boolean lock(TaskId id) {
        Connection connection = DataSourceUtils.getConnection(dataSource);
        try {
            return dialect.lockTask(connection, id);
        } catch (SQLException e) {
            throw new IllegalStateException("cannot lock WorkTask " + id.value(), e);
        }
    }

    @Override
    public boolean transitionState(TaskId id, TaskState expected, TaskState target, Instant updatedAt) {
        int changed = jdbc.update("""
                UPDATE work_task
                   SET state=?, version=version+1, updated_at_epoch_ms=?
                 WHERE id=? AND state=?
                """, target.name(), updatedAt.toEpochMilli(), id.value(), expected.name());
        return changed == 1;
    }

    @Override
    public List<WorkTask> findReadyCandidates() {
        return jdbc.query("""
                SELECT id,task_type,state,subject_type,subject_id,required_capabilities,skill_ref,instruction,
                       repository_ref,base_revision,priority,version,instruction_revision,created_at_epoch_ms,updated_at_epoch_ms
                  FROM work_task
                 WHERE state='READY' AND lease_token IS NULL
                 ORDER BY priority DESC, created_at_epoch_ms, id
                """, (rs, rowNum) -> mapTask(rs));
    }

    @Override
    public List<ReadyTaskCandidate> findReadySchedulingCandidates() {
        return jdbc.query("""
                SELECT wt.id,wt.task_type,wt.state,wt.subject_type,wt.subject_id,wt.required_capabilities,wt.skill_ref,wt.instruction,
                       wt.repository_ref,wt.base_revision,wt.priority,wt.version,wt.instruction_revision,wt.created_at_epoch_ms,wt.updated_at_epoch_ms,
                       COALESCE(active.active_leases,0) AS active_subject_leases
                  FROM work_task wt
                  LEFT JOIN (
                        SELECT subject_type,subject_id,COUNT(*) AS active_leases
                          FROM work_task
                         WHERE state IN ('CLAIMED','RUNNING')
                         GROUP BY subject_type,subject_id
                  ) active
                    ON active.subject_type=wt.subject_type AND active.subject_id=wt.subject_id
                 WHERE wt.state='READY' AND wt.lease_token IS NULL
                """, (rs, rowNum) -> new ReadyTaskCandidate(mapTask(rs), rs.getInt("active_subject_leases")));
    }

    @Override
    public Optional<TaskLease> claim(TaskId taskId, WorkerId workerId, LeaseToken leaseToken,
                                     Instant acquiredAt, Instant expiresAt, Instant heartbeatAt) {
        int changed = jdbc.update("""
                UPDATE work_task
                   SET state='CLAIMED', current_worker_id=?, lease_token=?,
                       lease_acquired_at_epoch_ms=?, lease_expires_at_epoch_ms=?, lease_heartbeat_at_epoch_ms=?,
                       attempt_no=attempt_no+1, version=version+1, updated_at_epoch_ms=?
                 WHERE id=? AND state='READY' AND lease_token IS NULL
                """, workerId.value(), leaseToken.value(), acquiredAt.toEpochMilli(), expiresAt.toEpochMilli(),
                heartbeatAt.toEpochMilli(), acquiredAt.toEpochMilli(), taskId.value());
        return changed == 1
                ? Optional.of(new TaskLease(taskId, workerId, leaseToken, acquiredAt, expiresAt, heartbeatAt))
                : Optional.empty();
    }

    @Override
    public Optional<TaskLease> findLease(TaskId taskId) {
        return jdbc.query("""
                SELECT current_worker_id,lease_token,lease_acquired_at_epoch_ms,
                       lease_expires_at_epoch_ms,lease_heartbeat_at_epoch_ms
                  FROM work_task WHERE id=? AND lease_token IS NOT NULL
                """, rs -> rs.next() ? Optional.of(new TaskLease(
                        taskId,
                        new WorkerId(rs.getString("current_worker_id")),
                        new LeaseToken(rs.getString("lease_token")),
                        Instant.ofEpochMilli(rs.getLong("lease_acquired_at_epoch_ms")),
                        Instant.ofEpochMilli(rs.getLong("lease_expires_at_epoch_ms")),
                        Instant.ofEpochMilli(rs.getLong("lease_heartbeat_at_epoch_ms"))))
                        : Optional.empty(), taskId.value());
    }

    @Override
    public boolean start(TaskId taskId, WorkerId workerId, LeaseToken leaseToken, Instant updatedAt) {
        return jdbc.update("""
                UPDATE work_task
                   SET state='RUNNING', version=version+1, updated_at_epoch_ms=?
                 WHERE id=? AND state='CLAIMED' AND current_worker_id=? AND lease_token=?
                   AND lease_expires_at_epoch_ms>?
                   AND instruction_revision=(SELECT instruction_revision FROM execution_attempt
                                               WHERE lease_token=? AND worker_id=? AND state='CLAIMED')
                """, updatedAt.toEpochMilli(), taskId.value(), workerId.value(), leaseToken.value(),
                updatedAt.toEpochMilli(), leaseToken.value(), workerId.value()) == 1;
    }

    @Override
    public boolean heartbeatLease(TaskId taskId, WorkerId workerId, LeaseToken leaseToken,
                                  Instant heartbeatAt, Instant expiresAt) {
        return jdbc.update("""
                UPDATE work_task
                   SET lease_heartbeat_at_epoch_ms=?, lease_expires_at_epoch_ms=?,
                       version=version+1, updated_at_epoch_ms=?
                 WHERE id=? AND state IN ('CLAIMED','RUNNING')
                   AND current_worker_id=? AND lease_token=? AND lease_expires_at_epoch_ms>?
                   AND EXISTS (SELECT 1 FROM execution_attempt ea
                                WHERE ea.lease_token=work_task.lease_token
                                  AND ea.worker_id=work_task.current_worker_id AND ea.state IN ('CLAIMED','RUNNING')
                                  AND (ea.instruction_revision=work_task.instruction_revision
                                       OR ea.stop_requested_at_epoch_ms IS NOT NULL))
                """, heartbeatAt.toEpochMilli(), expiresAt.toEpochMilli(), heartbeatAt.toEpochMilli(), taskId.value(),
                workerId.value(), leaseToken.value(), heartbeatAt.toEpochMilli()) == 1;
    }

    @Override
    public boolean completeOwned(TaskId taskId, WorkerId workerId, LeaseToken leaseToken, Instant updatedAt) {
        return terminalTransition(taskId, workerId, leaseToken, TaskState.COMPLETED, updatedAt);
    }

    @Override
    public boolean failOwned(TaskId taskId, WorkerId workerId, LeaseToken leaseToken, Instant updatedAt) {
        return terminalTransition(taskId, workerId, leaseToken, TaskState.FAILED, updatedAt);
    }

    private boolean terminalTransition(TaskId taskId, WorkerId workerId, LeaseToken leaseToken,
                                       TaskState target, Instant updatedAt) {
        return jdbc.update("""
                UPDATE work_task
                   SET state=?, current_worker_id=NULL, lease_token=NULL,
                       lease_acquired_at_epoch_ms=NULL, lease_expires_at_epoch_ms=NULL, lease_heartbeat_at_epoch_ms=NULL,
                       version=version+1, updated_at_epoch_ms=?
                 WHERE id=? AND state='RUNNING' AND current_worker_id=? AND lease_token=?
                   AND lease_expires_at_epoch_ms>?
                   AND EXISTS (SELECT 1 FROM execution_attempt ea
                                WHERE ea.lease_token=work_task.lease_token
                                  AND ea.worker_id=work_task.current_worker_id
                                  AND ea.instruction_revision=work_task.instruction_revision
                                  AND ea.stop_requested_at_epoch_ms IS NULL)
                """, target.name(), updatedAt.toEpochMilli(), taskId.value(), workerId.value(), leaseToken.value(),
                updatedAt.toEpochMilli()) == 1;
    }

    @Override
    public List<ExpiredLease> findExpiredLeases(Instant now, int limit) {
        if (limit < 1) return List.of();
        return jdbc.query("""
                SELECT id,current_worker_id,lease_token
                  FROM work_task
                 WHERE state IN ('CLAIMED','RUNNING')
                   AND lease_token IS NOT NULL AND lease_expires_at_epoch_ms<=?
                   AND (NOT EXISTS (SELECT 1 FROM execution_attempt ea
                                    WHERE ea.lease_token=work_task.lease_token
                                      AND ea.stop_requested_at_epoch_ms IS NOT NULL)
                        OR EXISTS (SELECT 1 FROM work_task_workspace_replacement r
                                    WHERE r.source_task_id=work_task.id AND r.status='PENDING_STOP'
                                      AND r.lease_token=work_task.lease_token))
                 ORDER BY lease_expires_at_epoch_ms,id
                 LIMIT ?
                """, (rs, rowNum) -> new ExpiredLease(
                        new TaskId(rs.getString("id")),
                        new WorkerId(rs.getString("current_worker_id")),
                        new LeaseToken(rs.getString("lease_token"))),
                now.toEpochMilli(), limit);
    }

    @Override
    public boolean requeueExpired(TaskId taskId, WorkerId workerId, LeaseToken leaseToken, Instant updatedAt) {
        boolean changed = jdbc.update("""
                UPDATE work_task
                   SET state='READY', current_worker_id=NULL, lease_token=NULL,
                       lease_acquired_at_epoch_ms=NULL, lease_expires_at_epoch_ms=NULL, lease_heartbeat_at_epoch_ms=NULL,
                       version=version+1, updated_at_epoch_ms=?
                 WHERE id=? AND state IN ('CLAIMED','RUNNING')
                   AND current_worker_id=? AND lease_token=? AND lease_expires_at_epoch_ms<=?
                   AND (NOT EXISTS (SELECT 1 FROM execution_attempt ea
                                    WHERE ea.lease_token=work_task.lease_token
                                      AND ea.stop_requested_at_epoch_ms IS NOT NULL)
                        OR EXISTS (SELECT 1 FROM work_task_workspace_replacement r
                                    WHERE r.source_task_id=work_task.id AND r.status='PENDING_STOP'
                                      AND r.lease_token=work_task.lease_token))
                """, updatedAt.toEpochMilli(), taskId.value(), workerId.value(), leaseToken.value(),
                updatedAt.toEpochMilli()) == 1;
        if (changed) conflictPendingReplacement(taskId, "Worker lease expired before stop acknowledgement", updatedAt);
        return changed;
    }

    @Override
    public boolean requeueOwnedForPause(TaskId taskId, WorkerId workerId, LeaseToken leaseToken, Instant updatedAt) {
        boolean changed = jdbc.update("""
                UPDATE work_task
                   SET state='READY', current_worker_id=NULL, lease_token=NULL,
                       lease_acquired_at_epoch_ms=NULL, lease_expires_at_epoch_ms=NULL, lease_heartbeat_at_epoch_ms=NULL,
                       version=version+1, updated_at_epoch_ms=?
                 WHERE id=? AND state IN ('CLAIMED','RUNNING')
                   AND current_worker_id=? AND lease_token=?
                """, updatedAt.toEpochMilli(), taskId.value(), workerId.value(), leaseToken.value()) == 1;
        if (changed) conflictPendingReplacement(taskId, "Run was paused before Worker stop acknowledgement", updatedAt);
        return changed;
    }

    @Override
    public boolean cancelOwned(TaskId taskId, WorkerId workerId, LeaseToken leaseToken, Instant updatedAt) {
        boolean changed = jdbc.update("""
                UPDATE work_task
                   SET state='CANCELLED', current_worker_id=NULL, lease_token=NULL,
                       lease_acquired_at_epoch_ms=NULL, lease_expires_at_epoch_ms=NULL, lease_heartbeat_at_epoch_ms=NULL,
                       version=version+1, updated_at_epoch_ms=?
                 WHERE id=? AND state IN ('CLAIMED','RUNNING')
                   AND current_worker_id=? AND lease_token=?
                """, updatedAt.toEpochMilli(), taskId.value(), workerId.value(), leaseToken.value()) == 1;
        if (changed) conflictPendingReplacement(taskId, "Run was cancelled before Worker stop acknowledgement", updatedAt);
        return changed;
    }

    private void conflictPendingReplacement(TaskId taskId, String reason, Instant at) {
        jdbc.update("""
                UPDATE work_task_workspace_replacement
                   SET status='CONFLICT', conflict_reason=?, resolved_at_epoch_ms=?
                 WHERE source_task_id=? AND status='PENDING_STOP'
                """, reason, at.toEpochMilli(), taskId.value());
    }

    @Override
    public boolean cancelInactive(TaskId taskId, TaskState expected, Instant updatedAt) {
        if (expected == TaskState.CLAIMED || expected == TaskState.RUNNING) {
            throw new IllegalArgumentException("active task cancellation requires lease ownership");
        }
        return jdbc.update("""
                UPDATE work_task
                   SET state='CANCELLED', version=version+1, updated_at_epoch_ms=?
                 WHERE id=? AND state=? AND current_worker_id IS NULL AND lease_token IS NULL
                """, updatedAt.toEpochMilli(), taskId.value(), expected.name()) == 1;
    }

    @Override
    public boolean retryFailed(TaskId taskId, Instant updatedAt) {
        return jdbc.update("""
                UPDATE work_task
                   SET state='READY', version=version+1, updated_at_epoch_ms=?
                 WHERE id=? AND state='FAILED'
                   AND current_worker_id IS NULL AND lease_token IS NULL
                """, updatedAt.toEpochMilli(), taskId.value()) == 1;
    }

    @Override
    public int attemptNo(TaskId taskId) {
        Integer attemptNo = jdbc.queryForObject("SELECT attempt_no FROM work_task WHERE id=?", Integer.class, taskId.value());
        if (attemptNo == null) throw new IllegalStateException("task not found: " + taskId.value());
        return attemptNo;
    }

    private void insertInitialRevision(WorkTask task) {
        jdbc.update("""
                INSERT INTO work_task_instruction_revision(
                    task_id, revision, instruction, actor, reason, predecessor_revision, created_at_epoch_ms
                ) VALUES(?,1,?,'system:task-service','Initial task instruction',NULL,?)
                """, task.id().value(), task.instruction(), task.createdAt().toEpochMilli());
    }

    private static Integer nullableInteger(java.sql.ResultSet rs, String column) throws SQLException {
        Number value = (Number) rs.getObject(column);
        return value == null ? null : value.intValue();
    }

    private static WorkTask mapTask(java.sql.ResultSet rs) throws SQLException {
        return new WorkTask(
                new TaskId(rs.getString("id")),
                TaskType.valueOf(rs.getString("task_type")),
                TaskState.valueOf(rs.getString("state")),
                rs.getString("subject_type"),
                rs.getString("subject_id"),
                CapabilitySetCodec.decode(rs.getString("required_capabilities")),
                rs.getString("skill_ref"),
                rs.getString("instruction"),
                rs.getInt("instruction_revision"),
                rs.getString("repository_ref"),
                rs.getString("base_revision"),
                new TaskPriority(rs.getInt("priority")),
                rs.getLong("version"),
                Instant.ofEpochMilli(rs.getLong("created_at_epoch_ms")),
                Instant.ofEpochMilli(rs.getLong("updated_at_epoch_ms")));
    }
}
