package me.asu.aidev.autonomous.application;

import me.asu.aidev.workspace.application.ManagedWorkspaceService;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Local single-machine Workspace definitions, grouped by Workspace and repository ref. */
@Component
@ConfigurationProperties(prefix = "aidev")
public class WorkspaceRegistry {
    private Map<String, Workspace> workspaces = new LinkedHashMap<>();
    private ManagedWorkspaceService managed;

    @Autowired
    public void setManagedWorkspaceService(ManagedWorkspaceService managed) { this.managed = managed; }

    public Map<String, Workspace> getWorkspaces() { return workspaces; }
    public void setWorkspaces(Map<String, Workspace> workspaces) {
        this.workspaces = workspaces == null ? new LinkedHashMap<>() : new LinkedHashMap<>(workspaces);
    }

    public Path repositoryPath(String repositoryRef) {
        return location(repositoryRef).repositoryPath();
    }

    public record Location(String workspaceId, String workspaceName, Path workspaceRoot,
                           Path repositoryPath, boolean managed) {}

    public Location location(String repositoryRef) {
        Location managedLocation = null;
        if (managed != null) {
            var target = managed.findTarget(repositoryRef);
            if (target.isPresent()) {
                var value = target.orElseThrow();
                managedLocation = new Location(value.workspaceId(), value.workspaceName(),
                        Path.of(value.workspaceRoot()).toAbsolutePath().normalize(),
                        Path.of(value.repositoryPath()).toAbsolutePath().normalize(), true);
            }
        }
        Path match = null;
        String workspaceId = null;
        for (var entry : workspaces.entrySet()) {
            var workspace = entry.getValue();
            Path candidate = workspace.getRepositories().get(repositoryRef);
            if (candidate == null) continue;
            candidate = candidate.toAbsolutePath().normalize();
            if (match != null && !match.equals(candidate)) {
                throw new IllegalArgumentException("repositoryRef is defined by more than one Workspace: " + repositoryRef);
            }
            match = candidate;
            workspaceId = entry.getKey();
        }
        if (managedLocation != null) {
            if (match != null && !match.equals(managedLocation.repositoryPath())) {
                throw new IllegalArgumentException("repositoryRef is defined by managed and YAML Workspaces at different paths: "
                        + repositoryRef);
            }
            return managedLocation;
        }
        if (match == null) throw new IllegalArgumentException("repositoryRef is not defined in any Workspace: " + repositoryRef);
        return new Location(workspaceId, workspaceId, match, match, false);
    }

    public static class Workspace {
        private Map<String, Path> repositories = new LinkedHashMap<>();
        public Map<String, Path> getRepositories() { return repositories; }
        public void setRepositories(Map<String, Path> repositories) {
            this.repositories = repositories == null ? new LinkedHashMap<>() : new LinkedHashMap<>(repositories);
        }
    }
}
