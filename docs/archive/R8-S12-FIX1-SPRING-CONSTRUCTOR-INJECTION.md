# R8-S12 Fix1 — Spring Constructor Injection

## Symptom

All Spring integration tests failed while loading `ApplicationContext` immediately after R8-S12 was introduced.

## Root cause

`FleetAdmissionBudgetService` has two constructors: a production constructor and a package-private test constructor that accepts a `Clock`. The production constructor was not marked with `@Autowired`.

For Spring-managed classes with multiple constructors, this project explicitly marks the production constructor with `@Autowired`. R8-S12 accidentally omitted that annotation, so Spring could not select the constructor to use when creating `FleetAdmissionBudgetService`.

## Fix

Add `@Autowired` to the public production constructor of `FleetAdmissionBudgetService`.

No scheduling semantics, database schema, migrations, worker protocol, or admission-control behavior changed.

## Regression guard

A source scan was run over all Spring-managed classes in `src/main/java`: every class with multiple constructors now has an explicitly `@Autowired` constructor.
