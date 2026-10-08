# Troubleshooting and Recovery

## 1. Start with these three views

For an autonomous run:

```text
GET /api/r8/runs/{runId}
GET /api/r8/runs/{runId}/dashboard
GET /api/r8/runs/{runId}/timeline
```

For fleet-wide issues:

```text
GET /api/r8/fleet/dashboard
GET /api/r8/fleet/scheduling
```

These usually answer whether the problem is engineering failure, operator policy, missing worker capability, worker capacity, run admission or fleet admission.

## 2. READY task never executes

Check fleet dashboard categories:

### `unmatchedReady`

No ACTIVE worker advertises all required capabilities.

Action: start/register a compatible worker or fix capability configuration.

### `capacityBlockedReady`

Compatible workers exist but their `maxConcurrency` is full.

Action: wait, increase worker capacity, or start another compatible worker.

### `admissionBlockedReady`

The run reached `maxParallelism`.

Action: wait for active work to drain or increase the run limit.

### `fleetAdmissionBlockedReady`

The global fleet ceiling is saturated.

Action: wait or increase the fleet ceiling.

### `policyBlockedReady`

The run is paused/failed/cancelled or otherwise not currently claimable.

Action: inspect run state and use resume/recover where valid.

## 3. Run is FAILED

An explicit worker failure sets the run to `FAILED`.

1. inspect `lastFailedTaskId` and `lastFailureReason`;
2. fix the external cause;
3. call:

```text
POST /api/r8/runs/{runId}/recover
```

Do not manually update task rows.

## 4. Worker disappeared

If the worker simply disappears, wait for lease recovery. The scheduled recovery process requeues expired claims and expires the attempt.

If recovery is disabled or delayed, inspect:

```text
AIW_LEASE_RECOVERY_ENABLED
AIW_LEASE_RECOVERY_INTERVAL_MS
AIW_LEASE_RECOVERY_BATCH_SIZE
```

## 5. Worker receives 401

`AIW_WORKER_TOKEN` is configured on the Control Plane and the worker is not sending the same bearer token.

Set the same value on both sides.

## 6. Worker receives 409 on heartbeat/complete

Likely ownership was lost because of:

- lease expiry;
- operator pause;
- operator cancel;
- stale/replayed action.

The worker should stop the local attempt and must not force completion. Reference workers abort after losing heartbeat ownership.

## 7. SQLite request hangs near 30 seconds

Check for nested JDBC queries while the single SQLite connection is still held.

The current profile deliberately uses:

```text
maximum-pool-size: 1
connection-timeout: 30000
```

R8-S7 previously had a dashboard `RowMapper` that issued another `JdbcTemplate.query`; the second query waited for the same connection until timeout. The correct fix is to remove the nested query, not increase the pool.

## 8. All Spring integration tests fail to load ApplicationContext

Find the **first** root `Caused by`; later tests may only show Spring’s failure-threshold message.

Known regression class:

- Spring bean has multiple constructors but the production constructor is missing `@Autowired`.

This happened in R8-S12 and caused all test contexts to fail before any business assertion ran.

## 9. Old behavior appears after upgrading a ZIP over an existing directory

ZIP extraction does not delete source files removed by a newer version. A superseded Java source can remain under `src/main/java` and be compiled together with its replacement.

This previously caused two completion handlers to run simultaneously.

Preferred upgrade for source distributions:

1. extract into a new clean directory; or
2. ensure deleted paths are explicitly removed/overwritten.

Always run:

```bash
mvn clean verify
```

`mvn clean` removes `target/`; it does not remove stale files under `src/`.

## 10. Run slot accounting delta is non-zero

This means `autonomous_run.active_slots` differs from real `CLAIMED/RUNNING` work for that run.

Treat this as an integrity issue. Do not “correct” it only in the dashboard. Investigate the claim/release transaction path and database history.

## 11. Fleet worker lease accounting delta is non-zero

The sum of worker `active_leases` differs from live task ownership. Investigate lost claim/release transactions or manual database changes.

## 12. `overcommittedSlots` is positive

This can be normal after runtime contraction:

```text
old limit = 8
active = 6
new limit = 2
```

The system intentionally preserves healthy leases and blocks new claims until active work drains. `overcommittedSlots` is not the same as an accounting delta.

## 13. Telegram adapter fails at startup

When `AIW_TELEGRAM_ENABLED=true`, both must be valid:

```text
AIW_TELEGRAM_BOT_TOKEN
AIW_TELEGRAM_ALLOWED_USER_IDS
```

A target chat must also be resolvable from approval `channelRef` or `AIW_TELEGRAM_CHAT_ID` when sending a request.

## 14. Parallel implementation conflicts excessively

The planner probably split work that was not truly independent. Parallel work items must be implementable from the same immutable base without requiring another item’s intermediate commit.

Prefer one larger implementation item when dependency/conflict risk is high.
