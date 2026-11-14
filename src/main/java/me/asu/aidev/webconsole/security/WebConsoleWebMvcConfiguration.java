package me.asu.aidev.webconsole.security;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConsoleWebMvcConfiguration implements WebMvcConfigurer {
    private final WebConsoleSecurityInterceptor interceptor;
    private final me.asu.aidev.worker.protocol.http.WorkerProtocolAccessInterceptor workers;

    public WebConsoleWebMvcConfiguration(WebConsoleSecurityInterceptor interceptor,me.asu.aidev.worker.protocol.http.WorkerProtocolAccessInterceptor workers) {
        this.interceptor = interceptor;
        this.workers=workers;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(workers).addPathPatterns("/api/r1/worker-protocol/**").order(-200);
        registry.addInterceptor(interceptor).addPathPatterns("/ui", "/ui/**","/api/r1/**")
                .excludePathPatterns("/api/r1/worker-protocol/**","/api/r1/authority/**").order(-100);
    }
}
