package me.asu.aidev.authority;

import java.util.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import me.asu.aidev.approval.application.ApprovalService;
import me.asu.aidev.approval.domain.ApprovalRequestId;
import me.asu.aidev.questions.FormalQuestions;
import me.asu.aidev.workflow.application.WorkflowPlanService;
import me.asu.aidev.workflow.domain.WorkflowTaskPosition;
import me.asu.aidev.work.domain.TaskId;
import me.asu.aidev.work.port.WorkTaskRepository;
import static me.asu.aidev.authority.ActionPermissionContract.*;

/** Formal provenance is authoritative; matching a repository or an arbitrary target row is insufficient. */
@Component
public class PermissionTargetResolver {
    private final AuthorityRepository repo;
    private final ObjectProvider<WorkflowPlanService> plans;
    private final ObjectProvider<WorkTaskRepository> tasks;
    private final ObjectProvider<ApprovalService> approvals;
    private final ObjectProvider<FormalQuestions> questions;
    public PermissionTargetResolver(AuthorityRepository repo,ObjectProvider<WorkflowPlanService> plans,ObjectProvider<WorkTaskRepository> tasks,
            ObjectProvider<ApprovalService> approvals,ObjectProvider<FormalQuestions> questions) {
        this.repo=repo;this.plans=plans;this.tasks=tasks;this.approvals=approvals;this.questions=questions;
    }
    public record Resolved(String planId,String sourceTask) {}
    private void bound(String key,String owner,boolean required) {
        var rows=repo.jdbc().queryForList("SELECT owner_id FROM authority_target WHERE target_key=?",String.class,key);
        if(required&&rows.size()!=1||rows.stream().anyMatch(value->!owner.equals(value)))throw new SecurityException("Human-confirmed target ownership required");
    }
    public void plan(String owner,String id) {plan(owner,id,new HashSet<>());}
    private void plan(String owner,String id,Set<String> visiting) {
        if(visiting.size()>16||!visiting.add(id))throw new SecurityException("Invalid provenance cycle");
        var facts=new HashSet<String>();boolean mappedPublication=false;
        var publication=repo.jdbc().queryForList("SELECT file_id FROM task_output_publication WHERE plan_id=?",String.class,id);
        if(!publication.isEmpty()) {
            file(owner,publication.getFirst(),visiting);facts.add(owner);
            var mapping=repo.jdbc().queryForList("SELECT t.owner_id FROM authority_publication_plan p JOIN authority_target t ON t.target_key=p.target_key WHERE p.plan_id=?",String.class,id);
            if(mapping.size()>1)throw new SecurityException("Ambiguous publication provenance");facts.addAll(mapping);mappedPublication=mapping.size()==1;
        }
        for(String creator:repo.jdbc().queryForList("SELECT created_by FROM flexible_workflow_plan WHERE id=?",String.class,id)) {
            if(creator.equals(repo.actor(owner)))facts.add(owner);
            else if(!publication.isEmpty()&&creator.startsWith("personal:")) {
                if(!mappedPublication)throw new SecurityException("Registered delegated publication provenance required");
                var actors=repo.jdbc().queryForList("SELECT owner_id FROM authority_identity WHERE worker_id=?",String.class,creator.substring(9));
                if(actors.size()!=1)throw new SecurityException("Publication actor provenance unavailable");facts.addAll(actors);
            } else throw new SecurityException("Plan belongs to another actor");
        }
        facts.addAll(repo.jdbc().queryForList("SELECT owner_id FROM execution_policy WHERE plan_id=?",String.class,id));
        if(facts.size()!=1||!facts.contains(owner))throw new SecurityException("Missing or conflicting formal plan owner");
        bound("PLAN:"+id,owner,false);visiting.remove(id);
    }
    public String task(String owner,String id) {return task(owner,id,new HashSet<>());}
    private String task(String owner,String id,Set<String> visiting) {
        var row=repo.jdbc().queryForMap("SELECT subject_type,subject_id FROM work_task WHERE id=?",id);
        var contexts=repo.jdbc().queryForList("SELECT c.plan_id,p.owner_id,c.policy_json AS frozen_policy FROM execution_context c JOIN execution_policy p ON p.plan_id=c.plan_id WHERE c.task_id=?",id);
        String plan;
        if("flexible-plan".equals(row.get("subject_type"))) {
            plan=String.valueOf(row.get("subject_id"));
            if(contexts.stream().anyMatch(c->!contextOwner(c,owner,plan)))throw new SecurityException("Conflicting task owner");
        } else {
            if(contexts.size()!=1||!contextOwner(contexts.getFirst(),owner,String.valueOf(contexts.getFirst().get("plan_id"))))throw new SecurityException("Formal task context required");
            plan=String.valueOf(contexts.getFirst().get("plan_id"));
        }
        plan(owner,plan,visiting);return plan;
    }
    private boolean contextOwner(Map<String,Object> context,String owner,String plan) {
        var frozen=repo.decode(String.valueOf(context.get("frozen_policy")),me.asu.aidev.execution.guard.GuardContract.Policy.class);
        return owner.equals(context.get("owner_id"))&&owner.equals(frozen.ownerId())&&plan.equals(context.get("plan_id"))&&plan.equals(frozen.planId());
    }
    private String file(String owner,String id,Set<String> visiting) {
        var ids=repo.jdbc().queryForList("SELECT a.task_id FROM task_output_file f JOIN execution_attempt a ON a.id=f.attempt_id WHERE f.id=?",String.class,id);
        if(ids.size()!=1)throw new SecurityException("Formal file source task required");
        task(owner,ids.getFirst(),visiting);return ids.getFirst();
    }
    public Resolved resolve(String owner,ActionScope rule,String selectedTask,String questionId) {
        String source=null,plan=null;
        switch(rule.action()) {
            case A13 -> {if(!"GLOBAL".equals(rule.targetId()))throw new IllegalArgumentException("Canonical Fleet target required");bound("GLOBAL:FLEET",owner,true);}
            case A14,A15 -> bound("WORKER:"+rule.targetId(),owner,true);
            case A11_APPROVE,A11_REJECT -> {source=approvals.getObject().find(new ApprovalRequestId(rule.targetId())).orElseThrow().sourceTaskId().value();plan=task(owner,source);}
            case A12_CREATE -> {source=file(owner,rule.targetId(),new HashSet<>());plan=task(owner,source);}
            case A12_EXECUTE -> {
                var ids=repo.jdbc().queryForList("SELECT file_id FROM task_output_publication WHERE plan_id=?",String.class,rule.targetId());
                if(ids.size()!=1)throw new SecurityException("Formal publication required");source=file(owner,ids.getFirst(),new HashSet<>());plan=rule.targetId();plan(owner,plan);
            }
            case A18_BUDGET,A19_ANSWER -> {
                if(questionId==null)throw new IllegalArgumentException("Exact formal question required");
                var q=questions.getObject().get(questionId);
                if(!owner.equals(q.ownerId())||!q.source().planId().equals(rule.targetId()))throw new SecurityException("Question source changed");
                plan=q.source().planId();source=q.source().taskId();plan(owner,plan);
                if(source!=null&&!task(owner,source).equals(plan))throw new SecurityException("Question/task plan conflict");
            }
            default -> {
                plan=rule.targetId();plan(owner,plan);
                source=rule.action()==ActionScope.Action.A03?rule.parameters().get("taskId"):selectedTask;
                if(source!=null) {
                    if(!task(owner,source).equals(plan))throw new SecurityException("Task is outside the selected plan");
                    if(rule.step()!=null) {
                        var position=WorkflowTaskPosition.parse(plans.getObject().get(plan),new TaskId(source)).orElseThrow();
                        if(position.step()!=rule.step())throw new SecurityException("Task is outside the selected step");
                    }
                }
            }
        }
        if(selectedTask!=null&&!selectedTask.equals(source))throw new SecurityException("Exact source task changed");
        return new Resolved(plan,source);
    }
    public void validateScope(String owner,ActionScope.Action action,Scope kind,String id) {
        if(!scopes(action).contains(kind))throw new IllegalArgumentException("Action/scope mismatch");
        if(kind==Scope.PLAN)plan(owner,id);else if(kind==Scope.TASK)task(owner,id);
        else if(kind==Scope.WORKER) {
            if(repo.jdbc().queryForObject("SELECT COUNT(*) FROM worker WHERE id=? AND id NOT IN (SELECT worker_id FROM authority_identity)",Long.class,id)!=1)throw new SecurityException("Existing ordinary Worker required");
            bound("WORKER:"+id,owner,false);
        }
    }
    public Resolved check(BusinessActionPermissions.Permission permission,ActionScope rule,String selectedTask,String questionId) {
        if(rule.action()!=permission.action())throw new SecurityException("Permission action changed");
        var resolved=resolve(permission.ownerId(),rule,selectedTask,questionId);
        if(permission.scopeKind()==Scope.PLAN&&!Objects.equals(permission.scopeId(),resolved.planId())
                ||permission.scopeKind()==Scope.TASK&&!Objects.equals(permission.scopeId(),resolved.sourceTask())
                ||permission.scopeKind()==Scope.WORKER&&!Objects.equals(permission.scopeId(),rule.targetId()))throw new SecurityException("Concrete action is outside the human-selected scope");
        return resolved;
    }
}
