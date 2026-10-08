# Go CLI

## Inspect First
- command tree under `cmd/`
- flag and subcommand wiring
- business logic behind commands
- command behavior and failure paths

## Keep Stable
- flags and subcommands
- exit codes
- printed output
- command package layout
- explicit error handling

## Validate
- use static command wiring, exit code, output, and error-flow review by default
- review behavior and failure paths independently against the changed code
- use one compile-only check for the changed command package
- ask before widening beyond that package
