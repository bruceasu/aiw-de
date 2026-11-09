package me.asu.aidev.authority;

import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;

@RestController
@RequestMapping("/api/r1/authority")
public class AuthorityController {
    private final AuthorityRepository repo;private final AuthorityBoundary boundary;private final AuthorityGrantService grants;
    private final ActionAdmissionService actions;private final AuthorityControlService controls;private final CoordinationService coordination;
    private final AuthorityBudgets budgets;
    private final AgentMessageService agentMessages;
    private final AgentIdentityService agentIdentities;
    public AuthorityController(AuthorityRepository repo,AuthorityBoundary boundary,AuthorityGrantService grants,ActionAdmissionService actions,
            AuthorityControlService controls,CoordinationService coordination,AuthorityBudgets budgets,AgentMessageService agentMessages,
            AgentIdentityService agentIdentities) {
        this.repo=repo;this.boundary=boundary;this.grants=grants;this.actions=actions;this.controls=controls;this.coordination=coordination;
        this.budgets=budgets;this.agentMessages=agentMessages;this.agentIdentities=agentIdentities;
    }
    private String human() { return Objects.requireNonNull(boundary.human(),"Verified human required"); }
    private Map<String,Object> grantView(AuthorityRepository.Grant grant) {
        var view=new LinkedHashMap<String,Object>();
        view.put("id",grant.id());view.put("ownerId",grant.owner());view.put("agentId",grant.workerId());
        view.put("revision",grant.revision());view.put("state",grant.state());view.put("rule",grant.rule());
        view.put("authority",grant.authority());view.put("effectiveAt",grant.effectiveMs());
        view.put("expiresAt",grant.expiresMs());view.put("targetKey",grant.targetKey());
        return view;
    }
    private AuthorityBoundary.Principal principal(HttpServletRequest request) { return (AuthorityBoundary.Principal)request.getAttribute("authorityPrincipal"); }
    public record GrantInput(String ownerId,String agentId,ActionScope scope,long expiresAt) {}
    public record TargetInput(String target,long generation) {}
    public record SupplementInput(String target,String action,long amount) {}
    public record ManageInput(String ownerId,String agentId,String planId,long version,String specDigest,long expiresAt) {}
    public record ReconcileInput(long attempt,String conclusion,String evidenceRef) {}
    public record EnableInput(long generation,String agentBoot) {}
    public record SessionInput(String agentBoot) {}
    public record MessageAckInput(String deliveryToken) {}
    @PostMapping("/sessions") public Object session(HttpServletRequest request,@RequestBody String raw) { return grants.session(principal(request),repo.decode(raw,SessionInput.class).agentBoot()); }
    @GetMapping("/identity") public Object identity(HttpServletRequest request) {
        var actor=principal(request);actions.enabled(actor);
        return Map.of("ownerId",actor.owner(),"agentId",actor.worker(),"generation",actor.generation(),"platformInstanceId",repo.jdbc().queryForObject("SELECT instance_id FROM authority_metadata WHERE id=1",String.class));
    }
    /** Authenticated connection metadata only; a disabled process cannot perform actions. */
    @GetMapping("/connection-state") public Object connectionState(HttpServletRequest request) {
        var actor=principal(request);
        String approvalState=repo.jdbc().queryForObject("SELECT approval_state FROM authority_identity WHERE worker_id=? AND identity_kind='AGENT'",String.class,actor.worker());
        return Map.of("ownerId",actor.owner(),"agentId",actor.worker(),"generation",actor.generation(),
                "enabled",actor.enabled(),
                "approvalState",approvalState,
                "platformInstanceId",repo.jdbc().queryForObject("SELECT instance_id FROM authority_metadata WHERE id=1",String.class));
    }
    @PostMapping("/effects/{id}/reconcile") public Object reconcile(@PathVariable String id,@RequestBody String raw) {
        var input=repo.decode(raw,ReconcileInput.class);controls.reconcile(id,input.attempt(),input.conclusion(),input.evidenceRef(),human());return Map.of("state","RECONCILED_NO_AUTOMATIC_REPLAY");
    }
    @PostMapping("/owners/bind") public Object bind() { return Map.of("ownerId",grants.bind(human())); }
    @PostMapping("/agents/invitations") public Object agentInvitation(@RequestBody String raw) {
        return agentIdentities.invitation(repo.decode(raw,AgentIdentityService.InvitationInput.class),human());
    }
    @PostMapping("/agents/pair") public Object pairAgent(HttpServletRequest request,@RequestBody String raw) {
        if(raw.length()>4096) throw new IllegalArgumentException("Pairing request too large");
        return agentIdentities.pair(repo.decode(raw,AgentIdentityService.PairInput.class),request);
    }
    @PostMapping("/agents/local/register") public Object registerLocalAgent(HttpServletRequest request,@RequestBody String raw) {
        if(raw.length()>8192) throw new IllegalArgumentException("Local Agent enrollment request too large");
        return agentIdentities.registerLocal(repo.decode(raw,AgentIdentityService.LocalRegistrationInput.class),request);
    }
    @PostMapping("/agents/{id}/approve-pairing") public Object approvePairing(@PathVariable String id,@RequestBody String raw) {
        if(raw.length()>8192) throw new IllegalArgumentException("Pairing approval request too large");
        return agentIdentities.approvePairing(id,repo.decode(raw,AgentIdentityService.PairingApprovalInput.class),human());
    }
    @PostMapping("/agents/certificate-rotation/request") public Object requestCertificateRotation(HttpServletRequest request) {
        return agentIdentities.requestCertificateRotation(principal(request),request);
    }
    @GetMapping("/agents/{id}/certificate-rotations") public Object certificateRotations(@PathVariable String id) {
        return agentIdentities.certificateRotations(id,human());
    }
    @PostMapping("/agents/{id}/certificate-rotations/{rotationId}/{operation:approve|reject}")
    public Object decideCertificateRotation(@PathVariable String id,@PathVariable String rotationId,@PathVariable String operation) {
        return agentIdentities.decideCertificateRotation(id,rotationId,operation.equals("approve"),human());
    }
    @PostMapping("/agents/{id}/{operation:enable|disable}") public Object enable(@PathVariable String id,@PathVariable String operation,@RequestBody String raw) {
        var input=repo.decode(raw,EnableInput.class);grants.enabled(id,input.generation(),input.agentBoot(),operation.equals("enable"),human());return Map.of("state",operation.equals("enable")?"ENABLED":"DISABLED");
    }
    @PostMapping("/agents/{id}/credential/revoke") public Object revokeAgentCredential(@PathVariable String id) {
        return agentIdentities.revokeCredential(id,human());
    }
    @PostMapping("/grants") public Object issue(@RequestBody String raw) {
        var input=repo.decode(raw,GrantInput.class);return grantView(grants.issue(input.ownerId(),input.agentId(),input.scope(),input.expiresAt(),human()));
    }
    @PostMapping("/grants/{id}/revoke") public Object revoke(@PathVariable String id) { grants.revoke(id,human());return Map.of("state","REVOKED"); }
    @PostMapping("/targets/{id}/{operation:takeover|return}") public Object target(@PathVariable String id,@PathVariable String operation,@RequestBody String raw) {
        var input=repo.decode(raw,TargetInput.class);
        if(!id.equals(AuthorityRepository.hash(input.target()))) throw new IllegalArgumentException("Exact target reference required");
        controls.returnTarget(input.target(),input.generation(),operation.equals("return"),human());return Map.of("state",operation.equals("return")?"RETURNED":"HUMAN");
    }
    @PostMapping("/restrictions/{id}/revoke") public Object revokeRestriction(@PathVariable String id) { controls.removeRestriction(id,human());return Map.of("state","PAUSED"); }
    @PostMapping("/budgets/{id}/supplement") public Object supplement(@PathVariable String id,@RequestBody String raw) {
        var input=repo.decode(raw,SupplementInput.class);if(!id.equals(AuthorityRepository.hash(input.target()+"\n"+input.action()))) throw new IllegalArgumentException("Exact budget reference required");
        controls.supplement(input.target(),input.action(),input.amount(),human(),budgets);return Map.of("added",input.amount());
    }
    @PostMapping("/managed-plans") public Object manage(@RequestBody String raw) {
        var input=repo.decode(raw,ManageInput.class);coordination.adopt(input.ownerId(),input.agentId(),input.planId(),input.version(),input.specDigest(),input.expiresAt(),human());return Map.of("state","ADOPTED");
    }
    @GetMapping("/grants") public Object list(HttpServletRequest request) {
        // This is a read-only pull of this Agent's own authority, also available while execution is disabled.
        var actor=principal(request);
        return repo.grants().stream().filter(g->g.owner().equals(actor.owner()) && g.workerId().equals(actor.worker())).map(this::grantView).toList();
    }
    @GetMapping("/state") public Object state(HttpServletRequest request,@RequestParam String grantId) { return actions.state(principal(request),grantId); }
    @GetMapping("/target-state") public Object targetState(HttpServletRequest request,@RequestParam String target) { return actions.targetState(principal(request),target); }
    @GetMapping("/management-state") public Object managementState(HttpServletRequest request,@RequestParam String planId) { return coordination.state(principal(request),planId); }
    @GetMapping("/receipts/{id}") public Object receipt(HttpServletRequest request,@PathVariable String id) { return actions.receipt(principal(request),id); }
    @PostMapping("/actions") public Object action(HttpServletRequest request,@RequestBody String raw) {
        var receipt=actions.apply(principal(request),repo.decode(raw,ActionAdmissionService.Envelope.class));return response(receipt);
    }
    @GetMapping("/messages") public Object messages(HttpServletRequest request,@RequestParam(defaultValue="20") int limit) {
        return agentMessages.claim(principal(request),limit);
    }
    @PostMapping("/messages/{id}/ack") public Object acknowledgeMessage(HttpServletRequest request,@PathVariable String id,@RequestBody String raw) {
        return agentMessages.acknowledge(principal(request),id,repo.decode(raw,MessageAckInput.class).deliveryToken());
    }
    @GetMapping("/agents/{id}/messages/dead") public Object deadMessages(@PathVariable String id) {
        return agentMessages.dead(id,human());
    }
    @PostMapping("/agents/{id}/messages/{messageId}/retry") public Object retryDeadMessage(@PathVariable String id,@PathVariable String messageId) {
        grants.identityHuman(human());agentMessages.retryDead(id,messageId,human());return Map.of("state","READY");
    }
    @PostMapping("/restrictions") public Object restrict(HttpServletRequest request,@RequestBody String raw) { return controls.restrict(principal(request),repo.decode(raw,AuthorityControlService.Restriction.class)); }
    @PostMapping("/coordination") public Object coordinate(HttpServletRequest request,@RequestBody String raw) { return response(coordination.advance(principal(request),repo.decode(raw,CoordinationService.Advance.class))); }
    private Object response(ActionAdmissionService.Receipt receipt) {
        int status=receipt.state().equals("REJECTED")?422:Set.of("ADMITTED","IN_PROGRESS","UNKNOWN").contains(receipt.state())?202:200;
        return ResponseEntity.status(status).body(receipt);
    }
    @ExceptionHandler(SecurityException.class) public ResponseEntity<?> denied() { return ResponseEntity.status(403).body(Map.of("code","SCOPE_DENIED","message","Current owner, identity and action authority required")); }
    @ExceptionHandler({IllegalArgumentException.class,NoSuchElementException.class}) public ResponseEntity<?> invalid() { return ResponseEntity.status(422).body(Map.of("code","INVALID_ACTION","message","Invalid or unavailable action input")); }
    @ExceptionHandler(IllegalStateException.class) public ResponseEntity<?> conflict() { return ResponseEntity.status(409).body(Map.of("code","STATE_CHANGED","message","Reload authoritative state or query the original request receipt")); }
}
