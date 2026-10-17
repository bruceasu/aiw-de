---
name: code-review
description: Independently review a branch, PR or committed diff against repository standards and requirements, including work without a numbered FD. Explicit invocation permits up to two bounded static-review agents.
---

# Code Review

Read `skills/reviewed-skill-contract.md`, `skills/work-management.md`, and only
Implementation review in `skills/workflow-methods.md`. This is an explicit
standalone review entry, useful without a numbered FD. It does not replace
fd-review's managed event, approve a release or authorize local delivery.
FD review uses the same method directly, not this two-agent orchestration.

## Fix the review scope

Resolve the requested commit/branch/tag or merge-base and capture its OID plus
the reviewed HEAD. Use `git diff <base>...HEAD` for the committed comparison and
record its base. If no fixed point is supplied and none is unambiguous, ask
for it. If a ref is invalid or the scope has no changes, report that result
before dispatch. Explicitly requested uncommitted work must be included and
identified; a committed diff alone does not cover work in progress.

Read the originating FD/approved Issue when present, otherwise the supplied
spec or relevant stable requirements. Ask for missing intent when needed;
if no spec is available, report Spec as unavailable, never passed. Read actual
repo coding standards and the shared method's bounded smell heuristics.

## Independent review and output

Explicit invocation permits at most two bounded static-review subagents, one
Standards and one Spec. Give each the fixed diff/revisions, relevant context,
shared method and repository budget. Neither executes tests/builds/network,
changes files, claims FD events or commits. The Spec reviewer also traces
correctness and failure paths; both report evidence limits. If no spec exists,
skip that axis and label it unavailable. If independent review capability is
missing, report the limitation rather than claim two independent reviews.

Aggregate Standards and Spec findings separately with file/location, supporting
rule/requirement, impact and correction. Label smell judgments and uncertainty;
include reviewed revisions, actual commands, skipped checks and residual risk.
Do not let a style pass mask incorrect behavior or vice versa. Run one bounded
pass, not automatic repair/review loops. This output is advisory standalone
evidence; fd-review remains the owner of a requested FD verification event.
No test, build, format, lint, network, push, release or deployment is authorized.
