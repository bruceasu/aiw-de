# R8 Codex Java Worker

Pure JDK 21 reference worker: JDK `HttpClient` + `ProcessBuilder` + Git CLI + `codex exec --json`. No third-party Java runtime dependency.

```bash
javac --release 21 -d out src/main/java/me/asu/aidev/worker/example/CodexJavaWorker.java
AIW_REPOSITORY_REF=core AIW_REPOSITORY_ROOT=/src/core \
java -cp out me.asu.aidev.worker.example.CodexJavaWorker
```

### R6 result publication

Set `AIW_GIT_PUBLISH_REMOTE=origin` (or another configured Git remote name) to publish repository-task result commits. The worker reports `publicationRef=git:<remote>:<branch>` and fetches compatible upstream publication refs supplied in the claim `inputs[]` before materializing a downstream worktree.
