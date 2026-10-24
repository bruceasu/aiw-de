package me.asu.aidev.webconsole.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.PathContainer;
import org.springframework.http.server.RequestPath;
import org.springframework.stereotype.Component;
import org.springframework.web.util.ServletRequestPathUtils;
import org.springframework.web.util.UriUtils;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class WebConsoleSecurityInterceptor implements HandlerInterceptor {
    private me.asu.aidev.authority.AuthorityBoundary delegation;
    @org.springframework.beans.factory.annotation.Autowired
    public void setAuthorityBoundary(me.asu.aidev.authority.AuthorityBoundary boundary) { this.delegation=boundary; }
    private final WebConsoleSecurityService security;

    public WebConsoleSecurityInterceptor(WebConsoleSecurityService security) {
        this.security = security;
    }

    private record ConsolePath(String match,String target) {}

    private static ConsolePath consolePath(HttpServletRequest request) {
        RequestPath parsed = ServletRequestPathUtils.hasParsedRequestPath(request)
                ? ServletRequestPathUtils.getParsedRequestPath(request)
                : RequestPath.parse(request.getRequestURI(),request.getContextPath());
        var match = new StringBuilder();var target = new StringBuilder();
        for (var element : parsed.pathWithinApplication().elements()) {
            if (element instanceof PathContainer.PathSegment segment) {
                match.append(segment.valueToMatch());
                target.append(UriUtils.encode(segment.valueToMatch(),StandardCharsets.UTF_8));
            } else { match.append(element.value());target.append(element.value()); }
        }
        return new ConsolePath(match.toString(),target.toString());
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        ConsolePath route = consolePath(request);
        String path = route.match();
        if (path.equals("/ui/login") || path.equals("/ui/console.css") || path.equals("/ui/console.js")) return true;
        boolean page = path.equals("/ui") || path.startsWith("/ui/");
        // MVC registration owns scope; raw URI aliases must never open an early pass.
        HttpSession session = request.getSession(true);
        if (!security.authenticated(session)) {
            if (page && (HttpMethod.GET.matches(request.getMethod()) || HttpMethod.HEAD.matches(request.getMethod()))) {
                String target = route.target() + (request.getQueryString() == null ? "" : "?" + request.getQueryString());
                String next = URLEncoder.encode(target, StandardCharsets.UTF_8);
                response.sendRedirect(request.getContextPath() + "/ui/login?next=" + next);
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
        if(delegation!=null && security.signedIn(session)) delegation.human(security.actor(session));
        request.setAttribute("consoleAuthEnabled", security.signedIn(session));
        request.setAttribute("consolePrincipal", security.username(session));
        request.setAttribute("consoleRole", role.name());
        request.setAttribute("canOperate", role.canOperate());
        request.setAttribute("canApprove", role.canApprove());
        request.setAttribute("canAdmin", role.canAdmin());
        request.setAttribute("csrfToken", security.ensureCsrf(session));
        return true;
    }
    @Override public void afterCompletion(HttpServletRequest request,HttpServletResponse response,Object handler,Exception error) {
        if(delegation!=null) delegation.human(null);
    }
}
