# Web Console

R9 consolidates the operational Web Console on top of the accepted R8-S13 Fix2 execution platform.

Open:

```text
http://127.0.0.1:8080/ui
```

## Pages

```text
/ui                       Mission Control Overview
/ui/runs/new              New autonomous requirement
/ui/runs                  Run list
/ui/runs/{runId}          Run detail, tasks, controls, timeline
/ui/workers               Worker fleet and drain/offline controls
/ui/approvals             Approval inbox and decision history
/ui/system                Fleet scheduling and accounting diagnostics
```

## Supported operations

The browser delegates every mutation to existing application-service authority. It does not maintain a second state machine.

- start an autonomous requirement run;
- pause / resume / recover / cancel a run;
- update per-run `maxParallelism`;
- update Fleet `maxParallelism`;
- request approval for a PASSED engineering result;
- approve / reject a pending approval;
- drain a Worker;
- mark a zero-lease Worker offline;
- monitor runs, tasks, workers, backlog, scheduling and timeline/audit state.

Run and Fleet contraction retain the existing natural-drain semantics: lowering a limit does not kill existing leases; it blocks new claims until usage drops below the new limit.

## Live monitoring

Overview and Fleet poll their existing HTML URLs every 5 and 8 seconds respectively and replace only marked read-only regions. Forms, navigation, focused content, open details and scroll position are retained. Other operational pages retain page refresh. Editing pauses polling; restoring the original form values or resetting the form resumes it. Hidden pages pause and resume when visible.

One request may be in flight, with a 10-second timeout. Failures retain the previous snapshot and retry on the next interval. The timestamp remains the last applied snapshot; after 30 seconds without a complete update, a stale warning appears. A login redirect, role change or unexpected HTML stops polling and asks you to reload. Worker action choices reflect page-load state: use **Reload operation choices** to update them. The server checks current state when an action is submitted.

Overview displays up to 12 non-terminal runs, 8 attention runs, 8 pending approvals, 8 workers, 5 waiting tasks per category and the latest 20 projected events. Pending and active-run totals are counted separately. Fleet displays at most 100 workers, 100 READY tasks and 100 active leases, with the corresponding full snapshot counts. READY tasks are ordered by priority descending, creation time ascending and task ID; leases by expiry then task ID. These are first-page snapshot previews, without pagination across changing snapshots. Waiting counts use the primary-blocker priority: policy, Run admission, capability, Worker capacity, then Fleet admission. Each READY row lists all currently observable limits, which can overlap. These are current facts rather than past claim decisions. The activity feed uses existing Run/task/attempt/approval sources and is not complete audit history.

Run links in Fleet require a task or lease with `AUTONOMOUS_REQUIREMENT` subject type and a matching saved Run ID. Other subjects and missing Run records show their original type/ID without an invented link. Active lease rows show Worker ID, task, attempt, state and expiry where recorded. Heartbeat timestamps, capacity and leases are observed facts; the page does not assign automatic healthy/unhealthy grades or alerts. Lowering Fleet or Run limits preserves active leases and drains naturally; draining a Worker stops new claims while its current leases continue. Pause revokes active leases, and cancellation is irreversible. The existing server-side actions retain permission, CSRF and current-state checks; stale action choices may fail and show the actual error.

Worker silhouettes, hue and identity marks are derived from the full Worker ID and stay consistent across Overview and Fleet. Management status, lease usage, runtime and capabilities are shown independently. Task failure does not imply Worker failure. Completed/current task counts are not an ETA or an engineering completion percentage.

All pages share a dark tactical style using local CSS, SVG and system fonts. **Motion: off** stores a local browser preference; the operating system's reduced-motion setting also suppresses pulses.

## Approval evidence

`/ui/approvals` separates pending requests from the latest 100 decided requests. Pending requests also show an independently counted total; the decided list is a window, not complete history. Each record shows its saved result revision, source task and attempt, gate close time, review and verification task/attempt verdicts and summaries. Run Detail shows the same saved approval evidence when its approval record is available.

Evidence is linked using the approval's source task, result revision and gate close time. Missing or inconsistent source, review or verification records are identified as unavailable; the page does not substitute the current Git HEAD or another attempt. Summaries are displayed as text. `publicationRef` is only a saved identifier: the Web Console does not fetch external documents, Git content or downloads. Approval actions still use the existing APPROVER/ADMIN, CSRF and `ApprovalService` checks. Run Detail refreshes approval status, evidence and forms as one region, pausing while the form is edited or focused; a stale or conflicting decision displays the current service error.

## Authorization roles

Mandatory browser-session authentication supports:

```text
VIEWER    read-only
OPERATOR  new runs, run control, scheduling, worker control
APPROVER  approval request/approve/reject
ADMIN     all Web Console operations
```

Configuration:

```text
AIW_WEB_CONSOLE_USERNAME=admin
AIW_WEB_CONSOLE_PASSWORD=
AIW_WEB_CONSOLE_ROLE=ADMIN
```

All console business pages require a real signed-in session, including Goal Workshop, task preparation and composed roles. The login page at `/ui/login` and its public resources remain available. Login validates the configured username and nonblank password; missing credentials show setup instructions and cannot create an authenticated session. The old enabled property/environment flag is ignored even when false. Page GET/HEAD redirects retain the original path/query and application context. Existing console APIs reject unauthenticated calls with 401. Browser mutations retain per-session CSRF and role checks.

## Security boundary

Web Console authentication protects `/ui` and `/ui/**`, and the registered console data roots under `/api/r1/`: goals, task-outputs, workflow-plans and plan-editor. Human authority routes retain their separate closed session boundary. They retain session, CSRF, current role and ownership checks. Other legacy administrative writes retain their original local policy until first delegate activation, which permanently requires human session, CSRF and action roles for those writes; Worker lease operations remain machine-authenticated. Default bind remains `127.0.0.1`. Remote deployment still needs an appropriate network/edge boundary; these custom application checks are not a complete production edge-security framework.

## Technology

- Spring MVC
- Thymeleaf
- vanilla CSS
- small vanilla JavaScript helper
- one Spring Boot JVM
- no npm / Node front-end build
- no SPA framework
- no additional persistence table
