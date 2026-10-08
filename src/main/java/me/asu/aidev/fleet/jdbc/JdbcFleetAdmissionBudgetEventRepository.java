package me.asu.aidev.fleet.jdbc;

import me.asu.aidev.fleet.domain.FleetAdmissionBudgetEvent;
import me.asu.aidev.fleet.domain.FleetAdmissionBudgetEventType;
import me.asu.aidev.fleet.port.FleetAdmissionBudgetEventRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcFleetAdmissionBudgetEventRepository implements FleetAdmissionBudgetEventRepository {
    private static final String GLOBAL = "GLOBAL";
    private final JdbcTemplate jdbc;

    public JdbcFleetAdmissionBudgetEventRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public FleetAdmissionBudgetEvent append(
            FleetAdmissionBudgetEventType type,
            String actor,
            int previousMaxParallelism,
            int maxParallelism,
            int activeLeases,
            String reason,
            Instant occurredAt) {
        Long previous = jdbc.queryForObject(
                "SELECT COALESCE(MAX(sequence_no),0) FROM fleet_admission_budget_event WHERE budget_id=?",
                Long.class, GLOBAL);
        long sequence = (previous == null ? 0L : previous) + 1L;
        var event = new FleetAdmissionBudgetEvent(
                sequence, type, actor, previousMaxParallelism, maxParallelism, activeLeases, reason, occurredAt);
        int inserted = jdbc.update("""
                INSERT INTO fleet_admission_budget_event(
                    budget_id,sequence_no,event_type,actor,previous_max_parallelism,max_parallelism,
                    active_leases,reason,occurred_at_epoch_ms
                ) VALUES(?,?,?,?,?,?,?,?,?)
                """, GLOBAL, event.sequenceNo(), event.type().name(), event.actor(),
                event.previousMaxParallelism(), event.maxParallelism(), event.activeLeases(), event.reason(),
                event.occurredAt().toEpochMilli());
        if (inserted != 1) throw new IllegalStateException("cannot append fleet admission budget event #" + sequence);
        return event;
    }

    @Override
    public List<FleetAdmissionBudgetEvent> findRecent(int limit) {
        if (limit < 1 || limit > 500) throw new IllegalArgumentException("limit must be between 1 and 500");
        return jdbc.query("""
                SELECT sequence_no,event_type,actor,previous_max_parallelism,max_parallelism,
                       active_leases,reason,occurred_at_epoch_ms
                  FROM fleet_admission_budget_event
                 WHERE budget_id=?
                 ORDER BY sequence_no DESC
                 LIMIT ?
                """, (rs, rowNum) -> new FleetAdmissionBudgetEvent(
                rs.getLong("sequence_no"), FleetAdmissionBudgetEventType.valueOf(rs.getString("event_type")),
                rs.getString("actor"), rs.getInt("previous_max_parallelism"), rs.getInt("max_parallelism"),
                rs.getInt("active_leases"), rs.getString("reason"),
                Instant.ofEpochMilli(rs.getLong("occurred_at_epoch_ms"))), GLOBAL, limit);
    }
}
