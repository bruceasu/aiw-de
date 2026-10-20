package me.asu.aidev.secretary.application;

import java.util.*;
import me.asu.aidev.execution.port.WorkerTaskClaimPolicy;
import me.asu.aidev.execution.port.TaskExecutionPolicy;
import me.asu.aidev.secretary.domain.SecretaryData.*;
import me.asu.aidev.secretary.jdbc.SecretaryRepository;
import me.asu.aidev.webconsole.security.WebConsoleSecurityService;
import me.asu.aidev.work.domain.*;
import me.asu.aidev.worker.domain.Worker;
import org.springframework.stereotype.Component;

@Component
public class SecretaryTaskPolicy implements WorkerTaskClaimPolicy, TaskExecutionPolicy {
    private final SecretaryRepository repo;
    private final WebConsoleSecurityService security;
    public SecretaryTaskPolicy(SecretaryRepository repo,WebConsoleSecurityService security) { this.repo=repo;this.security=security; }
    public Optional<Work> work(WorkTask task) {
        return switch(task.subjectType()) {
            case "secretary-chat" -> repo.find(task.subjectId());
            case "secretary-coordinate" -> repo.request(task.id().value()).flatMap(r->repo.find(r.workId()));
            case "flexible-plan" -> repo.plan(task.subjectId());
            default -> Optional.empty();
        };
    }
    public boolean authorized(Work w) {
        String current=security.currentOperatorAuthority(w.actor());
        return !current.isBlank() && current.equals(w.content().authority());
    }
    private boolean allowed(WorkTask task,Work w) {
        if ("secretary-chat".equals(task.subjectType())) return !security.currentOperatorAuthority(w.actor()).isBlank();
        if (!authorized(w) || !Set.of(State.QUEUED,State.ACTIVE).contains(w.state()) || !repo.current(w)) return false;
        if ("secretary-coordinate".equals(task.subjectType())) return repo.request(task.id().value())
            .filter(r->!r.consumed() && r.generation()==w.generation()).isPresent();
        // An expired professional attempt has an unknown outcome. Never repeat it implicitly.
        return w.state()==State.ACTIVE && repo.admitted(task.id().value(),w) && !repo.interrupted(task.id().value());
    }
    @Override public boolean canClaim(WorkTask task) { return work(task).isEmpty(); }
    @Override public boolean canClaim(WorkTask task,Worker worker) {
        if (work(task).isEmpty()) return true;
        repo.lock();
        Work w=work(task).orElseThrow();
        if (!allowed(task,w)) return false;
        if ("secretary-chat".equals(task.subjectType())) return worker.runtimeType().endsWith(":secretary");
        String role="secretary-coordinate".equals(task.subjectType())?"COORDINATOR":"SPECIALIST";
        if (role.equals("COORDINATOR") && !worker.runtimeType().endsWith(":plan-coordinator")) return false;
        return repo.participant(w,worker.id().value(),role,true);
    }
    @Override public void assertExecutionAllowed(WorkTask task) {
        if (work(task).isEmpty()) return;
        repo.lock();
        Work w=work(task).orElseThrow();
        // Already running work must be able to heartbeat, fail and record its real completion.
        if (task.state()!=TaskState.RUNNING && !allowed(task,w))
            throw new IllegalStateException("Secretary delegation was cancelled, changed or requires human confirmation");
    }
}
