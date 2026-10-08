# Run Mission detail

Open **Runs**, then a Run ID, at `/ui/runs/{runId}`. Mission is a display name for the existing Run. The page keeps the saved original requirement, lifecycle, control state, effective status, phase and task counts together. Completed/current tasks is a count, not an ETA.

## Tasks and dependencies

The SVG points from prerequisite to dependent and uses saved dependency rows. It does not infer a pipeline from task types. Open a node to read its task's full instruction in the table. **Hide graph** leaves the table available; the graph scrolls inside its own viewport. Cycles produce a warning and retain the actual dependency table.

The page returns the latest 200 tasks, up to 1200 dependencies belonging to those displayed tasks, and the latest 300 attempts for the Run. Each window displays its returned and total counts. Dependencies also show how many candidate edges belong to the displayed tasks. A partial graph, truncated dependency list or prerequisite outside the task window is explicitly marked. A prerequisite in another Run/subject keeps its recorded ID and scope label. Missing relationships are not created.

Attempt rows retain actual task, attempt number, historical Worker, state, timestamps, failure category, termination reason and completion report summary where recorded. Worker links open Fleet; a historical Worker does not imply a current lease. Tasks outside the displayed window keep their IDs without a broken task anchor. Completion summaries are reports, not a substituted failure cause.

## Failure and recovery

The newest Run FAILED event is read separately from the timeline and retains its actual time, related task, actor and detail. The latest 20 FAILED/RECOVERED Run events also show their total and truncation. A Run event has no stored attempt/Worker association; the page does not invent one. Attempt failure data remains available in execution history.

Automatic lease recovery requeues an expired task; manual recover requeues tasks after explicit Run failure and records a RECOVERED event. EXPIRED also occurs on operator pause/cancel. If the termination reason is absent, the page says it was not recorded. A subsequent attempt does not supply an invented recovery time. Pause revokes active leases; reducing a scheduling ceiling lets existing leases drain naturally. Cancel is irreversible.

## Recent timeline

The timeline returns **at most the latest 300 entries**, then displays them chronologically in UTC date groups. It shows total/returned counts, an explicit truncation message and the actual first/last timestamps. Category and text filters search only this returned window. Reset restores all returned entries. Without JavaScript, the complete returned window can still be read. Full history pagination and retention management are outside this feature.

Entries link only to tasks/attempts available in the page window and identify unrecorded metadata. Publication references are text; the page does not retrieve external files. The existing timeline service loads all projections for a Run before applying the output limit; this is not a database read limit or a performance guarantee.

## Refresh and actions

Read-only facts refresh every 5 seconds. Shared refresh protection retains focused/selected regions, expanded details and page scroll, and pauses while forms are edited or the page is hidden. Timeline filters, graph visibility and graph scroll survive refresh. A protected region may be an older snapshot; the refresh label reports this. Use **Refresh facts** or reload after reading.

Actions remain outside refresh regions so inputs are preserved. Their availability reflects page load; use **Reload action availability** to update it. Every submission still uses the original server permissions, CSRF protection, state checks and success/error feedback. Viewer, Operator, Approver and Admin keep their existing permissions.

See [FD-002](../features/FD-002_RUN_MISSION_DETAIL_AND_TIMELINE.md) for scope and verification evidence. Runtime and browser checks require their own recorded authorization.
