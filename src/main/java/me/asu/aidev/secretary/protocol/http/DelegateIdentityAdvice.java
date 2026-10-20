package me.asu.aidev.secretary.protocol.http;

import java.lang.reflect.Type;
import me.asu.aidev.secretary.application.DelegationBoundary;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;

/** Authenticate reserved Worker IDs on every existing protocol request body. */
@ControllerAdvice
public class DelegateIdentityAdvice extends RequestBodyAdviceAdapter {
    private final DelegationBoundary boundary;
    public DelegateIdentityAdvice(DelegationBoundary boundary) { this.boundary = boundary; }
    @Override public boolean supports(MethodParameter parameter, Type type, Class<? extends HttpMessageConverter<?>> converter) {
        String controller = parameter.getContainingClass().getName();
        return controller.equals("me.asu.aidev.worker.protocol.http.WorkerProtocolV1Controller")
                || controller.equals("me.asu.aidev.output.protocol.http.WorkerOutputController");
    }
    @Override public Object afterBodyRead(Object body, HttpInputMessage message, MethodParameter parameter,
                                         Type type, Class<? extends HttpMessageConverter<?>> converter) {
        try {
            String worker = (String) body.getClass().getMethod("workerId").invoke(body);
            if (worker != null) boundary.authenticate(worker, message.getHeaders().getFirst("X-AIW-Delegate-Key"));
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Worker protocol body lacks a verified identity", error);
        }
        return body;
    }
}
