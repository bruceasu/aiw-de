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
The Control Plane SHALL supply a Plan location, preserving a declared location without filesystem or Git checks, or allocating a Plan temporary directory when omitted. The Worker SHALL decide how to prepare and use it. Git and repository registration SHALL NOT be prerequisites for generic task execution. Repository and version requirements MAY be expressed in task content.

The Worker SHOULD isolate a single repository in a Plan-specific worktree. A multi-repository directory SHALL NOT cause automatic worktree creation for each repository. Concurrent execution against the same location SHALL require independent Git worktrees; otherwise Workers SHALL serialize access. Plans with different directories MAY execute concurrently.

### Requirement: Worker-reported shared handoffs
Before completion, the Worker SHALL retain its code and write a report and manifest under the shared location's `.ai/handoffs/<task-hash>/<attempt-hash>/` directory. Completion SHALL report the manifest path and SHA-256 with an owned active lease. The Control Plane SHALL record the exact producing attempt and forward its immutable reference to dependent tasks without uploading changed source files or reading the Worker filesystem. The consuming Worker SHALL verify the manifest digest and producer identity.

### Requirement: Plan location retention
Workers SHALL preserve files and worktrees across task completion, failure and retry. Unknown process termination SHALL retain exclusion locks. The Control Plane SHALL NOT reset or delete user-declared locations. Only a completed Plan with confirmed stopped execution SHALL permit cleanup of its platform-owned temporary directory; cleanup SHALL preserve execution summaries while local handoff files may be removed.

#### Scenario: Submit repository result
- **WHEN** a Worker completes repository work
- **THEN** it reports the resulting revision and changed files through the task result contract

## Notes

- Worker implementations are external processes and may use different languages and AI runtimes.
- Worker examples and supported runtime configuration are documented with each Worker implementation.

### Requirement: Personal identities are not specialist identities
An enrolled Agent identity SHALL NOT register, claim or start work as a professional lease Worker. The independent Agent action and state-notification protocols use dedicated authority credentials and process sessions. Existing professional Worker lease operations remain on /api/r1/worker-protocol/v1 and retain their original lease authority. Historical personal identities and Grants do not transfer to new Agent identities.
