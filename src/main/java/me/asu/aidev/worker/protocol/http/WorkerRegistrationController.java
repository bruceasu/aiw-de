package me.asu.aidev.worker.protocol.http;

import jakarta.servlet.http.HttpSession;
import java.util.Map;
import me.asu.aidev.worker.application.WorkerRegistrationService;
import me.asu.aidev.webconsole.security.WebConsoleSecurityService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class WorkerRegistrationController {
    private final WorkerRegistrationService registrations;
    private final WebConsoleSecurityService security;
    public WorkerRegistrationController(WorkerRegistrationService registrations,WebConsoleSecurityService security) {
        this.registrations=registrations;this.security=security;
    }
    public record Request(long expectedHeartbeatMs,String reason) {}
    @PostMapping("/api/r1/worker-registrations/{id}/unregister")
    public WorkerRegistrationService.Result unregister(@PathVariable String id,@RequestBody Request request,HttpSession session) {
        security.requireAdmin(session);
        return registrations.unregister(id,request.expectedHeartbeatMs(),request.reason(),security.actor(session));
    }
    @ExceptionHandler(SecurityException.class) public ResponseEntity<?> denied(SecurityException e) {return ResponseEntity.status(403).body(Map.of("error",e.getMessage()));}
    @ExceptionHandler(IllegalStateException.class) public ResponseEntity<?> conflict(IllegalStateException e) {return ResponseEntity.status(409).body(Map.of("error",e.getMessage()));}
    @ExceptionHandler(IllegalArgumentException.class) public ResponseEntity<?> invalid(IllegalArgumentException e) {return ResponseEntity.badRequest().body(Map.of("error",e.getMessage()));}
}
