package me.asu.aidev.questions;

import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import me.asu.aidev.authority.*;
import me.asu.aidev.execution.guard.GuardContract;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import static me.asu.aidev.questions.QuestionContract.*;

@RestController
public class QuestionController {
    private final AuthorityRepository repo;private final FormalQuestions questions;private final PersonalQuestions personal;private final TelegramQuestions telegram;
    public QuestionController(AuthorityRepository repo,FormalQuestions questions,PersonalQuestions personal,TelegramQuestions telegram) {this.repo=repo;this.questions=questions;this.personal=personal;this.telegram=telegram;}
    private <T> T read(String raw,Class<T> type) {if(raw.length()>32768) throw new IllegalArgumentException("Question envelope limit");return repo.decode(raw,type);}
    public record Plan(String planId) {}
    public record Control(String planId,long revision,String action) {}
    public record TelegramRequest(String planId,String operation,Binding binding,String questionId) {}
    @PostMapping("/api/r1/worker-protocol/v1/tasks/{task}/questions/source") public Object source(@PathVariable String task,@RequestBody String raw) {return questions.source(task,read(raw,GuardContract.Lease.class));}
    @PostMapping("/api/r1/worker-protocol/v1/tasks/{task}/questions/checkpoints") public Object checkpoints(@PathVariable String task,@RequestBody String raw) {return questions.checkpoints(task,read(raw,GuardContract.Lease.class));}
    @PostMapping("/api/r1/worker-protocol/v1/tasks/{task}/questions/receipt") public Object receipt(@PathVariable String task,@RequestBody String raw) {return questions.receipt(task,read(raw,QuestionReceipt.class));}
    @PostMapping("/api/r1/worker-protocol/v1/tasks/{task}/questions/open") public Object open(@PathVariable String task,@RequestBody String raw) {return questions.open(task,read(raw,Open.class));}
    @PostMapping("/api/r1/worker-protocol/v1/tasks/{task}/questions/ack") public Object ack(@PathVariable String task,@RequestBody String raw) {return questions.acknowledge(task,read(raw,GuardContract.Yield.class));}
    @PostMapping("/api/r1/authority/questions/read") public Object list(@RequestBody String raw) {return questions.humanList(read(raw,Plan.class).planId());}
    @PostMapping("/api/r1/authority/questions/reply") public Object reply(@RequestBody String raw) {return questions.humanReply(read(raw,Reply.class));}
    @PostMapping("/api/r1/authority/questions/budget") public Object budget(@RequestBody String raw) {return questions.budget(read(raw,BudgetQuestion.class));}
    @PostMapping("/api/r1/authority/questions/control") public Object control(@RequestBody String raw) {var c=read(raw,Control.class);if(!Set.of("pause","resume","cancel").contains(c.action())) throw new IllegalArgumentException("Unknown control");return questions.control(c.planId(),c.revision(),c.action());}
    @PostMapping("/api/r1/authority/questions/progress") public Object progress(@RequestBody String raw) {return questions.progress(read(raw,Plan.class).planId());}
    @PostMapping("/api/r1/authority/questions/telegram") public Object telegram(@RequestBody String raw) {var r=read(raw,TelegramRequest.class);return telegram.manage(r.planId(),r.operation(),r.binding(),r.questionId());}
    @GetMapping("/api/r1/authority/questions") public Object personalList(HttpServletRequest request,@RequestParam String planId) {return personal.list((AuthorityBoundary.Principal)request.getAttribute("authorityPrincipal"),planId);}
    @PostMapping("/api/r1/authority/question-replies") public Object personalReply(HttpServletRequest request,@RequestBody String raw) {
        var receipt=personal.submit((AuthorityBoundary.Principal)request.getAttribute("authorityPrincipal"),read(raw,PersonalReply.class));return ResponseEntity.status(receipt.state().equals("REJECTED")?422:200).body(receipt);
    }
    @ExceptionHandler(SecurityException.class) public ResponseEntity<?> denied() {return ResponseEntity.status(403).body(Map.of("code","QUESTION_SCOPE_DENIED"));}
    @ExceptionHandler({IllegalArgumentException.class,NoSuchElementException.class}) public ResponseEntity<?> invalid() {return ResponseEntity.unprocessableContent().body(Map.of("code","QUESTION_INVALID_INPUT"));}
    @ExceptionHandler(IllegalStateException.class) public ResponseEntity<?> conflict() {return ResponseEntity.status(409).body(Map.of("code","QUESTION_STATE_CHANGED","replay",false));}
}
