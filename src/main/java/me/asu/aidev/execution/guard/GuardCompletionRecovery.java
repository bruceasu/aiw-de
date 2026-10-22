package me.asu.aidev.execution.guard;

import me.asu.aidev.authority.AuthorityRepository;
import me.asu.aidev.execution.application.TaskExecutionService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Recovers committed terminal facts only. Never retries a tool, model request or unknown effect. */
@Component
public class GuardCompletionRecovery {
    private final AuthorityRepository repo;private final TaskExecutionService execution;
    private String cursor="";
    public GuardCompletionRecovery(AuthorityRepository repo,TaskExecutionService execution) { this.repo=repo;this.execution=execution; }
    @Scheduled(fixedDelayString="${aidev.execution-guard.recovery-delay-ms:5000}")
    public void recover() {
        var plans=repo.jdbc().queryForList("SELECT DISTINCT x.plan_id FROM execution_completion c JOIN execution_context x ON x.task_id=c.task_id WHERE c.applied=FALSE AND x.plan_id>? ORDER BY x.plan_id LIMIT 100",String.class,cursor);
        if(plans.isEmpty()) { cursor="";return; }
        for(String plan:plans) { execution.requestGuardedAdvance(plan);cursor=plan; }
    }
}
