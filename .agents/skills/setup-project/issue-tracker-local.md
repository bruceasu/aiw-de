# Local Work Tracking: FD Workflow

Use the AIW Issue and numbered FD workflow for new engineering work. This file
is a reference for local artifacts; it does not define a separate issue
tracker or duplicate Issue/FD lifecycle rules.

## New work

1. Use `issue-management` to discover and decide an Issue when the request
   needs problem clarification or approval. Use `aiw issue` for durable Issue
   records; check CLI help before an unfamiliar or mutating command.
2. Use `fd-workflow` to create or refine the engineering plan. A new FD is the
   numbered file `docs/features/FD-XXX_SLUG.md`, created through `aiw fd new`.
   It owns engineering decisions, ordered Work Items, status, Verification,
   and unresolved `%% NEEDS_INPUT` notes. `docs/features/FEATURE_INDEX.md` is
   only its index.
3. Use `implement` to execute ready FD Work Items. Follow
   `skills/work-management.md` for role handoffs, state, and evidence.

An Issue may link to an FD. A Task is not required for a new FD. Create an
OpenSpec change only when the user explicitly requests one; stable capability
specifications belong in `openspec/specs/`.

## Temporary files under `.ai/`

Use `.ai/requirements/drafts/` for temporary Issue drafts produced by
`issue-management`. Keep drafts project-relative, UTF-8, and within 64 KiB;
they are proposals, not approved Issue records. Do not create local ticket
files or copy durable decisions, FD status, or Work Items into this directory.

Use `.ai/tmp/` only for disposable working notes or temporary output that has
no dedicated artifact location. Use unique filenames and remove temporary
files when their work is incorporated or no longer needed. Do not use this
directory as an alternate source of truth.

AIW manages `.ai/fd/<fd-id>/` as role handoff receipts and logs. Treat it as
generated workflow data; do not handwrite events, reviews, or FD status there.

## Existing `.scratch` data

`.scratch/` is legacy data. Read existing files when a request references
them, but do not create or update `.scratch` maps, tickets, statuses, comments,
or wayfinding records for new work. Migrate only when the user explicitly
requests migration, preserving links and history in the appropriate Issue or
FD artifacts.

When asked to fetch existing work, resolve an AIW Issue by its Issue ID or an
FD by its ID/path. Do not infer an artifact from a bare title when multiple
records could match.
