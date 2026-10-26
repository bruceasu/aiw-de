# Handoffs and event recovery

Read before claiming an event, recovering a stale handoff, or reopening an FD.
Shared resources resolve through [dependencies.md](dependencies.md).

Routine single-host work without a runner needs no stage handoffs. The default
new command creates only the FD; --handoff or a configured runner selects the
managed path below. Existing receipts retain all ownership and recovery rules.

Use `aiw fd list`, `show`, and `resume` for status and recovery. A pending event
can be handled by the named role in the current host after `aiw fd claim <id>
<event-id> --session <host-session-id>`. A dispatched event with unknown outcome
must be reconciled against its original session or log.

The `--session` value is an opaque ownership reference for this host session;
it need not be a platform-provided ID. Prefer a stable ID supplied by the host.
If none is available, the role handling the event must generate its own unique
reference (for example, `fd011-reviewer-20261001-8f3c2a`, with a freshly
generated suffix), keep it for the whole session, and reuse it for an
idempotent claim. Do not ask the human to
provide an agent session ID or borrow another role's reference. Check the
latest receipt immediately before claiming: only claim a pending event for
the current role, and never replace a launching or dispatched owner. Record
the chosen reference in the role's report with the source event ID.

For an active `Pending Verification` FD blocked by a stale, unclaimed handoff,
`aiw fd request-review <id> --reason "..."` creates a fresh Reviewer request
for the current FD. It does not fabricate Worker completion. An independent
Reviewer claims that event and reports findings. Do not use this recovery path
for an in-flight event or to bypass unresolved `%% NEEDS_INPUT` gates.

For an active `Open` or `In Progress` FD whose latest pending Worker handoff
no longer matches the FD revision or digest, PM uses `aiw fd refresh-worker
<id> --reason "..."`. It cancels the stale receipt and creates a new
`work-requested` Worker handoff bound to the current FD. Claim the new event
and cite it on `implementation-ready`. Never use this operation to replace
a claimed or in-flight handoff, or edit the old receipt by hand.

To continue an archived `Closed` or `Deferred` FD, use `aiw fd reopen <id>
--reason "..."`. It preserves the earlier disposition and evidence, returns
the FD as `In Progress`, and creates a new Worker handoff. Claim that exact
event before editing and cite it on `implementation-ready`. Keep previous
Work Item checkboxes as historical progress; revise them only when the renewed
scope warrants it. Use distinct filenames for new reports and reviews (for
example, a revision suffix), since `close` preserves earlier archived evidence
and rejects collisions. An archived `Complete` FD uses `request-review` instead.
If a reopen reason is unreadable or mistaken, correct it with `aiw fd reopen
<id> --reason "..." --correct-reason` before claiming the pending Worker
event. This managed correction keeps the event ID, records the previous value,
and updates the FD and receipt digest together. Never hand-edit a receipt.
