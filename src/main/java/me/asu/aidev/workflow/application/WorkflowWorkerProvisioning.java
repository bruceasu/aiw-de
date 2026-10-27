package me.asu.aidev.workflow.application;

import java.time.Instant;
import java.util.*;
import me.asu.aidev.execution.config.AgentFactorySettings;
import me.asu.aidev.execution.config.ManagedWorkerProvisioner;
import me.asu.aidev.work.domain.TaskId;
import me.asu.aidev.work.port.WorkTaskRepository;
import me.asu.aidev.worker.domain.WorkerLiveness;
import me.asu.aidev.workflow.jdbc.JdbcRecommendationRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Supplies role-specialized processes only for already-confirmed native workflow tasks. */
@Service
public class WorkflowWorkerProvisioning {
    public record Preparation(String taskId,String planId,String state,String message) {}
    private final Map<String,Preparation> pending=new java.util.concurrent.ConcurrentHashMap<>();
    private String after="";
    public List<Preparation> pending(String planId) {return pending.values().stream().filter(p -> p.planId().equals(planId)).toList();}
    private final JdbcTemplate jdbc;
    private final WorkTaskRepository tasks;
    private final JdbcRecommendationRepository workers;
    private final AgentFactorySettings settings;
    private final ManagedWorkerProvisioner provisioner;
    public WorkflowWorkerProvisioning(JdbcTemplate jdbc,WorkTaskRepository tasks,JdbcRecommendationRepository workers,
            AgentFactorySettings settings,ManagedWorkerProvisioner provisioner) {
        this.jdbc=jdbc;this.tasks=tasks;this.workers=workers;this.settings=settings;this.provisioner=provisioner;
    }
    @org.springframework.scheduling.annotation.Scheduled(fixedDelayString="${aidev.workflow.worker-poll-ms:5000}")
    public void supply() {
        var config=settings.current();
        if(config==null || !config.automaticWorkers()) return;
        var ready=jdbc.queryForList("""
                SELECT t.id FROM work_task t JOIN flexible_workflow_plan p ON p.id=t.subject_id
                 WHERE t.subject_type='flexible-plan' AND t.state='READY' AND p.state='CONFIRMED'
                   AND NOT EXISTS (SELECT 1 FROM web_console_plan_archive a WHERE a.plan_id=p.id) AND t.id>?
                 ORDER BY t.id LIMIT 5
                """,String.class,after);
        if(ready.isEmpty()) after=""; else after=ready.getLast();
        pending.keySet().removeIf(id -> tasks.find(new TaskId(id)).map(t -> t.state()!=me.asu.aidev.work.domain.TaskState.READY).orElse(true));
        for(var id:ready) {
            me.asu.aidev.work.domain.WorkTask task=null;
            try {
                task=tasks.find(new TaskId(id)).orElseThrow();
                String role=switch(task.skillRef()) {
                    case "software-requirements-analyst" -> "requirements";
                    case "solution-architect" -> "architect";
                    case "implementation-planner" -> "planner";
                    case "coding-work-package-executor" -> "coder";
                    case "workflow-stage-reviewer" -> "stage-reviewer";
                    default -> null;
                };
                if(role==null) continue;
                var required=new HashSet<>(task.requiredCapabilities());
                if(task.repositoryRef()!=null) required.add("repo:"+task.repositoryRef());
                boolean online=workers.online(Instant.now().minus(WorkerLiveness.HEARTBEAT_WINDOW)).stream()
                        .anyMatch(w -> w.runtimeType().startsWith(config.runtime()+":") && w.supports(required));
                if(!online) {
                    var result=provisioner.requestNative(task.repositoryRef(),config.runtime(),role);
                    pending.put(id,new Preparation(id,task.subjectId(),result.starting()?"STARTING":"BLOCKED",result.message()));
                } else pending.remove(id);
            } catch(RuntimeException error) {
                if(task!=null) pending.put(id,new Preparation(id,task.subjectId(),"BLOCKED","Worker preparation failed; check the Agent factory status."));
                System.getLogger(getClass().getName()).log(System.Logger.Level.WARNING,"Confirmed workflow Worker preparation remains pending");
            }
        }
    }
}
