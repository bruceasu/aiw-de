# R5 Codex JavaScript Worker

JavaScript/ESM Codex SDK reference worker with per-attempt Git worktrees.

```bash
npm install
AIW_REPOSITORY_REF=core AIW_REPOSITORY_ROOT=/src/core node worker.mjs
```

### R6 result publication

Set `AIW_GIT_PUBLISH_REMOTE=origin` (or another configured Git remote name) to publish repository-task result commits. The worker reports `publicationRef=git:<remote>:<branch>` and fetches compatible upstream publication refs supplied in the claim `inputs[]` before materializing a downstream worktree.
