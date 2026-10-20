## Purpose

Define the external Worker integration boundary for workers that pull and execute Control Plane tasks.

## Requirements

### Requirement: Worker registration and availability
The system SHALL allow a Worker to register its identity, runtime type, capabilities, and maximum concurrency. A Worker SHALL be able to report liveness and transition through active, draining, and offline availability states.

#### Scenario: Register a worker
- **WHEN** a Worker registers with valid identity and capability information
- **THEN** the Control Plane makes it eligible for matching tasks subject to its availability and capacity

### Requirement: Pull-based task assignment
The system SHALL expose Worker Protocol v1 under `/api/r1/worker-protocol/v1`. A Worker SHALL request work by claiming a task, and the Control Plane SHALL return only work compatible with its capabilities and available capacity.

#### Scenario: No compatible task is available
- **WHEN** a Worker claims work and no eligible task is available
- **THEN** the Control Plane returns an empty claim result without assigning unrelated work

### Requirement: Worker execution lifecycle
The system SHALL support task start, heartbeat, completion, and failure operations. Completion and failure SHALL be accepted only from the current lease owner with valid lease authority.

#### Scenario: Complete an assigned task
- **WHEN** the current lease owner submits a completion result
- **THEN** the Control Plane validates the lease and records the result against that execution attempt

### Requirement: Optional shared bearer token
When a Worker Protocol token is configured, the system SHALL require the matching bearer token for Worker Protocol requests. In local mode without a configured token, token enforcement MAY be disabled.

#### Scenario: Token configured
- **WHEN** a request omits or supplies an invalid bearer token while token protection is enabled
- **THEN** the request is rejected before performing the requested protocol operation

### Requirement: Worker-owned repository workspace
For repository work, the Worker SHALL prepare and operate on its local workspace using the assigned immutable base revision and return a result revision with changed-file information. The Control Plane SHALL NOT rely on the Worker machine's local filesystem path as shared state.

#### Scenario: Submit repository result
- **WHEN** a Worker completes repository work
- **THEN** it reports the resulting revision and changed files through the task result contract

## Notes

- Worker implementations are external processes and may use different languages and AI runtimes.
- Worker examples and supported runtime configuration are documented with each Worker implementation.
