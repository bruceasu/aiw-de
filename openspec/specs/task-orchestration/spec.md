## Purpose

Define how the Control Plane owns work tasks, dependencies, worker leases, and execution recovery.

## Requirements

### Requirement: Guarded execution and immutable inputs
Tasks using `aiw.execution.guard.v1` SHALL freeze the confirmed policy, configuration digest, Skill references and completed upstream Attempt results. Claim SHALL require matching Python adapter capabilities; unsupported Go/TypeScript or backend capabilities SHALL NOT claim these tasks. Fixed instructions SHALL NOT be changed in place.

#### Scenario: An operation loses its response
- **WHEN** a guarded operation intent has committed and its outcome cannot be confirmed
- **THEN** the original operation remains UNKNOWN or queryable by its stable ID, reservations and resources remain accounted for, and lease expiry SHALL NOT automatically requeue or replay it

### Requirement: Execution budgets and factual completion
Required time, task and cost limits and pricing SHALL be explicitly configured before automatic admission. Usage SHALL survive restart, policy replacement and Worker replacement. Initial execution is separate from at most three plan/loop backjumps; stable transition IDs and explicit increments SHALL prevent duplicate consumption or reset. Reaching a threshold or pausing SHALL stop new dispatch and advancement while already authorized work may finish and record real consumption, which may exceed the threshold.

#### Scenario: A task finishes while advancement is paused
- **WHEN** its actual terminal facts are recorded
- **THEN** completion and consumption commit separately from advancement, a durable pending fact remains, and later recovery may apply that fact without rerunning the model or tool

### Requirement: Quiescent yield and continuation lineage
A guarded yield SHALL retain Lease and resources in YIELDING until the trusted adapter confirms all execution and tools are quiescent. Acknowledgment SHALL atomically end the Attempt as YIELDED, move the task to WAITING_INPUT and release resources. Applying a continuation SHALL create one successor and mark the source CONTINUED, a non-success terminal state. Only the real completed result at the end of the lineage SHALL satisfy the logical dependency/output slot.

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
