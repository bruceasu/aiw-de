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
    @org.springframework.beans.factory.annotation.Autowired private WorkflowReviewClaimPolicy stageReviewPolicy;
    @org.springframework.beans.factory.annotation.Autowired private FactoryReviewClaimPolicy factoryReviewPolicy;
    @org.springframework.beans.factory.annotation.Autowired private me.asu.aidev.execution.run.NativeRunClaimPolicy nativeRuns;
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
                 WHERE t.subject_type='flexible-plan' AND t.state='READY'
                   AND (p.state='CONFIRMED' OR (p.state='COMPLETED' AND EXISTS
                       (SELECT 1 FROM factory_plan_review r WHERE r.review_task_id=t.id AND r.state='PENDING')))
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

                var currentTask=task;
                boolean online=workers.online(Instant.now().minus(WorkerLiveness.HEARTBEAT_WINDOW)).stream()
                        .anyMatch(w -> w.runtimeType().startsWith(config.runtime()+":") && w.supports(required)
                                && (factoryReviewPolicy==null || factoryReviewPolicy.canClaim(currentTask,w))
                                && (stageReviewPolicy==null || stageReviewPolicy.canClaim(currentTask,w))
                                && (nativeRuns==null || nativeRuns.canClaim(currentTask,w)));
                if(!online) {
                    boolean frozen=required.stream().anyMatch(c->c.startsWith("native-config:"));
                    boolean incompatible=workers.online(Instant.now().minus(WorkerLiveness.HEARTBEAT_WINDOW)).stream().anyMatch(w->w.runtimeType().startsWith(config.runtime()+":")&&w.capabilities().contains(roleCapability(role)));
                    if(frozen&&incompatible) {
                        pending.put(id,new Preparation(id,task.subjectId(),"BLOCKED","此后继任务固定到原生 Worker 配置和交接存储；当前在线配置不匹配。恢复相同 Worker 配置，或明确创建新计划；不会自动切换模型。"));continue;
                    }
                    var result=provisioner.requestNative(task.repositoryRef(),config.runtime(),role);
                    pending.put(id,new Preparation(id,task.subjectId(),result.starting()?"STARTING":"BLOCKED",result.message()));
                } else pending.remove(id);
            } catch(RuntimeException error) {
                if(task!=null) pending.put(id,new Preparation(id,task.subjectId(),"BLOCKED","Worker preparation failed; check the Agent factory status."));
                System.getLogger(getClass().getName()).log(System.Logger.Level.WARNING,"Confirmed workflow Worker preparation remains pending");
            }
        }
    }
    private String roleCapability(String role) {
        return switch(role) {case "requirements"->"requirement-analysis";case "architect"->"solution-architecture";case "planner"->"implementation-planning";case "coder"->"coding";default->"workflow-review";};
    }
}
