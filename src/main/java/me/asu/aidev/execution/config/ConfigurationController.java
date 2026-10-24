package me.asu.aidev.execution.config;

import java.util.*;
import me.asu.aidev.authority.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import static me.asu.aidev.execution.config.ConfigurationContract.*;

@RestController
public class ConfigurationController {
    private final AuthorityRepository repo;private final ExecutionConfigurations configs;private final AgentFactory factory;
    private final ConfigurationUiOptions uiOptions;
    public ConfigurationController(AuthorityRepository repo,ExecutionConfigurations configs,AgentFactory factory,ConfigurationUiOptions uiOptions) { this.repo=repo;this.configs=configs;this.factory=factory;this.uiOptions=uiOptions; }
    private <T> T read(String raw,Class<T> type) { if(raw.length()>32768) throw new IllegalArgumentException("Bounded declarative request required");return repo.decode(raw,type); }
    public record Empty() {}
    @PostMapping("/api/r1/authority/execution-configurations/assets") public Object asset(@RequestBody String raw) { return configs.registerAsset(read(raw,Asset.class)); }
    private boolean role(String kind) {
        if(!Set.of("method","role").contains(kind)) throw new IllegalArgumentException("Unknown composition kind");
        return kind.equals("role");
    }
    public Object save(String raw) { return save(raw,"method"); }
    public Object propose(String raw) { return propose(raw,"method"); }
    public Object state(String raw) { return state(raw,"method"); }
    @PostMapping("/api/r1/authority/execution-configurations/candidates") public Object save(@RequestBody String raw,@RequestParam(defaultValue="method") String kind) {
        return role(kind)?configs.saveRole(read(raw,ComposedRoles.Definition.class)):configs.save(read(raw,Candidate.class));
    }
    @PostMapping("/api/r1/authority/execution-configurations/propose") public Object propose(@RequestBody String raw,@RequestParam(defaultValue="method") String kind) {
        return role(kind)?configs.proposeRole(read(raw,ComposedRoles.Definition.class)):configs.propose(read(raw,Assembly.class));
    }
    @PostMapping("/api/r1/authority/execution-configurations/prepare") public Object prepare(@RequestBody String raw) { return factory.prepare(read(raw,Intent.class)); }
    @PostMapping("/api/r1/authority/execution-configurations/state") public Object state(@RequestBody String raw,@RequestParam(defaultValue="method") String kind) {
        if("observations".equals(kind)) { read(raw,Empty.class);return configs.observations(); }
        if(role(kind)) return configs.role(read(raw,PreparedConfigRef.class));
        read(raw,Empty.class);String owner=configs.currentOwner();
        return Map.of("protocol",PROTOCOL,"categories",configs.categories(),"methods",configs.catalogue().methods(),
                "assets",configs.catalogue().assets().stream().map(Asset::reference).toList(),"preparations",factory.preparations(owner),"uiOptions",uiOptions.snapshot(),"roles",configs.roleChoices(owner),
                "configurations",repo.jdbc().queryForList("SELECT id,revision,digest,repository_ref FROM execution_configuration WHERE owner_id=? ORDER BY id,revision",owner),
                "workers",repo.jdbc().queryForList("SELECT c.worker_id,w.runtime_type,w.status,c.capabilities_json FROM execution_worker_configuration c JOIN worker w ON w.id=c.worker_id ORDER BY c.worker_id"));
    }
    @PostMapping("/api/r1/worker-protocol/v1/workers/{worker}/configuration") public void register(@PathVariable String worker,@RequestBody String raw) {
        var input=read(raw,WorkerRegistration.class);if(!worker.equals(input.workerId())) throw new SecurityException("Exact worker identity required");configs.registerWorker(input);
    }
    @ExceptionHandler(SecurityException.class) public ResponseEntity<?> denied() { return ResponseEntity.status(403).body(Map.of("code","AUTHORIZATION_REQUIRED")); }
    @ExceptionHandler(ComposedRoles.Invalid.class) public ResponseEntity<?> invalidRole(ComposedRoles.Invalid failure) { return ResponseEntity.unprocessableContent().body(Map.of("code","INVALID_ROLE_COMPOSITION","reason",failure.getMessage())); }
    @ExceptionHandler({IllegalArgumentException.class,NoSuchElementException.class}) public ResponseEntity<?> invalid() { return ResponseEntity.unprocessableContent().body(Map.of("code","INVALID_CONFIGURATION")); }
    @ExceptionHandler(IllegalStateException.class) public ResponseEntity<?> conflict() { return ResponseEntity.status(409).body(Map.of("code","SOURCE_CHANGED","replay",false)); }
    @ExceptionHandler(org.springframework.dao.DataAccessException.class) public ResponseEntity<?> failed() { return ResponseEntity.status(503).body(Map.of("code","PREPARATION_FAILED","replay",false)); }
}
