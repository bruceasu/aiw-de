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
    @org.springframework.beans.factory.annotation.Autowired private ExecutionWorkbench workbench;
    @org.springframework.beans.factory.annotation.Autowired private CapabilityPacks capabilityPacks;
    @org.springframework.beans.factory.annotation.Autowired private me.asu.aidev.workflow.application.FactoryPlanLifecycle planLifecycle;
    @PostMapping("/api/r1/authority/execution-configurations/reconcile-plan")
    public Object reconcilePlan(@RequestBody me.asu.aidev.workflow.application.FactoryPlanLifecycle.Reconcile input) {
        return planLifecycle.reconcile(input);
    }
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
        if("capability-pack".equals(kind)) return capabilityPacks.save(read(raw,CapabilityPacks.Save.class));
        if("workbench".equals(kind)) return workbench.command(read(raw,WorkbenchContract.HumanCommand.class));
        return role(kind)?configs.saveRole(read(raw,ComposedRoles.Definition.class)):configs.save(read(raw,Candidate.class));
    }
    @PostMapping("/api/r1/authority/execution-configurations/propose") public Object propose(@RequestBody String raw,@RequestParam(defaultValue="method") String kind) {
        return role(kind)?configs.proposeRole(read(raw,ComposedRoles.Definition.class)):configs.propose(read(raw,Assembly.class));
    }
    @PostMapping("/api/r1/authority/execution-configurations/prepare") public Object prepare(@RequestBody String raw) { return factory.prepare(read(raw,Intent.class)); }
    @PostMapping("/api/r1/authority/execution-configurations/state") public Object state(@RequestBody String raw,@RequestParam(defaultValue="method") String kind) {
        if("capability-packs".equals(kind)) {read(raw,Empty.class);return capabilityPacks.list();}
        if("capability-pack".equals(kind)) return capabilityPacks.lookup(read(raw,CapabilityPacks.Lookup.class));
        if("workbench".equals(kind)) return workbench.snapshot(configs.observations(),read(raw,WorkbenchContract.Query.class));
        if("workbench-receipt".equals(kind)) return workbench.receipt(read(raw,WorkbenchContract.ReceiptQuery.class));
        if("branch-history".equals(kind)) return workbench.branchHistory(read(raw,WorkbenchContract.BranchQuery.class));
        if("observations".equals(kind)) { read(raw,Empty.class);return configs.observations(); }
        if(role(kind)) return configs.role(read(raw,PreparedConfigRef.class));
        read(raw,Empty.class);String owner=configs.currentOwner();
        return Map.of("protocol",PROTOCOL,"categories",configs.categories(),"methods",configs.catalogue().methods(),
                "assets",configs.catalogue().assets().stream().map(Asset::reference).toList(),"preparations",factory.preparations(owner),"uiOptions",uiOptions.snapshot(),"roles",configs.roleChoices(owner),"capabilityPacks",capabilityPacks.list(),
                "configurations",repo.jdbc().queryForList("SELECT id,revision,digest,repository_ref FROM execution_configuration WHERE owner_id=? ORDER BY id,revision",owner),
                "workers",repo.jdbc().queryForList("SELECT c.worker_id,w.runtime_type,w.status,c.capabilities_json FROM execution_worker_configuration c JOIN worker w ON w.id=c.worker_id WHERE NOT EXISTS (SELECT 1 FROM factory_worker_registration f WHERE f.worker_id=w.id AND f.retired_at_epoch_ms IS NOT NULL) ORDER BY c.worker_id"));
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
