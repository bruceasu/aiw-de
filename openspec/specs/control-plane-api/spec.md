# Control Plane API

## Requirements

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
