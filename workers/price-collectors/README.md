# Price source adapters

`deepseek.py` is an isolated, standard-library-only collector for the public
DeepSeek pricing HTML. Its `fetch()` owns the source URL, redirects and page
retrieval; `parse(content, observed_ms, source_url)` interprets the page. It
sends no credentials or task data and does not update the price ledger. A
validated installation can update the page path, fetching rules and parser
without recompiling the Plane. Source and redirect URLs must remain on the
approved HTTPS host. Unsupported or ambiguous structure raises
`PriceStructureError`; callers must keep the last validated price on failure.

The returned `aiw.price-observation.v1` contains source provenance, a SHA-256
digest, numeric micro-currency units per million tokens, and explicit peak
conditions. It is an observation of the page, not a price history or invoice.
`accountingReady` is false: publication requires independent evidence of the
effective interval and a covered Chinese public-holiday calendar. An observation
time alone must not be used as a historical effective date. A future publication
step must validate the missing conditions and publish atomically; this module by
itself does not publish prices or install hot updates.

The Plane scheduler invokes the installed OpenAI and DeepSeek collectors asynchronously
after startup and then every 24 hours. Collection is enabled by default; set
`aidev.model-pricing.enabled=false` (or `AIDEV_MODEL_PRICING_ENABLED=false`)
to disable automatic requests. `aidev.model-pricing.python` selects the local
Python executable and `aidev.model-pricing.collector-root` selects the trusted
installed module directory. Separate Plane instances sharing a database use a
short lease and duplicate suppression. A failed collection keeps the previous
observation and does not prevent Plane startup or Worker execution. The
execution observations page shows the collection status. OpenAI's Markdown
adapter preserves table headers, source order and surrounding headings; it does
not infer service tiers or context thresholds. Automatic repair, verified module
installation and publication to the internal price table are not yet implemented.

If the fetched official page no longer matches the parser, the collector reports
the bounded public page, a sanitized structure code and parser version. The
Plane keeps the latest sample per provider and counts repeated matching failures;
the UI marks repair needed only after a previously successful source fails with
the same structure fingerprint at least twice. Ordinary source/network failures
do not increase this count. This identifies repair candidates but does not yet
create a Worker task or install its output.

The runner records the SHA-256 of the exact collector source bytes it executes;
the Plane retains the latest successful public sample. When the successful and
failing observations used the same collector bytes and the installed file still
matches, the Plane freezes the module and both bounded public page samples into
one deduplicated repair-material record. If those identities do not match, no
repair material is prepared. These records are not Worker dispatch or approval.

The repair-material read endpoint requires the exact live Worker task lease and
rechecks that the failure and baseline remain current. A Worker-local helper
checks hashes before creating its temporary input files. A separate bounded
result endpoint accepts only a collector file, a check file and a summary; the
receipt is immutable and does not execute, validate or install either file.
Task creation, connecting these helpers to the Worker loop, independent
validation and trusted installation are still pending.

`runner.py` exposes a single-request local process protocol for a future
coordinator. A collection request uses `aiw.price-collector-request.v1` and a
provider; the response includes the observation and original bounded public
content so the Plane can verify its digest. For offline samples, a separate
`aiw.price-parser-request.v1` supplies `observedMs` and `contentBase64`. Both
return `aiw.price-parser-response.v1`. Only allowlisted module names can be
selected, and rejected input does not echo the page. The caller must enforce a
subprocess timeout, an output size limit and a minimal environment without
credentials. Do not activate Worker-supplied module updates without the
separate validation and trusted installation workflow.

Contract fixtures are in `tests/fd088/test_deepseek_collector.py`. They are
offline examples, not evidence that the live page parser has passed a test.
