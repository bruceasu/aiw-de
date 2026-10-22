package me.asu.aidev.execution.guard;

import me.asu.aidev.execution.port.WorkerTaskClaimPolicy;
import me.asu.aidev.work.domain.WorkTask;
import me.asu.aidev.worker.domain.Worker;
import org.springframework.stereotype.Component;

@Component
public class GuardClaimPolicy implements WorkerTaskClaimPolicy {
    private final ExecutionGuard guard;
    private final me.asu.aidev.execution.config.ExecutionConfigurations configurations;
    public GuardClaimPolicy(ExecutionGuard guard,me.asu.aidev.execution.config.ExecutionConfigurations configurations) { this.guard=guard;this.configurations=configurations; }
    @Override public boolean canClaim(WorkTask task) {
        guard.serializeCandidate(task);
        return !guard.required(task) || guard.canClaim(task.id().value());
    }
    @Override public boolean canClaim(WorkTask task,Worker worker) {
        guard.serializeCandidate(task);
        if(task.requiredCapabilities().contains(me.asu.aidev.execution.config.ConfigurationContract.CAPABILITY))
            return configurations.managed(task.id().value()) && configurations.canClaim(task.id().value(),worker) && guard.canClaim(task.id().value());
        if(guard.required(task))
            return task.type()==me.asu.aidev.work.domain.TaskType.EXTERNAL_WORK && guard.managed(task.id().value()) && worker.capabilities().contains(GuardContract.CAPABILITY)
                    && (worker.runtimeType().startsWith("openai-agents:") || worker.runtimeType().startsWith("codex-app-server:"))
                    && worker.capabilities().contains("fixed-profile:"+guard.context(task.id().value()).configurationDigest())
                    && (guard.context(task.id().value()).originalPolicy().rules().stream().noneMatch(rule->rule.tool()==GuardContract.Tool.QUESTION)
                        || worker.capabilities().contains(me.asu.aidev.questions.QuestionContract.CAPABILITY))
                    && guard.context(task.id().value()).snapshot().references().stream().filter(ref->ref.kind().equals("skill")).allMatch(ref->
                        worker.capabilities().contains("fixed-skill:"+ref.id()+":"+ref.sha256()))
                    && worker.capabilities().stream().filter(cap->cap.startsWith("fixed-skill:")).allMatch(cap->guard.context(task.id().value()).snapshot().references().stream().anyMatch(ref->cap.equals("fixed-skill:"+ref.id()+":"+ref.sha256())))
                    && (guard.context(task.id().value()).originalPolicy().commands().isEmpty() || worker.capabilities().contains("guarded-command-linux-v1"))
                    && guard.canClaim(task.id().value());
        return true;
    }
}
