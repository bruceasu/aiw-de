package me.asu.aidev.autonomous.jdbc;

import me.asu.aidev.autonomous.domain.AutonomousRunEvent;
import me.asu.aidev.autonomous.domain.AutonomousRunEventType;
import me.asu.aidev.autonomous.port.AutonomousRunEventRepository;
import me.asu.aidev.work.domain.TaskId;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcAutonomousRunEventRepository implements AutonomousRunEventRepository {
    private final JdbcTemplate jdbc;

    public JdbcAutonomousRunEventRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public AutonomousRunEvent append(String runId, AutonomousRunEventType type, String actor,
                                     TaskId relatedTaskId, String detail, Instant occurredAt) {
        int advanced = jdbc.update("""
                UPDATE autonomous_run
                   SET event_sequence=event_sequence+1
                 WHERE id=?
                """, runId);
        if (advanced != 1) throw new IllegalStateException("autonomous run not found while appending event: " + runId);
        Long sequence = jdbc.queryForObject(
                "SELECT event_sequence FROM autonomous_run WHERE id=?", Long.class, runId);
        if (sequence == null) throw new IllegalStateException("autonomous run event sequence missing: " + runId);
        var event = new AutonomousRunEvent(runId, sequence, type, actor, relatedTaskId, detail, occurredAt);
        int changed = jdbc.update("""
                INSERT INTO autonomous_run_event(
                    run_id,sequence_no,event_type,actor,related_task_id,detail,occurred_at_epoch_ms
                ) VALUES(?,?,?,?,?,?,?)
                """, event.runId(), event.sequenceNo(), event.type().name(), event.actor(),
                event.relatedTaskId() == null ? null : event.relatedTaskId().value(), event.detail(),
                event.occurredAt().toEpochMilli());
        if (changed != 1) throw new IllegalStateException("cannot append autonomous run event " + runId + "#" + sequence);
        return event;
    }

    @Override
    public List<AutonomousRunEvent> findByRun(String runId) {
        return jdbc.query("""
                SELECT run_id,sequence_no,event_type,actor,related_task_id,detail,occurred_at_epoch_ms
                  FROM autonomous_run_event
                 WHERE run_id=?
                 ORDER BY sequence_no
                """, (rs, rowNum) -> new AutonomousRunEvent(
                rs.getString("run_id"), rs.getLong("sequence_no"),
                AutonomousRunEventType.valueOf(rs.getString("event_type")),
                rs.getString("actor"), taskId(rs.getString("related_task_id")), rs.getString("detail"),
                Instant.ofEpochMilli(rs.getLong("occurred_at_epoch_ms"))), runId);
    }

    private static TaskId taskId(String value) {
        return value == null ? null : new TaskId(value);
    }
}
