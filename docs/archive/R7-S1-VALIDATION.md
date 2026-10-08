# R7-S1 Validation

Local validation available in the generation environment:

```text
Java baseline                              21
R7 approval pure-domain/port compile       PASS
R7 approval main structural compile        PASS
R7 approval in-memory authority harness    PASS
SQLite migrations V1 -> V7                 PASS
approval_request lifecycle SQL smoke       PASS
core domain tables                         5
Flyway migrations                          7
integration test methods (*IT.java)        13
legacy environment-prefix residue          0
Spring-managed final-class scan            PASS
```

The generation environment does not contain a Maven binary, so the authoritative project gate remains:

```bash
mvn clean verify
```

R7-S1 adds no live Codex or live Telegram dependency to the Maven test suite.
