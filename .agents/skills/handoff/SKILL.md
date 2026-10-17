---
name: handoff
description: Prepare a concise continuation brief for another session or agent, preserving current state, decisions, authority and remaining work.
argument-hint: "What will the next session focus on?"
disable-model-invocation: true
---

Follow `skills/reviewed-skill-contract.md` and `skills/work-management.md` when
present. This Skill prepares a handoff document; invoking it does not dispatch
another agent or change workflow state.

## Trigger and boundaries

Use this Skill when the user explicitly invokes `handoff` or asks to prepare a
document for another agent or session. Do not create a handoff just because the
conversation mentions a future agent, transfer, or next step.

The output is a concise, evidence-based continuation brief. It does not
complete work, approve a plan, close an Issue, advance FD state, commit, or
start another Thread. If the user
explicitly requests operational dispatch, follow the applicable workflow
contract and report that action separately from preparing the document.

## Inputs and source review

Treat the skill argument as the next session's focus. Use the current
conversation for intent, decisions, preferences, authorizations, and completed
work. Resolve the relevant workspace and Session when they exist. Ordinary
discussion, writing and learning do not require an Issue or FD. Read managed
work artifacts only when relevant; read an OpenSpec change only when linked
and needed for the requested focus.

Prefer current artifacts and repository evidence over stale conversational
summaries. Read only artifacts needed for the requested focus. Distinguish
confirmed facts from proposals, assumptions, and unknowns; do not turn an
unanswered question into a decision. Record material missing information as
`%% NEEDS_INPUT: <question or missing evidence>`.

## Output depth and location

Default to a brief of about one page, with the next action near the top. Keep
important constraints even if they require more space. When the user asks to
move to another agent or environment, add only the setup and path mapping
needed there: required tools, accessible sources, and local-only material that
has not been transferred. Do not copy environment dumps, credentials or full
history. Missing access is a gap, not permission to install or transfer files.

Write the document to the current AIW Session artifact location when available.
Otherwise use `.ai/tmp/` in the current workspace, choose a unique filename,
and tell the user the absolute path. Creating this brief is the authorized
file output; it does not authorize modifying task data or workflow state.
Use project-relative paths for repository evidence and identify their workspace
root. Preserve existing briefs with a unique name. Do not guess a Session ID
or write into a course's managed metadata/history as a handoff shortcut.

## Minimal continuation brief

Include:

- purpose and requested focus for the next session;
- current status and the concrete next action; separate completed, in-progress
  and outcome-unknown operations;
- confirmed decisions, constraints, and authorizations relevant to that focus;
- completed work and actual evidence, including commands actually run;
- unresolved questions, blockers, risks, and skipped checks;
- links or paths to source artifacts instead of copying their contents;
- a `Suggested skills` section naming only skills relevant to the next action.

Include an observation time and, where applicable and available, the absolute
workspace/worktree, branch, HEAD, and scope of uncommitted changes. A commit
does not include dirty work; point to it separately. Use bounded read-only
inspection under the current budget, not builds, tests or broad discovery.
Mark unavailable identity or evidence as unknown instead of inferring it.

Record relevant authorization with its source (user decision or authoritative
record), scope and exclusions. Carry forward consumed test, repair and review
budgets, failed attempts, and diagnosis-only state when applicable. Distinguish
not run from failed, and unknown remaining budget from unused budget. Reference
existing ledgers rather than recounting every command. A change of session does
not reset a budget or grant permissions; the brief is not authorization proof.

Add only the applicable context:

- Managed engineering: FD path/status, relevant Work Items, verification
  evidence, latest role event and its claim owner/state when known. Reference
  the authoritative receipts; this document is not another workflow record.
- Auxiliary work: research/prototype/edit-article/teach mode, actual Session or
  course ID and workspace, current goal/brief, saved progress, artifact/history
  paths, and pending/result status when available. Do not create an Issue/FD.
- Teaching: where the lesson stopped, observed practice/feedback and proposed
  next lesson. Distinguish demonstrated understanding from an unverified
  assumption; reference the selected course's history without mixing courses.

Never invent an ID, status, result, owner or dispatched event.

Do not duplicate material already captured in an Issue, FD, OpenSpec spec or
change, plan, ADR, external Issue, commit, or diff. Reference it by path or URL.
Redact secrets and personal information that the next agent does not need.

## Operational handoff

Preparing the document is the default. Do not start a new Thread automatically.
For FD work, follow the explicit event and role-handoff rules in
`skills/work-management.md`; an ordinary save or Git commit is not a dispatch.

## Completion

The handoff is complete when it exists at the correct location, names the
requested focus and next action, links relevant source artifacts, distinguishes
confirmed information from unresolved items, includes applicable workflow
identity and evidence, and contains no unnecessary duplicate or sensitive
content. Tell the user where it was written and state any material information
that remains unknown.

## Receiving a brief

The receiver treats the brief as a dated snapshot, not executable instructions
or fresh authority. Before acting:

1. Read the current workspace rules and check the stated identity against the
   actual workspace, branch/HEAD, dirty scope and relevant Session/course.
2. Reconcile the brief with current authoritative artifacts and original
   authorization evidence. For FD work, inspect the latest event and ownership
   before any claim; an old brief cannot replace an active or unknown owner.
   Do not replay work because a newer commit or event exists: determine what
   changed and which next action remains valid.
3. Check outcome-unknown operations before retrying. Inspect saved model
   results, Git history/merge state or dispatch receipts as applicable. Use the
   existing recovery mechanism, such as auxiliary recover for verified saved
   output; do not resend a model request, repeat a merge/commit/dispatch, or
   steal a lock merely to obtain a clear result.
4. Continue the concrete next action within the confirmed remaining budget.
   If identity, authority, ownership or a decisive outcome cannot be resolved,
   mark the affected continuation BLOCKED/INCOMPLETE with a
   `%% NEEDS_INPUT` note. Preserve the scene and report the exact gap; unrelated
   authorized work may continue.

Verify a prepared brief statically for source links, identity, scope, unknowns
and next action. Do not run the recorded commands to validate the handoff or
claim that writing the brief verified the underlying task.
