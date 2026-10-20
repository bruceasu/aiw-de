package me.asu.aidev.workflow.protocol.http;

import jakarta.servlet.http.HttpSession;
import me.asu.aidev.webconsole.security.WebConsoleSecurityService;
import me.asu.aidev.workflow.application.WorkflowPlanService;
import me.asu.aidev.workflow.application.WorkflowQuestionService;
import me.asu.aidev.workflow.jdbc.JdbcWorkflowQuestionRepository.Answer;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/r1/plan-editor/plans/{planId}/questions")
public class WorkflowQuestionController {
    private final WorkflowQuestionService questions;
    private final WebConsoleSecurityService security;
    public WorkflowQuestionController(WorkflowQuestionService questions, WebConsoleSecurityService security) {
        this.questions = questions; this.security = security;
    }
    public record AnswerRequest(long version, String questionKey, String sourceFileId, String sourceHash,
                                String answer, boolean confirmed) {}
    @GetMapping
    public WorkflowQuestionService.Board board(@PathVariable String planId, HttpSession session) {
        requireRead(session); return questions.board(planId);
    }
    @GetMapping("/files/{fileId}")
    public WorkflowQuestionService.Preview preview(@PathVariable String planId, @PathVariable String fileId, HttpSession session) {
        requireRead(session); return questions.preview(planId, fileId);
    }
    @PostMapping("/answers")
    public Answer answer(@PathVariable String planId, @RequestBody AnswerRequest request, HttpSession session) {
        security.requireOperator(session);
        return questions.answer(planId, request.version(), request.questionKey(), request.sourceFileId(), request.sourceHash(),
                request.answer(), request.confirmed(), security.actor(session));
    }
    private void requireRead(HttpSession session) { if (!security.authenticated(session)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED); }
    @ExceptionHandler(WorkflowPlanService.PlanNotFound.class)
    ResponseEntity<String> missing(WorkflowPlanService.PlanNotFound error) { return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error.getMessage()); }
    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<String> invalid(IllegalArgumentException error) { return ResponseEntity.badRequest().body(error.getMessage()); }
    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<String> conflict(IllegalStateException error) { return ResponseEntity.status(HttpStatus.CONFLICT).body(error.getMessage()); }
    @ExceptionHandler(SecurityException.class)
    ResponseEntity<String> forbidden(SecurityException error) { return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error.getMessage()); }
}
