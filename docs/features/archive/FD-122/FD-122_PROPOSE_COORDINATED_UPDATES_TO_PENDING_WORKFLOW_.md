# FD-122: Propose coordinated updates to pending workflow nodes

**Status:** Complete
**Revision:** 6
**Review policy:** Independent
**Priority:** Medium
**Evidence policy:** Markdown

## Problem

FD-106 lets a Worker suggest a change to one undispatched workflow node. When
new results affect several later steps, the user must request and apply each
change separately. This makes it hard to review a coherent adjustment to the
remaining work.

## Options and decision

Do not add, delete, or reorder nodes in this change. That would change the
confirmed execution plan's task slots and could alter historical task-position
interpretation. Let the Worker propose coordinated changes to selected existing
nodes instead; preserve each node's identity and kind, and require human
confirmation before applying them.

## Solution

While an execution is paused, the user selects one or more undispatched nodes
and describes the requested adjustment. One ordinary read-only Worker task
receives the current goal, execution context, selected nodes and available
destination keys. It may propose updates to node titles, instructions and
existing routes. Branch labels and all execution settings remain fixed.

Show every before/after change and a graph preview. Apply all candidates in one
transaction only after explicit confirmation. Reject the full proposal if any
node is no longer pending, the run version changed, the response is malformed,
or any candidate violates the existing workflow rules. Resume, stop or discard
continues to use FD-106's current proposal-stop behavior.

Keep the existing single-node request and saved proposal readable. Store the
selected node keys in the existing suggestion task's saved basis, so the main
execution record format does not change. Add no table, background loop or
dependency.

## Scope

- Change only existing, unstarted non-END nodes in one paused execution.
- Keep node keys, kinds, assignment, member, required Skills, workspace tools,
  sources, commands, goal and permissions unchanged.
- Preserve branch choice labels and allow destinations only among existing
  nodes; do not introduce a route back to an already started or asked node.
- A batch may contain at most eight distinct nodes. If its serialized context
  exceeds the ordinary material limit, explain the limit and leave the run
  unchanged.
- No automatic application, node creation/deletion/reordering, permission
  change, migration, test execution, real model call or browser/service run.

## Work items

- [x] 1.1 Extend the amendment request and saved suggestion basis for several
  selected nodes while keeping the main execution record unchanged.
- [x] 1.2 Validate every candidate and apply all node updates atomically after
  confirmation; reject the whole batch when stale or invalid.
- [x] 1.3 Let the user select multiple pending nodes and review all diffs plus
  one graph preview in the existing workflow page.
- [x] 1.4 Compile affected source and record static compatibility and rollback
  evidence.

## Acceptance

- Only selected pending nodes can change; started nodes and all execution
  settings remain byte-for-byte equivalent in meaning.
- Each candidate keeps its key and kind; branch labels are unchanged and every
  destination exists. A proposal cannot newly route work to a node with a prior
  `DISPATCHED` or `QUESTION` event.
- Before confirmation, the execution is unchanged. Confirmation applies all
  accepted candidates together; any stale or invalid candidate applies none.
- Existing single-node proposals remain viewable and decidable.
- No database migration or new task slot is needed.

## Verification

- Offline Java compile after the review correction passed: `mvn -q -o -s D:/03_projects/AI-tools/aiw-de/.ai/fd/FD-081/offline-maven-settings.xml -Dmaven.repo.local=D:/.m2/repository -DskipTests compile` (JDK 25). `git diff --check` passed. `node --check src/main/resources/static/ui/workflows.js` passed before the Java-only correction.
- No tests, models, services, migrations, or browser were run.
- First independent review found the ASK-node routing P2 in [its report](reviews/review-fd122-20261114.md); `Changes requested` history is retained. The correction is recorded in [the Worker report](reports/worker-fix-20261114.md); focused independent recheck passed, as recorded below.
- The amendment API adds a batch request and response shape, so independent review is required. No database or saved main-execution format change is required. An older runtime can read a pending proposal but treats the batch response as invalid; it can be discarded, and no node is partially applied.

- 独立聚焦复核（`b4aa3d7d..f106769e`，来源 `FD-122-000005-implementation-ready`，会话 `fd122-review-recheck-20261114`）：Verification passed。`startedNode` 将既有 `QUESTION` 事件视为已启动，并同时用于待选校验和新路线校验；确认时在锁内复核 `view.changes` 中全部节点后才原子写回。实现差异仅涉及该缺陷修复，没有无关实现漂移。详情见 [聚焦复核报告](reviews/review-recheck-fd122-20261114.md)。本轮未运行测试、编译、服务、模型或浏览器。

## Sources

- [FD-106](../archive/FD-106/FD-106_CONFIRM_WORKFLOW_AMENDMENTS_BEFORE_CHANGING_FUTU.md):
  current single-node proposal and confirmation behavior.
- [FUNCTION-INVENTORY.md](../../FUNCTION-INVENTORY.md), item 03.
- User direction: workflow participants should adapt the remaining process to
  actual circumstances; keep changes reviewable and prefer the simplest sound
  implementation.

**Completed:** 2026-11-14
