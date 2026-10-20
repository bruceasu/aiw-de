package me.asu.aidev.secretary.protocol.http;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import me.asu.aidev.secretary.application.DelegationBoundary;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class DelegateIdentityMvc implements WebMvcConfigurer {
    private final DelegationBoundary boundary;
    public DelegateIdentityMvc(DelegationBoundary boundary) { this.boundary = boundary; }
    @Override public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
                Object variables = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
                if (variables instanceof Map<?, ?> map && map.get("workerId") instanceof String worker)
                    boundary.authenticate(worker, request.getHeader("X-AIW-Delegate-Key"));
                return true;
            }
        }).addPathPatterns("/api/r1/worker-protocol/**");
    }
}
