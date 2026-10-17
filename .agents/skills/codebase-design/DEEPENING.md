# Deepening a module

Read only for a scoped consolidation or dependency-design decision. Follow
[SKILL.md](SKILL.md); this method proposes design, not refactoring or tests.

## Identify what improves

Trace a real caller and the rule it must know. Compare leaving the current
shape with moving that rule behind an interface. Identify duplicated knowledge,
call ordering, error recovery and changed callers. Consolidation helps only
when it reduces caller/maintenance burden without obscuring required behavior.
Pure computation is not automatically worth merging; a local stand-in alone
does not make consolidation safe. Record migration and ownership costs.

## Dependency strategies

Use the actual dependency, not a prescribed architecture:

- In-process rules may be verified through a public behavior interface, while
  retaining focused rule checks where they detect distinct failures.
- Local I/O may use an existing inspectable stand-in for some scenarios; list
  differences from the real filesystem/database and necessary integration evidence.
- Owned remote systems may justify a port/adapter to isolate transport and
  recovery; an in-memory adapter checks logic, not protocol compatibility.
- External systems may need mocks for bounded scenarios plus independent
  contract/integration evidence for the risks a mock cannot represent.

Choose a seam for an observed variation, isolation, ownership or verification
need. Multiple adapters strengthen the evidence but are not a mandatory count.
Do not introduce an unnecessary second implementation merely to justify a port.
Keep internal seams private unless actual callers need them.

## Preserve behavioral protection

Propose behavior-based checks across the chosen interface, including relevant
error and state transitions. Keep existing unit/integration/contract/end-to-end
checks until their distinct assertions and risks are accounted for. Do not
automatically delete tests after consolidation. Explain any proposed replacement
and the evidence needed before retiring coverage; execution still needs authority.

In diagnosis-only, all of this remains a candidate design: no test edits,
production repair or fresh execution. Return in-scope decisions to the current
FD; propose out-of-scope changes for later without creating another work plan.
