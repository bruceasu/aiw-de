# Source provenance

The process-worker implementation was derived from the actual frozen R2 source package:

```text
ai-dev-manager-vnext-r2-final.zip
```

The complete R2 Skill package tree was copied into:

```text
workers/process-go/skills/
```

No Skill prose was regenerated or simplified in that directory.

## R2 Java process code inspected

The extraction also inspected the Java-side process preparation/policy classes, including:

- `ArchitectureExecutionSkillPolicy`
- `ArchitectureSkillNativePreparationService`
- `PlanningExecutionSkillPolicy`
- `PlanningSkillNativePreparationService`
- `CodingExecutionSkillPolicy`
- `VerificationExecutionSkillPolicy`
- `CrossRepositoryVerificationSkillNativePreparationService`
- `ReviewExecutionSkillPolicy`
- `CrossRepositoryReviewSkillNativePreparationService`
- `ReleaseReadinessExecutionSkillPolicy`
- `CrossRepositoryReleaseReadinessSkillNativePreparationService`
- requirement protocol/baseline/change-control services

Those Java classes were NOT copied into the worker because their responsibility is authoritative preparation, validation, persistence, lifecycle state, permissions, snapshots and command handling. R9 already keeps those categories in the Control Plane.

The worker carries forward only the executable process semantics:

```text
Protocol Skill
   -> Primary Skill
   -> bounded task instruction
   -> Codex execution
   -> Worker Protocol result/evidence
```

## Exact R2 Skill packages retained

- `engineering-artifact-protocol`
- `software-requirements-analyst`
- `solution-architect`
- `implementation-planner`
- `coding-work-package-executor`
- `software-test-verifier`
- `software-code-reviewer`
- `release-readiness-manager`
- `software-development-orchestrator`

Each package retains its `SKILL.md`, `agents/`, `references/`, scripts/assets where present.

## R9-native roles

R9 additionally needs:

- integration executor
- integration verifier
- repair executor

These reuse the closest R2 primary Skill plus a narrow R9-specific supplemental instruction. They do not invent new lifecycle authority.
