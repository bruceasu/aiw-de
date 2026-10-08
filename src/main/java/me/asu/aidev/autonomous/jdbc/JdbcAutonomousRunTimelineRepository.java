package me.asu.aidev.autonomous.jdbc;

import me.asu.aidev.autonomous.domain.AutonomousRunTimelineCategory;
import me.asu.aidev.autonomous.domain.AutonomousRunTimelineEntry;
import me.asu.aidev.autonomous.domain.AutonomousRunTimelineEventType;
import me.asu.aidev.autonomous.port.AutonomousRunTimelineRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcAutonomousRunTimelineRepository implements AutonomousRunTimelineRepository {
    private static final String SUBJECT_TYPE = "AUTONOMOUS_REQUIREMENT";
    private final JdbcTemplate jdbc;

    public JdbcAutonomousRunTimelineRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<AutonomousRunTimelineEntry> findProjectedEntries(String runId) {
        List<AutonomousRunTimelineEntry> entries = new ArrayList<>();
        entries.addAll(taskEntries(runId));
        entries.addAll(attemptEntries(runId));
        entries.addAll(approvalEntries(runId));
        entries.addAll(runEventEntries(runId));
        return entries;
    }

    private List<AutonomousRunTimelineEntry> taskEntries(String runId) {
        return jdbc.query("""
                SELECT id,task_type,created_at_epoch_ms
                  FROM work_task
                 WHERE subject_type=? AND subject_id=?
                """, (rs, rowNum) -> new AutonomousRunTimelineEntry(
                Instant.ofEpochMilli(rs.getLong("created_at_epoch_ms")),
                AutonomousRunTimelineCategory.TASK,
                AutonomousRunTimelineEventType.TASK_CREATED,
                rs.getString("id"), rs.getString("task_type"), null, null, null,
                null, null, null, null, null), SUBJECT_TYPE, runId);
    }

    private List<AutonomousRunTimelineEntry> attemptEntries(String runId) {
        List<AutonomousRunTimelineEntry> entries = new ArrayList<>();
        jdbc.query("""
                SELECT ea.task_id,wt.task_type,ea.attempt_no,ea.worker_id,ea.state,
                       ea.created_at_epoch_ms,ea.started_at_epoch_ms,ea.finished_at_epoch_ms,
                       ea.termination_reason,ea.completion_verdict,ea.result_revision,ea.publication_ref,
                       ea.changed_files,ea.engineering_gate_status,ea.engineering_gate_review_task_id,
                       ea.engineering_gate_verification_task_id,ea.engineering_gate_closed_at_epoch_ms
                  FROM execution_attempt ea
                  JOIN work_task wt ON wt.id=ea.task_id
                 WHERE wt.subject_type=? AND wt.subject_id=?
                """, rs -> {
            while (rs.next()) addAttempt(entries, rs);
            return null;
        }, SUBJECT_TYPE, runId);
        return entries;
    }

    private static void addAttempt(List<AutonomousRunTimelineEntry> entries, ResultSet rs) throws SQLException {
        String taskId = rs.getString("task_id");
        String taskType = rs.getString("task_type");
        int attemptNo = rs.getInt("attempt_no");
        String workerId = rs.getString("worker_id");
        String resultRevision = rs.getString("result_revision");
        String publicationRef = rs.getString("publication_ref");

        entries.add(new AutonomousRunTimelineEntry(
                Instant.ofEpochMilli(rs.getLong("created_at_epoch_ms")),
                AutonomousRunTimelineCategory.EXECUTION,
                AutonomousRunTimelineEventType.ATTEMPT_CLAIMED,
                taskId, taskType, attemptNo, workerId, null, "CLAIMED", null,
                null, null, null));

        Number startedAt = number(rs, "started_at_epoch_ms");
        if (startedAt != null) {
            entries.add(new AutonomousRunTimelineEntry(
                    Instant.ofEpochMilli(startedAt.longValue()),
                    AutonomousRunTimelineCategory.EXECUTION,
                    AutonomousRunTimelineEventType.ATTEMPT_STARTED,
                    taskId, taskType, attemptNo, workerId, null, "RUNNING", null,
                    null, null, null));
        }

        Number finishedAt = number(rs, "finished_at_epoch_ms");
        String finalState = rs.getString("state");
        if (finishedAt != null && ("COMPLETED".equals(finalState) || "FAILED".equals(finalState) || "EXPIRED".equals(finalState))) {
            AutonomousRunTimelineEventType finalType = switch (finalState) {
                case "COMPLETED" -> AutonomousRunTimelineEventType.ATTEMPT_COMPLETED;
                case "FAILED" -> AutonomousRunTimelineEventType.ATTEMPT_FAILED;
                default -> AutonomousRunTimelineEventType.ATTEMPT_EXPIRED;
            };
            String detail = finishDetail(rs, finalState);
            entries.add(new AutonomousRunTimelineEntry(
                    Instant.ofEpochMilli(finishedAt.longValue()),
                    AutonomousRunTimelineCategory.EXECUTION,
                    finalType,
                    taskId, taskType, attemptNo, workerId, null, finalState, null,
                    detail, resultRevision, publicationRef));
        }

        Number gateAt = number(rs, "engineering_gate_closed_at_epoch_ms");
        String gateStatus = rs.getString("engineering_gate_status");
        if (gateAt != null && gateStatus != null) {
            var gateType = "PASSED".equals(gateStatus)
                    ? AutonomousRunTimelineEventType.ENGINEERING_GATE_PASSED
                    : AutonomousRunTimelineEventType.ENGINEERING_GATE_REPAIR_REQUIRED;
            String detail = "review=" + rs.getString("engineering_gate_review_task_id")
                    + ", verification=" + rs.getString("engineering_gate_verification_task_id");
            entries.add(new AutonomousRunTimelineEntry(
                    Instant.ofEpochMilli(gateAt.longValue()),
                    AutonomousRunTimelineCategory.ENGINEERING_GATE,
                    gateType,
                    taskId, taskType, attemptNo, workerId, null, gateStatus, null,
                    detail, resultRevision, publicationRef));
        }
    }

    private List<AutonomousRunTimelineEntry> approvalEntries(String runId) {
        List<AutonomousRunTimelineEntry> entries = new ArrayList<>();
        jdbc.query("""
                SELECT id,source_task_id,status,requested_by,requested_at_epoch_ms,
                       decided_by,decision_note,decided_at_epoch_ms,result_revision
                  FROM approval_request
                 WHERE subject_type=? AND subject_id=?
                """, rs -> {
            while (rs.next()) {
                String id = rs.getString("id");
                String taskId = rs.getString("source_task_id");
                String status = rs.getString("status");
                String resultRevision = rs.getString("result_revision");
                entries.add(new AutonomousRunTimelineEntry(
                        Instant.ofEpochMilli(rs.getLong("requested_at_epoch_ms")),
                        AutonomousRunTimelineCategory.APPROVAL,
                        AutonomousRunTimelineEventType.APPROVAL_REQUESTED,
                        taskId, null, null, null, id, "PENDING", rs.getString("requested_by"),
                        null, resultRevision, null));
                Number decidedAt = number(rs, "decided_at_epoch_ms");
                if (decidedAt != null) {
                    var eventType = "APPROVED".equals(status)
                            ? AutonomousRunTimelineEventType.APPROVAL_APPROVED
                            : AutonomousRunTimelineEventType.APPROVAL_REJECTED;
                    entries.add(new AutonomousRunTimelineEntry(
                            Instant.ofEpochMilli(decidedAt.longValue()),
                            AutonomousRunTimelineCategory.APPROVAL,
                            eventType,
                            taskId, null, null, null, id, status, rs.getString("decided_by"),
                            rs.getString("decision_note"), resultRevision, null));
                }
            }
            return null;
        }, SUBJECT_TYPE, runId);
        return entries;
    }

    private List<AutonomousRunTimelineEntry> runEventEntries(String runId) {
        return jdbc.query("""
                SELECT sequence_no,event_type,actor,related_task_id,detail,occurred_at_epoch_ms
                  FROM autonomous_run_event
                 WHERE run_id=?
                """, (rs, rowNum) -> {
            String type = rs.getString("event_type");
            AutonomousRunTimelineEventType eventType = switch (type) {
                case "FAILED" -> AutonomousRunTimelineEventType.RUN_FAILED;
                case "RECOVERED" -> AutonomousRunTimelineEventType.RUN_RECOVERED;
                case "PAUSED" -> AutonomousRunTimelineEventType.RUN_PAUSED;
                case "RESUMED" -> AutonomousRunTimelineEventType.RUN_RESUMED;
                case "CANCELLED" -> AutonomousRunTimelineEventType.RUN_CANCELLED;
                case "MAX_PARALLELISM_CHANGED" -> AutonomousRunTimelineEventType.RUN_MAX_PARALLELISM_CHANGED;
                default -> throw new IllegalStateException("unknown autonomous run event type: " + type);
            };
            var category = switch (type) {
                case "PAUSED", "RESUMED", "CANCELLED" -> AutonomousRunTimelineCategory.OPERATOR;
                case "MAX_PARALLELISM_CHANGED" -> AutonomousRunTimelineCategory.SCHEDULING;
                default -> AutonomousRunTimelineCategory.RUN;
            };
            String state = switch (type) {
                case "FAILED" -> "FAILED";
                case "RECOVERED", "RESUMED" -> "RUNNING";
                case "PAUSED" -> "PAUSED";
                case "CANCELLED" -> "CANCELLED";
                case "MAX_PARALLELISM_CHANGED" -> "SCHEDULING_CHANGED";
                default -> throw new IllegalStateException("unknown autonomous run event type: " + type);
            };
            return new AutonomousRunTimelineEntry(
                    Instant.ofEpochMilli(rs.getLong("occurred_at_epoch_ms")), category, eventType,
                    rs.getLong("sequence_no"), rs.getString("related_task_id"), null, null, null, null, state,
                    rs.getString("actor"), rs.getString("detail"), null, null);
        }, runId);
    }

    private static String finishDetail(ResultSet rs, String finalState) throws SQLException {
        if ("EXPIRED".equals(finalState)) return nullablePair("terminationReason", rs.getString("termination_reason"));
        if ("COMPLETED".equals(finalState)) {
            String verdict = rs.getString("completion_verdict");
            String changedFiles = rs.getString("changed_files");
            int changedFileCount = changedFiles == null || changedFiles.isBlank() ? 0 : changedFiles.split("\\n").length;
            if (verdict == null && changedFileCount == 0) return null;
            return "verdict=" + (verdict == null ? "-" : verdict) + ", changedFiles=" + changedFileCount;
        }
        return null;
    }

    private static String nullablePair(String key, String value) {
        return value == null ? null : key + "=" + value;
    }

    private static Number number(ResultSet rs, String column) throws SQLException {
        return (Number) rs.getObject(column);
    }
}
