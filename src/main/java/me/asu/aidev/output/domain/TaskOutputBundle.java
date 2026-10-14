package me.asu.aidev.output.domain;

import java.util.List;

/** File versions are immutable; executionState is read from the authoritative attempt. */
public record TaskOutputBundle(String attemptId, String taskId, String repositoryRef, String baseRevision,
                               String resultRevision, String directory, long createdAtEpochMs,
                               String executionState, String reviewState, List<File> files) {
    public record File(String id, String path, String kind, String sha256, long bytes) {}
}
