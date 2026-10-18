## Purpose

Define how the Control Plane owns work tasks, dependencies, worker leases, and execution recovery.

## Requirements

### Requirement: Dependency-aware task readiness
The system SHALL keep a task unavailable for claim until its declared dependencies are complete. A task with no unresolved dependencies MAY become ready when its workflow or operator creates it.

#### Scenario: Dependency completes
- **WHEN** the final unresolved dependency of a task completes
- **THEN** the task becomes eligible for scheduling according to its state and required capabilities

### Requirement: Exclusive task claim
The system SHALL grant a task to at most one current worker lease. Claiming SHALL atomically establish task ownership and a lease token before returning the assignment.

#### Scenario: Concurrent workers claim the same task
- **WHEN** multiple eligible workers attempt to claim one ready task concurrently
- **THEN** no more than one worker receives the task as its active assignment

### Requirement: Lease-scoped execution authority
The system SHALL require the current worker identity and lease token for execution lifecycle operations. A worker SHALL heartbeat its lease while it owns active work.

#### Scenario: Stale lease operation
- **WHEN** a worker submits an operation using an expired or superseded lease
- **THEN** the Control Plane rejects the operation and preserves the current task owner and state

### Requirement: Expired lease recovery
The system SHALL detect expired leases and make interrupted work eligible for recovery according to task retry and attempt policy. The system SHALL retain execution attempt history.

#### Scenario: Worker disappears
- **WHEN** a worker stops renewing its lease and the lease expires
- **THEN** the Control Plane records the interrupted attempt and recovers the task for a later eligible claim

### Requirement: Operator run controls
The system SHALL support pausing, resuming, recovering, and cancelling an autonomous run. Lowering a runtime concurrency limit SHALL NOT revoke already active leases.

#### Scenario: Pause an active run
- **WHEN** an operator pauses a run
- **THEN** the run stops admitting additional work while already active leases remain subject to their normal completion or expiry handling

## Notes

- Task state, dependencies, and lease ownership are authoritative in the Control Plane and its persistence layer.
- Scheduling also applies capability matching, priority aging, run fairness, and run, fleet, and worker capacity limits.
- This spec describes current capability; production management API authentication and general skill/tool permission governance are outside its scope.
