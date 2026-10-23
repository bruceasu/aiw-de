package me.asu.aidev.authority;

import jakarta.servlet.http.*;
import java.util.*;
import me.asu.aidev.webconsole.security.WebConsoleSecurityService;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.*;

/** Closed routing: browser authority and machine authority never authenticate each other. */
@Configuration
public class AuthorityHttpConfiguration implements WebMvcConfigurer {
    private final AuthorityBoundary boundary;private final WebConsoleSecurityService security;
    public AuthorityHttpConfiguration(AuthorityBoundary boundary,WebConsoleSecurityService security) { this.boundary=boundary;this.security=security; }
    private boolean leaseMachine(String path) {
        return path.equals("/api/r1/worker-protocol/v1/workers/register") || path.equals("/api/r1/worker-protocol/v1/tasks/claim")
                || path.matches("/api/r1/worker-protocol/v1/workers/[^/]+/heartbeat")
                || path.matches("/api/r1/worker-protocol/v1/workers/[^/]+/configuration")
                || path.matches("/api/r1/worker-protocol/v1/tasks/[^/]+/questions/(source|open|ack|checkpoints|receipt)")
                || path.matches("/api/r1/worker-protocol/v1/tasks/[^/]+/(execution-context/read|operations/(begin|finish)|operations/[^/]+/receipt|yield/(prepare|ack)|delivery)")
                || path.matches("/api/r1/worker-protocol/v1/tasks/[^/]+/(start|heartbeat|complete|fail|stop-acknowledgement|outputs|outputs/read)");
    }
    private boolean personalMachine(String method,String path) {
        return method.equals("GET") && (Set.of("/identity","/state","/target-state","/grants","/management-state","/questions").contains(path) || path.matches("/receipts/[^/]+")||path.matches("/goal-proposals/[0-9a-f-]{36}"))
                || method.equals("POST") && Set.of("/sessions","/actions","/restrictions","/coordination","/question-replies","/goal-proposals").contains(path);
    }
    private boolean humanRoute(String method,String path) {
        return method.equals("POST") && (Set.of("/owners/bind","/workers","/grants","/managed-plans","/execution-policies","/execution-budgets/increment","/execution-effects/reconcile","/execution-meters/reconcile").contains(path)
                || path.matches("/execution-policies/[^/]+/(pause|resume|revoke)") || path.matches("/execution-contexts/[^/]+/bind")
                || path.matches("/execution-tasks/[^/]+/stopped")
                || path.matches("/execution-configurations/(assets|candidates|propose|prepare|state)")
                || path.matches("/questions/(read|reply|budget|control|progress|telegram)")
                || path.matches("/workflow-graphs/(read|change|decide|recommend)")
                || path.matches("/software-development/(proposals|adopt|read)")
                || path.matches("/workers/[^/]+/(enable|disable)") || path.matches("/grants/[^/]+/revoke")
                || path.matches("/targets/[^/]+/(takeover|return)") || path.matches("/restrictions/[^/]+/revoke")
                || path.matches("/budgets/[^/]+/supplement") || path.matches("/effects/[^/]+/reconcile"));
    }
    @Override public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override public boolean preHandle(HttpServletRequest request,HttpServletResponse response,Object handler) throws Exception {
                String path=request.getRequestURI().substring(request.getContextPath().length());String method=request.getMethod();
                if(path.startsWith("/api/r1/authority/")) {
                    String operation=path.substring("/api/r1/authority".length());
                    if(personalMachine(method,operation)) {
                        String authorization=request.getHeader("Authorization");String worker=request.getHeader("X-AIW-Personal-Worker");
                        if(authorization==null || !authorization.startsWith("Bearer ")) { response.sendError(401,"Dedicated machine credential required");return false; }
                        try { request.setAttribute("authorityPrincipal",boundary.authenticate(worker,authorization.substring(7))); }
                        catch(SecurityException error) { response.sendError(401,"Invalid personal identity");return false; }
                        if(!operation.equals("/sessions") && !boundary.processBoot(worker,request.getHeader("X-AIW-Personal-Boot"))) { response.sendError(401,"Current personal process session required");return false; }
                        return true;
                    }
                    if(!humanRoute(method,operation)) { response.sendError(403,"Authority route not allowed");return false; }
                } else {
                    if(leaseMachine(path)) return true;
                    if(!Set.of("/ui/authority","/ui/configurations","/ui/questions","/ui/workflow-graphs","/ui/software-development").contains(path) && Set.of("GET","HEAD","OPTIONS").contains(method)) return true;
                }
                var session=request.getSession(false);
                if(!security.enabled() || session==null || !security.authenticated(session)) { response.sendError(401,"Configured human authentication required");return false; }
                if(!Set.of("GET","HEAD").contains(method)) {
                    String csrf=request.getHeader("X-CSRF-Token");if(csrf==null) csrf=request.getParameter("_csrf");
                    if(!security.validCsrf(session,csrf)) { response.sendError(403,"Current human CSRF token required");return false; }
                    try {
                        if(path.matches("/api/r1/approvals/[^/]+/decision") || path.matches("/api/r1/authority/execution-(effects|meters)/reconcile") || path.matches("/api/r1/authority/execution-tasks/[^/]+/stopped") || path.equals("/api/r1/authority/execution-budgets/increment")) security.requireApprover(session);
                        else if(path.startsWith("/api/r1/authority/questions/")) { /* The decoded question kind selects the exact current role in its service. */ }
                        else if(path.startsWith("/api/r1/authority/execution-")) security.requireOperator(session);
                        else if(path.contains("deletion") || path.contains("delete")) security.requireAdmin(session);
                        else if(!path.startsWith("/api/r1/authority/")) security.requireOperator(session);
                    } catch(SecurityException error) { response.sendError(403,"Current action role required");return false; }
                }
                boundary.human(security.actor(session));return true;
            }
            @Override public void afterCompletion(HttpServletRequest request,HttpServletResponse response,Object handler,Exception error) { boundary.human(null); }
        }).addPathPatterns("/api/r1/**","/ui/authority","/ui/configurations","/ui/questions","/ui/workflow-graphs","/ui/software-development").order(10);
    }
}
