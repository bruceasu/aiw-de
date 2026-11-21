package me.asu.aidev.authority;

import java.util.*;
import me.asu.aidev.execution.guard.ExecutionAccounting;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
public class AuthorityPageController {
    @org.springframework.beans.factory.annotation.Autowired private me.asu.aidev.execution.guard.ToolApprovals toolApprovals;
    @org.springframework.beans.factory.annotation.Autowired private me.asu.aidev.execution.config.FactoryWorkerRegistry factoryWorkers;
    public record PendingItem(String id,String kind,String state,String summary,String href,long recordedAt,boolean needsHuman,boolean canAct) {}
    private static String url(String value){return java.net.URLEncoder.encode(value,java.nio.charset.StandardCharsets.UTF_8);}
    private static String preview(String value){return value.substring(0,Math.min(240,value.length()));}
    @PostMapping("/api/r1/authority/questions/inbox") @ResponseBody public Object inbox() {
        String actor=boundary.human();
        if(actor==null||boundary.delegated())return org.springframework.http.ResponseEntity.status(403).body(Map.of("error","Current human required"));
        boolean operate=!consoleSecurity.currentDelegationAuthority(actor,"A19_ANSWER").isEmpty(),approve=!consoleSecurity.currentDelegationAuthority(actor,"A18_BUDGET").isEmpty();
        if(!operate&&!approve)return org.springframework.http.ResponseEntity.status(403).body(Map.of("error","Operation or approval role required"));
        var items=new ArrayList<PendingItem>();
        if(operate)items.addAll(repo.jdbc().query("SELECT id,updated_ms,run_json FROM workflow_execution WHERE actor=? AND state='WAITING_USER' ORDER BY updated_ms DESC,id LIMIT 100",(rs,i)->{
            var run=repo.decode(rs.getString("run_json"),me.asu.aidev.workflow.application.WorkflowRuns.Run.class);
            var node=run.spec.nodes().stream().filter(n->n.key().equals(run.current)).findFirst().orElseThrow();
            return new PendingItem(run.id,"WORKFLOW","WAITING_USER",preview(node.instruction()),"/ui/workflows?execution="+url(run.id),rs.getLong("updated_ms"),true,true);
        },actor));
        items.addAll(repo.jdbc().query("""
            SELECT q.id,q.plan_id,q.state,q.payload_json,q.updated_ms FROM formal_question q
              JOIN authority_owner o ON o.id=q.owner_id WHERE o.actor=? AND q.state IN ('OPEN','ANSWERED')
             ORDER BY CASE WHEN q.state='OPEN' THEN 0 ELSE 1 END,q.updated_ms DESC,q.id LIMIT 100
            """,(rs,i)->{
                var q=repo.decode(rs.getString("payload_json"),me.asu.aidev.questions.QuestionContract.Question.class);
                boolean authorization=q.kind()==me.asu.aidev.questions.QuestionContract.Kind.AUTHORIZATION;
                return new PendingItem(rs.getString("id"),authorization?"AUTHORIZATION":"QUESTION",rs.getString("state"),preview(q.question()),
                        "/ui/questions?planId="+url(rs.getString("plan_id"))+"&questionId="+url(rs.getString("id")),rs.getLong("updated_ms"),rs.getString("state").equals("OPEN"),authorization?approve:operate);
            },actor));
        for(var item:toolApprovals.inbox()) {
            var a=item.approval();
            items.add(new PendingItem(a.id(),"TOOL_APPROVAL",a.state(),a.binding().action().name(),
                    "/ui/configurations?view=workbench&planId="+url(item.planId())+"&approvalId="+url(a.id()),item.createdAt(),a.state().equals("PENDING")&&a.quiescent(),approve));
        }
        items.sort(Comparator.comparing(PendingItem::needsHuman).reversed().thenComparing(Comparator.comparingLong(PendingItem::recordedAt).reversed()).thenComparing(PendingItem::id));
        return Map.of("items",items,"perSourceLimit",100,"observedAt",System.currentTimeMillis());
    }
    @org.springframework.beans.factory.annotation.Autowired private me.asu.aidev.webconsole.security.WebConsoleSecurityService consoleSecurity;
    @GetMapping("/ui/questions") public String questions(Model model) {
        String actor=Objects.requireNonNull(boundary.human(),"Authenticated human required");
        boolean operate=!consoleSecurity.currentDelegationAuthority(actor,"A19_ANSWER").isEmpty(),approve=!consoleSecurity.currentDelegationAuthority(actor,"A18_BUDGET").isEmpty();
        if(!operate&&!approve) throw new SecurityException("Current operation or approval role required");
        model.addAttribute("questionCanOperate",operate);model.addAttribute("questionCanApprove",approve);
        var ids=repo.jdbc().queryForList("SELECT p.plan_id FROM execution_policy p JOIN authority_owner o ON o.id=p.owner_id WHERE o.actor=? UNION SELECT p.plan_id FROM plan_run_owner p JOIN authority_owner o ON o.id=p.owner_id WHERE o.actor=? ORDER BY plan_id",String.class,actor,actor);
        Map<String,String> titles=new HashMap<>();
        plans.list().stream().filter(p->actor.equals(p.createdBy())).forEach(p->{
            String goal=p.spec().goal().strip().split("\\R",2)[0];
            titles.put(p.id(),goal.length()>80?goal.substring(0,80)+"…":goal);
        });
        model.addAttribute("questionPlans",ids);
        model.addAttribute("questionPlanChoices",ids.stream().map(id->Map.of("id",id,"title",titles.getOrDefault(id,id))).toList());
        model.addAttribute("questionAccountingEnforced",accounting.enforced());
        return "ui/questions";
    }
    @org.springframework.beans.factory.annotation.Autowired private me.asu.aidev.execution.config.ExecutionConfigurations configurations;
    public String configurations(Model model) { return configurations(model,""); }
    @GetMapping("/ui/configurations") public String configurations(Model model,@org.springframework.web.bind.annotation.RequestParam(defaultValue="") String view) {
        String actor=Objects.requireNonNull(boundary.human(),"Authenticated human required");
        var owners=repo.jdbc().queryForList("SELECT id FROM authority_owner WHERE actor=?",String.class,actor);
        model.addAttribute("ownerId",owners.size()==1?owners.getFirst():"");
        if(view.equals("workbench"))model.addAttribute("toolApprovalPlans",repo.jdbc().queryForList(
                "SELECT p.plan_id FROM execution_policy p JOIN authority_owner o ON o.id=p.owner_id WHERE o.actor=? ORDER BY p.plan_id",String.class,actor));
        model.addAttribute("categories",configurations.categories());
        model.addAttribute("methods",configurations.catalogue().methods());
        return view.equals("workbench")?"ui/worker-workbench":view.equals("observations")?"ui/execution-observations":view.equals("roles")?"ui/roles":"ui/configurations";
    }
    private final AuthorityRepository repo;private final AuthorityBoundary boundary;
    private final me.asu.aidev.workflow.application.WorkflowPlanService plans;
    private final ExecutionAccounting accounting;
    public AuthorityPageController(AuthorityRepository repo,AuthorityBoundary boundary,me.asu.aidev.workflow.application.WorkflowPlanService plans,ExecutionAccounting accounting) { this.repo=repo;this.boundary=boundary;this.plans=plans;this.accounting=accounting; }
    @GetMapping("/ui/agent-factory") public String agentFactory() {
        Objects.requireNonNull(boundary.human(),"Authenticated human required");
        return "ui/agent-factory";
    }
    @GetMapping("/ui/authority") public String authorityPage(Model model) {
        return page(model);
    }
    /** Keep the existing plan-management bookmark. */
    @GetMapping("/ui/delegation") public String delegation() {
        Objects.requireNonNull(boundary.human(),"Authenticated human required");
        return "redirect:/ui/authority#authority-plans";
    }
    public String page(Model model) {
        String actor=Objects.requireNonNull(boundary.human(),"Authenticated human required");
        var owners=repo.jdbc().queryForList("SELECT id FROM authority_owner WHERE actor=?",String.class,actor);
        model.addAttribute("owners",owners);
        model.addAttribute("accountingMode",accounting.mode());
        model.addAttribute("accountingEnforced",accounting.enforced());
        model.addAttribute("drafts",repo.encode(plans.list().stream().filter(p->p.state()==me.asu.aidev.workflow.domain.WorkflowPlan.State.DRAFT && p.createdBy().equals(actor))
                .map(p->Map.of("planId",p.id(),"version",p.version(),"specDigest",AuthorityRepository.hash(repo.encode(p.spec())),"businessSpec",p.spec())).toList()));
        model.addAttribute("workers",repo.jdbc().queryForList("SELECT worker_id,owner_id,generation,worker_boot,enabled_boot,read_scope FROM authority_identity WHERE identity_kind='AGENT' AND approval_state='APPROVED' AND owner_id IN (SELECT id FROM authority_owner WHERE actor=?)",actor).stream().map(row->{
            var result=new LinkedHashMap<>(row);result.put("enabled",boundary.boot().equals(result.remove("enabled_boot")));return result;
        }).toList());
        model.addAttribute("grants",repo.encode(repo.grants().stream().filter(g->owners.contains(g.owner())).toList()));
        model.addAttribute("targets",repo.encode(repo.jdbc().queryForList("SELECT target_key,generation,paused,restriction_revision FROM authority_target WHERE owner_id IN (SELECT id FROM authority_owner WHERE actor=?)",actor)));
        model.addAttribute("receipts",repo.encode(repo.jdbc().queryForList("SELECT request_id,state,result_json,updated_ms FROM authority_receipt WHERE owner_id IN (SELECT id FROM authority_owner WHERE actor=?) ORDER BY updated_ms DESC LIMIT 100",actor)));
        model.addAttribute("executionPolicies",repo.encode(repo.jdbc().queryForList("SELECT plan_id,revision,generation,revoked,paused,policy_json FROM execution_policy WHERE owner_id IN (SELECT id FROM authority_owner WHERE actor=?)",actor)));
        model.addAttribute("executionBudgets",repo.encode(repo.jdbc().queryForList("SELECT b.* FROM execution_budget b JOIN execution_policy p ON p.plan_id=b.plan_id JOIN authority_owner o ON o.id=p.owner_id WHERE o.actor=? ORDER BY b.plan_id,b.dimension,b.loop_id",actor)));
        model.addAttribute("executionEffects",repo.encode(repo.jdbc().queryForList("SELECT e.operation_id,e.task_id,e.attempt_id,e.tool_id,e.resource,e.state,e.result_digest,e.evidence_ref FROM execution_operation e JOIN execution_policy p ON p.plan_id=e.plan_id JOIN authority_owner o ON o.id=p.owner_id WHERE o.actor=? ORDER BY e.started_ms DESC LIMIT 100",actor)));
        model.addAttribute("executionMeters",repo.encode(repo.jdbc().queryForList("SELECT m.attempt_id,m.task_id,m.settled,m.elapsed_ms,m.cost_micros,m.usage_unknown,m.time_unknown,m.price_json FROM execution_meter m JOIN execution_policy p ON p.plan_id=m.plan_id JOIN authority_owner o ON o.id=p.owner_id WHERE o.actor=? ORDER BY m.started_ms DESC LIMIT 100",actor)));
        model.addAttribute("executionDeliveries",repo.encode(repo.jdbc().queryForList("SELECT d.task_id,d.attempt_id,d.digest,d.delivery_json,x.input_digest,x.config_digest FROM execution_delivery d JOIN execution_context x ON x.task_id=d.task_id JOIN execution_policy p ON p.plan_id=x.plan_id JOIN authority_owner o ON o.id=p.owner_id WHERE o.actor=? ORDER BY d.created_ms DESC LIMIT 100",actor)));
        model.addAttribute("stopCandidates",stopCandidates(actor,null));
        return "ui/authority";
    }

    @GetMapping("/api/r1/authority/execution-tasks/{task}/recovery") @ResponseBody
    public Object recovery(@PathVariable String task,jakarta.servlet.http.HttpSession session) {
        String actor=boundary.human();
        if(actor==null||boundary.delegated()||!consoleSecurity.role(session).canApprove())
            return org.springframework.http.ResponseEntity.status(403).body(Map.of("error","Approval permission is required to confirm a stop."));
        var candidates=stopCandidates(actor,task);
        if(candidates.isEmpty())return org.springframework.http.ResponseEntity.status(404).body(Map.of("error","No current execution is awaiting stop confirmation."));
        var candidate=candidates.getFirst();
        var effects=repo.jdbc().queryForList("""
                SELECT operation_id AS id,tool_id AS tool,resource,evidence_ref AS evidenceRef
                FROM execution_operation WHERE task_id=? AND state IN ('STARTED','UNKNOWN') ORDER BY started_ms,operation_id LIMIT 100
                """,task);
        return Map.of("candidate",candidate,"effects",effects);
    }

    private List<Map<String,Object>> stopCandidates(String actor,String task) {
        return repo.jdbc().query("""
                SELECT t.id AS task_id,a.id AS attempt_id,t.subject_id AS plan_id,a.attempt_no,a.worker_id,
                       COALESCE(json_extract(wf.run_json,'$.spec.title'),json_extract(p.spec_json,'$.title'),t.subject_id) AS plan_title,
                       t.lease_acquired_at_epoch_ms AS claimed_at,wf.id IS NOT NULL AS workflow,
                       CASE WHEN f.worker_id IS NOT NULL AND w.status='OFFLINE'
                                 AND w.registered_at_epoch_ms<=t.lease_acquired_at_epoch_ms THEN 1 ELSE 0 END AS local_exit
                  FROM work_task t JOIN execution_attempt a ON a.task_id=t.id AND a.lease_token=t.lease_token
                  JOIN flexible_workflow_plan p ON p.id=t.subject_id
                  LEFT JOIN workflow_execution wf ON wf.id=p.id
                  LEFT JOIN execution_context x ON x.task_id=t.id
                  LEFT JOIN execution_policy ep ON ep.plan_id=x.plan_id
                  LEFT JOIN plan_run_owner n ON n.plan_id=p.id
                  JOIN authority_owner o ON o.id=COALESCE(ep.owner_id,n.owner_id)
                  LEFT JOIN worker w ON w.id=a.worker_id
                  LEFT JOIN factory_worker_registration f ON f.worker_id=w.id
                 WHERE o.actor=? AND a.state='RUNNING' AND t.subject_type='flexible-plan'
                   AND t.state IN ('RUNNING','YIELDING') AND (t.lease_expires_at_epoch_ms<=? OR a.stop_requested_at_epoch_ms IS NOT NULL)
                   AND (x.task_id IS NOT NULL OR EXISTS (SELECT 1 FROM execution_run_session s WHERE s.attempt_id=a.id))
                   AND (? IS NULL OR t.id=?)
                 ORDER BY t.lease_expires_at_epoch_ms DESC,t.id LIMIT 100
                """,(rs,i)->{
                    var item=new LinkedHashMap<String,Object>();
                    String worker=rs.getString("worker_id");
                    item.put("taskId",rs.getString("task_id"));
                    item.put("attemptId",rs.getString("attempt_id"));
                    item.put("planId",rs.getString("plan_id"));
                    item.put("workflow",rs.getBoolean("workflow"));
                    item.put("label",preview(rs.getString("plan_title"))+" · #"+rs.getInt("attempt_no")+" · "+worker);
                    var evidence=rs.getBoolean("local_exit")?factoryWorkers.exitEvidence(worker,rs.getLong("claimed_at")):null;
                    item.put("evidenceRef",evidence==null?"":evidence.reference());
                    item.put("evidenceDigest",evidence==null?"":evidence.digest());
                    return item;
                },actor,System.currentTimeMillis(),task,task);
    }
}
