## Purpose

Define bounded, human-controlled secretary delegation. Design owner: FD-022;
approved business source: ISSUE-00006. This specification states required
behavior; its presence does not establish implementation or runtime success.

## Requirements

### Requirement: Authenticated, disabled-by-default identity
Only a human-enrolled, authenticated delegate Worker SHALL act for an identified
grant owner. Dedicated identity SHALL be checked separately from action authority.
Ordinary registration, capabilities, actor text and a shared protocol token SHALL
NOT establish a delegate identity. Reserved delegate identities SHALL also be
protected on ordinary Worker protocol writes. Restart, registration and heartbeat
SHALL NOT enable delegation. Credential rotation SHALL invalidate old candidates.

#### Scenario: Ordinary Worker impersonates a delegate
- WHEN a Worker submits a reserved identity without its dedicated credential
- THEN registration, claim and decision submission SHALL be rejected

### Requirement: Explicit versioned authority
Each enabled grant SHALL identify its owner, delegate, stable target, actions,
conditions, limits and expiry. The system SHALL retain the original text and
the confirmed structured version. Natural language and model output SHALL only
produce drafts; ambiguous or unconfirmed fields SHALL NOT grant authority.
All applicable prohibitions SHALL take precedence unless a later human-confirmed
rule explicitly replaces the applicable prohibition version. The server SHALL
record effective times and replacement relationships.

#### Scenario: A later allow does not replace a prohibition
- WHEN an applicable allow lacks an explicit replacement of an applicable deny
- THEN the request SHALL remain for human handling

### Requirement: Current authority and human priority
Before effects, the server SHALL recheck authenticated identity, human enablement,
current owner permissions, unexpired rule versions, target state and remaining
budgets. Human actions and delegate commits SHALL share a defined serialization
boundary. A completed human takeover SHALL invalidate earlier candidates;
reenablement SHALL NOT return ownership automatically. An authorized human may
explicitly return an unresolved request, requiring a fresh candidate and checks.
Already committed decisions SHALL remain truthful historical facts; revocation
SHALL NOT imply rollback of running work or external effects.

#### Scenario: Human revokes before a candidate commits
- WHEN revocation commits before the candidate's effect transaction
- THEN the candidate SHALL have no authority to execute

### Requirement: Durable independent budgets
Execution rounds, technical retries and review-only recoveries SHALL use separate
budgets. A full round includes execution and independent review regardless of
review verdict. History SHALL be shared by the same Plan and step across grants
and Workers. Non-step actions SHALL share history by a server-verified stable
target and action. Copying templates, grants or Plans SHALL NOT reset the same
target's consumption. Human supplements SHALL record increments and preserve
history. Missing reliable consumption evidence SHALL require human handling.

#### Scenario: Replacing a grant after exhaustion
- WHEN a new grant addresses the same exhausted target and action
- THEN the old consumption SHALL still limit execution until an explicit supplement

### Requirement: Complete bounded action adapters
The system SHALL support A01–A17 through fixed business adapters, retaining each
existing business Gate. Execution, review and delegate approval of one result
SHALL use different Workers, including historical participation before promotion.
Model identity equality SHALL NOT by itself violate independence. Delegated model
upgrades SHALL be enforced at actual task start, not only expressed as preferences.
Publication creation and execution, approval and rejection, and deletion scheduling
and undo SHALL be separate authorized subactions.

#### Scenario: Publication creation without execution authority
- WHEN a delegate can create a publication plan but lacks execution authority
- THEN neither ordinary plan confirmation nor a retry SHALL start publication

### Requirement: Compatibility and safe admission
Existing V4 grants SHALL NOT silently gain broader scope, lifetime or budget.
A target with pending or unsettled legacy repair/coordination SHALL NOT enter
new delegation until that work is resolved. History SHALL be retained and imported
as a budget baseline. Once admitted, legacy entry points SHALL NOT bypass the
new target's authority or budget. Secretary-managed Plans SHALL retain bounded
admission, cancellation, frozen input and independent coordination checks.

### Requirement: Truthful pending requests and reusable drafts
Unknown applicability, stale inputs or failed conditions SHALL retain the request,
evidence, suggestion and reason for human handling. They SHALL NOT become business
rejections or pass after a timeout. The grant owner and authorized administrators
SHALL be able to inspect the request, current authority and consumption. Idempotent
replays SHALL NOT repeat effects. Failure evidence SHALL survive a rolled-back
business transaction. Templates SHALL copy rules and provenance, not credentials,
enablement, consumption or decision history; new targets and expiry SHALL require
explicit confirmation. Template edits SHALL NOT silently change active grants.
