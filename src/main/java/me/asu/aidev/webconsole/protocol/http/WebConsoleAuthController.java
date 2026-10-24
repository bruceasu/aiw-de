package me.asu.aidev.webconsole.protocol.http;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import me.asu.aidev.webconsole.security.WebConsoleSecurityService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class WebConsoleAuthController {
    private final WebConsoleSecurityService security;

    public WebConsoleAuthController(WebConsoleSecurityService security) {
        this.security = security;
    }

    @GetMapping("/ui/login")
    public String loginPage(@RequestParam(required = false) String next, HttpSession session, Model model) {
        String target = safeNext(next);
        if (security.signedIn(session)) return "redirect:" + (target == null ? "/ui" : target);
        model.addAttribute("loginConfigured", security.loginConfigured());
        model.addAttribute("next", target);
        return "ui/login";
    }

    @PostMapping("/ui/login")
    public String login(@RequestParam String username,
                        @RequestParam String password,
                        @RequestParam(required = false) String next,
                        HttpSession session,
                        RedirectAttributes flash) {
        if (!security.authenticate(username, password)) {
            flash.addFlashAttribute("errorMessage", "Invalid username or password.");
            String target = safeNext(next);
            return "redirect:/ui/login" + (target == null ? "" : "?next=" + encode(target));
        }
        security.login(session);
        return "redirect:" + (safeNext(next) == null ? "/ui" : safeNext(next));
    }

    @PostMapping("/ui/logout")
    public String logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) session.invalidate();
        return "redirect:/ui/login";
    }

    private static String safeNext(String next) {
        if (next == null || next.isBlank()) return null;
        String value = next.strip();
        if (!value.startsWith("/ui") || value.startsWith("//") || value.contains("\r") || value.contains("\n")) return null;
        return value;
    }

    private static String encode(String value) {
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
    }
}
