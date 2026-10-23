package me.asu.aidev.workflow.graph;

import java.util.*;
import me.asu.aidev.authority.*;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import static me.asu.aidev.workflow.graph.GraphContract.*;

@Controller
public class GraphController {
    private final AuthorityRepository repo;private final AuthorityBoundary boundary;private final GraphPlans plans;private final GraphRecommendations recommendations;
    public GraphController(AuthorityRepository repo,AuthorityBoundary boundary,GraphPlans plans,GraphRecommendations recommendations){this.repo=repo;this.boundary=boundary;this.plans=plans;this.recommendations=recommendations;}
    public record Read(String ownerId,String planId) {}
    private <T> T read(String raw,Class<T> type){if(raw.length()>131072)throw new IllegalArgumentException("Graph envelope limit");return repo.decode(raw,type);}
    @GetMapping("/ui/workflow-graphs") public String page(Model model){var owners=repo.jdbc().queryForList("SELECT id FROM authority_owner WHERE actor=?",String.class,boundary.human());model.addAttribute("graphOwners",owners);return "ui/workflow-graphs";}
    @PostMapping("/api/r1/authority/workflow-graphs/read") @ResponseBody public Object state(@RequestBody String raw){var r=read(raw,Read.class);return r.planId()==null?plans.list(r.ownerId()):plans.state(r.ownerId(),r.planId());}
    @PostMapping("/api/r1/authority/workflow-graphs/change") @ResponseBody public Object change(@RequestBody String raw){return plans.change(read(raw,Change.class));}
    @PostMapping("/api/r1/authority/workflow-graphs/decide") @ResponseBody public Object decide(@RequestBody String raw){return plans.decide(read(raw,Decision.class));}
    @PostMapping("/api/r1/authority/workflow-graphs/recommend") @ResponseBody public Object recommend(@RequestBody String raw){return recommendations.request(read(raw,Recommendation.class));}
    @ExceptionHandler(SecurityException.class) @ResponseBody public ResponseEntity<?> denied(){return ResponseEntity.status(403).body(Map.of("code","GRAPH_SCOPE_DENIED"));}
    @ExceptionHandler({IllegalArgumentException.class,NoSuchElementException.class}) @ResponseBody public ResponseEntity<?> invalid(RuntimeException error){return ResponseEntity.unprocessableContent().body(Map.of("code","GRAPH_INVALID_INPUT","detail",Objects.toString(error.getMessage(),"Invalid graph")));}
    @ExceptionHandler(IllegalStateException.class) @ResponseBody public ResponseEntity<?> conflict(IllegalStateException error){return ResponseEntity.status(409).body(Map.of("code","GRAPH_STATE_CHANGED","detail",Objects.toString(error.getMessage(),"Refresh current state")));}
}
