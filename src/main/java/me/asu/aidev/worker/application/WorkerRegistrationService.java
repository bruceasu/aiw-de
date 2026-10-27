package me.asu.aidev.worker.application;

import java.time.Instant;
import java.util.Objects;
import me.asu.aidev.authority.AuthorityBoundary;
import me.asu.aidev.authority.AuthorityRepository;
import me.asu.aidev.worker.domain.WorkerId;
import me.asu.aidev.worker.domain.WorkerLiveness;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Explicit human unregistration never removes execution history or grants factory ownership. */
@Service
public class WorkerRegistrationService {
    private final AuthorityRepository repo;
    private final AuthorityBoundary boundary;
    public WorkerRegistrationService(AuthorityRepository repo,AuthorityBoundary boundary) {this.repo=repo;this.boundary=boundary;}
    public record Result(String workerId,String state) {}
    @Transactional public Result unregister(String id,long expectedHeartbeat,String reason,String actor) {
        new WorkerId(id);
        if(boundary.delegated() || actor==null || !Objects.equals(actor,boundary.human()))
            throw new SecurityException("Authenticated human administrator required");
        if(expectedHeartbeat<0 || reason==null || reason.isBlank() || reason.length()>1000)
            throw new IllegalArgumentException("Heartbeat snapshot and bounded cleanup reason required");
        boundary.lock();
        if(repo.jdbc().queryForObject("SELECT COUNT(*) FROM worker_registration_retirement WHERE worker_id=?",Long.class,id)>0)
            return new Result(id,"UNREGISTERED");
        boundary.changing("WORKER:"+id);
        long since=Instant.now().minus(WorkerLiveness.HEARTBEAT_WINDOW).toEpochMilli();
        if(repo.jdbc().update("""
                UPDATE worker SET status='OFFLINE',updated_at_epoch_ms=?
                 WHERE id=? AND last_heartbeat_at_epoch_ms=? AND active_leases=0
                   AND (status='OFFLINE' OR last_heartbeat_at_epoch_ms<?)
                   AND NOT EXISTS (SELECT 1 FROM work_task t WHERE t.current_worker_id=worker.id AND t.state IN ('CLAIMED','RUNNING'))
                   AND NOT EXISTS (SELECT 1 FROM execution_attempt a WHERE a.worker_id=worker.id AND a.state IN ('CLAIMED','RUNNING'))
                """,Instant.now().toEpochMilli(),id,expectedHeartbeat,since)!=1)
            throw new IllegalStateException("Worker is active, busy or changed; reload before unregistering");
        repo.jdbc().update("INSERT INTO worker_registration_retirement VALUES(?,?,?,?)",id,actor,reason.strip(),Instant.now().toEpochMilli());
        repo.audit("WORKER:"+id,null,null,actor,"WORKER_UNREGISTERED",reason.strip());
        return new Result(id,"UNREGISTERED");
    }
    public void registered(String id) {
        repo.jdbc().update("DELETE FROM worker_registration_retirement WHERE worker_id=?",id);
    }
}
