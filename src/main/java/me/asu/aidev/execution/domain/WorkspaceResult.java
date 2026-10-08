package me.asu.aidev.execution.domain;

import java.util.List;
import java.util.Objects;
import java.util.TreeSet;

/** Git result produced inside an assigned isolated workspace. */
public record WorkspaceResult(String resultRevision, List<String> changedFiles, String publicationRef) {
    public WorkspaceResult {
        Objects.requireNonNull(resultRevision, "resultRevision");
        resultRevision = resultRevision.strip().toLowerCase();
        if (!resultRevision.matches("(?i)([0-9a-f]{40}|[0-9a-f]{64})")) {
            throw new IllegalArgumentException("resultRevision must be a full Git commit id");
        }
        var normalized = new TreeSet<String>();
        if (changedFiles != null) {
            for (String file : changedFiles) {
                if (file == null || file.isBlank()) continue;
                String value = file.replace('\\', '/').strip();
                if (value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0 || value.indexOf(0) >= 0) {
                    throw new IllegalArgumentException("changed file contains unsupported control character");
                }
                if (value.startsWith("/") || value.equals("..") || value.startsWith("../") || value.contains("/../")) {
                    throw new IllegalArgumentException("changed file must be repository-relative: " + file);
                }
                normalized.add(value);
            }
        }
        changedFiles = List.copyOf(normalized);
        publicationRef = publicationRef == null || publicationRef.isBlank() ? null : publicationRef.strip();
    }

    public WorkspaceResult(String resultRevision, List<String> changedFiles) {
        this(resultRevision, changedFiles, null);
    }
}
