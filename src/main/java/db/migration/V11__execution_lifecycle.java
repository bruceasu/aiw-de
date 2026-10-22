package db.migration;

import java.sql.*;
import java.util.*;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/** Additive fresh-install migration. Never copies, deletes or silently upgrades old runtime records. */
public class V11__execution_lifecycle extends BaseJavaMigration {
    @Override public boolean canExecuteInTransaction() { return false; }
    @Override public void migrate(Context context) throws Exception {
        var connection=context.getConnection();
        try(var statement=connection.createStatement()) {
            for(String table:List.of("work_task","execution_attempt"))
                try(var rows=statement.executeQuery("SELECT COUNT(*) FROM "+table)) {
                    if(!rows.next() || rows.getLong(1)!=0) throw new SQLException("Execution guard requires a fresh runtime DB; user-controlled reset only");
                }
        }
        var product=connection.getMetaData().getDatabaseProductName();
        Map<String,String> states=new LinkedHashMap<>();
        states.put("work_task","'BLOCKED','READY','CLAIMED','RUNNING','YIELDING','WAITING_INPUT','CONTINUED','COMPLETED','FAILED','CANCELLED'");
        states.put("execution_attempt","'CLAIMED','RUNNING','YIELDED','COMPLETED','FAILED','EXPIRED'");
        if(product.equals("SQLite")) sqlite(connection,states);
        else if(product.equals("PostgreSQL")) postgres(connection,states);
        else throw new SQLException("Unsupported lifecycle database: "+product);
    }
    private void sqlite(Connection c,Map<String,String> states) throws SQLException {
        try(var s=c.createStatement()) {
            int foreignKeys;
            try(var r=s.executeQuery("PRAGMA foreign_keys")) { foreignKeys=r.next()?r.getInt(1):0; }
            s.execute("PRAGMA foreign_keys=OFF");
            try {
                s.execute("BEGIN IMMEDIATE");
                for(var entry:states.entrySet()) {
                    String table=entry.getKey();String ddl;
                    try(var query=c.prepareStatement("SELECT sql FROM sqlite_master WHERE type='table' AND name=?")) {
                        query.setString(1,table);try(var r=query.executeQuery()) { if(!r.next()) throw new SQLException("Missing lifecycle table");ddl=r.getString(1); }
                    }
                    var indexes=new ArrayList<String>();
                    try(var query=c.prepareStatement("SELECT sql FROM sqlite_master WHERE type='index' AND tbl_name=? AND sql IS NOT NULL")) {
                        query.setString(1,table);try(var r=query.executeQuery()) { while(r.next()) indexes.add(r.getString(1)); }
                    }
                    var pattern=java.util.regex.Pattern.compile("(?i)CHECK\\s*\\(\\s*state\\s+IN\\s*\\([^)]*\\)\\s*\\)");
                    var matcher=pattern.matcher(ddl);if(!matcher.find()) throw new SQLException("Unrecognized lifecycle CHECK");
                    String updated=matcher.replaceFirst("CHECK (state IN ("+entry.getValue()+"))");
                    if(matcher.find()) throw new SQLException("Ambiguous lifecycle CHECK");
                    String temporary=table+"_guard_new";
                    updated=updated.replaceFirst("(?i)CREATE TABLE\\s+[\"`\\[]?"+table+"[\"`\\]]?", "CREATE TABLE "+temporary);
                    if(updated.equals(ddl) || !updated.contains("CREATE TABLE "+temporary)) throw new SQLException("Unrecognized lifecycle DDL");
                    // Empty tables only: keep every current column and constraint, and original FK names.
                    s.execute(updated);s.execute("DROP TABLE "+table);s.execute("ALTER TABLE "+temporary+" RENAME TO "+table);
                    for(String index:indexes) s.execute(index);
                }
                try(var r=s.executeQuery("PRAGMA foreign_key_check")) { if(r.next()) throw new SQLException("Lifecycle FK check failed"); }
                s.execute("COMMIT");
            } catch(SQLException e) { try { s.execute("ROLLBACK"); } catch(SQLException rollback) { e.addSuppressed(rollback); } throw e; }
            finally { s.execute("PRAGMA foreign_keys="+foreignKeys); }
        }
    }
    private void postgres(Connection c,Map<String,String> states) throws SQLException {
        boolean auto=c.getAutoCommit();c.setAutoCommit(false);
        try {
            for(var entry:states.entrySet()) {
                var names=new ArrayList<String>();
                try(var q=c.prepareStatement("""
                        SELECT conname FROM pg_constraint WHERE conrelid=CAST(? AS regclass) AND contype='c'
                        AND cardinality(conkey)=1 AND conkey[1]=(SELECT attnum FROM pg_attribute WHERE attrelid=CAST(? AS regclass) AND attname='state')
                        """)) {
                    q.setString(1,entry.getKey());q.setString(2,entry.getKey());try(var r=q.executeQuery()) { while(r.next()) names.add(r.getString(1)); }
                }
                if(names.size()!=1) throw new SQLException("Unrecognized lifecycle state constraint");
                try(var s=c.createStatement()) {
                    s.execute("ALTER TABLE "+entry.getKey()+" DROP CONSTRAINT \""+names.getFirst().replace("\"","\"\"")+"\"");
                    s.execute("ALTER TABLE "+entry.getKey()+" ADD CONSTRAINT "+entry.getKey()+"_guard_state CHECK (state IN ("+entry.getValue()+"))");
                }
            }
            c.commit();
        } catch(SQLException e) { c.rollback();throw e; } finally { c.setAutoCommit(auto); }
    }
}
