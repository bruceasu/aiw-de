---
name: tdd
description: Test-driven development for frontend, Java, Go, and Python projects. Use when the user wants to build features or fix bugs test-first, mentions "red-green-refactor", or wants integration tests.
---

# Test-Driven Development

Follow `skills/reviewed-skill-contract.md` and `skills/work-management.md`.
TDD is explicit opt-in; `$implement` does not invoke it and tests require
explicit authorization.

Use TDD as a red-green loop. Keep each cycle focused on one seam and one observable behavior.

Before writing the first test, identify the repo's test stack, naming conventions, and package layout. If `CONTEXT.md` exists, read it. For Java, Go, and Python projects, follow the existing framework instead of forcing a new style.

Language cues:

- Java: JUnit or TestNG assertions, fixtures or builders, and `Mockito` only at boundaries.
- Go: `testing`, table-driven tests, `t.Run`, `httptest`, and small hand-written fakes.
- Python: `pytest` or `unittest`, fixtures, `assert`, `pytest.raises`, and `unittest.mock` or `monkeypatch` at boundaries.

## What a good test is

Tests verify behavior through public interfaces, not implementation details. A good test describes what a caller can observe and survives internal refactors.

See [tests.md](tests.md) for examples and [mocking.md](mocking.md) for mocking guidance.

## Seams

A seam is the public boundary you test at. Test only at pre-agreed seams. Before writing a test, name the seam and confirm it with the user.

Ask: "What is the public interface, and which seams should we test?"

## Anti-patterns

- Implementation-coupled: mocks internal collaborators, tests private methods, or verifies through a side channel.
- Tautological: expected value is derived the same way as the implementation.
- Horizontal slicing: writing all tests first, then all implementation. Work in vertical slices instead.

## Rules of the loop

- Red before green.
- One slice at a time.
- Refactoring belongs after the current loop, not inside it.
