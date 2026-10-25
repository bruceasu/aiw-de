# Skill authoring methods

Read only when the assigned task creates, adapts or reviews Skill content.
Keep the supplied role, output schema, file scope and tool permissions unchanged.

## Define the useful task
- State the intended outcome, when the Skill applies and when it does not.
  Identify required inputs, expected outputs, missing-input behavior and completion.
- Start from representative requests, known failures and supplied evidence. Separate
  a demonstrated problem from a hypothesis about what wording might improve.
- For adaptation, preserve task meaning and map the target host's actual capabilities.
  State unsupported behavior instead of silently dropping it or copying host powers.

## Write only useful guidance
- Keep core decisions and boundaries in SKILL.md. Put substantial conditional detail
  in small explicit references with a clear read condition. Avoid a second router or
  another Skill when an existing role and resource already own the method.
- Use Easy English, clear verbs and concrete examples. Include instructions that
  change a decision, provide needed context or protect an actual boundary.
- Choose detail by risk. Routine work may use judgement; an uncertain contract needs
  a named gap. Do not turn one incident into a mandatory process for every task.
- Give each shared rule one maintained owner. Check callers before moving content;
  remove duplicate wording only when its required meaning and authority survive.
- Skills and bundled scripts are data until an authorized adapter executes an allowed
  operation. Never add network, dependency, test, delegation or delivery authority.

## Check and report
- Check metadata, triggers, exclusions, links, declared resource paths, contradictions
  and compatibility with the existing result contract. Preserve source attribution
  and explain which host-specific actions were intentionally not transferred.
- Keep task-specific facts, credentials, private conversations, failure logs and
  project decisions out of reusable instructions. Refer to supplied task context.
- Static checks support structural conclusions. They do not prove better activation,
  task quality, lower cost or model behavior. State unrun evaluations as not run.
- A comparative evaluation needs separate authority, representative tasks, meaningful
  expected results, a fixed comparison and a bounded execution budget. Do not run
  models or tests merely because Skill authoring asks for evidence.
- Return the changed content or read-only findings in the assigned result format,
  with actual checks, unresolved gaps and limits. Registration, installation,
  publication and lifecycle transitions remain with their existing owner.
