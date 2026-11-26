---
name: systematic-debugging
description: Investigate an unexpected failure by reproducing it and tracing its cause before changing code.
---

# Systematic Debugging

Capture the observed behavior, expected behavior, environment and smallest known failing input.

Trace the failing value or state backwards across boundaries.

Compare a working path with the failing one, then choose an observation that would disprove the leading hypothesis.

After the cause is supported, make a focused fix and verify the original failure and nearby supported cases within the task budget.

Report missing evidence and untested paths.

Method adapted for AIW from [upstream](https://github.com/obra/superpowers/tree/main/skills/systematic-debugging).
