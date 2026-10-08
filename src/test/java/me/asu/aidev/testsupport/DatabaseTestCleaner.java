package me.asu.aidev.testsupport;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Clears mutable R3+ business state while preserving the Flyway-created schema.
 *
 * Integration test classes intentionally reuse one Spring ApplicationContext and one
 * SQLite database for speed, so every test method must start from an empty business state.
 */
public final class DatabaseTestCleaner {
    private DatabaseTestCleaner() {
    }

    public static void clear(JdbcTemplate jdbc) {
        jdbc.update("DELETE FROM autonomous_run_event");
        jdbc.update("DELETE FROM autonomous_run");
        jdbc.update("DELETE FROM approval_request");
        jdbc.update("DELETE FROM work_task_dependency");
        jdbc.update("DELETE FROM execution_attempt");
        jdbc.update("DELETE FROM work_task");
        jdbc.update("DELETE FROM worker");
    }
}
