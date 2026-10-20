package me.asu.aidev.secretary.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import me.asu.aidev.secretary.jdbc.SecretaryRepository;
import me.asu.aidev.worker.domain.WorkerId;
import me.asu.aidev.worker.port.WorkerRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DelegationIdentityService {
    private me.asu.aidev.webconsole.security.WebConsoleSecurityService security;
    @org.springframework.beans.factory.annotation.Autowired
    public void setSecurity(me.asu.aidev.webconsole.security.WebConsoleSecurityService security) { this.security=security; }
    private final JdbcTemplate jdbc;
    private final DelegationBoundary boundary;
    private final WorkerRepository workers;
    public DelegationIdentityService(JdbcTemplate jdbc, DelegationBoundary boundary, WorkerRepository workers) {
        this.jdbc = jdbc; this.boundary = boundary; this.workers = workers;
    }
    public record Identity(String workerId, long generation, boolean enabled, String changedBy, long changedMs,String processState,boolean live) {}
    public List<Identity> list() {
        return jdbc.query("SELECT worker_id,generation,enabled_boot,changed_by,changed_ms FROM delegation_identity ORDER BY worker_id",
                (rs,n) -> {
                    var worker=workers.find(new WorkerId(rs.getString(1)));
                    return new Identity(rs.getString(1), rs.getLong(2), boundary.boot().equals(rs.getString(3)), rs.getString(4), rs.getLong(5),
                            worker.map(w->w.status().name()).orElse("MISSING"),worker.filter(w->w.status()==me.asu.aidev.worker.domain.WorkerStatus.ACTIVE
                            && w.lastHeartbeatAt().isAfter(Instant.now().minus(me.asu.aidev.worker.domain.WorkerLiveness.HEARTBEAT_WINDOW))).isPresent());
                });
    }
    public record LegacyRequest(String id,String method,String path,long startedMs,boolean recoverable) {}
    public List<LegacyRequest> legacyRequests() {
        return jdbc.query("SELECT id,method,path,started_ms,server_boot FROM delegation_legacy_request WHERE state='ACTIVE' ORDER BY started_ms,id",
                (rs,n)->new LegacyRequest(rs.getString(1),rs.getString(2),rs.getString(3),rs.getLong(4),!boundary.boot().equals(rs.getString(5))));
    }
    @Transactional public void recoverLegacy(String id,boolean confirmedStopped,String reason,String actor) {
        boundary.lock();
        if(!confirmedStopped || reason==null || reason.isBlank() || reason.length()>2000)
            throw new IllegalArgumentException("Confirm that the original process stopped and provide the evidence/reason");
        var request=legacyRequests().stream().filter(r->r.id().equals(id)).findFirst().orElseThrow();
        if(!request.recoverable()) throw new IllegalStateException("This server still owns the in-flight request; it cannot be abandoned");
        jdbc.update("UPDATE delegation_legacy_request SET state='ABANDONED',finished_ms=? WHERE id=? AND state='ACTIVE'",System.currentTimeMillis(),id);
        jdbc.update("INSERT INTO delegation_audit VALUES(?,NULL,NULL,NULL,?,?,?,?)",UUID.randomUUID().toString(),actor,"LEGACY_REQUEST_RECOVERED",
                id+"; "+request.method()+" "+request.path()+"; human confirmed the original process stopped: "+reason,System.currentTimeMillis());
    }
    @Transactional public void enabled(String workerId, long generation, boolean enabled, boolean confirmed, String actor) {
        boundary.lock();
        if (enabled) {
            if(security==null || !security.enabled()) throw new IllegalStateException("Configure human console authentication before enabling delegation");
            if(jdbc.queryForObject("SELECT COUNT(*) FROM delegation_legacy_request WHERE state='ACTIVE'",Long.class)>0)
                throw new IllegalStateException("Legacy management requests are still in flight. Finish them, or explicitly recover a confirmed stopped process, before first enablement");
            if (!confirmed) throw new IllegalArgumentException("Explicit confirmation is required to enable delegation");
            var worker = workers.find(new WorkerId(workerId)).orElseThrow();
            if (worker.status() != me.asu.aidev.worker.domain.WorkerStatus.ACTIVE
                    || !worker.runtimeType().endsWith(":decision-delegate")
                    || !worker.capabilities().contains("delegated-decision")
                    || worker.lastHeartbeatAt().isBefore(Instant.now().minus(me.asu.aidev.worker.domain.WorkerLiveness.HEARTBEAT_WINDOW)))
                throw new IllegalStateException("A live, dedicated decision-delegate Worker is required");
            // User approved this compatibility change on 2026-10-21. It never silently reverts on restart/disable.
            jdbc.update("UPDATE delegation_security SET legacy_auth_required=TRUE WHERE id=1");
        }
        long now = Instant.now().toEpochMilli();
        if (jdbc.update("UPDATE delegation_identity SET enabled_boot=?,generation=generation+1,changed_by=?,changed_ms=? WHERE worker_id=? AND generation=?",
                enabled ? boundary.boot() : null, actor, now, workerId, generation) != 1)
            throw new IllegalStateException("Delegate identity changed; reload");
        jdbc.update("UPDATE delegation_request SET state='HUMAN',detail=?,updated_ms=? WHERE worker_id=? AND state='PENDING'",
                "Delegate enablement changed. The old candidate cannot be applied.", now, workerId);
        jdbc.update("INSERT INTO delegation_audit VALUES(?,NULL,NULL,NULL,?,?,?,?)", UUID.randomUUID().toString(), actor,
                enabled ? "DELEGATE_ENABLED" : "DELEGATE_DISABLED", "Worker " + workerId + "; old candidates invalidated", now);
    }
    @Transactional public void enroll(String workerId, String key, String actor) {
        boundary.lock();
        var worker = workers.find(new WorkerId(workerId)).orElseThrow(() -> new IllegalArgumentException("Register the Worker before enrollment"));
        if (worker.activeLeases() != 0) throw new IllegalStateException("Drain and finish current leases before enrolling or rotating a delegate");
        if (key == null || !key.matches("[A-Za-z0-9_-]{43,128}"))
            throw new IllegalArgumentException("Use a private random credential of 43 to 128 URL-safe characters");
        long now = Instant.now().toEpochMilli();
        jdbc.update("""
                INSERT INTO delegation_identity(worker_id,key_hash,generation,enabled_boot,changed_by,changed_ms)
                VALUES(?,?,1,NULL,?,?) ON CONFLICT(worker_id) DO UPDATE SET key_hash=excluded.key_hash,
                generation=delegation_identity.generation+1,enabled_boot=NULL,changed_by=excluded.changed_by,changed_ms=excluded.changed_ms
                """, workerId, SecretaryRepository.hash(key), actor, now);
        jdbc.update("UPDATE delegation_request SET state='HUMAN',detail=?,updated_ms=? WHERE worker_id=? AND state='PENDING'",
                "Delegate credential changed. A fresh human-confirmed enablement and candidate are required.", now, workerId);
        jdbc.update("INSERT INTO delegation_audit VALUES(?,NULL,NULL,NULL,?,?,?,?)", UUID.randomUUID().toString(), actor,
                "IDENTITY_ENROLLED", "Worker " + workerId + "; delegation disabled; credential not recorded in evidence", now);
    }
}
