package me.asu.aidev.workspace.application;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Read-only metadata projection. New Plans use location and do not register Workspaces. */
@Service
public class ManagedWorkspaceService {
    private final JdbcTemplate jdbc;
    public ManagedWorkspaceService(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public record Workspace(String id,String displayName,String rootPath,String targetMode,boolean enabled,List<Target> targets) {}
    public record Target(String repositoryRef,String workspaceId,String workspaceName,String workspaceRoot,
                         String repositoryPath,String repoId,String relativePath,String gitWarning,String sourceType) {}
    public List<Workspace> list() {
        return jdbc.query("SELECT id,display_name,root_path,target_mode,enabled FROM workspace_registry",
            (rs,row)->new Workspace(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getInt(5)==1,List.of()));
    }
    public Workspace get(String id) { return list().stream().filter(w->w.id().equals(id)).findFirst().orElseThrow(); }
    public List<Target> targets() {
        return jdbc.query("""
            SELECT r.repository_ref,w.id,w.display_name,w.root_path,r.repo_id,r.relative_path,r.git_warning
              FROM workspace_repo_registry r JOIN workspace_registry w ON w.id=r.workspace_id
             WHERE r.enabled=1 AND w.enabled=1
            """,(rs,row)->new Target(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),
                Path.of(rs.getString(4)).resolve(rs.getString(6)).toString(),rs.getString(5),rs.getString(6),rs.getString(7),"DECLARED"));
    }
    public Optional<Target> findTarget(String ref) { return targets().stream().filter(t->t.repositoryRef().equals(ref)).findFirst(); }
}
