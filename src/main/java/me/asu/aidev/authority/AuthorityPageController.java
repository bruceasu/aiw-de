package me.asu.aidev.authority;

import java.util.*;
import me.asu.aidev.execution.guard.ExecutionAccounting;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AuthorityPageController {
    @org.springframework.beans.factory.annotation.Autowired private me.asu.aidev.webconsole.security.WebConsoleSecurityService consoleSecurity;
    @GetMapping("/ui/questions") public String questions(Model model) {
        String actor=Objects.requireNonNull(boundary.human(),"Authenticated human required");
        boolean operate=!consoleSecurity.currentDelegationAuthority(actor,"A19_ANSWER").isEmpty(),approve=!consoleSecurity.currentDelegationAuthority(actor,"A18_BUDGET").isEmpty();
        if(!operate&&!approve) throw new SecurityException("Current operation or approval role required");
        model.addAttribute("questionCanOperate",operate);model.addAttribute("questionCanApprove",approve);
        var ids=repo.jdbc().queryForList("SELECT p.plan_id FROM execution_policy p JOIN authority_owner o ON o.id=p.owner_id WHERE o.actor=? ORDER BY p.plan_id",String.class,actor);
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
        model.addAttribute("categories",configurations.categories());
        model.addAttribute("methods",configurations.catalogue().methods());
        return view.equals("workbench")?"ui/worker-workbench":view.equals("observations")?"ui/execution-observations":view.equals("roles")?"ui/roles":"ui/configurations";
    }
    private final AuthorityRepository repo;private final AuthorityBoundary boundary;
    private final me.asu.aidev.workflow.application.WorkflowPlanService plans;
    private final ExecutionAccounting accounting;
    public AuthorityPageController(AuthorityRepository repo,AuthorityBoundary boundary,me.asu.aidev.workflow.application.WorkflowPlanService plans,ExecutionAccounting accounting) { this.repo=repo;this.boundary=boundary;this.plans=plans;this.accounting=accounting; }
    @GetMapping("/ui/authority") public String page(Model model) {
        String actor=Objects.requireNonNull(boundary.human(),"Authenticated human required");
        var owners=repo.jdbc().queryForList("SELECT id FROM authority_owner WHERE actor=?",String.class,actor);
        model.addAttribute("owners",owners);
        model.addAttribute("accountingMode",accounting.mode());
        model.addAttribute("accountingEnforced",accounting.enforced());
        model.addAttribute("drafts",repo.encode(plans.list().stream().filter(p->p.state()==me.asu.aidev.workflow.domain.WorkflowPlan.State.DRAFT && p.createdBy().equals(actor))
                .map(p->Map.of("planId",p.id(),"version",p.version(),"specDigest",AuthorityRepository.hash(repo.encode(p.spec())),"businessSpec",p.spec())).toList()));
        model.addAttribute("workers",repo.jdbc().queryForList("SELECT worker_id,owner_id,generation,worker_boot,enabled_boot,read_scope FROM authority_identity WHERE owner_id IN (SELECT id FROM authority_owner WHERE actor=?)",actor).stream().map(row->{
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
        return "ui/authority";
    }
}
