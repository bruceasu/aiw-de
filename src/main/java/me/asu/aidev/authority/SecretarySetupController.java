package me.asu.aidev.authority;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/r1/authority/secretary-setup")
public class SecretarySetupController {
    private final SecretarySetupService service;private final AuthorityBoundary boundary;private final AuthorityRepository repo;private final SecretaryExecutionSetup execution;
    public SecretarySetupController(SecretarySetupService service,AuthorityBoundary boundary,AuthorityRepository repo,SecretaryExecutionSetup execution) {this.service=service;this.boundary=boundary;this.repo=repo;this.execution=execution;}
    @PostMapping("/state") public Object state(@RequestBody String raw) {if(!repo.decode(raw,Map.class).isEmpty())throw new IllegalArgumentException();return service.state(boundary.human());}
    @PostMapping("/authorize") public Object authorize(@RequestBody String raw) {if(raw.length()>32768)throw new IllegalArgumentException();return service.authorize(repo.decode(raw,SecretarySetupService.Input.class),boundary.human());}
    @PostMapping("/execution-preview") public Object preview(@RequestBody String raw) {if(raw.length()>4096)throw new IllegalArgumentException();return execution.preview(repo.decode(raw,SecretaryExecutionSetup.Preview.class),boundary.human());}
    @ExceptionHandler(SecurityException.class) public ResponseEntity<?> denied() {return ResponseEntity.status(403).body(Map.of("message","当前账号或角色不能授予这项代理权限，请使用有相应权限的账号确认。"));}
    @ExceptionHandler({IllegalArgumentException.class,IllegalStateException.class}) public ResponseEntity<?> invalid(RuntimeException error) {return ResponseEntity.unprocessableContent().body(Map.of("message",error.getMessage()==null?"配置已变化，请重新读取并核对。":error.getMessage()));}
}
