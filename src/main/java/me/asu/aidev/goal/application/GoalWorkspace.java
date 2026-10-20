package me.asu.aidev.goal.application;

import java.nio.file.*;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import me.asu.aidev.workspace.application.ManagedWorkspaceService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Creates only this feature's execution workspace after explicit preparation. Never cleans user work. */
@Service
public class GoalWorkspace {
    private final ManagedWorkspaceService workspaces;
    private final Path root;
    public GoalWorkspace(ManagedWorkspaceService workspaces, @Value("${aidev.goals.workspace-root:data/goal-workspaces}") String root) {
        this.workspaces = workspaces; this.root = Path.of(root).toAbsolutePath().normalize();
    }
    public synchronized String ensure(String version) {
        UUID.fromString(version);
        String id = "goal-" + version;
        var existing = workspaces.findTarget(id + "/root");
        if (existing.isPresent()) return existing.get().repositoryRef();
        try {
            Files.createDirectories(root);
            Path realRoot = root.toRealPath();
            Path directory = realRoot.resolve(version).normalize();
            if (!directory.startsWith(realRoot) || directory.equals(realRoot)) throw new IllegalStateException("Invalid managed workspace path");
            // A deterministic empty directory may remain after an earlier transaction failed. Never follow links.
            if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) Files.createDirectory(directory);
            if (Files.isSymbolicLink(directory) || !directory.toRealPath().getParent().equals(realRoot)) throw new IllegalStateException("Unsafe managed workspace directory");
            if (!Files.exists(directory.resolve(".git"), LinkOption.NOFOLLOW_LINKS)) {
                Process git = null;
                try {
                    git = new ProcessBuilder("git", "init", "--", directory.toString()).redirectOutput(ProcessBuilder.Redirect.DISCARD)
                        .redirectError(ProcessBuilder.Redirect.DISCARD).start();
                    if (!git.waitFor(15, TimeUnit.SECONDS) || git.exitValue() != 0) throw new IllegalStateException("Git initialization failed; managed directory is preserved for recovery");
                } catch (java.io.IOException unavailable) { /* Existing DIRECT workspace support works without Git. */ }
                finally { if (git != null && git.isAlive()) { git.descendants().forEach(ProcessHandle::destroyForcibly); git.destroyForcibly(); } }
            }
            workspaces.create(id, "Goal execution " + version, directory.toString(), "ROOT");
            return id + "/root";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); throw new IllegalStateException("Workspace preparation interrupted; directory preserved", e);
        } catch (java.io.IOException e) { throw new IllegalStateException("Cannot prepare managed goal workspace; directory preserved", e); }
    }
}
