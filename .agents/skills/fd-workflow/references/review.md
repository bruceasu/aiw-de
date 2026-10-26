# Conditional independent review

Read when the FD's review policy requires independent review or the user
requests it. Routine Self work records its checks in Verification and proceeds
to delivery. A handoff or a completed Work Item does not trigger review.

## Request one review before delivery

Use the shared contract's risk criteria. Record **Review policy:** Independent
and a short reason in the FD. With the whole implementation ready, use
aiw fd request-review <id> --reason "..." ; the command sets Pending
Verification and creates the Reviewer request without a forced status change.
For an existing managed Worker handoff, complete that handoff normally instead.
Do not create a second request over a current pending or in-flight Reviewer.

Spawn one separate Reviewer subagent using fd-review. Give it the FD, exact
pending event, actual diff/commits, FD Verification, any relevant reports and
repository limits. It claims its own event and writes a concise independent
report before emitting verification-passed or changes-requested with that
source event. Use the FD itself as implementation evidence when no Worker
report exists. Never substitute a host-written independent verdict.

If the required independent session is unavailable, preserve the request and
report the limitation. Missing review capability does not block unrelated
Self FDs or authorize weakening this FD's chosen review policy.

## Repair and continue

Read the actual report and receipt. Repair actionable findings within the FD
scope; a focused recheck covers the fixes and affected behavior rather than
restarting the whole review. Retain review history and the existing maximum
of three failed outcomes across resumes. Stop when that cap is reached or
there is no safe actionable repair. Do not fabricate a pass or reset budgets.

On a current independent pass, continue [delivery](delivery.md) within the
user's stage limits. For Self work, the corresponding gate is a complete FD
and truthful self-check evidence. Unresolved defects or blocking questions
prevent either route from proceeding.
