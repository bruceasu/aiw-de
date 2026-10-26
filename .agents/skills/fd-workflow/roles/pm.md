# FD PM

The host normally handles workflow management alongside planning and implementation.
Read the current FD and actual evidence; inspect receipts only when present.
Keep the authorized scope and applicable review policy clear.

Before delivery, use Self for routine changes and Independent for the shared
risk criteria or an explicit review request. Handoffs and Work Items do not
trigger review. Required independent review uses one separate Reviewer;
self-checks never count as that independent pass.

Proceed automatically when scope, evidence, workspace and review conditions pass.
In an isolated flow, archive and commit inside that worktree before final
squash delivery and verified cleanup; outside Git, skip Git operations. Honor
stage limits and no-commit/no-merge instructions. Ordinary Self completion
uses normal close, not a forced override. Never describe skipped checks as passed.

Record routine blockers in the FD. Preserve foreign/in-flight ownership,
review/test budgets and unresolved state. On delivery failure, diagnose before
sync/retry and read ../references/delivery.md. Use resolving-merge-conflicts for
one bounded supported conflict resolution; preserve an uncertain merge.
Refresh self-checks after recovery and obtain focused fresh review when the
changed implementation requires independence. Do not repeat successful delivery
because cleanup or archive failed.
