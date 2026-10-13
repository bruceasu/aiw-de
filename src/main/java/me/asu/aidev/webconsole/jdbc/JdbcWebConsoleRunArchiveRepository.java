package me.asu.aidev.webconsole.jdbc;

import java.time.Instant;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Presentation-only archive state; authoritative Run and task rows are retained. */
@Repository
public class JdbcWebConsoleRunArchiveRepository {
    private final JdbcTemplate jdbc;

    public JdbcWebConsoleRunArchiveRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean isArchived(String runId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM web_console_run_archive WHERE run_id=?", Integer.class, runId);
        return count != null && count > 0;
    }

    public boolean archive(String runId, String actor, Instant at) {
        return jdbc.update("""
                INSERT INTO web_console_run_archive(run_id, archived_by, archived_at_epoch_ms)
                SELECT id, ?, ? FROM autonomous_run
                 WHERE id=? AND active_slots=0
                   AND (control_state='CANCELLED' OR status IN ('SUCCEEDED','REJECTED'))
                   AND NOT EXISTS (SELECT 1 FROM web_console_run_archive WHERE run_id=?)
                """, actor, at.toEpochMilli(), runId, runId) == 1;
    }

    public Optional<Instant> deleteAfter(String runId) {
        var rows = jdbc.query("SELECT delete_after_epoch_ms FROM web_console_run_archive WHERE run_id=?",
                (rs, row) -> {
                    Number value = (Number) rs.getObject(1);
                    return value == null ? null : Instant.ofEpochMilli(value.longValue());
                }, runId);
        return Optional.ofNullable(rows.isEmpty() ? null : rows.getFirst());
    }

    public boolean scheduleDeletion(String runId, String actor, Instant requestedAt, Instant deleteAfter) {
        return jdbc.update("""
                UPDATE web_console_run_archive
                   SET delete_requested_by=?, delete_requested_at_epoch_ms=?, delete_after_epoch_ms=?
                 WHERE run_id=? AND delete_after_epoch_ms IS NULL
                """, actor, requestedAt.toEpochMilli(), deleteAfter.toEpochMilli(), runId) == 1;
    }

    public boolean undoDeletion(String runId, Instant now) {
        return jdbc.update("""
                UPDATE web_console_run_archive
                   SET delete_requested_by=NULL, delete_requested_at_epoch_ms=NULL, delete_after_epoch_ms=NULL
                 WHERE run_id=? AND delete_after_epoch_ms>?
                """, runId, now.toEpochMilli()) == 1;
    }
}
