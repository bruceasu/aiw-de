# Price source adapters

The standard-library Python modules own provider-specific fetching, page parsing
and conversion to internal price rates. Each module exports `fetch()`,
`parse(content, observed_ms, source_url)` and `prices(observation)`.
`prices` returns rate dictionaries with `model`, `input`, `cached`, `output`
and any known internal schedule fields. Monetary amounts are integer
micro-currency units per million tokens. Missing prices stay missing.

`runner.py` adds IDs, observation-time validity and explicit internal defaults,
then returns `priceRules` alongside the raw public observation and source digest.
Java validates this internal schema and atomically publishes it; Java does not
interpret provider table headings, peak windows or page formats. The generic
catalog retains historical snapshots and fills previously unpriced usage.
Observation time does not establish an earlier historical effective date.

Collection runs in the background after startup and every 24 hours. The
`aidev.model-pricing.enabled` switch disables collection and background backfill.
Failure retains existing prices and reports a reason. Users explicitly choose
retry or repair; no failure automatically creates a repair Worker.

A repair Worker receives the source and failed public page, finds the latest
approved official page and returns a candidate. The administrator reviews the
exact candidate source and a fresh page snapshot, then approves each execution.
The candidate runs under the Plane account, not in an OS sandbox. The approved
run validates internal rates before installation and price publication. A failed
or interrupted candidate needs another explicit approval; it is not retried.

Only the existing allowlisted HTTPS provider hosts may be fetched. The runner
accepts one bounded JSON request and emits one bounded response. The Plane
controls process timeouts, output size and the environment. No model or API
credentials are sent to pricing pages. Offline examples under `tests/fd088`
are test source, not evidence that current live pages have been verified.
