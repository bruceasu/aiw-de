package me.asu.aidev.execution.guard;

import java.util.*;
import me.asu.aidev.authority.AuthorityRepository;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import static me.asu.aidev.execution.guard.GuardContract.*;

/** Machine routes use the existing token filter and exact Lease. Human routes use owner/session/CSRF. */
@RestController
public class ExecutionGuardController {
    private final AuthorityRepository repo;private final ExecutionGuard guard;private final ExecutionLedger ledger;private final ExecutionYieldService yields;
    @org.springframework.beans.factory.annotation.Autowired private me.asu.aidev.execution.config.ExecutionWorkbench workbench;
    public ExecutionGuardController(AuthorityRepository repo,ExecutionGuard guard,ExecutionLedger ledger,ExecutionYieldService yields) {
        this.repo=repo;this.guard=guard;this.ledger=ledger;this.yields=yields;
    }
    private <T> T read(String raw,Class<T> type) { if(raw.length()>131072) throw new IllegalArgumentException("Guard envelope exceeds limit");return repo.decode(raw,type); }
    public record Control(long revision) {}
    public record DeliveryInput(Lease lease,Delivery delivery) {}
    @PostMapping("/api/r1/authority/execution-policies") public Object policy(@RequestBody String raw) { return guard.confirm(read(raw,Policy.class)); }
    @PostMapping("/api/r1/authority/execution-policies/{plan}/{action:pause|resume|revoke}") public Object control(@PathVariable String plan,@PathVariable String action,@RequestBody String raw) { return guard.control(plan,read(raw,Control.class).revision(),action); }
    @PostMapping("/api/r1/authority/execution-contexts/{task}/bind") public Object bind(@PathVariable String task,@RequestBody String raw) { return guard.bind(task,read(raw,Bind.class)); }
    @PostMapping("/api/r1/authority/execution-budgets/increment") public Object increment(@RequestBody String raw) { return ledger.increment(read(raw,Increment.class),"human"); }
    @PostMapping("/api/r1/authority/execution-effects/reconcile") public Object reconcile(@RequestBody String raw) { return guard.reconcile(read(raw,Reconcile.class)); }
    @PostMapping("/api/r1/authority/execution-meters/reconcile") public Object meter(@RequestBody String raw) { return ledger.reconcileMeter(read(raw,MeterReconcile.class)); }
    @PostMapping("/api/r1/authority/execution-tasks/{task}/stopped") public Object stopped(@PathVariable String task,@RequestBody String raw) { return yields.humanStopped(task,read(raw,StopEvidence.class)); }
    public Object context(String task,String raw) { return context(task,raw,"fixed"); }
    @PostMapping("/api/r1/worker-protocol/v1/tasks/{task}/execution-context/read") public Object context(@PathVariable String task,@RequestBody String raw,@RequestParam(defaultValue="fixed") String kind) {
        if("workbench".equals(kind))return workbench.machine(task,read(raw,me.asu.aidev.execution.config.WorkbenchContract.MachineCommand.class));
        if(!"fixed".equals(kind))throw new IllegalArgumentException("Unknown context view");
        return guard.read(task,read(raw,Lease.class));
    }
    @PostMapping("/api/r1/worker-protocol/v1/tasks/{task}/operations/begin") public Object begin(@PathVariable String task,@RequestBody String raw) { return guard.begin(task,read(raw,Begin.class)); }
    @PostMapping("/api/r1/worker-protocol/v1/tasks/{task}/operations/finish") public Object finish(@PathVariable String task,@RequestBody String raw) { return guard.finish(task,read(raw,Finish.class)); }
    @PostMapping("/api/r1/worker-protocol/v1/tasks/{task}/operations/{operation}/receipt") public Object receipt(@PathVariable String task,@PathVariable String operation,@RequestBody String raw) { return guard.query(task,read(raw,Lease.class),operation); }
    @PostMapping("/api/r1/worker-protocol/v1/tasks/{task}/yield/{action:prepare|ack}") public Object yieldTask(@PathVariable String task,@PathVariable String action,@RequestBody String raw) { var input=read(raw,Yield.class);return action.equals("prepare")?yields.prepare(task,input):yields.acknowledge(task,input); }
    @PostMapping("/api/r1/worker-protocol/v1/tasks/{task}/delivery") public Object delivery(@PathVariable String task,@RequestBody String raw) { var input=read(raw,DeliveryInput.class);return guard.delivery(task,input.lease(),input.delivery()); }
    @ExceptionHandler(SecurityException.class) public ResponseEntity<?> denied() { return ResponseEntity.status(403).body(Map.of("code","GUARD_SCOPE_DENIED")); }
    @ExceptionHandler({IllegalArgumentException.class,NoSuchElementException.class}) public ResponseEntity<?> invalid() { return ResponseEntity.unprocessableContent().body(Map.of("code","GUARD_INVALID_INPUT")); }
    @ExceptionHandler(IllegalStateException.class) public ResponseEntity<?> conflict() { return ResponseEntity.status(409).body(Map.of("code","GUARD_STATE_CHANGED","replay",false)); }
}
