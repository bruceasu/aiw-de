---
name: diagnosing-bugs
description: Diagnose reported bugs, performance regressions or unclear test failures from available evidence; use bounded runtime reproduction only within existing authorization.
---

# Diagnose bugs

Follow `skills/reviewed-skill-contract.md`, `skills/work-management.md` and
repository rules. Use for a reported symptom or when the active FD/test flow
routes an unexplained failure here. Do not run this for every passing test or
routine code edit. Diagnosis returns evidence and recommendations; it does not
grant implementation, instrumentation, tests, Git or environment operations.

## Establish scope and evidence

Resolve the expected behavior from the user/Issue/FD/spec and capture the actual
symptom, input, relevant revision/dirty changes, raw output and accessible scope.
Read the affected call path and relevant CONTEXT/ADRs only when useful. Record
original commands as observed, proposed or not run; never invent reproduction.

Keep the observed result separate from its cause. Classify cause as product
defect, test/oracle defect, fixture/setup defect, environment failure or
undetermined, citing evidence. A failed assertion is not automatically a product
defect; a compile/import error is not a successful behavioral reproduction.

## Static diagnosis by default

Use existing output, traces and source to follow the failing behavior. Static
analysis does not require a previously executed red command. Rank a small set
of plausible explanations only as needed; state each prediction and what
evidence would distinguish it. Compare supporting and contradictory evidence.
Do not force a hypothesis count or repeated human checkpoints for routine facts.

Identify the narrowest confirmed cause or label the conclusion a supported
hypothesis/undetermined. Show the chain from input and caller to failure and
relevant contract; proximity to the error line alone is not proof. For timing
or nondeterministic behavior, mark unmeasured assumptions and request only the
decisive missing capture or authorized measurement when required. Do not claim
a performance improvement from source inspection alone.

## Optional runtime diagnosis

Reuse already-authorized evidence before proposing another experiment. Select
the smallest command that reaches the actual symptom and distinguishes a
specific prediction; inspect its side effects and record command, cwd, scope,
revision, authorization, duration and remaining budget before execution. If
runtime evidence is decisive but unauthorized, follow repository approval rules
and continue independent static work while it is unavailable.

No test/harness creation, debugging instrumentation, stress/fuzz loops,
profiling, git bisect, replay, server startup, browser or network activity is
authorized by loading this Skill. These need their applicable scope/authority;
do not increase reproduction rate through unbounded retries or concurrency.
Minimize an input only within the authorized experiment budget. A useful
reproducer tests the user's behavior, not merely successful startup. A suggested
command is not an observed result and does not authorize execution.

## FD and test collaboration

Return findings to the current FD/Worker or assigned test report. If authorized
implementation is in progress, Worker may repair a confirmed in-scope defect
under its existing authority, outside this diagnostic method. A regression
test is a proposal unless separately authorized; use tdd/fd-test only when
selected and allowed. Test authors retain acceptance-derived oracles and must
not weaken assertions to match the current implementation.

An existing diagnosis-only state takes precedence over the runtime path above:
consume the preserved failure evidence in one bounded static analysis, retain
the version/attempt ledger and original files, and propose a candidate fix
without applying it. Do not execute, instrument, create a new harness or resume
test-related repairs by switching Skills, sessions or commands. If that analysis
already exists, reference it rather than repeat it; new diagnostic work requires
explicit renewed scope and authority. Continue other independent authorized
work. Confirmed acceptance defects and material unknowns still block full FD
readiness, merge and Complete archive.

If structural coupling prevents a suitable fix or test seam, explain the
specific obstacle. Suggest codebase-design for interface questions or
improve-codebase-architecture for scoped structural alternatives; do not
automatically refactor, start agents or create another FD.

## Output and completion

Return symptom/expected behavior, revision and evidence, cause classification,
confirmed root cause or ranked hypotheses, candidate correction, skipped checks,
remaining budget and concrete next action. Link raw evidence instead of copying
large logs. Reuse the current report; standalone use returns in conversation
unless an output file is authorized. For decisive missing inputs use
`%% NEEDS_INPUT: ...` and BLOCKED/INCOMPLETE for the affected conclusion.

Complete the requested diagnostic report when these facts and limits are clear;
do not equate report completion with a reproduced, fixed or verified bug. Review
the evidence chain statically. Any authorized temporary instrumentation belongs
to its implementation owner, who records cleanup within scope; never delete
unknown files or the preserved failure scene to claim completion.
