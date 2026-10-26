package me.asu.aidev.goal.protocol.http;

import jakarta.servlet.http.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import com.fasterxml.jackson.core.type.TypeReference;
import me.asu.aidev.goal.application.*;
import me.asu.aidev.goal.domain.GoalData.*;
import me.asu.aidev.goal.jdbc.GoalRepository;
import me.asu.aidev.webconsole.security.WebConsoleSecurityService;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
public class GoalController {
    private final GoalRepository repo;
    private final GoalDiscussionService discussion;
    private final GoalAttachments attachments;
    private final GoalPlans plans;
    private final WebConsoleSecurityService security;
    public GoalController(GoalRepository repo, GoalDiscussionService discussion, GoalAttachments attachments, GoalPlans plans, WebConsoleSecurityService security) {
        this.repo = repo; this.discussion = discussion; this.attachments = attachments; this.plans = plans; this.security = security;
    }
    private String actor(HttpSession session) { security.requireOperator(session); return security.actor(session); }
    public record Revision(long revision, boolean confirmed) {}
    public record EditorRequest(long revision, Map<String,Object> editor) {}
    // Spring MVC uses Jackson 3. Expose plain JSON values instead of Jackson 2 tree beans.
    public record VersionResponse(String id, String goalId, long sourceRevision, Map<String,Object> snapshot,
                                  Map<String,Object> editor, long editorRevision, String recommendationId, String planId) {}
    private VersionResponse response(Version version) {
        TypeReference<Map<String,Object>> type = new TypeReference<>() {};
        return new VersionResponse(version.id(), version.goalId(), version.sourceRevision(),
            repo.json().convertValue(version.snapshot(), type), repo.json().convertValue(version.editor(), type),
            version.editorRevision(), version.recommendationId(), version.planId());
    }
    @GetMapping("/ui/goals") public String page(HttpSession session, Model model) { actor(session); return "ui/goals"; }
    @GetMapping("/api/r1/goals") @ResponseBody public Object list(HttpSession session) { return repo.list(actor(session)); }
    @PostMapping("/api/r1/goals") @ResponseBody public Object create(HttpSession session) { return discussion.create(actor(session)); }
    @GetMapping("/api/r1/goals/sources") @ResponseBody public Object sources(@RequestParam(defaultValue="") String query, HttpSession session) {
        actor(session); return discussion.sources(query.isBlank() ? null : GoalDiscussionService.bounded(query, "Query", 160, true));
    }
    @GetMapping("/api/r1/goals/{id}") @ResponseBody public Object detail(@PathVariable String id, HttpSession session) {
        String a = actor(session); Draft draft = repo.get(id, a);
        return Map.of("draft", draft, "attachments", draft.deleted() ? List.of() : repo.attachments(id,a),
            "versions", draft.deleted() ? List.of() : repo.versions(id,a).stream().map(this::response).toList(), "requests", discussion.requests(id,a));
    }
    @PostMapping("/api/r1/goals/{id}/edit") @ResponseBody public Object edit(@PathVariable String id, @RequestBody Edit edit, HttpSession session) {
        return discussion.edit(id, actor(session), edit);
    }
    @PostMapping("/api/r1/goals/{id}/delete") @ResponseBody public Object delete(@PathVariable String id, @RequestBody Revision request, HttpSession session) {
        return discussion.deleted(id, actor(session), request.revision(), true);
    }
    @PostMapping("/api/r1/goals/{id}/restore") @ResponseBody public Object restore(@PathVariable String id, @RequestBody Revision request, HttpSession session) {
        return discussion.deleted(id, actor(session), request.revision(), false);
    }
    @PostMapping("/api/r1/goals/{id}/messages") @ResponseBody public Object send(@PathVariable String id, @RequestBody GoalDiscussionService.Send request, HttpSession session) {
        return discussion.send(id, actor(session), request);
    }
    @PostMapping("/api/r1/goals/{id}/replies/{request}") @ResponseBody public Object reply(@PathVariable String id, @PathVariable String request, HttpSession session) {
        return discussion.reply(request, id, actor(session));
    }
    @PostMapping("/api/r1/goals/{id}/attachments") @ResponseBody public Object upload(@PathVariable String id, @RequestParam long revision,
        @RequestParam String filename, HttpServletRequest request, HttpSession session) throws java.io.IOException {
        String a = actor(session);
        attachments.active(id, a);
        if (request.getContentLengthLong() > 10 * 1024 * 1024) throw new IllegalArgumentException("Attachment exceeds 10 MiB");
        return attachments.upload(id, a, revision, filename, request.getInputStream().readNBytes(10 * 1024 * 1024 + 1));
    }
    @PostMapping("/api/r1/goals/{id}/attachments/{file}/convert") @ResponseBody public Object convert(@PathVariable String id, @PathVariable String file, HttpSession session) {
        String a = actor(session); var result = attachments.convert(id, file, a); return attachments.finish(id, file, a, result);
    }
    @GetMapping("/api/r1/goals/{id}/attachments/{file}") public ResponseEntity<byte[]> download(@PathVariable String id, @PathVariable String file, HttpSession session) {
        String a = actor(session);
        var selected = repo.attachments(id, a).stream().filter(f -> f.id().equals(file)).findFirst().orElseThrow();
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM)
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(selected.filename(), StandardCharsets.UTF_8).build().toString())
            .header("X-Content-Type-Options", "nosniff").cacheControl(CacheControl.noStore()).body(attachments.bytes(id, file, a));
    }
    @PostMapping("/api/r1/goals/{id}/formalize") @ResponseBody public Object formalize(@PathVariable String id, @RequestBody Revision request, HttpSession session) {
        return response(plans.formalize(id, actor(session), request.revision(), request.confirmed()));
    }
    @PostMapping("/api/r1/goals/versions/{id}/editor") @ResponseBody public Object editor(@PathVariable String id, @RequestBody EditorRequest request, HttpSession session) {
        return response(plans.saveEditor(id, actor(session), request.revision(), repo.json().valueToTree(request.editor())));
    }
    @PostMapping("/api/r1/goals/versions/{id}/analyze") @ResponseBody public Object analyze(@PathVariable String id, @RequestBody Revision request, HttpSession session) {
        return response(plans.analyze(id, actor(session), request.revision()));
    }
    @GetMapping("/api/r1/goals/versions/{id}/analysis") @ResponseBody public Object analysis(@PathVariable String id, HttpSession session) {
        return plans.analysis(id, actor(session));
    }
    @PostMapping("/api/r1/goals/versions/{id}/apply") @ResponseBody public Object apply(@PathVariable String id, @RequestBody Revision request, HttpSession session) {
        return response(plans.applyAnalysis(id, actor(session), request.revision()));
    }
    @PostMapping("/api/r1/goals/versions/{id}/prepare") @ResponseBody public Object prepare(@PathVariable String id, @RequestBody Revision request, HttpSession session) {
        return response(plans.prepare(id, actor(session), request.revision(), request.confirmed()));
    }
    @ExceptionHandler(SecurityException.class) public ResponseEntity<?> denied(SecurityException e) { return ResponseEntity.status(403).body(Map.of("error", e.getMessage())); }
    @ExceptionHandler(IllegalArgumentException.class) public ResponseEntity<?> invalid(IllegalArgumentException e) { return ResponseEntity.badRequest().body(Map.of("error", e.getMessage())); }
    @ExceptionHandler(IllegalStateException.class) public ResponseEntity<?> conflict(IllegalStateException e) { return ResponseEntity.status(409).body(Map.of("error", e.getMessage())); }
}
