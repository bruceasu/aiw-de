package me.asu.aidev.software;

import java.util.*;
import jakarta.servlet.http.HttpServletRequest;
import me.asu.aidev.authority.*;
import me.asu.aidev.webconsole.security.WebConsoleSecurityService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
public class SoftwareDevelopmentController {
    public record Read(String ownerId,String planId) {}
    private final AuthorityRepository repo;private final AuthorityBoundary boundary;private final GoalProposals goals;private final SoftwareDelivery delivery;private final WebConsoleSecurityService security;
    public SoftwareDevelopmentController(AuthorityRepository repo,AuthorityBoundary boundary,GoalProposals goals,SoftwareDelivery delivery,WebConsoleSecurityService security){this.repo=repo;this.boundary=boundary;this.goals=goals;this.delivery=delivery;this.security=security;}
    private <T> T read(String raw,Class<T> type){if(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>32768)throw new IllegalArgumentException("Selected envelope limit");return repo.decode(raw,type);}
    @GetMapping("/ui/software-development") public String page(Model model){String actor=Objects.requireNonNull(boundary.human());boolean op=!security.currentDelegationAuthority(actor,"A19_ANSWER").isEmpty();if(!op&&security.currentDelegationAuthority(actor,"A18_BUDGET").isEmpty())throw new SecurityException("Current read role required");
        model.addAttribute("softwareOwners",repo.jdbc().queryForList("SELECT id FROM authority_owner WHERE actor=?",String.class,actor));model.addAttribute("softwareCanOperate",op);return "ui/software-development";}
    @PostMapping("/api/r1/authority/goal-proposals") @ResponseBody public Object propose(HttpServletRequest request,@RequestBody String raw){return goals.submit((AuthorityBoundary.Principal)request.getAttribute("authorityPrincipal"),request.getHeader("X-AIW-Personal-Boot"),read(raw,GoalProposals.Proposal.class));}
    @GetMapping("/api/r1/authority/goal-proposals/{id}") @ResponseBody public Object receipt(HttpServletRequest request,@PathVariable String id){return goals.receipt((AuthorityBoundary.Principal)request.getAttribute("authorityPrincipal"),request.getHeader("X-AIW-Personal-Boot"),id);}
    @PostMapping("/api/r1/authority/software-development/proposals") @ResponseBody public Object proposals(@RequestBody String raw){return goals.list(read(raw,Read.class).ownerId());}
    @PostMapping("/api/r1/authority/software-development/adopt") @ResponseBody public Object adopt(@RequestBody String raw){return goals.adopt(read(raw,GoalProposals.Adopt.class));}
    @PostMapping("/api/r1/authority/software-development/read") @ResponseBody public Object progress(@RequestBody String raw){var r=read(raw,Read.class);return delivery.read(r.ownerId(),r.planId());}
    @ExceptionHandler(SecurityException.class) @ResponseBody public ResponseEntity<?> denied(){return ResponseEntity.status(403).body(Map.of("code","SOFTWARE_SCOPE_DENIED"));}
    @ExceptionHandler(org.springframework.dao.EmptyResultDataAccessException.class) @ResponseBody public ResponseEntity<?> missing(){return ResponseEntity.status(404).body(Map.of("code","SOFTWARE_RECORD_NOT_FOUND","replayAllowed",false));}
    @ExceptionHandler({IllegalArgumentException.class,NoSuchElementException.class}) @ResponseBody public ResponseEntity<?> invalid(){return ResponseEntity.unprocessableContent().body(Map.of("code","SOFTWARE_INVALID_INPUT"));}
    @ExceptionHandler(IllegalStateException.class) @ResponseBody public ResponseEntity<?> changed(){return ResponseEntity.status(409).body(Map.of("code","SOFTWARE_STATE_CHANGED","replayAllowed",false));}
}
