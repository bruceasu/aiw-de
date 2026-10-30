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

Workers cannot git push. Leave `AIW_GIT_PUBLISH_REMOTE` unset; code handoff needs a shared local Git repository.
