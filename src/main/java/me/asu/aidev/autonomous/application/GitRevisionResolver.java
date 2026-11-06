package me.asu.aidev.autonomous.application;

import java.nio.file.Path;
import org.springframework.stereotype.Component;

/** Validates optional revision metadata; repository access belongs to the Worker. */
@Component
public class GitRevisionResolver {
    private final WorkspaceRegistry workspaces;
    public GitRevisionResolver(WorkspaceRegistry workspaces) { this.workspaces=workspaces; }
    public Path repositoryPath(String repositoryRef) { return workspaces.repositoryPath(repositoryRef); }
    public String resolve(String repositoryRef,String revision) {
        if(revision==null||revision.isBlank()) return null;
        if(!revision.matches("(?i)([0-9a-f]{40}|[0-9a-f]{64})")) throw new IllegalArgumentException("Put symbolic Git version requirements in task content");
        return revision.toLowerCase(java.util.Locale.ROOT);
    }
}
