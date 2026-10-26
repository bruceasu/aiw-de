---
name: fd-review
description: Independently review a numbered FD implementation and hand findings back to Worker or pass Verification.
---

# FD Review

Read `skills/work-management.md`, the repository instructions, and only
Implementation review in `skills/workflow-methods.md`. Declare
`skills/testing-methods.md`; read Evidence and feedback only when relevant
test evidence exists. Use this
Skill when an FD is Pending Verification or the user explicitly requests an
independent review. The Reviewer must use a separate session from the Worker
that wrote the code. Do not implement new scope during review.

Inspect and write evidence from the FD's selected workspace. For explicit auto
inside Git, claims, FD updates and reports all run in that FD worktree; do not
write review artifacts into the parent workspace.

Read the FD, Issue evidence when present, relevant stable specs, FD Verification
and any separate Worker report, and the exact diff or commits under review. Check
correctness, acceptance coverage, compatibility, unexpected files, and the
truth of each reported verification result. This includes actual code logic,
affected call paths and failure behavior, not only delivered documents. Apply
the shared Correctness, Standards, Spec and Evidence dimensions; report hard
violations separately from smell heuristics. For docs/config-only work, review
those actual contracts. Do not invoke code-review or spawn its two-axis agents
inside this FD review: one independent Reviewer owns the combined FD verdict.
The shared method grants no extra review rounds. Run no test or final build unless
the repository authorization rules permit it. An unrun check is not passed.
Use the FD's latest recorded human decisions when judging required evidence.
If a check was explicitly waived, assess the remaining behavior using the
available static or user-provided evidence and report the untested risk; do
not repeat an older review's demand for that check. A waiver of testing does
not waive an unmet behavior requirement. Name the exact current requirement
and missing evidence when requesting changes.
When taking a pending Reviewer handoff in a host session, first claim its
exact event with `aiw fd claim <id> <event-id> --session <host-session-id>`.
Use this Reviewer's host-provided ID if available; otherwise create a unique,
stable reference in this Reviewer session and use it consistently. The value
is an ownership label, so the human need not supply it. Never use the Worker's
reference. Check the latest receipt before claiming and do not take over an
event owned by another session. Include this Reviewer's reference in the report.

Write a concise review report to a project-relative file under
`docs/features/<FD-ID>/reviews/`. Include FD ID, source event ID, reviewed commit or
diff base, findings with file paths, commands actually run, skipped checks,
and residual risk. Keep the independent report separate from implementation evidence.
For delivery recovery, inspect the recovery commit and diff together with the
original Worker report and supplemental recovery evidence. Do not treat a
commit or an empty sync result as proof of correct conflict resolution. A
clean parent advancing alone does not require a new review of unchanged FD work.
For an FD with `**Evidence policy:** Dual`, write the human report in Chinese
and a same-basename structured JSON sidecar with schema
`aiw.fd.evidence.v1`, kind `reviewer-report`, FD ID, source event, Markdown
filename, and structured findings/results. Put one `<!-- aiw-data: <same-basename-json-file> -->`
comment in Markdown and use the Markdown as `--artifact`.

Inspect relevant optional test evidence: confirmed rule/oracle, key assertions,
actual tested revisions, failure classification and unverified limits. Read
actual test code where needed, not just pass counts. Reports add no automatic
coverage threshold, extra test-review agent or acceptance condition. Respect
diagnosis-only and existing budgets; no automatic tests or test-driven repair
restart. Confirmed acceptance defects/material unknowns still block a pass.
Review only evidence relevant to the stated acceptance; an environmental
failure may leave testing incomplete while other evidence supports behavior.

If a material issue remains, emit `changes-requested` with `--producer
reviewer --artifact <report>` and `--source-event <id>` when the original
handoff was dispatched. This returns work to Worker. If all stated acceptance
conditions are supported by real evidence, emit `verification-passed` with the
same identity fields. Update the FD Verification section with the report path
and outcome. The Reviewer event does not itself merge or archive. For a normal
full FD implementation, the host PM automatically checks the shared delivery
gates and continues local delivery/archive; explicit review-only or no-merge
limits still apply. The Reviewer does not push, merge, release, deploy, or archive.
