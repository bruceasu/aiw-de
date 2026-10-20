package me.asu.aidev.secretary.protocol.http;

import java.lang.reflect.Type;
import java.util.*;
import me.asu.aidev.secretary.application.DelegationBoundary;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;

/** Legacy identity labels are metadata, not proof of authority. Use the verified human session. */
@ControllerAdvice
public class DelegationHumanBodyAdvice extends RequestBodyAdviceAdapter {
    private static final Set<String> ACTORS=Set.of("actor","requestedBy","decidedBy","createdBy","controlledBy","operator","approvedBy","acceptedBy");
    private final DelegationBoundary boundary;
    public DelegationHumanBodyAdvice(DelegationBoundary boundary) { this.boundary=boundary; }
    @Override public boolean supports(MethodParameter parameter,Type type,Class<? extends HttpMessageConverter<?>> converter) { return boundary.authenticatedHuman()!=null; }
    @Override public Object afterBodyRead(Object body,HttpInputMessage message,MethodParameter parameter,Type type,Class<? extends HttpMessageConverter<?>> converter) {
        String actor=boundary.authenticatedHuman();
        if(actor==null) return body;
        if(body instanceof Map<?,?> input) {
            var result=new LinkedHashMap<>(input);
            for(String key:ACTORS) if(result.containsKey(key)) replace(result,key,actor);
            return result;
        }
        if(!body.getClass().isRecord()) return body;
        try {
            var components=body.getClass().getRecordComponents();Object[] values=new Object[components.length];Class<?>[] types=new Class<?>[components.length];
            for(int i=0;i<components.length;i++) {
                var field=components[i];types[i]=field.getType();
                values[i]=field.getType()==String.class && ACTORS.contains(field.getName())?actor:field.getAccessor().invoke(body);
            }
            var constructor=body.getClass().getDeclaredConstructor(types);constructor.setAccessible(true);return constructor.newInstance(values);
        } catch(ReflectiveOperationException error) { throw new IllegalArgumentException("Cannot bind the authenticated human identity",error); }
    }
    private static <K,V> void replace(Map<K,V> ignored,String key,String actor) {
        // Maps from JSON have String keys and Object values; copy rather than mutate the caller's object.
        @SuppressWarnings("unchecked") Map<String,Object> writable=(Map<String,Object>)(Map<?,?>)ignored;
        writable.put(key,actor);
    }
}
