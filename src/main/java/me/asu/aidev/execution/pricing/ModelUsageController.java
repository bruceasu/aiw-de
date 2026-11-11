package me.asu.aidev.execution.pricing;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.NoSuchElementException;
import me.asu.aidev.authority.AuthorityRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Worker token and the exact original task lease protect these endpoints. */
@RestController
public class ModelUsageController {
    private final AuthorityRepository repo;
    private final ModelUsageLedger ledger;
    public ModelUsageController(AuthorityRepository repo, ModelUsageLedger ledger) { this.repo=repo; this.ledger=ledger; }
    private <T> T read(String raw, Class<T> type) {
        if (raw.getBytes(StandardCharsets.UTF_8).length > 16384) throw new IllegalArgumentException("MODEL_USAGE_ENVELOPE_LIMIT");
        return repo.decode(raw, type);
    }
    @PostMapping("/api/r1/worker-protocol/v1/tasks/{task}/model-usage/start")
    public Object start(@PathVariable String task, @RequestBody String raw) { return ledger.start(task, read(raw, ModelUsageLedger.Start.class)); }
    @PostMapping("/api/r1/worker-protocol/v1/tasks/{task}/model-usage/finish")
    public Object finish(@PathVariable String task, @RequestBody String raw) { return ledger.finish(task, read(raw, ModelUsageLedger.Terminal.class)); }
    @ExceptionHandler(SecurityException.class) public ResponseEntity<?> denied() { return ResponseEntity.status(403).body(Map.of("code", "MODEL_USAGE_DENIED")); }
    @ExceptionHandler({IllegalArgumentException.class, NoSuchElementException.class}) public ResponseEntity<?> invalid() { return ResponseEntity.unprocessableContent().body(Map.of("code", "MODEL_USAGE_INVALID")); }
    @ExceptionHandler(IllegalStateException.class) public ResponseEntity<?> conflict() { return ResponseEntity.status(409).body(Map.of("code", "MODEL_USAGE_CONFLICT")); }
}
