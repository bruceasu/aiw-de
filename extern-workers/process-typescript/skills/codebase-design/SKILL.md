---
name: codebase-design
description: Design concrete module responsibilities, interfaces and test seams for an assigned change, using real callers and failure cases.
---

# Codebase Design

Use for an in-scope module or interface decision. Do not turn a routine edit into
an architecture project. This is an added method on the existing Worker: keep
the primary role, frozen task, read-only setting, tools and result format.

## Frame the decision

Start with the required behavior, relevant module, known constraints and the
decision to make. Read the supplied design and representative callers through
permitted sources. Identify existing interfaces, data ownership and failure
handling before proposing a new boundary. If code access is unavailable, label
the design as based on the supplied snapshot and list the missing evidence.

## Design from use

1. Trace one ordinary call and one failure or boundary case. Show inputs,
   outputs, state changes, error ownership and any required call order.
2. Give each responsibility one clear owner. Keep an invariant with the state
   it protects. Separate policy from an external adapter when that hides real
   complexity; do not add layers just to make mocks easy.
3. Compare keeping the current boundary with a focused alternative. For a
   material unresolved choice, sketch a second meaningfully different design
   locally against the same requirements. A trivial choice needs no forced pair.
4. Write a small interface sketch and a caller example for each useful option.
   Include error behavior and side effects, not just class or method names.
5. Check whether the interface hides complexity or pushes rules, sequencing
   and repeated conversions into callers. Ask what callers would need to do if
   the proposed module were deleted. A wrapper with no hidden work may add cost.
6. Identify test seams at observable boundaries. Name the behavior to check,
   the controllable input and expected result. Keep real production contracts;
   do not invent a general framework for a single test.
7. Recommend the smallest design that meets the need. Describe caller changes,
   compatibility, migration order and important risks. Mark assumptions and
   decisions that require confirmation separately from observed code behavior.

## Deliver and finish

In the primary task's existing output fields, provide the decision, evidence
locations, responsibility/interface sketch, representative calls, option trade-offs,
recommendation and remaining gaps. An option is useful only when its effects on
callers, failures and validation are clear. Say which checks were actually run.

Design advice does not authorize implementation, new dependencies, schema/API
changes, tests, extra agents or a new workflow. Make files or code changes only
when the assigned task already permits them. If a broader design is needed,
report its impact through the existing question/gap path; do not silently expand
the task or replace its primary result contract.
