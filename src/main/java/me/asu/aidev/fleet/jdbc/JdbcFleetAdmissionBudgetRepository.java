package me.asu.aidev.fleet.jdbc;

import me.asu.aidev.fleet.domain.FleetAdmissionBudget;
import me.asu.aidev.fleet.port.FleetAdmissionBudgetRepository;
import me.asu.aidev.work.jdbc.WorkDatabaseDialect;
import java.time.Instant;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcFleetAdmissionBudgetRepository implements FleetAdmissionBudgetRepository {
    private static final String GLOBAL = "GLOBAL";

    private final JdbcTemplate jdbc;
    private final DataSource dataSource;
    private final WorkDatabaseDialect dialect;

    public JdbcFleetAdmissionBudgetRepository(JdbcTemplate jdbc, DataSource dataSource, WorkDatabaseDialect dialect) {
        this.jdbc = jdbc;
        this.dataSource = dataSource;
        this.dialect = dialect;
    }

    @Override
    public boolean configureIfUninitialized(int maxParallelism, Instant updatedAt) {
        validate(maxParallelism);
        return jdbc.update("""
                UPDATE fleet_admission_budget
                   SET max_parallelism=?, updated_at_epoch_ms=?
                 WHERE id=? AND updated_at_epoch_ms=0
                """, maxParallelism, updatedAt.toEpochMilli(), GLOBAL) == 1;
    }

    @Override
    public FleetAdmissionBudget lockAndSnapshot() {
        lockGlobal();
        return snapshot();
    }

    @Override
    public boolean updateMaxParallelism(int maxParallelism, Instant updatedAt) {
        validate(maxParallelism);
        return jdbc.update("""
                UPDATE fleet_admission_budget
                   SET max_parallelism=?, updated_at_epoch_ms=?
                 WHERE id=?
                """, maxParallelism, updatedAt.toEpochMilli(), GLOBAL) == 1;
    }

    @Override
    public boolean tryAdmit() {
        lockGlobal();
        Integer max = jdbc.queryForObject(
                "SELECT max_parallelism FROM fleet_admission_budget WHERE id=?", Integer.class, GLOBAL);
        if (max == null) throw new IllegalStateException("GLOBAL fleet admission budget row is missing");
        if (max == 0) return true;

        Integer active = liveActiveLeases();
        return active != null && active < max;
    }

    @Override
    public FleetAdmissionBudget snapshot() {
        return jdbc.queryForObject("""
                SELECT b.max_parallelism,b.updated_at_epoch_ms,
                       (SELECT COUNT(*) FROM work_task WHERE state IN ('CLAIMED','RUNNING')) AS active_leases
                  FROM fleet_admission_budget b
                 WHERE b.id=?
                """, (rs, rowNum) -> new FleetAdmissionBudget(
                rs.getInt("max_parallelism"), rs.getInt("active_leases"),
                Instant.ofEpochMilli(rs.getLong("updated_at_epoch_ms"))), GLOBAL);
    }

    private void lockGlobal() {
        var connection = DataSourceUtils.getConnection(dataSource);
        try {
            if (!dialect.lockFleetAdmissionBudget(connection)) {
                throw new IllegalStateException("GLOBAL fleet admission budget row is missing");
            }
        } catch (java.sql.SQLException e) {
            throw new IllegalStateException("cannot lock GLOBAL fleet admission budget", e);
        }
    }

    private Integer liveActiveLeases() {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM work_task WHERE state IN ('CLAIMED','RUNNING')", Integer.class);
    }

    private static void validate(int maxParallelism) {
        if (maxParallelism < 0 || maxParallelism > 65535) {
            throw new IllegalArgumentException("fleet maxParallelism must be between 0 and 65535");
        }
    }
}
