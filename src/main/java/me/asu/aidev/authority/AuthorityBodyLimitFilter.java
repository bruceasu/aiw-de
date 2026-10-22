package me.asu.aidev.authority;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.*;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class AuthorityBodyLimitFilter extends OncePerRequestFilter {
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws ServletException,IOException {
        String path=request.getRequestURI().substring(request.getContextPath().length());
        boolean machine=path.matches("/api/r1/worker-protocol/v1/tasks/[^/]+/(execution-context/read|operations/.*|yield/.*|delivery)")
                || path.matches("/api/r1/worker-protocol/v1/workers/[^/]+/configuration");
        if((!path.startsWith("/api/r1/authority/") && !machine) || !request.getMethod().equals("POST")) { chain.doFilter(request,response);return; }
        int limit=machine?131072:32768;
        byte[] bytes=request.getInputStream().readNBytes(limit+1);
        if(bytes.length>limit) { response.sendError(413,"Platform action exceeds its body limit");return; }
        var wrapped=new HttpServletRequestWrapper(request) {
            @Override public ServletInputStream getInputStream() {
                var input=new ByteArrayInputStream(bytes);
                return new ServletInputStream() {
                    @Override public int read() { return input.read(); }
                    @Override public boolean isFinished() { return input.available()==0; }
                    @Override public boolean isReady() { return true; }
                    @Override public void setReadListener(ReadListener listener) { throw new UnsupportedOperationException("Synchronous bounded action bodies only"); }
                };
            }
            @Override public BufferedReader getReader() { return new BufferedReader(new InputStreamReader(getInputStream(),java.nio.charset.StandardCharsets.UTF_8)); }
        };
        chain.doFilter(wrapped,response);
    }
}
