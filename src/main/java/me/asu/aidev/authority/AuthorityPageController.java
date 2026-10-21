package me.asu.aidev.authority;

import java.util.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AuthorityPageController {
    private final AuthorityRepository repo;private final AuthorityBoundary boundary;
    private final me.asu.aidev.workflow.application.WorkflowPlanService plans;
    public AuthorityPageController(AuthorityRepository repo,AuthorityBoundary boundary,me.asu.aidev.workflow.application.WorkflowPlanService plans) { this.repo=repo;this.boundary=boundary;this.plans=plans; }
    @GetMapping("/ui/authority") public String page(Model model) {
        String actor=Objects.requireNonNull(boundary.human(),"Authenticated human required");
        var owners=repo.jdbc.queryForList("SELECT id FROM authority_owner WHERE actor=?",String.class,actor);
        model.addAttribute("owners",owners);
        model.addAttribute("drafts",repo.encode(plans.list().stream().filter(p->p.state()==me.asu.aidev.workflow.domain.WorkflowPlan.State.DRAFT && p.createdBy().equals(actor))
                .map(p->Map.of("planId",p.id(),"version",p.version(),"specDigest",AuthorityRepository.hash(repo.encode(p.spec())),"businessSpec",p.spec())).toList()));
        model.addAttribute("workers",repo.jdbc.queryForList("SELECT worker_id,owner_id,generation,worker_boot,enabled_boot,read_scope FROM authority_identity WHERE owner_id IN (SELECT id FROM authority_owner WHERE actor=?)",actor).stream().map(row->{
            var result=new LinkedHashMap<>(row);result.put("enabled",boundary.boot().equals(result.remove("enabled_boot")));return result;
        }).toList());
        model.addAttribute("grants",repo.encode(repo.grants().stream().filter(g->owners.contains(g.owner())).toList()));
        model.addAttribute("targets",repo.encode(repo.jdbc.queryForList("SELECT target_key,generation,paused,restriction_revision FROM authority_target WHERE owner_id IN (SELECT id FROM authority_owner WHERE actor=?)",actor)));
        model.addAttribute("receipts",repo.encode(repo.jdbc.queryForList("SELECT request_id,state,result_json,updated_ms FROM authority_receipt WHERE owner_id IN (SELECT id FROM authority_owner WHERE actor=?) ORDER BY updated_ms DESC LIMIT 100",actor)));
        return "ui/authority";
    }
}
