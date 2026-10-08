package me.asu.aidev.workspace.application;

import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileVisitResult;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Local Workspace registrations edited in the Web Console. */
@Service
public class ManagedWorkspaceService {
    private static final Duration GIT_TIMEOUT = Duration.ofSeconds(15);
    private static final Duration CLONE_TIMEOUT = Duration.ofMinutes(3);
    private final JdbcTemplate jdbc;

    public ManagedWorkspaceService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public record Workspace(String id, String displayName, String rootPath, String targetMode,
                            boolean enabled, List<Target> targets) {}

    public record Target(String repositoryRef, String workspaceId, String workspaceName,
                         String workspaceRoot, String repositoryPath, String repoId,
                         String relativePath, String gitWarning, String sourceType) {}

    public List<Workspace> list() {
        List<Workspace> rows = jdbc.query("""
                SELECT id,display_name,root_path,target_mode,enabled
                  FROM workspace_registry ORDER BY display_name,id
                """, (rs, row) -> new Workspace(rs.getString("id"), rs.getString("display_name"),
                rs.getString("root_path"), rs.getString("target_mode"), rs.getInt("enabled") == 1,
                List.of()));
        return rows.stream().map(row -> new Workspace(row.id(), row.displayName(), row.rootPath(),
                row.targetMode(), row.enabled(), targetsFor(row))).toList();
    }

    public Workspace get(String workspaceId) {
        Workspace row = workspace(identifier(workspaceId, "workspaceId"));
        return new Workspace(row.id(), row.displayName(), row.rootPath(), row.targetMode(),
                row.enabled(), targetsFor(row));
    }

    public List<Target> targets() {
        return list().stream().filter(Workspace::enabled).flatMap(workspace -> workspace.targets().stream()).toList();
    }

    public Optional<Target> findTarget(String repositoryRef) {
        if (repositoryRef == null) return Optional.empty();
        List<Target> matches = jdbc.query("""
                SELECT r.repository_ref,r.workspace_id,w.display_name,w.root_path,r.repo_id,
                       r.relative_path,r.git_warning
                  FROM workspace_repo_registry r JOIN workspace_registry w ON w.id=r.workspace_id
                 WHERE r.repository_ref=? AND w.enabled=1 AND r.enabled=1
                """, (rs, row) -> target(rs.getString("repository_ref"), rs.getString("workspace_id"),
                rs.getString("display_name"), rs.getString("root_path"), rs.getString("repo_id"),
                rs.getString("relative_path"), rs.getString("git_warning")), repositoryRef);
        if (!matches.isEmpty() && !matches.getFirst().repoId().equals("root")) return matches.stream().findFirst();
        int separator = repositoryRef.indexOf('/');
        if (separator < 1) return Optional.empty();
        String workspaceId = repositoryRef.substring(0, separator);
        List<Workspace> workspaces = jdbc.query("""
                SELECT id,display_name,root_path,target_mode,enabled FROM workspace_registry WHERE id=? AND enabled=1
                """, (rs, row) -> new Workspace(rs.getString("id"), rs.getString("display_name"),
                rs.getString("root_path"), rs.getString("target_mode"), true, List.of()), workspaceId);
        if (workspaces.isEmpty()) return Optional.empty();
        Optional<Target> available = targetsFor(workspaces.getFirst()).stream()
                .filter(target -> target.repositoryRef().equals(repositoryRef)).findFirst();
        if (available.isPresent()) return available;
        if (!matches.isEmpty() && hasUnfinishedRootTask(workspaceId)) return matches.stream().findFirst();
        return Optional.empty();
    }

    @Transactional
    public Workspace create(String id, String displayName, String rootPath) {
        return create(id, displayName, rootPath, "CHILD");
    }

    @Transactional
    public Workspace create(String id, String displayName, String rootPath, boolean rootTarget) {
        return create(id, displayName, rootPath, rootTarget ? "ROOT" : "CHILD");
    }

    @Transactional
    public Workspace create(String id, String displayName, String rootPath, String targetMode) {
        id = identifier(id, "workspaceId");
        if (displayName == null || displayName.isBlank()) throw new IllegalArgumentException("displayName is blank");
        String name = displayName.strip();
        if (name.length() > 160) throw new IllegalArgumentException("displayName is too long");
        String mode = targetMode == null ? "ROOT" : targetMode.strip().toUpperCase();
        if (!mode.equals("ROOT") && !mode.equals("CHILD")) throw new IllegalArgumentException("invalid targetMode");
        Integer existing = jdbc.queryForObject("SELECT COUNT(*) FROM workspace_registry WHERE id=?", Integer.class, id);
        if (existing != null && existing > 0) throw new IllegalArgumentException("Workspace is already registered: " + id);
        Path root = workspaceRoot(rootPath);
        jdbc.update("""
                INSERT INTO workspace_registry(id,display_name,root_path,target_mode,enabled,created_at_epoch_ms)
                VALUES(?,?,?,?,1,?)
                """, id, name, root.toString(), mode, Instant.now().toEpochMilli());
        if (mode.equals("ROOT")) insertTarget(id, name, root, "root", ".");
        return new Workspace(id, name, root.toString(), mode, true, targetsFor(id));
    }

    @Transactional
    public Target addChild(String workspaceId, String repoId, String relativePath) {
        String id = identifier(workspaceId, "workspaceId");
        String child = identifier(repoId, "repoId");
        if (child.equals("root")) throw new IllegalArgumentException("root is reserved for Root mode");
        Workspace workspace = editableWorkspace(id);
        Path root = Path.of(workspace.rootPath());
        if (relativePath == null || relativePath.isBlank()) throw new IllegalArgumentException("relativePath is blank");
        Path relative = Path.of(relativePath.strip()).normalize();
        if (relative.isAbsolute() || relative.getNameCount() != 1 || relative.toString().equals(".")) {
            throw new IllegalArgumentException("child repository path must be one direct directory below the Workspace root");
        }
        Path childPath = root.resolve(relative).normalize();
        if (!childPath.startsWith(root) || childPath.equals(root) || !Files.isDirectory(childPath)) {
            throw new IllegalArgumentException("child repository must be an existing directory inside Workspace root");
        }
        beforeFirstExplicitChild(id);
        return insertTarget(id, workspace.displayName(), root, child, relative.toString());
    }

    /** Appends one directly named child under the Workspace root. */
    @Transactional
    public Target append(String workspaceId, String repoId, String kind, String source) {
        return append(workspaceId, repoId, kind, source, false);
    }

    @Transactional
    public Target append(String workspaceId, String repoId, String kind, String source, boolean initializeLinkedGit) {
        String id = identifier(workspaceId, "workspaceId");
        String child = identifier(repoId, "repoId");
        if (child.equals("root")) throw new IllegalArgumentException("root is reserved for the Workspace root");
        if ((id + "/" + child).length() > 120) throw new IllegalArgumentException("repositoryRef is too long");
        Integer alreadyRegistered = jdbc.queryForObject("""
                SELECT COUNT(*) FROM workspace_repo_registry WHERE repository_ref=?
                """, Integer.class, id + "/" + child);
        if (alreadyRegistered != null && alreadyRegistered > 0) {
            throw new IllegalArgumentException("repository is already registered: " + child);
        }
        Workspace workspace = editableWorkspace(id);
        Path root = Path.of(workspace.rootPath());
        Path destination = root.resolve(child).normalize();
        if (!destination.startsWith(root) || Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("child directory already exists or is outside Workspace root: " + child);
        }
        beforeFirstExplicitChild(id);
        String type = kind == null ? "" : kind.strip().toUpperCase();
        try {
            switch (type) {
                case "LOCAL_LINK" -> {
                    Path existing = existingDirectory(source);
                    linkDirectory(destination, existing);
                }
                case "BLANK" -> Files.createDirectory(destination);
                case "GIT_CLONE" -> cloneIntoWorkspace(source, root, destination);
                default -> throw new IllegalArgumentException("kind must be LOCAL_LINK, GIT_CLONE, or BLANK");
            }
        } catch (IOException e) {
            throw new IllegalStateException("cannot create child directory " + child + ": " + e.getMessage(), e);
        }
        return insertTarget(id, workspace.displayName(), root, child, child,
                !type.equals("LOCAL_LINK") || initializeLinkedGit);
    }

    @Transactional
    public void rename(String workspaceId, String displayName) {
        String id = identifier(workspaceId, "workspaceId");
        if (displayName == null || displayName.isBlank() || displayName.strip().length() > 160) {
            throw new IllegalArgumentException("displayName must contain 1 to 160 characters");
        }
        if (jdbc.update("UPDATE workspace_registry SET display_name=? WHERE id=?",
                displayName.strip(), id) != 1) throw new IllegalArgumentException("workspace not found: " + id);
    }

    @Transactional
    public void updateRoot(String workspaceId, String rootPath) {
        String id = identifier(workspaceId, "workspaceId");
        Path newRoot = existingDirectory(rootPath);
        List<String> current = jdbc.query("SELECT root_path FROM workspace_registry WHERE id=?",
                (rs, row) -> rs.getString("root_path"), id);
        if (current.isEmpty()) throw new IllegalArgumentException("workspace not found: " + id);
        if (newRoot.toString().equals(current.getFirst())) return;
        Integer used = taskCount(id, false);
        if (used != null && used > 0) {
            throw new IllegalStateException("Workspace path cannot change after tasks have used it");
        }
        List<Target> targets = explicitTargetsFor(id);
        for (Target target : targets) {
            Path directory = newRoot.resolve(target.relativePath()).normalize();
            if (!directory.startsWith(newRoot) || !Files.isDirectory(directory)) {
                throw new IllegalArgumentException("new Workspace root is missing target: " + target.relativePath());
            }
        }
        jdbc.update("UPDATE workspace_registry SET root_path=? WHERE id=?", newRoot.toString(), id);
        for (Target target : targets) {
            String warning = initializeGit(newRoot.resolve(target.relativePath()).normalize());
            jdbc.update("UPDATE workspace_repo_registry SET git_warning=? WHERE repository_ref=?",
                    warning, target.repositoryRef());
        }
    }

    @Transactional
    public void setRootTarget(String workspaceId, boolean rootTarget) {
        String id = identifier(workspaceId, "workspaceId");
        Workspace value = editableWorkspace(id);
        if (value.targetMode().equals(rootTarget ? "ROOT" : "CHILD")) return;
        Integer children = jdbc.queryForObject("""
                SELECT COUNT(*) FROM workspace_repo_registry
                 WHERE workspace_id=? AND repo_id<>'root' AND enabled=1
                """, Integer.class, id);
        if (children != null && children > 0) {
            throw new IllegalStateException("remove explicit child registrations before changing root targeting");
        }
        if (taskCount(id, true) > 0) {
            throw new IllegalStateException("Workspace has unfinished tasks");
        }
        if (rootTarget) {
            Integer rootRows = jdbc.queryForObject("""
                    SELECT COUNT(*) FROM workspace_repo_registry WHERE workspace_id=? AND repo_id='root'
                    """, Integer.class, id);
            if (rootRows == null || rootRows == 0) {
                insertTarget(id, value.displayName(), Path.of(value.rootPath()), "root", ".");
            } else {
                jdbc.update("UPDATE workspace_repo_registry SET enabled=1 WHERE workspace_id=? AND repo_id='root'", id);
            }
        }
        jdbc.update("UPDATE workspace_registry SET target_mode=? WHERE id=?", rootTarget ? "ROOT" : "CHILD", id);
    }

    @Transactional
    public void setEnabled(String workspaceId, boolean enabled) {
        String id = identifier(workspaceId, "workspaceId");
        if (!enabled) {
            Integer pending = taskCount(id, true);
            if (pending != null && pending > 0) {
                throw new IllegalStateException("Workspace has unfinished tasks and cannot be disabled");
            }
        }
        if (jdbc.update("UPDATE workspace_registry SET enabled=? WHERE id=?",
                enabled ? 1 : 0, id) != 1) throw new IllegalArgumentException("workspace not found: " + id);
    }

    @Transactional
    public void removeChild(String workspaceId, String repoId) {
        String id = identifier(workspaceId, "workspaceId");
        String child = identifier(repoId, "repoId");
        if (child.equals("root")) throw new IllegalArgumentException("Workspace root cannot be removed separately");
        String ref = id + "/" + child;
        if (explicitTargetsFor(id).stream().noneMatch(value -> value.repositoryRef().equals(ref))) {
            throw new IllegalArgumentException("registered child not found: " + ref);
        }
        Integer active = jdbc.queryForObject("""
                SELECT COUNT(*) FROM work_task WHERE repository_ref=?
                   AND state NOT IN ('COMPLETED','CANCELLED')
                """, Integer.class, ref);
        if (active != null && active > 0) throw new IllegalStateException("child has unfinished tasks");
        if (jdbc.update("DELETE FROM workspace_repo_registry WHERE workspace_id=? AND repo_id=?",
                id, child) != 1) throw new IllegalArgumentException("registered child not found: " + ref);
    }

    @Transactional
    public void delete(String workspaceId) {
        String id = identifier(workspaceId, "workspaceId");
        if (taskCount(id, true) > 0) throw new IllegalStateException("Workspace has unfinished tasks");
        jdbc.update("DELETE FROM workspace_repo_registry WHERE workspace_id=?", id);
        if (jdbc.update("DELETE FROM workspace_registry WHERE id=?", id) != 1) {
            throw new IllegalArgumentException("workspace not found: " + id);
        }
        jdbc.update("DELETE FROM direct_workspace_lock WHERE SUBSTR(repository_ref,1,?)=?",
                id.length() + 1, id + "/");
    }

    private Target insertTarget(String workspaceId, String workspaceName, Path root,
                                String repoId, String relativePath) {
        return insertTarget(workspaceId, workspaceName, root, repoId, relativePath, true);
    }

    private Target insertTarget(String workspaceId, String workspaceName, Path root,
                                String repoId, String relativePath, boolean initialize) {
        String repositoryRef = workspaceId + "/" + repoId;
        if (repositoryRef.length() > 120) throw new IllegalArgumentException("repositoryRef is too long");
        Path directory = root.resolve(relativePath).normalize();
        String warning = initialize ? initializeGit(directory) : gitState(directory);
        jdbc.update("""
                INSERT INTO workspace_repo_registry(
                    repository_ref,workspace_id,repo_id,relative_path,git_warning,enabled)
                VALUES(?,?,?,?,?,1)
                """, repositoryRef, workspaceId, repoId, relativePath, warning);
        return new Target(repositoryRef, workspaceId, workspaceName, root.toString(),
                directory.toString(), repoId, relativePath, warning, "EXPLICIT");
    }

    private List<Target> targetsFor(String workspaceId) {
        Workspace workspace = workspace(workspaceId);
        return targetsFor(workspace);
    }

    private List<Target> targetsFor(Workspace workspace) {
        List<Target> explicit = explicitTargetsFor(workspace.id()).stream()
                .filter(target -> !target.repoId().equals("root")).toList();
        if (!explicit.isEmpty()) return explicit;
        if (workspace.targetMode().equals("ROOT")) return List.of(rootTarget(workspace));
        Path root = Path.of(workspace.rootPath());
        List<Target> implicit = new ArrayList<>();
        if (Files.isDirectory(root)) {
            try (var children = Files.list(root)) {
                children.filter(Files::isDirectory)
                        .filter(child -> !child.getFileName().toString().equalsIgnoreCase(".git"))
                        .filter(child -> !child.getFileName().toString().startsWith(".aiw-clone-"))
                        .sorted()
                        .forEach(child -> implicit.add(implicitTarget(workspace, child)));
            } catch (IOException e) {
                throw new IllegalStateException("cannot list Workspace root: " + root, e);
            }
        }
        Set<String> refs = new HashSet<>();
        for (Target target : implicit) {
            if (!refs.add(target.repositoryRef())) {
                throw new IllegalStateException("implicit repositoryRef collision in Workspace " + workspace.id()
                        + ": " + target.repositoryRef());
            }
        }
        if (!implicit.isEmpty() && !hasUnfinishedRootTask(workspace.id())) return List.copyOf(implicit);
        return List.of(rootTarget(workspace));
    }

    private Workspace workspace(String id) {
        return jdbc.query("""
                SELECT id,display_name,root_path,target_mode,enabled FROM workspace_registry WHERE id=?
                """, (rs, row) -> new Workspace(rs.getString("id"), rs.getString("display_name"),
                rs.getString("root_path"), rs.getString("target_mode"), rs.getInt("enabled") == 1,
                List.of()), id).stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("workspace not found: " + id));
    }

    private Workspace editableWorkspace(String id) {
        Workspace value = workspace(id);
        if (!value.enabled()) throw new IllegalArgumentException("workspace is disabled: " + id);
        return value;
    }

    private void beforeFirstExplicitChild(String id) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM workspace_repo_registry
                 WHERE workspace_id=? AND repo_id<>'root' AND enabled=1
                """, Integer.class, id);
        if (count != null && count == 0 && taskCount(id, true) > 0) {
            throw new IllegalStateException("finish or cancel existing Workspace tasks before switching to explicit children");
        }
    }

    private int taskCount(String id, boolean unfinishedOnly) {
        String sql = "SELECT COUNT(*) FROM work_task WHERE SUBSTR(repository_ref,1,?)=?"
                + (unfinishedOnly ? " AND state NOT IN ('COMPLETED','CANCELLED')" : "");
        Integer count = jdbc.queryForObject(sql, Integer.class, id.length() + 1, id + "/");
        return count == null ? 0 : count;
    }

    private boolean hasUnfinishedRootTask(String id) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM work_task WHERE repository_ref=?
                   AND state NOT IN ('COMPLETED','CANCELLED')
                """, Integer.class, id + "/root");
        return count != null && count > 0;
    }

    private Target rootTarget(Workspace workspace) {
        return explicitTargetsFor(workspace.id()).stream()
                .filter(target -> target.repoId().equals("root"))
                .findFirst().map(target -> new Target(target.repositoryRef(), target.workspaceId(),
                        target.workspaceName(), target.workspaceRoot(), target.repositoryPath(),
                        "root", ".", target.gitWarning(), "ROOT"))
                .orElseGet(() -> new Target(workspace.id() + "/root", workspace.id(),
                        workspace.displayName(), workspace.rootPath(), workspace.rootPath(),
                        "root", ".", gitState(Path.of(workspace.rootPath())), "ROOT"));
    }

    private static Target implicitTarget(Workspace workspace, Path child) {
        String name = child.getFileName().toString();
        String repoId = name.matches("[A-Za-z0-9._-]{1,60}") && !name.equals("root")
                && workspace.id().length() + 1 + name.length() <= 120
                ? name : "auto-" + stableHash(name);
        return new Target(workspace.id() + "/" + repoId, workspace.id(), workspace.displayName(),
                workspace.rootPath(), child.toAbsolutePath().normalize().toString(), repoId,
                name, gitState(child), "IMPLICIT");
    }

    private static String gitState(Path directory) {
        return Files.exists(directory.resolve(".git")) ? null : "Git is not initialized; direct directory runs are available";
    }

    private static String stableHash(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes, 0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static void linkDirectory(Path destination, Path source) throws IOException {
        try {
            Files.createSymbolicLink(destination, source);
            return;
        } catch (IOException | UnsupportedOperationException | SecurityException linkFailure) {
            if (!System.getProperty("os.name", "").toLowerCase().contains("windows")
                    || Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("symbolic link failed: " + linkFailure.getMessage(), linkFailure);
            }
            // Windows junctions do not require the symbolic-link privilege. Paths travel as
            // environment values, so they cannot be interpreted as PowerShell code.
            ProcessBuilder builder = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive",
                    "-Command", "New-Item -ItemType Junction -Path $env:AIW_LINK_DESTINATION "
                            + "-Target $env:AIW_LINK_SOURCE -ErrorAction Stop | Out-Null");
            builder.environment().put("AIW_LINK_DESTINATION", destination.toString());
            builder.environment().put("AIW_LINK_SOURCE", source.toString());
            try {
                CommandResult result = runCommand(builder, GIT_TIMEOUT);
                if (result.timedOut() || result.exitCode() != 0 || !Files.isDirectory(destination)) {
                    throw new IOException("symbolic link failed: " + linkFailure.getMessage()
                            + "; Windows junction failed: " + result.detail());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Windows junction creation was interrupted", e);
            }
        }
    }

    private static void cloneIntoWorkspace(String source, Path root, Path destination) throws IOException {
        Path staging = Files.createTempDirectory(root, ".aiw-clone-");
        try {
            cloneRepository(source, staging);
            Files.move(staging, destination);
        } catch (IOException | RuntimeException failure) {
            try {
                removeOwnedClone(staging, root);
            } catch (IOException cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
    }

    private static void removeOwnedClone(Path staging, Path root) throws IOException {
        Path parent = root.toAbsolutePath().normalize();
        Path target = staging.toAbsolutePath().normalize();
        if (!parent.equals(target.getParent()) || !target.getFileName().toString().startsWith(".aiw-clone-")) {
            throw new IOException("clone cleanup target is outside the Workspace root");
        }
        if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) return;
        Files.walkFileTree(target, new SimpleFileVisitor<>() {
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult postVisitDirectory(Path directory, IOException error) throws IOException {
                if (error != null) throw error;
                Files.delete(directory);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void cloneRepository(String source, Path destination) {
        if (source == null || source.isBlank()) throw new IllegalArgumentException("Git source is blank");
        try {
            CommandResult result = runCommand(
                    new ProcessBuilder("git", "clone", "--", source.strip(), destination.toString()), CLONE_TIMEOUT);
            if (result.timedOut()) {
                throw new IllegalStateException("Git clone timed out; directory was not registered");
            }
            if (result.exitCode() != 0) {
                throw new IllegalStateException("Git clone failed with exit code " + result.exitCode()
                        + "; directory was not registered");
            }
        } catch (IOException e) {
            throw new IllegalStateException("Git clone could not start; directory was not registered", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Git clone interrupted; directory was not registered", e);
        }
    }

    private record CommandResult(int exitCode, boolean timedOut, String detail) {}

    private static CommandResult runCommand(ProcessBuilder builder, Duration timeout)
            throws IOException, InterruptedException {
        builder.redirectErrorStream(true);
        Process process = builder.start();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Thread reader = Thread.ofVirtual().start(() -> {
            try (InputStream input = process.getInputStream()) {
                byte[] buffer = new byte[1024];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    synchronized (output) {
                        int remaining = 4096 - output.size();
                        if (remaining > 0) output.write(buffer, 0, Math.min(count, remaining));
                    }
                }
            } catch (IOException ignored) {
                // The process may close its stream while being stopped after a timeout.
            }
        });
        boolean completed;
        try {
            completed = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!completed) process.destroyForcibly();
            reader.join(1000);
        } catch (InterruptedException e) {
            process.destroyForcibly();
            throw e;
        }
        String detail;
        synchronized (output) {
            detail = output.toString(StandardCharsets.UTF_8).strip();
        }
        if (detail.isEmpty()) detail = completed ? "exit code " + process.exitValue() : "timeout";
        return new CommandResult(completed ? process.exitValue() : -1, !completed, detail);
    }

    private List<Target> explicitTargetsFor(String workspaceId) {
        return jdbc.query("""
                SELECT r.repository_ref,r.workspace_id,w.display_name,w.root_path,r.repo_id,
                       r.relative_path,r.git_warning
                  FROM workspace_repo_registry r JOIN workspace_registry w ON w.id=r.workspace_id
                 WHERE r.workspace_id=? AND r.enabled=1 ORDER BY r.repo_id
                """, (rs, row) -> target(rs.getString("repository_ref"), rs.getString("workspace_id"),
                rs.getString("display_name"), rs.getString("root_path"), rs.getString("repo_id"),
                rs.getString("relative_path"), rs.getString("git_warning")), workspaceId);
    }

    private static Target target(String ref, String workspaceId, String name, String rootPath,
                                 String repoId, String relativePath, String warning) {
        Path root = Path.of(rootPath).toAbsolutePath().normalize();
        return new Target(ref, workspaceId, name, root.toString(),
                root.resolve(relativePath).normalize().toString(), repoId, relativePath, warning, "EXPLICIT");
    }

    private static String identifier(String value, String name) {
        if (value == null || !value.strip().matches("[A-Za-z0-9._-]{1,60}")) {
            throw new IllegalArgumentException(name + " must match [A-Za-z0-9._-]{1,60}");
        }
        return value.strip();
    }

    private static Path existingDirectory(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("rootPath is blank");
        Path path = Path.of(value.strip()).toAbsolutePath().normalize();
        if (!Files.isDirectory(path)) throw new IllegalArgumentException("Workspace root must be an existing directory: " + path);
        return path;
    }

    private static Path workspaceRoot(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("rootPath is blank");
        Path path = Path.of(value.strip()).toAbsolutePath().normalize();
        if (Files.isDirectory(path)) return path;
        if (Files.exists(path, LinkOption.NOFOLLOW_LINKS) || path.getParent() == null
                || !Files.isDirectory(path.getParent())) {
            throw new IllegalArgumentException("Workspace root must be a directory with an existing parent: " + path);
        }
        try {
            Files.createDirectory(path);
        } catch (FileAlreadyExistsException e) {
            if (!Files.isDirectory(path)) throw new IllegalArgumentException("Workspace root is not a directory: " + path, e);
        } catch (IOException e) {
            throw new IllegalStateException("cannot create Workspace root: " + path, e);
        }
        return path;
    }

    private static String initializeGit(Path directory) {
        if (Files.exists(directory.resolve(".git"))) return null;
        try {
            Process process = new ProcessBuilder("git", "-C", directory.toString(), "init")
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD).start();
            if (!process.waitFor(GIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                return "git init timed out; directory registration succeeded";
            }
            return process.exitValue() == 0 ? null : "git init failed; directory registration succeeded";
        } catch (IOException e) {
            return "git is unavailable or cannot initialize this directory; registration succeeded";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "git init was interrupted; directory registration succeeded";
        }
    }
}
