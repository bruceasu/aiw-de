package me.asu.aidev.execution.jdbc;

import me.asu.aidev.execution.domain.CompletionReport;
import me.asu.aidev.execution.domain.AiAttemptHistory;
import me.asu.aidev.execution.domain.AiSelection;
import me.asu.aidev.execution.domain.CompletionVerdict;
import me.asu.aidev.execution.domain.ExecutionAttempt;
import me.asu.aidev.execution.domain.ExecutionAttemptId;
import me.asu.aidev.execution.domain.ExecutionAttemptState;
import me.asu.aidev.execution.domain.LeaseToken;
import me.asu.aidev.execution.domain.TaskInput;
import me.asu.aidev.execution.domain.WorkspaceAssignment;
import me.asu.aidev.execution.domain.WorkspaceResult;
import me.asu.aidev.execution.port.ExecutionAttemptRepository;
import me.asu.aidev.work.domain.TaskId;
import me.asu.aidev.work.domain.TaskType;
import me.asu.aidev.worker.domain.WorkerId;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.time.Instant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcExecutionAttemptRepository implements ExecutionAttemptRepository {
    private final JdbcTemplate jdbc;

    public JdbcExecutionAttemptRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(ExecutionAttempt attempt) {
        jdbc.update("""
                INSERT INTO execution_attempt(
                    id,task_id,attempt_no,instruction_revision,worker_id,lease_token,state,external_session_id,
                    created_at_epoch_ms,started_at_epoch_ms,finished_at_epoch_ms
                ) VALUES(?,?,?,?,?,?,?,?,?,?,?)
                """, attempt.id().value(), attempt.taskId().value(), attempt.attemptNo(), attempt.instructionRevision(), attempt.workerId().value(),
                attempt.leaseToken().value(), attempt.state().name(), attempt.externalSessionId(),
                attempt.createdAt().toEpochMilli(), epoch(attempt.startedAt()), epoch(attempt.finishedAt()));
    }

    @Override
    public boolean transition(LeaseToken leaseToken, ExecutionAttemptState expected, ExecutionAttemptState target,
                              Instant startedAt, Instant finishedAt) {
        return jdbc.update("""
                UPDATE execution_attempt
                   SET state=?,
                       started_at_epoch_ms=COALESCE(started_at_epoch_ms, ?),
                       finished_at_epoch_ms=?
                 WHERE lease_token=? AND state=?
                """, target.name(), epoch(startedAt), epoch(finishedAt), leaseToken.value(), expected.name()) == 1;
    }


    @Override
    public boolean attachExternalSession(LeaseToken leaseToken, WorkerId workerId, String externalSessionId) {
        if (externalSessionId == null || externalSessionId.isBlank()) return true;
        return jdbc.update("""
                UPDATE execution_attempt
                   SET external_session_id=?
                 WHERE lease_token=? AND worker_id=? AND state IN ('CLAIMED','RUNNING')
                """, externalSessionId.strip(), leaseToken.value(), workerId.value()) == 1;
    }

    @Override
    public boolean attachWorkspaceResult(LeaseToken leaseToken, WorkerId workerId,
                                         WorkspaceAssignment assignment, WorkspaceResult result) {
        return jdbc.update("""
                UPDATE execution_attempt
                   SET workspace_key=?, branch_name=?, base_revision=?, result_revision=?, changed_files=?, publication_ref=?
                 WHERE lease_token=? AND worker_id=? AND state='RUNNING'
                """, assignment.workspaceKey(), assignment.branchName(), assignment.baseRevision(),
                result.resultRevision(), String.join("\n", result.changedFiles()), result.publicationRef(),
                leaseToken.value(), workerId.value()) == 1;
    }


    @Override
    public boolean attachCompletionReport(LeaseToken leaseToken, WorkerId workerId, CompletionReport report) {
        return jdbc.update("""
                UPDATE execution_attempt
                   SET completion_verdict=?, completion_summary=?
                 WHERE lease_token=? AND worker_id=? AND state='RUNNING'
                """, report.verdict().name(), report.summary(), leaseToken.value(), workerId.value()) == 1;
    }

    @Override
    public boolean attachAiSelection(LeaseToken leaseToken, WorkerId workerId, AiSelection selection) {
        return jdbc.update("""
                UPDATE execution_attempt
                   SET ai_profile_id=?, ai_profile_version=?, ai_provider=?, ai_runtime=?,
                       ai_model=?, ai_effort=?, ai_fast=?, ai_selection_reason=?
                 WHERE lease_token=? AND worker_id=? AND state='CLAIMED' AND ai_profile_id IS NULL
                """, selection.profileId(), selection.profileVersion(), selection.provider(), selection.runtime(),
                selection.model(), selection.effort(), selection.fast(), selection.reason(),
                leaseToken.value(), workerId.value()) == 1;
    }

    @Override
    public boolean attachFailureCategory(LeaseToken leaseToken, WorkerId workerId, String category) {
        return jdbc.update("""
                UPDATE execution_attempt SET failure_category=?
                 WHERE lease_token=? AND worker_id=? AND state='RUNNING'
                """, category, leaseToken.value(), workerId.value()) == 1;
    }

    @Override
    public boolean requestStop(LeaseToken leaseToken, WorkerId workerId, Instant requestedAt) {
        return jdbc.update("""
                UPDATE execution_attempt
                   SET stop_requested_at_epoch_ms=COALESCE(stop_requested_at_epoch_ms, ?)
                 WHERE lease_token=? AND worker_id=? AND state IN ('CLAIMED','RUNNING')
                """, requestedAt.toEpochMilli(), leaseToken.value(), workerId.value()) == 1;
    }

    @Override
    public boolean stopRequested(LeaseToken leaseToken, WorkerId workerId) {
        Integer requested = jdbc.queryForObject("""
                SELECT CASE WHEN stop_requested_at_epoch_ms IS NULL THEN 0 ELSE 1 END
                  FROM execution_attempt WHERE lease_token=? AND worker_id=? AND state IN ('CLAIMED','RUNNING')
                """, Integer.class, leaseToken.value(), workerId.value());
        return requested != null && requested == 1;
    }

    @Override
    public boolean acknowledgeStop(LeaseToken leaseToken, WorkerId workerId, Instant acknowledgedAt) {
        return jdbc.update("""
                UPDATE execution_attempt
                   SET stop_acknowledged_at_epoch_ms=COALESCE(stop_acknowledged_at_epoch_ms, ?),
                       state='EXPIRED', finished_at_epoch_ms=?
                 WHERE lease_token=? AND worker_id=? AND state IN ('CLAIMED','RUNNING')
                   AND stop_requested_at_epoch_ms IS NOT NULL
                """, acknowledgedAt.toEpochMilli(), acknowledgedAt.toEpochMilli(),
                leaseToken.value(), workerId.value()) == 1;
    }

    @Override
    public boolean stopAcknowledged(TaskId taskId, WorkerId workerId, LeaseToken leaseToken) {
        Integer acknowledged = jdbc.queryForObject("""
                SELECT CASE WHEN COUNT(*) > 0 THEN 1 ELSE 0 END
                  FROM execution_attempt
                 WHERE task_id=? AND worker_id=? AND lease_token=? AND state='EXPIRED'
                   AND stop_requested_at_epoch_ms IS NOT NULL
                   AND stop_acknowledged_at_epoch_ms IS NOT NULL
                """, Integer.class, taskId.value(), workerId.value(), leaseToken.value());
        return acknowledged != null && acknowledged == 1;
    }

    @Override
    public Optional<ExecutionAttemptId> findAttemptId(LeaseToken leaseToken) {
        List<String> rows = jdbc.query("SELECT id FROM execution_attempt WHERE lease_token=?",
                (rs, rowNum) -> rs.getString("id"), leaseToken.value());
        return rows.stream().findFirst().map(ExecutionAttemptId::new);
    }

    @Override
    public Optional<AiAttemptHistory> findPreviousAiAttempt(TaskId taskId, int beforeAttemptNo) {
        List<AiAttemptHistory> rows = jdbc.query("""
                SELECT ai_profile_id, ai_profile_version, ai_model, ai_effort, ai_fast,
                       failure_category, state
                  FROM execution_attempt
                 WHERE task_id=? AND attempt_no<? AND ai_profile_id IS NOT NULL
                 ORDER BY attempt_no DESC LIMIT 1
                """, (rs, rowNum) -> new AiAttemptHistory(
                rs.getString("ai_profile_id"), rs.getString("ai_profile_version"),
                rs.getString("ai_model"), rs.getString("ai_effort"), rs.getBoolean("ai_fast"),
                rs.getString("failure_category"), rs.getString("state")), taskId.value(), beforeAttemptNo);
        return rows.stream().findFirst();
    }

    @Override
    public boolean terminateActiveLease(LeaseToken leaseToken, Instant finishedAt, String terminationReason) {
        return jdbc.update("""
                UPDATE execution_attempt
                   SET state='EXPIRED', finished_at_epoch_ms=?, termination_reason=?
                 WHERE lease_token=? AND state IN ('CLAIMED','RUNNING')
                """, finishedAt.toEpochMilli(), terminationReason, leaseToken.value()) == 1;
    }

    @Override
    public Optional<CompletionReport> findCompletionReport(TaskId taskId) {
        List<CompletionReport> rows = jdbc.query("""
                SELECT completion_verdict, completion_summary
                  FROM execution_attempt
                 WHERE task_id=? AND state='COMPLETED' AND completion_verdict IS NOT NULL
                 ORDER BY attempt_no DESC
                 LIMIT 1
                """, (rs, rowNum) -> new CompletionReport(
                CompletionVerdict.valueOf(rs.getString("completion_verdict")),
                rs.getString("completion_summary")), taskId.value());
        return rows.stream().findFirst();
    }

    @Override
    public Optional<TaskInput> findCompletedInput(TaskId taskId) {
        List<TaskInput> rows = jdbc.query("""
                SELECT wt.task_type, wt.repository_ref, ea.base_revision, ea.result_revision,
                       ea.publication_ref, ea.changed_files, ea.completion_verdict, ea.completion_summary
                  FROM execution_attempt ea
                  JOIN work_task wt ON wt.id = ea.task_id
                 WHERE ea.task_id=? AND ea.state='COMPLETED'
                 ORDER BY ea.attempt_no DESC
                 LIMIT 1
                """, (rs, rowNum) -> new TaskInput(
                taskId,
                TaskType.valueOf(rs.getString("task_type")),
                rs.getString("repository_ref"),
                rs.getString("base_revision"),
                rs.getString("result_revision"),
                rs.getString("publication_ref"),
                decodeChangedFiles(rs.getString("changed_files")),
                rs.getString("completion_verdict") == null ? null : CompletionVerdict.valueOf(rs.getString("completion_verdict")),
                rs.getString("completion_summary")), taskId.value());
        return rows.stream().findFirst();
    }

    private static List<String> decodeChangedFiles(String value) {
        if (value == null || value.isBlank()) return List.of();
        return Arrays.stream(value.split("\\n"))
                .filter(s -> !s.isBlank())
                .toList();
    }

    private static Long epoch(Instant instant) {
        return instant == null ? null : instant.toEpochMilli();
    }
}
