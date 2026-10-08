# Validation

## Default

Static review is the default and normally the only validation:

- inspect the final diff;
- trace changed types, config, contracts, and call paths;
- check docs and prompt consistency.

After implementation, run one compile-only check. Prefer a `compile*` script
under `scripts/` or at the repository root; otherwise use the narrowest
language-level compiler command. Do not run `build*` scripts, tests,
final-artifact builds, formatters, linters, type checkers, verification
scripts, or smoke commands after an edit.
If that compile-only check finds a source error, correct it and rerun the same
compile-only command once. A second failure must be reported without another
automatic retry.

## Authorization

Executable validation other than compile-only is allowed only when:

- the user explicitly requests it;
- the task is specifically to create or repair tests; or
- static analysis cannot answer a decisive question.

For the last case, ask first. State the exact command, purpose, expected duration,
scope, and any network or permission risk.

## Runtime Budget

When authorized:

1. Run one focused command covering the changed path.
2. Rerun once only after relevant code or environment changed.
3. Ask before widening to package, module, repository, integration, or full
   build scope.
4. Never loop equivalent commands to obtain a passing result.

## Evidence

- Report exact commands run and their result.
- Report tests, builds, or checks intentionally not run.
- Say what remains unverified and why.
- Never imply runtime success from static review alone.
