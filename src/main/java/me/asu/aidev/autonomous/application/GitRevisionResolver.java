package me.asu.aidev.autonomous.application;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

/** Resolves a user supplied Git ref to a full commit ID at Run submission time. */
@Component
public class GitRevisionResolver {
    private static final Duration TIMEOUT = Duration.ofSeconds(15);
    private final WorkspaceRegistry workspaces;

    public GitRevisionResolver(WorkspaceRegistry workspaces) { this.workspaces = workspaces; }

    public Path repositoryPath(String repositoryRef) {
        Path path = workspaces.repositoryPath(repositoryRef);
        if (!Files.isDirectory(path)) throw new IllegalArgumentException("Workspace directory does not exist: " + path);
        return path;
    }

    public String resolve(String repositoryRef, String revision) {
        if (revision == null || revision.isBlank()) throw new IllegalArgumentException("baseRevision is blank");
        Path repository = repositoryPath(repositoryRef);
        Process process;
        try {
            process = new ProcessBuilder("git", "-C", repository.toString(), "rev-parse", "--verify",
                    "--end-of-options", revision + "^{commit}")
                    .redirectErrorStream(true).start();
            if (!process.waitFor(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                throw new IllegalArgumentException("Git revision resolution timed out for repositoryRef " + repositoryRef);
            }
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip();
            if (process.exitValue() != 0 || !output.matches("(?i)([0-9a-f]{40}|[0-9a-f]{64})")) {
                throw new IllegalArgumentException("baseRevision does not resolve to a commit in repositoryRef " + repositoryRef);
            }
            return output.toLowerCase();
        } catch (IOException e) {
            throw new IllegalStateException("cannot execute Git to resolve baseRevision", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while resolving baseRevision", e);
        }
    }
}
