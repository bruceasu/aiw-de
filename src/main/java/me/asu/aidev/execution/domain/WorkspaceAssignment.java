package me.asu.aidev.execution.domain;

import me.asu.aidev.work.domain.WorkTask;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/** Immutable workspace identity derived by the Control Plane for one execution attempt. */
public record WorkspaceAssignment(
        String repositoryRef,
        String baseRevision,
        String workspaceKey,
        String branchName) {

    public WorkspaceAssignment {
        Objects.requireNonNull(repositoryRef, "repositoryRef");
        Objects.requireNonNull(workspaceKey, "workspaceKey");
    }

    public static WorkspaceAssignment forTask(WorkTask task, int attemptNo) {
        Objects.requireNonNull(task, "task");
        if (!task.requiresWorkspace()) return null;
        if (attemptNo < 1) throw new IllegalArgumentException("attemptNo must be >= 1");
        String slug = slug(task.id().value());
        String hash = hash16(task.id().value());
        String stem = slug + "-" + hash;
        return new WorkspaceAssignment(
                task.repositoryRef(),
                task.baseRevision() == null ? null : task.baseRevision().toLowerCase(),
                stem + "-a" + attemptNo,
                task.baseRevision() == null ? null : "aidev/" + stem + "/a" + attemptNo);
    }

    private static String slug(String value) {
        String normalized = value.replaceAll("[^A-Za-z0-9_-]+", "-")
                .replaceAll("^-+|-+$", "");
        if (normalized.isBlank()) normalized = "task";
        return normalized.length() <= 40 ? normalized : normalized.substring(0, 40);
    }

    private static String hash16(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 8);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
