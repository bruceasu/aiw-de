# R5 Codex TypeScript Worker

Official Codex SDK reference worker with per-attempt Git worktrees.

```bash
npm install
npm run build
AIW_REPOSITORY_REF=core AIW_REPOSITORY_ROOT=/src/core npm start
```

### R6 result publication

Workers cannot git push. Leave `AIW_GIT_PUBLISH_REMOTE` unset; code handoff needs a shared local Git repository.
