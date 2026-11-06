package me.asu.aidev.workflow.application;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.List;
import me.asu.aidev.work.domain.WorkTask;
import me.asu.aidev.workflow.port.WorkflowPlanRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Allocates a location, without interpreting user locations or preparing repositories. */
@Service
public class PlanLocations {
    private final JdbcTemplate jdbc;
    private final WorkflowPlanRepository plans;
    private final Path temporaryRoot;

    public PlanLocations(JdbcTemplate jdbc, WorkflowPlanRepository plans,
            @Value("${aidev.plans.temporary-root:data/plan-locations}") String root) {
        this.jdbc=jdbc; this.plans=plans; this.temporaryRoot=Path.of(root).toAbsolutePath().normalize();
    }
    @Transactional
    public String forTask(WorkTask task) {
        var plan=plans.find(task.subjectId());
        String scope=plan.isPresent()?plan.get().id():task.subjectType()+":"+task.subjectId();
        return allocate(scope,plan.map(p->p.spec().location()).orElse(null));
    }
    public record View(String location,boolean temporary,boolean cleaned) {}
    public View view(String planId) {
        var found=jdbc.query("SELECT location,temporary,cleaned_at_epoch_ms FROM plan_execution_location WHERE scope_id=?",
                (rs,row)->new View(rs.getString(1),rs.getInt(2)==1,rs.getObject(3)!=null),planId);
        return found.isEmpty()?new View(plans.find(planId).orElseThrow().spec().location(),false,false):found.getFirst();
    }
    @Transactional
    public String allocate(String scope,String declared) {
        var existing=jdbc.queryForList("SELECT location FROM plan_execution_location WHERE scope_id=?",String.class,scope);
        if(!existing.isEmpty()) {
            if(jdbc.queryForObject("SELECT COUNT(*) FROM plan_execution_location WHERE scope_id=? AND cleaned_at_epoch_ms IS NOT NULL",Long.class,scope)!=0)
                throw new IllegalStateException("Plan location has been released");
            return existing.getFirst();
        }
        boolean temporary=declared==null || declared.isBlank();
        String location=declared;
        try {
            if(temporary) {
                Files.createDirectories(temporaryRoot);
                location=Files.createTempDirectory(temporaryRoot,"plan-").toAbsolutePath().toString();
            }
            jdbc.update("INSERT INTO plan_execution_location(scope_id,location,temporary,created_at_epoch_ms) VALUES(?,?,?,?) ON CONFLICT(scope_id) DO NOTHING",
                    scope,location,temporary?1:0,Instant.now().toEpochMilli());
            String selected=jdbc.queryForObject("SELECT location FROM plan_execution_location WHERE scope_id=?",String.class,scope);
            if(temporary && !location.equals(selected)) Files.delete(Path.of(location));
            return selected;
        } catch(IOException error) { throw new IllegalStateException("Cannot allocate Plan location",error); }
    }
    /** Explicit cleanup; stop/lease expiry alone never proves that Worker processes have stopped. */
    @Transactional
    public void cleanup(String planId) {
        jdbc.update("UPDATE flexible_workflow_plan SET version=version WHERE id=?",planId);
        var plan=plans.find(planId).orElseThrow();
        if(!java.util.Set.of(me.asu.aidev.workflow.domain.WorkflowPlan.State.COMPLETED,me.asu.aidev.workflow.domain.WorkflowPlan.State.WITHDRAWN).contains(plan.state()))
            throw new IllegalStateException("Only an ended Plan can release its temporary directory");
        Long unsafe=jdbc.queryForObject("""
                SELECT COUNT(*) FROM execution_attempt a JOIN work_task t ON t.id=a.task_id
                 WHERE t.subject_id=? AND a.state NOT IN ('COMPLETED','FAILED','CANCELLED')
                """,Long.class,planId);
        if(unsafe==null || unsafe!=0) throw new IllegalStateException("Worker termination is not confirmed");
        var rows=jdbc.queryForList("SELECT location FROM plan_execution_location WHERE scope_id=? AND temporary=1 AND cleaned_at_epoch_ms IS NULL",String.class,planId);
        if(rows.isEmpty()) return;
        String location=rows.getFirst();
        Long references=jdbc.queryForObject("""
                SELECT COUNT(*) FROM plan_execution_location l LEFT JOIN flexible_workflow_plan p ON p.id=l.scope_id
                 WHERE l.location=? AND l.scope_id<>? AND l.cleaned_at_epoch_ms IS NULL
                   AND (p.state IS NULL OR p.state NOT IN ('COMPLETED','WITHDRAWN'))
                """,Long.class,location,planId);
        if(references==null || references!=0) throw new IllegalStateException("Another unfinished Plan references this location");
        Path directory=Path.of(location).toAbsolutePath().normalize();
        Path gate=null;
        boolean ownsGate=false;
        try {
            Path root=temporaryRoot.toRealPath();
            if(!directory.getParent().equals(temporaryRoot) || !directory.startsWith(temporaryRoot))
                throw new IllegalStateException("Temporary Plan directory ownership mismatch");
            String cleanupKey=".cleanup-"+me.asu.aidev.authority.AuthorityRepository.hash(planId);
            Path quarantine=root.resolve(cleanupKey);
            Path marker=root.resolve(cleanupKey+".owner");
            String ownership=planId+"\n"+location;
            boolean original=Files.exists(directory,LinkOption.NOFOLLOW_LINKS);
            boolean staged=Files.exists(quarantine,LinkOption.NOFOLLOW_LINKS);
            if(original && staged) throw new IOException("Both original and cleanup directory exist; reconcile ownership before retry");
            if(Files.exists(marker,LinkOption.NOFOLLOW_LINKS)) {
                if(!Files.isRegularFile(marker,LinkOption.NOFOLLOW_LINKS) || Files.size(marker)>8192
                        || !Files.readString(marker).equals(ownership)) throw new IOException("Cleanup ownership marker mismatch");
            } else if(staged) throw new IOException("Cleanup directory has no ownership marker");
            if(original || staged) {
                Path source=original?directory:quarantine;
                Path physical=source.toRealPath();
                if(Files.isSymbolicLink(source) || !physical.getParent().equals(root))
                    throw new IllegalStateException("Temporary Plan directory ownership mismatch");
                Path parent=source;
                for(String part:List.of(".ai","locks")) {
                    parent=parent.resolve(part);
                    if(!Files.exists(parent,LinkOption.NOFOLLOW_LINKS)) Files.createDirectory(parent);
                    if(Files.isSymbolicLink(parent) || !parent.toRealPath().equals(physical.resolve(source.relativize(parent))))
                        throw new IOException("Unsafe cleanup lock path");
                }
                gate=parent.resolve("execution.lock");
                Files.createDirectory(gate);
                ownsGate=true;
                Path ownedGate=gate;
                Files.walkFileTree(source,new SimpleFileVisitor<Path>() {
                    @Override public FileVisitResult preVisitDirectory(Path dir,BasicFileAttributes attrs)throws IOException {
                        if(!dir.toRealPath().equals(physical.resolve(source.relativize(dir)))) throw new IOException("Linked cleanup subtree is unsafe");
                        if(dir.getFileName().toString().equals("execution.lock") && !dir.equals(ownedGate)) throw new IOException("Worker resources are still occupied");
                        return FileVisitResult.CONTINUE;
                    }
                });
                if(original) {
                    if(!Files.exists(marker,LinkOption.NOFOLLOW_LINKS)) Files.writeString(marker,ownership,StandardOpenOption.CREATE_NEW);
                    Files.move(directory,quarantine,StandardCopyOption.ATOMIC_MOVE);
                    gate=quarantine.resolve(".ai/locks/execution.lock");
                }
                Files.walkFileTree(quarantine,new SimpleFileVisitor<Path>() {
                    @Override public FileVisitResult preVisitDirectory(Path dir,BasicFileAttributes attrs)throws IOException {
                        if(!dir.toRealPath().equals(quarantine.resolve(quarantine.relativize(dir)))) throw new IOException("Linked cleanup subtree is unsafe");
                        return FileVisitResult.CONTINUE;
                    }
                    @Override public FileVisitResult visitFile(Path file,BasicFileAttributes attrs)throws IOException { Files.delete(file); return FileVisitResult.CONTINUE; }
                    @Override public FileVisitResult postVisitDirectory(Path dir,IOException error)throws IOException { if(error!=null)throw error;Files.delete(dir);return FileVisitResult.CONTINUE; }
                });
            }
            // A failed deletion retains the deterministic directory and its external ownership marker.
            if(Files.exists(quarantine,LinkOption.NOFOLLOW_LINKS)) throw new IOException("Cleanup directory still exists");
            Files.deleteIfExists(marker);
            jdbc.update("UPDATE plan_execution_location SET cleaned_at_epoch_ms=? WHERE scope_id=?",Instant.now().toEpochMilli(),planId);
        } catch(IOException error) { throw new IllegalStateException("Temporary Plan cleanup failed; deterministic cleanup coordinates retained for retry",error); }
        finally {
            if(ownsGate) try { Files.deleteIfExists(gate); }
            catch(IOException ignored) { /* Preserve an unresolved exclusion marker for reconciliation. */ }
        }
    }
}
