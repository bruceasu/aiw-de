package me.asu.aidev.work.application;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import me.asu.aidev.autonomous.application.GitRevisionResolver;
import me.asu.aidev.execution.domain.LeaseToken;
import me.asu.aidev.execution.port.ExecutionAttemptRepository;
import me.asu.aidev.work.domain.TaskId;
import me.asu.aidev.work.domain.TaskState;
import me.asu.aidev.work.domain.WorkTask;
import me.asu.aidev.work.port.TaskDependencyRepository;
import me.asu.aidev.work.port.WorkTaskRepository;
import me.asu.aidev.workspace.application.ManagedWorkspaceService;
import me.asu.aidev.worker.domain.WorkerId;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Audited replacement of one Run task without rewriting its execution history. */
@Service
public class TaskWorkspaceReplacementService {
    private final JdbcTemplate jdbc;
    private final WorkTaskRepository tasks;
    private final TaskDependencyRepository dependencies;
    private final WorkTaskService taskService;
    private final ExecutionAttemptRepository attempts;
    private final ManagedWorkspaceService workspaces;
    private final GitRevisionResolver revisions;
    private final Clock clock;

    public TaskWorkspaceReplacementService(JdbcTemplate jdbc, WorkTaskRepository tasks,
                                           TaskDependencyRepository dependencies, WorkTaskService taskService,
                                           ExecutionAttemptRepository attempts, ManagedWorkspaceService workspaces,
                                           GitRevisionResolver revisions) {
        this.jdbc = jdbc;
        this.tasks = tasks;
        this.dependencies = dependencies;
        this.taskService = taskService;
        this.attempts = attempts;
        this.workspaces = workspaces;
        this.revisions = revisions;
        this.clock = Clock.systemUTC();
    }

    public record Replacement(String id, String sourceTaskId, String replacementTaskId,
                              String status, String actor, String reason, String oldRepositoryRef,
                              String oldBaseRevision, String newRepositoryRef, String newBaseRevision,
                              String conflictReason, boolean workerStopRequired, Instant stopAcknowledgedAt,
                              Instant requestedAt, Instant resolvedAt) {}

    @Transactional(readOnly = true)
    public List<Replacement> forRun(String runId) {
        return jdbc.query("""
                SELECT r.* FROM work_task_workspace_replacement r
                  JOIN work_task t ON t.id=r.source_task_id
                 WHERE t.subject_type='AUTONOMOUS_REQUIREMENT' AND t.subject_id=?
                 ORDER BY r.requested_at_epoch_ms DESC,r.id DESC
                """, (rs, row) -> new Replacement(rs.getString("id"), rs.getString("source_task_id"),
                rs.getString("replacement_task_id"), rs.getString("status"), rs.getString("actor"),
                rs.getString("reason"), rs.getString("old_repository_ref"), rs.getString("old_base_revision"),
                rs.getString("new_repository_ref"), rs.getString("new_base_revision"),
                rs.getString("conflict_reason"), rs.getString("lease_token") != null,
                rs.getObject("stop_acknowledged_at_epoch_ms") == null ? null
                        : Instant.ofEpochMilli(rs.getLong("stop_acknowledged_at_epoch_ms")),
                Instant.ofEpochMilli(rs.getLong("requested_at_epoch_ms")),
                rs.getObject("resolved_at_epoch_ms") == null ? null
                        : Instant.ofEpochMilli(rs.getLong("resolved_at_epoch_ms"))), runId);
    }

    @Transactional
    public Replacement request(String runId, TaskId sourceId, long expectedVersion,
                               String repositoryRef, String mode, String revision, String actor, String reason) {
        actor = required(actor, "actor", 256);
        reason = required(reason, "reason", 2000);
        repositoryRef = required(repositoryRef, "repositoryRef", 120);
        if (!tasks.lock(sourceId)) throw new IllegalStateException("task not found: " + sourceId.value());
        WorkTask source = tasks.find(sourceId).orElseThrow();
        if (!"AUTONOMOUS_REQUIREMENT".equals(source.subjectType()) || !runId.equals(source.subjectId())) {
            throw new IllegalArgumentException("task does not belong to this Run");
        }
        var existing = latestAccepted(sourceId);
        if (existing != null && (existing.status().equals("PENDING_STOP") || existing.status().equals("COMPLETED"))) {
            var row = acceptedRow(sourceId);
            if (existing.actor().equals(actor) && existing.reason().equals(reason)
                    && existing.newRepositoryRef().equals(repositoryRef)
                    && ("DIRECT".equalsIgnoreCase(mode) == (existing.newBaseRevision() == null))
                    && java.util.Objects.equals(row.requestedRevision(), revision == null ? null : revision.strip())) return existing;
            throw new IllegalStateException("task already has a replacement request");
        }
        if (source.version() != expectedVersion) throw new IllegalStateException("task changed; reload before replacing Workspace");
        if (source.state() == TaskState.COMPLETED) {
            throw new IllegalStateException("completed task requires linked correction instead of Workspace replacement");
        }
        if (workspaces.findTarget(repositoryRef).isEmpty()) {
            throw new IllegalArgumentException("replacement target must be a registered, enabled Workspace repository");
        }
        String normalizedMode = required(mode, "mode", 16).toUpperCase();
        String baseRevision;
        if (normalizedMode.equals("DIRECT")) {
            if (revision != null && !revision.isBlank()) throw new IllegalArgumentException("DIRECT mode cannot have a Git revision");
            baseRevision = null;
        } else if (normalizedMode.equals("GIT")) {
            baseRevision = revisions.resolve(repositoryRef, revision == null || revision.isBlank() ? "HEAD" : revision.strip());
        } else throw new IllegalArgumentException("mode must be DIRECT or GIT");

        var now = clock.instant();
        String id = "replacement-" + UUID.randomUUID();
        String leaseWorker = null;
        String leaseToken = null;
        if (source.state() == TaskState.CLAIMED || source.state() == TaskState.RUNNING) {
            var lease = tasks.findLease(sourceId).orElseThrow(() -> new IllegalStateException("active task has no lease"));
            leaseWorker = lease.workerId().value();
            leaseToken = lease.token().value();
        } else if (!Set.of(TaskState.BLOCKED, TaskState.READY, TaskState.FAILED, TaskState.CANCELLED)
                .contains(source.state()) || tasks.findLease(sourceId).isPresent()) {
            throw new IllegalStateException("task is not safely replaceable in its current state");
        }
        // Reject any already-advanced downstream before asking a Worker to stop.
        lockAndCheckDependents(sourceId);
        jdbc.update("""
                INSERT INTO work_task_workspace_replacement(
                  id,source_task_id,status,actor,reason,old_repository_ref,old_base_revision,
                  new_repository_ref,new_base_revision,requested_revision,source_version,source_instruction_revision,
                  lease_worker_id,lease_token,
                  requested_at_epoch_ms)
                VALUES(?,?,'PENDING_STOP',?,?,?,?,?,?,?,?,?,?,?,?)
                """, id, sourceId.value(), actor, reason, source.repositoryRef(), source.baseRevision(),
                repositoryRef, baseRevision, revision == null ? null : revision.strip(),
                source.version(), source.instructionRevision(), leaseWorker, leaseToken, now.toEpochMilli());
        if (leaseToken != null) {
            if (!attempts.requestStop(new LeaseToken(leaseToken), new WorkerId(leaseWorker), now)) {
                throw new IllegalStateException("active Worker attempt cannot be asked to stop");
            }
            tasks.recordControlEvent(UUID.randomUUID().toString(), sourceId,
                    attempts.findAttemptId(new LeaseToken(leaseToken)).orElseThrow().value(),
                    "STOP_REQUESTED", actor, reason, false, now);
        } else {
            complete(id, source, now);
        }
        return latest(sourceId);
    }

    /** Called in the Worker ACK transaction after the old lease is fenced and released. */
    public void afterStopAcknowledged(TaskId sourceId, WorkerId workerId, LeaseToken token) {
        var pending = pending(sourceId);
        if (pending == null) return;
        if (!workerId.value().equals(pending.leaseWorker()) || !token.value().equals(pending.leaseToken())) {
            throw new IllegalStateException("replacement stop acknowledgement belongs to another lease");
        }
        var acknowledgedAt = clock.instant();
        jdbc.update("""
                UPDATE work_task_workspace_replacement SET stop_acknowledged_at_epoch_ms=?
                 WHERE id=? AND status='PENDING_STOP' AND stop_acknowledged_at_epoch_ms IS NULL
                """, acknowledgedAt.toEpochMilli(), pending.id());
        WorkTask source = tasks.find(sourceId).orElseThrow();
        if (source.state() != TaskState.READY || tasks.findLease(sourceId).isPresent()) {
            conflict(pending.id(), "task state changed before stop acknowledgement", clock.instant());
            return;
        }
        // Heartbeats change task version, so compare the immutable instruction revision
        // captured with the request after the ACK has released the old lease.
        if (source.instructionRevision() != pending.sourceInstructionRevision()) {
            conflict(pending.id(), "task instruction changed before stop acknowledgement", clock.instant());
            return;
        }
        try {
            lockAndCheckDependents(sourceId);
        } catch (IllegalStateException conflict) {
            conflict(pending.id(), conflict.getMessage(), clock.instant());
            return;
        }
        complete(pending.id(), source, clock.instant());
    }

    /** Natural completion, failure or requeue wins over a pending replacement. */
    public void conflictIfPending(TaskId sourceId, String reason) {
        var pending = pending(sourceId);
        if (pending != null) conflict(pending.id(), reason, clock.instant());
    }

    /** Record an operator request rejected by validation or a concurrent state change. */
    @Transactional
    public void recordRejected(String runId, TaskId sourceId, String repositoryRef, String revision,
                               String actor, String reason, String conflictReason) {
        var source = tasks.find(sourceId).orElse(null);
        if (source == null || !"AUTONOMOUS_REQUIREMENT".equals(source.subjectType())
                || !runId.equals(source.subjectId())) return;
        var now = clock.instant();
        jdbc.update("""
                INSERT INTO work_task_workspace_replacement(
                  id,source_task_id,status,actor,reason,old_repository_ref,old_base_revision,
                  new_repository_ref,requested_revision,source_version,source_instruction_revision,conflict_reason,
                  requested_at_epoch_ms,resolved_at_epoch_ms)
                VALUES(?,?,'REJECTED',?,?,?,?,?,?,?,?,?,?,?)
                """, "replacement-" + UUID.randomUUID(), sourceId.value(),
                auditText(actor, "unknown", 256), auditText(reason, "No reason supplied", 2000),
                source.repositoryRef(), source.baseRevision(), auditText(repositoryRef, null, 120),
                auditText(revision, null, 256), source.version(), source.instructionRevision(),
                auditText(conflictReason, "Request rejected", 2000), now.toEpochMilli(), now.toEpochMilli());
    }

    private void complete(String id, WorkTask source, Instant now) {
        var pending = pending(source.id());
        if (pending == null || !pending.id().equals(id)) throw new IllegalStateException("replacement request is no longer pending");
        List<TaskId> dependents = lockAndCheckDependents(source.id());
        if (!tasks.cancelInactive(source.id(), source.state(), now)) {
            throw new IllegalStateException("source task changed before Workspace replacement");
        }
        TaskId replacementId = new TaskId("replacement-" + UUID.randomUUID());
        Set<String> capabilities = new LinkedHashSet<>();
        source.requiredCapabilities().stream().filter(value -> !value.startsWith("repo:")).forEach(capabilities::add);
        WorkTask replacement = taskService.createReplacing(new CreateWorkTaskCommand(replacementId, source.type(),
                source.subjectType(), source.subjectId(), capabilities, source.skillRef(), source.instruction(),
                pending.newRepositoryRef(), pending.newBaseRevision(), source.priority(),
                Set.copyOf(dependencies.findDependencies(source.id()))),source.id());
        for (TaskId dependentId : dependents) {
            if (jdbc.update("""
                    UPDATE work_task_dependency SET depends_on_task_id=?
                     WHERE task_id=? AND depends_on_task_id=?
                    """, replacement.id().value(), dependentId.value(), source.id().value()) != 1) {
                throw new IllegalStateException("dependent task graph changed during Workspace replacement");
            }
        }
        if (jdbc.update("""
                UPDATE work_task_workspace_replacement
                   SET status='COMPLETED',replacement_task_id=?,resolved_at_epoch_ms=?
                 WHERE id=? AND status='PENDING_STOP'
                """, replacement.id().value(), now.toEpochMilli(), id) != 1) {
            throw new IllegalStateException("replacement request changed during completion");
        }
        tasks.recordControlEvent(UUID.randomUUID().toString(), source.id(), null,
                "WORKSPACE_REPLACED", pending.actor(), pending.reason(), false, now);
    }

    private List<TaskId> lockAndCheckDependents(TaskId sourceId) {
        List<TaskId> dependents = dependencies.findDependents(sourceId).stream()
                .sorted((a, b) -> a.value().compareTo(b.value())).toList();
        for (TaskId id : dependents) {
            if (!tasks.lock(id)) throw new IllegalStateException("dependent task disappeared: " + id.value());
            WorkTask task = tasks.find(id).orElseThrow();
            Integer attemptsCount = jdbc.queryForObject("SELECT COUNT(*) FROM execution_attempt WHERE task_id=?",
                    Integer.class, id.value());
            if ((task.state() != TaskState.BLOCKED && task.state() != TaskState.READY)
                    || tasks.findLease(id).isPresent() || (attemptsCount != null && attemptsCount > 0)) {
                throw new IllegalStateException("downstream task has already started: " + id.value());
            }
        }
        return dependents;
    }

    private void conflict(String id, String reason, Instant now) {
        jdbc.update("""
                UPDATE work_task_workspace_replacement
                   SET status='CONFLICT',conflict_reason=?,resolved_at_epoch_ms=?
                 WHERE id=? AND status='PENDING_STOP'
                """, reason, now.toEpochMilli(), id);
    }

    private ReplacementRow pending(TaskId sourceId) {
        return queryRow(sourceId, "AND status='PENDING_STOP'");
    }

    private Replacement latestAccepted(TaskId sourceId) {
        var row = acceptedRow(sourceId);
        if (row == null) return null;
        return forRun(tasks.find(sourceId).orElseThrow().subjectId()).stream()
                .filter(item -> item.id().equals(row.id())).findFirst().orElseThrow();
    }

    private ReplacementRow acceptedRow(TaskId sourceId) {
        return queryRow(sourceId, "AND status IN ('PENDING_STOP','COMPLETED')");
    }

    private Replacement latest(TaskId sourceId) {
        var row = latestRow(sourceId);
        if (row == null) return null;
        return forRun(tasks.find(sourceId).orElseThrow().subjectId()).stream()
                .filter(item -> item.id().equals(row.id())).findFirst().orElseThrow();
    }

    private ReplacementRow latestRow(TaskId sourceId) {
        return queryRow(sourceId, "");
    }

    private ReplacementRow queryRow(TaskId sourceId, String statusFilter) {
        return jdbc.query("""
                SELECT id,status,actor,reason,new_repository_ref,new_base_revision,requested_revision,
                       source_instruction_revision,lease_worker_id,lease_token
                  FROM work_task_workspace_replacement WHERE source_task_id=?
                """ + statusFilter + " ORDER BY requested_at_epoch_ms DESC,id DESC LIMIT 1",
                rs -> rs.next() ? new ReplacementRow(rs.getString("id"), rs.getString("status"),
                rs.getString("actor"), rs.getString("reason"), rs.getString("new_repository_ref"),
                rs.getString("new_base_revision"), rs.getString("requested_revision"),
                rs.getInt("source_instruction_revision"),
                rs.getString("lease_worker_id"), rs.getString("lease_token"))
                : null, sourceId.value());
    }

    private record ReplacementRow(String id, String status, String actor, String reason,
                                  String newRepositoryRef, String newBaseRevision, String requestedRevision,
                                  int sourceInstructionRevision,
                                  String leaseWorker, String leaseToken) {}

    private static String auditText(String value, String fallback, int maxLength) {
        if (value == null || value.isBlank()) return fallback;
        String text = value.strip();
        return text.substring(0, Math.min(text.length(), maxLength));
    }

    private static String required(String value, String name, int maxLength) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        String normalized = value.strip();
        if (normalized.length() > maxLength) throw new IllegalArgumentException(name + " is too long");
        return normalized;
    }
}
