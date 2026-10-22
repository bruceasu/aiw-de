package me.asu.aidev.authority;

import java.util.*;
import me.asu.aidev.approval.application.ApprovalService;
import me.asu.aidev.approval.domain.ApprovalRequestId;
import me.asu.aidev.development.application.EngineeringGateService;
import me.asu.aidev.execution.port.ExecutionAttemptRepository;
import me.asu.aidev.fleet.application.FleetAdmissionBudgetService;
import me.asu.aidev.output.application.TaskOutputService;
import me.asu.aidev.output.jdbc.JdbcTaskOutputRepository;



import me.asu.aidev.work.domain.*;
import me.asu.aidev.worker.application.WorkerService;
import me.asu.aidev.worker.domain.WorkerId;
import me.asu.aidev.workflow.application.*;
import me.asu.aidev.workflow.domain.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Each adapter preserves the original application service's business authority. */
@Service
public class AuthorityActions implements AuthorityTargets {
    private org.springframework.beans.factory.ObjectProvider<me.asu.aidev.execution.guard.ExecutionGuard> executionGuard;
    private org.springframework.beans.factory.ObjectProvider<me.asu.aidev.execution.guard.ExecutionLedger> executionLedger;
    @org.springframework.beans.factory.annotation.Autowired
    public void setExecutionGuard(org.springframework.beans.factory.ObjectProvider<me.asu.aidev.execution.guard.ExecutionGuard> guard,
            org.springframework.beans.factory.ObjectProvider<me.asu.aidev.execution.guard.ExecutionLedger> ledger) { this.executionGuard=guard;this.executionLedger=ledger; }
    private final JdbcTemplate jdbc;
    private final WorkflowPlanService plans;
    private final WorkflowCancellationService cancellation;
    private final WorkflowPlanArchiveService archives;
    private final ApprovalService approvals;
    private final EngineeringGateService gates;
    private final WorkerService workers;
    private final FleetAdmissionBudgetService fleet;
    private final TaskOutputService outputs;
    private final JdbcTaskOutputRepository outputRepo;
    private final AuthorityRepository repo;
    private final AuthorityAdmission admission;
    private final ManagedPlanAdmission coordinator;
    private final ExecutionAttemptRepository attempts;
    private org.springframework.beans.factory.ObjectProvider<AuthorityGrantService> grants;
    @org.springframework.beans.factory.annotation.Autowired
    public void setGrants(org.springframework.beans.factory.ObjectProvider<AuthorityGrantService> grants) { this.grants=grants; }
    private org.springframework.beans.factory.ObjectProvider<AuthorityBudgets> budgets;
    @org.springframework.beans.factory.annotation.Autowired
    public void setBudgets(org.springframework.beans.factory.ObjectProvider<AuthorityBudgets> budgets) { this.budgets=budgets; }
    private org.springframework.beans.factory.ObjectProvider<AuthorityTaskPolicy> taskPolicy;
    @org.springframework.beans.factory.annotation.Autowired
    public void setTaskPolicy(org.springframework.beans.factory.ObjectProvider<AuthorityTaskPolicy> policy) { this.taskPolicy=policy; }
    private me.asu.aidev.ai.application.AiProfileCatalog aiProfiles;
    @org.springframework.beans.factory.annotation.Autowired
    public void setAiProfiles(me.asu.aidev.ai.application.AiProfileCatalog profiles) { this.aiProfiles=profiles; }
    public AuthorityActions(JdbcTemplate jdbc,WorkflowPlanService plans,WorkflowCancellationService cancellation,
            WorkflowPlanArchiveService archives,ApprovalService approvals,EngineeringGateService gates,
            WorkerService workers,FleetAdmissionBudgetService fleet,TaskOutputService outputs,JdbcTaskOutputRepository outputRepo,
            AuthorityRepository repo,AuthorityAdmission admission,ManagedPlanAdmission coordinator,
            ExecutionAttemptRepository attempts) {
        this.jdbc=jdbc;this.plans=plans;this.cancellation=cancellation;this.archives=archives;this.approvals=approvals;
        this.gates=gates;this.workers=workers;this.fleet=fleet;this.outputs=outputs;this.outputRepo=outputRepo;
        this.repo=repo;this.admission=admission;this.coordinator=coordinator;
        this.attempts=attempts;
    }
    private boolean stepAction(ActionScope rule) { return Set.of(ActionScope.Action.A01,ActionScope.Action.A02,ActionScope.Action.A03,ActionScope.Action.A04).contains(rule.action()); }
    private boolean planAction(ActionScope rule) {
        return switch(rule.action()) { case A11_APPROVE,A11_REJECT,A12_CREATE,A13,A14,A15,A18_BUDGET -> false; default -> true; };
    }
    @Override public void validate(ActionScope rule) {
        if(stepAction(rule)!=(rule.step()!=null)) throw new IllegalArgumentException("Only A01-A04 require an exact Plan step");
        Set<String> allowed=new HashSet<>(Set.of("reason"));
        switch(rule.action()) {
            case A01 -> allowed.addAll(Set.of("scope","modelGrantId"));
            case A02 -> allowed.addAll(Set.of("profiles","catalogVersion","role"));
            case A03 -> allowed.addAll(Set.of("taskId","failureCategory","sideEffectsConfirmed","minimumIntervalSeconds"));
            case A04 -> allowed.add("warningConfirmed");
            case A06 -> allowed.add("targetStep");
            case A12_CREATE -> allowed.addAll(Set.of("reviewTaskId","targetPath","sourceHash"));
            case A12_EXECUTE -> allowed.addAll(Set.of("fileId","reviewTaskId","targetPath","sourceHash"));
            case A13 -> allowed.addAll(Set.of("maxParallelism","minimum","maximum","allowUnlimited","minimumIntervalSeconds"));
            case A18_BUDGET -> allowed.addAll(Set.of("dimension","loopId","amount","cumulativeCeiling"));
            default -> { }
        }
        if(!allowed.containsAll(rule.parameters().keySet())) throw new IllegalArgumentException("Unknown parameters cannot create action authority");
        if(rule.effect().equals("ALLOW")) {
            required(rule,"reason");
            List<String> mandatory=switch(rule.action()) {
                case A01 -> List.of("scope");
                case A02 -> List.of("profiles","catalogVersion","role");
                case A03 -> List.of("taskId","failureCategory","sideEffectsConfirmed");
                case A04 -> List.of("warningConfirmed");
                case A06 -> List.of("targetStep");
                case A12_CREATE -> List.of("reviewTaskId","targetPath","sourceHash");
                case A12_EXECUTE -> List.of("fileId","reviewTaskId","targetPath","sourceHash");
                case A13 -> List.of("maxParallelism","minimum","maximum","minimumIntervalSeconds");
                case A18_BUDGET -> List.of("dimension","amount","cumulativeCeiling");
                default -> List.of();
            };
            mandatory.forEach(field->required(rule,field));
        }
        if(rule.action()==ActionScope.Action.A13 && !rule.targetId().equals("GLOBAL")) throw new IllegalArgumentException("Fleet action must explicitly target GLOBAL");
    }
    private String required(ActionScope rule,String field) {
        String value=rule.parameters().get(field);
        if(value==null || value.isBlank()) throw new IllegalArgumentException("Missing action parameter: "+field);
        return value;
    }
    @Override public String key(ActionScope rule) {
        return switch(rule.action()) {
            case A11_APPROVE,A11_REJECT -> "APPROVAL:"+approvals.find(new ApprovalRequestId(rule.targetId())).orElseThrow().sourceTaskId().value();
            case A12_CREATE -> publicationKey(rule.targetId(),required(rule,"targetPath"));
            case A12_EXECUTE -> {
                var publication=outputRepo.publication(rule.targetId()).orElseThrow(()->new IllegalArgumentException("Verified publication plan required"));
                yield publicationKey(publication.fileId(),publication.targetPath());
            }
            case A13 -> "GLOBAL:FLEET";
            case A18_BUDGET -> { executionGuard.getObject().requirePolicyActor(rule.targetId());yield "PLAN:"+rule.targetId(); }
            case A14,A15 -> "WORKER:"+workers.get(new WorkerId(rule.targetId())).id().value();
            default -> {
                var plan=plans.get(rule.targetId());
                if(rule.step()!=null && rule.step()>=plan.spec().steps().size()) throw new IllegalArgumentException("Plan step not found");
                yield "PLAN:"+plan.id()+(rule.step()==null?"":":STEP:"+rule.step());
            }
        };
    }
    private String publicationKey(String file,String targetPath) {
        return outputs.publicationTarget(file,targetPath);
    }
    @Override public void admit(ActionScope rule) {
        if(planAction(rule)) admission.plan(rule.targetId());
        if(planAction(rule) && outputRepo.publication(rule.targetId()).isPresent()) {
            var publication=outputRepo.publication(rule.targetId()).orElseThrow();
            jdbc.update("INSERT INTO authority_publication_plan VALUES(?,?) ON CONFLICT(plan_id) DO NOTHING",
                    rule.targetId(),publicationKey(publication.fileId(),publication.targetPath()));
        }
    }
    @Override public long baseline(ActionScope rule) {
        if(rule.action()!=ActionScope.Action.A12_CREATE && rule.action()!=ActionScope.Action.A12_EXECUTE) return admission.baseline(rule);
        String target=key(rule);long count=0;
        for(var publication:jdbc.queryForList("SELECT plan_id,file_id,target_path FROM task_output_publication")) {
            if(!publicationKey(String.valueOf(publication.get("file_id")),String.valueOf(publication.get("target_path"))).equals(target)) continue;
            if(rule.action()==ActionScope.Action.A12_CREATE) count++;
            else count+=jdbc.queryForObject("SELECT COALESCE(SUM(attempt_no),0) FROM work_task WHERE subject_type='flexible-plan' AND subject_id=? AND skill_ref<>'workflow-stage-reviewer'",Long.class,publication.get("plan_id"));
        }
        return count;
    }
    private Map<String,Object> taskView(WorkTask task) {
        var row=new LinkedHashMap<String,Object>();
        row.put("id",task.id().value());row.put("state",task.state().name());row.put("version",task.version());
        row.put("nextAttempt",jdbc.queryForObject("SELECT attempt_no+1 FROM work_task WHERE id=?",Long.class,task.id().value()));
        row.put("instructionRevision",task.instructionRevision());row.put("instruction",task.instruction());row.put("skill",task.skillRef());
        attempts.findCompletionReport(task.id()).ifPresent(r->row.put("report",Map.of("verdict",r.verdict().name(),"summary",r.summary())));
        attempts.findCompletedInput(task.id()).ifPresent(r->row.put("resultRevision",Objects.toString(r.resultRevision(),"")));
        return row;
    }
    @Override public Map<String,Object> snapshot(ActionScope rule) {
        var row=new LinkedHashMap<String,Object>();row.put("target",key(rule));row.put("action",rule.action().name());
        if(planAction(rule)) {
            // Keep the existing secretary -> sorted tasks -> Plan order after the delegation lock.
            jdbc.update("UPDATE authority_lock SET version=version WHERE id=1");
            for(var task:plans.steps(rule.targetId()).stream().sorted(Comparator.comparing(t->t.id().value())).toList())
                jdbc.update("UPDATE work_task SET version=version WHERE id=?",task.id().value());
            jdbc.update("UPDATE flexible_workflow_plan SET version=version WHERE id=?",rule.targetId());
            var plan=plans.get(rule.targetId());
            row.put("state",plan.state().name());row.put("version",plan.version());row.put("spec",plan.spec());
            row.put("tasks",plans.steps(plan.id()).stream().map(this::taskView).toList());
            row.put("archived",archives.archived(plan.id()));row.put("deleteAfter",archives.deleteAfter(plan.id()).map(Object::toString).orElse(""));
            if(rule.step()!=null) row.put("rounds",plans.steps(plan.id()).stream()
                    .filter(t->!t.skillRef().equals("workflow-stage-reviewer"))
                    .map(t->WorkflowTaskPosition.parse(plan,t.id())).filter(Optional::isPresent).map(Optional::get)
                    .filter(p->p.step()==rule.step()).count());
            if(outputRepo.publication(plan.id()).isPresent()) {
                row.put("publication",publicationView(plan.id()));

            }
        } else switch(rule.action()) {
            case A18_BUDGET -> {
                var policy=executionGuard.getObject().policyRow(rule.targetId());
                row.put("version",policy.get("revision"));row.put("policyDigest",policy.get("digest"));
                row.put("state",AuthorityRepository.flag(policy.get("revoked"))?"REVOKED":AuthorityRepository.flag(policy.get("paused"))?"PAUSED":"ACTIVE");
                row.put("budgets",executionLedger.getObject().snapshot(rule.targetId()));
            }
            case A11_APPROVE,A11_REJECT -> {
                jdbc.update("UPDATE approval_request SET status=status WHERE id=?",rule.targetId());
                var approval=approvals.find(new ApprovalRequestId(rule.targetId())).orElseThrow();
                var gate=gates.find(approval.sourceTaskId()).orElseThrow();
                row.put("state",approval.status().name());row.put("sourceTask",approval.sourceTaskId().value());
                row.put("resultRevision",approval.resultRevision());row.put("gate",gate.status().name());
                row.put("review",taskView(task(gate.reviewTaskId().value())));row.put("verification",taskView(task(gate.verificationTaskId().value())));
                row.put("source",taskView(task(approval.sourceTaskId().value())));
            }
            case A12_CREATE -> {
                row.putAll(fileView(rule.targetId(),required(rule,"reviewTaskId"),required(rule,"targetPath")));
            }
            case A13 -> {
                jdbc.update("UPDATE fleet_admission_budget SET max_parallelism=max_parallelism WHERE id='GLOBAL'");
                var current=fleet.snapshot();row.put("state","GLOBAL");row.put("maxParallelism",current.maxParallelism());
                row.put("activeLeases",current.activeLeases());row.put("updated",current.updatedAt().toString());
            }
            case A14,A15 -> {
                jdbc.update("UPDATE worker SET status=status WHERE id=?",rule.targetId());
                var worker=workers.get(new WorkerId(rule.targetId()));row.put("state",worker.status().name());
                row.put("activeLeases",worker.activeLeases());row.put("runtime",worker.runtimeType());
                row.put("registered",worker.registeredAt().toString());
            }
            default -> throw new IllegalArgumentException("Unsupported action target");
        }
        return row;
    }
    private WorkTask task(String id) {
        // Use the existing public task repository through a narrow helper, not an HTTP adapter.
        return taskRepository.find(new TaskId(id)).orElseThrow();
    }
    private me.asu.aidev.work.port.WorkTaskRepository taskRepository;
    @org.springframework.beans.factory.annotation.Autowired
    public void setTaskRepository(me.asu.aidev.work.port.WorkTaskRepository tasks) { this.taskRepository=tasks; }
    private Map<String,Object> fileView(String file,String review,String target) {
        var source=outputs.file(file);var bundle=outputRepo.find(source.attemptId()).orElseThrow();
        return Map.of("state",bundle.executionState(),"fileId",file,"sourceHash",source.file().sha256(),
                "attemptId",bundle.attemptId(),"repository",bundle.repositoryRef(),"targetPath",target,
                "reviewTaskId",review,"review",taskView(task(review)),"source",taskView(task(bundle.taskId())));
    }
    private Map<String,Object> publicationView(String plan) {
        var publication=outputRepo.publication(plan).orElseThrow();
        return fileView(publication.fileId(),publication.reviewTaskId(),publication.targetPath());
    }
    @Override public Object execute(ActionScope rule,String actor,String grant,long revision) {
        return execute(rule,actor,grant,revision,null);
    }
    public Object execute(ActionScope rule,String actor,String grant,long revision,String requestId) {
        AuthorityRepository.Grant publication=null;
        if(planAction(rule) && Set.of(ActionScope.Action.A01,ActionScope.Action.A03,ActionScope.Action.A05,ActionScope.Action.A06).contains(rule.action())
                && outputRepo.publication(rule.targetId()).isPresent()
                && (rule.action()!=ActionScope.Action.A03 || !WorkflowTaskPosition.parse(plans.get(rule.targetId()),retrySource(rule).id()).orElseThrow().review()))
            publication=requirePublicationAuthority(rule,grant);
        if(rule.action()==ActionScope.Action.A12_EXECUTE) publication=repo.get(grant);
        Object result=executeAction(rule,actor,grant,revision,requestId);
        if(publication!=null) authorizePublicationTasks(rule.targetId(),publication);
        return result;
    }
    private Object executeAction(ActionScope rule,String actor,String grant,long revision,String requestId) {
        return switch(rule.action()) {
            case A18_BUDGET -> {
                if(requestId==null) throw new IllegalArgumentException("Formal delegated increment request required");
                yield executionLedger.getObject().increment(new me.asu.aidev.execution.guard.GuardContract.Increment(requestId,rule.targetId(),
                    me.asu.aidev.execution.guard.GuardContract.Dimension.valueOf(required(rule,"dimension")),rule.parameters().get("loopId"),
                    Long.parseLong(required(rule,"amount")),Long.parseLong(required(rule,"cumulativeCeiling"))),actor);
            }
            case A17_SCHEDULE -> { archives.scheduleDeletion(rule.targetId(),actor); yield "Deletion scheduled using the original retention window"; }
            case A17_UNDO -> { archives.undoDeletion(rule.targetId()); yield "Pending deletion undone within the original window"; }
            case A16 -> { archives.archive(rule.targetId(),actor); yield "Archived; retained original result history"; }
            case A14,A15 -> {
                if(jdbc.queryForObject("SELECT COUNT(*) FROM authority_identity WHERE worker_id=?",Long.class,rule.targetId())>0)
                    throw new IllegalArgumentException("A14/A15 affect ordinary Workers; delegate enablement uses its separate human control");
                if(rule.action()==ActionScope.Action.A14) workers.drain(new WorkerId(rule.targetId()));
                else {
                    if(workers.get(new WorkerId(rule.targetId())).activeLeases()!=0) throw new IllegalStateException("Finish active leases before delegated offline");
                    workers.offline(new WorkerId(rule.targetId()));
                }
                yield workers.get(new WorkerId(rule.targetId())).status().name();
            }
            case A13 -> {
                long interval=Long.parseLong(required(rule,"minimumIntervalSeconds"));
                if(interval<0 || interval>Integer.MAX_VALUE) throw new IllegalArgumentException("Bounded nonnegative Fleet interval required");
                var previous=jdbc.queryForList("SELECT created_ms FROM authority_audit WHERE target_key='GLOBAL:FLEET' AND event='ACTION_ADMITTED' AND detail LIKE 'A13;%' ORDER BY created_ms DESC LIMIT 1",Long.class);
                if(!previous.isEmpty() && System.currentTimeMillis()-previous.getFirst()<interval*1000L) throw new IllegalStateException("Configured Fleet change interval has not elapsed");
                int minimum=Integer.parseInt(required(rule,"minimum"));
                int maximum=Integer.parseInt(required(rule,"maximum"));
                int desired=Integer.parseInt(required(rule,"maxParallelism"));
                if(minimum<0 || maximum<minimum || maximum>65535 || desired<minimum || desired>maximum
                        || desired==0 && !rule.parameters().getOrDefault("allowUnlimited","false").equals("true"))
                    throw new IllegalArgumentException("Fleet change is outside the explicitly authorized interval; zero means unlimited");
                yield Integer.toString(fleet.updateMaxParallelism(desired,actor,required(rule,"reason")).maxParallelism());
            }
            case A12_CREATE -> {
                if(!outputs.file(rule.targetId()).file().sha256().equals(required(rule,"sourceHash"))) throw new IllegalStateException("Confirmed publication source hash changed");
                String plan=outputs.publication(rule.targetId(),required(rule,"reviewTaskId"),required(rule,"targetPath"),required(rule,"reason"),actor).id();
                jdbc.update("INSERT INTO authority_publication_plan VALUES(?,?)",plan,key(rule));
                yield plan;
            }
            case A12_EXECUTE -> {
                verifyPublicationRule(rule);
                yield coordinator.delegatedAdmission(rule.targetId(),repo.get(grant).workerId(),()->{
                    var plan=plans.get(rule.targetId());
                    if(plan.state()==WorkflowPlan.State.DRAFT) return plans.confirm(plan.id(),plan.version(),actor).state().name();
                    if(plan.state()!=WorkflowPlan.State.CONFIRMED || publicationCandidates(plan.id()).size()!=1)
                        throw new IllegalStateException("Exactly one interrupted or newly created publication execution must await fixed-attempt admission");
                    return "One fixed publication execution attempt admitted";
                });
            }
            case A11_APPROVE,A11_REJECT -> decideApproval(rule,actor,grant);
            case A10 -> cancellation.cancel(rule.targetId(),plans.get(rule.targetId()).version(),actor,required(rule,"reason")).state().name();
            case A09 -> plans.complete(rule.targetId(),plans.get(rule.targetId()).version(),actor,required(rule,"reason")).state().name();
            case A08 -> plans.withdraw(rule.targetId(),plans.get(rule.targetId()).version(),actor,required(rule,"reason")).state().name();
            case A07 -> plans.stop(rule.targetId(),plans.get(rule.targetId()).version(),actor,required(rule,"reason")).state().name();
            case A06 -> coordinator.delegatedAdmission(rule.targetId(),repo.get(grant).workerId(),()->plans.jump(rule.targetId(),plans.get(rule.targetId()).version(),Integer.parseInt(required(rule,"targetStep")),actor,required(rule,"reason")).state().name());
            case A05 -> coordinator.delegatedAdmission(rule.targetId(),repo.get(grant).workerId(),()->plans.confirm(rule.targetId(),plans.get(rule.targetId()).version(),actor).state().name());
            case A04 -> coordinator.delegatedAdmission(rule.targetId(),repo.get(grant).workerId(),()->{
                if(!required(rule,"warningConfirmed").equals("true")) throw new IllegalArgumentException("Explicit review-only recovery confirmation is required");
                var recovery=plans.reviewRecovery(rule.targetId());var plan=plans.get(rule.targetId());
                if(recovery.taskId()==null || WorkflowTaskPosition.parse(plan,new TaskId(recovery.taskId())).orElseThrow().step()!=rule.step())
                    throw new IllegalStateException("Review recovery is outside the authorized step");
                plans.resumeReview(plan.id(),recovery.taskId(),recovery.planVersion(),recovery.taskVersion(),actor,required(rule,"reason"),true);
                return "Review-only recovery admitted; execution round was not repeated";
            });
            case A03 -> coordinator.delegatedAdmission(rule.targetId(),repo.get(grant).workerId(),()->{
                var plan=plans.get(rule.targetId());
                var source=retrySource(rule);
                var failure=jdbc.queryForList("SELECT failure_category FROM execution_attempt WHERE task_id=? ORDER BY attempt_no DESC LIMIT 1",String.class,source.id().value());
                if(failure.isEmpty() || !Objects.equals(failure.getFirst(),required(rule,"failureCategory")))
                    throw new IllegalStateException("Failure category is unknown or not authorized for retry");
                if(!required(rule,"sideEffectsConfirmed").equals("true")) throw new IllegalArgumentException("Explicit duplicate-side-effect authority is required");
                long seconds=Long.parseLong(rule.parameters().getOrDefault("minimumIntervalSeconds","0"));
                if(seconds<0 || java.time.Instant.now().isBefore(source.updatedAt().plusSeconds(seconds)))
                    throw new IllegalStateException("Configured retry interval has not elapsed");
                long attempt=jdbc.queryForObject("SELECT attempt_no FROM work_task WHERE id=?",Long.class,source.id().value());
                plans.retryFailedTask(plan.id(),source.id().value(),plan.version(),source.version(),actor,required(rule,"reason"),true);
                jdbc.update("INSERT INTO authority_task(task_id,target_key,allowed_attempt,reserved,started,settled) VALUES(?,?,?,FALSE,FALSE,FALSE) ON CONFLICT(task_id) DO UPDATE SET allowed_attempt=excluded.allowed_attempt",
                        source.id().value(),key(rule),attempt+1);
                jdbc.update("UPDATE authority_task SET started=FALSE,settled=FALSE,state='PREPARED',request_id=NULL WHERE task_id=? AND state<>'UNKNOWN'",source.id().value());
                return source.id().value();
            });
            case A02 -> configureModels(rule,grant,revision);
            case A01 -> coordinator.delegatedAdmission(rule.targetId(),repo.get(grant).workerId(),()->{
                var recovery=plans.reviewRecovery(rule.targetId());
                var plan=plans.get(rule.targetId());
                var position=WorkflowTaskPosition.parse(plan,new TaskId(recovery.taskId())).orElseThrow();
                if(position.step()!=rule.step()) throw new IllegalStateException("Current exhausted review belongs to another step");
                var next=plans.continueAuthorizedRepair(plan.id(),recovery.taskId(),recovery.planVersion(),recovery.taskVersion(),actor,
                        required(rule,"reason"),required(rule,"scope")).orElseThrow(()->new IllegalStateException("Repair source changed"));
                jdbc.update("INSERT INTO authority_task(task_id,target_key,allowed_attempt,reserved,started,settled) VALUES(?,?,1,TRUE,FALSE,FALSE) ON CONFLICT(task_id) DO NOTHING",next.value(),key(rule));
                return next.value();
            });
        };
    }
    private Object decideApproval(ActionScope rule,String actor,String grant) {
        var approval=approvals.find(new ApprovalRequestId(rule.targetId())).orElseThrow();
        var gate=gates.find(approval.sourceTaskId()).orElseThrow();
        if(gate.status()!=me.asu.aidev.development.domain.EngineeringGateStatus.PASSED
                || !gate.resultRevision().equals(approval.resultRevision())) throw new IllegalStateException("Current PASSED engineering evidence is required");
        var executors=new HashSet<>(jdbc.queryForList("SELECT DISTINCT worker_id FROM execution_attempt WHERE task_id=?",String.class,approval.sourceTaskId().value()));
        for(String review:List.of(gate.reviewTaskId().value(),gate.verificationTaskId().value()))
            if(jdbc.queryForList("SELECT DISTINCT worker_id FROM execution_attempt WHERE task_id=?",String.class,review).stream().anyMatch(executors::contains))
                throw new IllegalStateException("Execution and independent engineering review/verification share historical Worker participation; human handling required");
        String worker=repo.get(grant).workerId();
        var related=new LinkedHashSet<String>();
        var queue=new ArrayDeque<String>();
        queue.add(approval.sourceTaskId().value());queue.add(gate.reviewTaskId().value());queue.add(gate.verificationTaskId().value());
        while(!queue.isEmpty()) {
            String next=queue.removeFirst();
            if(!related.add(next)) continue;
            if(related.size()>10000) throw new IllegalStateException("Cannot bound the complete participation evidence; human handling required");
            queue.addAll(jdbc.queryForList("SELECT depends_on_task_id FROM work_task_dependency WHERE task_id=?",String.class,next));
        }
        for(String task:related) {
            Long participation=jdbc.queryForObject("SELECT COUNT(*) FROM execution_attempt WHERE task_id=? AND worker_id=?",Long.class,task,worker);
            if(participation!=null && participation>0) throw new IllegalStateException("Delegate participated in the result lineage and cannot decide this result");
        }
        String note="On behalf of "+repo.get(grant).owner()+"; grant "+grant+":"+repo.get(grant).revision()+"; "+required(rule,"reason");
        var decision=rule.action()==ActionScope.Action.A11_APPROVE?me.asu.aidev.approval.domain.ApprovalDecision.APPROVE:me.asu.aidev.approval.domain.ApprovalDecision.REJECT;
        return approvals.decide(new me.asu.aidev.approval.application.DecideApprovalCommand(new ApprovalRequestId(rule.targetId()),decision,actor,note)).status().name();
    }
    private AuthorityRepository.Grant requirePublicationAuthority(ActionScope rule,String grant) {
        outputs.requirePublication(rule.targetId());
        var owner=repo.get(grant);
        var link=outputRepo.publication(rule.targetId()).orElseThrow();
        String key=publicationKey(link.fileId(),link.targetPath());
        var allowed=repo.grants().stream().filter(g->g.rule().action()==ActionScope.Action.A12_EXECUTE
                && g.rule().effect().equals("ALLOW") && key(g.rule()).equals(key) && g.owner().equals(owner.owner())
                && g.workerId().equals(owner.workerId()) && g.rule().targetId().equals(rule.targetId())
                && grants.getObject().permitted(g,snapshot(g.rule()))).findFirst()
                .orElseThrow(()->new IllegalStateException("Separate publication execution authority is required"));
        verifyPublicationRule(allowed.rule());
        budgets.getObject().consume(allowed,null,false);
        repo.audit(key,allowed.id(),null,"delegate:"+owner.workerId(),"PUBLICATION_EXECUTION_AUTHORIZED","Through "+rule.action()+"; on behalf of "+owner.owner());
        return allowed;
    }
    private List<WorkTask> publicationCandidates(String plan) {
        return plans.steps(plan).stream().filter(t->!t.skillRef().equals("workflow-stage-reviewer"))
                .filter(t->t.state()==TaskState.READY || t.state()==TaskState.BLOCKED)
                // Use the same revision/expiry/identity/boot predicate as claim/start.
                // An obsolete row does not prevent a fresh, separately debited decision.
                .filter(t->!taskPolicy.getObject().canClaim(t)).toList();
    }
    private void authorizePublicationTasks(String plan,AuthorityRepository.Grant grant) {
        if(!grants.getObject().targetCurrent(grant,null,null)) throw new IllegalStateException("Publication target changed or is paused");
        var target=jdbc.queryForMap("SELECT generation,restriction_revision FROM authority_target WHERE target_key=?",grant.targetKey());
        var candidates=publicationCandidates(plan);
        if(candidates.size()!=1) throw new IllegalStateException("Publication action must admit exactly one fixed execution attempt");
        var task=candidates.getFirst();
        long next=jdbc.queryForObject("SELECT attempt_no+1 FROM work_task WHERE id=?",Long.class,task.id().value());
        for(var previous:jdbc.queryForList("SELECT * FROM authority_task WHERE task_id=? AND started=TRUE",task.id().value())) {
            if(Set.of("UNKNOWN","IN_PROGRESS").contains(String.valueOf(previous.get("state")))) throw new IllegalStateException("Unconfirmed publication attempt cannot be replayed");
            jdbc.update("INSERT INTO authority_effect_history VALUES(?,?,?) ON CONFLICT(task_id,attempt) DO NOTHING",task.id().value(),previous.get("allowed_attempt"),repo.encode(previous));
            jdbc.update("UPDATE authority_task SET started=FALSE,settled=FALSE,state='PREPARED',request_id=NULL WHERE task_id=?",task.id().value());
        }
        long identity=jdbc.queryForObject("SELECT generation FROM authority_identity WHERE worker_id=?",Long.class,grant.workerId());
        jdbc.update("INSERT INTO authority_publication_attempt VALUES(?,?,?,?,?,?,?,?,?,?) ON CONFLICT(task_id) DO UPDATE SET allowed_attempt=excluded.allowed_attempt,grant_id=excluded.grant_id,grant_revision=excluded.grant_revision,worker_generation=excluded.worker_generation,enabled_boot=excluded.enabled_boot,target_key=excluded.target_key,owner_id=excluded.owner_id,target_generation=excluded.target_generation,restriction_revision=excluded.restriction_revision",
                task.id().value(),next,grant.id(),grant.revision(),identity,jdbc.queryForObject("SELECT enabled_boot FROM authority_identity WHERE worker_id=?",String.class,grant.workerId()),
                grant.targetKey(),grant.owner(),target.get("generation"),target.get("restriction_revision"));
        jdbc.update("INSERT INTO authority_task(task_id,target_key,allowed_attempt,reserved,started,settled) VALUES(?,?,?,FALSE,FALSE,FALSE) ON CONFLICT(task_id) DO UPDATE SET allowed_attempt=excluded.allowed_attempt",
                task.id().value(),key(grant.rule()),next);
        repo.audit(key(grant.rule()),grant.id(),null,"delegate:"+grant.workerId(),"PUBLICATION_ATTEMPT_ADMITTED",task.id().value()+"; attempt "+next+"; grant revision "+grant.revision());
    }
    private void verifyPublicationRule(ActionScope rule) {
        outputs.requirePublication(rule.targetId());
        var link=outputRepo.publication(rule.targetId()).orElseThrow();
        if(!link.fileId().equals(required(rule,"fileId")) || !link.reviewTaskId().equals(required(rule,"reviewTaskId"))
                || !link.targetPath().equals(required(rule,"targetPath")) || !outputs.file(link.fileId()).file().sha256().equals(required(rule,"sourceHash")))
            throw new IllegalStateException("Publication authority does not match the exact file, hash, review and target");
    }
    private WorkTask retrySource(ActionScope rule) {
        var plan=plans.get(rule.targetId());
        var candidates=plans.steps(plan.id()).stream().filter(t->WorkflowTaskPosition.parse(plan,t.id()).filter(p->p.step()==rule.step()).isPresent()).toList();
        String requested=required(rule,"taskId");
        if(!requested.equals("CURRENT")) return candidates.stream().filter(t->t.id().value().equals(requested)).findFirst().orElseThrow(()->new IllegalArgumentException("Retry task is outside the authorized Plan step"));
        return candidates.stream().max(Comparator.comparingLong((WorkTask t)->WorkflowTaskPosition.parse(plan,t.id()).orElseThrow().sequence())
                .thenComparingInt(t->WorkflowTaskPosition.parse(plan,t.id()).orElseThrow().resume())).orElseThrow();
    }
    private Object configureModels(ActionScope rule,String grant,long revision) {
        String version=required(rule,"catalogVersion");
        if(!version.equals(aiProfiles.version())) throw new IllegalStateException("Approved model catalog changed");
        var ids=Arrays.stream(required(rule,"profiles").split(",")).map(String::strip).filter(s->!s.isEmpty()).distinct().toList();
        if(ids.isEmpty() || !aiProfiles.approved(TaskType.EXTERNAL_WORK).stream().map(me.asu.aidev.ai.application.AiProfileCatalog.NamedProfile::id).toList().containsAll(ids))
            throw new IllegalArgumentException("Every selected profile must be approved for the real task type");
        String role=required(rule,"role");
        if(!Set.of("EXECUTION","REVIEW","BOTH").contains(role)) throw new IllegalArgumentException("Choose EXECUTION, REVIEW or BOTH");
        var plan=plans.get(rule.targetId());
        boolean active=plans.steps(plan.id()).stream().filter(t->Set.of(TaskState.CLAIMED,TaskState.RUNNING).contains(t.state()))
                .anyMatch(t->WorkflowTaskPosition.parse(plan,t.id()).filter(p->p.step()==rule.step()
                        && (role.equals("BOTH") || (p.review()?"REVIEW":"EXECUTION").equals(role))).isPresent());
        if(active) throw new IllegalStateException("Finish or stop active affected tasks before changing their model authority");
        for(String selected:role.equals("BOTH")?List.of("EXECUTION","REVIEW"):List.of(role))
            jdbc.update("INSERT INTO authority_model_scope VALUES(?,?,?,?,?,?) ON CONFLICT(target_key,role) DO UPDATE SET catalog_version=excluded.catalog_version,profiles_json=excluded.profiles_json,grant_id=excluded.grant_id,grant_revision=excluded.grant_revision",
                    key(rule),selected,version,repo.encode(ids),grant,revision);
        return "Model whitelist frozen for "+role+": "+String.join(",",ids);
    }
}
