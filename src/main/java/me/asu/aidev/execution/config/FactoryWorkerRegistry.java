package me.asu.aidev.execution.config;

import java.nio.file.*;
import java.time.Instant;
import java.util.Objects;
import me.asu.aidev.worker.domain.WorkerLiveness;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Registration visibility is separate from immutable execution history. Only factory-owned IDs are retired. */
@Service
public class FactoryWorkerRegistry {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    public FactoryWorkerRegistry(JdbcTemplate jdbc,PlatformTransactionManager transactions) {
        this.jdbc=jdbc;this.transaction=new TransactionTemplate(transactions);
    }
    public boolean busy(String id) {
        return jdbc.queryForObject("""
                SELECT (SELECT COUNT(*) FROM worker WHERE id=? AND active_leases>0)
                     + (SELECT COUNT(*) FROM work_task WHERE current_worker_id=? AND state IN ('CLAIMED','RUNNING'))
                     + (SELECT COUNT(*) FROM execution_attempt WHERE worker_id=? AND state IN ('CLAIMED','RUNNING'))
                """,Long.class,id,id,id)>0;
    }
    public void remember(String id,String key) {
        jdbc.update("""
                INSERT INTO factory_worker_registration VALUES(?,?,NULL)
                ON CONFLICT(worker_id) DO UPDATE SET compatibility_key=excluded.compatibility_key,retired_at_epoch_ms=NULL
                """,id,key);
    }
    public void registered(String id) {
        jdbc.update("UPDATE factory_worker_registration SET retired_at_epoch_ms=NULL WHERE worker_id=?",id);
    }
    public void heartbeat(String id) {
        jdbc.update("""
                UPDATE factory_worker_registration SET retired_at_epoch_ms=NULL WHERE worker_id=?
                 AND EXISTS (SELECT 1 FROM worker w WHERE w.id=? AND w.status='ACTIVE')
                """,id,id);
    }
    public boolean online(String id) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM worker WHERE id=? AND status='ACTIVE' AND last_heartbeat_at_epoch_ms>=?",
                Long.class,id,Instant.now().minus(WorkerLiveness.HEARTBEAT_WINDOW).toEpochMilli())>0;
    }
    /** Import prior factory status receipts; a name prefix alone does not prove ownership. */
    @jakarta.annotation.PostConstruct
    public void importLegacy() {
        Path directory=Path.of(System.getProperty("user.home"),".aiw","agent-factory","status");
        if(!Files.isDirectory(directory,LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(directory)) return;
        var json=new com.fasterxml.jackson.databind.ObjectMapper();
        try(var files=Files.list(directory)) {
            for(var file:files.limit(2000).toList()) {
                String name=file.getFileName().toString();
                if(!name.matches("factory-[0-9a-f]{24}\\.json") || !Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)) continue;
                try {
                    if(Files.size(file)>8192) continue;
                    String id=name.substring(0,name.length()-5);
                    if(!Objects.equals(id,json.readTree(Files.readString(file)).path("workerId").asText())) continue;
                    jdbc.update("""
                            INSERT INTO factory_worker_registration(worker_id,compatibility_key)
                            SELECT id,'legacy' FROM worker WHERE id=?
                            ON CONFLICT(worker_id) DO NOTHING
                            """,id);
                } catch(java.io.IOException ignored) { /* A partial receipt is not ownership evidence. */ }
            }
        } catch(java.io.IOException ignored) { /* Existing registrations remain visible without evidence. */ }
    }
    @org.springframework.scheduling.annotation.Scheduled(fixedDelayString="${aidev.agent-factory.registry-poll-ms:30000}")
    public void retireUnavailable() {
        long now=Instant.now().toEpochMilli();
        long since=Instant.now().minus(WorkerLiveness.HEARTBEAT_WINDOW).toEpochMilli();
        transaction.executeWithoutResult(status -> {
            jdbc.update("""
                    UPDATE worker SET updated_at_epoch_ms=updated_at_epoch_ms
                     WHERE EXISTS (SELECT 1 FROM factory_worker_registration f WHERE f.worker_id=worker.id)
                       AND (status='OFFLINE' OR last_heartbeat_at_epoch_ms<?) AND active_leases=0
                       AND NOT EXISTS (SELECT 1 FROM work_task t WHERE t.current_worker_id=worker.id AND t.state IN ('CLAIMED','RUNNING'))
                       AND NOT EXISTS (SELECT 1 FROM execution_attempt a WHERE a.worker_id=worker.id AND a.state IN ('CLAIMED','RUNNING'))
                    """,since);
            jdbc.update("""
                    UPDATE factory_worker_registration SET retired_at_epoch_ms=?
                     WHERE retired_at_epoch_ms IS NULL AND EXISTS
                       (SELECT 1 FROM worker w WHERE w.id=worker_id AND (w.status='OFFLINE' OR w.last_heartbeat_at_epoch_ms<?) AND w.active_leases=0)
                       AND NOT EXISTS (SELECT 1 FROM work_task t WHERE t.current_worker_id=worker_id AND t.state IN ('CLAIMED','RUNNING'))
                       AND NOT EXISTS (SELECT 1 FROM execution_attempt a WHERE a.worker_id=factory_worker_registration.worker_id AND a.state IN ('CLAIMED','RUNNING'))
                    """,now,since);
        });
    }
}
