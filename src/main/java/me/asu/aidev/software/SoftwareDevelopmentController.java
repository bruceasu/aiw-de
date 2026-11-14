package me.asu.aidev.software;

import java.util.*;
import jakarta.servlet.http.HttpServletRequest;
import me.asu.aidev.authority.*;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

@Controller
public class SoftwareDevelopmentController {
    public record Read(String ownerId,String planId) {}
    private final AuthorityRepository repo;private final GoalProposals goals;private final SoftwareDelivery delivery;
    public SoftwareDevelopmentController(AuthorityRepository repo,GoalProposals goals,SoftwareDelivery delivery){this.repo=repo;this.goals=goals;this.delivery=delivery;}
    private <T> T read(String raw,Class<T> type){if(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>32768)throw new IllegalArgumentException("Selected envelope limit");return repo.decode(raw,type);}
    @PostMapping("/api/r1/authority/goal-proposals") @ResponseBody public Object propose(HttpServletRequest request,@RequestBody String raw){return goals.submit((AuthorityBoundary.Principal)request.getAttribute("authorityPrincipal"),request.getHeader("X-AIW-Agent-Boot"),read(raw,GoalProposals.Proposal.class));}
    @GetMapping("/api/r1/authority/goal-proposals/{id}") @ResponseBody public Object receipt(HttpServletRequest request,@PathVariable String id){return goals.receipt((AuthorityBoundary.Principal)request.getAttribute("authorityPrincipal"),request.getHeader("X-AIW-Agent-Boot"),id);}
    @PostMapping("/api/r1/authority/software-development/read") @ResponseBody public Object progress(@RequestBody String raw){var r=read(raw,Read.class);return delivery.read(r.ownerId(),r.planId());}
    @ExceptionHandler(SecurityException.class) @ResponseBody public ResponseEntity<?> denied(){return ResponseEntity.status(403).body(Map.of("code","SOFTWARE_SCOPE_DENIED"));}
    @ExceptionHandler(org.springframework.dao.EmptyResultDataAccessException.class) @ResponseBody public ResponseEntity<?> missing(){return ResponseEntity.status(404).body(Map.of("code","SOFTWARE_RECORD_NOT_FOUND","replayAllowed",false));}
    @ExceptionHandler({IllegalArgumentException.class,NoSuchElementException.class}) @ResponseBody public ResponseEntity<?> invalid(){return ResponseEntity.unprocessableContent().body(Map.of("code","SOFTWARE_INVALID_INPUT"));}
    @ExceptionHandler(IllegalStateException.class) @ResponseBody public ResponseEntity<?> changed(){return ResponseEntity.status(409).body(Map.of("code","SOFTWARE_STATE_CHANGED","replayAllowed",false));}
}
