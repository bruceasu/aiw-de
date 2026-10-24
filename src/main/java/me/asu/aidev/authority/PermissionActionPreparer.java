package me.asu.aidev.authority;

import java.util.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import me.asu.aidev.questions.QuestionContract;
import me.asu.aidev.questions.FormalQuestions;
import me.asu.aidev.workflow.application.WorkflowPlanService;
import me.asu.aidev.workflow.domain.WorkflowTaskPosition;
import me.asu.aidev.work.domain.TaskId;
import static me.asu.aidev.authority.ActionPermissionContract.*;

/** Fixes an inert original request. Full business snapshots never leave this service. */
@Service
public class PermissionActionPreparer {
    @org.springframework.beans.factory.annotation.Autowired
    private ObjectProvider<me.asu.aidev.ai.application.AiProfileCatalog> catalogs;
    private final AuthorityRepository repo;private final AuthorityBoundary boundary;
    private final BusinessActionPermissions permissions;private final PermissionTargetResolver targets;
    private final ObjectProvider<AuthorityActions> actions;private final AuthorityGrantService grants;
    private final ObjectProvider<ActionAdmissionService> admission;private final ObjectProvider<FormalQuestions> questions;
    private final ObjectProvider<WorkflowPlanService> plans;private final ManagedPlanAdmission manager;
    public PermissionActionPreparer(AuthorityRepository repo,AuthorityBoundary boundary,BusinessActionPermissions permissions,
            PermissionTargetResolver targets,ObjectProvider<AuthorityActions> actions,AuthorityGrantService grants,
            ObjectProvider<ActionAdmissionService> admission,ObjectProvider<FormalQuestions> questions,ObjectProvider<WorkflowPlanService> plans,ManagedPlanAdmission manager) {
        this.repo=repo;this.boundary=boundary;this.permissions=permissions;this.targets=targets;this.actions=actions;
        this.grants=grants;this.admission=admission;this.questions=questions;this.plans=plans;this.manager=manager;
    }
    private List<Map<String,Object>> rows(AuthorityBoundary.Principal principal,String request) {
        return repo.jdbc().queryForList("SELECT * FROM authority_permission_preparation WHERE owner_id=? AND worker_id=? AND request_id=?",principal.owner(),principal.worker(),request);
    }
    private boolean sameBoot(AuthorityBoundary.Principal principal,Map<String,Object> row) {
        return principal.generation()==((Number)row.get("worker_generation")).longValue()&&boundary.boot().equals(row.get("platform_boot"))
                &&boundary.instance().equals(row.get("instance_id"))&&Objects.equals(row.get("worker_boot"),repo.jdbc().queryForObject("SELECT worker_boot FROM authority_identity WHERE worker_id=?",String.class,principal.worker()));
    }
    private Map<String,Object> view(AuthorityBoundary.Principal principal,Map<String,Object> row) {
        return Map.of("state",sameBoot(principal,row)?"PREPARED":"STALE","platformInstanceId",row.get("instance_id"),
                "permissionId",row.get("permission_id"),"requestId",row.get("request_id"),
                "envelope",repo.decode(String.valueOf(row.get("envelope_json")),ActionAdmissionService.Envelope.class),
                "questionProjection",row.get("projection_json")==null?"":row.get("projection_json"));
    }
    @Transactional public Map<String,Object> read(AuthorityBoundary.Principal principal,String request) {
        boundary.lock();admission.getObject().enabled(principal);uuid(request);
        var rows=rows(principal,request);if(rows.size()!=1)throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND,"Preparation not found");
        return view(principal,rows.getFirst());
    }
    @Transactional public Map<String,Object> prepare(AuthorityBoundary.Principal principal,Selection selection) {
        boundary.lock();admission.getObject().enabled(principal);boundary.requireMigrationSafeInstallation();
        String digest=AuthorityRepository.hash(repo.encode(selection));var existing=rows(principal,selection.requestId());
        if(!existing.isEmpty()) {
            if(!digest.equals(existing.getFirst().get("selection_digest")))throw new IllegalStateException("Original selection changed");
            return view(principal,existing.getFirst());
        }
        return boundary.as(principal,()->{
            var p=permissions.get(selection.permissionId());
            if(!p.ownerId().equals(principal.owner())||!p.workerId().equals(principal.worker())||!permissions.active(p))throw new SecurityException("Current own action permission required");
            if(p.usedUses()>=p.maximumUses())throw new IllegalStateException("Action permission exhausted");
            var parameters=new LinkedHashMap<>(selection.parameters());parameters.putIfAbsent("reason","Selected operation under explicit action permission");
            Integer step=selection.step();String source=selection.sourceTaskId(),questionId=null;
            if(p.action()==ActionScope.Action.A01||p.action()==ActionScope.Action.A04) {
                String current=plans.getObject().reviewRecovery(selection.targetId()).taskId();
                if(current==null||source!=null&&!source.equals(current))throw new IllegalStateException("Current review recovery source required");
                source=current;
                int actual=WorkflowTaskPosition.parse(plans.getObject().get(selection.targetId()),new TaskId(source)).orElseThrow().step();
                if(step!=null&&step!=actual)throw new SecurityException("Recovery step differs from its source");step=actual;
            }
            if(p.action()==ActionScope.Action.A02)exact(parameters,"catalogVersion",catalogs.getObject().version());
            if(p.action()==ActionScope.Action.A03) {
                if(source==null)source=parameters.get("taskId");
                if(source==null||!selection.targetId().equals(targets.task(p.ownerId(),source)))throw new SecurityException("Exact retry source required");
                exact(parameters,"taskId",source);
                int actual=WorkflowTaskPosition.parse(plans.getObject().get(selection.targetId()),new TaskId(source)).orElseThrow().step();
                if(step!=null&&step!=actual)throw new SecurityException("Retry step differs from its source");step=actual;
                var failures=repo.jdbc().queryForList("SELECT failure_category FROM execution_attempt WHERE task_id=? ORDER BY attempt_no DESC LIMIT 1",String.class,source);
                if(failures.isEmpty()||failures.getFirst()==null)throw new IllegalStateException("Known retry failure category required");
                exact(parameters,"failureCategory",failures.getFirst());
            }
            if(p.action()==ActionScope.Action.A12_CREATE||p.action()==ActionScope.Action.A12_EXECUTE) {
                String file=p.action()==ActionScope.Action.A12_CREATE?selection.targetId():parameters.get("fileId");
                if(file==null)throw new IllegalArgumentException("Publication source selection required");
                exact(parameters,"sourceHash",repo.jdbc().queryForObject("SELECT content_hash FROM task_output_file WHERE id=?",String.class,file));
            }
            QuestionContract.Question question=null;
            if(p.action()==ActionScope.Action.A18_BUDGET||p.action()==ActionScope.Action.A19_ANSWER) {
                if(selection.projectionJson()==null)throw new IllegalArgumentException("Original selected question projection required");
                var projection=repo.decode(selection.projectionJson(),QuestionContract.Projection.class);
                question=questions.getObject().get(projection.reply().questionId());questionId=question.questionId();
                if(!question.ownerId().equals(p.ownerId())||!question.source().planId().equals(selection.targetId())||question.requestVersion()!=projection.reply().requestVersion()
                        ||question.state()!=QuestionContract.State.OPEN||step!=null)throw new SecurityException("Current original question required");
                if(source!=null&&!Objects.equals(source,question.source().taskId()))throw new SecurityException("Question source task differs");
                source=question.source().taskId();
                if(p.action()==ActionScope.Action.A19_ANSWER) {
                    if(question.kind()!=QuestionContract.Kind.PROFESSIONAL)throw new SecurityException("Professional answer required");
                    exact(parameters,"kind","PROFESSIONAL");
                } else {
                    var a=question.authorization();
                    if(question.kind()!=QuestionContract.Kind.AUTHORIZATION||a==null||!selection.requestId().equals(a.requestId())||!projection.reply().rawAnswer().equals("APPROVE"))throw new SecurityException("Exact budget request and decision required");
                    exact(parameters,"dimension",a.dimension().name());exact(parameters,"amount",Long.toString(a.amount()));exact(parameters,"cumulativeCeiling",Long.toString(a.cumulativeCeiling()));
                    if(a.loopId()!=null)exact(parameters,"loopId",a.loopId());else if(parameters.containsKey("loopId"))throw new IllegalArgumentException("Unexpected loop scope");
                }
            } else if(selection.projectionJson()!=null)throw new IllegalArgumentException("Projection belongs only to formal question actions");
            var provisional=new ActionScope(p.action(),selection.targetId(),step,"ALLOW",Set.of("PENDING"),0,p.maximumUses(),parameters);
            var grant=child(p,provisional,source,questionId,selection.requestId());
            var snapshot=actions.getObject().snapshot(grant.rule());
            String retry=grant.rule().action()==ActionScope.Action.A03?grant.rule().parameters().get("taskId"):null;
            Long taskVersion=null,attempt=null;
            if(retry!=null) {
                var row=repo.jdbc().queryForMap("SELECT version,attempt_no FROM work_task WHERE id=?",retry);
                taskVersion=((Number)row.get("version")).longValue();attempt=((Number)row.get("attempt_no")).longValue()+1;
            }
            var envelope=new ActionAdmissionService.Envelope(1,selection.requestId(),grant.id(),grant.revision(),manager.restriction(grant.targetKey()),p.action(),
                    grant.rule().targetId(),grant.rule().step(),manager.generation(grant.targetKey()),AuthorityRepository.hash(repo.encode(snapshot)),
                    snapshot.get("version") instanceof Number version?version.longValue():null,taskVersion,retry,attempt,grant.rule().parameters(),
                    question==null?List.of():List.of("question-projection:"+AuthorityRepository.hash(selection.projectionJson())));
            String instance=boundary.instance(),boot=repo.jdbc().queryForObject("SELECT worker_boot FROM authority_identity WHERE worker_id=?",String.class,principal.worker());
            repo.jdbc().update("INSERT INTO authority_permission_preparation VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",principal.owner(),principal.worker(),selection.requestId(),p.id(),digest,grant.id(),
                    repo.encode(envelope),selection.projectionJson(),instance,principal.generation(),boundary.boot(),boot);
            return view(principal,rows(principal,selection.requestId()).getFirst());
        });
    }
    private void exact(Map<String,String> parameters,String key,String value) {
        if(parameters.containsKey(key)&&!value.equals(parameters.get(key)))throw new IllegalArgumentException("Concrete selection differs from its formal source");parameters.put(key,value);
    }
    private AuthorityRepository.Grant child(BusinessActionPermissions.Permission p,ActionScope provisional,String selectedTask,String questionId,String request) {
        actions.getObject().validate(provisional);var resolved=targets.check(p,provisional,selectedTask,questionId);
        String key=actions.getObject().key(provisional);grants.bindTarget(key,p.ownerId());actions.getObject().admit(provisional);
        var snapshot=actions.getObject().snapshot(provisional);
        var scope=new ActionScope(provisional.action(),provisional.targetId(),provisional.step(),"ALLOW",Set.of(String.valueOf(snapshot.get("state"))),0,p.maximumUses(),provisional.parameters());
        String id="AG-"+UUID.randomUUID();
        repo.jdbc().update("INSERT INTO authority_grant VALUES(?,?,?,1,'ENABLED',?,?,?,?,?)",id,p.ownerId(),p.workerId(),repo.encode(scope),p.authority(),System.currentTimeMillis(),p.expiresMs(),key);
        repo.jdbc().update("INSERT INTO authority_budget VALUES(?,?,?,0,0) ON CONFLICT(target_key,action) DO NOTHING",key,p.action().name(),p.maximumUses());
        repo.jdbc().update("INSERT INTO authority_permission_child VALUES(?,?,?,?,?,?)",id,p.id(),request,resolved.planId(),resolved.sourceTask(),questionId);
        repo.audit(key,p.id(),request,"personal:"+p.workerId(),"ACTION_SELECTION_FIXED","Inert exact child; no action or count consumed");return repo.get(id);
    }
    public void validateEnvelope(AuthorityBoundary.Principal principal,ActionAdmissionService.Envelope envelope) {
        if(permissions.child(envelope.grantId()).isEmpty())return;
        var rows=rows(principal,envelope.requestId());
        if(rows.size()!=1||!envelope.equals(repo.decode(String.valueOf(rows.getFirst().get("envelope_json")),ActionAdmissionService.Envelope.class))||!sameBoot(principal,rows.getFirst()))
            throw new SecurityException("Exact original prepared envelope and process required");
    }
    public boolean questionRead(AuthorityBoundary.Principal principal,QuestionContract.PersonalReply input,QuestionContract.Question question) {
        var c=permissions.child(input.envelope().grantId());if(c.isEmpty())return false;
        admission.getObject().enabled(principal);var rows=rows(principal,input.envelope().requestId());
        if(rows.size()!=1)throw new SecurityException("Original prepared question required");var row=rows.getFirst();
        if(!Objects.equals(row.get("projection_json"),input.projectionJson())||!repo.encode(input.envelope()).equals(row.get("envelope_json"))
                ||!Objects.equals(c.get().questionId(),question.questionId())||!question.ownerId().equals(principal.owner())||!Objects.equals(c.get().sourceTask(),question.source().taskId()))
            throw new SecurityException("Original selected question changed");
        boolean recorded=repo.jdbc().queryForObject("SELECT COUNT(*) FROM authority_receipt WHERE owner_id=? AND worker_id=? AND request_id=?",Long.class,principal.owner(),principal.worker(),input.envelope().requestId())==1;
        if(!recorded&&(!sameBoot(principal,row)||!permissions.grantActive(repo.get(input.envelope().grantId()))))throw new SecurityException("Question preparation is no longer current");
        return true;
    }
    /** Existing compound actions must independently hold and consume publication authority. */
    public void publication(AuthorityRepository.Grant initiating,String plan,String file,String review,String path) {
        if(!permissions.regime(initiating.owner(),initiating.workerId(),ActionScope.Action.A12_EXECUTE))return;
        var parent=permissions.list(initiating.owner()).stream().filter(p->p.workerId().equals(initiating.workerId())&&p.action()==ActionScope.Action.A12_EXECUTE&&permissions.active(p)).findFirst()
                .orElseThrow(()->new SecurityException("Current publication action permission required"));
        String request=permissions.requestId();if(request==null)throw new SecurityException("Original compound action identity required");
        if(repo.grants().stream().filter(g->g.rule().action()==ActionScope.Action.A12_EXECUTE&&g.rule().targetId().equals(plan))
                .anyMatch(g->permissions.child(g.id()).filter(c->c.permissionId().equals(parent.id())&&c.requestId().equals(request)).isPresent()))return;
        String hash=repo.jdbc().queryForObject("SELECT content_hash FROM task_output_file WHERE id=?",String.class,file);
        child(parent,new ActionScope(ActionScope.Action.A12_EXECUTE,plan,null,"ALLOW",Set.of("PENDING"),0,parent.maximumUses(),
                Map.of("reason","Publication required by the selected operation","fileId",file,"reviewTaskId",review,"targetPath",path,"sourceHash",hash)),null,null,request);
    }
}
