package me.asu.aidev.workspace.application;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import me.asu.aidev.autonomous.application.WorkspaceRegistry;
import me.asu.aidev.execution.port.TaskConcurrencyBudget;
import me.asu.aidev.work.domain.WorkTask;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Serializes direct-directory claims for one target across Runs. */
@Component
@Order(150)
public class DirectWorkspaceConcurrencyBudget implements TaskConcurrencyBudget {
    private static final String CLAIM_MUTEX = "__direct_workspace_claim__";
    private final JdbcTemplate jdbc;
    private final WorkspaceRegistry workspaces;

    public DirectWorkspaceConcurrencyBudget(JdbcTemplate jdbc, WorkspaceRegistry workspaces) {
        this.jdbc = jdbc;
        this.workspaces = workspaces;
    }

    @Override
    public boolean tryReserve(WorkTask task, Instant at) {
        if (task.repositoryRef() == null || task.baseRevision() != null) return true;
        jdbc.update("INSERT INTO direct_workspace_lock(repository_ref) VALUES(?) ON CONFLICT DO NOTHING",
                CLAIM_MUTEX);
        // Serialize admissions while comparing physical paths; distinct refs may be aliases.
        jdbc.update("UPDATE direct_workspace_lock SET repository_ref=repository_ref WHERE repository_ref=?",
                CLAIM_MUTEX);
        Path target = physicalPath(task.repositoryRef());
        return jdbc.query("""
                SELECT DISTINCT repository_ref FROM work_task
                 WHERE repository_ref IS NOT NULL AND base_revision IS NULL
                   AND state IN ('CLAIMED','RUNNING')
                """, (rs, row) -> rs.getString("repository_ref")).stream()
                .noneMatch(ref -> target.equals(physicalPath(ref)));
    }

    private Path physicalPath(String repositoryRef) {
        try {
            return workspaces.repositoryPath(repositoryRef).toRealPath();
        } catch (IOException error) {
            throw new IllegalStateException("direct Workspace target cannot be resolved: " + repositoryRef, error);
        }
    }

    @Override
    public void release(WorkTask task, Instant at) {
        // Live task state is the authority; there is no second counter to release.
    }
}
