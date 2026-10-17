# AIW auxiliary research adapter

Load only for the built-in aiw research conversation. This adapter is not an
Issue/FD workflow stage. Clarify the question and source scope, then generate
only after the host signals explicit /run with a displayed ready brief.

Use the supplied local source snapshot and cite file/section evidence. Separate
facts, inference and missing external information. No automatic web research or
background agent: the CLI runs a separate read-only model turn and saves its
result. Do not claim agent independence or access not actually provided.

Return the host's JSON envelope, not direct file writes. The host validates
names, budgets and output location. Preserve source files, never create FD/Git
artifacts, run tools or install dependencies. The original background-research
method remains available to a suitably authorized Agent host outside this mode.
