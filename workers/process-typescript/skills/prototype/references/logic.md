# Logic prototype

Read for a question about state, business rules, API shape or data behavior.
Keep the prototype Skill's task and permission boundaries and the primary output.

## Expose the question
- State the rule or uncertainty being explored and the expected observable result.
  Distinguish an accepted rule from a proposed rule that still needs a decision.
- List a small set of actions and meaningful cases: a normal path, a relevant edge
  and an invalid transition. Derive expectations from supplied rules, not from the
  prototype's own output. Do not invent a business policy to make the demo work.

## Make a small runnable model
- Use the existing project language or an already available standard runtime. Avoid
  new dependencies or a new framework for a small state experiment.
- Keep state and rules behind a small pure function, reducer or state-machine
  interface. Keep terminal input, rendering, files and other effects outside it.
- Build a thin interactive shell with current state, available actions and clear
  feedback. After each action show the resulting state and any reason for refusal.
  Offer a reset action and a clear exit. A minimal text menu is enough.
- Use memory and sample data. A persistence experiment needs its own declared
  scratch target and cleanup policy; do not connect to a live database by default.
- Provide one entry command or existing task-runner target. Do not modify unrelated
  project scripts, launch a process or install a dependency without task authority.

## Observe and hand off
- If execution is authorized, drive the selected cases through the available
  controlled command/preview tool. Record inputs, observed states and differences
  from expected behavior. Otherwise mark the demonstration as not run.
- Explain what the experiment answered, the counterexample it exposed and what is
  still uncertain. A few manual actions are not a full test suite or a proof of
  concurrency, performance, security or production correctness.
- Return source and entry paths, interaction instructions, reset/exit behavior,
  actual checks and a bounded recommendation in the primary report. Keep any
  reusable rule separate from the disposable shell; promotion is a later decision.
