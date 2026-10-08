Coding agents are insanely smart for some tasks but lack taste and good judgement in others. They are mortally terrified of errors, often duplicate code, leave dead code behind, or fail to reuse existing working patterns. My initial approach to solving this was an ever-growing CLAUDE.md which eventually got impractically long, and many of the entries didn’t always apply universally and felt like a waste of precious context window. So I created the dev guide (docs/dev_guide/). Agents read a summary on session start and can go deeper into any specific entry when prompted to do so. In my original project the dev guide grew organically, and I plan to extend the same concept to my new projects. Here’s an example of what a dev_guide might include:

|Entry | What it covers |
|------| ---------------|
|No silent fallback values | Config errors fail loudly instead of hiding behind defaults |
|DRY: extract helpers and utilities | Don’t rewrite the same parser or validation logic twice |
|No backwards compatibility | All deployments are test environments, no migration code necessary |
|Structured logging conventions | Uniform log format across all features |
|Embedding handling | Always normalize embeddings at ingestion, never trust raw format from the database  driver |
|Deployment safety | Destructive ops must wait for running tasks to complete before deploying |
|LLM JSON parsing | Always parse with lenient mode and regex fallback, never raw json.loads() |
