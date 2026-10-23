package me.asu.aidev.execution.skills;

import me.asu.aidev.execution.port.WorkerTaskClaimPolicy;
import me.asu.aidev.work.domain.WorkTask;
import me.asu.aidev.worker.domain.Worker;
import org.springframework.stereotype.Component;

@Component
public class SkillClaimPolicy implements WorkerTaskClaimPolicy {
    private final SkillFiles files;
    public SkillClaimPolicy(SkillFiles files) { this.files=files; }
    public boolean canClaim(WorkTask task) { return true; }
    public boolean canClaim(WorkTask task,Worker worker) {
        if(task.requiredCapabilities().contains(SkillFileContract.CAPABILITY) && !files.managed(task.id().value())) return false;
        return !files.managed(task.id().value()) || worker.capabilities().contains(SkillFileContract.CAPABILITY)
                && (worker.runtimeType().startsWith("openai-agents:") || worker.runtimeType().startsWith("openai-api:") || worker.runtimeType().startsWith("codex-app-server:"));
    }
}
