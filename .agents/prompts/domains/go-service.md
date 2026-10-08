# Go Service

## Inspect First
- `cmd/` entrypoints
- handlers
- services
- storage or client packages
- config packages and call paths

## Keep Stable
- package boundaries and `internal/` ownership
- exported APIs
- `context.Context` flow
- concurrency, retry, timeout, and shutdown behavior
- explicit error handling style

## Validate
- use static package, contract, context, and error-flow review by default
- review changed contracts and failure paths independently against the code
- use one compile-only check for the changed package
- ask before widening beyond that package
