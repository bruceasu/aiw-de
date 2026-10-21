package me.asu.aidev.authority;

import java.lang.reflect.Type;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;

/** Actor fields in server-declared request records come from the authenticated session. */
@ControllerAdvice
public class HumanActorAdvice extends RequestBodyAdviceAdapter {
    private final AuthorityBoundary boundary;
    public HumanActorAdvice(AuthorityBoundary boundary) { this.boundary=boundary; }
    @Override public boolean supports(MethodParameter parameter,Type type,Class<? extends HttpMessageConverter<?>> converter) { return true; }
    @Override public Object afterBodyRead(Object body,HttpInputMessage input,MethodParameter parameter,Type type,Class<? extends HttpMessageConverter<?>> converter) {
        if(boundary.human()==null || !body.getClass().isRecord()) return body;
        var components=body.getClass().getRecordComponents();
        boolean actor=java.util.Arrays.stream(components).anyMatch(c->c.getName().equals("actor") && c.getType()==String.class);
        if(!actor) return body;
        try {
            var types=new Class<?>[components.length];var values=new Object[components.length];
            for(int i=0;i<components.length;i++) {
                types[i]=components[i].getType();values[i]=components[i].getName().equals("actor")?boundary.human():components[i].getAccessor().invoke(body);
            }
            return body.getClass().getDeclaredConstructor(types).newInstance(values);
        } catch(ReflectiveOperationException error) { throw new IllegalStateException("Cannot bind trusted human actor"); }
    }
}
