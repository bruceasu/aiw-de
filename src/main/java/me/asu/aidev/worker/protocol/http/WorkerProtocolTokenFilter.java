package me.asu.aidev.worker.protocol.http;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Optional shared-token protection for the internal worker protocol.
 * Local mode works with no token. Remote deployments should configure AIW_WORKER_TOKEN.
 */
@Component
public class WorkerProtocolTokenFilter extends OncePerRequestFilter {
    private static final String PREFIX = "/api/r1/worker-protocol/";
    private final String configuredToken;

    public WorkerProtocolTokenFilter(@Value("${aidev.worker-protocol.token:}") String configuredToken) {
        this.configuredToken = configuredToken == null ? "" : configuredToken.strip();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return configuredToken.isEmpty() || !request.getRequestURI().substring(request.getContextPath().length()).startsWith(PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        String supplied = header != null && header.startsWith("Bearer ") ? header.substring(7) : "";
        if (!secureEquals(configuredToken, supplied)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"code\":\"unauthorized\",\"message\":\"invalid worker protocol token\"}");
            return;
        }
        chain.doFilter(request, response);
    }

    private static boolean secureEquals(String expected, String supplied) {
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8), supplied.getBytes(StandardCharsets.UTF_8));
    }
}
