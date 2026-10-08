# Migration and Rollback

## Migration review

For persistent or externally visible changes, determine:

- forward/backward compatibility window;
- deployment order;
- whether old and new application versions can coexist;
- lock/downtime/resource impact;
- backfill requirements;
- validation/reconciliation method;
- what happens after partial completion.

## Rollback realism

A rollback is not valid merely because the binary can be redeployed.

Consider:

- schema changes that old code cannot read;
- external side effects that cannot be undone;
- events/messages already emitted;
- caches or derived state;
- asynchronous jobs already started;
- irreversible transformations.

When rollback is unsafe, define forward-fix, containment, or reconciliation instead.
