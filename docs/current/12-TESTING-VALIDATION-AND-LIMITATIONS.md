# Testing, Validation and Current Limitations

## 1. Accepted baseline

Current documented baseline:

```text
R8-S13 Fix2
Java namespace: me.asu.aidev
Maven groupId: me.asu
Java: 21
Spring Boot: 4.0.8
Flyway: V1-V14
Tables: 9
Expected Failsafe integration-test methods: 29
```

The user’s authoritative local verification has passed `mvn clean verify` for this baseline.

## 2. Verification command

```bash
mvn clean verify
```

Use `clean`; stale compiled test/classes can otherwise confuse version-to-version validation.

## 3. What the integration suite currently exercises

The accumulated tests cover:

- task DAG readiness and late dependency creation;
- worker capability/capacity and lease recovery;
- multi-worker runtime concurrency/crash retry;
- HTTP Worker Protocol;
- Git workspace/result protocol;
- upstream result handoff;
- completion handler fan-out/idempotency;
- repair loop;
- engineering gate;
- human approval immutability;
- Telegram approval authorization;
- automatic approval policy;
- autonomous requirement flow;
- parallel implementation;
- result integration;
- run failure/recovery;
- pause/resume/cancel;
- timeline and dashboard;
- fleet/backlog dashboard;
- priority/fairness/aging;
- per-run admission;
- runtime run concurrency change;
- fleet admission;
- runtime fleet ceiling change/audit.

## 4. What is not yet proven enough

### Real long-running AI end-to-end behavior

Most Control Plane behavior is integration tested, but the next validation priority should be a real Codex-driven repository from natural-language requirement through review/repair/gate/approval.

This is needed to evaluate prompt quality, planning stability, token/runtime cost, repair convergence and integration conflicts.

### PostgreSQL multi-instance concurrency stress

The code contains PostgreSQL-specific locking/dialect behavior, but production confidence requires deliberate stress tests with multiple Control Plane instances and many concurrent claimers.

Important races include:

- claim vs claim;
- claim vs pause/cancel;
- claim vs run/fleet ceiling changes;
- completion vs lease expiry;
- recovery vs worker heartbeat;
- multiple runs sharing the fleet budget.

### Production management authentication

General management APIs currently lack an integrated authentication/authorization layer.

### Delivery/release

A run currently ends after engineering gate + approval. Automatic PR creation, merge, release, deployment and rollback are not part of the current R8 implementation.

### Capability-specific quota

R8-S14 capability-level quota was intentionally deferred. Current controls are worker capacity, per-run limit and fleet-wide limit. Implement capability quota only when real workload shows a shared-resource need that worker sizing cannot solve.

### Multi-repository atomic engineering result

The platform has `repositoryRef` and external workers, but the current autonomous parallel integration path is mainly designed around work items from one repository/base. A formal cross-repository final result is a future design area.

## 5. Recommended next validation order

1. complete/documented baseline (this package);
2. real Codex end-to-end on a small Java repository;
3. PostgreSQL concurrency/chaos tests;
4. management API security hardening;
5. release/PR delivery integration;
6. only then add scheduling/resource features driven by measured need.



## R9 consolidated Web Console validation

In addition to `R9S1WebConsoleIT`, R9 adds:

- `R9WebConsoleOperationsIT` for run creation, pause/resume, Run/Fleet scheduling and Worker drain/offline;
- `R9WebConsoleSecurityIT` for session login, CSRF enforcement and VIEWER write denial;
- `R9WebConsoleApprovalIT` for WebUI approval against the existing Approval authority.

Expected Failsafe integration-test methods: **33**. The authoritative acceptance command remains `mvn clean verify`.
