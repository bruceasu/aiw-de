# R8-S7 Fix1 - SQLite single-connection dashboard projection

## Symptom

`R8S7RunDashboardIT` returned HTTP 500 after approximately 30 seconds while projecting an active execution.

## Root cause

The SQLite profile intentionally uses a Hikari pool size of 1. The dashboard active-task query invoked `activeStartedAt()` from inside its `RowMapper`. That helper started a second `JdbcTemplate.query()` while the first query still owned the only pooled connection. The nested query therefore waited for Hikari's 30-second connection timeout and the endpoint failed with HTTP 500.

## Fix

The active-execution projection now reads `execution_attempt.started_at_epoch_ms` with a single `LEFT JOIN` on `(task_id, attempt_no)`. No nested JDBC call is performed while a result set is being mapped.

The SQLite single-connection configuration is intentionally unchanged. Increasing the pool size would hide the projection bug and weaken the local SQLite concurrency model.

## Regression guard

`R8S7RunDashboardIT` continues to exercise the active-execution dashboard path and now applies a 5-second HTTP timeout so a future connection self-deadlock fails promptly.

## Persistence impact

None. Core tables remain 7 and Flyway remains V1-V10.
