package me.asu.aidev.work.jdbc;

import me.asu.aidev.work.domain.TaskId;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public final class SqliteWorkDatabaseDialect implements WorkDatabaseDialect {
    @Override
    public boolean lockTask(Connection connection, TaskId id) throws SQLException {
        // With transaction_mode=IMMEDIATE SQLite serializes writers. This no-op update
        // also guarantees the target row participates in the write transaction.
        try (PreparedStatement ps = connection.prepareStatement("UPDATE work_task SET version=version WHERE id=?")) {
            ps.setString(1, id.value());
            return ps.executeUpdate() == 1;
        }
    }
    @Override
    public boolean lockAutonomousRun(Connection connection, String runId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE autonomous_run SET status_updated_at_epoch_ms=status_updated_at_epoch_ms WHERE id=?")) {
            ps.setString(1, runId);
            return ps.executeUpdate() == 1;
        }
    }
    @Override
    public boolean lockFleetAdmissionBudget(Connection connection) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE fleet_admission_budget SET updated_at_epoch_ms=updated_at_epoch_ms WHERE id='GLOBAL'")) {
            return ps.executeUpdate() == 1;
        }
    }
}

