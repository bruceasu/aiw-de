package me.asu.aidev.execution.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import me.asu.aidev.ai.application.AiProfileCatalog;
import me.asu.aidev.authority.AuthorityRepository;
import me.asu.aidev.authority.AuthorityBoundary;
import me.asu.aidev.execution.domain.AiSelection;
import me.asu.aidev.execution.port.WorkerTaskClaimPolicy;
import me.asu.aidev.work.domain.WorkTask;
import me.asu.aidev.worker.domain.Worker;
import org.springframework.stereotype.Service;

/** Freezes one reviewed factory profile per workflow task. */
@Service
public class FactoryTaskProfiles implements WorkerTaskClaimPolicy {
    private final AgentFactorySettings factory;
    private final AuthorityRepository db;
    @org.springframework.beans.factory.annotation.Autowired private AuthorityBoundary boundary;
    public FactoryTaskProfiles(AgentFactorySettings factory,AuthorityRepository db) {this.factory=factory;this.db=db;}

    public AgentFactorySettings.RuntimeProfile resolve(String requested) {
        var state=factory.runtimeProfiles();
        String id=requested==null?state.defaultId():requested;
        if(id==null)return null;
        return state.items().stream().filter(p->p.enabled()&&p.id().equals(id)).findFirst()
                .orElseThrow(()->new IllegalArgumentException("Runtime profile unavailable: "+id));
    }

    public void bind(String task,AgentFactorySettings.RuntimeProfile profile) {
        if(profile!=null)db.jdbc().update("INSERT INTO workflow_task_runtime(task_id,profile_json) VALUES(?,?)",
                task,db.encode(profile));
    }

    public void inherit(String source,String successor) {
        db.jdbc().update("""
                INSERT INTO workflow_task_runtime(task_id,profile_json)
                SELECT ?,profile_json FROM workflow_task_runtime WHERE task_id=? ON CONFLICT(task_id) DO NOTHING
                """,successor,source);
    }

    public AgentFactorySettings.RuntimeProfile read(WorkTask task) {
        if(!"flexible-plan".equals(task.subjectType()))return null;
        return db.jdbc().queryForList("SELECT profile_json FROM workflow_task_runtime WHERE task_id=?",
                String.class,task.id().value()).stream().findFirst().map(data->db.decode(data,AgentFactorySettings.RuntimeProfile.class)).orElse(null);
    }

    @Override public boolean canClaim(WorkTask task) {return true;}
    @Override public boolean canClaim(WorkTask task,Worker worker) {
        var profile=read(task);
        if(profile==null)return true;
        String prefix=profile.settings().workerRuntime();
        String runtime=worker.runtimeType();
        return runtime.startsWith(prefix)&&runtime.substring(prefix.length()).indexOf(':')<0
                &&worker.capabilities().contains("workflow-runtime-profile-v1");
    }

    public AiProfileCatalog.Guidance guidance(AgentFactorySettings.RuntimeProfile profile) {
        var settings=profile.settings();
        var value=new AiProfileCatalog.Profile(settings.apiProvider(),settings.runtime(),settings.model(),settings.effort(),false,1);
        return new AiProfileCatalog.Guidance(Long.toString(profile.version()),List.of(new AiProfileCatalog.NamedProfile(profile.id(),value)),null,profile.id());
    }

    public AiSelection select(AgentFactorySettings.RuntimeProfile profile,String id,String version,String reason) {
        var guidance=guidance(profile);
        if(!Objects.equals(id,profile.id())||!Objects.equals(version,guidance.version())||reason==null||reason.isBlank()||reason.length()>512)
            throw new IllegalArgumentException("The Worker must use the frozen runtime profile.");
        var value=guidance.allowedProfiles().getFirst().profile();
        return new AiSelection(id,version,value.provider(),value.runtime(),value.model(),value.effort(),value.fast(),reason.strip());
    }

    /** The claim transaction serializes new writers before reserving a lease. */
    public boolean workspaceAvailable(WorkTask task,boolean serialize) {
        if(!"flexible-plan".equals(task.subjectType())||!"workspace-work".equals(task.skillRef()))return true;
        if(serialize)boundary.lock();
        var positions=db.jdbc().queryForList("SELECT location FROM plan_execution_location WHERE scope_id=?",
                String.class,task.subjectId());
        if(positions.isEmpty())return false;
        Path location=physical(positions.getFirst());
        if(location==null)return false;
        var occupied=db.jdbc().query("""
                SELECT DISTINCT f.canonical_location,l.location
                  FROM work_task t LEFT JOIN workflow_task_runtime f ON f.task_id=t.id
                  JOIN plan_execution_location l ON l.scope_id=t.subject_id
                 WHERE t.subject_type='flexible-plan' AND t.skill_ref='workspace-work'
                   AND (t.lease_token IS NOT NULL OR EXISTS (
                        SELECT 1 FROM execution_attempt a WHERE a.task_id=t.id AND a.state='EXPIRED'
                         AND NOT EXISTS (SELECT 1 FROM execution_stop_evidence e WHERE e.attempt_id=a.id)
                   ))
                """, (row,index) -> {
                    String canonical=row.getString(1);
                    return canonical==null?physical(row.getString(2)):Path.of(canonical);
                });
        for(Path other:occupied){
            if(other==null||location.startsWith(other)||other.startsWith(location))return false;
        }
        if(serialize)db.jdbc().update("""
                INSERT INTO workflow_task_runtime(task_id,profile_json,canonical_location) VALUES(?,'null',?)
                 ON CONFLICT(task_id) DO UPDATE SET canonical_location=excluded.canonical_location
                """,task.id().value(),location.toString());
        return true;
    }

    public String canonicalLocation(WorkTask task) {
        return db.jdbc().queryForList("SELECT canonical_location FROM workflow_task_runtime WHERE task_id=?",
                String.class,task.id().value()).stream().filter(Objects::nonNull).findFirst().orElse(null);
    }

    private static Path physical(String value) {
        try {
            var abs=Path.of(value).toAbsolutePath().normalize();
            var existing=abs;
            while(existing!=null&&!Files.exists(existing))existing=existing.getParent();
            return existing==null?null:existing.toRealPath().resolve(existing.relativize(abs));
        } catch(InvalidPathException|IOException|SecurityException failure) {
            return null;
        }
    }
}
