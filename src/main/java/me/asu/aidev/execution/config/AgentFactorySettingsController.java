package me.asu.aidev.execution.config;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import me.asu.aidev.authority.AuthorityRepository;

@RestController
@RequestMapping("/api/r1/authority/agent-factory")
public class AgentFactorySettingsController {
    private final AgentFactorySettings settings;private final AuthorityRepository repo;private final ManagedWorkerProvisioner workers;
    @org.springframework.beans.factory.annotation.Autowired private ManagerProfileSelection manager;
    public AgentFactorySettingsController(AgentFactorySettings settings,AuthorityRepository repo,ManagedWorkerProvisioner workers) {this.settings=settings;this.repo=repo;this.workers=workers;}
    @PostMapping("/state") public Object state(@RequestBody String raw) {
        if(!repo.decode(raw,Map.class).isEmpty())throw new IllegalArgumentException();
        var result=new java.util.LinkedHashMap<>(settings.state());result.put("managedWorkers",workers.state());return result;
    }
    @PostMapping("/settings") public Object save(@RequestBody String raw) {
        if(raw.length()>16384)throw new IllegalArgumentException("工厂设置过长。");
        return settings.save(repo.decode(raw,AgentFactorySettings.Settings.class));
    }
    @PostMapping("/profiles") public Object saveProfile(@RequestBody String raw) {
        if(raw.length()>16384)throw new IllegalArgumentException("运行配置过长。");
        return settings.saveProfile(repo.decode(raw,AgentFactorySettings.ProfileChange.class));
    }
    @PostMapping("/profiles/rename") public Object renameProfile(@RequestBody String raw) {
        if(raw.length()>1024)throw new IllegalArgumentException("运行配置过长。");
        return settings.renameProfile(repo.decode(raw,AgentFactorySettings.ProfileRename.class));
    }
    @PostMapping("/profiles/delete") public Object deleteProfile(@RequestBody String raw) {
        if(raw.length()>1024)throw new IllegalArgumentException("运行配置过长。");
        return settings.deleteProfile(repo.decode(raw,AgentFactorySettings.ProfileDelete.class));
    }
    @PostMapping("/profile-recommendation") public Object recommend(@RequestBody String raw) {
        if(raw.length()>32768)throw new IllegalArgumentException("选型请求过长。");
        return manager.choose(repo.decode(raw,ManagerProfileSelection.Request.class));
    }
    @ExceptionHandler(SecurityException.class) public ResponseEntity<?> denied() {return ResponseEntity.status(403).body(Map.of("message","请使用有管理员权限的账号保存工厂模型和本机启动设置。"));}
    @ExceptionHandler({IllegalArgumentException.class,IllegalStateException.class}) public ResponseEntity<?> invalid(RuntimeException error) {return ResponseEntity.unprocessableContent().body(Map.of("message",error.getMessage()==null?"请检查工厂设置。":error.getMessage()));}
}
