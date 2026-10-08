package me.asu.aidev.work.jdbc;

import me.asu.aidev.work.domain.TaskId;
import java.sql.Connection;
import java.sql.SQLException;

public interface WorkDatabaseDialect {
    boolean lockTask(Connection connection, TaskId id) throws SQLException;
    boolean lockAutonomousRun(Connection connection, String runId) throws SQLException;
    boolean lockFleetAdmissionBudget(Connection connection) throws SQLException;
}
