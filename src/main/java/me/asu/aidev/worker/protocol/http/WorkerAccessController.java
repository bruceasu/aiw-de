package me.asu.aidev.worker.protocol.http;

import jakarta.servlet.http.HttpSession;
import me.asu.aidev.authority.WorkerAccess;
import me.asu.aidev.webconsole.security.WebConsoleSecurityService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/r1/worker-access")
public class WorkerAccessController {
    private final WorkerAccess access;private final WebConsoleSecurityService security;
    public WorkerAccessController(WorkerAccess access,WebConsoleSecurityService security){this.access=access;this.security=security;}
    @GetMapping public Object list(HttpSession session){security.requireAdmin(session);return access.list();}
    @PostMapping public Object create(HttpSession session,@RequestBody WorkerAccess.Create input){security.requireAdmin(session);return access.create(security.actor(session),input);}
    @PostMapping("/{id}") public Object state(HttpSession session,@PathVariable String id,@RequestBody WorkerAccess.Change input){security.requireAdmin(session);return access.change(id,input);}
    @ExceptionHandler(IllegalArgumentException.class) public ResponseEntity<?> invalid(IllegalArgumentException e){return ResponseEntity.unprocessableContent().body(java.util.Map.of("error",e.getMessage()));}
    @ExceptionHandler(IllegalStateException.class) public ResponseEntity<?> changed(IllegalStateException e){return ResponseEntity.status(409).body(java.util.Map.of("error",e.getMessage()));}
    @ExceptionHandler(org.springframework.dao.EmptyResultDataAccessException.class) public ResponseEntity<?> missing(){return ResponseEntity.status(404).body(java.util.Map.of("error","Worker credential not found"));}
}
