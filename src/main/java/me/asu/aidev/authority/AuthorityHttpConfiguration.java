package me.asu.aidev.authority;

import jakarta.servlet.http.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import me.asu.aidev.webconsole.security.WebConsoleApiRoutes;
import me.asu.aidev.webconsole.security.WebConsoleSecurityService;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.*;

/** Closed routing: browser authority and machine authority never authenticate each other. */
@Configuration
public class AuthorityHttpConfiguration implements WebMvcConfigurer {
    private final AuthorityBoundary boundary;private final WebConsoleSecurityService security;private final AgentTransportSecurity agentTransport;
    public AuthorityHttpConfiguration(AuthorityBoundary boundary,WebConsoleSecurityService security,AgentTransportSecurity agentTransport) { this.boundary=boundary;this.security=security;this.agentTransport=agentTransport; }
    private boolean leaseMachine(String path) {
        return path.equals("/api/r1/worker-protocol/v1/workers/register") || path.equals("/api/r1/worker-protocol/v1/tasks/claim")
                || path.matches("/api/r1/worker-protocol/v1/workers/[^/]+/heartbeat")
                || path.matches("/api/r1/worker-protocol/v1/workers/[^/]+/configuration")
                || path.matches("/api/r1/worker-protocol/v1/tasks/[^/]+/questions/(source|open|ack|checkpoints|receipt)")
                || path.matches("/api/r1/worker-protocol/v1/tasks/[^/]+/execution-run/(availability|open|events|control|seal)")
                || path.matches("/api/r1/worker-protocol/v1/tasks/[^/]+/(execution-context/read|operations/(begin|finish|approval|approved|approved-begin|approved-discard)|operations/[^/]+/receipt|yield/(prepare|ack)|delivery)")
                || path.matches("/api/r1/worker-protocol/v1/tasks/[^/]+/(start|workspace-baseline|heartbeat|complete|fail|stop-acknowledgement|handoff|outputs|outputs/read)");
    }
    private boolean agentMachine(String method,String path) {
        return method.equals("GET") && (Set.of("/professional-tasks/options","/professional-tasks/diagnostics","/connection-state","/identity","/state","/target-state","/grants","/management-state","/questions","/permission-questions","/action-permissions","/messages").contains(path) || path.matches("/professional-tasks/[0-9a-f-]{36}") || path.matches("/permission-actions/[0-9a-f-]{36}") || path.matches("/receipts/[^/]+")||path.matches("/goal-proposals/[0-9a-f-]{36}")||path.matches("/personal-plans/[0-9a-f-]{36}"))
                || method.equals("POST") && (Set.of("/professional-tasks","/sessions","/actions","/restrictions","/coordination","/question-replies","/goal-proposals","/personal-plans","/permission-actions/prepare","/agents/certificate-rotation/request").contains(path) || path.matches("/messages/[0-9a-f-]{36}/ack") || path.matches("/professional-tasks/[0-9a-f-]{36}/continue"));
    }
    private boolean humanRoute(String method,String path) {
        if(method.equals("GET") && (path.matches("/agents/[^/]+/messages/dead") || path.matches("/agents/[^/]+/certificate-rotations"))) return true;
        return method.equals("POST") && (Set.of("/owners/bind","/agents/invitations","/grants","/managed-plans","/execution-policies","/execution-budgets/increment","/execution-effects/reconcile","/execution-meters/reconcile").contains(path)
                || path.equals("/agents/invitations")
                || path.matches("/execution-policies/[^/]+/(pause|resume|revoke)") || path.matches("/execution-contexts/[^/]+/bind")
                || path.matches("/execution-tasks/[^/]+/stopped")
                || path.matches("/execution-configurations/(assets|candidates|propose|prepare|state|reconcile-plan)")
                || path.matches("/execution-runs/(bind|read|command|receipt)")
                || path.matches("/execution-tool-approvals/(read|decide)")
                || path.equals("/action-permissions") || path.equals("/action-permissions/state") || Set.of("/agent-factory/state","/agent-factory/settings").contains(path) || path.matches("/action-permissions/[^/]+/revoke")
                || path.matches("/questions/(read|reply|budget|control|progress|telegram)")
                || path.matches("/workflow-graphs/(read|change|decide|recommend)")
                || path.matches("/software-development/(proposals|adopt|read)")
                || path.matches("/agents/[^/]+/(approve-pairing|enable|disable)") || path.matches("/agents/[^/]+/credential/revoke") || path.matches("/agents/[^/]+/certificate-rotations/[^/]+/(approve|reject)") || path.matches("/grants/[^/]+/revoke")
                || path.matches("/targets/[^/]+/(takeover|return)") || path.matches("/restrictions/[^/]+/revoke")
                || path.matches("/budgets/[^/]+/supplement") || path.matches("/effects/[^/]+/reconcile")
                || path.matches("/agents/[^/]+/messages/[^/]+/retry"));
    }
    @Override public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override public boolean preHandle(HttpServletRequest request,HttpServletResponse response,Object handler) throws Exception {
                String path=request.getRequestURI().substring(request.getContextPath().length());String method=request.getMethod();
                if(path.startsWith("/api/r1/authority/")) {
                    String operation=path.substring("/api/r1/authority".length());
                    if(method.equals("POST") && operation.equals("/agents/pair")) return true;
                    if(agentMachine(method,operation)) {
                        String authorization=request.getHeader("Authorization");String agent=request.getHeader("X-AIW-Agent");
                        if(authorization==null || !authorization.startsWith("Bearer ")) { response.sendError(401,"Dedicated Agent credential required");return false; }
                        boolean rotationRequest=method.equals("POST") && operation.equals("/agents/certificate-rotation/request");
                        try {
                            request.setAttribute("authorityPrincipal",boundary.authenticate(agent,authorization.substring(7)));
                            agentTransport.authenticate(request,agent,rotationRequest || operation.equals("/sessions"));
                        }
                        catch(SecurityException error) { response.sendError(401,"Invalid Agent identity");return false; }
                        String approvalState=boundary.agentApprovalState(agent);
                        if(!Set.of("PENDING","APPROVED").contains(approvalState)) {
                            response.sendError(403,"Agent identity requires a fresh human-approved pairing");return false;
                        }
                        if("PENDING".equals(approvalState) && !Set.of("/sessions","/connection-state").contains(operation)) {
                            response.sendError(403,"Agent pairing approval pending");return false;
                        }
                        if(!operation.equals("/sessions") && !boundary.processBoot(agent,request.getHeader("X-AIW-Agent-Boot"))) { response.sendError(401,"Current Agent process session required");return false; }
                        return true;
                    }
                    if(!humanRoute(method,operation)) { response.sendError(403,"Authority route not allowed");return false; }
                } else {
                    if(leaseMachine(path)) return true;
                    if(!Set.of("/ui/authority","/ui/agents","/ui/delegation","/ui/agent-factory","/ui/configurations","/ui/questions","/ui/workflow-graphs","/ui/software-development").contains(path) && Set.of("GET","HEAD","OPTIONS").contains(method)) return true;
                    // The earlier console interceptor already checks local-session CSRF and the persistent auth requirement.
                    if(WebConsoleApiRoutes.matches(path) && !security.enabled() && !security.signedIn(request.getSession(false)) && !boundary.humanAuthRequired()) return true;
                }
                var session=request.getSession(false);
                if(!security.signedIn(session)) {
                    if(path.startsWith("/ui/") && method.equals("GET")) {
                        String target=path+(request.getQueryString()==null?"":"?"+request.getQueryString());
                        response.sendRedirect(request.getContextPath()+"/ui/login?next="+URLEncoder.encode(target,StandardCharsets.UTF_8));
                    } else response.sendError(401,"Configured human authentication required");
                    return false;
                }
                if(!Set.of("GET","HEAD").contains(method)) {
                    String csrf=request.getHeader("X-CSRF-Token");if(csrf==null) csrf=request.getParameter("_csrf");
                    if(!security.validCsrf(session,csrf)) { response.sendError(403,"Current human CSRF token required");return false; }
                    try {
                        if(path.equals("/api/r1/authority/execution-tool-approvals/decide") || path.matches("/api/r1/approvals/[^/]+/decision") || path.matches("/api/r1/authority/execution-(effects|meters)/reconcile") || path.matches("/api/r1/authority/execution-tasks/[^/]+/stopped") || path.equals("/api/r1/authority/execution-budgets/increment")) security.requireApprover(session);
                        else if(path.equals("/api/r1/authority/execution-tool-approvals/read")) {
                            if(!security.role(session).canOperate() && !security.role(session).canApprove())throw new SecurityException("Operation or approval role required");
                        }
                        else if(path.startsWith("/api/r1/authority/questions/")) { /* The decoded question kind selects the exact current role in its service. */ }
                        else if(path.startsWith("/api/r1/authority/execution-")) security.requireOperator(session);
                        else if(path.contains("deletion") || path.contains("delete")) security.requireAdmin(session);
                        else if(!path.startsWith("/api/r1/authority/")) security.requireOperator(session);
                    } catch(SecurityException error) { response.sendError(403,"Current action role required");return false; }
                }
                boundary.human(security.actor(session));return true;
            }
            @Override public void afterCompletion(HttpServletRequest request,HttpServletResponse response,Object handler,Exception error) { boundary.human(null); }
        }).addPathPatterns("/api/r1/**","/ui/authority","/ui/agents","/ui/delegation","/ui/configurations","/ui/questions","/ui/workflow-graphs","/ui/software-development").order(10);
    }
}
