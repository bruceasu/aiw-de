package me.asu.aidev.authority;

import java.nio.charset.StandardCharsets;
import java.util.*;
import jakarta.servlet.http.HttpServletRequest;
import me.asu.aidev.workflow.application.WorkflowRuns;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/** Secretary requests use the same task executor and the existing A05 permission. */
@RestController
@RequestMapping("/api/r1/authority/task-delegations")
public class WorkflowDelegationController {
    public record Input(String requestId,String instruction,String memberId,List<String> requiredSkills) {}
    private final AuthorityRepository repo;
    private final AuthorityBoundary boundary;
    private final ActionAdmissionService admission;
    private final BusinessActionPermissions permissions;
    private final WorkflowRuns workflows;
    public WorkflowDelegationController(AuthorityRepository repo,AuthorityBoundary boundary,ActionAdmissionService admission,
            BusinessActionPermissions permissions,WorkflowRuns workflows) {
        this.repo=repo;this.boundary=boundary;this.admission=admission;this.permissions=permissions;this.workflows=workflows;
    }
    private AuthorityBoundary.Principal authenticate(HttpServletRequest request) {
        boundary.lock();var agent=(AuthorityBoundary.Principal)request.getAttribute("authorityPrincipal");
        if(agent==null)throw new SecurityException("Authenticated Agent required");
        admission.enabled(agent);
        if(!boundary.processBoot(agent.worker(),request.getHeader("X-AIW-Agent-Boot")))throw new SecurityException("Current Agent process required");
        return agent;
    }
    @GetMapping("/options") @Transactional public Object options(HttpServletRequest request) {
        var agent=authenticate(request);
        boolean allowed=permissions.list(agent.owner()).stream().anyMatch(p->p.workerId().equals(agent.worker())
            &&p.action()==ActionScope.Action.A05&&p.scopeKind()==ActionPermissionContract.Scope.ALL&&permissions.active(p)&&p.usedUses()<p.maximumUses());
        return Map.of("ownerId",agent.owner(),"agentId",agent.worker(),"platformInstanceId",boundary.instance(),
            "available",allowed,"requiredPermission","A05","requiresRepository",false,"workspaceTools",false);
    }
    @PostMapping @Transactional public Object submit(HttpServletRequest request,@RequestBody String raw) {
        if(raw.getBytes(StandardCharsets.UTF_8).length>32768)throw new IllegalArgumentException("Bounded selected task required");
        var input=repo.decode(raw,Input.class);if(input==null)throw new IllegalArgumentException("Selected task required");
        var agent=authenticate(request);
        return boundary.as(agent,()->{
            workflows.delegate(agent,input.requestId(),input.instruction(),input.memberId(),input.requiredSkills());
            return workflows.delegation(agent,input.requestId());
        });
    }
    @GetMapping("/{id}") @Transactional public Object read(HttpServletRequest request,@PathVariable String id) {
        var agent=authenticate(request);return boundary.as(agent,()->workflows.delegation(agent,id));
    }
    @ExceptionHandler(SecurityException.class) public ResponseEntity<?> denied() {return ResponseEntity.status(403).body(Map.of("code","TASK_DELEGATION_DENIED"));}
    @ExceptionHandler(NoSuchElementException.class) public ResponseEntity<?> missing() {return ResponseEntity.status(404).body(Map.of("code","TASK_DELEGATION_NOT_FOUND"));}
    @ExceptionHandler(IllegalArgumentException.class) public ResponseEntity<?> invalid() {return ResponseEntity.unprocessableContent().body(Map.of("code","TASK_DELEGATION_INVALID"));}
    @ExceptionHandler(IllegalStateException.class) public ResponseEntity<?> changed() {return ResponseEntity.status(409).body(Map.of("code","TASK_DELEGATION_CHANGED"));}
}
