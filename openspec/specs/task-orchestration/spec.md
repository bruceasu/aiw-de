## Purpose

Define how the Control Plane owns work tasks, dependencies, worker leases, and execution recovery.

## Requirements

### Requirement: Prepared immutable execution configuration
Tasks requiring `aiw.execution.config.v1` SHALL become claimable only after registered method, Skill/knowledge versions, exact approved model, required adapter capability, confirmed policy and the deployment accounting mode have been checked. Budget completeness, pricing and balances SHALL block preparation only in enforce mode; record-only is the default. The unique intent version and preparation generation SHALL protect retries. READY, fixed references and business-task materialization SHALL commit atomically. Known preparation failures SHALL retain a reason and explicit recovery action without creating claimable work.

#### Scenario: Same Python Worker executes different methods
- **WHEN** it receives successive compatible tasks with different immutable configurations
- **THEN** each Attempt validates its exact content and capability snapshot, creates its own Agent context and keeps the existing Lease, output and cancellation semantics
- **AND** neither launch role nor a later configuration changes the earlier task's inputs, permissions, model or unresolved resources

#### Scenario: Unsupported or incomplete preparation
- **WHEN** a required backend/command adapter, registered source, permission, or an enforce-mode pricing or budget requirement is unavailable
- **THEN** preparation identifies the gap, no claimable task is created, and it does not silently change runtime, model or authority

### Requirement: Guarded execution and immutable inputs
Tasks using `aiw.execution.guard.v1` SHALL freeze the confirmed policy, configuration digest, Skill references and completed upstream Attempt results. Claim SHALL require matching Python adapter capabilities; unsupported Go/TypeScript or backend capabilities SHALL NOT claim these tasks. Fixed instructions SHALL NOT be changed in place.

#### Scenario: An operation loses its response
- **WHEN** a guarded operation intent has committed and its outcome cannot be confirmed
- **THEN** the original operation remains UNKNOWN or queryable by its stable ID, reservations and resources remain accounted for, and lease expiry SHALL NOT automatically requeue or replay it

### Requirement: Execution budgets and factual completion
The deployment SHALL default to record-only accounting. Missing, zero or exhausted budgets, unknown consumption and changed current pricing SHALL NOT block preparation, claim, task creation, backjumps or normal advancement in this mode. Actual usage, elapsed time, action/task/backjump counts, reservations and unknown data SHALL remain durable; absent pricing for a started Attempt SHALL produce unknown cost, not invented zero. Each Attempt SHALL use its original frozen price. Stable task, Attempt and transition IDs SHALL prevent double accounting.

An explicit enforce configuration SHALL restore complete time/task/cost budgets, pricing, positive per-Attempt reservations, price compatibility, balances and unknown-consumption checks. Its initial execution is separate from at most three plan/loop backjumps unless explicitly incremented. Thresholds SHALL stop new dispatch and advancement only in enforce mode. Manual pause SHALL stop new dispatch and advancement in both modes; already authorized work may finish and record real consumption, which may exceed the threshold. Mode changes SHALL NOT erase history or recompute old fees.

#### Scenario: Unknown historical consumption in record-only mode
- **WHEN** an old settled Attempt has unknown cost and no live task, Lease, Attempt or unknown external operation remains
- **THEN** normal new work and graph completion may proceed, the unknown data remains recorded, and no mandatory budget-increment question is created
- **AND** same-task unknown external effects and actual in-flight resources remain protected against replay or concurrent replacement

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

### Requirement: Versioned graph activation and selected joins
The `aiw.workflow.graph.v1` consumer SHALL freeze each region activation definition, inputs and selected output slots. All potential incoming choices SHALL resolve before selecting a node; only genuinely unselected paths become NOT_SELECTED. A failed or cancelled necessary member SHALL block its dependent path while independent branches may continue. END closes a local path; successful publication requires all necessary selected results and actual quiescence of started work.

#### Scenario: A selected child loops while an independent branch completes
- **WHEN** a declared ancestor loop backjump commits after old work is quiescent
- **THEN** its activation keeps a stable parent output slot, closes the old iteration without success, atomically charges the shared plan/loop ledger and starts a distinct iteration; the parent waits and the independent branch retains its original result
- **AND** only explicit carry imports freeze old completed Task/Attempt/commit evidence into the new iteration; old results do not satisfy new ordinary dependencies

### Requirement: Editable head and immutable execution provenance
The graph edit head SHALL be separate from activation definition versions. A validated patch MAY expand an unactivated subtree while preserving its external control/data/authority interface. Entry and patch SHALL serialize version binding. Completed facts and formal answers SHALL validate their frozen execution provenance rather than equal the latest edit head. Model candidates SHALL pass the same versioned human change boundary before execution.

#### Scenario: A draft recommendation is invalid
- **WHEN** the controlled read-only planning task returns malformed graph data
- **THEN** the candidate and diagnostics remain available for manual editing, and no candidate node executes

### Requirement: Formal graph questions and bounded recovery
Graph ASK and temporary task questions SHALL use the formal-question store and its sole APPLIED consumer. A temporary question SHALL reuse the guarded continuation and atomically transfer the graph slot. Paused facts remain durable without advancement. Known unconsumed technical failures MAY be explicitly retried with exact execution/task/Attempt versions; replacements SHALL preserve prior failures and consumed choices. UNKNOWN SHALL require human reconciliation rather than automatic replay.

#### Scenario: A budget prevents another loop iteration
- **WHEN** enforce mode is active and the initial iteration and three backjumps have consumed the shared loop allowance
- **THEN** the original transition remains blocked, one scoped increment request is retained without granting authority, and a formal approval resumes that same transition without double charging

### Requirement: Selected goals and factual integrated delivery
An explicitly confirmed minimal goal projection MAY be stored as an inert personal proposal. It SHALL NOT create an execution grant, task or active plan. A current human operator SHALL adopt the exact proposal through the existing versioned draft entry atomically with an immutable adoption receipt. A lost proposal response SHALL be queried by its original identity without automatic replay, including after restart or a 404.

#### Scenario: A previously adopted draft has been edited
- **WHEN** the same proposal and destination are adopted again by a currently authorized human
- **THEN** the original plan/version receipt is returned without overwriting the later edit head

#### Scenario: A final graph result has no formal Delivery
- **WHEN** the integrated view reads its frozen ROOT result and exact completed Task/Attempt
- **THEN** it reports missing evidence rather than synthesizing a Delivery or test pass; only actual context/configuration/source and Delivery records may support a recorded-evidence status

- Task state, dependencies, and lease ownership are authoritative in the Control Plane and its persistence layer.
- Scheduling also applies capability matching, priority aging, run fairness, and run, fleet, and worker capacity limits.
- This spec describes current capability; production management API authentication and general skill/tool permission governance are outside its scope.

### Requirement: Platform-owned immutable Skill files
New platform EXTERNAL_WORK tasks SHALL pin a complete declared Skill file set at creation or configuration preparation. Claim SHALL require skill-files-v1 and return only that task's fixed references and bytes with its Task/Attempt identity. Python, TypeScript and Go supported adapters SHALL verify source scope, reference identity, manifest and every file before model execution. Text asset digests SHALL retain text semantics; file-set versions SHALL identify the separate manifest digest.

#### Scenario: A Skill version changes while a task is waiting
- **WHEN** the platform introduces new Skill content
- **THEN** existing tasks, retries, continuations and workspace replacements retain their original file sets; already claimed bindings cannot be replaced

#### Scenario: A local directory has different same-name content
- **WHEN** a platform task carries fixed Skill files
- **THEN** the adapter uses the validated task bytes and rejects corrupt/incomplete content without local/latest fallback; explicitly configured local directories remain available for the separate standalone Profile contract

#### Scenario: A model needs an auxiliary Skill file
- **WHEN** the file is in the task's fixed manifest
- **THEN** a read-only resource tool can return its bounded bytes; undeclared files are denied, scripts remain data, and no additional workspace, shell, network or private knowledge authority is granted

#### Scenario: An adapter lacks the resource reader
- **WHEN** a Worker uses Codex CLI or lacks skill-files-v1
- **THEN** it cannot claim new platform Skill tasks; unsupported dynamic tool contracts fail explicitly rather than falling back to another executor
