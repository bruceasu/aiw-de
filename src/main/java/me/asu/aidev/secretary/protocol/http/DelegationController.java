package me.asu.aidev.secretary.protocol.http;

import jakarta.servlet.http.HttpSession;
import java.util.*;
import me.asu.aidev.secretary.application.*;
import me.asu.aidev.secretary.jdbc.DelegationRepository;
import me.asu.aidev.secretary.jdbc.DelegationRepository.Grant;
import me.asu.aidev.webconsole.security.WebConsoleSecurityService;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

/** Browser management uses the existing session and CSRF boundary, never a Worker credential. */
@Controller
@RequestMapping("/ui")
public class DelegationController {
    private DelegationTemplates templates;
    @org.springframework.beans.factory.annotation.Autowired
    public void setTemplates(DelegationTemplates templates) { this.templates=templates; }
    private final WebConsoleSecurityService security;
    private final DelegationIdentityService identities;
    private final DelegationGrantService grants;
    private final DelegationRequests requests;
    private final DelegationBudgets budgets;
    private final DelegationTargets targets;
    private final DelegationDrafts drafts;
    private final DelegationRepository repo;
    private final JdbcTemplate jdbc;
    private final me.asu.aidev.ai.application.AiProfileCatalog profiles;
    public DelegationController(WebConsoleSecurityService security,DelegationIdentityService identities,
            DelegationGrantService grants,DelegationRequests requests,DelegationBudgets budgets,DelegationTargets targets,
            DelegationDrafts drafts,DelegationRepository repo,JdbcTemplate jdbc,me.asu.aidev.ai.application.AiProfileCatalog profiles) {
        this.security=security;this.identities=identities;this.grants=grants;this.requests=requests;this.budgets=budgets;
        this.targets=targets;this.drafts=drafts;this.repo=repo;this.jdbc=jdbc;this.profiles=profiles;
    }
    private String actor(HttpSession session) {
        if(!security.authenticated(session)) throw new SecurityException("Console authentication required");
        return security.actor(session);
    }
    private void action(HttpSession session,String action) {
        if(security.currentDelegationAuthority(actor(session),action).isBlank()) throw new SecurityException("Current role does not authorize this action");
    }
    private void writer(HttpSession session) {
        var role=security.role(session);
        if(!role.canOperate() && !role.canApprove() && !role.canAdmin()) throw new SecurityException("A human decision role is required");
    }
    @GetMapping("/delegation") public String page(HttpSession session) { actor(session);return "ui/delegation"; }
    @GetMapping("/api/delegation") @ResponseBody public Object view(HttpSession session) {
        String actor=actor(session);boolean admin=security.role(session).canAdmin();
        var visible=grants.list(actor,admin);var ids=visible.stream().map(Grant::id).collect(java.util.stream.Collectors.toSet());
        var keys=new HashSet<String>();var views=new ArrayList<Map<String,Object>>();
        for(var grant:visible) {
            String key="";String availability;
            try { key=targets.key(grant.rule());keys.add(key);availability=grants.current(grant)?(grants.permitted(grant)?"Rule available; delegate must also be live and enabled":"Prohibited or conflicting"):"Draft, expired, revoked or current owner role changed"; }
            catch(RuntimeException error) { availability="Target evidence is unavailable; human handling required"; }
            views.add(Map.of("grant",grant,"targetKey",key,"availability",availability));
        }
        return Map.of("identities",identities.list(),"grants",views,"budgets",budgets.list().stream().filter(b->admin || keys.contains(b.target())).toList(),
                "requests",requests.list().stream().filter(r->admin || ids.contains(r.grantId())).toList(),
                "audit",jdbc.queryForList("SELECT * FROM delegation_audit ORDER BY created_ms DESC,id LIMIT 200").stream()
                    .filter(row->admin || ids.contains(row.get("grant_id")) || keys.contains(row.get("target_key"))).toList(),
                "profiles",profiles.approved(me.asu.aidev.work.domain.TaskType.EXTERNAL_WORK),"catalogVersion",profiles.version(),"administrator",admin,
                "legacyRequests",admin?identities.legacyRequests():List.of());
    }
    public record Enrollment(String workerId,String key) {}
    public record EnableIdentity(long generation,boolean enabled,boolean confirmed) {}
    public record Revision(long revision,boolean confirmed) {}
    public record Text(String text) {}
    public record Supplement(long amount) {}
    public record Recovery(boolean confirmedStopped,String reason) {}
    @PostMapping("/api/delegation/legacy-requests/{id}/recover") @ResponseBody public Object recover(@PathVariable String id,@RequestBody Recovery input,HttpSession session) {
        security.requireAdmin(session);identities.recoverLegacy(id,input.confirmedStopped(),input.reason(),actor(session));return Map.of("recovered",true);
    }
    @GetMapping("/api/delegation/grants/{id}/history") @ResponseBody public Object history(@PathVariable String id,HttpSession session) {
        Grant current=repo.get(id);if(!current.owner().equals(actor(session))) security.requireAdmin(session);
        return jdbc.queryForList("SELECT snapshot FROM delegation_history WHERE grant_id=? ORDER BY revision",String.class,id).stream().map(value->{
            Grant grant=repo.decode(value,Grant.class);var row=new LinkedHashMap<String,Object>();
            row.put("revision",grant.revision());row.put("originalText",grant.originalText());row.put("rule",grant.rule());row.put("expiresMs",grant.expiresMs());
            row.put("templateId",grant.templateId());row.put("templateRevision",grant.templateRevision());
            if(grant.templateId()!=null) row.put("originTemplate",jdbc.queryForList("SELECT snapshot FROM delegation_template_history WHERE template_id=? AND revision=? AND owner=?",
                    String.class,grant.templateId(),grant.templateRevision(),grant.owner()).stream().map(raw->repo.decode(raw,Map.class)).toList());
            return row;
        }).toList();
    }
    public record TemplateEdit(long revision,String originalText,me.asu.aidev.secretary.domain.DelegationRule rule) {}
    @GetMapping("/api/delegation/templates") @ResponseBody public Object templates(HttpSession session) { return templates.list(actor(session)); }
    @PostMapping("/api/delegation/grants/{id}/template") @ResponseBody public Object template(@PathVariable String id,@RequestBody Revision input,HttpSession session) {
        writer(session);return templates.create(id,input.revision(),actor(session));
    }
    @PostMapping("/api/delegation/templates/{id}/edit") @ResponseBody public Object templateEdit(@PathVariable String id,@RequestBody TemplateEdit input,HttpSession session) {
        writer(session);return templates.edit(id,input.revision(),input.originalText(),input.rule(),actor(session));
    }
    @PostMapping("/api/delegation/templates/{id}/delete") @ResponseBody public Object templateDelete(@PathVariable String id,@RequestBody Revision input,HttpSession session) {
        writer(session);templates.delete(id,input.revision(),actor(session));return Map.of("deleted",true);
    }
    @PostMapping("/api/delegation/identities") @ResponseBody public Object enroll(@RequestBody Enrollment input,HttpSession session) {
        security.requireAdmin(session);identities.enroll(input.workerId(),input.key(),actor(session));return Map.of("state","DISABLED");
    }
    @PostMapping("/api/delegation/identities/{worker}/enabled") @ResponseBody public Object enabled(@PathVariable String worker,@RequestBody EnableIdentity input,HttpSession session) {
        security.requireAdmin(session);identities.enabled(worker,input.generation(),input.enabled(),input.confirmed(),actor(session));return Map.of("updated",true);
    }
    @PostMapping("/api/delegation/grants") @ResponseBody public Object save(@RequestBody DelegationGrantService.Draft input,HttpSession session) {
        writer(session);return grants.save(input,actor(session));
    }
    @PostMapping("/api/delegation/grants/{id}/enable") @ResponseBody public Object enable(@PathVariable String id,@RequestBody Revision input,HttpSession session) {
        action(session,repo.get(id).rule().action().name());return grants.enable(id,input.revision(),input.confirmed(),actor(session));
    }
    @PostMapping("/api/delegation/grants/{id}/revoke") @ResponseBody public Object revoke(@PathVariable String id,@RequestBody Revision input,HttpSession session) {
        action(session,repo.get(id).rule().action().name());return grants.revoke(id,input.revision(),actor(session));
    }
    @PostMapping("/api/delegation/grants/{id}/supplement") @ResponseBody public Object supplement(@PathVariable String id,@RequestBody Supplement input,HttpSession session) {
        Grant grant=grants.owned(id,actor(session));action(session,grant.rule().action().name());
        budgets.supplement(targets.key(grant.rule()),grant.rule().action().name(),input.amount(),actor(session));return Map.of("added",input.amount());
    }
    private void requestAuthority(String id,HttpSession session) {
        var request=requests.list().stream().filter(r->r.id().equals(id)).findFirst().orElseThrow();
        Grant source=repo.decode(jdbc.queryForObject("SELECT snapshot FROM delegation_history WHERE grant_id=? AND revision=?",String.class,
                request.grantId(),request.grantRevision()),Grant.class);
        if(!source.owner().equals(actor(session))) security.requireAdmin(session);
        action(session,source.rule().action().name());
    }
    @PostMapping("/api/delegation/requests/{id}/human") @ResponseBody public Object human(@PathVariable String id,HttpSession session) {
        requestAuthority(id,session);requests.control(id,actor(session),false);return Map.of("state","HUMAN");
    }
    @PostMapping("/api/delegation/requests/{id}/return") @ResponseBody public Object giveBack(@PathVariable String id,HttpSession session) {
        requestAuthority(id,session);requests.control(id,actor(session),true);return Map.of("state","RETURNED");
    }
    @PostMapping("/api/delegation/drafts") @ResponseBody public Object draft(@RequestBody Text input,HttpSession session) {
        writer(session);return Map.of("id",drafts.create(actor(session),input.text()),"state","QUEUED");
    }
    @GetMapping("/api/delegation/drafts/{id}") @ResponseBody public Object draftResult(@PathVariable String id,HttpSession session) { return drafts.result(id,actor(session)); }
    @ExceptionHandler(SecurityException.class) public ResponseEntity<?> denied(SecurityException e) { return ResponseEntity.status(403).body(Map.of("error",e.getMessage())); }
    @ExceptionHandler(IllegalArgumentException.class) public ResponseEntity<?> invalid(IllegalArgumentException e) { return ResponseEntity.badRequest().body(Map.of("error",e.getMessage())); }
    @ExceptionHandler(IllegalStateException.class) public ResponseEntity<?> conflict(IllegalStateException e) { return ResponseEntity.status(409).body(Map.of("error",e.getMessage())); }
}
