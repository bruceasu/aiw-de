# Finding Severity

Assign severity based on impact and likelihood in the actual system context.

## CRITICAL

Use when exploitation/failure could reasonably cause catastrophic security compromise, irreversible or widespread financial/data corruption, or similarly severe impact and the issue is plausibly reachable.

Always blocking.

## HIGH

Use for likely or material correctness, security, data-integrity, compatibility, or availability failures that can affect important production behavior.

Normally blocking.

## MEDIUM

Use for real defects or engineering risks with limited scope, meaningful edge-case impact, insufficient tests around risky behavior, or maintainability issues likely to cause future defects.

Blocking when the WP/acceptance criteria cannot be considered safely complete.

## LOW

Use for limited-risk maintainability, diagnostics, test clarity, or minor robustness improvements that do not invalidate required behavior.

Normally nonblocking.

## Severity discipline

Do not assign severity from code aesthetics. Explain the concrete impact scenario.
