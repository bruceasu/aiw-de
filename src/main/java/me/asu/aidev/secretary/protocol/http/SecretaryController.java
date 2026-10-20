package me.asu.aidev.secretary.protocol.http;

import jakarta.servlet.http.HttpSession;
import java.util.Map;
import me.asu.aidev.secretary.application.SecretaryService;
import me.asu.aidev.webconsole.security.WebConsoleSecurityService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/ui")
public class SecretaryController {
    private final SecretaryService service;
    private final WebConsoleSecurityService security;
    public SecretaryController(SecretaryService service,WebConsoleSecurityService security) { this.service=service;this.security=security; }
    private String actor(HttpSession session) { security.requireOperator(session);return security.actor(session); }
    public record Revision(long revision,boolean confirmed) {}
    @GetMapping("/secretary") public String page(HttpSession session) { actor(session);return "ui/secretary"; }
    @GetMapping("/api/secretary") @ResponseBody public Object list(HttpSession session) { return service.list(actor(session)); }
    @PostMapping("/api/secretary") @ResponseBody public Object create(HttpSession session) { return service.create(actor(session)); }
    @GetMapping("/api/secretary/{id}") @ResponseBody public Object detail(@PathVariable String id,HttpSession session) { return service.detail(id,actor(session)); }
    @PostMapping("/api/secretary/{id}/edit") @ResponseBody public Object edit(@PathVariable String id,@RequestBody SecretaryService.Edit input,HttpSession session) { return service.edit(id,actor(session),input); }
    @PostMapping("/api/secretary/{id}/confirm") @ResponseBody public Object confirm(@PathVariable String id,@RequestBody SecretaryService.Confirm input,HttpSession session) { return service.confirm(id,actor(session),input); }
    @PostMapping("/api/secretary/{id}/cancel") @ResponseBody public Object cancel(@PathVariable String id,@RequestBody Revision input,HttpSession session) { return service.cancel(id,actor(session),input.revision()); }
    @PostMapping("/api/secretary/{id}/messages") @ResponseBody public Object send(@PathVariable String id,@RequestBody SecretaryService.Send input,HttpSession session) { return service.send(id,actor(session),input); }
    @PostMapping("/api/secretary/{id}/replies") @ResponseBody public Object replies(@PathVariable String id,HttpSession session) { return service.replies(id,actor(session)); }
    @PostMapping("/api/secretary/{id}/retry-coordination") @ResponseBody public Object retry(@PathVariable String id,@RequestBody Revision input,HttpSession session) { return service.retryCoordination(id,actor(session),input.revision(),input.confirmed()); }
    @ExceptionHandler(SecurityException.class) public ResponseEntity<?> denied(SecurityException e) { return ResponseEntity.status(403).body(Map.of("error",e.getMessage())); }
    @ExceptionHandler(IllegalArgumentException.class) public ResponseEntity<?> invalid(IllegalArgumentException e) { return ResponseEntity.badRequest().body(Map.of("error",e.getMessage())); }
    @ExceptionHandler(IllegalStateException.class) public ResponseEntity<?> conflict(IllegalStateException e) { return ResponseEntity.status(409).body(Map.of("error",e.getMessage())); }
    @ExceptionHandler(java.time.DateTimeException.class) public ResponseEntity<?> date(java.time.DateTimeException e) { return ResponseEntity.badRequest().body(Map.of("error","Use a valid ISO date with offset and IANA time zone")); }
}
