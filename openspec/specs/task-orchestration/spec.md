## Purpose

Define how the Control Plane owns work tasks, dependencies, worker leases, and execution recovery.

## Requirements

### Requirement: Human-readable preparation and optional method composition
The console SHALL offer plan, repository, method, approved-model and owner-scoped upstream choices without requiring hand-written Intent, Assembly or Candidate JSON. Internal preparation identity, versions and generations SHALL be maintained by the UI. Results SHALL explain readiness, blockers and recovery without rendering raw API JSON; READY SHALL mean task creation, not execution success. Created tasks SHALL have an owner-validated exact-task status entry that can be refreshed independently of preparation readiness; a plan-definition link SHALL NOT be presented as that task's execution progress. Default methods SHALL be usable without custom composition.

Read-only UI choices SHALL retain exact immutable configuration references and expose personal assets only as owner/repository-scoped metadata. Recovering an original preparation SHALL preserve its original input. Refreshing choices SHALL NOT silently substitute a new policy, model or configuration. Unchanged retries SHALL use the original input and current generation; edited content SHALL create the next intent version. Unknown write outcomes SHALL be checked before any explicit retry of the same original request, with no automatic identity replacement or replay.

Optional method composition SHALL use form selections and a readable preview, not raw protocol input. Input changes and stale asynchronous preview responses SHALL invalidate the old candidate. Explicit save SHALL submit that preview's exact candidate. A confirmed write SHALL remain reported as successful even when a later list refresh fails. Composition and content registration SHALL NOT invoke models or grant tools or execution permission.

### Requirement: Human-readable formal question inbox and controls
The console SHALL present formal questions as a selectable inbox with readable context, answer requirements, original answers and state, without raw API JSON. Professional answers and exact authorization decisions SHALL keep their distinct roles, original question/answer versions and immutable message identity. Only an unapplied delegated answer MAY offer an explicit human override with a reason. Refresh SHALL preserve page-local drafts and require explicit adoption of changed versions.

The inbox, task/control view and optional notification settings SHALL switch as panels. The control view SHALL distinguish stop requests from actual acknowledgment. Manual budget increment forms SHALL be optional and absent in record-only mode. Account-wide private-chat binding and bounded delivery history SHALL be explained; incomplete history, pending sends and UNKNOWN SHALL prevent another send, with no expiry-based UNKNOWN replay.

Stale responses from another selected plan SHALL be discarded. Writes SHALL freeze duplicate actions and plan switching. Unknown writes SHALL be checked against the original record before explicit same-body retry of an idempotent answer or budget request; controls and notification operations SHALL NOT offer automatic replay. Confirmed writes SHALL remain confirmed if a subsequent refresh fails. Unsafe integer versions or amounts SHALL NOT be silently rounded and submitted.

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

### Requirement: Scoped engineering methods in Worker Skills
Platform Worker Skills SHALL adapt useful planning, implementation, clarification, review, verification and handoff methods to the assigned role. They SHALL preserve frozen inputs, existing result formats and Control Plane lifecycle authority. Host FD commands, delivery, delegation and external-service powers SHALL NOT be inferred from adapted instructions.

For non-trivial implementation, the coding method SHALL guide a small acceptance-linked local work outline and an honest continuation of completed, pending and blocked outcomes. These notes SHALL use existing descriptive fields and SHALL NOT be represented as durable WorkItems, an automatic recovery state machine or independent review. Continuation SHALL preserve original operation identities, unknown effects and consumed execution authority, and identify stale evidence against changed inputs.

#### Scenario: A host method recommends running a test or another agent
- **WHEN** that method is adapted into a platform Skill
- **THEN** the Worker may propose the relevant next action, but execution still requires its existing task/tool authority; a new session or method does not renew an exhausted budget

#### Scenario: A task is interrupted after partial implementation
- **WHEN** the Worker returns a continuation in its assigned report
- **THEN** it identifies actual outputs, unverified or blocked acceptance, original unresolved operations and the next responsible role without claiming task acceptance or automatically replaying effects

#### Scenario: A Skill authoring task has only static evidence
- **WHEN** the assigned task updates Skill content and declared references
- **THEN** the result distinguishes structural checks from unrun model behavior or comparative quality evaluation, and does not infer registration, installation or publication

### Requirement: Composable prototype Skill
The registered prototype Skill SHALL be selectable as an additional Skill on an existing working method. Its files and immutable asset reference SHALL use the existing configuration and Skill-file binding contracts. Selecting it SHALL preserve the primary Skill, Worker role, runtime, output format and current tool authority; it SHALL NOT create a separate Worker type or process.

The Skill SHALL guide actual minimal UI or logic artifact creation, a concrete entry point, relevant interactions and an observation/handoff against a bounded question. Prototype code and conclusions SHALL remain distinguishable from accepted production behavior. Missing write or execution authority SHALL be reported rather than inferred from Skill selection.

#### Scenario: A coding Worker needs to explore a UI choice
- **WHEN** its configured method includes prototype and the task permits that experiment
- **THEN** it can follow the declared UI resource to create appropriately scoped runnable artifacts and report actual checks in its existing result format, without changing roles or granting additional tools

#### Scenario: Prototype files can be written but preview is not authorized
- **WHEN** the Worker has file authority but lacks the required command or preview capability
- **THEN** the method guides file delivery and exact entry instructions with preview marked not run; no server, browser session or permission is synthesized

### Requirement: Composable engineering and communication Skills
The public Skill catalogue SHALL offer codebase-design, improve-codebase-architecture, domain-modeling, diagnosing-bugs, resolving-merge-conflicts, research, handoff, teach and edit-article as optional additions to an existing working method. Each SHALL provide task-relevant inputs, practical steps, completion output and evidence-gap handling in declared Skill files. Registration SHALL use the existing immutable asset and Skill-file contracts, without changing existing assets, default methods or primary profiles.

Selection SHALL preserve primary responsibilities, read-only constraints, runtime, output schema, frozen source scope and tool authority. The existing maximum of six additional Skills SHALL remain. When a requested activity cannot fit the primary task or available tools, the Worker SHALL report the gap through its existing result/question path instead of switching roles or inventing authority.

#### Scenario: An architecture task needs domain clarification and research
- **WHEN** its method selects codebase-design, domain-modeling and research within the current task scope
- **THEN** the Skills guide concrete interfaces, terms and source-backed conclusions in the existing output, using only permitted evidence; selection does not grant external-source access or start another agent

#### Scenario: A conflict-repair task lacks operation evidence or an adapter
- **WHEN** the actual merge/rebase base, both sides' intent, operation state or necessary Git adapter is unavailable
- **THEN** the conflict Skill guides preservation of the current state and a precise gap report, without raw Git-state manipulation, guessed completion or replay of an unknown operation

#### Scenario: A task requests teaching, prose editing or a handoff
- **WHEN** a selected Skill supports that activity within the primary task
- **THEN** it guides a useful lesson with practice, a faithful article revision or an evidence-based continuation brief as applicable; file writes require existing scope, and no course state, publication or workflow dispatch is implied
