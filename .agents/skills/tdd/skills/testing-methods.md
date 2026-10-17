# Shared test design and evidence

Load for opted-in TDD or FD testing. The entry declares
`skills/testing-methods.md`; the installer supplies this canonical resource.
Read Design before creating tests, Execution before running, and Evidence
before reporting. This method grants no runtime, Git or FD state authority.

## Design

For each important scenario record a stable ID, acceptance/rule reference,
boundary, input/preconditions, expected observable result and its derivation,
key assertions, and a plausible incorrect behavior those assertions reject.
Group equivalent data rows under one rule; do not create paperwork per trivial
assertion. Missing policy blocks only affected scenarios, not independent work.

Derive the oracle from confirmed rules, contracts, independently worked
examples or justified invariants. Never copy the current implementation's
output, call production helpers to compute expected values, or reproduce the
same algorithm as the only oracle. Label snapshots of existing behavior as
characterization, not proof that the behavior meets requirements. A useful
invariant needs stated assumptions and should not replace exact examples when
the contract has exact outputs. Do not invent rounding, ownership or ordering
rules to make a test writable.

Select a small set by changed risk: normal behavior, relevant boundaries,
invalid input, permission/ownership, state transitions, duplicate requests or
dependency failure and side effects. These are choices, not a mandatory suite.
Explain consequential omissions. Coverage is a measurement, not correctness;
executing code without checking its behavior is not acceptance evidence.

Use a stable contract of the chosen module, not necessarily a public exported
API. Reuse an FD-defined boundary without asking again; ask only for material
ambiguity. Replace dependencies outside the rule under test, and disclose what
the replacement cannot validate. Do not mock away that rule. Call counts/order,
files and persisted results are valid assertions when part of the contract;
avoid incidental private structure. Do not redesign APIs or add dependencies
just to facilitate a test without the repository's required authorization.

For each key assertion, reason about a plausible faulty implementation and why
it would fail. Label this static reasoning. Fault injection or mutation testing
is separate execution requiring authorization; never claim it was performed
from reasoning alone. Do not add a mutation tool by default.

Example: an explicitly approved rule says fee = amount * rate, decimal
ROUND_HALF_UP to two places. Amount 10.05 and rate 0.10 give exact 1.005,
expected 1.01. Assert the exact fee; success status alone misses truncation.
Use this only if that rule is actually confirmed, not as a default policy.

## Execution

Before execution record the exact command, working directory, bounded scope,
implementation/test revision and dirty diff identity, authorization basis,
expected duration and side effects. Inspect invoked code/fixtures/cleanup and
apply repository rules, including language-specific prohibitions. A test
support request is not permission for dependencies, network, release artifacts,
broader suites or arbitrary commands. Cases-only is not execution authority.

Expected TDD Red and its planned Green are distinct phases of a predeclared
behavior cycle. Both require authorization within local budgets. Expected Red
is not a failed repair attempt; import/configuration/fixture errors are not
valid target Red. New interfaces failing to compile are preparation evidence,
not proof an assertion detects the intended behavior. Observe that assertion
when runnable. Do not claim TDD completed without actual Red and Green.

Unexpected failures use the repo's repair/rerun budget. Here: one focused
execution, at most one rerun after a relevant fix. Do not rerun unchanged
failures, widen scope, or reset the budget with a new session, command spelling,
scenario, review round or FD resume. Deliberately planned TDD phases are not a
loophole for extra failed Green attempts. Stricter local limits prevail. Record
planned cycles, runs, consumed retries and remaining authority; additional
cycles/commands are allowed only within an explicit preauthorized scope.

On exhausted budget, no safe fix, or unclear failure cause, switch the affected
testing effort to diagnosis-only. Stop its automatic test-driven repair and
execution, perform one bounded static diagnosis, retain files and raw outputs,
and continue independent authorized work. No repeated diagnostic loop. Do not
weaken assertions, edit requirements, silently skip evidence, or delete the
scene to obtain completion. Record a concrete candidate correction without
applying it in diagnosis-only. Resume that effort only on explicit renewed
direction and valid authorization; unrelated changes do not reset it.

## Evidence and feedback

Keep observed status (passed, failed, blocked, not-run, not-applicable) separate
from cause (product-defect, test-defect, environment, undetermined). A failed
assertion is an observation, not automatic proof of product fault. Cite the
evidence supporting the cause and leave uncertainty explicit. Partial execution
cannot pass the whole scenario.

Bind evidence to actual implementation commit plus dirty diff/snapshot identity,
test commit plus dirty diff/snapshot identity, FD revision, relevant environment,
commands, phase, exit code, and raw output location. Record reused tests and
implementation material read. Session separation alone does not establish an
independent oracle. Missing metadata is a stated limitation, not fabricated.
Keep paired Markdown/JSON facts aligned. Old reports remain unchanged.

Changing assertions after Red requires explanation and new relevant Red evidence
within authorization; weakening an assertion invalidates the original claim.
Later code/test edits make affected results stale. Reuse unaffected evidence
only with a reason; do not imply an old result executed against new code.

Relevant test facts and confirmed defects may go to Worker/PM and Reviewer;
they do not emit FD events, add coverage thresholds or own a release verdict.
Confirmed acceptance defects and material unknowns remain delivery blockers.
An environment failure may permit static assessment of adequately evidenced
behavior, with testing explicitly incomplete; it is never a test pass. An
archived FD keeps its historical evidence; report later defects for an explicit
follow-up FD or supported recovery, not silent archive edits.
