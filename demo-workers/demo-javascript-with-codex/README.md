# R5 Codex JavaScript Worker

JavaScript/ESM Codex SDK reference worker with per-attempt Git worktrees.

```bash
npm install
AIW_REPOSITORY_REF=core AIW_REPOSITORY_ROOT=/src/core node worker.mjs
```

### R6 result publication

Workers cannot git push. Leave `AIW_GIT_PUBLISH_REMOTE` unset; code handoff needs a shared local Git repository.
