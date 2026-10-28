package me.asu.aidev.workflow.application;

import java.time.Instant;
import java.util.*;
import me.asu.aidev.authority.*;
import me.asu.aidev.execution.config.ConfigurationContract.*;
import me.asu.aidev.execution.domain.CompletionReport;
import me.asu.aidev.execution.domain.CompletionVerdict;
import me.asu.aidev.execution.port.ExecutionAttemptRepository;
import me.asu.aidev.work.application.CreateWorkTaskCommand;
import me.asu.aidev.work.application.WorkTaskService;
import me.asu.aidev.work.domain.*;
import me.asu.aidev.work.port.WorkTaskRepository;
import me.asu.aidev.workflow.domain.*;
import me.asu.aidev.workflow.jdbc.JdbcConfiguredPlanHistoryRepository;
import me.asu.aidev.workflow.port.WorkflowPlanRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Owns confirmation and completion of factory execution inside the original Plan aggregate. */
@Service
public class FactoryPlanLifecycle {
    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.beans.factory.ObjectProvider<WorkflowGitDeliveryService> gitDelivery;

    public record Draft(WorkflowPlanSpec spec, String state, long version, String reason) {}
    public record View(Draft originalDraft, String confirmedBy, long confirmedAt) {}
    public record BoundTask(WorkTask task, String stepKey) {}
    public record Reconcile(String planId, String taskId, long expectedPlanVersion, String reason) {}
    private final AuthorityRepository repo;
    private final AuthorityBoundary boundary;
    private final ManagedPlanAdmission manager;
    private final BusinessActionPermissions permissions;
    private final AuthorityGrantService grants;
    private final WorkflowPlanRepository plans;
    private final WorkTaskRepository tasks;
    private final JdbcConfiguredPlanHistoryRepository history;
    @org.springframework.beans.factory.annotation.Autowired private WorkTaskService taskService;
    @org.springframework.beans.factory.annotation.Autowired private ExecutionAttemptRepository attempts;
    @org.springframework.beans.factory.annotation.Autowired private me.asu.aidev.output.jdbc.JdbcTaskOutputRepository taskOutputs;

    public FactoryPlanLifecycle(AuthorityRepository repo, AuthorityBoundary boundary, ManagedPlanAdmission manager,
            BusinessActionPermissions permissions, AuthorityGrantService grants, WorkflowPlanRepository plans,
            WorkTaskRepository tasks, JdbcConfiguredPlanHistoryRepository history) {
        this.repo=repo; this.boundary=boundary; this.manager=manager; this.permissions=permissions;
        this.grants=grants; this.plans=plans; this.tasks=tasks; this.history=history;
    }

    public View view(String planId) {
        return repo.jdbc().query("SELECT original_draft_json,confirmed_by,confirmed_ms FROM workflow_factory_plan WHERE plan_id=?",
                (r,n) -> new View(repo.decode(r.getString(1),Draft.class),r.getString(2),r.getLong(3)),planId)
                .stream().findFirst().orElse(null);
    }
    public List<BoundTask> currentTasks(String planId) {
        // Release the binding query's connection before looking up tasks (SQLite may use a one-connection pool).
        var bindings=repo.jdbc().queryForList("SELECT task_id,step_key FROM workflow_factory_task WHERE plan_id=? AND current_task=TRUE ORDER BY created_ms,task_id",planId);
        return bindings.stream().map(row -> new BoundTask(tasks.find(new TaskId((String)row.get("task_id"))).orElseThrow(),
                (String)row.get("step_key"))).toList();
    }

    @Transactional
    public void confirm(Intent intent, String owner, PreparedConfigRef config, Method method) {
        boundary.lock();
        WorkflowPlan plan=plans.find(intent.planId()).orElseThrow();
        String actor=boundary.optionalPrincipal()==null?boundary.human():"personal:"+boundary.optionalPrincipal().worker();
        Intent accepted=fixed(intent,config);
        var original=repo.jdbc().queryForList("SELECT confirmed_intent_json FROM workflow_factory_plan WHERE plan_id=?",plan.id());
        if (!original.isEmpty()) {
            if (plan.state()!=WorkflowPlan.State.CONFIRMED || !sameWork(repo.decode((String)original.getFirst().get("confirmed_intent_json"),Intent.class),accepted))
                throw new IllegalStateException("SOURCE_CHANGED: the confirmed factory Plan cannot be replaced or rerun after completion");
            return;
        }
        if (plan.state()!=WorkflowPlan.State.DRAFT || !tasks.findBySubject(WorkflowPlanService.SUBJECT,plan.id()).isEmpty())
            throw new IllegalStateException("A clean draft is required for factory Plan confirmation");
        if (!plan.createdBy().equals(repo.actor(owner))) throw new SecurityException("Owned draft required");
        var principal=boundary.optionalPrincipal();
        if (principal==null) {
            grants.owns(owner,actor); grants.human(actor,"A05");
        } else {
            if (!principal.owner().equals(owner) || !principal.enabled() || !manager.inScope(plan.id()))
                throw new SecurityException("Fresh managed Plan admission required");
            String request=UUID.nameUUIDFromBytes(("factory-confirm:"+plan.id()+":"+plan.version()).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
            permissions.confirmFactoryPlan(principal,plan.id(),request);
        }
        persistConfirmation(plan,accepted,config,method,actor);
        repo.jdbc().update("INSERT INTO factory_plan_review(plan_id,state,updated_at_epoch_ms) VALUES(?,'WAITING',?)",plan.id(),System.currentTimeMillis());
    }

    private void persistConfirmation(WorkflowPlan plan, Intent intent, PreparedConfigRef config, Method method, String actor) {
        String title=plan.spec().goal().lines().findFirst().orElse("Professional work");
        String goal=title+"\n\n"+intent.instruction();
        if (goal.length()>16384) goal=title;
        var step=new WorkflowPlanSpec.Step("execute",Set.of(method.roleCapability()),Set.of(),method.primarySkill(),
                intent.instruction(),intent.repositoryRef(),intent.baseRevision(),Set.of());
        var spec=new WorkflowPlanSpec(goal,plan.spec().source(),List.of(step));
        long now=System.currentTimeMillis();
        repo.jdbc().update("INSERT INTO workflow_factory_plan VALUES(?,?,?,?,?)",plan.id(),
                repo.encode(new Draft(plan.spec(),plan.state().name(),plan.version(),plan.reason())),repo.encode(intent),actor,now);
        if (repo.jdbc().update("""
                UPDATE flexible_workflow_plan SET state='CONFIRMED',spec_json=?,version=version+1,updated_by=?,reason=?,updated_at_epoch_ms=?
                 WHERE id=? AND state='DRAFT' AND version=?
                """,repo.encode(spec),actor,"Confirmed actual factory execution scope; original draft retained",now,plan.id(),plan.version())!=1)
            throw new IllegalStateException("Plan changed during factory confirmation");
        var managed=repo.jdbc().queryForList("SELECT spec_digest FROM authority_managed_plan WHERE plan_id=?",plan.id());
        if (!managed.isEmpty() && repo.jdbc().update("UPDATE authority_managed_plan SET spec_digest=?,version=? WHERE plan_id=? AND spec_digest=?",
                AuthorityRepository.hash(repo.encode(spec)),plan.version()+1,plan.id(),AuthorityRepository.hash(repo.encode(plan.spec())))!=1)
            throw new IllegalStateException("Managed draft changed during confirmation");
        if (gitDelivery!=null) gitDelivery.getObject().capture(plans.find(plan.id()).orElseThrow());
        repo.audit("PLAN:"+plan.id(),null,null,actor,"FACTORY_PLAN_CONFIRMED",intent.intentId()+":"+intent.intentVersion()+"; config="+config.digest());
    }

    @Transactional
    public void bind(Intent intent, String taskId, String resultKind) {
        boundary.lock();
        WorkflowPlan plan=plans.find(intent.planId()).orElseThrow();
        if (plan.state()!=WorkflowPlan.State.CONFIRMED || view(plan.id())==null) throw new IllegalStateException("Confirmed factory Plan required");
        Review review=review(plan.id());
        if (review!=null && "PENDING".equals(review.state()) && !Objects.equals(review.sourceTaskId(),taskId))
            throw new IllegalStateException("A review is pending for the current factory task");
        for (var previous:currentTasks(plan.id())) {
            if (previous.task().id().value().equals(taskId)) return;
            if (!Set.of(TaskState.COMPLETED,TaskState.FAILED,TaskState.CANCELLED).contains(previous.task().state()))
                throw new IllegalStateException("Reconcile the current factory task before submitting a replacement");
        }
        var task=tasks.find(new TaskId(taskId)).orElseThrow();
        if (!task.subjectType().equals("configured-plan") || !task.subjectId().equals(plan.id())) throw new SecurityException("Exact factory task required");
        repo.jdbc().update("UPDATE workflow_factory_task SET current_task=FALSE WHERE plan_id=?",plan.id());
        repo.jdbc().update("INSERT INTO workflow_factory_task VALUES(?,?,?, ?,TRUE,?)",taskId,plan.id(),"execute",resultKind,System.currentTimeMillis());
        repo.audit("PLAN:"+plan.id(),null,null,"factory","FACTORY_PLAN_TASK_BOUND",taskId);
    }

    @Transactional
    public void completed(WorkTask task, CompletionReport report) {
        if (!task.subjectType().equals("configured-plan")) return;
        boundary.lock();
        WorkflowPlan plan=plans.find(task.subjectId()).orElseThrow();
        if (plan.state()!=WorkflowPlan.State.CONFIRMED || view(plan.id())==null) return;
        var current=repo.jdbc().queryForList("SELECT task_id,result_kind FROM workflow_factory_task WHERE plan_id=? AND current_task=TRUE",plan.id());
        if (current.isEmpty() || current.stream().noneMatch(r -> task.id().value().equals(r.get("task_id")))) return;
        var recorded=history.read(plan.id());
        for (var binding:current) {
            var result=recorded.tasks().stream().filter(t -> t.id().equals(binding.get("task_id"))).findFirst().orElseThrow();
            if (!successful(result,String.valueOf(binding.get("result_kind")))) return;
        }
        if (reviewRequired(plan.id())) return;
        if (!plans.transition(plan.id(),plan.version(),WorkflowPlan.State.CONFIRMED,WorkflowPlan.State.COMPLETED,
                "factory","Recorded factory work completed: "+task.id().value(),Instant.now()))
            throw new IllegalStateException("Plan changed during completion");
        repo.jdbc().update("UPDATE authority_managed_plan SET version=? WHERE plan_id=?",plan.version()+1,plan.id());
        if (gitDelivery!=null) gitDelivery.getObject().enqueue(plan.id());
        repo.audit("PLAN:"+plan.id(),null,null,"factory","FACTORY_PLAN_COMPLETED",task.id().value());
    }

    /** Historical plans have no row until their owner explicitly requests a review. */
    public boolean reviewRequired(String planId) {
        return repo.jdbc().queryForObject("SELECT COUNT(*) FROM factory_plan_review WHERE plan_id=? AND state<>'PASSED'",Long.class,planId)>0;
    }

    public record Review(String state, String sourceTaskId, String reviewTaskId) {}
    public Review review(String planId) {
        return repo.jdbc().query("SELECT state,source_task_id,review_task_id FROM factory_plan_review WHERE plan_id=?",
                (r,n) -> new Review(r.getString(1),r.getString(2),r.getString(3)),planId).stream().findFirst().orElse(null);
    }

    @Transactional
    public Review requestReview(String planId, long version, String actor) {
        boundary.lock();
        WorkflowPlan plan=plans.find(planId).orElseThrow();
        if (!Objects.equals(actor,boundary.human()) || !Objects.equals(actor,plan.createdBy()))
            throw new SecurityException("The authenticated Plan owner is required");
        grants.human(actor,"A05");
        if (plan.version()!=version || !Set.of(WorkflowPlan.State.CONFIRMED,WorkflowPlan.State.COMPLETED).contains(plan.state()) || view(planId)==null)
            throw new IllegalStateException("Factory Plan state or version changed");
        if (repo.jdbc().queryForObject("SELECT COUNT(*) FROM web_console_plan_archive WHERE plan_id=?",Long.class,planId)>0)
            throw new IllegalStateException("Archived plans cannot start a review");
        if (repo.jdbc().queryForObject("SELECT COUNT(*) FROM plan_git_delivery WHERE plan_id=? AND state='MERGING'",Long.class,planId)>0)
            throw new IllegalStateException("A delivery is in progress; wait for its recorded outcome");
        Review existing=review(planId);
        if (existing!=null && "PASSED".equals(existing.state())) throw new IllegalStateException("Review already passed");
        if (existing!=null && "PENDING".equals(existing.state())) {
            var previous=tasks.find(new TaskId(existing.reviewTaskId())).orElseThrow();
            if (!Set.of(TaskState.FAILED,TaskState.CANCELLED).contains(previous.state()))
                throw new IllegalStateException("Review is already pending");
        }
        var bindings=repo.jdbc().queryForList("SELECT task_id,result_kind FROM workflow_factory_task WHERE plan_id=? AND current_task=TRUE",planId);
        if (bindings.size()!=1) throw new IllegalStateException("One current factory task is required");
        String source=String.valueOf(bindings.getFirst().get("task_id"));
        var recorded=history.read(planId).tasks().stream().filter(t -> t.id().equals(source)).findFirst().orElseThrow();
        if (!successful(recorded,String.valueOf(bindings.getFirst().get("result_kind")))) throw new IllegalStateException("A successful recorded result is required");
        var input=attempts.findCompletedInput(new TaskId(source)).orElseThrow();
        var step=plan.spec().steps().getFirst();
        int next=tasks.findBySubject("flexible-plan",planId).stream()
                .map(task -> WorkflowTaskPosition.parse(plan,task.id())).flatMap(Optional::stream)
                .filter(WorkflowTaskPosition::review).mapToInt(WorkflowTaskPosition::round).max().orElse(0)+1;
        String reviewId=planId+"~execute~review-"+next;
        boundary.changing("PLAN:"+planId);
        var capabilities=new LinkedHashSet<String>(); capabilities.add("workflow-review");
        if (taskOutputs.completed(source).map(bundle -> bundle.files().stream()
                .anyMatch(file -> Set.of("DOCUMENT","ATTACHMENT").contains(file.kind()))).orElse(false)) capabilities.add("task-outputs-v1");
        taskService.createIfAbsent(new CreateWorkTaskCommand(new TaskId(reviewId),TaskType.EXTERNAL_WORK,"flexible-plan",planId,
                Set.copyOf(capabilities),"workflow-stage-reviewer",
                "Independently review the recorded output against the plan goal and instruction.\nGoal: "+plan.spec().goal()
                        +"\nInstruction: "+step.instruction()+"\nEnd with AIW_VERDICT: PASS or AIW_VERDICT: REPAIR_REQUIRED.",
                step.repositoryRef(),input.resultRevision()==null?step.baseRevision():input.resultRevision(),TaskPriority.NORMAL,Set.of(new TaskId(source))));
        boundary.allowHumanTask(reviewId);
        repo.jdbc().update("INSERT INTO factory_plan_review(plan_id,source_task_id,review_task_id,state,updated_at_epoch_ms) VALUES(?,?,?,'PENDING',?) ON CONFLICT(plan_id) DO UPDATE SET source_task_id=excluded.source_task_id,review_task_id=excluded.review_task_id,state=excluded.state,updated_at_epoch_ms=excluded.updated_at_epoch_ms",
                planId,source,reviewId,System.currentTimeMillis());
        repo.audit("PLAN:"+planId,null,null,actor,"FACTORY_REVIEW_REQUESTED",source+":"+reviewId);
        return review(planId);
    }

    @Transactional
    public boolean completeReview(WorkTask task, CompletionReport report) {
        boundary.lock();
        Review current=review(task.subjectId());
        if (current==null || !Objects.equals(current.reviewTaskId(),task.id().value()) || !"PENDING".equals(current.state())) return false;
        if (report==null) throw new IllegalStateException("Independent review requires a verdict");
        var bindings=repo.jdbc().queryForList("SELECT task_id FROM workflow_factory_task WHERE plan_id=? AND current_task=TRUE",String.class,task.subjectId());
        if (bindings.size()!=1 || !Objects.equals(bindings.getFirst(),current.sourceTaskId()))
            throw new IllegalStateException("Reviewed factory result is no longer current");
        String state=report.verdict()==CompletionVerdict.PASS?"PASSED":"CHANGES_REQUIRED";
        repo.jdbc().update("UPDATE factory_plan_review SET state=?,updated_at_epoch_ms=? WHERE plan_id=? AND review_task_id=? AND state='PENDING'",
                state,System.currentTimeMillis(),task.subjectId(),task.id().value());
        if ("PASSED".equals(state)) {
            WorkflowPlan plan=plans.find(task.subjectId()).orElseThrow();
            if (plan.state()==WorkflowPlan.State.CONFIRMED) {
                if (!plans.transition(plan.id(),plan.version(),WorkflowPlan.State.CONFIRMED,WorkflowPlan.State.COMPLETED,"factory","Independent review passed: "+task.id().value(),Instant.now()))
                    throw new IllegalStateException("Plan changed during reviewed completion");
                repo.jdbc().update("UPDATE authority_managed_plan SET version=? WHERE plan_id=?",plan.version()+1,plan.id());
                if (gitDelivery!=null) gitDelivery.getObject().enqueue(plan.id());
            }
        }
        repo.audit("PLAN:"+task.subjectId(),null,null,"factory","FACTORY_REVIEW_"+state,task.id().value());
        return true;
    }

    private boolean successful(JdbcConfiguredPlanHistoryRepository.Task task, String kind) {
        if (!history.successful(task)) return false;
        var result=task.attempts().getFirst();
        return !"WORKSPACE".equals(kind) || result.resultRevision()!=null
                && !result.resultRevision().equals(result.baseRevision())
                && result.changedFiles()!=null && !result.changedFiles().isBlank();
    }

    /** Explicit owner recovery adopts a real completed result; never prepares or replays a task. */
    @Transactional
    public WorkflowPlan reconcile(Reconcile input) {
        boundary.lock();
        String actor=Objects.requireNonNull(boundary.human(),"Authenticated Plan owner required");
        grants.human(actor,"A05");
        if (input.reason()==null || input.reason().isBlank() || input.reason().length()>2000) throw new IllegalArgumentException("Recovery reason required");
        WorkflowPlan plan=plans.find(input.planId()).orElseThrow();
        if (!actor.equals(plan.createdBy())) throw new SecurityException("Owned Plan required");
        if (plan.state()==WorkflowPlan.State.COMPLETED && currentTasks(plan.id()).stream().anyMatch(t -> t.task().id().value().equals(input.taskId()))) return plan;
        if (plan.version()!=input.expectedPlanVersion()) throw new IllegalStateException("Plan version changed");
        if (repo.jdbc().queryForObject("SELECT COUNT(*) FROM work_task WHERE subject_id=? AND state NOT IN ('COMPLETED','FAILED','CANCELLED')",Long.class,plan.id())>0)
            throw new IllegalStateException("Active or waiting work prevents historical recovery");
        var rows=repo.jdbc().queryForList("SELECT * FROM execution_preparation WHERE task_id=? AND state='READY'",input.taskId());
        if (rows.size()!=1) throw new IllegalArgumentException("One recorded factory preparation required");
        var original=rows.getFirst();
        Intent intent=repo.decode((String)original.get("intent_json"),Intent.class);
        PreparedConfigRef config=repo.decode((String)original.get("config_json"),PreparedConfigRef.class);
        if (!intent.planId().equals(plan.id()) || !repo.actor((String)original.get("owner_id")).equals(actor)) throw new SecurityException("Preparation owner differs");
        var selected=history.read(plan.id()).tasks().stream().filter(t -> t.id().equals(input.taskId())).findFirst().orElseThrow();
        if (!successful(selected,config.resultKind())) throw new IllegalStateException("Actual successful result required for completed Plan recovery");
        if (view(plan.id())==null) {
            var configuration=repo.jdbc().queryForObject("SELECT content_json FROM execution_configuration WHERE id=? AND revision=? AND digest=?",
                    String.class,config.id(),config.revision(),config.digest());
            Content content=repo.decode(configuration,Content.class);
            persistConfirmation(plan,fixed(intent,config),config,content.candidate().method(),actor);
        }
        bind(intent,input.taskId(),config.resultKind());
        completed(tasks.find(new TaskId(input.taskId())).orElseThrow(),null);
        repo.audit("PLAN:"+plan.id(),null,null,actor,"FACTORY_PLAN_RECOVERED",input.reason());
        return plans.find(plan.id()).orElseThrow();
    }

    private Intent fixed(Intent i,PreparedConfigRef config) {
        return new Intent(i.intentId(),i.intentVersion(),0,i.planId(),i.policyRevision(),i.repositoryRef(),i.baseRevision(),
                i.instruction(),i.dependencies(),i.runtime(),i.aiProfileId(),null,config,null);
    }
    private boolean sameWork(Intent a,Intent b) {
        return a.planId().equals(b.planId()) && a.repositoryRef().equals(b.repositoryRef()) && a.baseRevision().equals(b.baseRevision())
                && a.instruction().equals(b.instruction()) && a.dependencies().equals(b.dependencies()) && a.runtime().equals(b.runtime())
                && a.aiProfileId().equals(b.aiProfileId()) && a.configRef().equals(b.configRef());
    }
}
