package me.asu.aidev.modelauth;

import jakarta.servlet.http.HttpSession;
import java.util.Map;
import me.asu.aidev.authority.AuthorityRepository;
import me.asu.aidev.worker.application.WorkerService;
import me.asu.aidev.worker.domain.WorkerId;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import static me.asu.aidev.modelauth.ModelAuthContract.*;

@RestController
public class ModelAuthController {
    @org.springframework.beans.factory.annotation.Autowired private FactoryModelCatalog catalog;
    private final ModelConnections connections;private final OpenAiLogin login;private final AuthorityRepository repo;private final WorkerService workers;
    public ModelAuthController(ModelConnections connections,OpenAiLogin login,AuthorityRepository repo,WorkerService workers) {
        this.connections=connections;this.login=login;this.repo=repo;this.workers=workers;
    }
    private <T>T read(String raw,Class<T> type) {if(raw.length()>131072)throw new IllegalArgumentException();return repo.decode(raw,type);}
    private static HttpHeaders headers() {var headers=new HttpHeaders();headers.setCacheControl("no-store");headers.setPragma("no-cache");return headers;}
    @PostMapping("/api/r1/model-connections/state") public ResponseEntity<?> state() {return ResponseEntity.ok().headers(headers()).body(connections.state());}
    @PostMapping("/api/r1/model-connections/catalog-context") public ResponseEntity<?> context() {return ResponseEntity.ok().headers(headers()).body(catalog.context());}
    @PostMapping("/api/r1/model-connections/models") public ResponseEntity<?> models(@RequestBody String raw) {return ResponseEntity.ok().headers(headers()).body(catalog.models(read(raw,FactoryModelCatalog.Query.class)));}
    @PostMapping("/api/r1/model-connections/import") public Object importCredential(@RequestBody String raw) {return connections.importCredential(read(raw,Import.class));}
    @PostMapping("/api/r1/model-connections/login") public ResponseEntity<?> login(@RequestBody String raw,HttpSession session) {return ResponseEntity.ok().headers(headers()).body(login.start(read(raw,Login.class),session));}
    @PostMapping("/api/r1/model-connections/logins/{id}/status") public Object status(@PathVariable String id,HttpSession session) {return login.status(id,session,false);}
    @PostMapping("/api/r1/model-connections/logins/{id}/cancel") public Object cancel(@PathVariable String id,HttpSession session) {return login.status(id,session,true);}
    @PostMapping("/api/r1/model-connections/refresh") public Object refresh(@RequestBody String raw) {return connections.refresh(read(raw,Selection.class));}
    @PostMapping("/api/r1/model-connections/disconnect") public Object disconnect(@RequestBody String raw) {return connections.disconnect(read(raw,Selection.class));}
    @PostMapping("/api/r1/worker-protocol/v1/tasks/{task}/model-credential") public ResponseEntity<?> access(@PathVariable String task,@RequestBody String raw) {
        var input=read(raw,Access.class);return ResponseEntity.ok().headers(headers()).body(connections.access(task,input,workers.get(new WorkerId(input.lease().workerId()))));
    }
    @ExceptionHandler(SecurityException.class) public ResponseEntity<?> denied() {return ResponseEntity.status(403).headers(headers()).body(Map.of("code","MODEL_AUTH_SCOPE_DENIED"));}
    @ExceptionHandler({IllegalArgumentException.class,NullPointerException.class}) public ResponseEntity<?> invalid() {return ResponseEntity.unprocessableContent().headers(headers()).body(Map.of("code","MODEL_AUTH_INVALID_INPUT"));}
    @ExceptionHandler(IllegalStateException.class) public ResponseEntity<?> conflict(IllegalStateException error) {
        String code=error.getMessage();if(code==null||!code.matches("[A-Z][A-Z0-9_]{2,90}"))code="MODEL_AUTH_STATE_CHANGED";
        return ResponseEntity.status(409).headers(headers()).body(Map.of("code",code));
    }
}
