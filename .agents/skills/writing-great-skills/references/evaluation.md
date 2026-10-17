# Optional Skill Evaluation

Read when the user asks to assess behavior or when a consequential uncertainty
cannot be answered by static review. Reading this reference grants no runtime
permission. Follow the host's execution and delegation rules.

## Choose the evidence needed

- **Structure:** inspect metadata, reference paths, examples, consistency,
  dependencies, and authorization. This does not demonstrate improved results.
- **Behavior:** observe whether representative requests select the intended
  skill and whether execution respects scope and completion. Include plausible
  non-trigger cases when assessing activation.
- **Comparative value:** compare useful outcomes and cost against the old
  version or no skill. State the baseline and the reason for using it.

Choose a few relevant cases that expose the claimed improvement. State inputs,
expected observable outcomes, material failure cases, permitted execution,
and stopping conditions before running. Avoid assertions that merely match
the new prose, headings, or implementation. Use task quality and cost measures
that matter to this skill, not a universal score or fixed run count.

## Execute only if authorized

Separate an evaluation plan from permission to execute it. Use the smallest
approved scope. Model calls, tools, generated artifacts, helper scripts,
network, subagents, and packaging retain their existing authorization limits.
An upstream creator's default evaluation loop does not override local rules.

Use independent runs or human review where needed and available; report
same-session assessment as such. Keep inputs and conditions comparable, and
retain failures as evidence. Repeat only for a relevant change under the
allowed budget. Do not optimize indefinitely or select a flattering metric.

## Report

Record the version, baseline, cases, actual commands/actions, observations,
cost where known, failures, skipped checks, and uncertainty. Distinguish an
observed improvement from an untested hypothesis. If execution is unavailable,
return a static assessment or evaluation plan and mark runtime evidence absent.
