package me.asu.aidev.workflow.protocol.http;

import jakarta.servlet.http.HttpSession;
import me.asu.aidev.webconsole.security.WebConsoleSecurityService;
import me.asu.aidev.workflow.application.WorkflowRecommendationService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/ui/system/recommendations")
public class RecommendationConsoleController {
    private final WorkflowRecommendationService recommendations;
    private final WebConsoleSecurityService security;
    public RecommendationConsoleController(WorkflowRecommendationService recommendations, WebConsoleSecurityService security) {
        this.recommendations = recommendations;
        this.security = security;
    }
    @GetMapping public String page(HttpSession session, Model model) {
        security.requireAdmin(session);
        model.addAttribute("page", "system");
        model.addAttribute("settings", recommendations.settings());
        model.addAttribute("profiles", recommendations.profileOptions());
        model.addAttribute("availability", recommendations.availability());
        model.addAttribute("audit", recommendations.audit());
        return "ui/recommendation-settings";
    }
    @PostMapping public String save(@RequestParam(defaultValue="false") boolean enabled,
            @RequestParam(required=false) String planner, @RequestParam(required=false) String model,
            @RequestParam(required=false) String effort, @RequestParam long revision, HttpSession session, RedirectAttributes flash) {
        security.requireAdmin(session);
        try {
            recommendations.configure(new WorkflowRecommendationService.Configuration(enabled, planner, model, effort, revision), security.actor(session));
            flash.addFlashAttribute("successMessage", "Recommendation settings saved. No restart is required.");
        } catch (IllegalArgumentException | IllegalStateException error) { flash.addFlashAttribute("errorMessage", error.getMessage()); }
        return "redirect:/ui/system/recommendations";
    }
    @ExceptionHandler(SecurityException.class)
    @ResponseBody
    @ResponseStatus(org.springframework.http.HttpStatus.FORBIDDEN)
    public String forbidden(SecurityException error) { return error.getMessage(); }
}
