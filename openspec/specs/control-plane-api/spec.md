# Control Plane API

### Requirement: Mandatory browser console sessions
Every business page under `/ui` and `/ui/**` SHALL require a real signed-in browser session. Only `/ui/login` and the designated public console.css/console.js resources SHALL bypass this gate. The former authentication enable flag SHALL NOT permit anonymous authentication, ADMIN access or operator authority, including when set to false. Login SHALL remain available without an enable switch and SHALL reject blank or unconfigured passwords.

#### Scenario: An anonymous browser opens a role or goal page
- WHEN it requests a console business page with GET or HEAD
- THEN it is redirected to login with the original path, query and application context preserved
- AND successful login returns through the existing session flow rather than an anonymous local-console actor

#### Scenario: A console URL uses a routing alias
- WHEN Spring MVC maps an encoded or matrix-parameter alias to a console page or data API
- THEN the same signed-in-session gate applies using the parsed path segments, without an early pass based on the raw URI
- AND page redirects use an encoded canonical console path with the original query preserved, without turning encoded path characters into URL fragments or separators

#### Scenario: A console data request has no signed-in session
- WHEN a registered console JSON/file API or console write is requested anonymously
- THEN the console gate returns 401 rather than data or a login HTML redirect
- AND authenticated requests retain existing CSRF, role and ownership checks

#### Scenario: A machine operates its lease
- WHEN it calls an existing worker or dedicated personal-machine route
- THEN its original credential, identity, scope and lease checks apply without adding a browser-session requirement
- AND legacy anonymous console records are not reassigned or converted into current human authority

## Requirements

### Requirement: Configuration preparation and capability snapshots
The closed human `/api/r1/authority/execution-configurations/` routes SHALL govern registered declarative assets, candidate proposal/version save, preparation and owned state. Current session, CSRF, owner and operator checks SHALL apply. Worker capability registration SHALL use the machine-token route `/api/r1/worker-protocol/v1/workers/{workerId}/configuration`; ordinary lease claim SHALL atomically persist the selected capability snapshot. The optional `executionConfiguration` payload SHALL preserve the legacy task contract when absent.

#### Scenario: Candidate tries to add an executable tool
- **WHEN** a generated declaration includes unknown fields, unregistered references or tools outside the fixed catalog
- **THEN** it is rejected before materialization; saving a candidate SHALL NOT grant tool or resource access

### Requirement: Separate guarded machine and human routes
Guarded machine operations SHALL use `/api/r1/worker-protocol/v1/tasks/{taskId}` for fixed context reads, begin/finish/receipt, yield prepare/ack and formal delivery. Enabling this protocol SHALL require a configured machine token and exact task/Attempt/Lease ownership. Human policy, input binding, dimension increments, effect/consumption reconciliation and stop confirmation SHALL use the closed `/api/r1/authority/execution-*` route set with human session, CSRF, owner and action-role checks. Request bodies SHALL reject unknown fields and exceed neither their structural nor transport size limits.

#### Scenario: Model text proposes an additional tool
- **WHEN** a Skill, generated configuration or model call requests an unapproved tool or resource
- **THEN** the platform and actual registered adapter SHALL reject it; declaring a template or capability SHALL NOT expand the effective policy

### Requirement: One canonical API namespace
All control-plane JSON and file APIs SHALL use `/api/r1/` as their common
namespace. This includes console-owned data APIs and Worker protocol APIs.
The Worker protocol base SHALL be `/api/r1/worker-protocol/v1`. HTML pages
and redirecting form actions SHALL retain their `/ui/` addresses. Old API
addresses SHALL NOT be registered as compatibility aliases or redirects.

#### Scenario: Console data access after migration
- WHEN a client calls a migrated console API under `/api/r1/`
- THEN its existing human session, CSRF, ownership and action role checks apply
- AND its request and response payload contracts remain unchanged

#### Scenario: First delegate activation
- WHEN an authorized human enables a delegate through the migrated console API
- THEN console authentication protects that call
- AND generic legacy administrative receipts do not block the call itself

#### Scenario: Worker access after migration
- WHEN a Worker uses `/api/r1/worker-protocol/v1` to register or report work
- THEN its existing machine bearer and dedicated identity checks apply
- AND ordinary lease operations do not require a browser session

#### Scenario: Old API address
- WHEN a client calls an old API address
- THEN no old controller mapping or compatibility redirect handles that request

### Requirement: Personal authority route separation
Platform authority APIs SHALL live under /api/r1/authority. Human routes require configured session authentication, CSRF and the current action role. Machine routes use a dedicated credential, claimed process boot and explicit owner/target scope; they SHALL NOT accept a browser cookie or shared lease token instead. Unknown method/path combinations are rejected. Action bodies are bounded to 32 KiB and strict closed DTOs. The human page is /ui/authority. Old secretary/delegation APIs are removed, not aliased.

### Requirement: Owned read-only execution observations
`POST /api/r1/authority/execution-configurations/state?kind=observations` SHALL accept the closed empty request and return `aiw.execution.observations.v1` for the server-resolved current Owner only. Existing session, CSRF and operator checks SHALL apply. Default method and role state behavior SHALL remain compatible. The protected `/ui/configurations?view=observations` view SHALL show readable records and explicit manual refresh without running tasks or dumping raw JSON.

The projection SHALL read at most the latest 100 metered Attempts by Attempt creation time, joining the actual task, selected profile/runtime/model, frozen configuration and formal delivery. A bound configuration SHALL be owned by the current Owner; composed names SHALL come from the verified frozen role package. The projection SHALL NOT write state or return credentials, lease tokens, machine sessions, full instructions, command arguments or full completion summaries. Delivery sources SHALL include their fixed references and total count, with at most 16 listed per Attempt.

#### Scenario: Usage or pricing has not been collected
- WHEN an Attempt has unknown usage, has not started metering or lacks settled time/cost data
- THEN absent values are shown as unknown rather than known zero, cached tokens are described as a subset of input, and no runtime total, quality score or savings ratio is invented

#### Scenario: A manual reconciliation preserves an unknown flag
- WHEN existing stored time or cost is present alongside the original unknown marker
- THEN both the stored value with its price unit and the marker remain visible, without treating the marker as permission to discard the correction

#### Scenario: A read operation is repeated
- WHEN successful READ records within a visible Attempt share the same resource and result digest
- THEN the extra count can be displayed as same-result rereads, without representing all reads, waste, quality or a reason to block execution

#### Scenario: The browser identity or response scope changes
- WHEN a refreshed snapshot has a different Owner or an expired page receives an older response
- THEN the page does not combine those records with the current identity and requires a valid new snapshot; Worker-reported verdicts remain distinct from independent acceptance
