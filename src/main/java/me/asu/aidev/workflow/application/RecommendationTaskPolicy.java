package me.asu.aidev.workflow.application;

import java.util.List;
import me.asu.aidev.ai.application.AiProfileCatalog;
import me.asu.aidev.execution.domain.AiSelection;
import me.asu.aidev.execution.port.WorkerTaskClaimPolicy;
import me.asu.aidev.work.domain.WorkTask;
import me.asu.aidev.worker.domain.Worker;
import me.asu.aidev.workflow.jdbc.JdbcRecommendationRepository;
import me.asu.aidev.workflow.jdbc.JdbcRecommendationRepository.Frozen;
import org.springframework.stereotype.Component;

/** One frozen policy shared by admission, guidance and start. */
@Component
public class RecommendationTaskPolicy implements WorkerTaskClaimPolicy {
    public static final String SUBJECT = "workflow-recommendation";
    private final JdbcRecommendationRepository repository;
    public RecommendationTaskPolicy(JdbcRecommendationRepository repository) { this.repository = repository; }
    public boolean managed(WorkTask task) { return SUBJECT.equals(task.subjectType()) && repository.request(task.id().value()).isPresent(); }
    public Frozen frozen(WorkTask task) { return repository.request(task.id().value()).orElseThrow().frozen(); }
    public static boolean matches(Frozen frozen, String runtime) {
        if (frozen.profiles().isEmpty()) return List.of("codex-cli:", "codex-sdk:", "codex-app-server:", "openai-agents:", "openai-api:").stream().anyMatch(runtime::startsWith);
        return frozen.profiles().stream().anyMatch(p -> runtime.startsWith(p.profile().runtime() + ":"));
    }
    @Override public boolean canClaim(WorkTask task) { return true; }
    @Override public boolean canClaim(WorkTask task, Worker worker) {
        return !managed(task) || matches(frozen(task), worker.runtimeType());
    }
    public AiProfileCatalog.Guidance guidance(WorkTask task, String runtime) {
        var frozen = frozen(task);
        if (!matches(frozen, runtime)) throw new IllegalArgumentException("Worker does not match frozen recommendation profiles");
        var allowed = frozen.profiles().stream().filter(p -> runtime.startsWith(p.profile().runtime() + ":")).toList();
        return allowed.isEmpty() ? null : new AiProfileCatalog.Guidance(frozen.version(), allowed, null, allowed.getFirst().id());
    }
    public AiSelection select(WorkTask task, String runtime, String id, String version, String reason) {
        var frozen = frozen(task);
        if (frozen.profiles().isEmpty()) {
            if (id != null || version != null || reason != null) throw new IllegalArgumentException("recommendation uses Worker defaults, not a profile");
            return null;
        }
        if (!frozen.version().equals(version) || reason == null || reason.isBlank() || reason.length() > 512)
            throw new IllegalArgumentException("frozen recommendation profile version and selection reason are required");
        var chosen = frozen.profiles().stream().filter(p -> p.id().equals(id) && runtime.startsWith(p.profile().runtime() + ":"))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("selection is outside frozen recommendation profiles"));
        var p = chosen.profile();
        return new AiSelection(chosen.id(), frozen.version(), p.provider(), p.runtime(), p.model(), p.effort(), p.fast(), reason.strip());
    }
}
