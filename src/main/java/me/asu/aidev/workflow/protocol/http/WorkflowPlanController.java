package me.asu.aidev.workflow.protocol.http;

import jakarta.servlet.http.HttpSession;
import java.util.List;
import me.asu.aidev.webconsole.security.WebConsoleSecurityService;
import me.asu.aidev.work.domain.WorkTask;
import me.asu.aidev.workflow.application.WorkflowPlanService;
import me.asu.aidev.workflow.application.WorkflowCancellationService;
import me.asu.aidev.workflow.application.WorkflowPlanArchiveService;
import me.asu.aidev.workflow.domain.WorkflowPlan;
import me.asu.aidev.workflow.domain.WorkflowPlanSpec;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Session/CSRF protected management API. No Worker submission token can confirm a plan. */
@RestController
@RequestMapping("/ui/workflow-plans")
public class WorkflowPlanController {
    private final WorkflowPlanService plans;
    private final WorkflowCancellationService cancellation;
    private final WorkflowPlanArchiveService archives;
    private final WebConsoleSecurityService security;

    public WorkflowPlanController(WorkflowPlanService plans, WorkflowCancellationService cancellation,
                                  WorkflowPlanArchiveService archives,
                                  WebConsoleSecurityService security) {
        this.plans = plans;
        this.cancellation = cancellation;
        this.archives = archives;
        this.security = security;
    }

    @GetMapping
    public List<WorkflowPlan> list(@RequestParam(defaultValue = "current") String view) {
        return plans.list("archived".equals(view));
    }

    @GetMapping("/{id}")
    public Detail get(@PathVariable String id) { return new Detail(plans.get(id), plans.steps(id), plans.controlHistory(id)); }

    @PostMapping
    public WorkflowPlan draft(@RequestBody WorkflowPlanSpec spec, HttpSession session) {
        security.requireOperator(session);
        return plans.draft(spec, security.actor(session));
    }

    @PostMapping("/{id}/copy")
    public WorkflowPlan copy(@PathVariable String id, HttpSession session) {
        security.requireOperator(session);
        return plans.copy(id, security.actor(session));
    }

    @PostMapping("/{id}/confirm")
    public WorkflowPlan confirm(@PathVariable String id, @RequestBody Version request, HttpSession session) {
        security.requireOperator(session);
        return plans.confirm(id, request.version(), security.actor(session));
    }

    @PostMapping("/{id}/withdraw")
    public WorkflowPlan withdraw(@PathVariable String id, @RequestBody Withdrawal request, HttpSession session) {
        security.requireOperator(session);
        return plans.withdraw(id, request.version(), security.actor(session), request.reason());
    }

    @PostMapping("/{id}/jump")
    public WorkflowPlan jump(@PathVariable String id, @RequestBody Jump request, HttpSession session) {
        security.requireOperator(session);
        return plans.jump(id, request.version(), request.targetStep(), security.actor(session), request.reason());
    }

    @PostMapping("/{id}/stop")
    public WorkflowPlan stop(@PathVariable String id, @RequestBody Withdrawal request, HttpSession session) {
        security.requireOperator(session);
        return plans.stop(id, request.version(), security.actor(session), request.reason());
    }

    @PostMapping("/{id}/complete")
    public WorkflowPlan complete(@PathVariable String id, @RequestBody Withdrawal request, HttpSession session) {
        security.requireOperator(session);
        return plans.complete(id, request.version(), security.actor(session), request.reason());
    }

    @PostMapping("/{id}/cancel")
    public WorkflowPlan cancel(@PathVariable String id, @RequestBody Withdrawal request, HttpSession session) {
        security.requireOperator(session);
        return cancellation.cancel(id, request.version(), security.actor(session), request.reason());
    }

    @PostMapping("/{id}/resolve-stop")
    public WorkflowPlan resolveStop(@PathVariable String id, @RequestBody Version request, HttpSession session) {
        security.requireOperator(session);
        return plans.clearStaleControl(id, request.version(), security.actor(session));
    }

    @PostMapping("/{id}/archive")
    public void archive(@PathVariable String id, HttpSession session) {
        security.requireOperator(session);
        archives.archive(id, security.actor(session));
    }

    @PostMapping("/{id}/schedule-delete")
    public void scheduleDelete(@PathVariable String id, HttpSession session) {
        security.requireAdmin(session);
        archives.scheduleDeletion(id, security.actor(session));
    }

    @PostMapping("/{id}/undo-delete")
    public void undoDelete(@PathVariable String id, HttpSession session) {
        security.requireAdmin(session);
        archives.undoDeletion(id);
    }

    @ExceptionHandler(WorkflowPlanService.PlanNotFound.class)
    ResponseEntity<String> notFound(WorkflowPlanService.PlanNotFound e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<String> invalid(IllegalArgumentException e) { return ResponseEntity.badRequest().body(e.getMessage()); }

    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<String> conflict(IllegalStateException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
    }

    @ExceptionHandler(SecurityException.class)
    ResponseEntity<String> forbidden(SecurityException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
    }

    public record Detail(WorkflowPlan plan, List<WorkTask> tasks,
                         List<me.asu.aidev.workflow.jdbc.JdbcWorkflowControlRepository.Control> controls) {}
    public record Version(Long version) {
        public Version { validateVersion(version); }
    }
    public record Withdrawal(Long version, String reason) {
        public Withdrawal { validateVersion(version); }
    }
    public record Jump(Long version, Integer targetStep, String reason) {
        public Jump {
            validateVersion(version);
            if (targetStep == null || targetStep < 0) throw new IllegalArgumentException("targetStep is required");
        }
    }

    private static void validateVersion(Long version) {
        if (version == null || version < 0) throw new IllegalArgumentException("nonnegative version is required");
    }
}
