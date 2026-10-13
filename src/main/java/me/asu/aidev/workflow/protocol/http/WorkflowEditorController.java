package me.asu.aidev.workflow.protocol.http;

import jakarta.servlet.http.HttpSession;
import java.time.Instant;
import java.util.List;
import me.asu.aidev.fleet.application.FleetDashboardService;
import me.asu.aidev.webconsole.security.WebConsoleSecurityService;
import me.asu.aidev.workspace.application.ManagedWorkspaceService;
import me.asu.aidev.workflow.application.WorkflowEditorService;
import me.asu.aidev.workflow.application.WorkflowPlanService;
import me.asu.aidev.workflow.application.WorkflowCancellationService;
import me.asu.aidev.workflow.application.WorkflowPlanArchiveService;
import me.asu.aidev.workflow.application.WorkflowRecommendationService;
import me.asu.aidev.workflow.domain.WorkflowPlan;
import me.asu.aidev.workflow.domain.WorkflowPlanSpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/ui")
public class WorkflowEditorController {
    private final WorkflowEditorService editor;
    private final WorkflowPlanService plans;
    private final WorkflowCancellationService cancellation;
    private final WorkflowPlanArchiveService archives;
    private final WorkflowRecommendationService recommendations;
    private final FleetDashboardService fleet;
    private final WebConsoleSecurityService security;
    private final ManagedWorkspaceService workspaces;

    public WorkflowEditorController(WorkflowEditorService editor, WorkflowPlanService plans,
                                    WorkflowCancellationService cancellation,
                                    WorkflowPlanArchiveService archives,
                                    WorkflowRecommendationService recommendations, FleetDashboardService fleet,
                                    WebConsoleSecurityService security, ManagedWorkspaceService workspaces) {
        this.editor = editor;
        this.plans = plans;
        this.cancellation = cancellation;
        this.archives = archives;
        this.recommendations = recommendations;
        this.fleet = fleet;
        this.security = security;
        this.workspaces = workspaces;
    }

    @GetMapping("/plans")
    public String listPage(@RequestParam(defaultValue = "current") String view, Model model) {
        model.addAttribute("page", "plans");
        boolean archived = "archived".equals(view);
        model.addAttribute("archivedView", archived);
        var rows = plans.list(archived);
        model.addAttribute("plans", rows);
        model.addAttribute("archivablePlans", rows.stream().filter(p -> archives.archivable(p.id()))
                .map(WorkflowPlan::id).collect(java.util.stream.Collectors.toSet()));
        var due = new java.util.HashMap<String, Instant>();
        for (WorkflowPlan plan : rows) archives.deleteAfter(plan.id()).ifPresent(at -> due.put(plan.id(), at));
        model.addAttribute("deleteAfterByPlan", due);
        return "ui/plans";
    }

    @GetMapping("/plans/new")
    public String newPage(HttpSession session, Model model) {
        security.requireOperator(session);
        model.addAttribute("page", "plans");
        model.addAttribute("workspaceTargets", workspaces.targets());
        return "ui/plan-editor";
    }

    @GetMapping("/plans/{id}")
    public String detailPage(@PathVariable String id, Model model) {
        requireEnabled();
        model.addAttribute("page", "plans");
        model.addAttribute("planId", id);
        model.addAttribute("archived", archives.archived(id));
        model.addAttribute("deleteAfter", archives.deleteAfter(id).orElse(null));
        model.addAttribute("archivable", archives.archivable(id));
        return "ui/plan-detail";
    }

    @PostMapping("/plans/{id}/archive")
    public String archive(@PathVariable String id, HttpSession session, RedirectAttributes flash) {
        security.requireOperator(session);
        try { archives.archive(id, security.actor(session)); flash.addFlashAttribute("successMessage", "Plan archived."); }
        catch (RuntimeException error) { flash.addFlashAttribute("errorMessage", error.getMessage()); }
        return "redirect:/ui/plans";
    }

    @PostMapping("/plans/{id}/schedule-delete")
    public String scheduleDelete(@PathVariable String id, HttpSession session, RedirectAttributes flash) {
        security.requireAdmin(session);
        try { archives.scheduleDeletion(id, security.actor(session)); flash.addFlashAttribute("successMessage", "Plan scheduled for deletion in 30 days."); }
        catch (RuntimeException error) { flash.addFlashAttribute("errorMessage", error.getMessage()); }
        return "redirect:/ui/plans?view=archived";
    }

    @PostMapping("/plans/{id}/undo-delete")
    public String undoDelete(@PathVariable String id, HttpSession session, RedirectAttributes flash) {
        security.requireAdmin(session);
        try { archives.undoDeletion(id); flash.addFlashAttribute("successMessage", "Plan deletion cancelled."); }
        catch (RuntimeException error) { flash.addFlashAttribute("errorMessage", error.getMessage()); }
        return "redirect:/ui/plans?view=archived";
    }

    @GetMapping("/plan-editor/catalog")
    @ResponseBody
    public Catalog catalog() {
        requireEnabled();
        return new Catalog(editor.roles(), editor.templates(), recommendations.available(),
                fleet.dashboard(100).workers().workers());
    }

    @GetMapping("/plan-editor/plans/{id}")
    @ResponseBody
    public WorkflowEditorService.Detail detail(@PathVariable String id) {
        requireEnabled();
        return editor.detail(id);
    }

    @PostMapping("/plan-editor/drafts")
    @ResponseBody
    public WorkflowPlan draft(@RequestBody WorkflowPlanSpec spec, HttpSession session) {
        requireWrite(session);
        return editor.draft(spec, security.actor(session));
    }

    @PostMapping("/plan-editor/plans/{id}/confirm")
    @ResponseBody
    public WorkflowPlan confirm(@PathVariable String id, @RequestBody WorkflowPlanController.Version request,
                                HttpSession session) {
        requireWrite(session);
        return editor.confirm(id, request.version(), security.actor(session));
    }

    @PostMapping("/plan-editor/plans/{id}/withdraw")
    @ResponseBody
    public WorkflowPlan withdraw(@PathVariable String id, @RequestBody WorkflowPlanController.Withdrawal request,
                                 HttpSession session) {
        requireWrite(session);
        return plans.withdraw(id, request.version(), security.actor(session), request.reason());
    }

    @PostMapping("/plan-editor/plans/{id}/jump")
    @ResponseBody
    public WorkflowPlan jump(@PathVariable String id, @RequestBody WorkflowPlanController.Jump request,
                             HttpSession session) {
        requireWrite(session);
        return plans.jump(id, request.version(), request.targetStep(), security.actor(session), request.reason());
    }

    @PostMapping("/plan-editor/plans/{id}/stop")
    @ResponseBody
    public WorkflowPlan stop(@PathVariable String id, @RequestBody WorkflowPlanController.Withdrawal request,
                             HttpSession session) {
        requireWrite(session);
        return plans.stop(id, request.version(), security.actor(session), request.reason());
    }

    @PostMapping("/plan-editor/plans/{id}/complete")
    @ResponseBody
    public WorkflowPlan complete(@PathVariable String id, @RequestBody WorkflowPlanController.Withdrawal request,
                                 HttpSession session) {
        requireWrite(session);
        return plans.complete(id, request.version(), security.actor(session), request.reason());
    }

    @PostMapping("/plan-editor/plans/{id}/cancel")
    @ResponseBody
    public WorkflowPlan cancel(@PathVariable String id, @RequestBody WorkflowPlanController.Withdrawal request,
                               HttpSession session) {
        requireWrite(session);
        return cancellation.cancel(id, request.version(), security.actor(session), request.reason());
    }

    @PostMapping("/plan-editor/plans/{id}/resolve-stop")
    @ResponseBody
    public WorkflowPlan resolveStop(@PathVariable String id, @RequestBody WorkflowPlanController.Version request,
                                    HttpSession session) {
        requireWrite(session);
        return plans.clearStaleControl(id, request.version(), security.actor(session));
    }

    @PostMapping("/plan-editor/recommendations")
    @ResponseBody
    public WorkflowRecommendationService.Result recommend(@RequestBody WorkflowRecommendationService.Request request,
                                                          HttpSession session) {
        requireWrite(session);
        return recommendations.request(request, security.actor(session));
    }

    @GetMapping("/plan-editor/recommendations/{id}")
    @ResponseBody
    public WorkflowRecommendationService.Result recommendation(@PathVariable String id, HttpSession session) {
        requireWrite(session);
        return recommendations.get(id, security.actor(session));
    }

    @PostMapping("/plan-editor/recommendations/{id}/withdraw")
    @ResponseBody
    public WorkflowRecommendationService.Result withdrawRecommendation(@PathVariable String id, HttpSession session) {
        requireWrite(session);
        return recommendations.cancelQueued(id, security.actor(session));
    }

    private void requireEnabled() {
        // Plans are the only supported execution flow.
    }

    private void requireWrite(HttpSession session) {
        requireEnabled();
        security.requireOperator(session);
    }

    @ExceptionHandler(WorkflowPlanService.PlanNotFound.class)
    ResponseEntity<String> notFound(WorkflowPlanService.PlanNotFound e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<String> invalid(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(e.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<String> conflict(IllegalStateException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
    }

    @ExceptionHandler(SecurityException.class)
    ResponseEntity<String> forbidden(SecurityException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
    }

    public record Catalog(List<WorkflowEditorService.Role> roles, List<WorkflowEditorService.Template> templates,
                          boolean recommendationAvailable, List<me.asu.aidev.fleet.domain.FleetDashboard.WorkerSummary> workers) {}
}
