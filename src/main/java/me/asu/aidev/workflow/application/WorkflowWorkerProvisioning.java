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

/** Supplies compatible processes only for already-confirmed native workflow tasks. */
@Service
public class WorkflowWorkerProvisioning {
    @org.springframework.beans.factory.annotation.Autowired private me.asu.aidev.worker.application.TeamMembers team;
    @org.springframework.beans.factory.annotation.Autowired private org.springframework.beans.factory.ObjectProvider<WorkflowRuns> workflows;
    @org.springframework.beans.factory.annotation.Autowired private org.springframework.beans.factory.ObjectProvider<WorkflowEditorService> editor;
    @org.springframework.beans.factory.annotation.Autowired private WorkflowReviewClaimPolicy stageReviewPolicy;
    @org.springframework.beans.factory.annotation.Autowired private FactoryReviewClaimPolicy factoryReviewPolicy;
    @org.springframework.beans.factory.annotation.Autowired private me.asu.aidev.execution.run.NativeRunClaimPolicy nativeRuns;
    @org.springframework.beans.factory.annotation.Autowired private me.asu.aidev.execution.config.FactoryTaskProfiles runtimeProfiles;
    @org.springframework.beans.factory.annotation.Autowired private me.asu.aidev.modelauth.ModelConnections modelConnections;
    @org.springframework.beans.factory.annotation.Autowired private me.asu.aidev.execution.config.ManagerProfileSelection managerProfiles;
    @org.springframework.beans.factory.annotation.Value("${aidev.manager.automatic-diagnosis:true}")
    private boolean managerAutomaticDiagnosis = true;
    @org.springframework.beans.factory.annotation.Value("${aidev.manager.automatic-rate-limit-retry:false}")
    private boolean automaticRateLimitRetry;
    public record Preparation(String taskId,String planId,String state,String message) {}
    private final Map<String,Preparation> pending=new java.util.concurrent.ConcurrentHashMap<>();
    private String after="";
    private String memberAfter="";
    private String caretakerAfter="";
    private String recoveryAfter="";
    private final java.util.concurrent.atomic.AtomicBoolean selectingProfile=new java.util.concurrent.atomic.AtomicBoolean();
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
    private void diagnoseFailedTasks() {
        var failed=jdbc.queryForList("""
                SELECT t.id AS task_id,t.subject_id AS plan_id,a.id AS attempt_id,o.actor
                  FROM work_task t JOIN flexible_workflow_plan p ON p.id=t.subject_id
                  JOIN plan_run_owner o ON o.plan_id=p.id
                  JOIN execution_attempt a ON a.task_id=t.id AND a.attempt_no=t.attempt_no
                 WHERE t.subject_type='flexible-plan' AND t.state='FAILED' AND a.state='FAILED'
                   AND p.state='CONFIRMED' AND t.id>?
                   AND NOT EXISTS (SELECT 1 FROM web_console_plan_archive ar WHERE ar.plan_id=p.id)
                   AND NOT EXISTS (SELECT 1 FROM workflow_execution d WHERE
                       (d.actor=o.actor AND d.request_id='caretaker:' || a.id) OR
                       (d.id=p.id AND (d.request_id LIKE 'caretaker:%' OR d.request_id LIKE 'diagnose-%')))
                 ORDER BY t.id LIMIT 2
                """,caretakerAfter);
        caretakerAfter=failed.isEmpty()?"":(String)failed.getLast().get("task_id");
        for(var source:failed) {
            String task=(String)source.get("task_id"), attempt=(String)source.get("attempt_id"), actor=(String)source.get("actor");
            try {
                var reader=editor.getObject();
                var detail=reader.taskDetail((String)source.get("plan_id"),task);
                if(!detail.attempts().isEmpty()&&detail.attempts().getFirst().id().equals(attempt))
                    workflows.getObject().startCaretaker(task,attempt,actor,reader.diagnosisEvidence(detail,actor));
            } catch(RuntimeException error) {
                System.getLogger(getClass().getName()).log(System.Logger.Level.WARNING,
                        "Manager diagnosis pending for "+task+" ("+error.getClass().getSimpleName()+")");
            }
        }
    }
    @org.springframework.scheduling.annotation.Scheduled(fixedDelayString="${aidev.workflow.worker-poll-ms:5000}")
    public void supply() {
        if(automaticRateLimitRetry)recoverRateLimits();
        if(managerAutomaticDiagnosis) diagnoseFailedTasks();
        var waiting=jdbc.queryForList("""
                SELECT w.id FROM workflow_execution w WHERE w.id>? AND (
                    w.state='WAITING_MEMBER' OR (w.state='FAILED' AND EXISTS (
                        SELECT 1 FROM work_task t JOIN work_task_control_event e ON e.task_id=t.id
                        WHERE t.subject_type='flexible-plan' AND t.subject_id=w.id AND t.state='READY'
                          AND e.action='RETRY_FAILED' AND e.warning_confirmed=1 AND e.created_at_epoch_ms=t.updated_at_epoch_ms
                    ))) ORDER BY w.id LIMIT 20
                """,String.class,memberAfter);
        memberAfter=waiting.isEmpty()?"":waiting.getLast();
        for(var id:waiting)try {
            var runs=workflows.getObject();
            var request=runs.pendingProfile(id);
            if(request==null)runs.assignPending(id);
            else if(selectingProfile.compareAndSet(false,true))Thread.ofVirtual().name("workflow-profile-selection").start(()->{
                try {
                    me.asu.aidev.execution.config.ManagerProfileSelection.Result result=null;
                    String failure=null;
                    try {result=managerProfiles.chooseForWorkflow(id,request.input());}
                    catch(RuntimeException error) {failure=Objects.toString(error.getMessage(),"Manager profile selection failed.");}
                    runs.finishProfile(id,request.version(),result,failure);
                } finally {selectingProfile.set(false);}
            });
        }
        catch(RuntimeException error) {System.getLogger(getClass().getName()).log(System.Logger.Level.WARNING,"Workflow dispatch remains pending for "+id+" ("+error.getClass().getSimpleName()+")");}
        var defaults=settings.current();
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
        var reserved=new HashMap<String,Integer>();
        var registered=workers.online(Instant.now().minus(WorkerLiveness.HEARTBEAT_WINDOW));
        for(var id:ready) {
            me.asu.aidev.work.domain.WorkTask task=null;
            try {
                task=tasks.find(new TaskId(id)).orElseThrow();
                var fixed=runtimeProfiles==null?null:runtimeProfiles.read(task);
                var config=fixed==null?defaults:fixed.settings();
                if(config==null||!config.automaticWorkers())continue;
                if(runtimeProfiles!=null&&!runtimeProfiles.workspaceAvailable(task,false)) {
                    pending.put(id,new Preparation(id,task.subjectId(),"BLOCKED","The working location is unavailable or overlaps an execution still holding its resources."));
                    continue;
                }
                if(!team.canClaim(task)) {
                    pending.put(id,new Preparation(id,task.subjectId(),"WAITING_MEMBER","The selected team member has stopped accepting work. Resume that member to continue."));continue;
                }
                String role=switch(task.skillRef()) {
                    case "software-requirements-analyst" -> "requirements";
                    case "solution-architect" -> "architect";
                    case "implementation-planner" -> "planner";
                    case "general-work" -> "general";
                    case "workspace-work" -> "workspace";
                    case "coding-work-package-executor" -> "coder";
                    case "workflow-stage-reviewer" -> "stage-reviewer";
                    default -> null;
                };
                if(role==null) continue;
                var required=new HashSet<>(task.requiredCapabilities());

                var currentTask=task;
                var compatible=registered.stream()
                        .filter(w -> w.runtimeType().startsWith(config.runtime()+":") && w.supports(required)
                                && (factoryReviewPolicy==null || factoryReviewPolicy.canClaim(currentTask,w))
                                && (stageReviewPolicy==null || stageReviewPolicy.canClaim(currentTask,w))
                                && (nativeRuns==null || nativeRuns.canClaim(currentTask,w))
                                && (runtimeProfiles==null || runtimeProfiles.canClaim(currentTask,w))
                                && (modelConnections==null || modelConnections.canClaim(currentTask,w))).toList();
                boolean spare=compatible.stream().filter(w->w.activeLeases()+reserved.getOrDefault(w.id().value(),0)<w.maxConcurrency())
                        .findFirst().map(w->{reserved.merge(w.id().value(),1,Integer::sum);return true;}).orElse(false);
                if(!spare) {
                    if(required.contains(WorkflowRuns.REQUIRED_CHECKS)&&!config.runtime().equals("openai-agents")) {
                        pending.put(id,new Preparation(id,task.subjectId(),"BLOCKED","Required checks need a compatible Python OpenAI Worker."));continue;
                    }
                    if(required.contains("guarded-command-linux-v1")){
                        pending.put(id,new Preparation(id,task.subjectId(),"BLOCKED","This task requires a connected Linux/WSL Worker with the configured command adapter."));continue;
                    }
                    boolean frozen=required.stream().anyMatch(c->c.startsWith("native-config:"));
                    boolean incompatible=compatible.isEmpty()&&registered.stream().anyMatch(w->w.runtimeType().startsWith(config.runtime()+":")&&w.capabilities().contains(roleCapability(role)));
                    if(frozen&&incompatible) {
                        pending.put(id,new Preparation(id,task.subjectId(),"BLOCKED","此后继任务固定到原生 Worker 配置和交接存储；当前在线配置不匹配。恢复相同 Worker 配置，或明确创建新计划；不会自动切换模型。"));continue;
                    }
                    boolean commandRequired=required.contains("guarded-command-v1");
                    String launchRole=Set.of("general","workspace").contains(role)?"task":role;
                    var result=provisioner.requestWorkflow(task.repositoryRef(),config.runtime(),launchRole,
                            task.requiredCapabilities().contains(me.asu.aidev.execution.guard.GuardContract.CAPABILITY),commandRequired,fixed);
                    pending.put(id,new Preparation(id,task.subjectId(),result.starting()?"STARTING":"BLOCKED",result.message()));
                } else pending.remove(id);
            } catch(RuntimeException error) {
                if(task!=null) pending.put(id,new Preparation(id,task.subjectId(),"BLOCKED","Worker preparation failed; check the Agent factory status."));
                System.getLogger(getClass().getName()).log(System.Logger.Level.WARNING,"Confirmed workflow Worker preparation remains pending");
            }
        }
    }

    private void recoverRateLimits() {
        var candidates=jdbc.queryForList("""
                SELECT t.id AS task_id,t.subject_id AS plan_id,a.id AS attempt_id
                  FROM work_task t JOIN workflow_execution w ON w.id=t.subject_id
                  JOIN execution_attempt a ON a.task_id=t.id AND a.attempt_no=t.attempt_no
                 WHERE t.subject_type='flexible-plan' AND t.state='FAILED' AND t.skill_ref='general-work'
                   AND w.state IN ('RUNNING','FAILED') AND a.state='FAILED' AND a.attempt_no=1
                   AND a.termination_reason='MODEL_API_RATE_LIMIT' AND a.finished_at_epoch_ms<=? AND t.id>?
                 ORDER BY t.id LIMIT 10
                """,System.currentTimeMillis()-60_000,recoveryAfter);
        recoveryAfter=candidates.isEmpty()?"":(String)candidates.getLast().get("task_id");
        for(var row:candidates)try {
            workflows.getObject().recoverModelRateLimit((String)row.get("plan_id"),(String)row.get("task_id"),(String)row.get("attempt_id"));
        } catch(RuntimeException error) {
            System.getLogger(getClass().getName()).log(System.Logger.Level.WARNING,"Automatic rate-limit recovery remains pending.");
        }
    }
    private String roleCapability(String role) {
        return switch(role) {case "general"->"general-work";case "workspace"->"workspace-work";case "requirements"->"requirement-analysis";case "architect"->"solution-architecture";case "planner"->"implementation-planning";case "coder"->"coding";default->"workflow-review";};
    }
}
