package db.migration;

import java.sql.*;
import java.util.*;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/** Preserve existing plans and their references while adding a real terminal state. */
public class V20__formal_factory_plans extends BaseJavaMigration {
    @Override public boolean canExecuteInTransaction() { return false; }
    @Override public void migrate(Context context) throws Exception {
        Connection c = context.getConnection();
        boolean sqlite = c.getMetaData().getDatabaseProductName().equals("SQLite");
        boolean auto = c.getAutoCommit();
        int foreignKeys = 0;
        try (var s = c.createStatement()) {
            if (sqlite) {
                try (var r = s.executeQuery("PRAGMA foreign_keys")) { foreignKeys = r.next() ? r.getInt(1) : 0; }
                s.execute("PRAGMA foreign_keys=OFF");
                s.execute("BEGIN IMMEDIATE");
            } else {
                if (!c.getMetaData().getDatabaseProductName().equals("PostgreSQL")) throw new SQLException("Unsupported plan database");
                c.setAutoCommit(false);
            }
            try {
                if (sqlite) {
                    String ddl;
                    try (var r = s.executeQuery("SELECT sql FROM sqlite_master WHERE type='table' AND name='flexible_workflow_plan'")) {
                        if (!r.next()) throw new SQLException("Missing plan table");
                        ddl = r.getString(1);
                    }
                    var objects = new ArrayList<String>();
                    try (var r = s.executeQuery("SELECT sql FROM sqlite_master WHERE tbl_name='flexible_workflow_plan' AND type IN ('index','trigger') AND sql IS NOT NULL")) {
                        while (r.next()) objects.add(r.getString(1));
                    }
                    var pattern = java.util.regex.Pattern.compile("(?i)CHECK\\s*\\(\\s*state\\s+IN\\s*\\([^)]*\\)\\s*\\)");
                    var matcher = pattern.matcher(ddl);
                    if (!matcher.find()) throw new SQLException("Unrecognized plan state constraint");
                    String updated = matcher.replaceFirst("CHECK (state IN ('DRAFT','CONFIRMED','COMPLETED','WITHDRAWN','STOPPED'))");
                    if (matcher.find()) throw new SQLException("Ambiguous plan state constraint");
                    updated = updated.replaceFirst("(?i)CREATE TABLE\\s+[\"`\\[]?flexible_workflow_plan[\"`\\]]?", "CREATE TABLE flexible_workflow_plan_v20");
                    if (!updated.contains("CREATE TABLE flexible_workflow_plan_v20")) throw new SQLException("Unrecognized plan DDL");
                    s.execute(updated);
                    s.execute("INSERT INTO flexible_workflow_plan_v20 SELECT * FROM flexible_workflow_plan");
                    s.execute("DROP TABLE flexible_workflow_plan");
                    s.execute("ALTER TABLE flexible_workflow_plan_v20 RENAME TO flexible_workflow_plan");
                    for (String object : objects) s.execute(object);
                } else {
                    var names = new ArrayList<String>();
                    try (var r = s.executeQuery("""
                            SELECT conname FROM pg_constraint WHERE conrelid='flexible_workflow_plan'::regclass AND contype='c'
                              AND cardinality(conkey)=1 AND conkey[1]=(SELECT attnum FROM pg_attribute
                                WHERE attrelid='flexible_workflow_plan'::regclass AND attname='state')
                            """)) { while (r.next()) names.add(r.getString(1)); }
                    if (names.size() != 1) throw new SQLException("Unrecognized plan state constraint");
                    s.execute("ALTER TABLE flexible_workflow_plan DROP CONSTRAINT \"" + names.getFirst().replace("\"", "\"\"") + "\"");
                    s.execute("ALTER TABLE flexible_workflow_plan ADD CONSTRAINT flexible_workflow_plan_state_check CHECK (state IN ('DRAFT','CONFIRMED','COMPLETED','WITHDRAWN','STOPPED'))");
                }
                s.execute("""
                        CREATE TABLE workflow_factory_plan (
                          plan_id VARCHAR(128) PRIMARY KEY REFERENCES flexible_workflow_plan(id) ON DELETE CASCADE,
                          original_draft_json TEXT NOT NULL, confirmed_intent_json TEXT NOT NULL,
                          confirmed_by VARCHAR(256) NOT NULL, confirmed_ms BIGINT NOT NULL)
                        """);
                s.execute("""
                        CREATE TABLE workflow_factory_task (
                          task_id VARCHAR(128) PRIMARY KEY REFERENCES work_task(id) ON DELETE CASCADE,
                          plan_id VARCHAR(128) NOT NULL REFERENCES workflow_factory_plan(plan_id) ON DELETE CASCADE,
                          step_key VARCHAR(32) NOT NULL, result_kind VARCHAR(32) NOT NULL,
                          current_task BOOLEAN NOT NULL, created_ms BIGINT NOT NULL)
                        """);
                s.execute("CREATE INDEX idx_workflow_factory_task_plan ON workflow_factory_task(plan_id,current_task)");
                s.execute("CREATE UNIQUE INDEX idx_workflow_factory_current_step ON workflow_factory_task(plan_id,step_key) WHERE current_task=TRUE");
                if (sqlite) {
                    try (var r = s.executeQuery("PRAGMA foreign_key_check")) { if (r.next()) throw new SQLException("Plan migration foreign key check failed"); }
                    s.execute("COMMIT");
                } else c.commit();
            } catch (Exception failure) {
                try { if (sqlite) s.execute("ROLLBACK"); else c.rollback(); }
                catch (SQLException rollback) { failure.addSuppressed(rollback); }
                throw failure;
            } finally {
                if (sqlite) s.execute("PRAGMA foreign_keys=" + foreignKeys);
                else c.setAutoCommit(auto);
            }
        }
    }
}
