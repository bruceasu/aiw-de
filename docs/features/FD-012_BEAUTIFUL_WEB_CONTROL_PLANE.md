# FD-012: Beautiful Web Control Plane

**Status:** Open
**Revision:** 5
**Priority:** Medium
**Evidence policy:** Dual

## Problem

The approved Issue limits this FD to M1: Mission Control Overview and shared visual foundations. Operators already have real Overview, Fleet, Run, approval, and system data, but the Overview must make current work, capacity, waiting causes, human attention, and recent activity read as one tactical command surface. Worker identity and execution state must also remain recognizable between Overview and Fleet. The current baseline already has bounded projections, partial polling, and Worker identity marks; the design must preserve their truth and continuity while improving hierarchy, scanability, and narrow-window access.

## Options and decision

1. Replace the Web Console with a SPA or a separate dashboard service: rejected because M1 can use the existing Spring MVC/Thymeleaf pages and authoritative application services; a new frontend runtime would add deployment and data-boundary cost without an Issue-backed need.
2. Add streaming transport or a new persistence/read model: rejected for M1. Existing 5-second Overview and 8-second Fleet partial refresh already expose snapshot time, stale state, and bounded-window labels. Revisit only if implementation evidence shows these cannot meet the approved experience.
3. Refine the existing pages and shared CSS/SVG presentation: selected. Keep server-rendered pages, existing projections, partial-refresh boundaries, permission checks, and Control Plane authority; make the command hierarchy, tactical visual language, Worker identity, and responsive states consistent across Overview and Fleet.

## Solution

Use the existing Overview and Fleet pages as the M1 experience. Organize the Overview around (a) current action and attention, (b) capacity and waiting diagnostics, and (c) bounded recent activity, with explicit links to existing Run/Fleet/Approvals/System contexts. Keep counts and sample-window labels paired; preserve the distinction between Run lifecycle, control state, task state, Worker management status, lease occupancy, and unknown data. Keep the existing dark tactical palette and local CSS/SVG approach, with semantic colors, readable text, keyboard focus, reduced-motion behavior, and no decorative element that obscures data or actions.

Use the existing Worker identity derived from the full Worker ID in both Overview and Fleet. Pair its stable mark with the real Worker ID, management status, runtime/capability facts when present, and lease usage; do not infer health or expertise. Keep the 5s/8s partial refresh and current stale/error/input-preservation behavior. Any refinement must remain inside the existing read-only refresh regions and leave forms, focus, expanded details, and scroll stable.

No schema, persistence, authorization, management API, scheduler, or lifecycle change is required by the approved M1 scope. Query changes are allowed only for a demonstrated M1 display gap and must remain a read-only projection with truthful count/window semantics. The broader M2 through M4 outcomes remain outside this FD.

## Scope

**In scope:** Mission Control Overview hierarchy and presentation; shared navigation/panel/status styling needed by M1; Overview and Fleet Worker identity/state consistency; true empty, attention, stale, refresh-failure, and narrow-window presentations; links to existing operations and details; static evidence against the M1 requirements.

**Out of scope:** Run Detail redesign and timeline work (M2); full Fleet workflow or scheduling changes (M3); approval evidence redesign (M4); new history/retention, health thresholds/alerts, external artifact retrieval, auth/RBAC changes, new persistence, push dispatch, or a new frontend/runtime. Do not create mock operational data or represent unavailable evidence as healthy or complete.

## Work items

- [ ] 1.1 Refine the Overview command hierarchy for current Runs, attention, and existing actions. Size: S; difficulty: Medium; depends on 1.2 for shared presentation. Evidence: static review shows the active/attention distinction, real-state links, empty states, and no stale or bounded list presented as complete.
- [ ] 1.2 Apply a shared tactical visual foundation to the Overview and Fleet surfaces. Size: S; difficulty: Medium; no dependency. Evidence: shared navigation, typography, panels, semantic status colors, focus states, and reduced-motion behavior are consistent without hiding operational content.
- [ ] 1.3 Present capacity, waiting diagnostics, approvals, and recent activity as bounded command information. Size: S; difficulty: Medium; depends on 1.2. Evidence: each sample is labeled with its source window and total where available; blocker labels preserve primary-current-blocker semantics; activity is clearly not complete audit history.
- [ ] 1.4 Make Worker identity and execution state consistent between Overview and Fleet. Size: S; difficulty: Medium; depends on 1.2. Evidence: the same Worker ID yields the same identity mark in both pages, while management status and lease usage remain separately readable and unknown facts stay unknown.
- [ ] 1.5 Refine responsive and live-monitoring states for the M1 surfaces. Size: S; difficulty: Medium; depends on 1.1–1.4. Evidence: narrow layouts retain access to primary data/actions; stale/failure/refresh feedback is visible; polling retains current input, focus, open details, and scroll behavior.

## Acceptance

1. Overview presents active work, human attention, Fleet/capacity, waiting causes, and recent activity in a clear command hierarchy, with a direct path to the existing authoritative detail or operation.
2. Empty and populated states use actual values. No example count, health grade, ETA, engineering completion percentage, or claim-history explanation is invented.
3. Active/attention, READY/waiting, and approval data retain their current distinct semantics. Bounded samples disclose their window and do not imply a complete total/history.
4. Worker identity remains stable between Overview and Fleet. Management status, runtime/capabilities, and lease occupancy are not conflated; OFFLINE/DRAINING/active execution remain readable without relying on color or motion.
5. Refresh and stale/error states tell the operator which snapshot is visible and preserve active user interaction. Failed refresh never presents the old snapshot as current or healthy.
6. Keyboard focus, status text, reduced-motion presentation, long IDs/text, and narrow-window navigation keep the primary information and actions accessible.
7. All displayed actions continue to use existing routes/services and server-side authorization/current-state checks. No M2–M4 or management API security change is implied.

## Verification

- Worker: inspect the final diff and perform one repository-authorized compile-only check after implementation; default to the narrowest compile command. Static review must trace page models, refresh-region boundaries, permission-conditioned actions, and the M1 data-window labels.
- Do not run tests, a final build, formatters, linters, type checks, browser automation, or a verification script in this workflow. A browser-level visual check remains unverified under the current repository authorization; report that limitation and any visual risk explicitly.
- Reviewer: independently compare the actual diff and Worker report with the approved M1 scope and each acceptance item. Optional `$fd-test` is a separate workflow and is not part of this FD's default acceptance.

## TODO

Complete 1.2 before 1.1 and complete 1.3 and 1.4 before 1.5. Record each item before beginning the next dependent slice. Keep item IDs stable after implementation starts.

## Sources

- Approved Issue and M1 boundary: `docs/issues/ISSUE-00002/issue-plan.md`.
- Current Web Console behavior and refresh/window semantics: `docs/current/15-WEB-CONSOLE.md`.
- Existing M1 templates: `src/main/resources/templates/ui/dashboard.html`, `workers.html`, and shared UI fragments.
- Existing overview projection: `src/main/java/me/asu/aidev/webconsole/application/WebConsoleQueryService.java` and `WebConsoleOverview.java`.
- Existing shared styling and identity presentation: `src/main/resources/static/ui/console.css` and `src/main/resources/templates/ui/worker-identity.html`.
- Issue: ISSUE-00002.
