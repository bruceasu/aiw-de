# FD-123: Propose structural changes to pending workflow nodes

**Status:** Complete
**Revision:** 12
**Review policy:** Independent
**Priority:** Medium
**Evidence policy:** Markdown

## Problem

FD-122 lets a read-only Worker coordinate content and route changes to existing
unstarted nodes. A workflow still cannot adapt when new work must be added,
removed, or placed in a different order.

## Options and decision

Keep structural changes manual, or extend the existing proposal and confirmation
flow to include the remaining graph. Use the existing flow: one read-only Worker
proposes a candidate graph, the user reviews the full change and graph preview,
and one explicit confirmation applies it to the paused execution.

## Solution

Add a structural-change mode beside FD-122's existing content-change mode. The
Worker receives the goal, current context, and a sanitized graph. It returns a
candidate node list. The server validates the whole candidate against the
current execution and keeps all execution settings from the existing snapshot.

The user sees added and removed nodes, changed routes, and the full graph
preview. Confirmation replaces the current execution's node list atomically
only while its version, paused state, and underlying Plan version still match.
A stale or invalid candidate changes nothing.

Keep the start node, goal, workflow title, sources, commands and existing node
keys. Existing node kinds, titles, instructions, member settings, Skills and
workspace-tool settings remain unchanged. A current node's not-yet-used exits
may be retargeted to implement a future reordering; preserve its branch labels
and current task settings. Previously completed nodes and already traversed
routes remain unchanged. Delete only nodes that have not started. New working
nodes use ordinary automatic assignment and have no fixed member, Skills,
workspace tools or command access. TASK and DECISION nodes need Plan task slots.
Append those slots to the confirmed Plan without moving or deleting existing
slots; deleted graph nodes leave dormant Plan slots so historical task indices
remain stable. A new key may not reuse any key already reserved by a Plan slot.
The Plan's existing 32-slot limit is shared across added and dormant slots, so
the proposal must respect the remaining slot capacity. Keep at least one
existing END node and the graph's 32-node limit.

Store the proposal in the existing helper task and execution data; add no table,
migration, dependency or background process. Existing content-change proposals
continue using their current path.

## Scope

- Structural proposals are available only while a workflow execution is paused.
- The helper Worker only drafts a candidate; it does not execute proposed work
  or apply the graph.
- Preserve existing member and permission settings. New nodes cannot add file,
  command or member-specific authority. New TASK/DECISION Plan slots are
  appended with only their ordinary general-work capability.
- Keep Plan step indices and task history stable. Appended execution slots do
  not change the Plan's original Owner source pin; its initial digest is stored
  with the workflow execution so existing formal questions remain valid. No new
  persistence table is needed.
- Do not change reusable definitions, goal, sources, commands, current task
  instruction, node identity, or the record of work already performed.
- No automatic application, database migration, tests, real model calls,
  browser or service runs.

## Work items

- [x] 1.1 Add a structural proposal mode using the existing read-only helper
  task and a sanitized current-graph basis.
- [x] 1.2 Validate additions, deletions and route changes against the paused
  execution; append Plan task slots without moving historical indices and
  atomically apply only a current valid graph.
- [x] 1.3 Show an itemized structural diff and graph preview in the existing
  execution page; keep existing content proposals and translations working.
- [x] 1.4 Compile affected Java sources and statically inspect API, persistence,
  locking, execution and UI paths; record evidence for independent review.

## Acceptance

- Before confirmation, the execution graph and Plan are unchanged. An execution
  or Plan version/state change makes the candidate stale; no candidate can be
  partially applied.
- The start node and execution-level goal, sources and commands remain the
  same. Existing node keys and execution settings remain fixed; the current
  node's unresolved exits may change while its branch labels and task settings
  remain fixed.
- Only unstarted nodes can be removed. Existing started work and traversed
  routes remain unchanged. Every route target exists, at least one END remains,
  the candidate has at most 32 nodes, and added nodes are reachable from the
  start node.
- New TASK/DECISION nodes receive appended Plan slots; prior Plan slots and task
  indices never move or disappear. Removed work slots remain dormant. New
  slots cannot use a key reserved by any existing Plan slot and cannot exceed
  the Plan's remaining 32-slot capacity.
- New nodes receive no fixed member, Skills, workspace tools or command access.
- The page previews added nodes, removed nodes, changed routes and the candidate
  graph before offering explicit confirmation.
- Existing single-node and coordinated content proposals remain decidable.
- No database schema migration or new execution record type is needed.

## Verification

- Compile-only check passed in the FD-123 worktree:
  `mvn -q -o -s D:/03_projects/AI-tools/aiw-de/.ai/fd/FD-081/offline-maven-settings.xml '-Dmaven.repo.local=D:/.m2/repository' -DskipTests compile`
  (JDK 25; 2026-11-14). The first invocation failed before Maven because
  PowerShell split the unquoted `-Dmaven.repo.local` argument; the quoted
  invocation above passed.
- `node --check src/main/resources/static/ui/workflows.js` passed after the UI
  edits. `git diff --check` passed after implementation.
- Static call-path review confirmed `WorkflowRuns.advance` dispatches by stable
  Plan step key, while `WorkflowTaskPosition` derives history from step index.
  New TASK/DECISION slots are appended and removed slots remain dormant. The
  workflow execution retains the initial Plan source digest so appended slots
  do not change formal-question source validation.
- The first review request was canceled before a verdict because the source-pin
  handling changed after dispatch. The fresh review of
  `FD-123-000007-review-requested` found one P2: a newly proposed DECISION can
  pass preview validation but exceed the Plan Step instruction limit on
  confirmation. See [independent review](reviews/review-20261114-r2.md).
- Fixed by constructing the exact Plan task slot during candidate validation and
  reusing that constructor during application. An overlong instruction now
  makes the proposal invalid before it can be confirmed. The compile-only check
  passed again after this fix. The focused independent recheck of
  `FD-123-000011-implementation-ready` passed; see
  [P2 recheck](reviews/recheck-20261114-r3.md).
- Tests, application startup, model calls, database/runtime behavior and browser
  behavior were not run. They remain unverified.

## Sources

- [FD-106](../archive/FD-106/FD-106_CONFIRM_WORKFLOW_AMENDMENTS_BEFORE_CHANGING_FUTU.md):
  existing confirmed proposal lifecycle.
- [FD-122](../archive/FD-122/FD-122_PROPOSE_COORDINATED_UPDATES_TO_PENDING_WORKFLOW_.md):
  coordinated content proposal and current-node routing guard.
- [FUNCTION-INVENTORY.md](../../FUNCTION-INVENTORY.md), item 03.
- User confirmation: allow Worker-proposed node additions, deletions and
  reordering with full preview and explicit human confirmation; do not let the
  Worker alter members or permissions.

**Completed:** 2026-11-14
**Disposition reason:** Implementation, compile-only validation, static evidence, and required independent review are complete; runtime checks remain unrun as authorized.
