# FD templates

Use the repository's docs/templates/TEMPLATE.md; the CLI fallback follows the
same minimum structure. Keep the template short. New FDs start as Design in
single-host use; managed dispatch uses the real design-requested transition.

A routine FD needs:

- Status, Revision, Review policy: Self, Evidence policy: Markdown.
- Problem, Solution, Scope and observable Acceptance.
- Necessary numbered Work items, TODO and Verification.

Add options, interface details, compatibility analysis or a file inventory
when they clarify a material decision. Keep Work Item IDs stable after work
starts; explicitly cancel superseded items with reasons.

Verification records planned checks first, then actual commands/results,
skipped checks, risks and the short review-policy reason. Use Independent for
the shared risk criteria or an explicit review request. Old FDs without that
field retain their historical independent-review requirement. Do not remove
existing Dual policy or rewrite old reports to fit the new defaults.

## Navigation and completion

Each FD owns its folder and index.md. FEATURE_INDEX.md is fixed navigation;
aiw fd list supplies current status. Status stays in the design rather than
a shared mutable status table.

Design is planning, Open is ready, In Progress is implementation, Pending
Verification means independent review is requested, and Complete means the
selected verification route is satisfied. Self completion does not claim an
independent verdict. Complete, Closed and Deferred archives live under
docs/features/archive/<FD-ID>/ with their matching evidence.

Update a changelog only when the repository uses one. Preserve explicit Git
and stage limits. Local completion does not grant push, publication or deployment.
