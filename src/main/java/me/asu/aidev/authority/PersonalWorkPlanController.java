package me.asu.aidev.authority;

import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.NoSuchElementException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/r1/authority/personal-plans")
public class PersonalWorkPlanController {
    private final AuthorityRepository repo;
    private final PersonalWorkPlans plans;
    public PersonalWorkPlanController(AuthorityRepository repo, PersonalWorkPlans plans) { this.repo = repo; this.plans = plans; }
    private AuthorityBoundary.Principal principal(HttpServletRequest request) {
        return (AuthorityBoundary.Principal) request.getAttribute("authorityPrincipal");
    }
    @PostMapping
    public Object create(HttpServletRequest request, @RequestBody String raw) {
        if (raw.getBytes(StandardCharsets.UTF_8).length > 32768) throw new IllegalArgumentException("Personal work envelope limit");
        var input = repo.decode(raw, PersonalWorkPlans.Input.class);
        if (input == null) throw new IllegalArgumentException("Personal work required");
        return plans.create(principal(request), request.getHeader("X-AIW-Personal-Boot"), input);
    }
    @GetMapping("/{id}")
    public Object read(HttpServletRequest request, @PathVariable String id) {
        return plans.read(principal(request), request.getHeader("X-AIW-Personal-Boot"), id);
    }
    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<?> denied() { return ResponseEntity.status(403).body(Map.of("code", "PERSONAL_PLAN_DENIED")); }
    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<?> missing() { return ResponseEntity.status(404).body(Map.of("code", "PERSONAL_PLAN_NOT_FOUND")); }
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<?> invalid() { return ResponseEntity.unprocessableContent().body(Map.of("code", "PERSONAL_PLAN_INVALID_INPUT")); }
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<?> changed() { return ResponseEntity.status(409).body(Map.of("code", "PERSONAL_PLAN_CHANGED")); }
}
