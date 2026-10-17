# One bounded second opinion

Read only for consultation, not default navigation. Follow the parent
[SKILL.md](../SKILL.md) and repository authorization/budget. Permission to update
this Skill does not authorize using it to call a model.

## Choose the channel

Use the current host directly for routine state lookup and decisions. When a
second opinion is authorized, pick one suitable channel:

| Channel | Suitable purpose | Preconditions and limits |
| --- | --- | --- |
| Host subagent | Independent reading of a bounded design/question | Host capability and actual delegation authority; one read-only assignment, no recursive delegation or runtime operations |
| aiw ask | Consultation through the configured AIW provider | Model/network and local-history authority; fixed known provider, one single-turn call, explicit context |

Do not call both to compare answers by default. If the channel is unavailable,
prepare the consultation brief or report that limitation; do not silently fall
back to the other channel. The consultation is not a managed Reviewer role.

For a host agent, supply the bounded question and necessary artifacts, the
permitted access and no-edit/no-execution constraints. Request a proposed answer
with supporting evidence and uncertainty. Specify a different model/reasoning
setting only if actually supported and authorized; otherwise retain the host's
allowed settings and state what is known. A new agent alone is not an upgrade.

## aiw ask capabilities and side effects

The current command accepts --json, --system-prompt, --system-prompt-file and
--allow-path; chat/resume exist but are not the single-consultation path. It
loads the configured provider/model. There are no ask-specific --provider,
--model or --reasoning flags. Do not invent them or edit global/private config
to obtain an upgrade. Already-authorized process-local configuration may be
used when supported, with known settings recorded and no secret values printed.

Before calling, inspect the effective non-secret configuration within scope.
A blank/auto provider can try multiple detected backends on failure inside one
invocation. Therefore require a known fixed provider for this one-attempt mode;
if it cannot be established within authority, do not invoke ask. One host call
does not guarantee the number of provider/SDK-internal transport attempts;
record any known limits instead of claiming exactly one remote request.

Ask validates its structured Answer protocol; --json returns that object, not
arbitrary free-form Markdown. Put the concrete bounded question in the prompt
and keep system instructions compatible with the Answer protocol. Do not pass
this whole routing Skill, its frontmatter or instructions to dispatch roles as
the consultation system prompt. A rejected response ends the attempt.

Ask attempts to save questions/results/errors under ~/.aiw/ask, including
single-turn use. This history side effect needs to fit the authorization;
successful answer output does not guarantee history persistence succeeded.
It is not a no-write navigation command. --allow-path authorizes directories,
not individual files; do not send the workspace root as a convenience grant.
Filesystem-aware CLI providers may see their working directory and enforce
different restrictions; readonly/add-dir are not a universal read allowlist.
If only a supplied text snapshot is allowed, choose a channel that respects
that boundary or stop rather than claiming isolation.

System prompt delivery is backend-dependent: the current Copilot ask path
passes the task prompt without the separate SystemPrompt, while other paths
can forward it. Do not rely on a system prompt flag as an isolation boundary;
include the essential question/context limits in the task prompt and use only
a backend whose known behavior fits the authorized task. Record a capability
gap instead of fixing provider code or switching backends during consultation.

## Context, result and stopping

Before the attempt state the question, evidence/revision boundary, requested
channel and known settings, authority, expected cost/duration where known,
and side effects. Supply selected facts/excerpts explicitly; do not assume the
model receives this conversation or has read the project. Exclude secrets.
Prefer an existing authorized prompt artifact when suitable; creating another
file also needs output authority. Do not run interactive chat/resume, background
processes or a chain of consultations to solve the question.

Return the outcome to the owning discussion/FD with assumptions, contradictions
and limitations. A needs_clarification/unsupported/error/interrupted/invalid
result is not an accepted recommendation. Failure, timeout or unknown outcome
ends the attempt; inspect existing results before any separately authorized
retry, and never reset a budget by changing channel/model/command/session.
Do not weaken acceptance or convert advice into human approval. No method here
authorizes edits, test repair/execution, lifecycle events or delivery operations.
