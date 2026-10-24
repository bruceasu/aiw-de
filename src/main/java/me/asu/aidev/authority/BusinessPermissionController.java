package me.asu.aidev.authority;

import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import me.asu.aidev.workflow.application.WorkflowPlanService;
import static me.asu.aidev.authority.ActionPermissionContract.*;

/** Additive routes. The existing authority interceptor owns machine, human and CSRF separation. */
@RestController
@RequestMapping("/api/r1/authority")
public class BusinessPermissionController {
    @org.springframework.beans.factory.annotation.Autowired
    private ObjectProvider<me.asu.aidev.questions.FormalQuestions> questions;
    private final AuthorityRepository repo;private final AuthorityBoundary boundary;private final AuthorityGrantService grants;
    private final BusinessActionPermissions permissions;private final PermissionActionPreparer preparer;private final PermissionTargetResolver targets;
    private final ObjectProvider<WorkflowPlanService> plans;private final ObjectProvider<ActionAdmissionService> admission;
    public BusinessPermissionController(AuthorityRepository repo,AuthorityBoundary boundary,AuthorityGrantService grants,
            BusinessActionPermissions permissions,PermissionActionPreparer preparer,PermissionTargetResolver targets,
            ObjectProvider<WorkflowPlanService> plans,ObjectProvider<ActionAdmissionService> admission) {
        this.repo=repo;this.boundary=boundary;this.grants=grants;this.permissions=permissions;this.preparer=preparer;this.targets=targets;this.plans=plans;this.admission=admission;
    }
    private <T>T read(String raw,Class<T> type) {if(raw.length()>32768)throw new IllegalArgumentException("Bounded request required");return repo.decode(raw,type);}
    private AuthorityBoundary.Principal principal(HttpServletRequest request) {return Objects.requireNonNull((AuthorityBoundary.Principal)request.getAttribute("authorityPrincipal"));}
    @PostMapping("/action-permissions") public Object issue(@RequestBody String raw) {return permissions.view(permissions.issue(read(raw,Issue.class),boundary.human()));}
    @PostMapping("/action-permissions/{id}/revoke") public Object revoke(@PathVariable String id) {permissions.revoke(id,boundary.human());return Map.of("state","REVOKED");}
    @GetMapping("/action-permissions") public Object list(HttpServletRequest request) {
        var p=principal(request);admission.getObject().enabled(p);return permissions.list(p.owner()).stream().filter(v->v.workerId().equals(p.worker())).map(permissions::view).toList();
    }
    @PostMapping("/permission-actions/prepare") public Object prepare(HttpServletRequest request,@RequestBody String raw) {return preparer.prepare(principal(request),read(raw,Selection.class));}
    @GetMapping("/permission-actions/{requestId}") public Object preparation(HttpServletRequest request,@PathVariable String requestId) {return preparer.read(principal(request),requestId);}
    @GetMapping("/permission-questions") public Object questionChoices(HttpServletRequest request,@RequestParam String permissionId,@RequestParam String planId) {
        var actor=principal(request);admission.getObject().enabled(actor);var p=permissions.get(permissionId);
        if(!p.ownerId().equals(actor.owner())||!p.workerId().equals(actor.worker())||!permissions.active(p)
                ||!Set.of(ActionScope.Action.A18_BUDGET,ActionScope.Action.A19_ANSWER).contains(p.action()))throw new SecurityException("Current question permission required");
        targets.plan(p.ownerId(),planId);
        if(p.scopeKind()==Scope.PLAN&&!p.scopeId().equals(planId))throw new SecurityException("Plan outside question permission");
        var choices=p.usedUses()>=p.maximumUses()?List.of():questions.getObject().list(planId).stream()
                .filter(q->q.ownerId().equals(p.ownerId())&&q.state()==me.asu.aidev.questions.QuestionContract.State.OPEN)
                .filter(q->(p.action()==ActionScope.Action.A19_ANSWER)==(q.kind()==me.asu.aidev.questions.QuestionContract.Kind.PROFESSIONAL))
                .filter(q->p.scopeKind()!=Scope.TASK||Objects.equals(p.scopeId(),q.source().taskId())).toList();
        return Map.of("permission",permissions.view(p),"questions",choices);
    }
    public record Empty() {}
    @PostMapping("/action-permissions/state") public Object state(@RequestBody String raw) {
        read(raw,Empty.class);String actor=boundary.human();grants.identityHuman(actor);
        var owners=repo.jdbc().queryForList("SELECT id FROM authority_owner WHERE actor=?",String.class,actor);
        if(owners.isEmpty())return Map.of("ownerId","","permissions",List.of(),"plans",List.of(),"tasks",List.of(),"workers",List.of(),"catalogue",catalogue(actor));
        if(owners.size()!=1)throw new SecurityException("Unique current owner required");String owner=owners.getFirst();
        var choices=new LinkedHashMap<String,String>();
        for(var plan:plans.getObject().list())try {targets.plan(owner,plan.id());choices.put(plan.id(),title(plan.spec().goal()));}catch(SecurityException ignored) { /* Not this actor's plan. */ }
        for(String id:repo.jdbc().queryForList("SELECT plan_id FROM execution_policy WHERE owner_id=? ORDER BY plan_id",String.class,owner))try {targets.plan(owner,id);choices.putIfAbsent(id,id);}catch(SecurityException ignored) { /* Conflicting facts are never selectable. */ }
        var taskChoices=new ArrayList<Map<String,String>>();
        for(var row:repo.jdbc().queryForList("SELECT id,instruction FROM work_task ORDER BY updated_at_epoch_ms DESC,id")) {
            if(taskChoices.size()>=100)break;
            try {String id=String.valueOf(row.get("id")),plan=targets.task(owner,id);taskChoices.add(Map.of("id",id,"planId",plan,"title",title((String)row.get("instruction"))));}
            catch(SecurityException ignored) { /* Never export another owner's task title. */ }
        }
        var workers=repo.jdbc().queryForList("SELECT w.id,w.runtime_type,w.status,t.owner_id FROM worker w LEFT JOIN authority_target t ON t.target_key='WORKER:'||w.id WHERE w.id NOT IN (SELECT worker_id FROM authority_identity) AND (t.owner_id IS NULL OR t.owner_id=?) ORDER BY w.id",owner);
        return Map.of("ownerId",owner,"permissions",permissions.list(owner).stream().map(permissions::view).toList(),
                "plans",choices.entrySet().stream().map(e->Map.of("id",e.getKey(),"title",e.getValue())).toList(),"tasks",taskChoices,"workers",workers,"catalogue",catalogue(actor));
    }
    private List<Map<String,Object>> catalogue(String actor) {
        List<Map<String,Object>> result=new ArrayList<>();
        for(var action:ActionScope.Action.values()) {
            boolean allowed;
            try {grants.human(actor,action.name());allowed=true;}catch(SecurityException error){allowed=false;}
            result.add(Map.of("action",action.name(),"label",label(action),"group",group(action),"scopes",scopes(action),"allowed",allowed));
        }
        return result;
    }
    private static String title(String value) {if(value==null||value.isBlank())return "未命名";String first=value.strip().split("\\R",2)[0];return first.length()>100?first.substring(0,100)+"…":first;}
    private static String label(ActionScope.Action action) {
        return switch(action) {
            case A01->"追加修复轮次";case A02->"设置执行或评审模型";case A03->"重试指定失败任务";case A04->"恢复独立评审";
            case A05->"确认计划";case A06->"跳转计划步骤";case A07->"停止计划";case A08->"撤回计划";case A09->"人工完成计划";case A10->"取消计划";
            case A11_APPROVE->"批准审批";case A11_REJECT->"拒绝审批";case A12_CREATE->"创建发布计划";case A12_EXECUTE->"执行一次发布";
            case A13->"调整运行并发";case A14->"排空普通 Worker";case A15->"离线普通 Worker";case A16->"归档计划";
            case A17_SCHEDULE->"排期删除";case A17_UNDO->"撤销排期删除";case A18_BUDGET->"批准具体额度追加";case A19_ANSWER->"回答工作问题";
        };
    }
    private static String group(ActionScope.Action action) {
        return switch(action) {
            case A01,A02,A03,A04->"任务执行与恢复";case A05,A06,A07,A08,A09,A10->"计划管理";
            case A11_APPROVE,A11_REJECT,A18_BUDGET,A19_ANSWER->"审批与工作问题";case A12_CREATE,A12_EXECUTE->"交付与发布";
            case A13,A14,A15->"Worker 与运行管理";default->"记录管理";
        };
    }
    @ExceptionHandler(SecurityException.class) public ResponseEntity<?> denied() {return ResponseEntity.status(403).body(Map.of("code","PERMISSION_SCOPE_DENIED","message","当前账号、秘书、角色或适用范围不允许此操作。"));}
    @ExceptionHandler({IllegalArgumentException.class,NoSuchElementException.class}) public ResponseEntity<?> invalid() {return ResponseEntity.unprocessableContent().body(Map.of("code","PERMISSION_INPUT_INVALID","message","请检查动作、有效期、次数和业务选择。"));}
    @ExceptionHandler(IllegalStateException.class) public ResponseEntity<?> changed() {return ResponseEntity.status(409).body(Map.of("code","PERMISSION_CHANGED","message","授权期、额度或原操作状态已变化，请先刷新核对。"));}
    @ExceptionHandler(org.springframework.dao.DataAccessException.class) public ResponseEntity<?> unavailable() {return ResponseEntity.status(503).body(Map.of("code","PERMISSION_STORAGE_UNCONFIRMED","message","无法确认登记结果，请按原请求核对，勿重新创建授权。"));}
}
