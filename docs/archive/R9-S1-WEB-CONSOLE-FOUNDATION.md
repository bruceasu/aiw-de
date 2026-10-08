# R9-S1 — Web Console Foundation

Status: implementation candidate built on the accepted R8-S13 Fix2 (`me.asu.aidev`) baseline.

## Purpose

R9 changes the focus from Control Plane scheduling depth to product usability. R9-S1 adds a read-only Web Console so operators and users can understand the system without calling REST endpoints manually.

## Technology

- Spring Boot 4 / Spring MVC
- Thymeleaf server-side templates
- Vanilla CSS and a tiny amount of JavaScript
- no npm, Node build, SPA framework, WebSocket, or second deployment unit

## Routes

```text
/                         -> redirect to /ui
/ui                       -> Fleet / Run / Approval dashboard
/ui/runs                  -> autonomous run list
/ui/runs/{runId}          -> run detail, tasks, execution state and timeline
/ui/workers               -> worker fleet
/ui/approvals             -> approval inbox/history (read-only)
/ui/system                -> scheduling and accounting diagnostics
```

## Authority boundary

R9-S1 is deliberately read-only.

The Web Console:

- does not write database state;
- does not call pause/resume/recover/cancel;
- does not change run or fleet scheduling limits;
- does not approve/reject requests;
- does not claim or control Workers;
- does not create a second domain authority.

All displayed state is projected from the same Control Plane services/tables already used by the R8 APIs.

## Read model

`me.asu.aidev.webconsole` contains Web-specific read models. The UI does not place SQL in Thymeleaf templates or controllers.

```text
WebConsoleController
        |
WebConsoleQueryService
        |
        +-- FleetDashboardService
        +-- AutonomousRunDashboardService
        +-- AutonomousRunTimelineService
        +-- JdbcWebConsoleReadRepository
```

The JDBC repository only provides list/detail data that did not previously have a dedicated read API. It is transactionally read-only.

## Refresh model

Dashboard and Run Detail use a five-second browser refresh. R9-S1 intentionally does not introduce WebSocket/SSE infrastructure. Live transport can be reconsidered only after the initial UI proves useful.

## Security boundary

R9-S1 remains a local/trusted-internal console. It must not be exposed directly to the public Internet because the management surface does not yet have complete authentication/RBAC.

Authentication and role separation are intentionally deferred to a later R9 security stage.

## Persistence

No schema or Flyway change.

R9-S1 remains on:

```text
9 persisted authority/audit tables
Flyway V1-V14
```

## Acceptance

`R9S1WebConsoleIT` verifies that the read-only pages render from a real SQLite Control Plane context, including a real autonomous run.
