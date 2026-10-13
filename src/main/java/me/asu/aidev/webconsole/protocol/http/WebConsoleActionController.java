package me.asu.aidev.webconsole.protocol.http;

import jakarta.servlet.http.HttpSession;
import me.asu.aidev.webconsole.application.WebConsoleActionService;
import me.asu.aidev.webconsole.security.WebConsoleSecurityService;
import java.nio.charset.StandardCharsets;
import java.util.function.Supplier;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.UriUtils;

@Controller
@RequestMapping("/ui")
public class WebConsoleActionController {
    private final WebConsoleActionService actions;
    private final WebConsoleSecurityService security;

    public WebConsoleActionController(WebConsoleActionService actions, WebConsoleSecurityService security) {
        this.actions = actions;
        this.security = security;
    }

    @PostMapping("/workers/{workerId}/drain")
    public String drainWorker(@PathVariable String workerId, HttpSession session, RedirectAttributes flash) {
        security.requireOperator(session);
        return execute(flash, "redirect:/ui/workers", "Worker is draining and will receive no new work.",
                () -> actions.drainWorker(workerId));
    }

    @PostMapping("/workers/{workerId}/offline")
    public String offlineWorker(@PathVariable String workerId, HttpSession session, RedirectAttributes flash) {
        security.requireOperator(session);
        return execute(flash, "redirect:/ui/workers", "Worker marked offline.",
                () -> actions.offlineWorker(workerId));
    }

    @PostMapping("/system/fleet-scheduling")
    public String fleetScheduling(@RequestParam int maxParallelism,
                                  @RequestParam(required = false) String reason,
                                  HttpSession session, RedirectAttributes flash) {
        security.requireOperator(session);
        return execute(flash, "redirect:/ui/system", "Fleet scheduling limit updated.",
                () -> actions.updateFleetScheduling(maxParallelism, security.actor(session), reason));
    }

    private static String execute(RedirectAttributes flash, String redirect, String success, Runnable operation) {
        try {
            operation.run();
            flash.addFlashAttribute("successMessage", success);
        } catch (RuntimeException e) {
            flash.addFlashAttribute("errorMessage", message(e));
        }
        return redirect;
    }

    private static String message(RuntimeException e) {
        String value = e.getMessage();
        return value == null || value.isBlank() ? e.getClass().getSimpleName() : value;
    }
}
