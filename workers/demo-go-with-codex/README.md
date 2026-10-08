# R5 Codex Go Worker

Dependency-free Go reference worker. Worker Protocol uses `net/http`; Codex uses installed `codex exec --json`; R5 isolation uses Git worktrees.

```bash
go build -o codex-go-worker .
AIW_REPOSITORY_REF=core \
AIW_REPOSITORY_ROOT=/src/core \
AIW_WORKTREE_ROOT=/src/.aidev-worktrees \
./codex-go-worker
```

The Codex CLI must already be installed/authenticated. `AIW_CODEX_PATH` overrides the executable.

### R6 result publication

Set `AIW_GIT_PUBLISH_REMOTE=origin` (or another configured Git remote name) to publish repository-task result commits. The worker reports `publicationRef=git:<remote>:<branch>` and fetches compatible upstream publication refs supplied in the claim `inputs[]` before materializing a downstream worktree.
