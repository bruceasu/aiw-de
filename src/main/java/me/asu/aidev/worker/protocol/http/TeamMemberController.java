package me.asu.aidev.worker.protocol.http;

import jakarta.servlet.http.HttpSession;
import java.util.Map;
import java.util.NoSuchElementException;
import me.asu.aidev.authority.AuthorityRepository;
import me.asu.aidev.webconsole.security.WebConsoleSecurityService;
import me.asu.aidev.worker.application.TeamMembers;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/r1/team-members")
public class TeamMemberController {
    private final TeamMembers members;private final WebConsoleSecurityService security;private final AuthorityRepository db;
    public TeamMemberController(TeamMembers members,WebConsoleSecurityService security,AuthorityRepository db){this.members=members;this.security=security;this.db=db;}
    private String actor(HttpSession session){security.requireOperator(session);return security.actor(session);}
    public record State(long revision,String action) {}
    @GetMapping public Object list(HttpSession session,@RequestParam(defaultValue="false") boolean archived){return members.list(actor(session),archived);}
    @PostMapping public Object save(HttpSession session,@RequestBody String raw){String actor=actor(session);if(raw.length()>20000)throw new IllegalArgumentException("Member profile is too large");return members.save(actor,db.decode(raw,TeamMembers.Save.class));}
    @GetMapping("/{id}") public Object read(HttpSession session,@PathVariable String id){return members.get(actor(session),id);}
    @GetMapping("/{id}/history") public Object history(HttpSession session,@PathVariable String id){return members.history(actor(session),id);}
    @GetMapping("/{id}/export") public Object export(HttpSession session,@PathVariable String id){return Map.of("protocol","aiw.team-member.v1","member",members.get(actor(session),id),"scope","Member profile and human-maintained notes only; shared knowledge and model credentials are not included.");}
    @PostMapping("/{id}/state") public Object state(HttpSession session,@PathVariable String id,@RequestBody State input){return members.state(actor(session),id,input.revision(),input.action());}
    @ExceptionHandler(SecurityException.class) public ResponseEntity<?> denied(){return ResponseEntity.status(403).body(Map.of("error","Operator login required"));}
    @ExceptionHandler(NoSuchElementException.class) public ResponseEntity<?> missing(){return ResponseEntity.status(404).body(Map.of("error","Member not found"));}
    @ExceptionHandler(IllegalArgumentException.class) public ResponseEntity<?> invalid(IllegalArgumentException error){return ResponseEntity.unprocessableContent().body(Map.of("error",error.getMessage()));}
    @ExceptionHandler(IllegalStateException.class) public ResponseEntity<?> changed(IllegalStateException error){return ResponseEntity.status(409).body(Map.of("error",error.getMessage()));}
}
