# FD-089: Agent permission page redesign and multi-select action grants

**Status:** In Progress
**Revision:** 1
**Review policy:** Independent
**Priority:** Medium
**Evidence policy:** Markdown

## Problem

The Agent permission page opens on identity and connection controls, repeats links to Agent management, and uses a single-action dropdown. Operators cannot quickly understand the routine path or compare multiple platform actions before granting them.

## Options and decision

Keep the existing capability sections and single-action issue/revoke API contracts, but make action grants the primary page flow. Add a bounded batch endpoint so checkbox selections create separate permission periods atomically. Do not represent a group selection as one wildcard permission.

## Solution

Make “动作授权” the first and default tab. Replace the group/action dropdown pair with grouped checkboxes populated from the existing catalogue. Keep unavailable actions visible but disabled. Share Agent, expiry, per-action maximum uses, and a scope selected from the intersection supported by every selected action. Confirmation lists each action as its own permission and clarifies that each receives its own maximum-use counter.

Add `POST /api/r1/authority/action-permissions/batch`. Accept a bounded non-empty list of distinct action issues for one approved Agent, each with an independent request ID and expected prior permission. Run all issues in one transaction using the existing per-action validation, authorization, audit, replacement, and idempotency behavior. A failure rolls back the batch; retrying the exact payload remains safe. Keep the existing single-issue route unchanged.

Clarify the page title and first-use guide, move Agent identity controls to the final tab, remove duplicate pairing guidance, and use simpler labels for the retained plan hosting, execution, and history sections.

## Scope

Redesign this page and the permission picker/confirmation; add the human-authorized batch issue route and its transactional service path. No database migration, new grant scope, default permission, Agent pairing, plan hosting, execution-policy, or audit behavior changes. Preserve advanced controls.

## Work items

- [x] 1.1 Simplify the page entry flow, titles, section order, and default tab without removing existing capabilities.
- [x] 1.2 Replace single-action dropdown selection with grouped checkboxes and explicit per-action batch confirmation.
- [x] 1.3 Add an atomic, idempotent batch issue endpoint while preserving single-issue behavior and authorization checks.

## Acceptance

- Opening the page lands on action permissions and explains when this page is needed.
- Operators can select multiple currently allowed actions by checkbox; disallowed actions remain unselectable.
- A batch is rejected client-side if selected actions have no common supported scope, and the server independently validates every action.
- Confirmation shows the exact Agent, each action, shared scope, expiry, and a separate maximum-use count for every action.
- All selected actions are stored as distinct permission records with independent request IDs and usage counters.
- Batch creation is all-or-nothing; retrying the identical payload returns the original records without duplicate permissions or counters.
- The legacy single-action issue endpoint and individual revoke operation keep their existing behavior.
- Existing plan hosting, execution settings, records, and Agent controls remain reachable.

## Verification

- Static review: inspected the permission contract, service transaction path, Agent message writes, human-route allowlist, UI request/recovery flow, and changed page structure. The batch reuses the single-action validator and audit path inside one transaction.
- Compile-only: passed with JDK 25 using `. 'C:\green\bin\setup-jdk25.ps1'; & "$env:MAVEN_HOME\bin\mvn.cmd" --offline "-Dmaven.repo.local=D:/.m2/repository" "-DskipTests" compile` (344 production sources). Earlier attempts stopped on command lookup, `JAVA_HOME`, and PowerShell argument parsing; the corrected command completed successfully.
- Tests were not added or run.
- Independent implementation review remains required because this adds a permission-writing batch endpoint.

## Sources

- Issue: none
