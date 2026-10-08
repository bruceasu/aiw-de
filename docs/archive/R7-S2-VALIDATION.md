# R7-S2 Validation

Target gate:

```bash
mvn clean verify
```

Expected integration-test methods: **14**.

Additional implementation checks performed while building this candidate:

```text
JDK 21 Telegram adapter structural compile  PASS
legacy environment-prefix residue          0
new database tables                         0
new Flyway migrations                       0
core table count                            5
Flyway latest                               V7
```

`R7S2TelegramApprovalIT` talks only to an in-process mock Bot API server. It must never require a real Telegram bot token or Internet access during Maven verification.
