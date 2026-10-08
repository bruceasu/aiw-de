# Decomposition Heuristics

Use these heuristics to turn requirement capabilities into software boundaries.

## First ask what must stay together

Keep responsibilities together when they:

- participate in the same invariant or atomic transaction
- change together frequently
- share the same authoritative data model
- must fail or recover together
- have very low latency/high chattiness between them
- are owned and released by the same small team
- gain little from independent scaling

## Consider separating when there is a strong boundary

Separation is more justified when responsibilities have materially different:

- data ownership or regulatory constraints
- trust/security boundaries
- availability or scaling profiles
- lifecycle/release cadence
- failure isolation requirements
- technology constraints imposed by external integration
- ownership/team boundaries

One signal alone is rarely sufficient.

## Distributed-boundary cost checklist

Before creating an application/service boundary, account for:

- network failure and latency
- partial failure
- serialization/version compatibility
- authentication and service identity
- retry/idempotency behavior
- distributed tracing/observability
- deployment/version coordination
- data consistency and reconciliation
- local developer/test complexity
- operational ownership

If these costs do not buy a requirement-level benefit, prefer an in-process module boundary.

## User does not need to know the systems

When the original user describes one business process spanning unknown systems, infer responsibility boundaries from questions such as:

- Who owns the authoritative state?
- Who is allowed to decide whether the operation is accepted?
- Which existing system must perform an irreversible action?
- Who retries or reconciles a failed downstream action?
- Which party must be independently auditable?
- Which data is local versus externally authoritative?

Use the answers to construct system context. Do not ask the user how many services to build.
