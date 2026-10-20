package me.asu.aidev.webconsole.security;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConsoleWebMvcConfiguration implements WebMvcConfigurer {
    private final WebConsoleSecurityInterceptor interceptor;

    public WebConsoleWebMvcConfiguration(WebConsoleSecurityInterceptor interceptor) {
        this.interceptor = interceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor).addPathPatterns("/ui", "/ui/**")
                .addPathPatterns(WebConsoleApiRoutes.patterns()).order(-100);
    }
}
