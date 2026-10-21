# Personal secretary

## Purpose

FD-024 separates the personal secretary from the Control Plane. The user may replace its implementation and storage. The bundled reference is a Python process with an owner-bound SQLite database.

## Requirements

### Requirement: Private ownership and replaceable process
Conversation, preferences, memory, reminders, personal schedules, original policy text, policy versions, templates, drafts, model reasoning, decisions and personal outbox SHALL stay in the personal process and private store. The platform SHALL NOT read that store or require its schema or implementation language. A user-submitted business Plan is platform data; a private conversation is not implicitly a task payload.

#### Scenario: Two private identities
- WHEN different owners or Workers use the reference implementation
- THEN each SHALL use a distinct identity-bound data directory, and a foreign private database SHALL be rejected

### Requirement: Independent human and machine authority
Only an authenticated human platform session with CSRF and the current role SHALL bind an owner, enroll/rotate a dedicated personal credential, grant minimal capabilities and explicitly enable execution. Ordinary Worker registration, shared protocol tokens, capability names and actor text SHALL NOT create this authority. Dedicated identities SHALL NOT register or execute as specialist lease Workers.

Each declared personal process boot SHALL begin a disabled session. A new boot or credential invalidates previous generation-bound candidates and permits. The platform boot also invalidates enablement. A custom implementation cannot expand authority by editing its private database or counters. Physical restarts hidden by a custom implementation are not detectable from a reused credential alone.

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
