package me.asu.aidev.goal.protocol.http;

import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import me.asu.aidev.authority.AuthorityBoundary;
import me.asu.aidev.goal.application.DiscussionBoard;
import me.asu.aidev.webconsole.security.WebConsoleSecurityService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

@Controller
public class DiscussionBoardController {
    public record Input(String title,String body) {}
    public record ClarificationInput(String questionId,String body) {}
    public record SecretaryReply(UUID delegationId,UUID requestId,String body) {}
    private final DiscussionBoard board;
    private final WebConsoleSecurityService security;

    public DiscussionBoardController(DiscussionBoard board,WebConsoleSecurityService security) {
        this.board=board;this.security=security;
    }

    private String actor(HttpSession session) { security.requireOperator(session);return security.actor(session); }

    @GetMapping("/ui/discussions") public String page(HttpSession session) {actor(session);return "ui/discussions";}
    @GetMapping("/api/r1/discussions") @ResponseBody public Object list(HttpSession session,
            @RequestParam(defaultValue="") String q,@RequestParam(defaultValue="") String wish,@RequestParam(defaultValue="0") int page) {
        var rows=board.search(actor(session),q,wish,page);
        return Map.of("items",rows.subList(0,Math.min(rows.size(),25)),"hasMore",rows.size()>25);
    }
    @PostMapping("/api/r1/discussions") @ResponseBody public Object create(HttpSession session,@RequestBody Input input) {
        return board.create(actor(session),input.title(),input.body());
    }
    @GetMapping("/api/r1/discussions/{id}") @ResponseBody public Object detail(HttpSession session,@PathVariable String id) {
        return board.detail(actor(session),id);
    }
    @PostMapping("/api/r1/discussions/{id}/reply") @ResponseBody public Object reply(HttpSession session,@PathVariable String id,@RequestBody Input input) {
        board.reply(actor(session),id,input.body());return Map.of("saved",true);
    }
    @PostMapping("/api/r1/discussions/{id}/close") @ResponseBody public Object close(HttpSession session,@PathVariable String id) {
        board.close(actor(session),id);return Map.of("saved",true);
    }
    @PostMapping("/api/r1/discussions/{id}/delegate") @ResponseBody public Object delegate(HttpSession session,
            @PathVariable String id,@RequestBody ClarificationInput input) {
        board.delegate(actor(session),id,input.questionId());return Map.of("saved",true);
    }
    @PostMapping("/api/r1/discussions/{id}/withdraw") @ResponseBody public Object withdraw(HttpSession session,
            @PathVariable String id,@RequestBody ClarificationInput input) {
        board.withdraw(actor(session),id,input.questionId());return Map.of("saved",true);
    }
    @PostMapping("/api/r1/discussions/{id}/answer") @ResponseBody public Object answer(HttpSession session,
            @PathVariable String id,@RequestBody ClarificationInput input) {
        board.answer(actor(session),id,input.questionId(),input.body());return Map.of("saved",true);
    }

    private AuthorityBoundary.Principal secretary(HttpServletRequest request) {
        return (AuthorityBoundary.Principal)request.getAttribute("authorityPrincipal");
    }
    @GetMapping("/api/r1/authority/discussions") @ResponseBody public Object inbox(HttpServletRequest request) {
        return board.secretaryTopics(secretary(request));
    }
    @GetMapping("/api/r1/authority/discussions/topics") @ResponseBody public Object secretaryBrowse(HttpServletRequest request,
            @RequestParam(defaultValue="") String q,@RequestParam(defaultValue="0") int page) {
        var rows=board.secretaryBrowse(secretary(request),q,page);
        return Map.of("items",rows.subList(0,Math.min(rows.size(),25)),"hasMore",rows.size()>25);
    }
    @GetMapping("/api/r1/authority/discussions/{id}") @ResponseBody public Object secretaryDetail(HttpServletRequest request,@PathVariable String id) {
        return board.secretaryDetail(secretary(request),id);
    }
    @PostMapping("/api/r1/authority/discussions/{id}/replies") @ResponseBody public Object secretaryReply(HttpServletRequest request,
            @PathVariable String id,@RequestBody SecretaryReply reply) {
        board.secretaryReply(secretary(request),id,reply.delegationId(),reply.requestId(),reply.body());return Map.of("saved",true);
    }
    @ExceptionHandler(SecurityException.class) public ResponseEntity<?> denied(SecurityException error) {
        return ResponseEntity.status(403).body(Map.of("error",error.getMessage()));
    }
    @ExceptionHandler(NoSuchElementException.class) public ResponseEntity<?> missing() {
        return ResponseEntity.status(404).body(Map.of("error","Discussion not found."));
    }
    @ExceptionHandler(IllegalArgumentException.class) public ResponseEntity<?> invalid(IllegalArgumentException error) {
        return ResponseEntity.badRequest().body(Map.of("error",error.getMessage()));
    }
    @ExceptionHandler(IllegalStateException.class) public ResponseEntity<?> conflict(IllegalStateException error) {
        return ResponseEntity.status(409).body(Map.of("error",error.getMessage()));
    }
}
