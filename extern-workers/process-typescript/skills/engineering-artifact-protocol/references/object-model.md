# Object Model

## Canonical durable types

| Type | ID prefix | Primary owner | Purpose |
|---|---|---|---|
| `requirement` | `REQ-` | requirements analyst | Functional/non-functional/data/interface/constraint requirement |
| `acceptance_criterion` | `AC-` | requirements analyst | Verifiable acceptance condition |
| `change_request` | `CHANGE-` | orchestrator / requirements | Requested change and impact scope |
| `architecture_decision` | `ADR-` | architect | Architecture decision record |
| `architecture_element` | `ARCH-` | architect | Module/application/interface/data/deployment element when structured tracking is useful |
| `work_package` | `WP-` | implementation planner | Bounded executable unit of engineering work |
| `implementation_evidence` | `IMPL-EVID-` | coding executor | What changed and what local verification was performed |
| `review_finding` | `FINDING-` | code reviewer | Review issue with severity/disposition |
| `gap` | `GAP-` | any agent | Requirement/design/plan/protocol/environment gap |
| `verification_evidence` | `TEST-EVID-` | test verifier | Independent evidence that behavior was verified |
| `approval` | `APPROVAL-` | authorized human/policy actor | Explicit approval/denial for a controlled action or gate |
| `release_candidate` | `RC-` | release manager | Exact candidate composition and readiness state |
| `orchestration_event` | `ORCH-EVT-` | orchestrator | State transition or routing event |

## Baseline types

Baselines are immutable manifests of exact object/document versions.

| Type | Prefix |
|---|---|
| Requirements baseline | `SRS-BL-` |
| Architecture baseline | `ARCH-BL-` |
| Implementation baseline | `IMPL-BL-` |
| Code baseline | `CODE-BL-` |
| Verification baseline | `TEST-BL-` |
| Release baseline | `REL-BL-` |

A baseline must contain exact version references, not "latest" aliases.

## Baseline manifest

A baseline payload should include:

- `scope`: project/increment/release scope
- `items[]`: `{id, version, contentHash?}`
- `supersedes`: prior baseline ID/version when applicable
- `acceptance`: authority, time, and approval reference when accepted

An accepted baseline is immutable. Corrections require a new baseline version or new baseline ID according to repository policy; never rewrite history silently.

## Entity versus document

Treat SRS/architecture/design reports as human-readable projections or packaged documents. The authoritative engineering semantics should be represented by structured entities and baseline manifests whenever autonomous orchestration depends on them.
