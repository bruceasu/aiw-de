# Execution Report Format

Use this structure for the final Work Package execution evidence.

## WP execution

**WP ID:** `WP-...`  
**Outcome:** one of the defined completion states  
**Code baseline / branch / revision:** if known  

### 1. Objective delivered

State the implemented externally meaningful behavior in 1-3 paragraphs.

### 2. Traceability

| Type | IDs | Coverage |
|---|---|---|
| Requirements | FR/NFR/... | implemented / not applicable / blocked |
| Acceptance criteria | AC/... | verified / unverified / failed |
| Architecture | MOD/APP/INT/DATA/... | conformed / affected |
| ADR | ADR/... | conformed / gap found |

### 3. Change set

List affected repositories, modules, components, files, schemas, or configuration areas. Do not dump every trivial file when a concise component summary is clearer.

### 4. Implementation notes

Record only decisions that help review the change. Distinguish local implementation choices from architecture decisions.

### 5. Verification evidence

For every command/check actually run, record:

- command or check name;
- scope;
- result;
- relevant failure summary if not successful.

Do not state that an unexecuted test passed.

### 6. Acceptance coverage

For each AC, explain the test/evidence that verifies it. If not verified, mark `UNVERIFIED` and explain why.

### 7. Deviations

State `None` when there are no deviations. Otherwise list each deviation from WP scope, architecture, contracts, or expected verification.

### 8. Findings / blockers

Use a classified item such as:

`GAP-### | DESIGN_GAP | affects WP-014, ADR-007 | retry ownership undefined`

or

`FINDING-### | INCIDENTAL_DEFECT | outside WP | null handling in legacy importer`

### 9. Review focus

Point the reviewer to the highest-risk logic, compatibility concerns, concurrency behavior, migration behavior, security-sensitive code, or unverified assumptions.
