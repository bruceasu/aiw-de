package me.asu.aidev.secretary.application;

import me.asu.aidev.execution.port.TaskFailureListener;
import me.asu.aidev.secretary.domain.SecretaryData.State;
import me.asu.aidev.secretary.jdbc.SecretaryRepository;
import me.asu.aidev.work.domain.WorkTask;
import org.springframework.stereotype.Component;

@Component
public class SecretaryFailureListener implements TaskFailureListener {
    private final SecretaryRepository repo;
    private final SecretaryTaskPolicy policy;
    public SecretaryFailureListener(SecretaryRepository repo,SecretaryTaskPolicy policy) { this.repo=repo;this.policy=policy; }
    @Override public void onFailed(WorkTask task,String reason) {
        if("secretary-chat".equals(task.subjectType())) return;
        // TaskExecutionPolicy already acquired the secretary lock before the failure writes.
        policy.work(task).filter(w->w.state()==State.QUEUED || w.state()==State.ACTIVE)
            .ifPresent(w->repo.state(w,State.NEEDS_HUMAN,"Worker failed. Inspect original task evidence; no business action was retried"));
    }
}
