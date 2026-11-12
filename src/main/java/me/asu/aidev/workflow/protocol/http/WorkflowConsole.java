package me.asu.aidev.workflow.protocol.http;

import jakarta.servlet.http.HttpSession;
import java.util.*;
import me.asu.aidev.webconsole.security.WebConsoleSecurityService;
import me.asu.aidev.workflow.application.WorkflowRuns;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
public class WorkflowConsole {
    private final WorkflowRuns workflows;
    private final WebConsoleSecurityService security;
    public WorkflowConsole(WorkflowRuns workflows,WebConsoleSecurityService security) {this.workflows=workflows;this.security=security;}
    public record Save(String id,long version,WorkflowRuns.Spec spec) {}
    public record Start(String requestId,String definitionId,long version,String input,String location) {}
    public record Simple(String requestId,String instruction,String location) {}
    public record Control(long version,String action,String answer) {}
    @GetMapping("/ui/workflows") public String page(Model model) {model.addAttribute("page","workflows");return "ui/workflows";}
    @GetMapping("/api/r1/workflows") @ResponseBody public Object list(HttpSession session) {
        security.requireOperator(session);return Map.of("definitions",workflows.definitions(),"executions",workflows.executions());
    }
    @PostMapping("/api/r1/workflows/definitions") @ResponseBody public Object save(@RequestBody Save input,HttpSession session) {
        security.requireOperator(session);return workflows.save(input.id(),input.version(),input.spec());
    }
    @PostMapping("/api/r1/workflows/start") @ResponseBody public Object start(@RequestBody Start input,HttpSession session) {
        security.requireOperator(session);return workflows.start(input.requestId(),input.definitionId(),input.version(),input.input(),input.location());
    }
    @PostMapping("/api/r1/workflows/tasks") @ResponseBody public Object simple(@RequestBody Simple input,HttpSession session) {
        security.requireOperator(session);return workflows.simple(input.requestId(),input.instruction(),input.location());
    }
    @GetMapping("/api/r1/workflows/executions/{id}") @ResponseBody public Object view(@PathVariable String id,HttpSession session) {
        security.requireOperator(session);return workflows.view(id);
    }
    @PostMapping("/api/r1/workflows/executions/{id}/control") @ResponseBody public Object control(@PathVariable String id,@RequestBody Control input,HttpSession session) {
        security.requireOperator(session);return workflows.control(id,input.version(),input.action(),input.answer());
    }
    @ExceptionHandler(SecurityException.class) @ResponseBody public ResponseEntity<?> denied() {
        return ResponseEntity.status(403).body(Map.of("error","This workflow belongs to another user or needs a current login."));
    }
    @ExceptionHandler(NoSuchElementException.class) @ResponseBody public ResponseEntity<?> missing() {
        return ResponseEntity.status(404).body(Map.of("error","Workflow or execution not found."));
    }
    @ExceptionHandler(IllegalArgumentException.class) @ResponseBody public ResponseEntity<?> invalid(IllegalArgumentException error) {
        return ResponseEntity.unprocessableContent().body(Map.of("error",Objects.toString(error.getMessage(),"Invalid workflow input")));
    }
    @ExceptionHandler(IllegalStateException.class) @ResponseBody public ResponseEntity<?> changed(IllegalStateException error) {
        return ResponseEntity.status(409).body(Map.of("error",Objects.toString(error.getMessage(),"Workflow state changed")));
    }
}
