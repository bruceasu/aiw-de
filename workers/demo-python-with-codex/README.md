# R5 Codex Python Worker

Codex Python SDK reference worker with per-attempt Git worktrees.

```bash
python -m venv .venv
. .venv/bin/activate
python -m pip install -r requirements.txt
AIW_REPOSITORY_REF=core \
AIW_REPOSITORY_ROOT=/src/core \
AIW_WORKTREE_ROOT=/src/.aidev-worktrees \
python codex_worker.py
```

The worker automatically advertises `repo:core` and runs Codex inside the Control Plane-assigned attempt worktree.

### R6 result publication

Set `AIW_GIT_PUBLISH_REMOTE=origin` (or another configured Git remote name) to publish repository-task result commits. The worker reports `publicationRef=git:<remote>:<branch>` and fetches compatible upstream publication refs supplied in the claim `inputs[]` before materializing a downstream worktree.
