# Assertion design examples

Use the shared Design method. Select only scenarios relevant to changed risk.

## Exact business rule

Given the confirmed decimal rounding rule in testing-methods.md:

```python
def test_fee_rounds_half_up_at_cent_boundary():
    # Approved R-03: exact 10.05 * 0.10 = 1.005, rounds to 1.01.
    assert fee(Decimal("10.05"), Decimal("0.10")) == Decimal("1.01")
```

This distinguishes truncation for that case. It does not prove every amount
or another rounding policy. Imports and fixtures use the existing test stack.
Using `fee(...)` or its production rounding helper to generate the expected
value would remove the independent oracle.

## Rejection and side effects

For a confirmed ownership rule, submit another user's record ID. Assert the
specified denial and that no prohibited state change occurred through the
agreed observable boundary. A success-path assertion alone cannot detect a
missing ownership check. Do not invent the error code or disclosure policy.

## Persistence and protocols

For create-and-retrieve, use create plus retrieval APIs if that is the contract.
For a data export, inspect the exported file: it is the actual output. A direct
storage assertion is appropriate for a storage contract, but incidental table
layout assertions couple a higher-level test to internals. Call order/count
is appropriate when a retry or protocol contract explicitly specifies it.

## Characterization

Snapshotting current output may protect a refactor, but label it as existing
behavior and state whether correctness was independently checked. Never rename
a copied output into an approved business expectation.

For every key scenario, state a plausible error the assertions distinguish.
Static reasoning about that error is not an executed mutation check.
