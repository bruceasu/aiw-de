package me.asu.aidev.goal.application;

import java.util.*;
import me.asu.aidev.ai.application.AiProfileCatalog;
import me.asu.aidev.execution.domain.AiSelection;
import me.asu.aidev.execution.port.WorkerTaskClaimPolicy;
import me.asu.aidev.goal.jdbc.GoalRepository;
import me.asu.aidev.work.domain.*;
import me.asu.aidev.worker.domain.Worker;
import org.springframework.stereotype.Component;

@Component
public class GoalTaskPolicy implements WorkerTaskClaimPolicy {
    public record Frozen(String backend, String version, List<AiProfileCatalog.NamedProfile> profiles) {}
    private final GoalRepository repo;
    private final AiProfileCatalog profiles;
    public GoalTaskPolicy(GoalRepository repo, AiProfileCatalog profiles) { this.repo = repo; this.profiles = profiles; }
    public boolean managed(WorkTask task) { return "goal-discussion".equals(task.subjectType()); }
    public static boolean runtime(String backend, String runtime) {
        return "openai".equals(backend) ? runtime.startsWith("openai-agents:")
            : List.of("codex-cli:", "codex-app-server:").stream().anyMatch(runtime::startsWith);
    }
    public Frozen freeze(String backend) {
        var selected = profiles.approved(TaskType.EXTERNAL_WORK).stream()
            .filter(p -> runtime(backend, p.profile().runtime() + ":"))
            .filter(p -> me.asu.aidev.workflow.application.RecommendationProfiles.compatible(p.profile())).toList();
        if (profiles.hasPolicy(TaskType.EXTERNAL_WORK) && selected.isEmpty())
            throw new IllegalStateException("No approved AI profile matches the selected backend");
        return new Frozen(backend, profiles.version(), selected);
    }
    public Frozen frozen(WorkTask task) {
        return repo.jdbc.query("SELECT frozen_json FROM goal_request WHERE id=?", (rs,n) -> repo.decode(rs.getString(1), Frozen.class),
            task.id().value()).stream().findFirst().orElseThrow(() -> new IllegalStateException("Frozen goal request missing"));
    }
    public boolean matches(Frozen f, String runtime) {
        return runtime(f.backend(), runtime) && (f.profiles().isEmpty() || f.profiles().stream().anyMatch(p -> runtime.startsWith(p.profile().runtime() + ":")));
    }
    @Override public boolean canClaim(WorkTask task) { return true; }
    @Override public boolean canClaim(WorkTask task, Worker worker) {
        return !managed(task) || matches(frozen(task), worker.runtimeType());
    }
    public AiProfileCatalog.Guidance guidance(WorkTask task, String runtime) {
        Frozen f = frozen(task);
        if (!matches(f, runtime)) throw new IllegalArgumentException("Worker does not match frozen goal backend");
        var allowed = f.profiles().stream().filter(p -> runtime.startsWith(p.profile().runtime() + ":")).toList();
        return allowed.isEmpty() ? null : new AiProfileCatalog.Guidance(f.version(), allowed, null, allowed.getFirst().id());
    }
    public AiSelection select(WorkTask task, String runtime, String id, String version, String reason) {
        Frozen f = frozen(task);
        if (!matches(f, runtime)) throw new IllegalArgumentException("Worker does not match frozen goal backend");
        if (f.profiles().isEmpty()) {
            if (id != null || version != null || reason != null) throw new IllegalArgumentException("Goal uses Worker defaults");
            return null;
        }
        if (!Objects.equals(f.version(), version) || reason == null || reason.isBlank() || reason.length() > 512)
            throw new IllegalArgumentException("Frozen profile version and selection reason are required");
        var chosen = f.profiles().stream().filter(p -> p.id().equals(id) && runtime.startsWith(p.profile().runtime() + ":")).findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Profile is outside the frozen goal backend"));
        var p = chosen.profile();
        return new AiSelection(chosen.id(), f.version(), p.provider(), p.runtime(), p.model(), p.effort(), p.fast(), reason.strip());
    }
}
