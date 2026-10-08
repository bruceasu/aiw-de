package me.asu.aidev.approval.jdbc;

import me.asu.aidev.approval.domain.ApprovalChannel;
import me.asu.aidev.approval.domain.ApprovalRequest;
import me.asu.aidev.approval.domain.ApprovalRequestId;
import me.asu.aidev.approval.domain.ApprovalStatus;
import me.asu.aidev.approval.port.ApprovalRequestRepository;
import me.asu.aidev.work.domain.TaskId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcApprovalRequestRepository implements ApprovalRequestRepository {
    private final JdbcTemplate jdbc;

    public JdbcApprovalRequestRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean insertIfAbsent(ApprovalRequest request) {
        return jdbc.update("""
                INSERT INTO approval_request(
                    id,source_task_id,subject_type,subject_id,result_revision,
                    engineering_gate_closed_at_epoch_ms,status,requested_by,channel,channel_ref,
                    requested_at_epoch_ms,decided_by,decision_note,decided_at_epoch_ms
                ) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                ON CONFLICT(source_task_id) DO NOTHING
                """,
                request.id().value(), request.sourceTaskId().value(), request.subjectType(), request.subjectId(),
                request.resultRevision() == null ? "" : request.resultRevision(), request.engineeringGateClosedAt().toEpochMilli(), request.status().name(),
                request.requestedBy(), request.channel().name(), request.channelRef(), request.requestedAt().toEpochMilli(),
                request.decidedBy(), request.decisionNote(), request.decidedAt() == null ? null : request.decidedAt().toEpochMilli()) == 1;
    }

    @Override
    public Optional<ApprovalRequest> find(ApprovalRequestId id) {
        List<ApprovalRequest> rows = jdbc.query("SELECT * FROM approval_request WHERE id=?",
                (rs, rowNum) -> map(rs), id.value());
        return rows.stream().findFirst();
    }

    @Override
    public Optional<ApprovalRequest> findBySourceTask(TaskId sourceTaskId) {
        List<ApprovalRequest> rows = jdbc.query("SELECT * FROM approval_request WHERE source_task_id=?",
                (rs, rowNum) -> map(rs), sourceTaskId.value());
        return rows.stream().findFirst();
    }

    @Override
    public boolean decide(ApprovalRequestId id, ApprovalStatus expectedStatus, ApprovalStatus newStatus,
                          String decidedBy, String note, Instant decidedAt) {
        return jdbc.update("""
                UPDATE approval_request
                   SET status=?, decided_by=?, decision_note=?, decided_at_epoch_ms=?
                 WHERE id=? AND status=?
                """, newStatus.name(), decidedBy, note, decidedAt.toEpochMilli(), id.value(), expectedStatus.name()) == 1;
    }

    private static ApprovalRequest map(ResultSet rs) throws SQLException {
        Number decidedAt = (Number) rs.getObject("decided_at_epoch_ms");
        return new ApprovalRequest(
                new ApprovalRequestId(rs.getString("id")),
                new TaskId(rs.getString("source_task_id")),
                rs.getString("subject_type"),
                rs.getString("subject_id"),
                rs.getString("result_revision"),
                Instant.ofEpochMilli(rs.getLong("engineering_gate_closed_at_epoch_ms")),
                ApprovalStatus.valueOf(rs.getString("status")),
                rs.getString("requested_by"),
                ApprovalChannel.valueOf(rs.getString("channel")),
                rs.getString("channel_ref"),
                Instant.ofEpochMilli(rs.getLong("requested_at_epoch_ms")),
                rs.getString("decided_by"),
                rs.getString("decision_note"),
                decidedAt == null ? null : Instant.ofEpochMilli(decidedAt.longValue()));
    }
}
