package me.asu.aidev.worker.protocol.http;

import jakarta.servlet.http.*;
import me.asu.aidev.authority.*;
import me.asu.aidev.webconsole.security.WebConsoleSecurityService;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/** MVC matches the same normalized routes as controllers; raw URL aliases cannot skip authentication. */
@Component
public class WorkerProtocolAccessInterceptor implements HandlerInterceptor {
    private final WorkerAccess access;private final AuthorityBoundary boundary;private final WebConsoleSecurityService security;
    public WorkerProtocolAccessInterceptor(WorkerAccess access,AuthorityBoundary boundary,WebConsoleSecurityService security){this.access=access;this.boundary=boundary;this.security=security;}
    @Override public boolean preHandle(HttpServletRequest request,HttpServletResponse response,Object handler)throws Exception{
        String header=request.getHeader("Authorization"),worker;
        try{
            if(header==null&&security.localRequest(request))worker="*";
            else{
                if(!request.isSecure()&&!WebConsoleSecurityService.loopback(request.getRemoteAddr()))throw new SecurityException("Remote Worker access requires HTTPS");
                if(header==null||!header.startsWith("Bearer "))throw new SecurityException("Worker credential required");
                String method=handler instanceof HandlerMethod h?h.getMethod().getName().toLowerCase(java.util.Locale.ROOT):"";
                worker=access.authenticate(header.substring(7),method.contains("register")||method.contains("claim"));
            }
            boundary.workerIdentity(worker);return true;
        }catch(SecurityException error){response.sendError(401,error.getMessage());return false;}
        catch(IllegalStateException error){response.sendError(403,error.getMessage());return false;}
    }
    @Override public void afterCompletion(HttpServletRequest request,HttpServletResponse response,Object handler,Exception error){boundary.workerIdentity(null);}
}
