package me.asu.aidev.work.jdbc;

import me.asu.aidev.work.domain.TaskId;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public final class PostgresWorkDatabaseDialect implements WorkDatabaseDialect {
    @Override
    public boolean lockTask(Connection connection, TaskId id) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("SELECT id FROM work_task WHERE id=? FOR UPDATE")) {
            ps.setString(1, id.value());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }
    @Override
    public boolean lockAutonomousRun(Connection connection, String runId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("SELECT id FROM autonomous_run WHERE id=? FOR UPDATE")) {
            ps.setString(1, runId);
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        }
    }
    @Override
    public boolean lockFleetAdmissionBudget(Connection connection) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT id FROM fleet_admission_budget WHERE id='GLOBAL' FOR UPDATE")) {
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        }
    }
}

