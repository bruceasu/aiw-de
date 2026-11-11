package me.asu.aidev.execution.pricing;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import me.asu.aidev.authority.AuthorityRepository;
import me.asu.aidev.execution.guard.GuardContract.Lease;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ModelPriceRepairController {
    public record Read(Lease lease) {}
    private final AuthorityRepository repo;
    private final ModelPriceRepairMaterials materials;
    private final ModelPriceRepairResults results;
    public ModelPriceRepairController(AuthorityRepository repo, ModelPriceRepairMaterials materials, ModelPriceRepairResults results) {
        this.repo = repo; this.materials = materials; this.results = results;
    }
    @PostMapping("/api/r1/worker-protocol/v1/tasks/{task}/price-repair/materials")
    public Object read(@PathVariable String task, @RequestBody String raw) {
        if (raw.getBytes(StandardCharsets.UTF_8).length > 16384) throw new IllegalArgumentException("PRICE_REPAIR_ENVELOPE_LIMIT");
        Read request = repo.decode(raw, Read.class);
        if (request == null) throw new IllegalArgumentException("PRICE_REPAIR_ENVELOPE_INVALID");
        return materials.read(task, request.lease());
    }
    @PostMapping("/api/r1/worker-protocol/v1/tasks/{task}/price-repair/result")
    public Object submit(@PathVariable String task, @RequestBody String raw) {
        if (raw.getBytes(StandardCharsets.UTF_8).length > 2_800_000) throw new IllegalArgumentException("PRICE_REPAIR_ENVELOPE_LIMIT");
        return results.submit(task, repo.decode(raw, ModelPriceRepairResults.Submission.class));
    }
    @ExceptionHandler(SecurityException.class) public ResponseEntity<?> denied() { return ResponseEntity.status(403).body(Map.of("code", "PRICE_REPAIR_DENIED")); }
    @ExceptionHandler(IllegalArgumentException.class) public ResponseEntity<?> invalid() { return ResponseEntity.unprocessableContent().body(Map.of("code", "PRICE_REPAIR_INVALID")); }
    @ExceptionHandler(IllegalStateException.class) public ResponseEntity<?> conflict() { return ResponseEntity.status(409).body(Map.of("code", "PRICE_REPAIR_CONFLICT")); }
}
