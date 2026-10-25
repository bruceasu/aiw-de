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
    public AuthorityController(AuthorityRepository repo,AuthorityBoundary boundary,AuthorityGrantService grants,ActionAdmissionService actions,
            AuthorityControlService controls,CoordinationService coordination,AuthorityBudgets budgets) {
        this.repo=repo;this.boundary=boundary;this.grants=grants;this.actions=actions;this.controls=controls;this.coordination=coordination;this.budgets=budgets;
    }
    private String human() { return Objects.requireNonNull(boundary.human(),"Verified human required"); }
    private AuthorityBoundary.Principal principal(HttpServletRequest request) { return (AuthorityBoundary.Principal)request.getAttribute("authorityPrincipal"); }
    public record IdentityInput(String ownerId,String workerId,String credential,List<String> readScope) {}
    public record RegistrationInput(String credential,List<String> readScope) {}
    public record GrantInput(String ownerId,String workerId,ActionScope scope,long expiresAt) {}
    public record TargetInput(String target,long generation) {}
    public record SupplementInput(String target,String action,long amount) {}
    public record ManageInput(String ownerId,String workerId,String planId,long version,String specDigest,long expiresAt) {}
    public record ReconcileInput(long attempt,String conclusion,String evidenceRef) {}
    public record EnableInput(long generation,String workerBoot) {}
    public record SessionInput(String workerBoot) {}
    @PostMapping("/sessions") public Object session(HttpServletRequest request,@RequestBody String raw) { return grants.session(principal(request),repo.decode(raw,SessionInput.class).workerBoot()); }
    @GetMapping("/identity") public Object identity(HttpServletRequest request) {
        var actor=principal(request);actions.enabled(actor);
        return Map.of("ownerId",actor.owner(),"workerId",actor.worker(),"generation",actor.generation(),"platformInstanceId",repo.jdbc().queryForObject("SELECT instance_id FROM authority_metadata WHERE id=1",String.class));
    }
    /** Authenticated connection metadata only; a disabled process cannot perform actions. */
    @GetMapping("/connection-state") public Object connectionState(HttpServletRequest request) {
        var actor=principal(request);
        return Map.of("ownerId",actor.owner(),"workerId",actor.worker(),"generation",actor.generation(),
                "enabled",actor.enabled(),"authorization",SecretaryAuthorizationNotice.publicState(repo,actor),
                "platformInstanceId",repo.jdbc().queryForObject("SELECT instance_id FROM authority_metadata WHERE id=1",String.class));
    }
    @PostMapping("/effects/{id}/reconcile") public Object reconcile(@PathVariable String id,@RequestBody String raw) {
        var input=repo.decode(raw,ReconcileInput.class);controls.reconcile(id,input.attempt(),input.conclusion(),input.evidenceRef(),human());return Map.of("state","RECONCILED_NO_AUTOMATIC_REPLAY");
    }
    @PostMapping("/owners/bind") public Object bind() { return Map.of("ownerId",grants.bind(human())); }
    @PostMapping("/workers/new") public Object register(@RequestBody String raw) {
        RegistrationInput input;
        try { input=repo.decode(raw,RegistrationInput.class); }
        catch(IllegalArgumentException error) { return invalidIdentityInput("request","登记请求格式不正确，请刷新后重试。身份标识由系统生成。"); }
        if(input==null) return invalidIdentityInput("request","登记请求不能为空。");
        return grants.register(input.credential(),input.readScope(),human());
    }
    @PostMapping("/workers/{id}/credential") public Object rotateCredential(@PathVariable String id,@RequestBody String raw) {
        RegistrationInput input;
        try { input=repo.decode(raw,RegistrationInput.class); }
        catch(IllegalArgumentException error) { return invalidIdentityInput("request","凭证轮换请求格式不正确，请刷新后重新选择秘书。"); }
        if(input==null) return invalidIdentityInput("request","凭证轮换请求不能为空。");
        return grants.rotateCredential(id,input.credential(),input.readScope(),human());
    }
    @PostMapping("/workers") public Object enroll(@RequestBody String raw) {
        IdentityInput input;
        try { input=repo.decode(raw,IdentityInput.class); }
        catch(IllegalArgumentException error) { return invalidIdentityInput("request","登记请求格式不正确，请刷新页面后重新提交。请求仅接受 ownerId、workerId、credential 和列表类型的 readScope。"); }
        if(input==null) return invalidIdentityInput("request","登记请求不能为空。");
        grants.enroll(input.ownerId(),input.workerId(),input.credential(),input.readScope(),human());return Map.of("state","DISABLED");
    }
    @PostMapping("/workers/{id}/{operation:enable|disable}") public Object enable(@PathVariable String id,@PathVariable String operation,@RequestBody String raw) {
        var input=repo.decode(raw,EnableInput.class);grants.enabled(id,input.generation(),input.workerBoot(),operation.equals("enable"),human());return Map.of("state",operation.equals("enable")?"ENABLED":"DISABLED");
    }
    @PostMapping("/grants") public Object issue(@RequestBody String raw) {
        var input=repo.decode(raw,GrantInput.class);return grants.issue(input.ownerId(),input.workerId(),input.scope(),input.expiresAt(),human());
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
        var input=repo.decode(raw,ManageInput.class);coordination.adopt(input.ownerId(),input.workerId(),input.planId(),input.version(),input.specDigest(),input.expiresAt(),human());return Map.of("state","ADOPTED");
    }
    @GetMapping("/grants") public Object list(HttpServletRequest request) {
        var actor=principal(request);actions.enabled(actor);
        return repo.grants().stream().filter(g->g.owner().equals(actor.owner()) && g.workerId().equals(actor.worker())).toList();
    }
    @GetMapping("/state") public Object state(HttpServletRequest request,@RequestParam String grantId) { return actions.state(principal(request),grantId); }
    @GetMapping("/target-state") public Object targetState(HttpServletRequest request,@RequestParam String target) { return actions.targetState(principal(request),target); }
    @GetMapping("/management-state") public Object managementState(HttpServletRequest request,@RequestParam String planId) { return coordination.state(principal(request),planId); }
    @GetMapping("/receipts/{id}") public Object receipt(HttpServletRequest request,@PathVariable String id) { return actions.receipt(principal(request),id); }
    @PostMapping("/actions") public Object action(HttpServletRequest request,@RequestBody String raw) {
        var receipt=actions.apply(principal(request),repo.decode(raw,ActionAdmissionService.Envelope.class));return response(receipt);
    }
    @PostMapping("/restrictions") public Object restrict(HttpServletRequest request,@RequestBody String raw) { return controls.restrict(principal(request),repo.decode(raw,AuthorityControlService.Restriction.class)); }
    @PostMapping("/coordination") public Object coordinate(HttpServletRequest request,@RequestBody String raw) { return response(coordination.advance(principal(request),repo.decode(raw,CoordinationService.Advance.class))); }
    private Object response(ActionAdmissionService.Receipt receipt) {
        int status=receipt.state().equals("REJECTED")?422:Set.of("ADMITTED","IN_PROGRESS","UNKNOWN").contains(receipt.state())?202:200;
        return ResponseEntity.status(status).body(receipt);
    }
    @ExceptionHandler(SecurityException.class) public ResponseEntity<?> denied() { return ResponseEntity.status(403).body(Map.of("code","SCOPE_DENIED","message","Current owner, identity and action authority required")); }
    @ExceptionHandler(AuthorityGrantService.InvalidIdentityInput.class)
    public ResponseEntity<?> invalidIdentityInput(AuthorityGrantService.InvalidIdentityInput error) { return invalidIdentityInput(error.field(),error.getMessage()); }
    private static ResponseEntity<?> invalidIdentityInput(String field,String message) {
        return ResponseEntity.status(422).body(Map.of("code","INVALID_ACTION","field",field,"message",message));
    }
    @ExceptionHandler({IllegalArgumentException.class,NoSuchElementException.class}) public ResponseEntity<?> invalid() { return ResponseEntity.status(422).body(Map.of("code","INVALID_ACTION","message","Invalid or unavailable action input")); }
    @ExceptionHandler(IllegalStateException.class) public ResponseEntity<?> conflict() { return ResponseEntity.status(409).body(Map.of("code","STATE_CHANGED","message","Reload authoritative state or query the original request receipt")); }
}
