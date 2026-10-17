---
name: publish-github-issue
description: Publish or update a managed GitHub Issue summary from a local Issue or FD, with an optional OpenSpec change, when explicitly requested.
disable-model-invocation: true
---

# Publish local work to GitHub Issues

Follow `skills/reviewed-skill-contract.md`, `skills/work-management.md`, and
repository rules. Use only for an explicit publication/update request; normal
Issue discovery, FD completion and local archive do not publish or close remote
Issues. Loading this Skill does not authorize network, writes or local commits.
Permission to edit the Skill is not permission to publish project records.

## Resolve the requested source

Resolve the canonical local Issue (including legacy REQ), FD, or explicitly
requested OpenSpec change from current artifacts. Do not require a change or
create one to publish. If the source, target repository or disclosure scope is
ambiguous, mark `%% NEEDS_INPUT: ...` and resolve it before any remote write.

Read the Issue Plan for problem, goal, scope, acceptance and decisions. Read
selected FD(s), including archived ones, for engineering scope, actual Work
Items, verification and delivery. A draft is not approved; not-run checks are
not passes; local archive is not release. Read an OpenSpec change only when
present and relevant, or explicitly selected as the publication source.

## Choose the mapping owner

Read [references/publication.md](references/publication.md) before selecting
or changing a mapping, migrating legacy content, or writing remotely.

- An Issue normally owns one remote Issue. Its linked FDs reuse that target,
  each with its own managed block. Do not replace another FD's block.
- A standalone FD may own its own mapping. A linked FD uses a separate target
  only when explicitly requested and recorded as `publication_mode=independent`.
  Preserve that explicit strategy on later updates.
- Inspect both Issue and FD mapping locations and pending operation records.
  Unexplained different targets are a conflict, not a reason to pick one or
  create a replacement. The mapping owner and projected source can differ.
- If a linked FD initiates the first publication, create the Issue owner's
  problem/acceptance summary plus the selected FD's block, then save the
  Issue-owned mapping; do not invent a standalone FD mapping.

## Prepare and publish

1. Resolve `owner/repo` explicitly or from the current Git origin. Preserve
   the user's actual authorization scope; when already authorized, do not add
   a fixed-word confirmation checkpoint. Show the concrete target and summary
   before an external write. Ask only for missing authority or material scope
   decisions. Require `GITHUB_TOKEN` without displaying its value.
2. Use [scripts/projection.py](scripts/projection.py), or equivalent bounded
   behavior, to render selected summaries. Use `render_source_projection`
   with `source_kind=issue|fd|openspec` and the actual canonical ID; include
   source paths, current status, acceptance/progress and real verification.
   Keep detailed discussions, secrets and temporary diagnostics local.
3. Validate all applicable mappings and pending records first. For an existing
   target, call `aiw github --json get-issue` and verify repository/number/URL
   and current state. Do not reopen closed Issues or change titles unless
   requested. Missing, malformed or inaccessible mapped targets are BLOCKED;
   never silently create a replacement.
4. Apply `replace_managed_block` with explicit source kind/ID for new source
   blocks. Preserve all text outside that block exactly, other source blocks
   and the human title. Refuse malformed/duplicate/nested markers. Legacy
   OpenSpec block migration requires a verified source relationship and
   explicit migration scope, as described in the reference.
5. Persist a pending operation record before any create/update, then use
   `aiw github --json create-issue` or `update-issue --body-file` only within
   authority. Pass Markdown via a UTF-8 body file or stdin. Re-read the body
   immediately before an update and recompute if it changed; do not silently
   overwrite concurrent human edits. Full-body GitHub writes still have a
   race window without CAS; stop if safe reconciliation cannot be established.
6. Follow the reference's result/mapping commit order. Preserve pending intent
   and response evidence on unknown outcomes or local save failures; do not
   automatically repeat a create/update. Report the verified remote URL,
   mapping owner/path and actual result, or the exact preserved recovery gap.

## Completion and boundaries

Success requires a verified remote result and saved local mapping, not just a
rendered draft. A draft-only request stops before network/writes. If publication
or mapping commit fails, report BLOCKED/INCOMPLETE and preserve recovery evidence;
do not advance/approve/merge/archive local work, close remote Issues, or start
another FD. Reuse `github-issues-management` only for separately requested
general comments, labels, closing or querying, within the same authorization.

Validate references, source selection, block isolation and mapping/result
evidence statically under the repository budget. Do not run tests, builds or
extra remote probes merely to validate the Skill. The local Issue/FD remains
authoritative; this is a one-way summary, not a second plan or status owner.
