# Data Model and Initial Schema

The current project starts from one Flyway migration: `V1__initial_schema.sql`.
It creates the tables referenced by the current Java runtime and their required
constraints, indexes, and initial records. This SQL file is the source of truth
for the schema.

The baseline is for a new, empty database. It does not upgrade, migrate, or
reset an existing database. Keep the old database untouched and configure the
application to use a new database file or database instance.

## 1. Persistence boundaries

The Control Plane stores task, workflow, Worker, authorization, and execution
state. Workers report changes through the Worker protocol; they do not write
directly to the Control Plane database. Workspace files and Git worktrees live
outside the relational schema.

## 2. Table definitions

Use `src/main/resources/db/migration/V1__initial_schema.sql` for the exact table
columns, foreign keys, indexes, and initial records. There is no separate
historical upgrade chain in the fresh-install baseline.

## 3. Database support

The application supports its configured SQLite or PostgreSQL database. The
same initial schema is applied by Flyway at startup.

## 4. How data moves through the database

The Control Plane owns durable task and workflow state. A Worker receives work
through the protocol and returns status, results, and handoff references. The
Control Plane persists those reports through its application code. Files and
worktrees remain at their declared locations unless a feature explicitly stores
an artifact record in the database.

## 5. Future schema changes

For this fresh schema, keep the initial migration focused on the current
application. Future schema changes should use new Flyway migrations; do not add
old database backfill or reset behavior to startup.
