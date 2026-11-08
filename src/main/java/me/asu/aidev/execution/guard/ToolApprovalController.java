package me.asu.aidev.execution.guard;

import java.util.*;
import me.asu.aidev.authority.AuthorityRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Human decisions require session, CSRF, approver role and exact Owner; Worker can only request. */
@RestController
public class ToolApprovalController {
    private final AuthorityRepository repo;private final ToolApprovals approvals;
    public ToolApprovalController(AuthorityRepository repo,ToolApprovals approvals) {this.repo=repo;this.approvals=approvals;}
    public record Plan(String planId) {public Plan {GuardContract.text(planId,128);}}
    public record ApprovedResponse(boolean available,ToolApprovals.Approved operation) {}
    public record DiscardRequest(GuardContract.Lease lease,ToolApprovals.Discard discard) {
        public DiscardRequest {Objects.requireNonNull(lease);Objects.requireNonNull(discard);}
    }
    private <T> T read(String raw,Class<T> type) {
        if(raw.length()>512*1024)throw new IllegalArgumentException("Approval envelope exceeds limit");
        return repo.decode(raw,type);
    }
    @PostMapping("/api/r1/worker-protocol/v1/tasks/{task}/operations/approval")
    public Object open(@PathVariable String task,@RequestBody String raw) {return approvals.open(task,read(raw,ToolApprovals.Binding.class));}
    @PostMapping("/api/r1/worker-protocol/v1/tasks/{task}/operations/approved-begin")
    public Object consume(@PathVariable String task,@RequestBody String raw) {return approvals.consume(task,read(raw,ToolApprovals.Consume.class));}
    @PostMapping("/api/r1/worker-protocol/v1/tasks/{task}/operations/approved")
    public ApprovedResponse approved(@PathVariable String task,@RequestBody String raw) {
        var operation=approvals.approved(task,read(raw,GuardContract.Lease.class));
        return new ApprovedResponse(operation!=null,operation);
    }
    @PostMapping("/api/r1/worker-protocol/v1/tasks/{task}/operations/approved-discard")
    public void discard(@PathVariable String task,@RequestBody String raw) {
        var request=read(raw,DiscardRequest.class);approvals.discard(task,request.lease(),request.discard());
    }
    @PostMapping("/api/r1/authority/execution-tool-approvals/read")
    public Object list(@RequestBody String raw) {return approvals.list(read(raw,Plan.class).planId());}
    @PostMapping("/api/r1/authority/execution-tool-approvals/decide")
    public Object decide(@RequestBody String raw) {return approvals.decide(read(raw,ToolApprovals.Decision.class));}
    @ExceptionHandler(SecurityException.class) public ResponseEntity<?> denied() {return ResponseEntity.status(403).body(Map.of("code","TOOL_APPROVAL_SCOPE_DENIED"));}
    @ExceptionHandler(IllegalArgumentException.class) public ResponseEntity<?> invalid() {return ResponseEntity.unprocessableContent().body(Map.of("code","TOOL_APPROVAL_INVALID_INPUT"));}
    @ExceptionHandler(IllegalStateException.class) public ResponseEntity<?> changed() {return ResponseEntity.status(409).body(Map.of("code","TOOL_APPROVAL_STATE_CHANGED","execute",false));}
}
