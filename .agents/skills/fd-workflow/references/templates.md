# FD templates and lifecycle

## FEATURE_INDEX.md

```markdown
# Feature Design Index

Planned features and improvements for {project_name}.

See `AGENTS.md` for FD lifecycle stages and management guidelines.

## Active Features

| FD | Title | Status | Effort | Priority |
|----|-------|--------|--------|----------|
| - | - | - | - | No active features yet |

## Completed

| FD | Title | Completed | Notes |
|----|-------|-----------|-------|
| - | - | - | No completed features yet |

## Deferred / Closed

| FD | Title | Status | Notes |
|----|-------|--------|-------|
| - | - | - | No deferred features yet |

## Backlog

Low-priority or blocked items. Promote to Active when ready to design.

| FD | Title | Notes |
|----|-------|-------|
| - | - | No backlog items yet |
```

## FD file template

```markdown
# FD-XXX: Title

**Status:** Open
**Priority:** Low | Medium | High
**Effort:** Low (< 1 hour) | Medium (1-4 hours) | High (> 4 hours)
**Impact:** Brief description of what this enables

## Problem

What we're solving and why it matters.

## Solution

How to implement it. Be specific about approach.

## Files to Create/Modify

| File | Action | Purpose |
|------|--------|---------|
| `path/to/file` | CREATE / MODIFY | What and why |

## Verification

How to test that it works. Concrete steps.

## Related

- Links to related FDs, docs, or issues
```

## CHANGELOG.md skeleton

```markdown
# Changelog

All notable changes to this project will be documented in this file.

The format is based on Keep a Changelog, and this project uses Semantic Versioning unless existing project conventions say otherwise.

## [Unreleased]
```

When closing a Complete FD, add one concise entry under Added, Changed, Fixed, or Removed. End the entry with `(FD-XXX)`.

## AGENTS.md section

Append or merge this section into the repository's `AGENTS.md`:

```markdown
---

## Feature Design (FD) Management

Features are tracked in `docs/features/`. Each FD has a dedicated file (`FD-XXX_TITLE.md`) and is indexed in `docs/features/FEATURE_INDEX.md`.

### FD Lifecycle

| Stage | Description |
|-------|-------------|
| **Planned** | Identified but not yet designed |
| **Design** | Actively designing |
| **Open** | Designed and ready for implementation |
| **In Progress** | Currently being implemented |
| **Pending Verification** | Code complete, awaiting verification |
| **Complete** | Verified working and ready to archive |
| **Deferred** | Postponed or blocked |
| **Closed** | Will not be implemented |

### FD Operations

Use the `fd-workflow` skill for:

- `init` — initialize or repair FD tracking
- `new` — create a feature design
- `explore` — inspect project, FD history, and recent activity
- `deep` — four-angle deep analysis with claim verification
- `status` — reconcile and report FD status
- `verify` — review and verify implementation
- `close` — complete/close/defer and archive an FD

### Conventions

- FD files: `docs/features/FD-XXX_TITLE.md`
- Default commit format: `FD-XXX: Brief description` unless repository history shows another convention
- Numbering: highest known FD number + 1
- Source of truth: FD file status over index status
- Archive: Complete, Closed, and Deferred FDs move to `docs/features/archive/`

### Index sections

1. Active Features — all non-complete active work
2. Completed — completed FDs, newest first
3. Deferred / Closed — postponed or rejected work
4. Backlog — low-priority or blocked candidates

### Inline annotations (`%%`)

Lines beginning with `%%` in FD/design documents are direct user annotations. Address every annotation; remove it after it is resolved. Preserve and report annotations that require an unresolved decision rather than guessing.
```
