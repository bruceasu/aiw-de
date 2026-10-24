---
name: handoff
description: Prepare or resume a concise task handoff that preserves current evidence, exact references, permissions, unknown operations and the next safe step.
---

# Handoff

Use for an assigned pause, transfer, resumption or substantial context change.
Do not produce a full history after every tool call. Keep the primary task,
role, tools and result format. A handoff is a summary, not a dispatch event,
task approval or a new source of authority.

## Prepare a useful brief

Start with current focus, actual state and the next action. Use the existing
formal task and result records, revisions, workspace evidence and operation
receipts as inputs. Include only the details needed to continue:

1. Task and goal references, scope, acceptance and the current source revision.
   Copy exact known identifiers; mark unknown ones as unknown.
2. Completed work and concrete artifact paths or commits, with the observations
   that support completion. Separate code written from checks run and outcomes.
3. Pending work, blockers, decisions, assumptions and the highest-impact question.
   Keep failed checks and unresolved review findings visible.
4. Current workspace/operation identity and relevant unrelated edits. Record any
   in-flight or unknown-result operation with its original reference and known
   status. Do not replace it with a new operation because the result is unclear.
5. Current permissions, read-only or diagnosis-only limits, commands actually
   run, remaining retry budget when known, and actions still requiring authority.
6. One concrete next step, its prerequisites and a stop condition. Include links
   to authoritative records instead of copying large logs or the whole conversation.

Do not include credentials, tokens, private unrelated data or invented session
IDs. A short brief should explain what can proceed and what must first be
reconciled. Preserve important failures even when a later summary is shorter.

## Resume from a brief

Treat the brief as a dated snapshot. Compare it with the current formal task,
frozen configuration, source revision, permitted scope and original operation
result before acting. Keep confirmed decisions, but recheck stale state. If the
brief conflicts with a current authoritative record, report the mismatch through
the normal gap path rather than silently switching scope or taking ownership.

Reconcile an unknown operation using its existing reference and available status
adapter. Without decisive evidence, leave dependent actions pending. A handoff
does not reset retry limits, grant missing tools or allow replay of a write.

## Deliver and finish

Use the primary output's existing description/report fields, or an explicitly
assigned writable file. State whether the brief is prepared or resumption was
actually performed. Completion means another authorized participant can identify
the true state, evidence, blocker and next step; it does not mean that participant
has been dispatched. Do not create tasks/threads, claim locks, transfer sessions,
change workflow status or send external messages on the strength of this Skill.
