# Personal secretary

## Purpose

FD-024 separates the personal secretary from the Control Plane. The user may replace its implementation and storage. The bundled reference is a Python process with an owner-bound SQLite database.

The bundled personal secretary is one replaceable Agent client. Its Control Plane connection SHALL use the generic Agent identity, pairing, authorization and state-change message protocol. Control Plane routes, persisted identity types and audit events SHALL NOT identify an Agent as a secretary, and the Control Plane SHALL NOT call back to the personal service.

## Requirements

### Requirement: Private ownership and replaceable process
Conversation, preferences, memory, reminders, personal schedules, original policy text, policy versions, templates, drafts, model reasoning, decisions and personal outbox SHALL stay in the personal process and private store. The platform SHALL NOT read that store or require its schema or implementation language. A user-submitted business Plan is platform data; a private conversation is not implicitly a task payload.

#### Scenario: Two private identities
- WHEN different owners or Workers use the reference implementation
- THEN each SHALL use a distinct identity-bound data directory, and a foreign private database SHALL be rejected

### Requirement: Independent human and machine authority
For a REMOTE Agent, only an authenticated human platform session with CSRF and the current role SHALL bind an owner, approve the paired identity, grant optional minimal capabilities and explicitly enable its current process. Pairing approval SHALL atomically approve the pending Agent and enable the current process; an exact initial Grant MAY be included. Ordinary Worker registration, shared protocol tokens, capability names and actor text SHALL NOT create this authority.

A LOCAL Agent MAY enroll without an Owner login or pairing invitation only through the dedicated direct-loopback registration endpoint. The Control Plane SHALL bind it to the configured administrator Owner, never an Owner supplied by the client, and SHALL grant only the existing Plan and Task defaults. The endpoint SHALL reject browser-originated and forwarded requests and SHALL not be exposed through the remote mTLS listener. Revoked identities SHALL remain revoked.

Each REMOTE process boot SHALL begin a disabled session and require explicit human enablement. A LOCAL process SHALL become enabled after its authenticated loopback session is confirmed against the configured administrator Owner. A new boot or credential invalidates previous generation-bound candidates and permits; the platform boot invalidates prior enablement until a new session is established. A custom implementation cannot expand authority by editing its private database or counters. Physical restarts hidden by a custom implementation are not detectable from a reused credential alone.

### Requirement: Bounded private scheduling and Plan coordination
Scheduling SHALL freeze the approved Plan version and business spec digest. Missed minutes and changed targets SHALL require explicit reconfirmation; they SHALL NOT catch up automatically. The platform SHALL own clean-draft adoption, manager binding, role separation, fixed task attempts and source consumption. Real specialist results SHALL remain recordable while the manager is offline, but new advancement SHALL require fresh authenticated admission.

#### Scenario: Human takeover before continuation
- WHEN takeover or a target generation change precedes a pending continuation
- THEN the old continuation SHALL be rejected, and restart or heartbeat SHALL NOT return ownership

### Requirement: Private UI boundary
The personal UI SHALL have its own login, session, CSRF and explicit origin. It SHALL NOT import platform sessions, accept permissive CORS, trust localhost by itself or expose private content through a platform proxy. The reference binds loopback by default, validates Host and Origin, uses a bounded one-time pairing code and requires an explicit HTTPS origin for remote access.

### Requirement: No old secretary execution mode
The in-platform secretary, private APIs, schedulers and obsolete specialist profiles SHALL be removed, without aliases, a dual-mode flag or a compatibility bridge. V8/V9 migration history and existing data SHALL remain sealed. Existing private data SHALL block new delegation through a durable inventory Gate; no owner or migration permission is inferred. Real already-running outcomes may still be recorded, but retired queued or claimed private tasks SHALL NOT start.

### Requirement: Offline migration planning only
The tool SHALL read only explicit offline snapshots and exact owner mappings, emit metadata-only plans, seal unknown ownership and mixed snapshots, validate versioned candidates and reject conflicting import batches. Records SHALL remain ARCHIVE_ONLY. Actual export, import, cutover, cleanup and rollback SHALL stay disabled, without a bypass flag. A snapshot inventory SHALL NOT be represented as proof of live reconciliation or backup readiness.

## Evidence limit

The FD workflow requires production compile-only and independent static implementation review by default. No runtime safety, actual migration or release readiness is established by this specification or a compile result.

### Formal question projection

The personal secretary SHALL use the same aiw.questions.v1 question ID and request version as Web Console and explicitly bound Telegram private chat. It SHALL show the selected goal, necessary context and original answer locally before explicit submission. Private chats, policy text, templates and credentials SHALL remain local. A19_ANSWER SHALL preserve the exact grant parameters, proposal digest, original envelope and receipt. Saving an answer SHALL NOT imply progression or extra authority. See docs/FORMAL-QUESTIONS.md.

### Requirement: Human action permission periods
When a human approves and enables a REMOTE Agent process, or a LOCAL Agent completes automatic loopback enrollment and session establishment, the platform SHALL provide owner-wide default action permission for A01–A10 and A19_ANSWER. These defaults SHALL cover the current Owner's existing and future applicable plans and tasks without a human-set expiry or cumulative action count. Target ownership, exact request parameters, process enablement, role authority, per-operation admission, accounting and audit SHALL remain enforced. A previous explicit period for a default action SHALL be retained as replaced history. Approvals, publication, deletion, budget increases, Worker and global configuration SHALL still require explicit human action permission periods.

For actions requiring separate authorization, the human authorization UI SHALL group actions by function and request only an Agent, one action, expiry and cumulative count, with optional applicable scope. ALL SHALL cover the current Owner's existing and future applicable targets and share one hard count. PLAN and exact source TASK scopes SHALL only narrow authority; step-wide or global actions SHALL NOT claim TASK scope. Ordinary Worker scope MAY be explicitly human-bound when unclaimed; machines SHALL NOT first-claim unowned global or Worker targets. Conflicting formal ownership SHALL deny access.

Each Owner/Agent/action SHALL have at most one current period. Explicit new periods SHALL replace prior same-action authority while preserving history. Once migrated, legacy issue paths SHALL NOT provide parallel authority, including after expiry or revocation. Creation retries SHALL retain the original request and detect concurrent replacement.

#### Scenario: Shared last use
- WHEN different applicable targets use the same period
- THEN each accepted original operation SHALL consume once in the business transaction, including required indirect publication authority; rejected/rolled-back operations and receipt replay SHALL NOT consume again
- AND exhaustion SHALL reject new operations while preserving the last already-admitted task's valid permit; later failure, cancellation or legacy budget settlement SHALL NOT refund the parent count

### Requirement: Inert fixed selections and durable recovery
The platform SHALL fix concrete target, business selections, actual state/version and immutable original Envelope under current identity and formal ownership. Preparation SHALL NOT execute actions or consume permission uses. Submission SHALL preserve existing role, restrictions, takeover, generation, unknown-effect and exact request checks. A child capability SHALL NOT admit a different request or outlive its parent.

The reference secretary SHALL persist the original selection/request before preparation, then persist the original Envelope before using the existing durable action/question outbox. Unknown results SHALL retain and reconcile the same request; stale process-bound preparations SHALL NOT be silently rebuilt. Full snapshots and private data SHALL NOT be exposed through preparation. Scoped formal-question reads SHALL NOT expand legacy read_scope; original question projection and source associations SHALL remain exact. Historical receipt reads SHALL require the same Owner and Worker. See docs/ACTION-PERMISSIONS.md.

### Requirement: Secretary-owned SDK execution and independent model connections
The reference secretary SHALL prefer the Python OpenAI Agents SDK for new installations, require an explicit authentication source and model, and preserve existing provider selections. Its model connections SHALL be independent of the Control Plane. ChatGPT OAuth and API Key connections SHALL NOT grant file or platform authority. Codex SHALL remain an explicitly selected fallback without automatic account, runtime or billing-source switching.

The SDK SHALL manage the main model/tool loop while the secretary owns tool implementations, current authorization, finite budgets, canonical history and durable effects. Only completed model responses MAY dispatch tools. Unsupported SDK/OAuth capabilities SHALL fail clearly before effects. The initial OAuth adapter MAY translate completed text actions into SDK function calls instead of claiming support for unverified native tool formats.
SDK final output SHALL be treated only as a result, never dispatched through a legacy text-action loop.

### Requirement: Durable control and honest file and knowledge outcomes
Steering SHALL change the current request at a safe boundary; follow-up requests SHALL remain separate queued inputs. Pause/cancel requests SHALL NOT claim that in-flight effects were undone. Unknown outcomes SHALL be reconciled using original receipts and operation identities before replay. Interrupted tool calls SHALL distinguish not executed, waiting and unknown outcomes.
Chat submissions SHALL carry a durable client submission ID bound to the original content and target before creating a conversation or queueing execution. Replays SHALL return the original record; ID reuse with different content or target SHALL be rejected. Formal asynchronous receipts SHALL synchronize run terminal states without reviving cancelled runs. Follow-ups after failure or cancellation SHALL be marked not applied.

Text edits SHALL preserve the original by default and create revision copies with verified versions and unique exact replacements. Partial reads SHALL NOT authorize whole-document replacement. Original writeback SHALL remain unavailable without a verified platform protection boundary. Backups, diffs and restoration copies SHALL retain version checks, and complex formats SHALL NOT be rewritten as plain text.

Knowledge Base tools SHALL preserve the existing optional remote API contract, source versions, trusted citations, request budgets and note-creation idempotency. Saving SHALL require the current user's explicit intent; accepted, saved and searchable states SHALL be distinct. Private conversations and memory SHALL NOT be automatically uploaded to the Knowledge Base.
Note reconciliation and replay SHALL require the original connection and credential identity. A shared service ID alone SHALL NOT authorize sending a prior principal's note using replacement credentials. Missing identity evidence SHALL block replay.

The reference private-store upgrade SHALL retain existing data, save a restricted pre-upgrade snapshot, and fence older writers out of the new runtime record format. Runtime metadata SHALL be observable without routinely returning full transcripts or credentials. Evidence remains subject to the limits above.
Permanent conversation deletion SHALL remove its private runtime graph and submission records in the same deletion transaction while retaining unrelated work, existing backups and output files under the existing deletion policy.
Both deletion entries SHALL reject nonterminal runs and terminal runs with unresolved effects, rechecking under the deletion transaction. Cancellation SHALL NOT discard recovery evidence. Reconciliation after cancellation SHALL keep the run cancelled and SHALL NOT resubmit a note on a missing remote creation record. Supported desktop chat clients SHALL retain the original submission ID, content and target while the outcome is unknown.

## Agent client transport

### Requirement: Remote Agent client mutual TLS
The personal-secretary client SHALL act as one Agent implementation. For a remote profile it SHALL verify the Control Plane server certificate, present its per-Agent client certificate and private key, reject HTTP downgrade, and use the Control Plane-approved certificate pin through the Agent protocol. Private CA trust may be configured locally without storing CA private keys in the application. Certificate replacement SHALL use the human-approved Control Plane rotation flow. Local profiles SHALL use explicit loopback only.

#### Scenario: Remote pairing
- **WHEN** the client pairs using a remote invitation
- **THEN** it verifies the HTTPS server and presents its configured client certificate before receiving a new Agent credential
