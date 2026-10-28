package me.asu.aidev.execution.config;

import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import me.asu.aidev.authority.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/r1/authority/professional-tasks")
public class PersonalTaskPreparationController {
    private final AuthorityRepository repo;
    private final PersonalTaskPreparations preparations;
    public PersonalTaskPreparationController(AuthorityRepository repo,PersonalTaskPreparations preparations) {
        this.repo=repo;this.preparations=preparations;
    }
    private AuthorityBoundary.Principal principal(HttpServletRequest request) {
        return (AuthorityBoundary.Principal)request.getAttribute("authorityPrincipal");
    }
    @GetMapping("/options") public Object options(HttpServletRequest request) { return preparations.options(principal(request)); }
    @GetMapping("/diagnostics") public Object diagnostics(HttpServletRequest request,@RequestParam String planId) {return preparations.diagnostics(principal(request),planId);}
    @PostMapping public Object prepare(HttpServletRequest request,@RequestBody String raw) {
        if(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>32768) throw new IllegalArgumentException("Bounded selected task required");
        var input=repo.decode(raw,PersonalTaskPreparations.Input.class);
        if(input==null) throw new IllegalArgumentException("Selected task required");
        return preparations.prepare(principal(request),input);
    }
    @GetMapping("/{id}") public Object read(HttpServletRequest request,@PathVariable String id) { return preparations.read(principal(request),id); }
    @PostMapping("/{id}/continue") public Object continuePreparation(HttpServletRequest request,@PathVariable String id,@RequestBody String raw) {
        if(!repo.decode(raw,Map.class).isEmpty())throw new IllegalArgumentException("Empty continuation required");
        return preparations.continuePreparation(principal(request),id);
    }
    @ExceptionHandler(SecurityException.class) public ResponseEntity<?> denied() { return ResponseEntity.status(403).body(Map.of("code","PROFESSIONAL_SCOPE_REQUIRED")); }
    @ExceptionHandler(NoSuchElementException.class) public ResponseEntity<?> missing() { return ResponseEntity.status(404).body(Map.of("code","PROFESSIONAL_REQUEST_NOT_FOUND")); }
    @ExceptionHandler(IllegalArgumentException.class) public ResponseEntity<?> invalid() { return ResponseEntity.unprocessableContent().body(Map.of("code","PROFESSIONAL_INPUT_INVALID")); }
    @ExceptionHandler(PersonalTaskPreparations.PlanNotDraft.class)
    public ResponseEntity<?> planNotDraft(PersonalTaskPreparations.PlanNotDraft failure) {
        return ResponseEntity.status(409).body(Map.of("code","PROFESSIONAL_PLAN_NOT_DRAFT",
                "planId",failure.planId(),"planState",failure.planState(),"message",failure.getMessage()));
    }
    @ExceptionHandler(IllegalStateException.class) public ResponseEntity<?> changed() { return ResponseEntity.status(409).body(Map.of("code","PROFESSIONAL_SOURCE_CHANGED")); }
}
