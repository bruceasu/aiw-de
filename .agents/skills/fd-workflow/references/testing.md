# Opted-in test support

Shared `skills/...` resources resolve via [dependencies](dependencies.md), not this directory.

Load only when the user requests test support. This is host orchestration, not
a new CLI mode, FD state or legacy Tester route. Read the needed sections of
`skills/testing-methods.md`. Ordinary auto remains without tests.

## Select and plan

Recognize intent, not a token: `with test`, `with tests`, `带测试`, `需要测试支持`
or equivalent wording in any language selects suitable core-rule TDD during
implementation and a separate fd-test round after implementation, before the
implementation-ready event. A quoted example, discussion of tests, or negated
request does not select execution. Cases-only, no-TDD, no-execution, stage and
item limits prevail; record the resolved scope rather than forcing both modes.

Planner records this selection in FD Verification: target rules/boundaries,
appropriate TDD slices (or why none fit), independent acceptance scope, commands
or how exact commands will be authorized, finite phase/run budget and evidence
locations. This is part of the FD, never a second plan. User selection requests
test support but does not bypass repository runtime/language restrictions.
When exact Planner approval is required, inspect invoked code/side effects and
record a revision-bound decision before execution. Escalate only actions the
repository reserves for the human. Missing authority blocks execution only;
prepare cases and continue independent permitted work without repeated asks.

## Worker and independent testing

Worker loads tdd for suitable deterministic core behavior, follows the shared
oracle and valid Red/Green rules, and records cycles and actual outcomes in
its implementation report. Framework/setup errors are not target Red. Use
root tests/ here and honor local Go restrictions. Do not force TDD onto every
document/configuration edit or claim it from post-implementation tests.

After scoped implementation and compile/static checks, host commits the current
implementation/test changes and fixes the tested revision. Before emitting
implementation-ready, delegate one bounded fd-test assignment to a session
separate from Worker. Initially provide the FD, acceptance/public contracts,
relevant specs, scope, repository rules, worktree, report templates and budget
ledger; do not prime its expected results with Worker conclusions. It records
scenarios first, then reads necessary implementation/test facilities for
adaptation. New tests may be uncommitted; capture their diff identity, do not
claim HEAD contains them. Record reused tests and access limits.

The test agent writes only assigned root tests/ and its paired report in the
recorded FD workspace. Worker/host must not edit that workspace concurrently.
It claims/emits no FD events, changes no FD state and performs no Git delivery.
It returns facts to host/Worker; host inspects and commits scoped test/report
changes. Availability failure is recorded, never replaced by self-authored
independence. Do not launch Reviewer while the Worker handoff is still active.

For a confirmed product defect within remaining authority, Worker repairs it
and preserves the reproducer. Rebind any approval to the new actual revision
and rerun only when allowed. A test defect needs rule-based correction; never
change an oracle merely to fit production. Unknown causes trigger diagnosis.
No separate PM test-acceptance vote or mandatory coverage metric is introduced.

After acceptance scenarios/oracles have been recorded, an unclear failure may
use diagnosing-bugs to distinguish product, test/oracle, fixture/setup and
environment causes from existing evidence. This does not grant implementation
access beyond the assignment, change an oracle or authorize another execution.
At diagnosis-only, use its static path once, or reference the analysis already
produced for that effort; no additional repair, instrumentation or runtime loop.
Structural observations may suggest codebase-design or a scoped architecture
discussion to Planner, but do not turn a test assignment into refactoring.
If the Skill is unavailable, retain the shared bounded diagnosis behavior and
record the limitation. No extra agent or workflow event is introduced.

## Persistent failure: diagnose and continue

Use shared Execution's finite budget and retain the ledger across resume,
agent changes and review repairs. Under current repo rules, one unexpected
failed execution permits at most one rerun after a relevant correction.
Predeclared authorized Red/Green phases are separate from repair retries.
An unchanged failure does not earn a retry. A new test name/command is not a
new allowance for the same failing effort. Do not grant tests to this FD merely
because it changes testing Skills.

At exhaustion, no safe repair or unclear cause, automatically record
diagnosis-only: stop affected test-related repair and all further executions
in that effort, perform one bounded static diagnosis, preserve original output,
candidate correction, versions, consumed budget and unresolved behavior. Record
this in FD Verification and the test/Worker reports. Continue other independent
authorized implementation, docs and static review. Do not ask the human to
choose a failure menu; renewed execution/repair requires explicit new direction
and valid authority. Explicit stop/termination always takes precedence and
preserves the scene. Later unrelated edits do not reset diagnosis-only.

Continuing does not waive acceptance: confirmed defects, material unknowns or
unresolved implementation items still block full-FD implementation-ready,
merge and Complete archive. Provide partial static feedback only within an
authorized scope, with no full-FD event or false readiness. Environment/test
failure alone is not proof of a product defect; if actual behavior can be
supported by other evidence, independent Reviewer may assess it while clearly
recording testing incomplete. Never convert not-run/failed tests to passed.
Missing execution authority or test-session capability cannot be silently
waived; record the incomplete requested scope and any needed decision.

## Review and delivery

When whole-FD readiness is supported, Worker links actual test evidence and
remaining limits in FD Verification or the required managed report. Emit
implementation-ready only for an actual handoff within user stage limits.
The review policy selects Self completion or an independent Reviewer.
A required Reviewer can inspect relevant scenario oracles, assertions, version
coverage, cause classification and diagnosis. It owns the implementation
verdict; fd-test supplies facts, not status/thresholds or a delivery verdict.
Review requires no second test-review agent. A test report completed in
diagnosis-only does not by itself satisfy behavior acceptance.

If later review or merge recovery changes tested behavior, mark affected
results stale. No automatic test repair/run after diagnosis-only; use valid
remaining authority or record the limitation and gate. Existing review drift
rules still apply. Final records distinguish code/static review, compile-only,
tests actually run, requested tests incomplete and remaining risks.
