# Publication identity and recovery

Read for mapping selection, legacy migration and all remote writes. These are
publication artifacts; they do not own Issue/FD lifecycle or grant authority.

## Stable mappings

New Issue/REQ-owned mappings: `docs/external/github/issue/<canonical-id>.json`.
FD-owned mappings: `docs/external/github/fd/<canonical-id>.json`. These paths
survive movement of Issue/FD records to archive. Resolve canonical IDs from
actual records, never from filenames guessed from user abbreviations. Use
`mapping_path` for new locations; reject linked/redirected paths. An explicitly
selected standalone OpenSpec change may retain its legacy mapping location.

Mappings keep version 1 with repository, positive integer issue_number and a
matching GitHub Issue URL. New mappings also identify their owner with
source_kind/source_id, publication_mode (`aggregate` for Issue, `independent`
for FD), published_at and content_hash. The content hash describes the body
actually sent, not all local source files. Record projected source IDs in the
body; the owner is not necessarily the source of the latest block update.

Inspect Issue and FD mappings before choosing the target. A linked FD normally
uses only the Issue mapping. An explicit independent FD mapping is a deliberate
separate remote Issue; do not aggregate it later without renewed direction.
Different unexplained targets, identity mismatch or invalid mapping stop work.
Never overwrite a mapping to another repository/number as a recovery shortcut.

Old version-1 mappings without owner fields remain readable, including
`openspec/changes/<change-id>/external/github.json`. Verify their relationship
from current local evidence and the fetched Issue, not merely a matching title.
If a legacy and a new mapping coexist, require the same target and unambiguous
ownership or stop. Migration is explicit: retain the old evidence and record
the new owner/target; do not delete the old mapping or create another Issue
silently. A stale legacy copy must be reconciled before later publications.

## Managed blocks and compatibility

New blocks use `<!-- aiw:source:issue:ISSUE-00001:start -->` and the matching
end marker; FD and OpenSpec blocks use their corresponding kind/canonical ID.
Each source has one non-nested block. Updates preserve other source blocks and
every byte outside the selected block. Keep the complete remote body within
64 KiB; reduce generated summaries rather than truncating human content.

The old render_projection and two-argument replace_managed_block functions
retain OpenSpec markers and historical whitespace behavior. New callers pass
source_kind/source_id. Converting an old OpenSpec block requires explicit
migrate_legacy=True, a verified source relationship, one complete legacy block
and no existing destination block. Do not treat an unrelated legacy block as
the selected Issue/FD: preserve it and resolve migration scope with the user.

## Pending operation and unknown results

Before remote create/update, atomically write `<canonical-id>.pending.json`
beside the owner mapping. Include version, operation, repository, mapping owner,
projected source IDs, request time, body hash and the Issue number for update.
Use a unique operation ID and exclusive creation; an existing pending record
blocks another writer. Keep intended body/evidence locally with the operation
so recovery can compare markers/hash without exposing credentials. Check both
Issue-owner and FD pending locations for linked work. This is a receipt for one
publication operation, not a task scheduler or Issue/FD state machine.

On a successful response, first atomically attach its repository/number/URL to
that pending record, then save the validated owner mapping, then remove only
that completed operation's pending record. `save_mapping` writes atomically,
but does not publish, resolve lineage, acquire a lock or manage this receipt.
Generic local artifact tools may be used for receipt creation/updates; writing
a plain partial JSON file is not an atomic receipt. Never remove an unknown
or another operation's pending record.

On timeout, cancellation, malformed response or local write failure, preserve
the receipt and any returned target. Stop new writes. With authorization, first
read a known remote target, or for an unknown create use repository, source
markers and request time to investigate existing Issues. Do not equate an
absent mapping or an inconclusive search with permission to create again.
If identity/outcome remains uncertain, report the exact gap for human direction.
Once verified, complete the missing local mapping steps without repeating the
remote write. A confirmed failure/retry needs an explicit recorded recovery
decision and normal write authority. Publication and FD completion stay separate.
