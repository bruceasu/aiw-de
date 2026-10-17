# Replacement dependencies

Choose replacements relative to the rule under test, not ownership of code.
Use fakes or existing boundary adapters when they keep the rule real and the
test deterministic. Do not add a mocking framework by default.

- Business payment logic can replace an external payment client. Testing the
  payment adapter itself must exercise that adapter rather than replace it.
- Replace time/randomness when exact control matters. Avoid global state leaks;
  restore fixtures and use assigned temporary paths.
- An in-memory database cannot establish real database transaction, locking or
  constraint behavior. State the limit instead of claiming integration proof.
- File content, persisted outcomes and call order/count may be required contract
  assertions. Avoid incidental implementation structure.
- Internal collaborators can be replaced at a justified module boundary; never
  replace the calculation, permission check or transition being tested.

Prefer existing injection points. Changing public interfaces, persistence or
concurrency for testability needs the normal design/authorization process.

Use native conventions: Java JUnit/TestNG and existing Mockito boundaries; Go
hand-written fakes/httptest only when allowed; Python pytest/unittest fixtures
and scoped monkeypatches. Repository restrictions override these examples.

Record which dependency was replaced, why, which behavior remained real and
which claims the replacement cannot support. Verify fixture setup and cleanup
before authorizing execution; avoid secrets and live external services.
