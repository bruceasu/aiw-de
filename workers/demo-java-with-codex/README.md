# R8 Codex Java Worker

Pure JDK 21 reference worker: JDK `HttpClient` + `ProcessBuilder` + Git CLI + `codex exec --json`. No third-party Java runtime dependency.

```bash
javac --release 21 -d out src/main/java/me/asu/aidev/worker/example/CodexJavaWorker.java
AIW_REPOSITORY_REF=core AIW_REPOSITORY_ROOT=/src/core \
java -cp out me.asu.aidev.worker.example.CodexJavaWorker
```

### R6 result publication

Workers cannot git push. Leave `AIW_GIT_PUBLISH_REMOTE` unset; code handoff needs a shared local Git repository.
