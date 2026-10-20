package me.asu.aidev.webconsole.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class WebConsoleSecurityInterceptor implements HandlerInterceptor {
    private me.asu.aidev.secretary.application.DelegationBoundary delegation;
    @org.springframework.beans.factory.annotation.Autowired
    public void setDelegationBoundary(me.asu.aidev.secretary.application.DelegationBoundary boundary) { this.delegation=boundary; }
    private final WebConsoleSecurityService security;

    public WebConsoleSecurityInterceptor(WebConsoleSecurityService security) {
        this.security = security;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (path.equals("/ui/login") || path.equals("/ui/console.css") || path.equals("/ui/console.js")) return true;
        if (!path.equals("/ui") && !path.startsWith("/ui/")) return true;
        if(delegation!=null && delegation.legacyAuthRequired() && !security.enabled()) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED,"Human console authentication must remain enabled after delegation activation");return false;
        }

        HttpSession session = request.getSession(true);
        if (!security.authenticated(session)) {
            if (HttpMethod.GET.matches(request.getMethod())) {
                String next = URLEncoder.encode(path, StandardCharsets.UTF_8);
                response.sendRedirect("/ui/login?next=" + next);
            } else {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Web Console authentication required");
            }
            return false;
        }

        if (!HttpMethod.GET.matches(request.getMethod()) && !HttpMethod.HEAD.matches(request.getMethod())) {
            if (!security.validCsrf(session, request.getParameter("_csrf"))) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "invalid Web Console CSRF token");
                return false;
            }
        }

        WebConsoleRole role = security.role(session);
        if(delegation!=null && security.enabled()) delegation.authenticatedHuman(security.actor(session));
        request.setAttribute("consoleAuthEnabled", security.enabled());
        request.setAttribute("consolePrincipal", security.username(session));
        request.setAttribute("consoleRole", role.name());
        request.setAttribute("canOperate", role.canOperate());
        request.setAttribute("canApprove", role.canApprove());
        request.setAttribute("canAdmin", role.canAdmin());
        request.setAttribute("csrfToken", security.ensureCsrf(session));
        return true;
    }
    @Override public void afterCompletion(HttpServletRequest request,HttpServletResponse response,Object handler,Exception error) {
        if(delegation!=null) delegation.clearHuman();
    }
}
