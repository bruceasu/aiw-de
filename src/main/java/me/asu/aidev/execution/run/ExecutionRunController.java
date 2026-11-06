package me.asu.aidev.execution.run;

import java.util.*;
import me.asu.aidev.authority.AuthorityRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import static me.asu.aidev.execution.run.RunContract.*;

@RestController
public class ExecutionRunController {
    private final AuthorityRepository repo;private final ExecutionRuns runs;
    public ExecutionRunController(AuthorityRepository repo,ExecutionRuns runs) { this.repo=repo;this.runs=runs; }
    private <T>T read(String raw,Class<T> type) {
        if(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>65536)throw new IllegalArgumentException("Run envelope exceeds limit");
        return repo.decode(raw,type);
    }
    @PostMapping("/api/r1/worker-protocol/v1/tasks/{task}/execution-run/open") public Object open(@PathVariable String task,@RequestBody String raw) { return runs.open(task,read(raw,Open.class)); }
    @PostMapping("/api/r1/worker-protocol/v1/tasks/{task}/execution-run/events") public Object events(@PathVariable String task,@RequestBody String raw) { return runs.events(task,read(raw,Events.class)); }
    @PostMapping("/api/r1/worker-protocol/v1/tasks/{task}/execution-run/control") public Object poll(@PathVariable String task,@RequestBody String raw) { return runs.poll(task,read(raw,Poll.class)); }
    @PostMapping("/api/r1/worker-protocol/v1/tasks/{task}/execution-run/seal") public Object seal(@PathVariable String task,@RequestBody String raw) { return runs.seal(task,read(raw,Seal.class)); }
    @PostMapping("/api/r1/authority/execution-runs/read") public Object query(@RequestBody String raw) { return runs.read(read(raw,Query.class)); }
    @PostMapping("/api/r1/authority/execution-runs/command") public Object command(@RequestBody String raw) { return runs.command(read(raw,Command.class)); }
    @PostMapping("/api/r1/authority/execution-runs/receipt") public Object receipt(@RequestBody String raw) { return runs.receipt(read(raw,ReceiptQuery.class)); }
    @ExceptionHandler(SecurityException.class) public ResponseEntity<?> denied() { return ResponseEntity.status(403).body(Map.of("code","RUN_SCOPE_DENIED")); }
    @ExceptionHandler({IllegalArgumentException.class,NoSuchElementException.class}) public ResponseEntity<?> invalid() { return ResponseEntity.unprocessableContent().body(Map.of("code","RUN_INVALID_INPUT")); }
    @ExceptionHandler(IllegalStateException.class) public ResponseEntity<?> conflict() { return ResponseEntity.status(409).body(Map.of("code","RUN_STATE_CHANGED")); }
}
