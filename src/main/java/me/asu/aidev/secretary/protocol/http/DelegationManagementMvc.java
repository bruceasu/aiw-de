package me.asu.aidev.secretary.protocol.http;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Set;
import me.asu.aidev.secretary.application.DelegationBoundary;
import me.asu.aidev.webconsole.security.WebConsoleSecurityService;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Once delegation is enabled, old administrative APIs cannot impersonate a human. */
@Configuration
public class DelegationManagementMvc implements WebMvcConfigurer {
    private final DelegationBoundary boundary;
    private final WebConsoleSecurityService security;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;
    private final org.springframework.transaction.support.TransactionTemplate transaction;
    public DelegationManagementMvc(DelegationBoundary boundary,WebConsoleSecurityService security,
            org.springframework.jdbc.core.JdbcTemplate jdbc,org.springframework.transaction.PlatformTransactionManager manager) {
        this.boundary=boundary;this.security=security;this.jdbc=jdbc;
        this.transaction=new org.springframework.transaction.support.TransactionTemplate(manager);
    }
    private boolean machine(String path) {
        if(path.equals("/api/worker-protocol/v1/workers/register") || path.equals("/api/worker-protocol/v1/tasks/claim")) return true;
        if(path.matches("/api/worker-protocol/v1/workers/[^/]+/heartbeat")) return true;
        return path.matches("/api/worker-protocol/v1/tasks/[^/]+/(start|heartbeat|complete|fail|stop-acknowledgement|outputs|outputs/read)");
    }
    @Override public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override public boolean preHandle(HttpServletRequest request,HttpServletResponse response,Object handler) throws Exception {
                boundary.clearHuman();
                String path=request.getRequestURI().substring(request.getContextPath().length());
                if(Set.of("GET","HEAD","OPTIONS").contains(request.getMethod()) || machine(path)) return true;
                // Register legacy requests under the same DB lock used by first activation.
                // Activation refuses while any such request is in flight; no lock spans HTTP processing.
                String receipt=transaction.execute(status->{
                    boundary.lock();
                    if(boundary.legacyAuthRequired()) return null;
                    String id=java.util.UUID.randomUUID().toString();
                    jdbc.update("INSERT INTO delegation_legacy_request VALUES(?,?,?,?, 'ACTIVE',?,NULL)",id,boundary.boot(),request.getMethod(),path,System.currentTimeMillis());
                    return id;
                });
                if(receipt!=null) { request.setAttribute("aiw.delegation.legacyReceipt",receipt);return true; }
                var session=request.getSession(false);
                if(!security.enabled() || session==null || !security.authenticated(session)) {
                    response.sendError(401,"Human console authentication is required for administrative API writes after delegation activation");return false;
                }
                String csrf=request.getHeader("X-CSRF-Token");
                if(csrf==null) csrf=request.getParameter("_csrf");
                if(!security.validCsrf(session,csrf)) { response.sendError(403,"A current human CSRF token is required");return false; }
                try {
                    if(path.matches("/api/r7/approvals/[^/]+/decision")) security.requireApprover(session);
                    else if(path.contains("delete") || path.contains("deletion")) security.requireAdmin(session);
                    else security.requireOperator(session);
                } catch(SecurityException error) { response.sendError(403,error.getMessage());return false; }
                boundary.authenticatedHuman(security.actor(session));return true;
            }
            @Override public void afterCompletion(HttpServletRequest request,HttpServletResponse response,Object handler,Exception error) {
                boundary.clearHuman();
                Object receipt=request.getAttribute("aiw.delegation.legacyReceipt");
                if(receipt instanceof String id) transaction.executeWithoutResult(status->{
                    boundary.lock();jdbc.update("UPDATE delegation_legacy_request SET state='DONE',finished_ms=? WHERE id=? AND state='ACTIVE'",System.currentTimeMillis(),id);
                });
            }
        }).addPathPatterns("/api/**");
    }
}
