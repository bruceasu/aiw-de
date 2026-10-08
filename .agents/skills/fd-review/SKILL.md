---
name: fd-review
description: Independently review a numbered FD implementation and hand findings back to Worker or pass Verification.
---

# FD Review

Read `skills/work-management.md` and the repository instructions. Use this
Skill when an FD is Pending Verification or the user explicitly requests an
independent review. The Reviewer must use a separate session from the Worker
that wrote the code. Do not implement new scope during review.

Read the FD, Issue evidence when present, relevant stable specs, the Worker's
implementation report, and the exact diff or commits under review. Check
correctness, acceptance coverage, compatibility, unexpected files, and the
truth of each reported verification result. Run no test or final build unless
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
`docs/features/reviews/`. Include FD ID, source event ID, reviewed commit or
diff base, findings with file paths, commands actually run, skipped checks,
and residual risk. Keep the report separate from the Worker's report.
For an FD with `**Evidence policy:** Dual`, write the human report in Chinese
and a same-basename structured JSON sidecar with schema
`aiw.fd.evidence.v1`, kind `reviewer-report`, FD ID, source event, Markdown
filename, and structured findings/results. Put one `<!-- aiw-data: <same-basename-json-file> -->`
comment in Markdown and use the Markdown as `--artifact`.

Do not inspect or evaluate optional `fd-test` reports. They do not add
acceptance conditions or replace evidence required by the FD. Review only
artifacts and evidence relevant to the stated acceptance.

If a material issue remains, emit `changes-requested` with `--producer
reviewer --artifact <report>` and `--source-event <id>` when the original
handoff was dispatched. This returns work to Worker. If all stated acceptance
conditions are supported by real evidence, emit `verification-passed` with the
same identity fields. Update the FD Verification section with the report path
and outcome. A passed review permits a later close decision; it does not
push, merge, release, deploy, or archive by itself.
